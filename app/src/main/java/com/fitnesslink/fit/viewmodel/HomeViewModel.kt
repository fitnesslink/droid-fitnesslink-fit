package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.GoalSummary
import com.fitnesslink.fit.model.HabitLog
import com.fitnesslink.fit.model.HomeDashboard
import com.fitnesslink.fit.model.HorizontalCalendar
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeViewModel : ViewModel() {
    var calendarItems by mutableStateOf<List<HorizontalCalendar>>(emptyList())
    var dashboards by mutableStateOf<List<HomeDashboard>>(emptyList())
    var goalSummaries by mutableStateOf<List<GoalSummary>>(emptyList())
    var scrollToDay by mutableIntStateOf(1)

    fun loadData() {
        val totalDays = getTotalDaysOfMonth()
        val currentDay = getCurrentDay()
        scrollToDay = maxOf(1, currentDay - 3)
        calendarItems = MockDataProvider.calendarItems(totalDays, currentDay)
        dashboards = DatabaseManager.dashboards()
        viewModelScope.launch { refreshFromServer() }
    }

    fun completeHabit(habitId: String) {
        viewModelScope.launch {
            try {
                ApiClient.goalApi.logHabit(habitId, HabitLog(habitId = habitId))
            } catch (_: Exception) {}
        }
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        try {
            val goals = ApiClient.goalApi.list()
            goalSummaries = goals.map { goal ->
                val progress = if (goal.targetValue != null && goal.targetValue > 0) {
                    (goal.currentValue / goal.targetValue).coerceAtMost(1.0)
                } else 0.0
                GoalSummary(
                    id = goal.id,
                    title = goal.title,
                    goalType = goal.goalType,
                    progressPercent = progress,
                    trajectoryStatus = goal.trajectoryStatus,
                    targetDate = goal.targetDate
                )
            }
        } catch (_: Exception) { /* use cached */ }
    }

    private fun getTotalDaysOfMonth(): Int {
        val calendar = Calendar.getInstance()
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    private fun getCurrentDay(): Int {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    }
}
