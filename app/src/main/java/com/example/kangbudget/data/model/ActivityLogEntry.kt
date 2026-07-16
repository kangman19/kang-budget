package com.example.kangbudget.data.model

data class ActivityLogEntry(
    val transactionId: String,
    val monthId: String,
    val categoryId: String,
    val categoryName: String,
    val categoryType: String,
    val amount: Double,
    val description: String,
    /** Explicit user-logged date the transaction was recorded for (yyyy-MM-dd). */
    val date: String,
    /** Explicit user-logged time the transaction was recorded for (HH:mm). */
    val time: String
)
