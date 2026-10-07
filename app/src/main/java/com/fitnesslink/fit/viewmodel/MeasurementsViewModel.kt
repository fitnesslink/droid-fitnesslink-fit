package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.BodyMeasurementValue
import com.fitnesslink.fit.model.BodyPart
import com.fitnesslink.fit.model.MeasurementEntry
import com.fitnesslink.fit.model.MeasurementUnit
import com.fitnesslink.fit.progress.BodyTrackingStore
import com.fitnesslink.fit.progress.MeasurementMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * iOS `MeasurementsViewModel`: cache-first, server reconcile in the
 * background, local-first add / delete fanned out per body part by
 * `BodyTrackingStore`. Also backs `MeasurementEntryDetailScreen`.
 */
class MeasurementsViewModel : ViewModel() {
    var entries by mutableStateOf<List<MeasurementEntry>>(emptyList())
        private set
    var preferredUnit by mutableStateOf(MeasurementUnit.defaultUnit)
    var showAddEntry by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
        private set

    // Add entry form
    var selectedParts by mutableStateOf<Set<BodyPart>>(emptySet())
    var values by mutableStateOf<Map<BodyPart, String>>(emptyMap())
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
            try { withContext(Dispatchers.IO) { BodyTrackingStore.refreshMeasurements() } } finally { isRefreshing = false }
            hydrateFromCache()
        }
    }

    private fun hydrateFromCache() {
        entries = BodyTrackingStore.measurementEntries().sortedByDescending { it.date }
    }

    val latestEntry: MeasurementEntry? get() = entries.firstOrNull()

    fun entry(id: String): MeasurementEntry? = entries.firstOrNull { it.id.equals(id, true) }

    /** The entry logged just before [entryId]; null for the oldest. */
    fun previousOf(entryId: String): MeasurementEntry? = MeasurementMath.previousOf(entries, entryId)

    /** Latest vs. previous change for [part], in the display unit. */
    fun changes(part: BodyPart): Double? {
        if (entries.size < 2) return null
        return MeasurementMath.delta(entries[0], entries[1], part, preferredUnit)
    }

    fun displayValue(m: BodyMeasurementValue): Double = MeasurementMath.displayValue(m, preferredUnit)

    fun togglePart(part: BodyPart) {
        selectedParts = if (selectedParts.contains(part)) {
            values = values - part
            selectedParts - part
        } else {
            selectedParts + part
        }
    }

    fun updateValue(part: BodyPart, value: String) {
        values = values + (part to value.filter { it.isDigit() || it == '.' })
    }

    val canSave: Boolean
        get() = selectedParts.any { part -> (values[part]?.toDoubleOrNull() ?: 0.0) > 0 }

    fun addEntry() {
        val measurements = BodyPart.entries.filter { it in selectedParts }.mapNotNull { part ->
            val v = values[part]?.toDoubleOrNull() ?: return@mapNotNull null
            if (v <= 0) return@mapNotNull null
            BodyMeasurementValue(bodyPart = part, value = v, unit = preferredUnit, createdAt = newDate)
        }
        if (measurements.isEmpty()) return
        val entry = MeasurementEntry(date = newDate, measurements = measurements, notes = newNotes.trim())
        selectedParts = emptySet()
        values = emptyMap()
        newNotes = ""
        newDate = Date()
        showAddEntry = false
        viewModelScope.launch { BodyTrackingStore.addMeasurements(entry) }
    }

    fun deleteEntry(id: String) {
        entries = entries.filter { it.id != id }
        viewModelScope.launch { BodyTrackingStore.deleteMeasurements(id) }
    }
}
