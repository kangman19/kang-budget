package com.example.kangbudget.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kangbudget.data.model.Category
import com.example.kangbudget.ui.components.SettingsSheet
import com.example.kangbudget.ui.components.TransactionDetailSheet
import com.example.kangbudget.ui.util.LocalAmountsHidden
import com.example.kangbudget.viewmodel.BudgetViewModel
import com.example.kangbudget.viewmodel.HomeTab
import com.example.kangbudget.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    budgetViewModel: BudgetViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val monthId by budgetViewModel.monthId.collectAsStateWithLifecycle()
    val budget by budgetViewModel.budget.collectAsStateWithLifecycle()
    val insights by budgetViewModel.insights.collectAsStateWithLifecycle()
    val transactionsByCategory by budgetViewModel.transactionsByCategory.collectAsStateWithLifecycle()
    val selectedTab by budgetViewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedCategoryId by budgetViewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val categories by budgetViewModel.categories.collectAsStateWithLifecycle()

    val activityLog by settingsViewModel.activityLog.collectAsStateWithLifecycle()
    val allBudgets by settingsViewModel.allBudgets.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var amountsHidden by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalAmountsHidden provides amountsHidden) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("KangBudget") },
                    actions = {
                        IconButton(onClick = { amountsHidden = !amountsHidden }) {
                            Icon(
                                if (amountsHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (amountsHidden) "Show amounts" else "Hide amounts"
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                TabRow(selectedTabIndex = if (selectedTab == HomeTab.HOME) 0 else 1) {
                    Tab(
                        selected = selectedTab == HomeTab.HOME,
                        onClick = { budgetViewModel.selectTab(HomeTab.HOME) },
                        text = { Text("Home") }
                    )
                    Tab(
                        selected = selectedTab == HomeTab.INSIGHTS,
                        onClick = { budgetViewModel.selectTab(HomeTab.INSIGHTS) },
                        text = { Text("Insights") }
                    )
                }

                when (selectedTab) {
                    HomeTab.HOME -> HomeScreen(
                        budget = budget,
                        monthId = monthId,
                        insights = insights,
                        onOpenCategory = { category -> budgetViewModel.openCategoryDetail(category.id) },
                        onEditCategory = { category, name, goal -> budgetViewModel.editCategory(category, name, goal) },
                        onDeleteCategory = { category -> budgetViewModel.deleteCategory(category) },
                        onQuickAddTransaction = { category, amount, description, dateTime ->
                            budgetViewModel.addTransaction(category, amount, description, dateTime)
                        },
                        onCreateCategory = { category -> budgetViewModel.addCategory(category) },
                        onEditInitialBalance = { newBalance -> budgetViewModel.editInitialBalance(newBalance) }
                    )
                    HomeTab.INSIGHTS -> InsightsScreen(monthId = monthId, insights = insights)
                }
            }
        }

        val selectedCategory: Category? = categories.find { it.id == selectedCategoryId }
        if (selectedCategory != null) {
            TransactionDetailSheet(
                category = selectedCategory,
                transactions = transactionsByCategory[selectedCategory.id].orEmpty(),
                onDismiss = { budgetViewModel.closeCategoryDetail() },
                onUpdateTransaction = { transaction -> budgetViewModel.updateTransaction(selectedCategory.id, transaction) },
                onDeleteTransaction = { transactionId -> budgetViewModel.deleteTransaction(selectedCategory.id, transactionId) }
            )
        }

        if (showSettings) {
            SettingsSheet(
                activityLog = activityLog,
                budgets = allBudgets,
                currentMonthId = monthId,
                onDismiss = { showSettings = false },
                onSelectMonth = { newMonthId -> budgetViewModel.switchMonth(newMonthId) },
                onCloneMonth = { targetId, targetName, balance ->
                    settingsViewModel.cloneMonth(monthId, targetId, targetName, balance)
                }
            )
        }
    }
}
