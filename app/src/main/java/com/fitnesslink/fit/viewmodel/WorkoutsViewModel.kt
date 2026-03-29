package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.model.WorkoutList
import com.fitnesslink.fit.persistence.DatabaseManager

class WorkoutsViewModel : ViewModel() {
    var workouts by mutableStateOf<List<WorkoutList>>(emptyList())

    fun loadData() {
        workouts = DatabaseManager.allWorkouts()
    }
}
