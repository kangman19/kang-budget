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
    /** Previous month's carry-over compounded with this month's income — the "Total income" display node. */
    val totalIncome: Double,
    /** Income generated within the active month only: fixed expected targets + logged open/goal transactions. */
    val currentMonthIncome: Double,
    /** Ending balance carried over from the previous month. */
    val rolloverBalance: Double,
    val totalBudgeted: Double,
    val totalExpenditure: Double,
    val remainingToSpend: Double,
    /** Predictive runway: previous-month balance + (expected fixed income + logged open income) − total budgeted. May go negative. */
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
        .sortedByDescending { it.spent }

    // Fixed income counts at its expected monthly target; open/goal income counts from logged transactions.
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
        .sortedByDescending { it.earned }

    val totalExpenditure = expenseBreakdown.sumOf { it.spent }
    val totalBudgeted = expenseBreakdown.sumOf { it.category.targetGoal }
    val currentMonthIncome = incomeBreakdown.sumOf { it.earned }
    val rolloverBalance = budget?.initialBalance ?: 0.0
    val elapsedDays = daysElapsed(monthId).coerceAtLeast(1)

    return InsightsData(
        totalIncome = rolloverBalance + currentMonthIncome,
        currentMonthIncome = currentMonthIncome,
        rolloverBalance = rolloverBalance,
        totalBudgeted = totalBudgeted,
        totalExpenditure = totalExpenditure,
        remainingToSpend = totalBudgeted - totalExpenditure,
        // Adding an expense category grows totalBudgeted and immediately shrinks the runway;
        // logging income (or a bigger carry-over) immediately compounds it. Negatives flow through.
        provisionalBalance = rolloverBalance + currentMonthIncome - totalBudgeted,
        daysLeft = daysRemaining(monthId),
        dailyAverageSpend = totalExpenditure / elapsedDays,
        topSpendingCategory = expenseBreakdown.filter { it.spent > 0 }.maxByOrNull { it.spent },
        topEarningCategory = incomeBreakdown.filter { it.earned > 0 }.maxByOrNull { it.earned },
        expenseBreakdown = expenseBreakdown,
        incomeBreakdown = incomeBreakdown
    )
}
