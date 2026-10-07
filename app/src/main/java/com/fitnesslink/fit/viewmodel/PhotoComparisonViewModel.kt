package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.MeasurementEntry
import com.fitnesslink.fit.model.PhotoAngle
import com.fitnesslink.fit.model.ProgressPhotoEntry
import com.fitnesslink.fit.model.WeightEntry
import com.fitnesslink.fit.progress.BodyTrackingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * iOS `PhotoComparisonViewModel`: newest two entries pre-selected as
 * After / Before; the "closest weight / measurements" now read the real
 * caches instead of `MockDataProvider`.
 */
class PhotoComparisonViewModel : ViewModel() {
    var allEntries by mutableStateOf<List<ProgressPhotoEntry>>(emptyList())
        private set
    var beforeEntry by mutableStateOf<ProgressPhotoEntry?>(null)
        private set
    var afterEntry by mutableStateOf<ProgressPhotoEntry?>(null)
        private set
    var selectedAngle by mutableStateOf(PhotoAngle.FRONT)

    init {
        BodyTrackingStore.ensureRegistered()
        viewModelScope.launch { BodyTrackingStore.changes.collect { hydrateFromCache() } }
    }

    fun loadData() {
        hydrateFromCache()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                BodyTrackingStore.refreshPhotos()
                BodyTrackingStore.refreshWeight()
                BodyTrackingStore.refreshMeasurements()
            }
            hydrateFromCache()
        }
    }

    private fun hydrateFromCache() {
        allEntries = BodyTrackingStore.photoEntries().sortedByDescending { it.date }
        // Keep an explicit selection when it still exists; otherwise default to the newest pair.
        afterEntry = afterEntry?.let { sel -> allEntries.firstOrNull { it.id == sel.id } } ?: allEntries.getOrNull(0)
        beforeEntry = beforeEntry?.let { sel -> allEntries.firstOrNull { it.id == sel.id } } ?: allEntries.getOrNull(1)
    }

    fun selectBefore(entry: ProgressPhotoEntry) { beforeEntry = entry }
    fun selectAfter(entry: ProgressPhotoEntry) { afterEntry = entry }
    fun closestWeight(date: Date): WeightEntry? = BodyTrackingStore.closestWeight(date)
    fun closestMeasurements(date: Date): MeasurementEntry? = BodyTrackingStore.closestMeasurements(date)
}
