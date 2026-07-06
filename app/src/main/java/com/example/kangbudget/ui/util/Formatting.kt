package com.example.kangbudget.ui.util

import java.text.DecimalFormat

private val AMOUNT_FORMAT = DecimalFormat("#,##0")

fun formatAmount(amount: Double): String = AMOUNT_FORMAT.format(amount)

fun formatPercent(percent: Double): String = "${percent.toInt()}%"
