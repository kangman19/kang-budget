package com.example.kangbudget.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kangbudget.ui.components.DonutChart
import com.example.kangbudget.ui.components.DonutSlice
import com.example.kangbudget.ui.components.LegendRow
import com.example.kangbudget.ui.util.buildCategoryColorMap
import com.example.kangbudget.ui.util.formatAmount
import com.example.kangbudget.util.InsightsData
import com.example.kangbudget.util.monthIdToDisplayName

@Composable
fun InsightsScreen(
    monthId: String,
    insights: InsightsData,
    modifier: Modifier = Modifier
) {
    val expenseColors = buildCategoryColorMap(insights.expenseBreakdown.map { it.category.name })
    val incomeColors = buildCategoryColorMap(insights.incomeBreakdown.map { it.category.name })

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = monthIdToDisplayName(monthId),
            style = MaterialTheme.typography.titleMedium
        )

        DonutSection(
            header = "Income chart",
            title = "Total earned",
            total = insights.currentMonthIncome,
            slices = insights.incomeBreakdown.map { earning ->
                DonutSlice(earning.category.name, earning.earned, incomeColors.getValue(earning.category.name))
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        DonutSection(
            header = "Expenses chart",
            title = "Total spent",
            total = insights.totalExpenditure,
            slices = insights.expenseBreakdown.map { spend ->
                DonutSlice(spend.category.name, spend.spent, expenseColors.getValue(spend.category.name))
            }
        )
    }
}

@Composable
private fun DonutSection(header: String, title: String, total: Double, slices: List<DonutSlice>) {
    Column {
        Text(
            text = header,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
        )
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
