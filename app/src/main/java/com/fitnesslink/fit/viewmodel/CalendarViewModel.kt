package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.CalendarCell
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

class CalendarViewModel : ViewModel() {
    var calendarCells by mutableStateOf<List<CalendarCell>>(emptyList())
    var filteredContent by mutableStateOf<List<FitnessContent>>(emptyList())
    var selectedDate by mutableStateOf(System.currentTimeMillis())
    var scheduledDays by mutableStateOf<Set<Int>>(emptySet())
    var showWorkoutPicker by mutableStateOf(false)

    fun loadData() {
        buildCalendarCells()
        loadScheduledDays()
        loadContentForSelectedDate()
        viewModelScope.launch { refreshFromServer() }
    }

    fun selectDate(day: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        selectedDate = cal.timeInMillis
        calendarCells = calendarCells.map { it.copy(selected = it.dayNumber == day) }
        loadContentForSelectedDate()
    }

    fun scheduleWorkout(workoutId: String, title: String, dateMillis: Long) {
        val entry = FitnessContent(
            id = UUID.randomUUID().toString(),
            title = title,
            programId = "",
            workoutId = workoutId,
            mealPlanId = "",
            status = "scheduled",
            scheduledDate = dateMillis
        )
        DatabaseManager.insertCalendarContent(entry)
        viewModelScope.launch {
            if (NetworkMonitor.isConnected.value) {
                try { ApiClient.calendarApi.create(entry) } catch (_: Exception) {}
            }
        }
        loadScheduledDays()
        loadContentForSelectedDate()
        buildCalendarCells()
    }

    fun deleteEntry(id: String) {
        DatabaseManager.deleteCalendarContent(id)
        viewModelScope.launch {
            if (NetworkMonitor.isConnected.value) {
                try { ApiClient.calendarApi.delete(id) } catch (_: Exception) {}
            }
        }
        loadScheduledDays()
        loadContentForSelectedDate()
        buildCalendarCells()
    }

    // Private

    private fun buildCalendarCells() {
        val cal = Calendar.getInstance()
        val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = cal.get(Calendar.DAY_OF_MONTH)

        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstWeekday = cal.get(Calendar.DAY_OF_WEEK) // Sunday=1

        val selectedCal = Calendar.getInstance().apply { timeInMillis = selectedDate }
        val selectedDay = selectedCal.get(Calendar.DAY_OF_MONTH)

        val cells = mutableListOf<CalendarCell>()
        // Empty offset cells
        for (i in 0 until firstWeekday - 1) {
            cells.add(CalendarCell(id = "empty-$i"))
        }
        for (day in 1..totalDays) {
            val status = when {
                scheduledDays.contains(day) && day < today -> "completed"
                scheduledDays.contains(day) -> "scheduled"
                else -> ""
            }
            cells.add(CalendarCell(
                id = "$day",
                dayNumber = day,
                name = "$day",
                status = status,
                selected = day == selectedDay
            ))
        }
        calendarCells = cells
    }

    private fun loadScheduledDays() {
        val cal = Calendar.getInstance()
        scheduledDays = DatabaseManager.calendarScheduledDays(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1
        )
    }

    private fun loadContentForSelectedDate() {
        filteredContent = DatabaseManager.calendarContent(forDate = selectedDate)
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        try {
            val remote = ApiClient.calendarApi.getEntries()
            remote.forEach { DatabaseManager.insertCalendarContent(it) }
            loadScheduledDays()
            loadContentForSelectedDate()
            buildCalendarCells()
        } catch (_: Exception) { /* use cached */ }
    }
}
