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
import java.util.Calendar
import java.util.Date

class ProgressPhotosViewModel : ViewModel() {
    var entries by mutableStateOf<List<ProgressPhotoEntry>>(emptyList())
    var searchDate by mutableStateOf<Date?>(null)
    var showDatePicker by mutableStateOf(false)

    fun loadData() {
        entries = MockDataProvider.progressPhotoEntries.sortedByDescending { it.date }
        viewModelScope.launch { refreshFromServer() }
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        try {
            val remote = ApiClient.bodyTrackingApi.getProgressPhotos()
            entries = remote.sortedByDescending { it.date }
        } catch (_: Exception) { /* use cached */ }
    }

    val filteredEntries: List<ProgressPhotoEntry>
        get() {
            val sd = searchDate ?: return entries
            val cal = Calendar.getInstance()
            return entries.filter { entry ->
                cal.time = sd
                val searchDay = cal.get(Calendar.DAY_OF_YEAR)
                val searchYear = cal.get(Calendar.YEAR)
                cal.time = entry.date
                cal.get(Calendar.DAY_OF_YEAR) == searchDay && cal.get(Calendar.YEAR) == searchYear
            }
        }

    fun deleteEntry(id: String) {
        entries = entries.filter { it.id != id }
    }

    fun closestWeight(date: Date): WeightEntry? = MockDataProvider.closestWeight(date)
    fun closestMeasurements(date: Date): MeasurementEntry? = MockDataProvider.closestMeasurements(date)
}
