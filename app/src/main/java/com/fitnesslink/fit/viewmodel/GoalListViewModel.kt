package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalStatus
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import kotlinx.coroutines.launch

class GoalListViewModel : ViewModel() {
    var goals by mutableStateOf<List<Goal>>(emptyList())

    fun loadData() {
        viewModelScope.launch { refreshFromServer() }
    }

    fun archiveGoal(goalId: String) {
        goals = goals.map {
            if (it.id == goalId) it.copy(status = GoalStatus.Archived) else it
        }
        viewModelScope.launch {
            try {
                val goal = goals.firstOrNull { it.id == goalId } ?: return@launch
                ApiClient.goalApi.update(goalId, goal)
            } catch (_: Exception) {}
        }
    }

    fun pauseGoal(goalId: String) {
        goals = goals.map {
            if (it.id == goalId) it.copy(status = GoalStatus.Paused) else it
        }
        viewModelScope.launch {
            try {
                val goal = goals.firstOrNull { it.id == goalId } ?: return@launch
                ApiClient.goalApi.update(goalId, goal)
            } catch (_: Exception) {}
        }
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        try {
            goals = ApiClient.goalApi.list()
        } catch (_: Exception) { /* use cached */ }
    }
}
