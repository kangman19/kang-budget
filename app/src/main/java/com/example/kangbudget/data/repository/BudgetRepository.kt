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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val BUDGETS = "budgets"
private const val CATEGORIES = "categories"
private const val TRANSACTIONS = "transactions"

private val LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

class BudgetRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val categoryTypeCache = ConcurrentHashMap<String, String>()
    private val categoryNameCache = ConcurrentHashMap<String, String>()

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
    }

    fun observeAllBudgets(): Flow<List<Budget>> = callbackFlow {
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
                    categoryTypeCache["$monthId/${it.id}"] = it.type
                    categoryNameCache["$monthId/${it.id}"] = it.name
                }
                trySend(categories)
            }
        awaitClose { registration.remove() }
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
        transactionsRef(monthId, category.id).document(docRef.id)
            .set(transaction.copy(id = docRef.id))
            .await()
        if (category.budgetType == BudgetType.EXCEL && category.webhookIdentifier.isNotBlank()) {
            ExcelWebhookApi.pushToExcelWebhook(category.webhookIdentifier, transaction.amount, transaction.description)
        }
    }

    suspend fun updateTransaction(monthId: String, categoryId: String, transaction: Transaction) {
        transactionsRef(monthId, categoryId).document(transaction.id).set(transaction).await()
    }

    suspend fun deleteTransaction(monthId: String, categoryId: String, transactionId: String) {
        transactionsRef(monthId, categoryId).document(transactionId).delete().await()
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
