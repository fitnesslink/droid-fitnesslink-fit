package com.fitnesslink.fit.network

import android.content.Context
import android.content.SharedPreferences

enum class ApiService { CORE, NUTRITION, NOTIFICATION }

enum class ApiEnvironment {
    LOCAL, NGROK, STAGING, PRODUCTION;

    fun baseUrl(service: ApiService, ngrokUrl: String = ""): String = when (this) {
        LOCAL -> when (service) {
            ApiService.CORE -> "http://10.0.2.2:5100/api/v1/"
            ApiService.NUTRITION -> "http://10.0.2.2:5200/api/v1/"
            ApiService.NOTIFICATION -> "http://10.0.2.2:5300/api/v1/"
        }
        NGROK -> {
            val base = ngrokUrl.trimEnd('/')
            when (service) {
                ApiService.CORE -> "$base/core/api/v1/"
                ApiService.NUTRITION -> "$base/nutrition/api/v1/"
                ApiService.NOTIFICATION -> "$base/notifications/api/v1/"
            }
        }
        STAGING -> when (service) {
            ApiService.CORE -> "https://staging-core.fitnesslink.app/api/v1/"
            ApiService.NUTRITION -> "https://staging-nutrition.fitnesslink.app/api/v1/"
            ApiService.NOTIFICATION -> "https://staging-notifications.fitnesslink.app/api/v1/"
        }
        PRODUCTION -> when (service) {
            ApiService.CORE -> "https://core.fitnesslink.app/api/v1/"
            ApiService.NUTRITION -> "https://nutrition.fitnesslink.app/api/v1/"
            ApiService.NOTIFICATION -> "https://notifications.fitnesslink.app/api/v1/"
        }
    }
}

object ApiConfiguration {
    private const val PREFS_NAME = "fl_api_config"
    private const val KEY_ENV = "environment"
    private const val KEY_NGROK_URL = "ngrok_url"

    private lateinit var prefs: SharedPreferences

    var environment: ApiEnvironment = ApiEnvironment.LOCAL
        private set
    var ngrokUrl: String = ""
        private set

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val envName = prefs.getString(KEY_ENV, "LOCAL") ?: "LOCAL"
        environment = try { ApiEnvironment.valueOf(envName) } catch (_: Exception) { ApiEnvironment.LOCAL }
        ngrokUrl = prefs.getString(KEY_NGROK_URL, "") ?: ""
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
