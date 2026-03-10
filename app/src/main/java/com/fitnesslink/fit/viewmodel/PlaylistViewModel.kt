package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.Workout

class PlaylistViewModel : ViewModel() {
    var workout by mutableStateOf(Workout())
    var showVideoOverlay by mutableStateOf(false)

    fun loadData(workoutId: String) {
        workout = MockDataProvider.workoutDetail(workoutId)
    }

    fun showVideo(url: String) {
        showVideoOverlay = true
    }

    fun hideVideo() {
        showVideoOverlay = false
    }
}
