package com.fitnesslink.fit.network

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo

/**
 * The four backend services. Mirrors iOS `APIService`: every service is
 * served from the single production host under its own path prefix
 * (`https://api.fitnesslink.fit/{prefix}/api/v1/`).
 */
enum class ApiService(val pathPrefix: String, val localPort: Int) {
    CORE("core", 5100),
    NUTRITION("nutrition", 5200),
    NOTIFICATION("notifications", 5300),
    BILLING("billing", 5400)
}

enum class ApiEnvironment {
    /** Emulator → host machine. Each service runs on its own port. */
    LOCAL,
    /** A tunnel in front of the local gateway; same `/{prefix}/api/v1/` layout as prod. */
    NGROK,
    PRODUCTION;

    fun baseUrl(service: ApiService, ngrokUrl: String = ""): String = when (this) {
        LOCAL -> "http://10.0.2.2:${service.localPort}/api/v1/"
        NGROK -> "${ngrokUrl.trimEnd('/')}/${service.pathPrefix}/api/v1/"
        PRODUCTION -> "https://api.fitnesslink.fit/${service.pathPrefix}/api/v1/"
    }
}

object ApiConfiguration {
    private const val PREFS_NAME = "fl_api_config"
    private const val KEY_ENV = "environment"
    private const val KEY_NGROK_URL = "ngrok_url"

    private lateinit var prefs: SharedPreferences

    /**
     * True when the installed APK is debuggable. The project does not
     * generate `BuildConfig` (AGP 8 default), so the debug/release split is
     * read from the application flags instead.
     */
    var isDebugBuild: Boolean = false
        private set

    /** PRODUCTION for release builds, LOCAL for debug builds. */
    val defaultEnvironment: ApiEnvironment
        get() = if (isDebugBuild) ApiEnvironment.LOCAL else ApiEnvironment.PRODUCTION

    var environment: ApiEnvironment = ApiEnvironment.PRODUCTION
        private set
    var ngrokUrl: String = ""
        private set

    fun initialize(context: Context) {
        isDebugBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ngrokUrl = prefs.getString(KEY_NGROK_URL, "") ?: ""
        val stored = prefs.getString(KEY_ENV, null)
        environment = stored
            ?.let { name -> ApiEnvironment.entries.firstOrNull { it.name == name } }
            ?: defaultEnvironment
        // Release builds never talk to an emulator-only host, whatever an
        // old preference says.
        if (!isDebugBuild && environment == ApiEnvironment.LOCAL) {
            environment = ApiEnvironment.PRODUCTION
        }
    }

    fun setEnvironment(env: ApiEnvironment) {
        environment = env
        prefs.edit().putString(KEY_ENV, env.name).apply()
    }

    fun setNgrokUrl(url: String) {
        ngrokUrl = url
        environment = ApiEnvironment.NGROK
        prefs.edit()
            .putString(KEY_NGROK_URL, url)
            .putString(KEY_ENV, ApiEnvironment.NGROK.name)
            .apply()
    }

    fun baseUrl(service: ApiService): String = environment.baseUrl(service, ngrokUrl)
}
