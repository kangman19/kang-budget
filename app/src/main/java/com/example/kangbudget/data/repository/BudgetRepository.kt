package com.example.kangbudget.data.repository

import com.example.kangbudget.data.model.ActivityLogEntry
import com.example.kangbudget.data.model.Budget
import com.example.kangbudget.data.model.BudgetType
import com.example.kangbudget.data.model.Category
import com.example.kangbudget.data.model.Transaction
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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
import kotlin.coroutines.resume

private const val TAG = "BudgetRepository"
private const val AUTH_WAIT_MS = 15_000L

/**
 * How long a dispatch waits for `config/webhooks` to load before leaving its op queued. It has
 * to cover anonymous sign-in plus a Firestore server read, so it is deliberately generous —
 * the wait costs nothing user-facing (webhook dispatch is already off the write path).
 */
private const val CONFIG_WAIT_MS = 20_000L
private const val SEND_ATTEMPTS = 3
private const val RETRY_BASE_MS = 2_000L

/**
 * Guards [BudgetRepository.flushOutbox]. Module-level rather than per-instance because callers
 * legitimately hold different repository instances (the readiness view model builds its own)
 * while sharing one Firestore outbox collection.
 */
private val flushMutex = Mutex()

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

    fun observeExplicitBudgets(): Flow<List<Budget>> = callbackFlow {
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
     *
     * Takes the already-collected [activityLog] flow rather than calling
     * [observeGlobalActivityLog] itself — that listener replays the entire collection-group
     * history through per-document category resolution, so a second independent subscription
     * would double that cost for no benefit; callers share the one hot flow instead.
     */
    fun observeAllBudgets(activityLog: Flow<List<ActivityLogEntry>>): Flow<List<Budget>> = combine(
        observeExplicitBudgets(),
        activityLog
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
                // Resolved concurrently: sequentially awaiting one lookup per document made
                // the whole history's worth of uncached (non-current-month) categories pile
                // up as serial round-trips on every cold start.
                launch {
                    val entries = coroutineScope {
                        docs.map { doc -> async { buildActivityLogEntry(doc) } }
                            .awaitAll()
                            .filterNotNull()
                    }
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

    /**
     * The webhook tab identifier for a category, or "" when it is not an excel-type target.
     *
     * `budgetType` alone decides whether a category syncs. The stored [Category.webhookIdentifier]
     * is only a cache of [Category.deriveWebhookIdentifier], so a blank one is re-derived rather
     * than treated as "not an excel category" — requiring both, as this did, silently excluded
     * every excel category whose document predates the field or was cloned from one, with no
     * dispatch and therefore no log to explain the missing rows.
     */
    private fun webhookIdentifierOf(category: Category): String =
        if (category.budgetType == BudgetType.EXCEL) {
            category.webhookIdentifier.ifBlank { Category.deriveWebhookIdentifier(category.name) }
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
        }.getOrElse {
            // A failed lookup says nothing about the category's budget type. Caching "" for it
            // would permanently mark an excel category as non-excel for the rest of the session.
            Log.w(TAG, "Could not resolve the webhook target for $cacheKey", it)
            return null
        }

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
     * Queue an op on the Firestore outbox *before* POSTing, then delete it once the sheet has
     * it. Neither Firestore call is awaited: offline, the write lands in Firestore's local
     * journal and the op replays from [flushOutbox]. Idempotent upserts make replay harmless.
     *
     * The dispatch *waits* for [ExcelWebhookApi] to be configured rather than giving up when it
     * is not yet. `config/webhooks` can only be read after anonymous sign-in lands, so a
     * transaction added in the first seconds of a cold start races the config load; bailing out
     * there left the op for the next launch, which reads exactly like a broken integration.
     */
    private fun dispatchWebhook(op: WebhookOp) {
        webhookScope.launch {
            Log.i(TAG, "Dispatching ${op.op} for tx ${op.txId} -> sheet tab '${op.identifier}'")
            val doc = db.collection(WEBHOOK_OUTBOX).document()
            runCatching { doc.set(op.toOutboxMap()) }
                .onFailure { Log.w(TAG, "Failed to queue webhook outbox doc for ${op.op}/${op.txId}", it) }

            if (!ExcelWebhookApi.awaitEnabled(CONFIG_WAIT_MS)) {
                Log.w(TAG, "Webhook transport not configured; ${op.op}/${op.txId} queued for retry")
                return@launch
            }
            if (sendWithRetry(op)) runCatching { doc.delete() }
        }
    }

    /**
     * Sends one op, retrying only the failures a retry can fix.
     *
     * A [WebhookResult.Rejected] means the script ran and refused this exact payload — a bad
     * shared secret, say — so resending it just burns quota and fills the log; it stays on the
     * outbox for a later launch instead. Only [WebhookResult.TransportFailure] (a dropped
     * connection, a timeout, a cold Apps Script deployment) gets the backoff.
     */
    private suspend fun sendWithRetry(op: WebhookOp): Boolean {
        var backoffMs = RETRY_BASE_MS
        repeat(SEND_ATTEMPTS) { attempt ->
            val result = runCatching { sendWebhook(op) }
                .getOrElse { WebhookResult.TransportFailure("sendWebhook threw: $it") }
            when (result) {
                is WebhookResult.Delivered -> return true
                is WebhookResult.Rejected -> {
                    Log.w(
                        TAG,
                        "Webhook op ${op.op}/${op.txId} rejected (${result.error}); " +
                            "left on outbox, not retrying this payload"
                    )
                    return false
                }
                is WebhookResult.TransportFailure -> {
                    val remaining = SEND_ATTEMPTS - attempt - 1
                    Log.w(
                        TAG,
                        "Webhook op ${op.op}/${op.txId} did not reach the sheet " +
                            "(${result.detail}); $remaining attempt(s) left"
                    )
                    if (remaining > 0) {
                        delay(backoffMs)
                        backoffMs *= 2
                    }
                }
            }
        }
        Log.w(TAG, "Webhook op ${op.op}/${op.txId} exhausted retries; left on outbox")
        return false
    }

    private suspend fun sendWebhook(op: WebhookOp): WebhookResult = when (op.op) {
        "upsert" -> op.payload
            ?.let { ExcelWebhookApi.upsert(op.identifier, it) }
            ?: WebhookResult.Rejected("outbox upsert doc had no payload")
        "delete" -> ExcelWebhookApi.delete(op.identifier, op.txId)
        else -> WebhookResult.Rejected("unknown op ${op.op}")
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
     * pending outbox ops. Call once at app start.
     *
     * Order matters. `config/webhooks` and `webhookOutbox` are both gated on
     * `request.auth != null` (see firestore.rules), so every server-backed step here has to
     * wait for the anonymous sign-in token. Running this concurrently with sign-in — as the
     * "faster login" change did — meant the server read came back PERMISSION_DENIED on any
     * launch that had nothing cached, [ExcelWebhookApi] stayed unconfigured for the whole
     * session, and every dispatch was dropped before it ever reached the network.
     *
     * The cache read is the exception: it is served from local persistence without touching
     * the rules, so it runs first and gives an offline cold launch an endpoint immediately.
     */
    suspend fun loadWebhookConfig() {
        var configured = readWebhookConfig(Source.CACHE)?.also(::applyWebhookConfig) != null

        if (!awaitSignedIn()) {
            Log.w(TAG, "Sign-in never landed; skipping the webhook config refresh and outbox flush")
            if (!configured) Log.w(TAG, "config/webhooks unavailable; webhook sync stays disabled")
            return
        }

        // Always refresh from the server, even on a cache hit: nothing but a server read ever
        // updates the cached copy, so a re-deployed /exec URL, a rotated secret or a flipped
        // `enabled` would otherwise stay stale for the life of the install.
        if (readWebhookConfig(Source.SERVER)?.also(::applyWebhookConfig) != null) configured = true
        if (!configured) {
            Log.w(TAG, "config/webhooks unavailable; webhook sync stays disabled")
            return
        }

        // One round trip that tells the whole story in a single log line: whether the /exec URL
        // resolves, whether the deployment answers without a Google login, and whether the
        // shared secret matches. Without it a misconfigured endpoint only ever shows up as rows
        // that quietly fail to appear in the sheet.
        when (val pong = ExcelWebhookApi.ping()) {
            is WebhookResult.Delivered ->
                Log.i(TAG, "Sheets endpoint reachable and the shared secret was accepted")
            is WebhookResult.Rejected ->
                Log.w(
                    TAG,
                    "Sheets endpoint answered but refused the ping (${pong.error}) — check that " +
                        "config/webhooks.secret matches SECRET in the deployed Code.gs"
                )
            is WebhookResult.TransportFailure ->
                Log.w(TAG, "Sheets endpoint unreachable: ${pong.detail}")
        }

        flushOutbox()
    }

    private suspend fun readWebhookConfig(source: Source): DocumentSnapshot? =
        runCatching { db.collection(CONFIG).document(WEBHOOKS_DOC).get(source).await() }
            .onFailure {
                if (source == Source.SERVER) Log.w(TAG, "config/webhooks server read failed", it)
                else Log.d(TAG, "config/webhooks not in the offline cache yet")
            }
            .getOrNull()
            ?.takeIf { it.exists() }

    private fun applyWebhookConfig(doc: DocumentSnapshot) {
        ExcelWebhookApi.configure(
            url = doc.getString("url").orEmpty(),
            secret = doc.getString("secret").orEmpty(),
            enabled = doc.getBoolean("enabled") ?: false
        )
    }

    /**
     * Suspends until a Firebase user exists, so rule-gated reads carry a token. Returns false
     * if none arrives within [AUTH_WAIT_MS] — sign-in is kicked off by AppReadyViewModel, and
     * if it failed there is nothing here to wait for.
     */
    private suspend fun awaitSignedIn(): Boolean {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) return true
        return withTimeoutOrNull(AUTH_WAIT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : FirebaseAuth.AuthStateListener {
                    override fun onAuthStateChanged(state: FirebaseAuth) {
                        if (state.currentUser == null) return
                        state.removeAuthStateListener(this)
                        if (continuation.isActive) continuation.resume(true)
                    }
                }
                auth.addAuthStateListener(listener)
                continuation.invokeOnCancellation { auth.removeAuthStateListener(listener) }
            }
        } ?: false
    }

    /**
     * Replays every queued outbox op, deleting each one the sheet accepts. Safe to call from
     * anywhere at any time: [flushMutex] serializes overlapping flushes (app start and a
     * foreground resume can easily coincide) so the same op is never in flight twice.
     */
    suspend fun flushOutbox() = flushMutex.withLock {
        if (!ExcelWebhookApi.awaitEnabled(CONFIG_WAIT_MS)) return@withLock
        val snapshot = runCatching {
            db.collection(WEBHOOK_OUTBOX).get().await()
        }.onFailure { Log.w(TAG, "Failed to read webhook outbox", it) }.getOrNull() ?: return@withLock
        if (snapshot.isEmpty) return@withLock
        Log.i(TAG, "Flushing ${snapshot.size()} queued webhook op(s)")
        for (doc in snapshot.documents) {
            val op = outboxDocToOp(doc) ?: continue
            if (sendWithRetry(op)) runCatching { doc.reference.delete() }
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
        if (identifier.isBlank()) {
            Log.d(
                TAG,
                "No sheet sync for tx ${saved.id}: category '${category.name}' has " +
                    "budgetType='${category.budgetType}'"
            )
            return
        }
        Log.i(
            TAG,
            "Dispatching upsert for tx ${saved.id} -> sheet tab '${identifier}'"
        )
        dispatchWebhook(
            WebhookOp(
                op = "upsert",
                identifier = identifier,
                txId = saved.id,
                payload = buildWebhookPayload(monthId, category.name, category.type, saved)
            )
        )
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
