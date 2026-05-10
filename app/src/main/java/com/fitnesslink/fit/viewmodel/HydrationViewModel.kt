package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.HydrationGoal
import com.fitnesslink.fit.model.WaterIntakeEntry
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.sync.SyncManager
import com.google.gson.GsonBuilder
import kotlinx.coroutines.launch
import java.util.Date

class HydrationViewModel : ViewModel() {
    var todayEntries by mutableStateOf<List<WaterIntakeEntry>>(emptyList())
    var goal by mutableStateOf(HydrationGoal())
    var isRefreshing by mutableStateOf(false)
        private set

    private val gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .create()

    /**
     * Total intake today expressed in the user's goal unit so the dashboard
     * math stays in one unit regardless of which unit each entry was logged in.
     */
    val totalIntake: Double
        get() = todayEntries.sumOf { it.unit.convert(it.amount, goal.unit) }

    val hydrationProgress: Double
        get() {
            if (goal.dailyGoal <= 0) return 0.0
            return (totalIntake / goal.dailyGoal).coerceAtMost(1.0)
        }

    val remainingIntake: Double
        get() = (goal.dailyGoal - totalIntake).coerceAtLeast(0.0)

    fun loadData() {
        todayEntries = DatabaseManager.waterEntries(Date())
        goal = DatabaseManager.hydrationGoal()
        viewModelScope.launch { refreshFromServer() }
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        isRefreshing = true
        try {
            val remote = ApiClient.nutritionApi.getWaterEntries()
            remote.forEach { DatabaseManager.saveWaterEntry(it) }
            todayEntries = DatabaseManager.waterEntries(Date())
        } catch (_: Exception) { /* use cached */ }
        try {
            val remoteGoal = ApiClient.nutritionApi.getHydrationGoal()
            DatabaseManager.saveHydrationGoal(remoteGoal)
            goal = remoteGoal
        } catch (_: Exception) { /* use cached */ }
        isRefreshing = false
    }

    fun addEntry(entry: WaterIntakeEntry) {
        DatabaseManager.saveWaterEntry(entry)
        todayEntries = DatabaseManager.waterEntries(Date())

        // Mirror the food-entry pattern: enqueue first so the write survives
        // a network failure or app kill, then try inline; if inline fails the
        // SyncScheduler drains on the next connectivity pass.
        DatabaseManager.enqueueSyncEntry(
            entityType = SyncManager.WATER_ENTRIES_TYPE,
            entityId = entry.id,
            action = "create",
            payload = gson.toJson(entry)
        )
        viewModelScope.launch {
            if (!NetworkMonitor.isConnected.value) return@launch
            try { ApiClient.nutritionApi.addWaterEntry(entry) } catch (_: Exception) {}
        }
    }

    fun deleteEntry(id: String) {
        DatabaseManager.deleteWaterEntry(id)
        todayEntries = DatabaseManager.waterEntries(Date())

        DatabaseManager.enqueueSyncEntry(
            entityType = SyncManager.WATER_ENTRIES_TYPE,
            entityId = id,
            action = "delete",
            payload = ""
        )
        viewModelScope.launch {
            if (!NetworkMonitor.isConnected.value) return@launch
            try { ApiClient.nutritionApi.deleteWaterEntry(id) } catch (_: Exception) {}
        }
    }

    fun updateGoal(newGoal: HydrationGoal) {
        DatabaseManager.saveHydrationGoal(newGoal)
        goal = newGoal
        viewModelScope.launch {
            if (!NetworkMonitor.isConnected.value) return@launch
            try { ApiClient.nutritionApi.upsertHydrationGoal(newGoal) } catch (_: Exception) {}
        }
    }
}
