package com.example.kangbudget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kangbudget.data.model.ActivityLogEntry
import com.example.kangbudget.data.model.CategoryType
import com.example.kangbudget.ui.util.formatAmount
import com.example.kangbudget.ui.util.privacyBlur
import com.example.kangbudget.util.monthIdToDisplayName

private val INCOME_GREEN = Color(0xFF2ECC71)
private val EXPENSE_RED = Color(0xFFE74C3C)

@Composable
fun ActivityLogDialog(
    activityLog: List<ActivityLogEntry>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Activity Log")
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                if (activityLog.isEmpty()) {
                    Text(
                        "No transactions logged yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn {
                        items(activityLog, key = { it.transactionId }) { entry ->
                            ActivityLogRow(entry)
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ActivityLogRow(entry: ActivityLogEntry) {
    val color = if (entry.categoryType == CategoryType.INCOME) INCOME_GREEN else EXPENSE_RED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.categoryName, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${entry.date} · ${entry.time} · ${monthIdToDisplayName(entry.monthId)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (entry.description.isNotBlank()) {
                Text(entry.description, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            formatAmount(entry.amount),
            modifier = Modifier.privacyBlur(),
            color = color,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
