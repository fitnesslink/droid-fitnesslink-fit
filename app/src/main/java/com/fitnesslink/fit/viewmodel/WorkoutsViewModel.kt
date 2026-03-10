package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.WorkoutList

class WorkoutsViewModel : ViewModel() {
    var workouts by mutableStateOf<List<WorkoutList>>(emptyList())

    fun loadData() {
        workouts = MockDataProvider.workouts
    }
}
