package dev.withcapsule.android

import android.content.Context
import android.os.Build
import dev.appoutlet.umami.Umami
import dev.appoutlet.umami.api.event
import dev.appoutlet.umami.api.identify
import dev.withcapsule.android.data.local.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AnalyticsManager(context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val screenResolution: String =
        context.resources.displayMetrics.let { "${it.widthPixels}x${it.heightPixels}" }

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
        scope.launch {
            isEnabled = repository.anonymousAnalyticsEnabled.first()
        }
    }

    fun event(url: String, name: String, data: Map<String, String>? = null) {
        scope.launch {
            try {
                isEnabled = repository.anonymousAnalyticsEnabled.first()
                if (isEnabled) {
                    umami.event(url = url, name = name, data = data)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun identify(data: Map<String, String>) {
        scope.launch {
            try {
                isEnabled = repository.anonymousAnalyticsEnabled.first()
                if (isEnabled) {
                    umami.identify(data = data)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

lateinit var analytics: AnalyticsManager
