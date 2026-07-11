package com.example.kangbudget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.unit.dp
import com.example.kangbudget.data.model.Category
import com.example.kangbudget.data.model.IncomeType
import com.example.kangbudget.ui.util.OPEN_INCOME_COLOR
import com.example.kangbudget.ui.util.formatAmount
import com.example.kangbudget.ui.util.incomeGoalProgressColor
import com.example.kangbudget.util.CategoryEarning

@Composable
fun IncomeCategoryCard(
    earning: CategoryEarning,
    onClick: () -> Unit,
    onEditConfirmed: (name: String, targetGoal: Double) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onQuickAdd: (amount: Double, description: String) -> Unit
) {
    val category: Category = earning.category
    var showQuickAdd by remember { mutableStateOf(false) }
    val allowQuickAdd = category.incomeType != IncomeType.FIXED

    SwipeableCategoryCard(
        category = category,
        onClick = onClick,
        onEditConfirmed = onEditConfirmed,
        onDeleteConfirmed = onDeleteConfirmed
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = category.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = incomeSubtitle(category),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatAmount(earning.earned),
                        color = if (category.incomeType == IncomeType.OPEN) OPEN_INCOME_COLOR else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (allowQuickAdd) {
                        IconButton(onClick = { showQuickAdd = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add income")
                        }
                    }
                }
            }
            if (category.incomeType == IncomeType.GOAL) {
                Spacer(modifier = Modifier.height(8.dp))
                ProgressStatusBar(
                    fraction = (earning.percentOfGoal / 100.0).toFloat(),
                    color = incomeGoalProgressColor(earning.percentOfGoal)
                )
            }
        }
    }

    if (showQuickAdd) {
        QuickAddTransactionDialog(
            categoryName = category.name,
            onDismiss = { showQuickAdd = false },
            onConfirm = { amount, description ->
                onQuickAdd(amount, description)
                showQuickAdd = false
            }
        )
    }
}

private fun incomeSubtitle(category: Category): String = when (category.incomeType) {
    IncomeType.FIXED -> "Fixed · ${formatAmount(category.targetGoal)} monthly"
    IncomeType.OPEN -> "Open · add as it comes"
    IncomeType.GOAL -> "Goal · ${formatAmount(category.targetGoal)}"
    else -> ""
}
