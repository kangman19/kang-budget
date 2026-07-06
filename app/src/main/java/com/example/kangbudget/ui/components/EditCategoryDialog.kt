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

    val showGoalField = category.type == CategoryType.EXPENSE || category.incomeType == IncomeType.GOAL

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
                        onValueChange = { targetGoalText = it },
                        label = { Text(if (category.type == CategoryType.EXPENSE) "Budget target" else "Goal") }
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
}
