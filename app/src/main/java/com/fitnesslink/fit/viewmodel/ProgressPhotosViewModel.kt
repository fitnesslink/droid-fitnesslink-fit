package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.MeasurementEntry
import com.fitnesslink.fit.model.ProgramDates
import com.fitnesslink.fit.model.ProgressPhotoEntry
import com.fitnesslink.fit.model.WeightEntry
import com.fitnesslink.fit.progress.BodyTrackingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * iOS `ProgressPhotosViewModel`: cache-first list, server reconcile in the
 * background, same-day search filter. Also backs `PhotoEntryDetailScreen`
 * (which used to read `MockDataProvider`).
 */
class ProgressPhotosViewModel : ViewModel() {
    var entries by mutableStateOf<List<ProgressPhotoEntry>>(emptyList())
        private set
    var searchDate by mutableStateOf<Date?>(null)
    var showDatePicker by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
        private set

    init {
        BodyTrackingStore.ensureRegistered()
        viewModelScope.launch { BodyTrackingStore.changes.collect { hydrateFromCache() } }
    }

    fun loadData() {
        hydrateFromCache()
        viewModelScope.launch {
            isRefreshing = true
            try {
                withContext(Dispatchers.IO) {
                    BodyTrackingStore.refreshPhotos()
                    // The linked chips need the latest weigh-ins / measurements too.
                    BodyTrackingStore.refreshWeight()
                    BodyTrackingStore.refreshMeasurements()
                }
            } finally {
                isRefreshing = false
            }
            hydrateFromCache()
        }
    }

    private fun hydrateFromCache() {
        entries = BodyTrackingStore.photoEntries().sortedByDescending { it.date }
    }

    fun entry(id: String): ProgressPhotoEntry? = entries.firstOrNull { it.id.equals(id, true) }

    val filteredEntries: List<ProgressPhotoEntry>
        get() {
            val sd = searchDate ?: return entries
            return entries.filter { ProgramDates.isSameDay(it.date.time, sd.time) }
        }

    fun deleteEntry(id: String) {
        entries = entries.filter { it.id != id }
        viewModelScope.launch { BodyTrackingStore.deletePhotoEntry(id) }
    }

    fun closestWeight(date: Date): WeightEntry? = BodyTrackingStore.closestWeight(date)
    fun closestMeasurements(date: Date): MeasurementEntry? = BodyTrackingStore.closestMeasurements(date)
}
