package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.launch

class WorkoutDetailViewModel : ViewModel() {
    var workout by mutableStateOf(Workout())
    var hasSession by mutableStateOf(false)

    val exerciseCount: Int
        get() = workout.phases
            .flatMap { it.taskRows }
            .mapNotNull { it.task }
            .count { it.isMovement }

    fun loadData(workoutId: String) {
        workout = DatabaseManager.workout(workoutId) ?: Workout()
        viewModelScope.launch { refreshFromServer(workoutId) }
    }

    private suspend fun refreshFromServer(workoutId: String) {
        if (!NetworkMonitor.isConnected.value) return
        try {
            val remote = ApiClient.workoutApi.get(workoutId)
            DatabaseManager.insertWorkout(remote)
            workout = DatabaseManager.workout(workoutId) ?: workout
        } catch (_: Exception) { /* use cached */ }
    }
}
