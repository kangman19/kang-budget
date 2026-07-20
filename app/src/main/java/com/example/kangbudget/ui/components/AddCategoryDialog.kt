package com.example.kangbudget.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import com.example.kangbudget.data.model.BudgetType
import com.example.kangbudget.data.model.Category
import com.example.kangbudget.data.model.CategoryType
import com.example.kangbudget.data.model.IncomeType

@Composable
fun AddCategoryDialog(
    categoryType: String,
    onDismiss: () -> Unit,
    onConfirm: (Category) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var targetGoalText by remember { mutableStateOf("") }
    var incomeType by remember { mutableStateOf(IncomeType.FIXED) }
    var budgetType by remember { mutableStateOf(BudgetType.NORMAL) }
    var showCalculator by remember { mutableStateOf(false) }

    val showGoalField = categoryType == CategoryType.EXPENSE || incomeType == IncomeType.GOAL || incomeType == IncomeType.FIXED

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (categoryType == CategoryType.EXPENSE) "Add expense category" else "Add income category") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                if (categoryType == CategoryType.INCOME) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        listOf(IncomeType.FIXED, IncomeType.OPEN, IncomeType.GOAL).forEach { option ->
                            FilterChip(
                                selected = incomeType == option,
                                onClick = { incomeType = option },
                                label = { Text(option.replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }
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
                                    categoryType == CategoryType.EXPENSE -> "Budget target"
                                    incomeType == IncomeType.FIXED -> "Fixed amount"
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
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    listOf(BudgetType.NORMAL, BudgetType.EXCEL).forEach { option ->
                        FilterChip(
                            selected = budgetType == option,
                            onClick = { budgetType = option },
                            label = { Text(option.replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            Category(
                                name = name.trim(),
                                type = categoryType,
                                incomeType = if (categoryType == CategoryType.INCOME) incomeType else IncomeType.FIXED,
                                targetGoal = targetGoalText.toDoubleOrNull() ?: 0.0,
                                archived = false,
                                budgetType = budgetType
                            )
                        )
                    }
                }
            ) { Text("Add") }
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
