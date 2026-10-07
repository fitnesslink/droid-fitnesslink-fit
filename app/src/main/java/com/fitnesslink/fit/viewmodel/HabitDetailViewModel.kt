package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.goals.GoalStore
import com.fitnesslink.fit.goals.HabitActivity
import com.fitnesslink.fit.goals.HabitDayStatus
import com.fitnesslink.fit.model.Habit
import com.fitnesslink.fit.model.HabitLog
import com.fitnesslink.fit.model.HabitStats
import com.fitnesslink.fit.model.Streak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * iOS `HabitDetailViewModel`, but the heatmap, statistics and recent logs
 * come from local `habit_logs` (iOS only had mock data there). Also owns the
 * "Mark done today" toggle, which neither platform had on this screen.
 */
class HabitDetailViewModel : ViewModel() {
    var habit by mutableStateOf(Habit())
        private set
    var streak by mutableStateOf(Streak())
        private set
    var goalTitle by mutableStateOf<String?>(null)
        private set
    /** Local-day start → status for the heatmap window. */
    var dayStatuses by mutableStateOf<Map<Long, HabitDayStatus>>(emptyMap())
        private set
    var windowDays by mutableStateOf<List<Long>>(emptyList())
        private set
    var stats by mutableStateOf(HabitStats())
        private set
    var recentLogs by mutableStateOf<List<HabitLog>>(emptyList())
        private set
    var isCompletedToday by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    private var habitId: String = ""

    init {
        GoalStore.ensureRegistered()
        viewModelScope.launch { GoalStore.changes.collect { if (habitId.isNotEmpty()) hydrateFromCache() } }
    }

    fun loadData(habitId: String) {
        this.habitId = habitId
        hydrateFromCache()
        viewModelScope.launch {
            isRefreshing = true
            try { withContext(Dispatchers.IO) { GoalStore.refreshHabitDetail(habitId) } } finally { isRefreshing = false }
            hydrateFromCache()
        }
    }

    private fun hydrateFromCache() {
        val cached = GoalStore.habit(habitId)
        if (cached != null) habit = cached
        streak = GoalStore.streak(habitId) ?: Streak(habitId = habitId)
        goalTitle = habit.goalId?.let { GoalStore.goal(it)?.title?.takeIf { t -> t.isNotBlank() } }

        val now = System.currentTimeMillis()
        val days = HabitActivity.windowDays(now, WEEKS)
        val since = days.first()
        val completed = GoalStore.completedDays(habitId, since)
        val trackedFrom = if (cached != null) GoalStore.trackedFrom(cached) else null
        windowDays = days
        dayStatuses = HabitActivity.dayStatuses(days, completed, now, trackedFrom)
        stats = HabitActivity.stats(dayStatuses.values, streak)
        recentLogs = GoalStore.habitLogs(habitId, limit = 7)
        isCompletedToday = GoalStore.isCompletedToday(habitId)
    }

    /** Toggle today's completion (Android improvement over both platforms). */
    fun toggleToday() {
        if (habitId.isEmpty()) return
        val target = !isCompletedToday
        isCompletedToday = target
        viewModelScope.launch { GoalStore.logHabit(habitId, target) }
    }

    companion object {
        const val WEEKS = 12
    }
}
