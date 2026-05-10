package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.WorkoutList
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.launch

class WorkoutsViewModel : ViewModel() {
    var workouts by mutableStateOf<List<WorkoutList>>(emptyList())
        private set

    /** Selected training-level filter, or null = all levels. */
    var levelFilter by mutableStateOf<String?>(null)

    val visibleWorkouts: List<WorkoutList>
        get() = workouts.filter {
            levelFilter == null || it.trainingLevel.equals(levelFilter, ignoreCase = true)
        }

    fun loadData() {
        workouts = DatabaseManager.allWorkouts()
        viewModelScope.launch { refreshFromServer() }
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        try {
            val remote = ApiClient.workoutApi.listView()
            remote.forEach { DatabaseManager.insertWorkout(it) }
            workouts = DatabaseManager.allWorkouts()
        } catch (_: Exception) { /* use cached */ }
    }
}
