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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kangbudget.data.model.CategoryType
import com.example.kangbudget.ui.components.DonutChart
import com.example.kangbudget.ui.components.DonutSlice
import com.example.kangbudget.ui.components.LegendRow
import com.example.kangbudget.ui.components.StatTile
import com.example.kangbudget.ui.util.buildCategoryColorMap
import com.example.kangbudget.ui.util.formatAmount
import com.example.kangbudget.ui.util.formatPercent
import com.example.kangbudget.util.InsightsData
import com.example.kangbudget.util.monthIdToDisplayName

private val EXPENSE_RED = Color(0xFFE74C3C)
private val INCOME_GREEN = Color(0xFF2ECC71)

@Composable
fun InsightsScreen(
    monthId: String,
    insights: InsightsData,
    modifier: Modifier = Modifier
) {
    val expenseColors = buildCategoryColorMap(insights.expenseBreakdown.map { it.category.name })
    val incomeColors = buildCategoryColorMap(insights.incomeBreakdown.map { it.category.name })

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Spend by category — ${monthIdToDisplayName(monthId)}",
            style = MaterialTheme.typography.titleMedium
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { StatTile("Income spent", formatPercent(insights.incomeSpentPercent), Modifier.aspectRatio(1.3f)) }
            item { StatTile("Remaining to spend", formatAmount(insights.remainingToSpend), Modifier.aspectRatio(1.3f)) }
            item { StatTile("Net cash", formatAmount(insights.netCash), Modifier.aspectRatio(1.3f)) }
            item { StatTile("Total budgeted", formatAmount(insights.totalBudgeted), Modifier.aspectRatio(1.3f)) }
            item {
                StatTile(
                    "Provisional balance",
                    formatAmount(insights.provisionalBalance),
                    Modifier.aspectRatio(1.3f),
                    valueColor = if (insights.provisionalBalance >= 0) INCOME_GREEN else EXPENSE_RED
                )
            }
            item { StatTile("Total expenditure", formatAmount(insights.totalExpenditure), Modifier.aspectRatio(1.3f), valueColor = EXPENSE_RED) }
            item { StatTile("Days left", insights.daysLeft.toString(), Modifier.aspectRatio(1.3f)) }
            item {
                TopCategoryTile(
                    topSpendName = insights.topSpendingCategory?.category?.name,
                    topEarnName = insights.topEarningCategory?.category?.name
                )
            }
            item { StatTile("Daily avg spend", formatAmount(insights.dailyAverageSpend), Modifier.aspectRatio(1.3f)) }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        DonutSection(
            title = "Total spent",
            total = insights.totalExpenditure,
            slices = insights.expenseBreakdown.map { spend ->
                DonutSlice(spend.category.name, spend.spent, expenseColors.getValue(spend.category.name))
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        DonutSection(
            title = "Total earned",
            total = insights.totalIncome,
            slices = insights.incomeBreakdown.map { earning ->
                DonutSlice(earning.category.name, earning.earned, incomeColors.getValue(earning.category.name))
            }
        )
    }
}

@Composable
private fun TopCategoryTile(topSpendName: String?, topEarnName: String?) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.aspectRatio(1.3f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Top category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(topSpendName ?: "—", style = MaterialTheme.typography.bodyLarge, color = EXPENSE_RED)
            Text(topEarnName ?: "—", style = MaterialTheme.typography.bodyLarge, color = INCOME_GREEN)
        }
    }
}

@Composable
private fun DonutSection(title: String, total: Double, slices: List<DonutSlice>) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(slices = slices, centerLabel = title, centerValue = formatAmount(total))
            Column(modifier = Modifier.padding(start = 16.dp).fillMaxWidth()) {
                slices.forEach { slice ->
                    LegendRow(
                        color = slice.color,
                        label = slice.label,
                        percent = if (total > 0) slice.value / total * 100 else 0.0,
                        amount = slice.value
                    )
                }
            }
        }
    }
}
