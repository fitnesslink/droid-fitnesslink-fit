package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.ChartPoint
import com.fitnesslink.fit.model.WeightChartPoint
import com.fitnesslink.fit.model.WeightEntry
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.progress.BodyTrackingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * iOS `WeightLogViewModel`: cache-first, server reconcile in the background,
 * local-first add with a queued `POST /weight`. Delete has UI on Android
 * (iOS only had the method). Values display in [preferredUnit] regardless of
 * the unit each entry was logged in.
 */
class WeightLogViewModel : ViewModel() {
    var entries by mutableStateOf<List<WeightEntry>>(emptyList())
        private set
    var preferredUnit by mutableStateOf(WeightUnit.current)
    var showAddEntry by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
        private set

    // Add entry form
    var newWeight by mutableStateOf("")
    var newDate by mutableStateOf(Date())
    var newNotes by mutableStateOf("")

    init {
        BodyTrackingStore.ensureRegistered()
        viewModelScope.launch { BodyTrackingStore.changes.collect { hydrateFromCache() } }
    }

    fun loadData() {
        hydrateFromCache()
        viewModelScope.launch {
            isRefreshing = true
            try { withContext(Dispatchers.IO) { BodyTrackingStore.refreshWeight() } } finally { isRefreshing = false }
            hydrateFromCache()
        }
    }

    private fun hydrateFromCache() {
        entries = BodyTrackingStore.weightEntries().sortedByDescending { it.date }
    }

    /** An entry's weight in the display unit. */
    fun displayWeight(entry: WeightEntry): Double = entry.unit.convert(entry.weight, preferredUnit)

    val chartPoints: List<WeightChartPoint>
        get() = entries.map { WeightChartPoint(id = it.id, date = it.date, weight = displayWeight(it)) }

    /** Date-keyed points for `ReportLineChart`, oldest first. */
    val reportPoints: List<ChartPoint>
        get() = entries.sortedBy { it.date }.map { ChartPoint(date = it.date, value = displayWeight(it)) }

    val latestWeight: WeightEntry? get() = entries.firstOrNull()

    val weightChange: Double?
        get() = if (entries.size >= 2) displayWeight(entries[0]) - displayWeight(entries[1]) else null

    val allTimeChange: Double?
        get() = if (entries.size >= 2) displayWeight(entries.first()) - displayWeight(entries.last()) else null

    fun addEntry() {
        val weight = newWeight.toDoubleOrNull() ?: return
        if (weight <= 0) return
        val entry = WeightEntry(weight = weight, unit = preferredUnit, date = newDate, notes = newNotes.trim())
        newWeight = ""
        newNotes = ""
        newDate = Date()
        showAddEntry = false
        viewModelScope.launch { BodyTrackingStore.addWeight(entry) }
    }

    fun deleteEntry(id: String) {
        entries = entries.filter { it.id != id }
        viewModelScope.launch { BodyTrackingStore.deleteWeight(id) }
    }
}
