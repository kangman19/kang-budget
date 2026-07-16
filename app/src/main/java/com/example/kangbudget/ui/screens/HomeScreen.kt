package com.example.kangbudget.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kangbudget.data.model.Budget
import com.example.kangbudget.data.model.Category
import com.example.kangbudget.data.model.CategoryType
import com.example.kangbudget.ui.components.AddCategoryDialog
import com.example.kangbudget.ui.components.EditInitialBalanceDialog
import com.example.kangbudget.ui.components.ExpenseCategoryCard
import com.example.kangbudget.ui.components.IncomeCategoryCard
import com.example.kangbudget.ui.components.StatTile
import com.example.kangbudget.ui.util.OPEN_INCOME_COLOR
import com.example.kangbudget.ui.util.expenseProgressColor
import com.example.kangbudget.ui.util.formatAmount
import com.example.kangbudget.ui.util.formatPercent
import com.example.kangbudget.ui.util.privacyBlur
import com.example.kangbudget.util.InsightsData
import com.example.kangbudget.util.previousMonthDisplayName
import java.time.LocalDateTime

private val EXPENSE_RED = Color(0xFFE74C3C)
private val INCOME_GREEN = OPEN_INCOME_COLOR

@Composable
fun HomeScreen(
    budget: Budget?,
    monthId: String,
    insights: InsightsData,
    onOpenCategory: (Category) -> Unit,
    onEditCategory: (Category, String, Double) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onQuickAddTransaction: (Category, Double, String, LocalDateTime) -> Unit,
    onCreateCategory: (Category) -> Unit,
    onEditInitialBalance: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var addDialogType by remember { mutableStateOf<String?>(null) }
    var showEditBalanceDialog by remember { mutableStateOf(false) }

    val budgetUsedPercent = if (insights.totalBudgeted > 0) insights.totalExpenditure / insights.totalBudgeted * 100 else 0.0

    val statTiles = listOf<@Composable () -> Unit>(
        { StatTile("Left to spend", formatAmount(insights.remainingToSpend), Modifier.aspectRatio(1.3f), valueColor = EXPENSE_RED) },
        {
            StatTile(
                "Total spent",
                "${formatAmount(insights.totalExpenditure)} / ${formatAmount(insights.totalBudgeted)}",
                Modifier.aspectRatio(1.3f),
                valueColor = EXPENSE_RED
            )
        },
        { StatTile("Total income", formatAmount(insights.totalIncome), Modifier.aspectRatio(1.3f), valueColor = INCOME_GREEN) },
        {
            StatTile(
                "Budget used",
                formatPercent(budgetUsedPercent),
                Modifier.aspectRatio(1.3f),
                valueColor = expenseProgressColor(budgetUsedPercent)
            )
        },
        { StatTile("Days left", insights.daysLeft.toString(), Modifier.aspectRatio(1.3f)) },
        { StatTile("Daily avg", formatAmount(insights.dailyAverageSpend), Modifier.aspectRatio(1.3f)) },
        {
            TopCategoriesTile(
                topIncomeName = insights.topEarningCategory?.category?.name,
                topExpenseName = insights.topSpendingCategory?.category?.name,
                modifier = Modifier.aspectRatio(1.3f)
            )
        },
        { StatTile("Saved", formatAmount(insights.remainingToSpend.coerceAtLeast(0.0)), Modifier.aspectRatio(1.3f), valueColor = INCOME_GREEN) },
        {
            StatTile(
                "Provisional balance",
                formatAmount(insights.provisionalBalance),
                Modifier.aspectRatio(1.3f),
                valueColor = if (insights.provisionalBalance >= 0) INCOME_GREEN else EXPENSE_RED
            )
        }
    )

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.padding(top = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = { showEditBalanceDialog = true }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Balance from ${previousMonthDisplayName(monthId)}   KES ",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = formatAmount(budget?.initialBalance ?: 0.0),
                        modifier = Modifier.privacyBlur(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        items(statTiles.chunked(3)) { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowTiles.forEach { tile ->
                    Box(modifier = Modifier.weight(1f)) { tile() }
                }
            }
        }

        item {
            SectionHeader(
                title = "Income",
                total = formatAmount(insights.totalIncome),
                totalColor = INCOME_GREEN,
                onAddCategory = { addDialogType = CategoryType.INCOME }
            )
        }
        items(insights.incomeBreakdown, key = { it.category.id }) { earning ->
            IncomeCategoryCard(
                earning = earning,
                onClick = { onOpenCategory(earning.category) },
                onEditConfirmed = { name, goal -> onEditCategory(earning.category, name, goal) },
                onDeleteConfirmed = { onDeleteCategory(earning.category) },
                onQuickAdd = { amount, description, dateTime -> onQuickAddTransaction(earning.category, amount, description, dateTime) }
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SectionHeader(
                title = "Expenses",
                total = "${formatAmount(insights.totalExpenditure)} / ${formatAmount(insights.totalBudgeted)}",
                totalColor = EXPENSE_RED,
                onAddCategory = { addDialogType = CategoryType.EXPENSE }
            )
        }
        items(insights.expenseBreakdown, key = { it.category.id }) { spend ->
            ExpenseCategoryCard(
                spend = spend,
                onClick = { onOpenCategory(spend.category) },
                onEditConfirmed = { name, goal -> onEditCategory(spend.category, name, goal) },
                onDeleteConfirmed = { onDeleteCategory(spend.category) },
                onQuickAdd = { amount, description, dateTime -> onQuickAddTransaction(spend.category, amount, description, dateTime) }
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
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

    if (showEditBalanceDialog) {
        EditInitialBalanceDialog(
            currentBalance = budget?.initialBalance ?: 0.0,
            onDismiss = { showEditBalanceDialog = false },
            onConfirm = { newBalance ->
                onEditInitialBalance(newBalance)
                showEditBalanceDialog = false
            }
        )
    }
}

/**
 * Double-sided top-categories block: income row (green) on top,
 * expense row (red) underneath.
 */
@Composable
private fun TopCategoriesTile(
    topIncomeName: String?,
    topExpenseName: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Top categories",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = topIncomeName ?: "—",
                color = INCOME_GREEN,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = topExpenseName ?: "—",
                color = EXPENSE_RED,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    total: String,
    totalColor: Color,
    onAddCategory: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = total,
                modifier = Modifier.privacyBlur(),
                color = totalColor,
                style = MaterialTheme.typography.titleMedium
            )
        }
        TextButton(onClick = onAddCategory) { Text("+ Add category") }
    }
}
