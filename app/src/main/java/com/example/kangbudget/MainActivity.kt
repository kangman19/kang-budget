package com.example.kangbudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.kangbudget.data.repository.BudgetRepository
import com.example.kangbudget.ui.screens.MainScreen
import com.example.kangbudget.ui.theme.KangBudgetTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            // Firestore rules require an authenticated caller; this app has no login UI, so
            // every install gets its own anonymous identity, cached locally after the first
            // run. MainScreen's Firestore listeners must not attach before this resolves, or
            // their first snapshot gets permission-denied and the listener closes for good —
            // so setContent is deferred until sign-in completes (near-instant once cached).
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                runCatching { auth.signInAnonymously().await() }
            }
            // Load the Sheets webhook endpoint and replay any queued offline ops. Reads from
            // Firestore's local cache, so this succeeds even on a cold, offline launch.
            BudgetRepository().loadWebhookConfig()

            setContent {
                KangBudgetTheme {
                    MainScreen()
                }
            }
        }
    }
}
