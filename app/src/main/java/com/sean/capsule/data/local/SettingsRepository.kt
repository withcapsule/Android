package com.sean.capsule.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val SERVER_OPTION = stringPreferencesKey("server_option")
        val CUSTOM_URL = stringPreferencesKey("custom_url")
        val CUSTOM_PROTOCOL_INDEX = intPreferencesKey("custom_protocol_index")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false
        }

    val hapticsEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.HAPTICS_ENABLED] ?: true
        }

    val serverOption: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SERVER_OPTION] ?: "Default"
        }

    val customUrl: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.CUSTOM_URL] ?: ""
        }

    val customProtocolIndex: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.CUSTOM_PROTOCOL_INDEX] ?: 0
        }

    suspend fun updateHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun updateServerOption(option: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SERVER_OPTION] = option
        }
    }

    suspend fun updateCustomUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_URL] = url
        }
    }

    suspend fun updateCustomProtocolIndex(index: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_PROTOCOL_INDEX] = index
        }
    }

    suspend fun updateOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] = completed
        }
    }
}
