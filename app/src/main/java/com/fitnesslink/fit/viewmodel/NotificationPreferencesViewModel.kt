package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.GoalNotificationPreference
import com.fitnesslink.fit.model.NotificationType
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FTNSSLNK-233 port: per-category notification preferences for the inbox
 * (System / Goals / Content / Calendar). Separate from the per-goal
 * reminder settings in [NotificationSettingsViewModel] — these four toggles
 * gate whole producers server-side. Optimistic save-on-toggle with a
 * full-object PUT (the endpoint accepts full updates, so every field rides
 * along), revert on failure.
 */
class NotificationPreferencesViewModel : ViewModel() {
    var preference by mutableStateOf(GoalNotificationPreference())
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var saveErrorMessage by mutableStateOf<String?>(null)

    fun loadData() {
        runCatching { DatabaseManager.notificationPreference() }.getOrNull()?.let { preference = it }
        viewModelScope.launch { refreshFromServer() }
    }

    fun isEnabled(type: NotificationType): Boolean = when (type) {
        NotificationType.SYSTEM -> preference.enableSystem
        NotificationType.GOALS -> preference.enableGoals
        NotificationType.CONTENT -> preference.enableContent
        NotificationType.CALENDAR -> preference.enableCalendar
    }

    fun setEnabled(type: NotificationType, enabled: Boolean) {
        val previous = preference
        preference = when (type) {
            NotificationType.SYSTEM -> previous.copy(enableSystem = enabled)
            NotificationType.GOALS -> previous.copy(enableGoals = enabled)
            NotificationType.CONTENT -> previous.copy(enableContent = enabled)
            NotificationType.CALENDAR -> previous.copy(enableCalendar = enabled)
        }
        saveErrorMessage = null
        val updated = preference
        viewModelScope.launch {
            try {
                ApiClient.notificationApi.updatePreferences(updated)
                withContext(Dispatchers.IO) { runCatching { DatabaseManager.saveNotificationPreference(updated) } }
            } catch (_: Exception) {
                preference = previous
                saveErrorMessage = "Couldn't save your change. Check your connection and try again."
            }
        }
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        isRefreshing = true
        try {
            val fresh = ApiClient.notificationApi.getPreferences()
            preference = fresh
            withContext(Dispatchers.IO) { runCatching { DatabaseManager.saveNotificationPreference(fresh) } }
        } catch (_: Exception) {
            // keep defaults / cache (all categories on)
        } finally {
            isRefreshing = false
        }
    }
}
