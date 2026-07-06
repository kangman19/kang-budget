package com.example.kangbudget.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kangbudget.data.model.ActivityLogEntry
import com.example.kangbudget.data.model.Budget
import com.example.kangbudget.data.repository.BudgetRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val repository = BudgetRepository()

    val activityLog: StateFlow<List<ActivityLogEntry>> = repository.observeGlobalActivityLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBudgets: StateFlow<List<Budget>> = repository.observeAllBudgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun cloneMonth(sourceMonthId: String, targetMonthId: String, targetMonthName: String, newInitialBalance: Double) {
        viewModelScope.launch {
            repository.cloneMonth(sourceMonthId, targetMonthId, targetMonthName, newInitialBalance)
        }
    }
}
