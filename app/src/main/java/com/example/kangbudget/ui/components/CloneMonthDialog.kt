package com.example.kangbudget.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
fun CloneMonthDialog(
    sourceMonthId: String,
    onDismiss: () -> Unit,
    onConfirm: (targetMonthId: String, targetMonthName: String, initialBalance: Double) -> Unit
) {
    var targetMonthId by remember { mutableStateOf("") }
    var targetMonthName by remember { mutableStateOf("") }
    var initialBalanceText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clone $sourceMonthId") },
        text = {
            Column {
                Text("Copies all active categories into a new month with clean balances.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = targetMonthId,
                    onValueChange = { targetMonthId = it },
                    label = { Text("New month id (e.g. 2026-07)") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = targetMonthName,
                    onValueChange = { targetMonthName = it },
                    label = { Text("New month name (e.g. July 2026)") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = initialBalanceText,
                    onValueChange = { initialBalanceText = it },
                    label = { Text("Initial balance") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (targetMonthId.isNotBlank() && targetMonthName.isNotBlank()) {
                        onConfirm(targetMonthId.trim(), targetMonthName.trim(), initialBalanceText.toDoubleOrNull() ?: 0.0)
                    }
                }
            ) { Text("Clone") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
