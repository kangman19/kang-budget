package com.example.kangbudget.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode

/** Strict Amount-field filter: digits with at most one decimal point (empty allowed while typing). */
internal val AMOUNT_INPUT_PATTERN = Regex("^\\d*\\.?\\d*$")

private val KEYPAD_ROWS = listOf(
    listOf("C", "DEL", "/", "*"),
    listOf("7", "8", "9", "-"),
    listOf("4", "5", "6", "+"),
    listOf("1", "2", "3", "."),
    listOf("0")
)

/**
 * Floating micro-calculator for the transaction Amount field. "Save" evaluates the
 * expression and hands the plain numeric result back via [onSave]; "Cancel" dismisses
 * without touching the parent field.
 */
@Composable
fun TransactionCalculatorDialog(
    initialExpression: String,
    onDismiss: () -> Unit,
    onSave: (result: String) -> Unit
) {
    var expression by remember { mutableStateOf(initialExpression) }
    var showError by remember { mutableStateOf(false) }

    val liveResult = evaluateExpression(expression)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calculator") },
        text = {
            Column {
                // Digital readout: current expression plus a live running result when valid.
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = expression.ifEmpty { "0" },
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        )
                        Text(
                            text = when {
                                showError -> "Invalid expression"
                                liveResult != null && expression.isNotEmpty() -> "= ${formatCalculatorResult(liveResult)}"
                                else -> " "
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (showError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                KEYPAD_ROWS.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { key ->
                            OutlinedButton(
                                onClick = {
                                    showError = false
                                    expression = when (key) {
                                        "C" -> ""
                                        "DEL" -> expression.dropLast(1)
                                        else -> expression + key
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text(key, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val result = evaluateExpression(expression)
                    if (result != null) {
                        onSave(formatCalculatorResult(result))
                    } else {
                        showError = true
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Formats an evaluated result as a plain, human-editable numeric string:
 * rounded to 2 decimal places with trailing zeros stripped (e.g. 4000.0 → "4000").
 */
fun formatCalculatorResult(value: Double): String =
    BigDecimal.valueOf(value)
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

/**
 * Safely evaluates a basic arithmetic expression (+, -, *, / with standard precedence
 * and unary sign). Returns null — never throws — for malformed syntax, division by
 * zero, or non-finite results.
 */
fun evaluateExpression(expression: String): Double? {
    if (expression.isBlank()) return null
    val result = ExpressionParser(expression).parse() ?: return null
    return if (result.isFinite()) result else null
}

private class ExpressionParser(private val text: String) {
    private var pos = 0

    fun parse(): Double? {
        val value = parseExpression() ?: return null
        return if (pos == text.length) value else null
    }

    // expression := term (('+' | '-') term)*
    private fun parseExpression(): Double? {
        var left = parseTerm() ?: return null
        while (true) {
            when (peek()) {
                '+' -> { pos++; left += parseTerm() ?: return null }
                '-' -> { pos++; left -= parseTerm() ?: return null }
                else -> return left
            }
        }
    }

    // term := factor (('*' | '/') factor)*
    private fun parseTerm(): Double? {
        var left = parseFactor() ?: return null
        while (true) {
            when (peek()) {
                '*' -> { pos++; left *= parseFactor() ?: return null }
                '/' -> {
                    pos++
                    val divisor = parseFactor() ?: return null
                    if (divisor == 0.0) return null
                    left /= divisor
                }
                else -> return left
            }
        }
    }

    // factor := ('+' | '-') factor | number
    private fun parseFactor(): Double? = when (peek()) {
        '-' -> { pos++; parseFactor()?.let { -it } }
        '+' -> { pos++; parseFactor() }
        else -> parseNumber()
    }

    private fun parseNumber(): Double? {
        val start = pos
        var seenDot = false
        while (pos < text.length && (text[pos].isDigit() || (text[pos] == '.' && !seenDot))) {
            if (text[pos] == '.') seenDot = true
            pos++
        }
        if (pos == start) return null
        return text.substring(start, pos).toDoubleOrNull()
    }

    private fun peek(): Char? = text.getOrNull(pos)
}
