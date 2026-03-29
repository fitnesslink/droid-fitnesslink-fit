package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.*
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
    }

    fun deleteEntry(id: String) {
        entries = entries.filter { it.id != id }
        chartPoints = entries.map { WeightChartPoint(id = it.id, date = it.date, weight = it.weight) }
    }
}
