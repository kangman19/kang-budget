package com.example.kangbudget.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.privacyDataStore by preferencesDataStore(name = "privacy_prefs")

private val AMOUNTS_HIDDEN_KEY = booleanPreferencesKey("amounts_hidden")

class PrivacyPreferences(private val context: Context) {

    val amountsHidden: Flow<Boolean> = context.privacyDataStore.data
        .map { it[AMOUNTS_HIDDEN_KEY] ?: false }

    suspend fun setAmountsHidden(hidden: Boolean) {
        context.privacyDataStore.edit { it[AMOUNTS_HIDDEN_KEY] = hidden }
    }
}
