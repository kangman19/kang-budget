package com.example.kangbudget.ui.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

// Design tokens for progress-state indication.
val ICY_CYAN = Color(0xFF00E5FF)
val EMERALD_GREEN = Color(0xFF00E676)
val VIVID_YELLOW = Color(0xFFFFEA00)
val DARK_ORANGE = Color(0xFFFF8C00)
val CRIMSON_RED = Color(0xFFDC143C)

/**
 * 4-tier expense threshold logic:
 * 0–39% standard layout color · 40–99% vivid yellow · exactly 100% dark orange · >100% crimson red.
 */
@Composable
@ReadOnlyComposable
fun expenseProgressColor(percentUsed: Double): Color = when {
    percentUsed < 40.0 -> MaterialTheme.colorScheme.primary
    percentUsed < 100.0 -> VIVID_YELLOW
    percentUsed == 100.0 -> DARK_ORANGE
    else -> CRIMSON_RED
}

/** Icy cyan at or above 100% of the target goal, emerald green while under 100%. */
fun incomeGoalProgressColor(percentReached: Double): Color =
    if (percentReached >= 100.0) ICY_CYAN else EMERALD_GREEN

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
