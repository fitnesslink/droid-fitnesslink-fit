package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.persistence.DatabaseManager

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
    }
}
