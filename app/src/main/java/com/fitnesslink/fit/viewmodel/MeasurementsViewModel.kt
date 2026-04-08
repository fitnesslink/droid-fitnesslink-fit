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

class MeasurementsViewModel : ViewModel() {
    var entries by mutableStateOf<List<MeasurementEntry>>(emptyList())
    var preferredUnit by mutableStateOf(MeasurementUnit.defaultUnit)
    var showAddEntry by mutableStateOf(false)

    var selectedParts by mutableStateOf<Set<BodyPart>>(emptySet())
    var values by mutableStateOf<Map<BodyPart, String>>(emptyMap())
    var newDate by mutableStateOf(Date())
    var newNotes by mutableStateOf("")

    fun loadData() {
        entries = MockDataProvider.measurementEntries.sortedByDescending { it.date }
        viewModelScope.launch { refreshFromServer() }
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        try {
            val remote = ApiClient.bodyTrackingApi.getMeasurements()
            entries = remote.sortedByDescending { it.date }
        } catch (_: Exception) { /* use cached */ }
    }

    val latestEntry: MeasurementEntry? get() = entries.firstOrNull()

    fun changes(part: BodyPart): Double? {
        if (entries.size < 2) return null
        val latest = entries[0].measurement(part) ?: return null
        val previous = entries[1].measurement(part) ?: return null
        return latest.value - previous.value
    }

    fun togglePart(part: BodyPart) {
        selectedParts = if (selectedParts.contains(part)) {
            values = values - part
            selectedParts - part
        } else {
            selectedParts + part
        }
    }

    fun updateValue(part: BodyPart, value: String) {
        values = values + (part to value)
    }

    fun addEntry() {
        val measurements = selectedParts.mapNotNull { part ->
            val v = values[part]?.toDoubleOrNull() ?: return@mapNotNull null
            if (v <= 0) return@mapNotNull null
            BodyMeasurementValue(bodyPart = part, value = v, unit = preferredUnit)
        }
        if (measurements.isEmpty()) return
        val entry = MeasurementEntry(
            userId = "user1",
            date = newDate,
            measurements = measurements,
            notes = newNotes
        )
        entries = (entries + entry).sortedByDescending { it.date }
        selectedParts = emptySet()
        values = emptyMap()
        newNotes = ""
        newDate = Date()
        showAddEntry = false
    }

    fun deleteEntry(id: String) {
        entries = entries.filter { it.id != id }
    }
}
