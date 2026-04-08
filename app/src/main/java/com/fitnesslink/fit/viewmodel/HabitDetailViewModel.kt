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
import kotlinx.coroutines.launch

class HabitDetailViewModel : ViewModel() {
    var habit by mutableStateOf(Habit())
    var streak by mutableStateOf(Streak())

    fun loadData(habitId: String) {
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
