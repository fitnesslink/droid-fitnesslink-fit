package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.Program
import com.fitnesslink.fit.model.ProgramWeek
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.launch

class ProgramDetailViewModel : ViewModel() {
    var program by mutableStateOf(Program())
    var scheduleWeeks by mutableStateOf<List<ProgramWeek>>(emptyList())

    fun loadData(programId: String) {
        program = DatabaseManager.program(programId) ?: Program()
        loadSchedule(programId)
        viewModelScope.launch { refreshFromServer(programId) }
    }

    fun deleteProgram(id: String) {
        DatabaseManager.deleteProgram(id)
        viewModelScope.launch {
            if (!NetworkMonitor.isConnected.value) return@launch
            try { ApiClient.programApi.delete(id) } catch (_: Exception) {}
        }
    }

    private fun loadSchedule(programId: String) {
        val schedules = DatabaseManager.schedulesForProgram(programId)
        if (schedules.isEmpty()) return
        val allWorkouts = DatabaseManager.allWorkouts()
        val lookup = allWorkouts.associateBy { it.id }

        val maxWeek = schedules.maxOf { it.weekNumber }
        scheduleWeeks = (1..maxWeek).map { w ->
            val week = ProgramWeek.empty(w)
            week.copy(
                days = week.days.map { day ->
                    val schedule = schedules.find { it.weekNumber == w && it.dayNumber == day.dayNumber }
                    if (schedule != null) day.copy(workout = lookup[schedule.workoutId.toString()])
                    else day
                }
            )
        }
    }

    private suspend fun refreshFromServer(programId: String) {
        if (!NetworkMonitor.isConnected.value) return
        try {
            val remote = ApiClient.programApi.get(programId)
            DatabaseManager.insertProgram(remote)
            program = DatabaseManager.program(programId) ?: program
        } catch (_: Exception) { /* use cached */ }
    }
}
