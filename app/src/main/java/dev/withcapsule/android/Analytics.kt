package dev.withcapsule.android

import android.content.Context
import dev.appoutlet.umami.Umami
import dev.appoutlet.umami.api.event
import dev.withcapsule.android.data.local.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AnalyticsManager(context: Context) {
    private val umami = Umami(website = "b85c84d7-1ff9-4b0b-ae29-e7694d4cd48f") {
        baseUrl("https://au.withcapsule.dev/")
        hostname("dev.withcapsule.android")
    }

    private val repository = SettingsRepository(context)
    private var isEnabled = true

    init {
        CoroutineScope(Dispatchers.IO).launch {
            isEnabled = repository.anonymousAnalyticsEnabled.first()
        }
    }

    fun event(url: String, name: String, data: Map<String, String>? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            // Refresh enabled state from repository to be sure
            isEnabled = repository.anonymousAnalyticsEnabled.first()
            if (isEnabled) {
                umami.event(url = url, name = name, data = data)
            }
        }
    }
}

lateinit var analytics: AnalyticsManager
