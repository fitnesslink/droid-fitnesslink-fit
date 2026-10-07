package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.goals.GoalStore
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.Habit
import com.fitnesslink.fit.model.Milestone
import com.fitnesslink.fit.model.Streak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * iOS `GoalDetailViewModel` over the local cache: goal, linked habits with
 * their streaks, milestones. Delete cascades through `GoalStore.delete`
 * (habits, streaks, logs; offline-safe). Milestone create / achieve are the
 * Android extras kept from the previous build.
 */
class GoalDetailViewModel : ViewModel() {
    var goal by mutableStateOf(Goal())
        private set
    var habits by mutableStateOf<List<Habit>>(emptyList())
        private set
    var milestones by mutableStateOf<List<Milestone>>(emptyList())
        private set
    var streaks by mutableStateOf<Map<String, Streak>>(emptyMap())
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    /** True once the goal row is gone locally (the screen should pop). */
    var isDeleted by mutableStateOf(false)
        private set

    private var goalId: String = ""

    init {
        GoalStore.ensureRegistered()
        viewModelScope.launch { GoalStore.changes.collect { if (goalId.isNotEmpty()) hydrateFromCache() } }
    }

    fun loadData(goalId: String) {
        this.goalId = goalId
        hydrateFromCache()
        viewModelScope.launch {
            isRefreshing = true
            try { withContext(Dispatchers.IO) { GoalStore.refreshGoalDetail(goalId) } } finally { isRefreshing = false }
            hydrateFromCache()
        }
    }

    private fun hydrateFromCache() {
        if (isDeleted) return
        val cached = GoalStore.goal(goalId)
        if (cached != null) goal = cached
        habits = GoalStore.habits(goalId)
        milestones = GoalStore.milestones(goalId)
        streaks = habits.associate { it.id to (GoalStore.streak(it.id) ?: Streak(habitId = it.id)) }
    }

    val completedMilestones: List<Milestone> get() = milestones.filter { it.achievedAt != null }
    val upcomingMilestones: List<Milestone> get() = milestones.filter { it.achievedAt == null }

    /** iOS `deleteGoal`: local cascade + queued server deletes. */
    fun deleteGoal(onDone: () -> Unit = {}) {
        if (goalId.isEmpty()) return
        viewModelScope.launch {
            GoalStore.delete(goalId)
            isDeleted = true
            onDone()
        }
    }

    fun addMilestone(goalId: String, title: String, targetValue: Double) {
        if (title.isBlank()) return
        viewModelScope.launch { GoalStore.addMilestone(goalId, title, targetValue) }
    }

    fun achieveMilestone(milestoneId: String) {
        viewModelScope.launch { GoalStore.achieveMilestone(goalId, milestoneId) }
    }
}
