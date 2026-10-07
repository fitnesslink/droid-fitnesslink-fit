package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.calendar.CalendarDayKeys
import com.fitnesslink.fit.calendar.CalendarDayStatuses
import com.fitnesslink.fit.calendar.CalendarDeleteScope
import com.fitnesslink.fit.calendar.CalendarService
import com.fitnesslink.fit.model.CalendarCell
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.calendarContentForMonth
import com.fitnesslink.fit.persistence.calendarContentForProgramScope
import com.fitnesslink.fit.persistence.monthEndMillis
import com.fitnesslink.fit.persistence.monthStartMillis
import com.fitnesslink.fit.programs.ProgramScheduler
import com.fitnesslink.fit.programs.UserProgramStore
import com.fitnesslink.fit.ui.calendar.CalendarAddAction
import com.fitnesslink.fit.ui.calendar.CalendarAddMenuAnchor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

/**
 * Port of iOS `ViewModels/CalendarViewModel.swift`. All reads are cheap
 * synchronous SQLite re-reads; every write goes through [CalendarService]
 * (local row → device mirror → queued server call).
 */
class CalendarViewModel : ViewModel() {
    var calendarCells by mutableStateOf<List<CalendarCell>>(emptyList())
        private set
    var filteredContent by mutableStateOf<List<FitnessContent>>(emptyList())
        private set
    /** Local-midnight millis of the selected day. */
    var selectedDate by mutableStateOf(startOfDay(System.currentTimeMillis()))
        private set
    /** First-of-month millis of the visible month. */
    var currentMonth by mutableStateOf(monthStartMillis(System.currentTimeMillis()))
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    // Add menu / sheets
    /** Which trigger the anchored add menu is open from; null = closed. */
    var addMenuAnchor by mutableStateOf<CalendarAddMenuAnchor?>(null)
    private var pendingAddAction: CalendarAddAction? = null
    var showWorkoutPicker by mutableStateOf(false)
    var showProgramPicker by mutableStateOf(false)
    var showWorkoutCreator by mutableStateOf(false)
    var showProgramCreator by mutableStateOf(false)

    // Multi-select
    var isSelecting by mutableStateOf(false)
        private set
    var selectedEntryIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Entry being moved — drives the seeded `ScheduleDatePickerSheet`. */
    var moveTarget by mutableStateOf<FitnessContent?>(null)

    init {
        CalendarService.ensureRegistered()
        viewModelScope.launch { CalendarService.changes.collect { hydrateFromCache() } }
    }

    val selectedDayLabel: String get() = CalendarDayKeys.dayLabel(selectedDate)

    fun loadData() {
        hydrateFromCache()
        viewModelScope.launch { refreshFromServer() }
    }

    /** Cheap DB re-read on every appearance (a workout deleted elsewhere cascades its calendar rows). */
    fun hydrateFromCache() {
        val statuses = CalendarDayStatuses.from(
            DatabaseManager.calendarContentForMonth(currentMonth), yearOf(currentMonth), monthOf(currentMonth)
        )
        filteredContent = DatabaseManager.calendarContent(forDate = selectedDate)
        calendarCells = buildCells(statuses)
    }

    // MARK: - Month navigation

    fun goToPreviousMonth() = changeMonth(-1)
    fun goToNextMonth() = changeMonth(1)

    /** Jump to the month containing [dateMillis] and select that day (month picker / follow-after-move). */
    fun setMonth(dateMillis: Long) {
        currentMonth = monthStartMillis(dateMillis)
        selectedDate = startOfDay(dateMillis)
        exitSelectionMode()
        loadData()
    }

    private fun changeMonth(by: Int) {
        val cal = Calendar.getInstance().apply { timeInMillis = currentMonth; add(Calendar.MONTH, by) }
        currentMonth = monthStartMillis(cal.timeInMillis)
        selectedDate = currentMonth
        exitSelectionMode()
        loadData()
    }

    fun selectDate(day: Int) {
        selectedDate = dateFor(day)
        exitSelectionMode()
        filteredContent = DatabaseManager.calendarContent(forDate = selectedDate)
        calendarCells = calendarCells.map { it.copy(selected = it.dayNumber == day) }
    }

    /** Local-midnight millis of [day] in the visible month. */
    fun dateFor(day: Int): Long = Calendar.getInstance().apply {
        timeInMillis = currentMonth
        set(Calendar.DAY_OF_MONTH, day.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH)))
    }.timeInMillis

    // MARK: - Add menu

    /** Remember the choice and close the menu; the sheet opens from [runPendingAddAction] once it has gone. */
    fun chooseAddAction(action: CalendarAddAction) {
        pendingAddAction = action
        addMenuAnchor = null
    }

    fun runPendingAddAction() {
        val action = pendingAddAction ?: return
        pendingAddAction = null
        when (action) {
            CalendarAddAction.SCHEDULE_WORKOUT -> showWorkoutPicker = true
            CalendarAddAction.SCHEDULE_PROGRAM -> showProgramPicker = true
            CalendarAddAction.CREATE_WORKOUT -> showWorkoutCreator = true
            CalendarAddAction.CREATE_PROGRAM -> showProgramCreator = true
        }
    }

    // MARK: - Writes

    fun scheduleWorkout(workoutId: String, title: String, date: Date, durationMinutes: Int? = null) {
        viewModelScope.launch {
            CalendarService.scheduleWorkout(workoutId, title, date, durationMinutes)
            followDate(date.time)
        }
    }

    /** New workout saved from the calendar's "Create New Workout" — schedule it on the selected day. */
    fun scheduleSavedWorkout(workoutId: String) {
        val title = DatabaseManager.workout(workoutId)?.name ?: "Workout"
        scheduleWorkout(workoutId, title, Date(selectedDate))
    }

    /** Lay a program onto the calendar from [startDate] (same path as Program Detail → Add to Calendar). */
    fun scheduleProgram(programId: String, startDate: Date) {
        viewModelScope.launch {
            runCatching { ProgramScheduler.addToCalendar(programId, startDate) }
            followDate(startDate.time)
        }
    }

    /** Move an entry, then follow it (switching month if needed). */
    fun moveEntry(id: String, to: Date) {
        viewModelScope.launch {
            CalendarService.reschedule(id, to)
            exitSelectionMode()
            followDate(to.time)
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            CalendarService.remove(id)
            hydrateFromCache()
        }
    }

    fun deleteEntries(ids: Collection<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            CalendarService.removeAll(ids)
            exitSelectionMode()
            hydrateFromCache()
        }
    }

    fun deleteSelectedEntries() = deleteEntries(selectedEntryIds.toList())

    /** iOS `DateContentView` scope rule for the delete confirmation. */
    fun deleteScope(entry: FitnessContent): CalendarDeleteScope = CalendarDeleteScope.forEntry(
        entry,
        enrollmentEntries = entry.programEnrollmentId?.takeIf { it.isNotEmpty() }?.let { DatabaseManager.calendarContentForEnrollment(it) } ?: emptyList(),
        programEntries = if (entry.programId.isNotEmpty()) DatabaseManager.calendarContentForProgram(entry.programId) else emptyList()
    )

    /**
     * Remove every calendar workout of the program run [content] came from
     * (whole-program scope). Enrolled runs go through `UserProgramStore.remove`
     * (calendar entries via `CalendarService.remove`, queued `/removal`, local
     * `user_programs` row); legacy rows without an enrollment id fall back to
     * tearing down every entry sharing the program id.
     */
    fun removeProgram(content: FitnessContent) {
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { DatabaseManager.calendarContentForProgramScope(content) }
            val enrollmentIds = entries.mapNotNull { it.programEnrollmentId?.takeIf { id -> id.isNotEmpty() } }.toSet()
            for (eid in enrollmentIds) {
                runCatching { UserProgramStore.remove(eid) }
                    .onFailure { CalendarService.removeEnrollment(eid) }
            }
            CalendarService.removeAll(entries.filter { it.programEnrollmentId.isNullOrEmpty() }.map { it.id })
            exitSelectionMode()
            hydrateFromCache()
        }
    }

    // MARK: - Multi-select

    fun toggleSelectionMode() {
        isSelecting = !isSelecting
        if (!isSelecting) selectedEntryIds = emptySet()
    }

    fun exitSelectionMode() {
        isSelecting = false
        selectedEntryIds = emptySet()
    }

    fun toggleSelection(id: String) {
        selectedEntryIds = if (selectedEntryIds.contains(id)) selectedEntryIds - id else selectedEntryIds + id
    }

    val allSelected: Boolean get() = filteredContent.isNotEmpty() && selectedEntryIds.size == filteredContent.size

    fun toggleSelectAll() {
        selectedEntryIds = if (allSelected) emptySet() else filteredContent.map { it.id }.toSet()
    }

    // MARK: - Private

    /** Select [dateMillis]; switch month when it is outside the visible one. */
    private fun followDate(dateMillis: Long) {
        if (monthStartMillis(dateMillis) == currentMonth) {
            selectedDate = startOfDay(dateMillis)
            hydrateFromCache()
        } else {
            setMonth(dateMillis)
        }
    }

    private fun buildCells(statuses: CalendarDayStatuses): List<CalendarCell> {
        val cal = Calendar.getInstance().apply { timeInMillis = currentMonth }
        val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstWeekday = cal.get(Calendar.DAY_OF_WEEK) // Sunday = 1
        val isSelectedMonth = monthStartMillis(selectedDate) == currentMonth
        val selectedDay = Calendar.getInstance().apply { timeInMillis = selectedDate }.get(Calendar.DAY_OF_MONTH)
        val cells = ArrayList<CalendarCell>(42)
        for (i in 0 until firstWeekday - 1) cells.add(CalendarCell(id = "empty-$i"))
        for (day in 1..totalDays) {
            cells.add(CalendarCell(
                id = "$day", dayNumber = day, name = "$day",
                status = statuses.statusFor(day),
                selected = isSelectedMonth && day == selectedDay,
                hasProgram = statuses.program.contains(day)
            ))
        }
        return cells
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        isRefreshing = true
        try {
            CalendarService.refresh(Date(monthStartMillis(currentMonth)), Date(monthEndMillis(currentMonth)))
            hydrateFromCache()
        } finally {
            isRefreshing = false
        }
    }

    private companion object {
        fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        fun yearOf(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)
        fun monthOf(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.MONTH) + 1
    }
}
