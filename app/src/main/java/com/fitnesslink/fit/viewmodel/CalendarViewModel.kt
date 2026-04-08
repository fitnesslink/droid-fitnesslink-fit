package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.CalendarCell
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.launch
import java.util.Calendar

class CalendarViewModel : ViewModel() {
    var calendarCells by mutableStateOf<List<CalendarCell>>(emptyList())
    var content by mutableStateOf<List<FitnessContent>>(emptyList())

    fun loadData() {
        val calendar = Calendar.getInstance()
        val totalDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        calendarCells = MockDataProvider.calendarCells(totalDays)
        content = DatabaseManager.calendarContent()
        viewModelScope.launch { refreshFromServer() }
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        try {
            val remote = ApiClient.calendarApi.getEntries()
            remote.forEach { DatabaseManager.insertCalendarContent(it) }
            content = DatabaseManager.calendarContent()
        } catch (_: Exception) { /* use cached */ }
    }
}
