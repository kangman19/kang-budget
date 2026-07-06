package com.example.kangbudget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kangbudget.data.model.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableCategoryCard(
    category: Category,
    onClick: () -> Unit,
    onEditConfirmed: (name: String, targetGoal: Double) -> Unit,
    onDeleteConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    cardContent: @Composable () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> showEditDialog = true
                SwipeToDismissBoxValue.EndToStart -> showDeleteDialog = true
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { SwipeGestureBackground(dismissState.dismissDirection) },
        content = {
            Surface(
                onClick = onClick,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                cardContent()
            }
        }
    )

    if (showEditDialog) {
        EditCategoryDialog(
            category = category,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, goal ->
                onEditConfirmed(name, goal)
                showEditDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        DeleteCategoryDialog(
            categoryName = category.name,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                onDeleteConfirmed()
                showDeleteDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeGestureBackground(direction: SwipeToDismissBoxValue?) {
    val (color, icon, alignment) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Triple(Color(0xFF3498DB), Icons.Filled.Edit, Alignment.CenterStart)
        SwipeToDismissBoxValue.EndToStart -> Triple(Color(0xFFE74C3C), Icons.Filled.Delete, Alignment.CenterEnd)
        else -> Triple(Color.Transparent, null, Alignment.Center)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        icon?.let { Icon(imageVector = it, contentDescription = null, tint = Color.White) }
    }
}
