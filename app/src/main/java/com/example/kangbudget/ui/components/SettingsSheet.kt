package com.example.kangbudget.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kangbudget.data.model.ActivityLogEntry
import com.example.kangbudget.data.model.Budget
import com.example.kangbudget.data.model.CategoryType
import com.example.kangbudget.ui.util.formatAmount
import com.example.kangbudget.ui.util.privacyBlur
import com.example.kangbudget.util.monthIdToDisplayName

private val INCOME_GREEN = Color(0xFF2ECC71)
private val EXPENSE_RED = Color(0xFFE74C3C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    activityLog: List<ActivityLogEntry>,
    budgets: List<Budget>,
    currentMonthId: String,
    onDismiss: () -> Unit,
    onSelectMonth: (String) -> Unit,
    onCloneMonth: (targetMonthId: String, targetMonthName: String, initialBalance: Double) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCloneDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).heightIn(max = 560.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)
            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.padding(top = 12.dp)) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Activity") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Budgets") })
            }

            when (selectedTab) {
                0 -> LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                    items(activityLog, key = { it.transactionId }) { entry -> ActivityLogRow(entry) }
                }
                1 -> Column(modifier = Modifier.padding(top = 12.dp)) {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(budgets, key = { it.id }) { budget ->
                            BudgetRow(budget = budget, isActive = budget.id == currentMonthId, onClick = { onSelectMonth(budget.id) })
                        }
                    }
                    Button(
                        onClick = { showCloneDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) { Text("Clone Month") }
                }
            }
        }
    }

    if (showCloneDialog) {
        CloneMonthDialog(
            sourceMonthId = currentMonthId,
            onDismiss = { showCloneDialog = false },
            onConfirm = { targetId, targetName, balance ->
                onCloneMonth(targetId, targetName, balance)
                showCloneDialog = false
            }
        )
    }
}

@Composable
private fun ActivityLogRow(entry: ActivityLogEntry) {
    val color = if (entry.categoryType == CategoryType.INCOME) INCOME_GREEN else EXPENSE_RED
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(entry.categoryName, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${entry.date} · ${entry.time} · ${monthIdToDisplayName(entry.monthId)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (entry.description.isNotBlank()) {
                Text(entry.description, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            formatAmount(entry.amount),
            modifier = Modifier.privacyBlur(),
            color = color,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun BudgetRow(budget: Budget, isActive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(budget.name, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (isActive) "Active" else "View",
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) INCOME_GREEN else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
    HorizontalDivider()
}
