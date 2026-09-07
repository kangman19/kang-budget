package com.example.kangbudget.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kangbudget.data.repository.BudgetRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "AppReadyViewModel"
private const val READY_TIMEOUT_MS = 10_000L

sealed interface AppReadyState {
    data object Loading : AppReadyState
    data object Ready : AppReadyState
    data object OfflineReady : AppReadyState
    data object Error : AppReadyState
}

class AppReadyViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<AppReadyState>(AppReadyState.Loading)
    val state: StateFlow<AppReadyState> = _state.asStateFlow()

    init {
        start()
    }

    fun retry() {
        _state.value = AppReadyState.Loading
        start()
    }

    private fun start() {
        viewModelScope.launch {
            // No point waiting out the timeout when there's no network at all: nothing will
            // ever connect, so drop straight to the cached/offline view instead of a blank spinner.
            if (!isNetworkAvailable()) {
                _state.value = AppReadyState.OfflineReady
                return@launch
            }
            val signedIn = withTimeoutOrNull(READY_TIMEOUT_MS) {
                val auth = FirebaseAuth.getInstance()
                // Sign-in and the (cache-first) webhook config load don't depend on each other,
                // so run them concurrently instead of waterfalling the config load behind auth.
                val authResult = async {
                    if (auth.currentUser == null) {
                        runCatching { auth.signInAnonymously().await() }
                            .onFailure { Log.w(TAG, "Anonymous sign-in failed", it) }
                    }
                    auth.currentUser != null
                }
                val configResult = async {
                    runCatching { BudgetRepository().loadWebhookConfig() }
                        .onFailure { Log.w(TAG, "loadWebhookConfig failed", it) }
                }
                val result = authResult.await()
                configResult.await()
                result
            }
            _state.value = when {
                signedIn == true -> AppReadyState.Ready
                isNetworkAvailable() -> AppReadyState.Error
                else -> AppReadyState.OfflineReady
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
