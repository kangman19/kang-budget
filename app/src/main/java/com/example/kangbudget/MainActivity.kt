package com.example.kangbudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.kangbudget.ui.screens.MainScreen
import com.example.kangbudget.ui.theme.KangBudgetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KangBudgetTheme {
                MainScreen()
            }
        }
    }
}
