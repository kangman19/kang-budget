package com.example.kangbudget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun EditInitialBalanceDialog(
    currentBalance: Double,
    onDismiss: () -> Unit,
    onConfirm: (newBalance: Double) -> Unit
) {
    var balanceText by remember { mutableStateOf(if (currentBalance != 0.0) currentBalance.toString() else "") }
    var showCalculator by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit initial balance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = balanceText,
                    // Strict numeric validation: digits and at most one decimal point.
                    onValueChange = { input ->
                        if (input.isEmpty() || AMOUNT_INPUT_PATTERN.matches(input)) {
                            balanceText = input
                        }
                    },
                    label = { Text("Initial balance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        IconButton(onClick = { showCalculator = true }) {
                            Icon(Icons.Filled.Calculate, contentDescription = "Open calculator")
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(balanceText.toDoubleOrNull() ?: 0.0) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showCalculator) {
        TransactionCalculatorDialog(
            initialExpression = balanceText,
            onDismiss = { showCalculator = false },
            onSave = { result ->
                balanceText = result
                showCalculator = false
            }
        )
    }
}
