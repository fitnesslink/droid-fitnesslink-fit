package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.goals.GoalStore
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * iOS `GoalListViewModel`: cache-first (`hydrateFromCache`), then a server
 * refresh that pushes queued goal / habit writes before pulling. The
 * `GoalStore.changes` flow replaces iOS's `onAppear` / sheet-dismiss
 * re-hydrate — any local write re-reads the cache.
 */
class GoalListViewModel : ViewModel() {
    var activeGoals by mutableStateOf<List<Goal>>(emptyList())
        private set
    var archivedGoals by mutableStateOf<List<Goal>>(emptyList())
        private set
    var showArchived by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
        private set

    /** Every non-archived goal (active + paused), for callers that want one list. */
    val goals: List<Goal> get() = activeGoals

    init {
        GoalStore.ensureRegistered()
        viewModelScope.launch { GoalStore.changes.collect { hydrateFromCache() } }
    }

    fun loadData() {
        hydrateFromCache()
        viewModelScope.launch { refreshFromServer() }
    }

    fun hydrateFromCache() {
        val all = GoalStore.goals()
        // iOS shows `.active` only; paused goals stay visible here so the
        // Pause action has somewhere to show its result (Resume).
        activeGoals = all.filter { it.status == GoalStatus.Active || it.status == GoalStatus.Paused }
        archivedGoals = all.filter { it.status == GoalStatus.Archived }
    }

    fun archiveGoal(goalId: String) = setStatus(goalId, GoalStatus.Archived)

    fun pauseGoal(goalId: String) = setStatus(goalId, GoalStatus.Paused)

    fun resumeGoal(goalId: String) = setStatus(goalId, GoalStatus.Active)

    private fun setStatus(goalId: String, status: GoalStatus) {
        val goal = (activeGoals + archivedGoals).firstOrNull { it.id.equals(goalId, true) } ?: return
        viewModelScope.launch { GoalStore.update(goal.copy(status = status)) }
    }

    private suspend fun refreshFromServer() {
        isRefreshing = true
        try {
            withContext(Dispatchers.IO) { GoalStore.refresh() }
        } finally {
            isRefreshing = false
        }
        hydrateFromCache()
    }
}
