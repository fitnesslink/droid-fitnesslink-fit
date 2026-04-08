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
import kotlinx.coroutines.launch

class NotificationSettingsViewModel : ViewModel() {
    var preferences by mutableStateOf(GoalNotificationPreference())

    fun loadData() {
        viewModelScope.launch { refreshFromServer() }
    }

    fun updateCoachingTone(tone: CoachingTone) {
        preferences = preferences.copy(coachingTone = tone)
        syncPreferences()
    }

    fun updateMaxDailyNotifications(count: Int) {
        preferences = preferences.copy(maxDailyNotifications = count)
        syncPreferences()
    }

    fun toggleHabitReminders() {
        preferences = preferences.copy(enableHabitReminders = !preferences.enableHabitReminders)
        syncPreferences()
    }

    fun toggleStreakAlerts() {
        preferences = preferences.copy(enableStreakAlerts = !preferences.enableStreakAlerts)
        syncPreferences()
    }

    fun toggleMilestones() {
        preferences = preferences.copy(enableMilestones = !preferences.enableMilestones)
        syncPreferences()
    }

    fun toggleAiCoaching() {
        preferences = preferences.copy(enableAiCoaching = !preferences.enableAiCoaching)
        syncPreferences()
    }

    fun toggleReengagement() {
        preferences = preferences.copy(enableReengagement = !preferences.enableReengagement)
        syncPreferences()
    }

    fun toggleGoalCheckIns() {
        preferences = preferences.copy(enableGoalCheckIns = !preferences.enableGoalCheckIns)
        syncPreferences()
    }

    private fun syncPreferences() {
        viewModelScope.launch {
            try { ApiClient.notificationApi.updatePreferences(preferences) } catch (_: Exception) {}
        }
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        try {
            preferences = ApiClient.notificationApi.getPreferences()
        } catch (_: Exception) { /* use cached */ }
    }
}
