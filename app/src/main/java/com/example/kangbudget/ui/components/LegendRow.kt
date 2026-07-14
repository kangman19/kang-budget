package com.example.kangbudget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kangbudget.ui.util.formatAmount
import com.example.kangbudget.ui.util.formatPercent
import com.example.kangbudget.ui.util.privacyBlur

@Composable
fun LegendRow(
    color: Color,
    label: String,
    percent: Double,
    amount: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
            Text(text = label, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = "${formatPercent(percent)} · ${formatAmount(amount)}",
            modifier = Modifier.privacyBlur(radius = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
