package com.example.kangbudget.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

enum class HomeTab { HOME, INSIGHTS }

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel : ViewModel() {

    private val repository = BudgetRepository()

    private val _monthId = MutableStateFlow(currentMonthId())
    val monthId: StateFlow<String> = _monthId.asStateFlow()

    private val _selectedTab = MutableStateFlow(HomeTab.HOME)
    val selectedTab: StateFlow<HomeTab> = _selectedTab.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    val budget: StateFlow<Budget?> = _monthId
        .flatMapLatest { repository.observeBudget(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val categories: StateFlow<List<Category>> = _monthId
        .flatMapLatest { repository.observeCategories(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactionsByCategory: StateFlow<Map<String, List<Transaction>>> = combine(_monthId, categories) { id, cats -> id to cats }
        .flatMapLatest { (id, cats) ->
            if (cats.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(cats.map { category -> repository.observeTransactions(id, category.id) }) { lists ->
                    cats.indices.associate { index -> cats[index].id to lists[index] }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val insights: StateFlow<InsightsData> = combine(budget, categories, transactionsByCategory, _monthId) { b, cats, txMap, id ->
        calculateInsights(b, cats, txMap, id)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        calculateInsights(null, emptyList(), emptyMap(), currentMonthId())
    )

    fun selectTab(tab: HomeTab) {
        _selectedTab.value = tab
    }

    fun switchMonth(newMonthId: String) {
        _monthId.value = newMonthId
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
        viewModelScope.launch {
            repository.updateTransaction(monthId.value, categoryId, transaction)
        }
    }

    fun deleteTransaction(categoryId: String, transactionId: String) {
        viewModelScope.launch {
            repository.deleteTransaction(monthId.value, categoryId, transactionId)
        }
    }
}
