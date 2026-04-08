package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.*
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import kotlinx.coroutines.launch
import java.util.Date

class WeightLogViewModel : ViewModel() {
    var entries by mutableStateOf<List<WeightEntry>>(emptyList())
    var chartPoints by mutableStateOf<List<WeightChartPoint>>(emptyList())
    var preferredUnit by mutableStateOf(WeightUnit.defaultUnit)
    var showAddEntry by mutableStateOf(false)

    var newWeight by mutableStateOf("")
    var newDate by mutableStateOf(Date())
    var newNotes by mutableStateOf("")

    fun loadData() {
        entries = MockDataProvider.weightEntries.sortedByDescending { it.date }
        chartPoints = MockDataProvider.weightChartPoints
        viewModelScope.launch { refreshFromServer() }
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        try {
            val remote = ApiClient.bodyTrackingApi.getWeightEntries()
            entries = remote.sortedByDescending { it.date }
            chartPoints = entries.map { WeightChartPoint(id = it.id, date = it.date, weight = it.weight) }
        } catch (_: Exception) { /* use cached */ }
    }

    val latestWeight: WeightEntry? get() = entries.firstOrNull()

    val weightChange: Double?
        get() = if (entries.size >= 2) entries[0].weight - entries[1].weight else null

    val allTimeChange: Double?
        get() = if (entries.size >= 2) entries.first().weight - entries.last().weight else null

    fun addEntry() {
        val weight = newWeight.toDoubleOrNull() ?: return
        if (weight <= 0) return
        val entry = WeightEntry(
            userId = "user1",
            weight = weight,
            unit = preferredUnit,
            date = newDate,
            notes = newNotes
        )
        entries = (entries + entry).sortedByDescending { it.date }
        chartPoints = entries.map { WeightChartPoint(id = it.id, date = it.date, weight = it.weight) }
        newWeight = ""
        newNotes = ""
        newDate = Date()
        showAddEntry = false
        viewModelScope.launch {
            try { ApiClient.bodyTrackingApi.addWeightEntry(entry) } catch (_: Exception) {}
        }
    }

    fun deleteEntry(id: String) {
        entries = entries.filter { it.id != id }
        chartPoints = entries.map { WeightChartPoint(id = it.id, date = it.date, weight = it.weight) }
        viewModelScope.launch {
            try { ApiClient.bodyTrackingApi.deleteWeightEntry(id) } catch (_: Exception) {}
        }
    }
}
