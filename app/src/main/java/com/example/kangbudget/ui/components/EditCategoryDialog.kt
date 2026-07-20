package com.example.kangbudget.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kangbudget.data.model.Category
import com.example.kangbudget.data.model.CategoryType
import com.example.kangbudget.data.model.IncomeType

@Composable
fun EditCategoryDialog(
    category: Category,
    onDismiss: () -> Unit,
    onConfirm: (name: String, targetGoal: Double) -> Unit
) {
    var name by remember { mutableStateOf(category.name) }
    var targetGoalText by remember { mutableStateOf(if (category.targetGoal > 0) category.targetGoal.toString() else "") }
    var showCalculator by remember { mutableStateOf(false) }

    val showGoalField = category.type == CategoryType.EXPENSE ||
        category.incomeType == IncomeType.GOAL ||
        category.incomeType == IncomeType.FIXED

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit category") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                if (showGoalField) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetGoalText,
                        // Strict numeric validation: digits and at most one decimal point.
                        onValueChange = { input ->
                            if (input.matches(AMOUNT_INPUT_PATTERN)) targetGoalText = input
                        },
                        label = {
                            Text(
                                when {
                                    category.type == CategoryType.EXPENSE -> "Budget target"
                                    category.incomeType == IncomeType.FIXED -> "Fixed amount"
                                    else -> "Goal"
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            IconButton(onClick = { showCalculator = true }) {
                                Icon(Icons.Filled.Calculate, contentDescription = "Open calculator")
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(name.trim(), targetGoalText.toDoubleOrNull() ?: 0.0)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showCalculator) {
        TransactionCalculatorDialog(
            initialExpression = targetGoalText,
            onDismiss = { showCalculator = false },
            onSave = { result ->
                targetGoalText = result
                showCalculator = false
            }
        )
    }
}
