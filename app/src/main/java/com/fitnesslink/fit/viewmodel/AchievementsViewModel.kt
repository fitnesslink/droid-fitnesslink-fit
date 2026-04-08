package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.Achievement
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import kotlinx.coroutines.launch

class AchievementsViewModel : ViewModel() {
    var achievements by mutableStateOf<List<Achievement>>(emptyList())

    fun loadData() {
        viewModelScope.launch { refreshFromServer() }
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        try {
            achievements = ApiClient.goalApi.getMyAchievements()
        } catch (_: Exception) { /* use cached */ }
    }
}
