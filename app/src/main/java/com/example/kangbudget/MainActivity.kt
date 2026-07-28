package com.example.kangbudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.kangbudget.data.repository.BudgetRepository
import com.example.kangbudget.ui.screens.MainScreen
import com.example.kangbudget.ui.theme.KangBudgetTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Load the Sheets webhook endpoint and replay any queued offline ops. Reads from
        // Firestore's local cache, so this succeeds even on a cold, offline launch.
        lifecycleScope.launch {
            BudgetRepository().loadWebhookConfig()
        }
        setContent {
            KangBudgetTheme {
                MainScreen()
            }
        }
    }
}
