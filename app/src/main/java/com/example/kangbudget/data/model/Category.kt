package com.example.kangbudget.data.model

object CategoryType {
    const val INCOME = "income"
    const val EXPENSE = "expense"
}

object IncomeType {
    const val FIXED = "fixed"
    const val OPEN = "open"
    const val GOAL = "goal"
}

object BudgetType {
    const val NORMAL = "normal"
    const val EXCEL = "excel"
}

data class Category(
    val id: String = "",
    val name: String = "",
    val type: String = CategoryType.EXPENSE,
    val incomeType: String = IncomeType.FIXED,
    val targetGoal: Double = 0.0,
    val isArchived: Boolean = false,
    val budgetType: String = BudgetType.NORMAL,
    val webhookIdentifier: String = ""
) {
    companion object {
        fun deriveWebhookIdentifier(categoryName: String): String =
            categoryName.trim().lowercase().replace(Regex("\\s+"), "-")
    }
}
