package com.example.kangbudget.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kangbudget.data.model.ActivityLogEntry
import com.example.kangbudget.data.model.Budget
import com.example.kangbudget.data.model.Category
import com.example.kangbudget.data.model.Transaction
import com.example.kangbudget.data.repository.BudgetRepository
import com.example.kangbudget.util.InsightsData
import com.example.kangbudget.util.calculateInsights
import com.example.kangbudget.util.currentMonthId
import com.google.firebase.Timestamp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

enum class HomeTab { HOME, INSIGHTS }

/**
 * A deep-link request from the Activity Log that may still be waiting on its month's
 * Firestore snapshots to arrive. Held as intent, never as an eagerly-resolved value.
 */
private data class PendingTransactionEdit(
    val monthId: String,
    val categoryId: String,
    val transactionId: String
)

/**
 * Categories stamped with the month they were loaded for. The stamp is what makes the
 * deep link safe: category ids are reused across months (cloneMonth copies them verbatim),
 * so an unstamped lookup can match the *previous* month's data for the same category id.
 */
private data class MonthCategories(val monthId: String, val categories: List<Category>)

/** Transactions stamped with the month they were loaded for. See [MonthCategories]. */
private data class MonthTransactions(
    val monthId: String,
    val byCategory: Map<String, List<Transaction>>
)

/**
 * Resolution state for an Activity Log deep link. [Loading] is emitted while the target
 * month's snapshots are still in flight, so the UI never has to read a half-switched cache.
 */
sealed interface TransactionEditState {
    data object Idle : TransactionEditState
    data class Loading(val monthId: String) : TransactionEditState
    data class Ready(
        val monthId: String,
        val category: Category,
        val transaction: Transaction
    ) : TransactionEditState
    data class Unavailable(val monthId: String) : TransactionEditState
}

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel : ViewModel() {

    private val repository = BudgetRepository()

    private val _monthId = MutableStateFlow(currentMonthId())
    val monthId: StateFlow<String> = _monthId.asStateFlow()

    private val _selectedTab = MutableStateFlow(HomeTab.HOME)
    val selectedTab: StateFlow<HomeTab> = _selectedTab.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _pendingTransactionEdit = MutableStateFlow<PendingTransactionEdit?>(null)

    val budget: StateFlow<Budget?> = _monthId
        .flatMapLatest { repository.observeBudget(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val scopedCategories: StateFlow<MonthCategories?> = _monthId
        .flatMapLatest { id -> repository.observeCategories(id).map { MonthCategories(id, it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Derived from the stamped categories rather than combine(_monthId, categories): a plain
    // combine emits as soon as _monthId changes, briefly pairing the NEW month id with the
    // OLD month's category list and opening listeners on paths that belong to neither.
    private val scopedTransactions: StateFlow<MonthTransactions?> = scopedCategories
        .flatMapLatest { scoped ->
            when {
                scoped == null -> flowOf(null)
                scoped.categories.isEmpty() -> flowOf(MonthTransactions(scoped.monthId, emptyMap()))
                else -> combine(
                    scoped.categories.map { category ->
                        repository.observeTransactions(scoped.monthId, category.id)
                    }
                ) { lists ->
                    MonthTransactions(
                        scoped.monthId,
                        scoped.categories.indices.associate { index ->
                            scoped.categories[index].id to lists[index]
                        }
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val categories: StateFlow<List<Category>> = scopedCategories
        .map { it?.categories.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactionsByCategory: StateFlow<Map<String, List<Transaction>>> = scopedTransactions
        .map { it?.byCategory.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Resolves a pending Activity Log deep link reactively. Rather than reading the cache
     * immediately after switching months — which returns the outgoing month's data — this
     * waits until both streams report the *requested* month, then looks the transaction up.
     * A definitive [TransactionEditState.Unavailable] is only possible once that data has
     * landed, so no timeout or arbitrary delay is involved.
     */
    val transactionEditState: StateFlow<TransactionEditState> =
        combine(_pendingTransactionEdit, scopedCategories, scopedTransactions) { pending, cats, txs ->
            if (pending == null) return@combine TransactionEditState.Idle
            if (cats?.monthId != pending.monthId || txs?.monthId != pending.monthId) {
                return@combine TransactionEditState.Loading(pending.monthId)
            }
            val category = cats.categories.find { it.id == pending.categoryId }
            val transaction = txs.byCategory[pending.categoryId]
                ?.find { it.id == pending.transactionId }
            if (category != null && transaction != null) {
                TransactionEditState.Ready(pending.monthId, category, transaction)
            } else {
                TransactionEditState.Unavailable(pending.monthId)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionEditState.Idle)

    val insights: StateFlow<InsightsData> = combine(budget, categories, transactionsByCategory, _monthId) { b, cats, txMap, id ->
        calculateInsights(b, cats, txMap, id)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        calculateInsights(null, emptyList(), emptyMap(), currentMonthId())
    )

    val activityLog: StateFlow<List<ActivityLogEntry>> = repository.observeGlobalActivityLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBudgets: StateFlow<List<Budget>> = repository.observeAllBudgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(tab: HomeTab) {
        _selectedTab.value = tab
    }

    fun switchMonth(newMonthId: String) {
        _monthId.value = newMonthId
    }

    /**
     * Deep link from the Activity Log: switches the dashboard to [monthId] and opens the
     * edit panel for the given transaction once that month's data has actually loaded.
     *
     * The intent is recorded *before* the month switch so a fast snapshot can never land in
     * the gap between the two. If [monthId] is already the active month the streams are
     * already stamped with it and resolution completes on the next emission.
     */
    fun navigateToAndEditTransaction(monthId: String, categoryId: String, transactionId: String) {
        _selectedCategoryId.value = null
        _pendingTransactionEdit.value = PendingTransactionEdit(monthId, categoryId, transactionId)
        _monthId.value = monthId
    }

    fun dismissTransactionEdit() {
        _pendingTransactionEdit.value = null
    }

    fun openCategoryDetail(categoryId: String) {
        _selectedCategoryId.value = categoryId
    }

    fun closeCategoryDetail() {
        _selectedCategoryId.value = null
    }

    fun editInitialBalance(newInitialBalance: Double) {
        viewModelScope.launch {
            repository.updateInitialBalance(monthId.value, newInitialBalance)
        }
    }

    fun addCategory(category: Category) {
        viewModelScope.launch {
            repository.addCategory(monthId.value, category)
        }
    }

    fun editCategory(category: Category, newName: String, newTargetGoal: Double) {
        viewModelScope.launch {
            repository.updateCategory(monthId.value, category.copy(name = newName, targetGoal = newTargetGoal))
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.archiveCategory(monthId.value, category.id)
        }
    }

    fun addTransaction(category: Category, amount: Double, description: String, dateTime: LocalDateTime) {
        viewModelScope.launch {
            val instant = dateTime.atZone(ZoneId.systemDefault()).toInstant()
            repository.addTransaction(
                monthId.value,
                category,
                Transaction(
                    amount = amount,
                    description = description,
                    date = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    time = dateTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    timestamp = Timestamp(Date.from(instant))
                )
            )
        }
    }

    fun updateTransaction(categoryId: String, transaction: Transaction) {
        updateTransactionIn(monthId.value, categoryId, transaction)
    }

    /**
     * Writes against an explicit month instead of the currently-selected one. The deep-link
     * editor uses this so the save still targets the originating month even if the user
     * switches months while the panel is open.
     */
    fun updateTransactionIn(monthId: String, categoryId: String, transaction: Transaction) {
        viewModelScope.launch {
            repository.updateTransaction(monthId, categoryId, transaction)
        }
    }

    fun deleteTransaction(categoryId: String, transactionId: String) {
        viewModelScope.launch {
            repository.deleteTransaction(monthId.value, categoryId, transactionId)
        }
    }

    fun cloneMonth(sourceMonthId: String, targetMonthId: String, targetMonthName: String, newInitialBalance: Double) {
        viewModelScope.launch {
            repository.cloneMonth(sourceMonthId, targetMonthId, targetMonthName, newInitialBalance)
        }
    }
}
