package com.sean.capsule.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Serializable
data class HistoryEntry(
    val id: String,
    val fileName: String,
    val timestamp: Long,
    val isUpload: Boolean,
    val isEncrypted: Boolean,
    val url: String? = null
)

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val SERVER_OPTION = stringPreferencesKey("server_option")
        val CUSTOM_URL = stringPreferencesKey("custom_url")
        val CUSTOM_PROTOCOL_INDEX = intPreferencesKey("custom_protocol_index")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DOWNLOAD_DIR_URI = stringPreferencesKey("download_dir_uri")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HISTORY_JSON = stringPreferencesKey("history_json")
    }

    val history: Flow<List<HistoryEntry>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[PreferencesKeys.HISTORY_JSON] ?: "[]"
            try {
                Json.decodeFromString<List<HistoryEntry>>(json)
            } catch (e: Exception) {
                emptyList()
            }
        }

    val themeMode: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.THEME_MODE] ?: "SYSTEM"
        }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false
        }

    val downloadDirUri: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DOWNLOAD_DIR_URI]
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

    suspend fun updateDownloadDirUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(PreferencesKeys.DOWNLOAD_DIR_URI)
            } else {
                preferences[PreferencesKeys.DOWNLOAD_DIR_URI] = uri
            }
        }
    }

    suspend fun updateThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode
        }
    }

    suspend fun addHistoryEntry(entry: HistoryEntry) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[PreferencesKeys.HISTORY_JSON] ?: "[]"
            val currentList = try {
                Json.decodeFromString<List<HistoryEntry>>(currentJson)
            } catch (e: Exception) {
                emptyList()
            }
            
            val newList = (listOf(entry) + currentList).take(15)
            preferences[PreferencesKeys.HISTORY_JSON] = Json.encodeToString(newList)
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.HISTORY_JSON)
        }
    }
}
