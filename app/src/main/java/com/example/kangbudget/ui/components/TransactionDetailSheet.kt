package com.example.kangbudget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kangbudget.data.model.Category
import com.example.kangbudget.data.model.Transaction
import com.example.kangbudget.ui.util.formatAmount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    category: Category,
    transactions: List<Transaction>,
    onDismiss: () -> Unit,
    onUpdateTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (transactionId: String) -> Unit
) {
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = category.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${transactions.size} transactions logged",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                items(transactions, key = { it.id }) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        onEdit = { editingTransaction = transaction },
                        onDelete = { onDeleteTransaction(transaction.id) }
                    )
                }
            }
        }
    }

    editingTransaction?.let { transaction ->
        EditTransactionDialog(
            transaction = transaction,
            onDismiss = { editingTransaction = null },
            onConfirm = {
                onUpdateTransaction(it)
                editingTransaction = null
            }
        )
    }
}

@Composable
private fun TransactionRow(
    transaction: Transaction,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = transaction.description.ifBlank { "No description" }, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${transaction.date} · ${transaction.time}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = formatAmount(transaction.amount), style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit transaction") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete transaction") }
        }
    }
}
