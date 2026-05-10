package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.Achievement
import com.fitnesslink.fit.model.AchievementType
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import kotlinx.coroutines.launch

class AchievementsViewModel : ViewModel() {
    var achievements by mutableStateOf<List<Achievement>>(emptyList())
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    val earnedAchievements: List<Achievement> get() = achievements

    val lockedTypes: List<AchievementType>
        get() {
            val earnedTypes = achievements.map { it.achievementType }.toSet()
            return AchievementType.entries.filter { it !in earnedTypes }
        }

    fun loadData() {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (!NetworkMonitor.isConnected.value) return@launch
            isRefreshing = true
            try {
                achievements = ApiClient.goalApi.getMyAchievements()
            } catch (_: Exception) { /* use cached */ }
            isRefreshing = false
        }
    }
}
