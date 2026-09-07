package com.example.kangbudget.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kangbudget.data.PrivacyPreferences
import com.example.kangbudget.data.model.Category
import com.example.kangbudget.ui.components.ActivityLogDialog
import com.example.kangbudget.ui.components.BudgetHubDialog
import com.example.kangbudget.ui.components.EditTransactionDialog
import com.example.kangbudget.ui.components.TransactionDetailSheet
import com.example.kangbudget.ui.util.LocalAmountsHidden
import com.example.kangbudget.viewmodel.BudgetViewModel
import com.example.kangbudget.util.currentMonthId
import com.example.kangbudget.util.monthIdToDisplayName
import com.example.kangbudget.viewmodel.HomeTab
import com.example.kangbudget.viewmodel.TransactionEditState
import kotlinx.coroutines.launch

private val HISTORICAL_BLUE = Color(0xFF2196F3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    budgetViewModel: BudgetViewModel = viewModel(),
    offlineNotice: Boolean = false
) {
    val monthId by budgetViewModel.monthId.collectAsStateWithLifecycle()
    val budget by budgetViewModel.budget.collectAsStateWithLifecycle()
    val insights by budgetViewModel.insights.collectAsStateWithLifecycle()
    val transactionsByCategory by budgetViewModel.transactionsByCategory.collectAsStateWithLifecycle()
    val selectedTab by budgetViewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedCategoryId by budgetViewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val categories by budgetViewModel.categories.collectAsStateWithLifecycle()

    val activityLog by budgetViewModel.activityLog.collectAsStateWithLifecycle()
    val allBudgets by budgetViewModel.allBudgets.collectAsStateWithLifecycle()
    val transactionEditState by budgetViewModel.transactionEditState.collectAsStateWithLifecycle()

    var showBudgetHub by remember { mutableStateOf(false) }
    var showActivityLog by remember { mutableStateOf(false) }
    var offlineBannerDismissed by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val privacyPreferences = remember { PrivacyPreferences(context.applicationContext) }
    val amountsHidden by privacyPreferences.amountsHidden.collectAsStateWithLifecycle(initialValue = false)

    CompositionLocalProvider(LocalAmountsHidden provides amountsHidden) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (monthId == currentMonthId()) {
                            Text("KangBudget")
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(monthIdToDisplayName(monthId))
                                Spacer(modifier = Modifier.width(8.dp))
                                HistoricalViewBadge(onClick = { budgetViewModel.resetToCurrentMonth() })
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            coroutineScope.launch { privacyPreferences.setAmountsHidden(!amountsHidden) }
                        }) {
                            Icon(
                                if (amountsHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (amountsHidden) "Show amounts" else "Hide amounts"
                            )
                        }
                        IconButton(onClick = { showActivityLog = true }) {
                            Icon(Icons.Filled.History, contentDescription = "Activity Log")
                        }
                        IconButton(onClick = { showBudgetHub = true }) {
                            Icon(Icons.Filled.Description, contentDescription = "Budget Management")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                if (offlineNotice && !offlineBannerDismissed) {
                    OfflineBanner(onDismiss = { offlineBannerDismissed = true })
                }
                TabRow(selectedTabIndex = if (selectedTab == HomeTab.HOME) 0 else 1) {
                    Tab(
                        selected = selectedTab == HomeTab.HOME,
                        onClick = { budgetViewModel.selectTab(HomeTab.HOME) },
                        text = { Text("Home") }
                    )
                    Tab(
                        selected = selectedTab == HomeTab.INSIGHTS,
                        onClick = { budgetViewModel.selectTab(HomeTab.INSIGHTS) },
                        text = { Text("Doughnuts") }
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

        if (showActivityLog) {
            ActivityLogDialog(
                activityLog = activityLog,
                onDismiss = { showActivityLog = false },
                onEntryClick = { entry ->
                    // Close the log immediately; the edit panel takes over as soon as the
                    // requested month's snapshots resolve.
                    showActivityLog = false
                    budgetViewModel.navigateToAndEditTransaction(
                        monthId = entry.monthId,
                        categoryId = entry.categoryId,
                        transactionId = entry.transactionId
                    )
                }
            )
        }

        when (val editState = transactionEditState) {
            is TransactionEditState.Idle -> Unit

            is TransactionEditState.Loading -> AlertDialog(
                onDismissRequest = { budgetViewModel.dismissTransactionEdit() },
                title = { Text("Loading transaction") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            text = "Opening ${monthIdToDisplayName(editState.monthId)}…",
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { budgetViewModel.dismissTransactionEdit() }) {
                        Text("Cancel")
                    }
                }
            )

            is TransactionEditState.Ready -> EditTransactionDialog(
                transaction = editState.transaction,
                onDismiss = { budgetViewModel.dismissTransactionEdit() },
                onConfirm = { updated ->
                    // Writes against the originating month explicitly, not the selected one.
                    budgetViewModel.updateTransactionIn(
                        monthId = editState.monthId,
                        categoryId = editState.category.id,
                        transaction = updated
                    )
                    budgetViewModel.dismissTransactionEdit()
                }
            )

            is TransactionEditState.Unavailable -> AlertDialog(
                onDismissRequest = { budgetViewModel.dismissTransactionEdit() },
                title = { Text("Transaction unavailable") },
                text = {
                    Text(
                        "This transaction is no longer in " +
                            "${monthIdToDisplayName(editState.monthId)}. It may have been deleted."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { budgetViewModel.dismissTransactionEdit() }) {
                        Text("OK")
                    }
                }
            )
        }

        if (showBudgetHub) {
            BudgetHubDialog(
                budgets = allBudgets,
                currentMonthId = monthId,
                onDismiss = { showBudgetHub = false },
                onSelectMonth = { newMonthId -> budgetViewModel.switchMonth(newMonthId) },
                onCloneMonth = { targetId, targetName, balance ->
                    budgetViewModel.cloneMonth(monthId, targetId, targetName, balance)
                }
            )
        }
    }
}

@Composable
private fun OfflineBanner(onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "You're offline — showing saved data. Sync features are paused until you reconnect.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

/**
 * Pulsing blue "HISTORICAL VIEW" tag shown in the top bar while [monthId] differs from the
 * live system month. Tapping it snaps the dashboard back to the current month.
 */
@Composable
private fun HistoricalViewBadge(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "historicalPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Surface(
        shape = RoundedCornerShape(50),
        color = HISTORICAL_BLUE.copy(alpha = 0.15f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(HISTORICAL_BLUE.copy(alpha = pulseAlpha))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "HISTORICAL VIEW",
                style = MaterialTheme.typography.labelSmall,
                color = HISTORICAL_BLUE
            )
        }
    }
}
