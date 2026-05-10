package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.Habit
import com.fitnesslink.fit.model.Milestone
import com.fitnesslink.fit.model.Streak
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import kotlinx.coroutines.launch

class GoalDetailViewModel : ViewModel() {
    var goal by mutableStateOf(Goal())
    var habits by mutableStateOf<List<Habit>>(emptyList())
    var milestones by mutableStateOf<List<Milestone>>(emptyList())
    var streaks by mutableStateOf<Map<String, Streak>>(emptyMap())

    fun loadData(goalId: String) {
        viewModelScope.launch { refreshFromServer(goalId) }
    }

    /** Optimistic milestone create — appends locally, posts to API. */
    fun addMilestone(goalId: String, title: String, targetValue: Double) {
        val draft = Milestone(
            id = java.util.UUID.randomUUID().toString(),
            goalId = goalId,
            title = title.trim(),
            targetValue = targetValue,
            achievedAt = null
        )
        milestones = milestones + draft
        viewModelScope.launch {
            try { ApiClient.goalApi.createMilestone(draft) } catch (_: Exception) {}
        }
    }

    /** Mark milestone reached locally and on the server. */
    fun achieveMilestone(milestoneId: String) {
        val now = System.currentTimeMillis()
        milestones = milestones.map {
            if (it.id == milestoneId) it.copy(achievedAt = now) else it
        }
        viewModelScope.launch {
            try { ApiClient.goalApi.achieveMilestone(milestoneId) } catch (_: Exception) {}
        }
    }

    private suspend fun refreshFromServer(goalId: String) {
        if (!NetworkMonitor.isConnected.value) return
        try {
            goal = ApiClient.goalApi.get(goalId)
        } catch (_: Exception) { /* use cached */ }
        try {
            habits = ApiClient.goalApi.getHabitsForGoal(goalId)
        } catch (_: Exception) {}
        try {
            milestones = ApiClient.goalApi.getMilestones(goalId)
        } catch (_: Exception) {}
        try {
            val streakMap = mutableMapOf<String, Streak>()
            for (habit in habits) {
                try {
                    streakMap[habit.id] = ApiClient.goalApi.getStreak(habit.id)
                } catch (_: Exception) {}
            }
            streaks = streakMap
        } catch (_: Exception) {}
    }
}
