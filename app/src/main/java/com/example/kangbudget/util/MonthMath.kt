package com.example.kangbudget.util

import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val MONTH_ID_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM")

fun currentMonthId(): String = YearMonth.now().format(MONTH_ID_FORMAT)

fun monthIdToDisplayName(monthId: String): String {
    val yearMonth = YearMonth.parse(monthId, MONTH_ID_FORMAT)
    return yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
}

/** Month name one month before the active session month, e.g. "2026-07" → "June". */
fun previousMonthDisplayName(monthId: String): String =
    YearMonth.parse(monthId, MONTH_ID_FORMAT)
        .minusMonths(1)
        .format(DateTimeFormatter.ofPattern("MMMM"))

fun daysInMonth(monthId: String): Int = YearMonth.parse(monthId, MONTH_ID_FORMAT).lengthOfMonth()

fun daysElapsed(monthId: String): Int {
    val target = YearMonth.parse(monthId, MONTH_ID_FORMAT)
    val now = YearMonth.now()
    return when {
        target < now -> target.lengthOfMonth()
        target > now -> 0
        else -> java.time.LocalDate.now().dayOfMonth
    }
}

fun daysRemaining(monthId: String): Int {
    val target = YearMonth.parse(monthId, MONTH_ID_FORMAT)
    val now = YearMonth.now()
    return when {
        target < now -> 0
        target > now -> target.lengthOfMonth()
        else -> target.lengthOfMonth() - java.time.LocalDate.now().dayOfMonth
    }
}
