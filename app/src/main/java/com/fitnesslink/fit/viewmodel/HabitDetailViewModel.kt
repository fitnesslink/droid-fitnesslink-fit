package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.Habit
import com.fitnesslink.fit.model.Streak
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.launch

class HabitDetailViewModel : ViewModel() {
    var habit by mutableStateOf(Habit())
    var streak by mutableStateOf(Streak())

    /** Local-day start millis for each day this habit was completed. */
    var completedDays by mutableStateOf<Set<Long>>(emptySet())
        private set

    fun loadData(habitId: String) {
        // Heatmap looks back 12 weeks; compute that window once at load.
        val since = System.currentTimeMillis() - (12L * 7 * 86_400_000L)
        completedDays = DatabaseManager.habitLogDates(habitId, since)
        viewModelScope.launch { refreshFromServer(habitId) }
    }

    private suspend fun refreshFromServer(habitId: String) {
        if (!NetworkMonitor.isConnected.value) return
        try {
            val habits = ApiClient.goalApi.getMyHabits()
            habits.firstOrNull { it.id == habitId }?.let { habit = it }
        } catch (_: Exception) {}
        try {
            streak = ApiClient.goalApi.getStreak(habitId)
        } catch (_: Exception) {}
    }
}
