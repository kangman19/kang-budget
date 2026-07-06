package com.example.kangbudget.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.kangbudget.data.model.Category
import com.example.kangbudget.data.model.CategoryType
import com.example.kangbudget.ui.components.AddCategoryDialog
import com.example.kangbudget.ui.components.ExpenseCategoryCard
import com.example.kangbudget.ui.components.IncomeCategoryCard
import com.example.kangbudget.ui.components.StatTile
import com.example.kangbudget.ui.util.formatAmount
import com.example.kangbudget.ui.util.formatPercent
import com.example.kangbudget.util.InsightsData
import com.example.kangbudget.util.monthIdToDisplayName

private val EXPENSE_RED = Color(0xFFE74C3C)

@Composable
fun HomeScreen(
    budget: Budget?,
    monthId: String,
    insights: InsightsData,
    onOpenCategory: (Category) -> Unit,
    onEditCategory: (Category, String, Double) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onQuickAddTransaction: (Category, Double, String) -> Unit,
    onCreateCategory: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    var addDialogType by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.padding(16.dp)) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                text = "Balance from ${monthIdToDisplayName(monthId)}   KES ${formatAmount(budget?.initialBalance ?: 0.0)}",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { StatTile("Left to spend", formatAmount(insights.remainingToSpend), Modifier.aspectRatio(1.3f)) }
            item { StatTile("Total spent", formatAmount(insights.totalExpenditure), Modifier.aspectRatio(1.3f), valueColor = EXPENSE_RED) }
            item { StatTile("Total income", formatAmount(insights.totalIncome), Modifier.aspectRatio(1.3f)) }
            item {
                StatTile(
                    "Budget used",
                    formatPercent(if (insights.totalBudgeted > 0) insights.totalExpenditure / insights.totalBudgeted * 100 else 0.0),
                    Modifier.aspectRatio(1.3f)
                )
            }
            item { StatTile("Days left", insights.daysLeft.toString(), Modifier.aspectRatio(1.3f)) }
            item { StatTile("Daily avg", formatAmount(insights.dailyAverageSpend), Modifier.aspectRatio(1.3f)) }
            item { StatTile("Top category", insights.topSpendingCategory?.category?.name ?: "—", Modifier.aspectRatio(1.3f)) }
            item { StatTile("Saved", formatAmount(insights.remainingToSpend.coerceAtLeast(0.0)), Modifier.aspectRatio(1.3f)) }
            item {
                StatTile(
                    "Upcoming bills",
                    "${insights.expenseBreakdown.count { it.spent == 0.0 }} bills",
                    Modifier.aspectRatio(1.3f)
                )
            }
        }

        SectionHeader(title = "Income", onAddCategory = { addDialogType = CategoryType.INCOME })
        insights.incomeBreakdown.forEach { earning ->
            IncomeCategoryCard(
                earning = earning,
                onClick = { onOpenCategory(earning.category) },
                onEditConfirmed = { name, goal -> onEditCategory(earning.category, name, goal) },
                onDeleteConfirmed = { onDeleteCategory(earning.category) },
                onQuickAdd = { amount, description -> onQuickAddTransaction(earning.category, amount, description) }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        SectionHeader(title = "Expenses", onAddCategory = { addDialogType = CategoryType.EXPENSE })
        insights.expenseBreakdown.forEach { spend ->
            ExpenseCategoryCard(
                spend = spend,
                onClick = { onOpenCategory(spend.category) },
                onEditConfirmed = { name, goal -> onEditCategory(spend.category, name, goal) },
                onDeleteConfirmed = { onDeleteCategory(spend.category) },
                onQuickAdd = { amount, description -> onQuickAddTransaction(spend.category, amount, description) }
            )
        }
    }

    addDialogType?.let { type ->
        AddCategoryDialog(
            categoryType = type,
            onDismiss = { addDialogType = null },
            onConfirm = { category ->
                onCreateCategory(category)
                addDialogType = null
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, onAddCategory: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onAddCategory) { Text("+ Add category") }
    }
}
