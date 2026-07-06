package com.example.kangbudget.data.model

import com.google.firebase.Timestamp

data class ActivityLogEntry(
    val transactionId: String,
    val monthId: String,
    val categoryId: String,
    val categoryName: String,
    val categoryType: String,
    val amount: Double,
    val description: String,
    val date: String,
    val time: String,
    val timestamp: Timestamp
)
