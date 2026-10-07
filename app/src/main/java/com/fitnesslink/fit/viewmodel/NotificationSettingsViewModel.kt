package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.CoachingTone
import com.fitnesslink.fit.model.GoalNotificationPreference
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Goal reminder settings (iOS `NotificationSettingsViewModel`): coaching
 * tone, the six reminder toggles and the daily cap. Every save is a
 * full-object PUT so the category flags and quiet hours ride along
 * untouched; a failed save reverts and reports.
 */
class NotificationSettingsViewModel : ViewModel() {
    var preferences by mutableStateOf(GoalNotificationPreference())
        private set
    var saveErrorMessage by mutableStateOf<String?>(null)

    fun loadData() {
        runCatching { DatabaseManager.notificationPreference() }.getOrNull()?.let { preferences = it }
        viewModelScope.launch { refreshFromServer() }
    }

    fun updateCoachingTone(tone: CoachingTone) = save { it.copy(coachingTone = tone) }
    fun updateMaxDailyNotifications(count: Int) = save { it.copy(maxDailyNotifications = count) }
    fun toggleHabitReminders() = save { it.copy(enableHabitReminders = !it.enableHabitReminders) }
    fun toggleStreakAlerts() = save { it.copy(enableStreakAlerts = !it.enableStreakAlerts) }
    fun toggleMilestones() = save { it.copy(enableMilestones = !it.enableMilestones) }
    fun toggleAiCoaching() = save { it.copy(enableAiCoaching = !it.enableAiCoaching) }
    fun toggleReengagement() = save { it.copy(enableReengagement = !it.enableReengagement) }
    fun toggleGoalCheckIns() = save { it.copy(enableGoalCheckIns = !it.enableGoalCheckIns) }

    /** Optimistic: apply, PUT the full object, revert on failure. */
    private fun save(transform: (GoalNotificationPreference) -> GoalNotificationPreference) {
        val previous = preferences
        val updated = transform(previous)
        preferences = updated
        saveErrorMessage = null
        viewModelScope.launch {
            try {
                ApiClient.notificationApi.updatePreferences(updated)
                withContext(Dispatchers.IO) { runCatching { DatabaseManager.saveNotificationPreference(updated) } }
            } catch (_: Exception) {
                preferences = previous
                saveErrorMessage = "Couldn't save your change. Check your connection and try again."
            }
        }
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        try {
            val fresh = ApiClient.notificationApi.getPreferences()
            preferences = fresh
            withContext(Dispatchers.IO) { runCatching { DatabaseManager.saveNotificationPreference(fresh) } }
        } catch (_: Exception) { /* use cached */ }
    }
}
