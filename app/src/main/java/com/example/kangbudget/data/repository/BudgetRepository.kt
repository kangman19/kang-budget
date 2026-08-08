package com.example.kangbudget.data.repository

import com.example.kangbudget.data.model.ActivityLogEntry
import com.example.kangbudget.data.model.Budget
import com.example.kangbudget.data.model.BudgetType
import com.example.kangbudget.data.model.Category
import com.example.kangbudget.data.model.Transaction
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.kangbudget.util.monthIdToDisplayName
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "BudgetRepository"

private const val BUDGETS = "budgets"
private const val CATEGORIES = "categories"
private const val TRANSACTIONS = "transactions"
private const val CONFIG = "config"
private const val WEBHOOKS_DOC = "webhooks"
private const val WEBHOOK_OUTBOX = "webhookOutbox"

private val LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

class BudgetRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val categoryTypeCache = ConcurrentHashMap<String, String>()
    private val categoryNameCache = ConcurrentHashMap<String, String>()
    // "$monthId/$categoryId" -> webhook tab identifier, or "" for non-excel categories.
    private val categoryWebhookCache = ConcurrentHashMap<String, String>()

    // Webhook dispatch is fire-and-forget: user-facing writes await only their Firestore op,
    // never the HTTP round-trip. A SupervisorJob keeps one failed send from cancelling others.
    private val webhookScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun budgetDoc(monthId: String) = db.collection(BUDGETS).document(monthId)
    private fun categoriesRef(monthId: String) = budgetDoc(monthId).collection(CATEGORIES)
    private fun categoryDoc(monthId: String, categoryId: String) = categoriesRef(monthId).document(categoryId)
    private fun transactionsRef(monthId: String, categoryId: String) =
        categoryDoc(monthId, categoryId).collection(TRANSACTIONS)

    fun observeBudget(monthId: String): Flow<Budget?> = callbackFlow {
        val registration = budgetDoc(monthId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObject(Budget::class.java))
        }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.w(TAG, "observeBudget($monthId) failed", e)
        emit(null)
    }

    private fun observeExplicitBudgets(): Flow<List<Budget>> = callbackFlow {
        val registration = db.collection(BUDGETS)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Budget::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.w(TAG, "observeExplicitBudgets failed", e)
        emit(emptyList())
    }

    /**
     * Historical months only get an explicit `budgets/{monthId}` doc when the user clones
     * into them. A month that was simply lived in as the active month (then superseded by
     * the next one at rollover) has no such doc, so its categories/transactions would
     * otherwise be invisible to the Budget Management list. This synthesizes a placeholder
     * [Budget] for any monthId seen in the transaction logs but missing an explicit doc —
     * the real category/transaction data for that month is still queried by monthId directly
     * and is unaffected by whether the parent budget doc exists.
     */
    fun observeAllBudgets(): Flow<List<Budget>> = combine(
        observeExplicitBudgets(),
        observeGlobalActivityLog()
    ) { explicit, activityLog ->
        val explicitIds = explicit.map { it.id }.toSet()
        val synthesized = activityLog.map { it.monthId }.toSet()
            .minus(explicitIds)
            .map { monthId -> Budget(id = monthId, name = monthIdToDisplayName(monthId)) }
        (explicit + synthesized).sortedBy { it.id }
    }

    fun observeCategories(monthId: String): Flow<List<Category>> = callbackFlow {
        val registration = categoriesRef(monthId)
            .whereEqualTo("archived", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val categories = snapshot?.toObjects(Category::class.java).orEmpty()
                    .sortedBy { it.createdAt }
                categories.forEach {
                    val key = "$monthId/${it.id}"
                    categoryTypeCache[key] = it.type
                    categoryNameCache[key] = it.name
                    categoryWebhookCache[key] = webhookIdentifierOf(it)
                }
                trySend(categories)
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.w(TAG, "observeCategories($monthId) failed", e)
        emit(emptyList())
    }

    fun observeTransactions(monthId: String, categoryId: String): Flow<List<Transaction>> = callbackFlow {
        val registration = transactionsRef(monthId, categoryId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Transaction::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.w(TAG, "observeTransactions($monthId, $categoryId) failed", e)
        emit(emptyList())
    }

    fun observeGlobalActivityLog(): Flow<List<ActivityLogEntry>> = callbackFlow {
        val latestSnapshotTicket = AtomicLong(0)
        val registration = db.collectionGroup(TRANSACTIONS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents.orEmpty()
                val ticket = latestSnapshotTicket.incrementAndGet()
                // Category name resolution may need async Firestore lookups, so map off the
                // listener and only emit if no newer snapshot has arrived in the meantime.
                launch {
                    val entries = docs.mapNotNull { doc -> buildActivityLogEntry(doc) }
                    if (latestSnapshotTicket.get() == ticket) trySend(entries)
                }
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.w(TAG, "observeGlobalActivityLog failed", e)
        emit(emptyList())
    }

    private suspend fun buildActivityLogEntry(doc: DocumentSnapshot): ActivityLogEntry? {
        val transaction = doc.toObject(Transaction::class.java) ?: return null
        val categoryRef = doc.reference.parent.parent ?: return null
        val monthRef = categoryRef.parent.parent ?: return null
        val identity = resolveCategoryIdentity(monthRef.id, categoryRef)

        // Anchor rows to the explicit user-logged date/time metadata; the recorded instant is
        // only consulted to backfill legacy rows written before those fields existed.
        val loggedAt = LocalDateTime.ofInstant(
            transaction.timestamp.toDate().toInstant(),
            ZoneId.systemDefault()
        )
        return ActivityLogEntry(
            transactionId = transaction.id,
            monthId = monthRef.id,
            categoryId = categoryRef.id,
            categoryName = identity.name,
            categoryType = identity.type,
            amount = transaction.amount,
            description = transaction.description,
            date = transaction.date.ifBlank { loggedAt.format(LOG_DATE_FORMAT) },
            time = transaction.time.ifBlank { loggedAt.format(LOG_TIME_FORMAT) }
        )
    }

    private data class CategoryIdentity(val name: String, val type: String)

    /**
     * Resolves the human-readable category name/type for an activity log row. Served from the
     * in-memory cache when the category has already been observed; otherwise falls back to a
     * safe lookup of the category document itself (covers months not currently subscribed).
     */
    private suspend fun resolveCategoryIdentity(monthId: String, categoryRef: DocumentReference): CategoryIdentity {
        val cacheKey = "$monthId/${categoryRef.id}"
        val cachedName = categoryNameCache[cacheKey]
        val cachedType = categoryTypeCache[cacheKey]
        if (cachedName != null && cachedType != null) return CategoryIdentity(cachedName, cachedType)

        val category = runCatching {
            categoryRef.get().await().toObject(Category::class.java)
        }.getOrNull()

        return if (category != null && category.name.isNotBlank()) {
            categoryNameCache[cacheKey] = category.name
            categoryTypeCache[cacheKey] = category.type
            CategoryIdentity(category.name, category.type)
        } else {
            CategoryIdentity(cachedName ?: "Unknown category", cachedType ?: "")
        }
    }

    // ---- Webhook (Excel/Sheets) sync ------------------------------------------------------

    /** The webhook tab identifier for a category, or "" when it is not an excel-type target. */
    private fun webhookIdentifierOf(category: Category): String =
        if (category.budgetType == BudgetType.EXCEL && category.webhookIdentifier.isNotBlank()) {
            category.webhookIdentifier
        } else {
            ""
        }

    /**
     * Resolves the webhook tab for a category on edit/delete, where only ids are known. Served
     * from the cache populated by [observeCategories]; falls back to a document `get()` for
     * months not currently subscribed. Returns null for non-excel categories. Mirrors
     * [resolveCategoryIdentity].
     */
    private suspend fun resolveWebhookTarget(monthId: String, categoryId: String): String? {
        val cacheKey = "$monthId/$categoryId"
        categoryWebhookCache[cacheKey]?.let { return it.ifBlank { null } }

        val category = runCatching {
            categoryDoc(monthId, categoryId).get().await().toObject(Category::class.java)
        }.getOrNull()

        val identifier = category?.let { webhookIdentifierOf(it) } ?: ""
        categoryWebhookCache[cacheKey] = identifier
        return identifier.ifBlank { null }
    }

    /**
     * Builds the A–H row for a transaction, reusing the date/time fallback from
     * [buildActivityLogEntry] so rows written from legacy transactions still get a date.
     */
    private fun buildWebhookPayload(
        monthId: String,
        categoryName: String,
        categoryType: String,
        transaction: Transaction
    ): WebhookPayload {
        val loggedAt = LocalDateTime.ofInstant(
            transaction.timestamp.toDate().toInstant(),
            ZoneId.systemDefault()
        )
        return WebhookPayload(
            txId = transaction.id,
            date = transaction.date.ifBlank { loggedAt.format(LOG_DATE_FORMAT) },
            time = transaction.time.ifBlank { loggedAt.format(LOG_TIME_FORMAT) },
            monthId = monthId,
            categoryName = categoryName,
            categoryType = categoryType,
            description = transaction.description,
            amount = transaction.amount
        )
    }

    /** One queued webhook operation. [payload] is present for upsert, null for delete. */
    private data class WebhookOp(
        val op: String,
        val identifier: String,
        val txId: String,
        val payload: WebhookPayload?
    )

    /**
     * Queue an op on the Firestore outbox *before* POSTing, then delete it on a 2xx. Neither
     * Firestore call is awaited: offline, the write lands in Firestore's local journal and the
     * op replays from [flushOutbox] on next launch. Idempotent upserts make replay harmless.
     */
    private fun dispatchWebhook(op: WebhookOp) {
        if (!ExcelWebhookApi.isEnabled()) return
        webhookScope.launch {
            val doc = db.collection(WEBHOOK_OUTBOX).document()
            runCatching { doc.set(op.toOutboxMap()) }
            if (runCatching { sendWebhook(op) }.getOrDefault(false)) {
                runCatching { doc.delete() }
            }
        }
    }

    private suspend fun sendWebhook(op: WebhookOp): Boolean = when (op.op) {
        "upsert" -> op.payload != null && ExcelWebhookApi.upsert(op.identifier, op.payload)
        "delete" -> ExcelWebhookApi.delete(op.identifier, op.txId)
        else -> true
    }

    private fun WebhookOp.toOutboxMap(): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "op" to op,
            "identifier" to identifier,
            "txId" to txId,
            "createdAt" to Timestamp.now()
        )
        payload?.let {
            map["date"] = it.date
            map["time"] = it.time
            map["monthId"] = it.monthId
            map["categoryName"] = it.categoryName
            map["categoryType"] = it.categoryType
            map["description"] = it.description
            map["amount"] = it.amount
        }
        return map
    }

    private fun outboxDocToOp(doc: DocumentSnapshot): WebhookOp? {
        val op = doc.getString("op") ?: return null
        val identifier = doc.getString("identifier") ?: return null
        val txId = doc.getString("txId") ?: return null
        val payload = if (op == "upsert") {
            WebhookPayload(
                txId = txId,
                date = doc.getString("date").orEmpty(),
                time = doc.getString("time").orEmpty(),
                monthId = doc.getString("monthId").orEmpty(),
                categoryName = doc.getString("categoryName").orEmpty(),
                categoryType = doc.getString("categoryType").orEmpty(),
                description = doc.getString("description").orEmpty(),
                amount = doc.getDouble("amount") ?: 0.0
            )
        } else {
            null
        }
        return WebhookOp(op, identifier, txId, payload)
    }

    /**
     * Loads `config/webhooks` ({url, secret, enabled}) into [ExcelWebhookApi] and flushes any
     * pending outbox ops. Firestore's offline cache means this works on a cold, offline launch.
     * Call once at app start.
     */
    suspend fun loadWebhookConfig() {
        val doc = runCatching {
            db.collection(CONFIG).document(WEBHOOKS_DOC).get().await()
        }.getOrNull()
        if (doc != null && doc.exists()) {
            ExcelWebhookApi.configure(
                url = doc.getString("url").orEmpty(),
                secret = doc.getString("secret").orEmpty(),
                enabled = doc.getBoolean("enabled") ?: false
            )
        }
        flushOutbox()
    }

    /** Replays every queued outbox op; deletes each on a successful 2xx. */
    suspend fun flushOutbox() {
        if (!ExcelWebhookApi.isEnabled()) return
        val snapshot = runCatching {
            db.collection(WEBHOOK_OUTBOX).get().await()
        }.getOrNull() ?: return
        for (doc in snapshot.documents) {
            val op = outboxDocToOp(doc) ?: continue
            if (runCatching { sendWebhook(op) }.getOrDefault(false)) {
                runCatching { doc.reference.delete() }
            }
        }
    }

    suspend fun createBudget(budget: Budget) {
        budgetDoc(budget.id).set(budget).await()
    }

    suspend fun updateInitialBalance(monthId: String, initialBalance: Double) {
        budgetDoc(monthId).set(
            mapOf("initialBalance" to initialBalance),
            SetOptions.merge()
        ).await()
    }

    suspend fun addCategory(monthId: String, category: Category) {
        val docRef = categoryDoc(monthId, category.id.ifBlank { categoriesRef(monthId).document().id })
        val webhookIdentifier = if (category.budgetType == BudgetType.EXCEL) {
            Category.deriveWebhookIdentifier(category.name)
        } else {
            ""
        }
        val toSave = category.copy(
            id = docRef.id,
            monthId = monthId,
            webhookIdentifier = webhookIdentifier,
            createdAt = Timestamp.now()
        )
        docRef.set(toSave).await()
    }

    suspend fun updateCategory(monthId: String, category: Category) {
        val webhookIdentifier = if (category.budgetType == BudgetType.EXCEL) {
            Category.deriveWebhookIdentifier(category.name)
        } else {
            ""
        }
        categoryDoc(monthId, category.id).set(category.copy(webhookIdentifier = webhookIdentifier)).await()
    }

    suspend fun archiveCategory(monthId: String, categoryId: String) {
        categoryDoc(monthId, categoryId).update("archived", true).await()
    }

    suspend fun addTransaction(monthId: String, category: Category, transaction: Transaction) {
        val docRef = transactionsRef(monthId, category.id).document()
        val saved = transaction.copy(id = docRef.id)
        transactionsRef(monthId, category.id).document(docRef.id).set(saved).await()

        val identifier = webhookIdentifierOf(category)
        if (identifier.isNotBlank()) {
            dispatchWebhook(
                WebhookOp(
                    op = "upsert",
                    identifier = identifier,
                    txId = saved.id,
                    payload = buildWebhookPayload(monthId, category.name, category.type, saved)
                )
            )
        }
    }

    suspend fun updateTransaction(monthId: String, categoryId: String, transaction: Transaction) {
        transactionsRef(monthId, categoryId).document(transaction.id).set(transaction).await()

        val identifier = resolveWebhookTarget(monthId, categoryId) ?: return
        val identity = resolveCategoryIdentity(monthId, categoryDoc(monthId, categoryId))
        dispatchWebhook(
            WebhookOp(
                op = "upsert",
                identifier = identifier,
                txId = transaction.id,
                payload = buildWebhookPayload(monthId, identity.name, identity.type, transaction)
            )
        )
    }

    suspend fun deleteTransaction(monthId: String, categoryId: String, transactionId: String) {
        transactionsRef(monthId, categoryId).document(transactionId).delete().await()

        val identifier = resolveWebhookTarget(monthId, categoryId) ?: return
        dispatchWebhook(WebhookOp(op = "delete", identifier = identifier, txId = transactionId, payload = null))
    }

    suspend fun cloneMonth(
        sourceMonthId: String,
        targetMonthId: String,
        targetMonthName: String,
        newInitialBalance: Double
    ) {
        val activeCategories = categoriesRef(sourceMonthId)
            .whereEqualTo("archived", false)
            .get()
            .await()
            .toObjects(Category::class.java)

        val batch = db.batch()
        batch.set(
            budgetDoc(targetMonthId),
            Budget(
                id = targetMonthId,
                name = targetMonthName,
                initialBalance = newInitialBalance,
                createdAt = Timestamp.now()
            )
        )
        activeCategories.forEach { source ->
            val targetDoc = categoryDoc(targetMonthId, source.id)
            batch.set(
                targetDoc,
                source.copy(archived = false, monthId = targetMonthId, createdAt = Timestamp.now())
            )
        }
        batch.commit().await()
    }
}
