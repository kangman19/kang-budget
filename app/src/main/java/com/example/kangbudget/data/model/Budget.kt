package com.example.kangbudget.data.model

import com.google.firebase.Timestamp

data class Budget(
    val id: String = "",
    val name: String = "",
    val initialBalance: Double = 0.0,
    val createdAt: Timestamp = Timestamp.now()
)
