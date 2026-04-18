package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.*
import com.fitnesslink.fit.model.api.ProgramSchedule
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.api.AddWeeklyWorkoutRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.launch
import java.util.UUID

class ProgramEditorViewModel : ViewModel() {
    var program by mutableStateOf(Program())
    var weeks by mutableStateOf<List<ProgramWeek>>(emptyList())
    var isNewProgram by mutableStateOf(true)
    var isDirty by mutableStateOf(false)

    // Workout picker state
    var showWorkoutPicker by mutableStateOf(false)
    var targetWeekNumber by mutableStateOf(1)
    var targetDayNumber by mutableStateOf(1)
    var availableWorkouts by mutableStateOf<List<WorkoutList>>(emptyList())
    var workoutSearchText by mutableStateOf("")
    var draftImageFilename by mutableStateOf<String?>(null)

    val filteredWorkouts: List<WorkoutList>
        get() = if (workoutSearchText.isEmpty()) availableWorkouts
        else availableWorkouts.filter { it.name.contains(workoutSearchText, ignoreCase = true) }

    // Init

    fun createNewProgram() {
        program = Program(id = UUID.randomUUID().toString())
        weeks = listOf(ProgramWeek.empty(1))
        isNewProgram = true
        isDirty = false
    }

    fun loadExistingProgram(id: String) {
        program = DatabaseManager.program(id) ?: Program()
        val schedules = DatabaseManager.schedulesForProgram(id)
        val allWorkouts = DatabaseManager.allWorkouts()
        val workoutLookup = allWorkouts.associateBy { it.id }

        val maxWeek = schedules.maxOfOrNull { it.weekNumber } ?: 1
        weeks = (1..maxOf(maxWeek, 1)).map { w ->
            val week = ProgramWeek.empty(w)
            week.copy(
                days = week.days.map { day ->
                    val schedule = schedules.find { it.weekNumber == w && it.dayNumber == day.dayNumber }
                    if (schedule != null) day.copy(workout = workoutLookup[schedule.workoutId.toString()])
                    else day
                }
            )
        }
        isNewProgram = false
        isDirty = false
    }

    // Save

    fun save() {
        if (program.name.isEmpty()) program = program.copy(name = "My Program")
        program = program.copy(weeks = weeks.size)
        DatabaseManager.insertProgram(program)

        val schedules = weeks.flatMap { week ->
            week.days.mapNotNull { day ->
                val workout = day.workout ?: return@mapNotNull null
                val workoutUUID = try { UUID.fromString(workout.id) } catch (_: Exception) { return@mapNotNull null }
                val programUUID = try { UUID.fromString(program.id) } catch (_: Exception) { return@mapNotNull null }
                ProgramSchedule(
                    id = UUID.randomUUID(),
                    programId = programUUID,
                    workoutId = workoutUUID,
                    weekNumber = day.weekNumber,
                    dayNumber = day.dayNumber
                )
            }
        }
        DatabaseManager.replaceSchedulesForProgram(program.id, schedules)

        val wasNew = isNewProgram
        isDirty = false
        isNewProgram = false

        viewModelScope.launch { syncToServer(schedules, wasNew) }
    }

    private suspend fun syncToServer(schedules: List<ProgramSchedule>, isNew: Boolean) {
        if (!NetworkMonitor.isConnected.value) return
        try {
            if (isNew) {
                ApiClient.programApi.create(program)
            } else {
                ApiClient.programApi.update(program.id, program)
            }
            schedules.forEach { s ->
                ApiClient.programApi.addWeeklyWorkout(
                    program.id,
                    AddWeeklyWorkoutRequest(s.workoutId.toString(), s.weekNumber, s.dayNumber)
                )
            }
        } catch (_: Exception) { /* use local cache */ }
    }

    // Week Management

    fun addWeek() {
        val nextNumber = (weeks.maxOfOrNull { it.weekNumber } ?: 0) + 1
        weeks = weeks + ProgramWeek.empty(nextNumber)
        isDirty = true
    }

    fun removeWeek(weekNumber: Int) {
        if (weeks.size <= 1) return
        weeks = weeks.filter { it.weekNumber != weekNumber }
            .mapIndexed { index, week ->
                val newNumber = index + 1
                week.copy(
                    weekNumber = newNumber,
                    days = week.days.map { it.copy(weekNumber = newNumber) }
                )
            }
        isDirty = true
    }

    fun duplicateWeek(weekNumber: Int) {
        val source = weeks.find { it.weekNumber == weekNumber } ?: return
        val nextNumber = (weeks.maxOfOrNull { it.weekNumber } ?: 0) + 1
        weeks = weeks + ProgramWeek(
            weekNumber = nextNumber,
            days = source.days.map { day ->
                ProgramDaySlot(weekNumber = nextNumber, dayNumber = day.dayNumber, workout = day.workout)
            }
        )
        isDirty = true
    }

    // Workout Assignment

    fun openWorkoutPicker(week: Int, day: Int) {
        targetWeekNumber = week
        targetDayNumber = day
        workoutSearchText = ""
        loadAvailableWorkouts()
        showWorkoutPicker = true
    }

    fun assignWorkout(workout: WorkoutList, weekNumber: Int, dayNumber: Int) {
        weeks = weeks.map { week ->
            if (week.weekNumber != weekNumber) week
            else week.copy(days = week.days.map { day ->
                if (day.dayNumber != dayNumber) day
                else day.copy(workout = workout)
            })
        }
        isDirty = true
    }

    fun removeWorkout(weekNumber: Int, dayNumber: Int) {
        weeks = weeks.map { week ->
            if (week.weekNumber != weekNumber) week
            else week.copy(days = week.days.map { day ->
                if (day.dayNumber != dayNumber) day
                else day.copy(workout = null)
            })
        }
        isDirty = true
    }

    // Move Workout (Drag & Drop)

    fun moveWorkout(fromWeek: Int, fromDay: Int, toWeek: Int, toDay: Int) {
        if (fromWeek == toWeek && fromDay == toDay) return

        val fromWorkout = weeks.find { it.weekNumber == fromWeek }
            ?.days?.find { it.dayNumber == fromDay }?.workout
        val toWorkout = weeks.find { it.weekNumber == toWeek }
            ?.days?.find { it.dayNumber == toDay }?.workout

        weeks = weeks.map { week ->
            week.copy(days = week.days.map { day ->
                when {
                    week.weekNumber == fromWeek && day.dayNumber == fromDay -> day.copy(workout = toWorkout)
                    week.weekNumber == toWeek && day.dayNumber == toDay -> day.copy(workout = fromWorkout)
                    else -> day
                }
            })
        }
        isDirty = true
    }

    // Helpers

    fun loadAvailableWorkouts() {
        availableWorkouts = DatabaseManager.allWorkouts()
    }

    val assignedWorkoutCount: Int
        get() = weeks.flatMap { it.days }.count { it.workout != null }
}
