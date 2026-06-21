package dev.withcapsule.android

import android.content.Context
import android.os.Build
import dev.appoutlet.umami.Umami
import dev.appoutlet.umami.api.event
import dev.withcapsule.android.data.local.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AnalyticsManager(context: Context) {
    // Physical screen resolution (e.g. "1080x2400") for Umami's Screens report. Note: Umami derives
    // the Device type from the User-Agent, not from this value, so this is purely informational.
    private val screenResolution: String =
        context.resources.displayMetrics.let { "${it.widthPixels}x${it.heightPixels}" }

    // Non-identifying device attributes. Umami has no native "device model" dimension, so these are
    // sent as custom event data. Attach them to a single per-session event (app launch) rather than
    // every event, since they don't change within a session.
    val deviceInfo: Map<String, String> = mapOf(
        "device_model" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "android_version" to "Android ${Build.VERSION.RELEASE}",
    )

    private val umami = Umami(website = "b85c84d7-1ff9-4b0b-ae29-e7694d4cd48f") {
        baseUrl("https://au.withcapsule.dev/")
        hostname("dev.withcapsule.android")
        screenSize(screenResolution)
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
