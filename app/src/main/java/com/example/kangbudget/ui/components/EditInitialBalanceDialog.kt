package com.example.kangbudget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = balanceText,
                        onValueChange = { input ->
                            if (input.isEmpty() || AMOUNT_INPUT_PATTERN.matches(input)) {
                                balanceText = input
                            }
                        },
                        label = { Text("Initial balance") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { showCalculator = true },
                        modifier = Modifier.fillMaxWidth(0.3f)
                    ) {
                        Text("Calc")
                    }
                }
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
