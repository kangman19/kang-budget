package com.example.kangbudget

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.kangbudget.data.repository.BudgetRepository
import com.example.kangbudget.ui.screens.AppRoot
import com.example.kangbudget.ui.theme.KangBudgetTheme
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KangBudgetTheme {
                AppRoot()
            }
        }
    }

    /**
     * Retry anything still queued for the Sheet every time the app comes to the foreground.
     *
     * Previously the outbox was only ever replayed once, from `loadWebhookConfig()` at app
     * start, so an op that failed after that point — the phone was on a captive portal, the
     * Apps Script deployment was briefly down — stayed queued until the process was killed and
     * relaunched. Resume is the natural retry point: it is when the network has most likely
     * changed. The flush waits for the config internally and is serialized against the startup
     * one, so a resume during launch is harmless.
     */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            runCatching { BudgetRepository().flushOutbox() }
                .onFailure { Log.w(TAG, "Foreground outbox flush failed", it) }
        }
    }
}
