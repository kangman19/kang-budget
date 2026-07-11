package com.example.kangbudget.util

import com.example.kangbudget.data.model.Budget
import com.example.kangbudget.data.model.Category
import com.example.kangbudget.data.model.CategoryType
import com.example.kangbudget.data.model.IncomeType
import com.example.kangbudget.data.model.Transaction

data class CategorySpend(val category: Category, val spent: Double) {
    val percentUsed: Double get() = if (category.targetGoal > 0) (spent / category.targetGoal) * 100 else 0.0
}

data class CategoryEarning(val category: Category, val earned: Double) {
    val percentOfGoal: Double get() = if (category.targetGoal > 0) (earned / category.targetGoal) * 100 else 0.0
}

data class InsightsData(
    val totalIncome: Double,
    val totalBudgeted: Double,
    val totalExpenditure: Double,
    val incomeSpentPercent: Double,
    val remainingToSpend: Double,
    val netCash: Double,
    val provisionalBalance: Double,
    val daysLeft: Int,
    val dailyAverageSpend: Double,
    val topSpendingCategory: CategorySpend?,
    val topEarningCategory: CategoryEarning?,
    val expenseBreakdown: List<CategorySpend>,
    val incomeBreakdown: List<CategoryEarning>
)

fun calculateInsights(
    budget: Budget?,
    categories: List<Category>,
    transactionsByCategory: Map<String, List<Transaction>>,
    monthId: String
): InsightsData {
    val expenseBreakdown = categories
        .filter { it.type == CategoryType.EXPENSE }
        .map { category -> CategorySpend(category, transactionsByCategory[category.id].orEmpty().sumOf { it.amount }) }

    val incomeBreakdown = categories
        .filter { it.type == CategoryType.INCOME }
        .map { category ->
            val earned = if (category.incomeType == IncomeType.FIXED) {
                category.targetGoal
            } else {
                transactionsByCategory[category.id].orEmpty().sumOf { it.amount }
            }
            CategoryEarning(category, earned)
        }

    val totalExpenditure = expenseBreakdown.sumOf { it.spent }
    val totalIncome = incomeBreakdown.sumOf { it.earned }
    val totalBudgeted = expenseBreakdown.sumOf { it.category.targetGoal }
    val initialBalance = budget?.initialBalance ?: 0.0
    val elapsedDays = daysElapsed(monthId).coerceAtLeast(1)

    return InsightsData(
        totalIncome = totalIncome,
        totalBudgeted = totalBudgeted,
        totalExpenditure = totalExpenditure,
        incomeSpentPercent = if (totalIncome > 0) (totalExpenditure / totalIncome) * 100 else 0.0,
        remainingToSpend = totalBudgeted - totalExpenditure,
        netCash = initialBalance + totalIncome,
        provisionalBalance = totalIncome - totalBudgeted,
        daysLeft = daysRemaining(monthId),
        dailyAverageSpend = totalExpenditure / elapsedDays,
        topSpendingCategory = expenseBreakdown.filter { it.spent > 0 }.maxByOrNull { it.spent },
        topEarningCategory = incomeBreakdown.filter { it.earned > 0 }.maxByOrNull { it.earned },
        expenseBreakdown = expenseBreakdown,
        incomeBreakdown = incomeBreakdown
    )
}
