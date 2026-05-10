package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.model.GoalSummary
import com.fitnesslink.fit.model.HabitLog
import com.fitnesslink.fit.model.HomeDashboard
import com.fitnesslink.fit.model.HorizontalCalendar
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeViewModel : ViewModel() {
    var calendarItems by mutableStateOf<List<HorizontalCalendar>>(emptyList())
    var dashboards by mutableStateOf<List<HomeDashboard>>(emptyList())
    var goalSummaries by mutableStateOf<List<GoalSummary>>(emptyList())
    var scrollToDay by mutableIntStateOf(1)
    var selectedDate by mutableStateOf(Date())
        private set
    var todayWorkouts by mutableStateOf<List<FitnessContent>>(emptyList())
        private set

    val isSelectedDateToday: Boolean
        get() {
            val cal = Calendar.getInstance()
            val today = cal.get(Calendar.DAY_OF_YEAR) to cal.get(Calendar.YEAR)
            cal.time = selectedDate
            val sel = cal.get(Calendar.DAY_OF_YEAR) to cal.get(Calendar.YEAR)
            return today == sel
        }

    /** Display label for the selected day — "Today" or "Mon, Mar 5". */
    val selectedDateLabel: String
        get() = if (isSelectedDateToday) "Today" else dateLabelFormat.format(selectedDate)

    fun loadData() {
        val totalDays = getTotalDaysOfMonth()
        val currentDay = getCurrentDay()
        scrollToDay = maxOf(1, currentDay - 3)
        selectedDate = Date()
        calendarItems = MockDataProvider.calendarItems(totalDays, currentDay)
        dashboards = DatabaseManager.dashboards()
        loadWorkoutsForSelectedDate()
        viewModelScope.launch { refreshFromServer() }
    }

    /**
     * User tapped a day in the horizontal date strip. Update the selected
     * date and rebuild the strip so the highlight moves. Date-sensitive
     * sections read from `selectedDate` to refresh their content.
     */
    fun selectDay(day: Int) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        selectedDate = cal.time
        calendarItems = calendarItems.map { it.copy(selected = it.dayNumber == day) }
        loadWorkoutsForSelectedDate()
    }

    /**
     * Refresh the list of workouts scheduled for `selectedDate`. Reads from
     * the local calendar_content cache so swiping between days is instant;
     * SyncManager keeps that cache fresh in the background.
     */
    private fun loadWorkoutsForSelectedDate() {
        todayWorkouts = DatabaseManager.calendarContent(selectedDate.time)
            .filter { it.workoutId.isNotEmpty() }
    }

    /**
     * Number of exercises (tasks) across all phases in a cached workout, or
     * 0 if the workout isn't cached locally.
     */
    fun exerciseCount(workoutId: String): Int =
        DatabaseManager.workout(workoutId)
            ?.phases
            ?.sumOf { it.taskRows.size }
            ?: 0

    /** Cached workout duration in minutes (or null if unknown). */
    fun workoutDuration(workoutId: String): Int? =
        DatabaseManager.workout(workoutId)?.estimatedTime

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

    companion object {
        private val dateLabelFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    }
}
