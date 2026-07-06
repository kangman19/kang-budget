package com.example.kangbudget.ui.util

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/** Green <= 40%, Yellow 41-100%, Red > 100% of budget used. */
fun expenseProgressColor(percentUsed: Double): Color = when {
    percentUsed <= 40.0 -> Color(0xFF2ECC71)
    percentUsed <= 100.0 -> Color(0xFFF1C40F)
    else -> Color(0xFFE74C3C)
}

/** Frosty cyan while filling toward a goal, solid green once the goal is met or exceeded. */
fun incomeGoalProgressColor(percentReached: Double): Color = when {
    percentReached >= 100.0 -> Color(0xFF2ECC71)
    else -> Color(0xFF7FDBFF)
}

val OPEN_INCOME_COLOR = Color(0xFF2ECC71)

private val ROYGBIV = listOf(
    Color(0xFFE74C3C), // Red
    Color(0xFFE67E22), // Orange
    Color(0xFFF1C40F), // Yellow
    Color(0xFF2ECC71), // Green
    Color(0xFF3498DB), // Blue
    Color(0xFF4B0082), // Indigo
    Color(0xFF8E44AD)  // Violet
)

/**
 * Maps category names to ROYGBIV colors in sequential order. Categories beyond the 7th
 * receive a randomly generated hex color guaranteed not to collide with colors already
 * registered in [existingColors].
 */
fun colorForCategoryIndex(index: Int, existingColors: Collection<Color>): Color {
    if (index < ROYGBIV.size) return ROYGBIV[index]
    var candidate: Color
    do {
        candidate = Color(
            red = Random.nextFloat(),
            green = Random.nextFloat(),
            blue = Random.nextFloat(),
            alpha = 1f
        )
    } while (existingColors.contains(candidate))
    return candidate
}

fun buildCategoryColorMap(categoryNames: List<String>): Map<String, Color> {
    val colorMap = LinkedHashMap<String, Color>()
    categoryNames.forEachIndexed { index, name ->
        colorMap[name] = colorForCategoryIndex(index, colorMap.values)
    }
    return colorMap
}
