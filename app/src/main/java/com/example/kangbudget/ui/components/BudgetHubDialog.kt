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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kangbudget.data.model.Budget
import com.example.kangbudget.util.monthIdToDisplayName

private val INCOME_GREEN = Color(0xFF2ECC71)

@Composable
fun BudgetHubDialog(
    budgets: List<Budget>,
    currentMonthId: String,
    onDismiss: () -> Unit,
    onSelectMonth: (String) -> Unit,
    onCloneMonth: (targetMonthId: String, targetMonthName: String, initialBalance: Double) -> Unit
) {
    var showCloneDialog by remember { mutableStateOf(false) }
    val sortedBudgets = remember(budgets) {
        budgets.sortedByDescending { it.id }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Budget Management")
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 500.dp)) {
                Button(
                    onClick = { showCloneDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clone Budget to Next Month")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text(
                    "Historical Budgets",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (sortedBudgets.isEmpty()) {
                    Text(
                        "No budgets available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn {
                        items(sortedBudgets, key = { it.id }) { budget ->
                            BudgetHistoryRow(
                                budget = budget,
                                isActive = budget.id == currentMonthId,
                                onClick = {
                                    onSelectMonth(budget.id)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )

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
private fun BudgetHistoryRow(
    budget: Budget,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(budget.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                monthIdToDisplayName(budget.id),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            if (isActive) "Active" else "View",
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) INCOME_GREEN else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
    HorizontalDivider()
}
