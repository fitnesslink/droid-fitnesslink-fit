package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.*
import java.util.Date

class PhotoComparisonViewModel : ViewModel() {
    var allEntries by mutableStateOf<List<ProgressPhotoEntry>>(emptyList())
    var beforeEntry by mutableStateOf<ProgressPhotoEntry?>(null)
    var afterEntry by mutableStateOf<ProgressPhotoEntry?>(null)
    var selectedAngle by mutableStateOf(PhotoAngle.FRONT)

    fun loadData() {
        allEntries = MockDataProvider.progressPhotoEntries.sortedByDescending { it.date }
        if (allEntries.size >= 2) {
            afterEntry = allEntries[0]
            beforeEntry = allEntries[1]
        } else if (allEntries.isNotEmpty()) {
            afterEntry = allEntries[0]
        }
    }

    fun selectBefore(entry: ProgressPhotoEntry) { beforeEntry = entry }
    fun selectAfter(entry: ProgressPhotoEntry) { afterEntry = entry }
    fun closestWeight(date: Date): WeightEntry? = MockDataProvider.closestWeight(date)
    fun closestMeasurements(date: Date): MeasurementEntry? = MockDataProvider.closestMeasurements(date)
}
