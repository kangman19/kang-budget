package com.example.kangbudget.data.model

import com.google.firebase.Timestamp

data class Transaction(
    val id: String = "",
    val amount: Double = 0.0,
    val description: String = "",
    val date: String = "",
    val time: String = "",
    val timestamp: Timestamp = Timestamp.now()
)
