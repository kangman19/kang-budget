package com.example.kangbudget.data.repository

import com.example.kangbudget.data.model.ActivityLogEntry
import com.example.kangbudget.data.model.Budget
import com.example.kangbudget.data.model.BudgetType
import com.example.kangbudget.data.model.Category
import com.example.kangbudget.data.model.Transaction
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

private const val BUDGETS = "budgets"
private const val CATEGORIES = "categories"
private const val TRANSACTIONS = "transactions"

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
            .whereEqualTo("isArchived", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val categories = snapshot?.toObjects(Category::class.java).orEmpty()
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
        val registration = db.collectionGroup(TRANSACTIONS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents.orEmpty()
                trySend(
                    docs.mapNotNull { doc ->
                        val transaction = doc.toObject(Transaction::class.java) ?: return@mapNotNull null
                        val categoryRef = doc.reference.parent.parent ?: return@mapNotNull null
                        val monthRef = categoryRef.parent.parent ?: return@mapNotNull null
                        val cacheKey = "${monthRef.id}/${categoryRef.id}"
                        ActivityLogEntry(
                            transactionId = transaction.id,
                            monthId = monthRef.id,
                            categoryId = categoryRef.id,
                            categoryName = categoryNameCache[cacheKey] ?: categoryRef.id,
                            categoryType = categoryTypeCache[cacheKey] ?: "",
                            amount = transaction.amount,
                            description = transaction.description,
                            date = transaction.date,
                            time = transaction.time,
                            timestamp = transaction.timestamp
                        )
                    }
                )
            }
        awaitClose { registration.remove() }
    }

    suspend fun createBudget(budget: Budget) {
        budgetDoc(budget.id).set(budget).await()
    }

    suspend fun addCategory(monthId: String, category: Category) {
        val docRef = categoryDoc(monthId, category.id.ifBlank { categoriesRef(monthId).document().id })
        val webhookIdentifier = if (category.budgetType == BudgetType.EXCEL) {
            Category.deriveWebhookIdentifier(category.name)
        } else {
            ""
        }
        val toSave = category.copy(id = docRef.id, webhookIdentifier = webhookIdentifier)
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
        categoryDoc(monthId, categoryId).update("isArchived", true).await()
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
            .whereEqualTo("isArchived", false)
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
                source.copy(isArchived = false)
            )
        }
        batch.commit().await()
    }
}
