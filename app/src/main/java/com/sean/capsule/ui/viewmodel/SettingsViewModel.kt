package com.sean.capsule.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sean.capsule.data.local.HistoryEntry
import com.sean.capsule.data.local.SettingsRepository
import com.sean.capsule.data.remote.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

enum class ServerOption(val displayName: String, val baseUrl: String?) {
    Default("Default (https://send.withcapsule.dev)", "https://send.withcapsule.dev"),
    Custom("Custom", null)
}

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val onboardingCompleted: StateFlow<Boolean> = repository.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hapticsEnabled: StateFlow<Boolean> = repository.hapticsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val downloadDirUri: StateFlow<String?> = repository.downloadDirUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val themeMode: StateFlow<ThemeMode> = repository.themeMode
        .map { mode -> 
            try { ThemeMode.valueOf(mode) } catch (e: Exception) { ThemeMode.SYSTEM }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val history: StateFlow<List<HistoryEntry>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _serverOption = MutableStateFlow("Default")
    val serverOption: StateFlow<String> = _serverOption.asStateFlow()

    private val _customUrl = MutableStateFlow("")
    val customUrl: StateFlow<String> = _customUrl.asStateFlow()

    private val _customProtocolIndex = MutableStateFlow(0)
    val customProtocolIndex: StateFlow<Int> = _customProtocolIndex.asStateFlow()

    private val protocols = listOf("https://", "http://")

    val effectiveBaseUrl: StateFlow<String> = kotlinx.coroutines.flow.combine(
        repository.serverOption,
        repository.customUrl,
        repository.customProtocolIndex
    ) { option, url, protocolIndex ->
        if (option == ServerOption.Default.name) {
            ServerOption.Default.baseUrl!!
        } else {
            "${protocols.getOrElse(protocolIndex) { "https://" }}$url"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServerOption.Default.baseUrl!!)

    private val _pingResponse = MutableStateFlow<String?>(null)
    val pingResponse: StateFlow<String?> = _pingResponse.asStateFlow()

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    init {
        viewModelScope.launch {
            _serverOption.value = repository.serverOption.first()
            _customUrl.value = repository.customUrl.first()
            _customProtocolIndex.value = repository.customProtocolIndex.first()
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateHapticsEnabled(enabled)
        }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch {
            repository.updateOnboardingCompleted(completed)
        }
    }

    fun setDownloadDirUri(uri: String?) {
        viewModelScope.launch {
            repository.updateDownloadDirUri(uri)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.updateThemeMode(mode.name)
        }
    }

    fun updateServerOption(option: String) {
        _serverOption.value = option
        clearPingResponse()

        if (option == ServerOption.Default.name) {
            viewModelScope.launch {
                repository.updateServerOption(option)
            }
        }
    }

    fun updateCustomUrl(url: String) {
        _customUrl.value = url
        clearPingResponse()
    }

    fun updateCustomProtocolIndex(index: Int) {
        _customProtocolIndex.value = index
        clearPingResponse()
    }

    fun saveServerConfig() {
        viewModelScope.launch {
            val isDefault = _serverOption.value == ServerOption.Default.name
            repository.updateServerOption(_serverOption.value)
            if (!isDefault) {
                repository.updateCustomUrl(_customUrl.value)
                repository.updateCustomProtocolIndex(_customProtocolIndex.value)
            }
        }
    }

    fun saveAndPingServer() {
        viewModelScope.launch {
            saveServerConfig()

            val isDefault = _serverOption.value == ServerOption.Default.name
            val url = if (isDefault) {
                ServerOption.Default.baseUrl!!
            } else {
                "${protocols.getOrElse(_customProtocolIndex.value) { "https://" }}${_customUrl.value}"
            }
            pingServer(url)
        }
    }

    private fun pingServer(baseUrl: String) {
        viewModelScope.launch {
            _isPinging.value = true
            _pingResponse.value = null
            
            try {
                val okHttpClient = OkHttpClient.Builder()
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl.let { if (it.endsWith("/")) it else "$it/" })
                    .client(okHttpClient)
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .build()

                val apiService = retrofit.create(ApiService::class.java)
                val response = apiService.ping()
                _pingResponse.value = response
            } catch (e: Exception) {
                _pingResponse.value = "Error: ${e.message}"
            } finally {
                _isPinging.value = false
            }
        }
    }
    
    fun clearPingResponse() {
        _pingResponse.value = null
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun removeHistoryItem(fileId: String) {
        viewModelScope.launch {
            repository.removeHistoryEntry(fileId)
        }
    }

    fun deleteFileFromServer(fileId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val okHttpClient = OkHttpClient.Builder()
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.HEADERS
                    })
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(effectiveBaseUrl.value.let { if (it.endsWith("/")) it else "$it/" })
                    .client(okHttpClient)
                    .build()

                val apiService = retrofit.create(ApiService::class.java)
                val response = apiService.deleteFile(fileId)

                if (response.isSuccessful) {
                    repository.removeHistoryEntry(fileId)
                    onSuccess()
                } else {
                    val error = response.errorBody()?.string() ?: "Server error ${response.code()}"
                    onError(error)
                }
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }
    }
}
