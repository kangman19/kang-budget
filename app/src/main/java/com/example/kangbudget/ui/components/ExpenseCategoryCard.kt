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
import androidx.compose.material.icons.filled.Edit
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
import com.example.kangbudget.ui.util.expenseProgressColor
import com.example.kangbudget.ui.util.formatAmount
import com.example.kangbudget.util.CategorySpend

@Composable
fun ExpenseCategoryCard(
    spend: CategorySpend,
    onClick: () -> Unit,
    onEditConfirmed: (name: String, targetGoal: Double) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onQuickAdd: (amount: Double, description: String) -> Unit
) {
    val category: Category = spend.category
    var showQuickAdd by remember { mutableStateOf(false) }
    val barColor = expenseProgressColor(spend.percentUsed)
    val isOverBudget = spend.percentUsed > 100.0

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
                Text(text = category.name, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${formatAmount(spend.spent)} / ${formatAmount(category.targetGoal)}",
                        color = barColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = { showQuickAdd = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add transaction")
                    }
                    IconButton(onClick = { onClick() }) {
                        Icon(Icons.Filled.Edit, contentDescription = "View details")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            ProgressStatusBar(fraction = (spend.percentUsed / 100.0).toFloat(), color = barColor)
            if (isOverBudget) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Over budget by ${formatAmount(spend.spent - category.targetGoal)}",
                    color = barColor,
                    style = MaterialTheme.typography.labelSmall
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
