package com.fitnesslink.fit.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.calendar.CalendarService
import com.fitnesslink.fit.model.DailyProgress
import com.fitnesslink.fit.model.DateStripBuilder
import com.fitnesslink.fit.model.DateStripEntry
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalStatus
import com.fitnesslink.fit.model.GoalSummary
import com.fitnesslink.fit.model.HabitLog
import com.fitnesslink.fit.model.HabitTodayItem
import com.fitnesslink.fit.model.HomeDashboard
import com.fitnesslink.fit.model.HomeDashboardMath
import com.fitnesslink.fit.model.HomeDateItem
import com.fitnesslink.fit.model.ModelGson
import com.fitnesslink.fit.model.ProgramDates
import com.fitnesslink.fit.model.Streak
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.advanceHabitStreak
import com.fitnesslink.fit.model.dailyProgressFor
import com.fitnesslink.fit.model.rollBackHabitStreak
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.dashboardSessions
import com.fitnesslink.fit.persistence.deleteHabitLogById
import com.fitnesslink.fit.persistence.habitIdsCompletedOn
import com.fitnesslink.fit.persistence.habitLogOn
import com.fitnesslink.fit.persistence.habitStreak
import com.fitnesslink.fit.persistence.homeGoals
import com.fitnesslink.fit.persistence.homeHabits
import com.fitnesslink.fit.persistence.purgeOrphanedHabits
import com.fitnesslink.fit.persistence.upsertHabitStreak
import com.fitnesslink.fit.programs.UserProgramStore
import com.fitnesslink.fit.sync.SyncDomain
import com.fitnesslink.fit.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Device-local "workouts this week" display goal (iOS UserDefaults
 * `weeklyWorkoutGoal`, FTNSSLNK-365). Absent until the user sets one — the
 * tile shows a "Set weekly goal" CTA instead of implying a default.
 */
object WeeklyWorkoutGoalPrefs {
    private const val PREFS_NAME = "fitnesslink_prefs"
    const val KEY = "weeklyWorkoutGoal"

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var memory: Int? = null

    fun initialize(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun get(): Int? {
        val p = prefs ?: return memory
        return if (p.contains(KEY)) p.getInt(KEY, 0).takeIf { it > 0 } else null
    }

    fun set(goal: Int) {
        memory = goal
        prefs?.edit()?.putInt(KEY, goal)?.apply()
    }
}

/**
 * Port of iOS `HomeViewModel`. Everything the Home tab renders is derived
 * from the local DB (`hydrateFromCache` is cheap and runs on every appear);
 * the server refresh drains queued writes first, then pulls the selected
 * day's calendar, goals, habits and program enrollments.
 */
class HomeViewModel : ViewModel() {
    var dateItems by mutableStateOf<List<HomeDateItem>>(emptyList())
        private set
    var dashboards by mutableStateOf<List<HomeDashboard>>(emptyList())
        private set
    var goalSummaries by mutableStateOf<List<GoalSummary>>(emptyList())
        private set
    var todayHabits by mutableStateOf<List<HabitTodayItem>>(emptyList())
        private set
    var dailyProgress by mutableStateOf(DailyProgress())
        private set
    var todayWorkouts by mutableStateOf<List<FitnessContent>>(emptyList())
        private set
    /**
     * True when the selected day has no workout but falls inside a program
     * enrollment's scheduled span — a prescribed rest day, as opposed to a
     * plain empty day (FTNSSLNK-363).
     */
    var isProgramRestDay by mutableStateOf(false)
        private set
    /** First workout scheduled the day after the selected day (FTNSSLNK-366). */
    var tomorrowWorkout by mutableStateOf<FitnessContent?>(null)
        private set
    /** Day-of-month the strip should centre on. */
    var scrollToDay by mutableIntStateOf(1)
        private set
    /** Bumped by [selectToday] so the strip re-centres even when the day is unchanged. */
    var recenterToken by mutableIntStateOf(0)
        private set
    var selectedDate by mutableStateOf(Date())
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var weeklyWorkoutGoal by mutableStateOf<Int?>(null)
        private set

    private var loaded = false
    /** Year / month (0-based) the strip currently shows. */
    private var stripYear = 0
    private var stripMonth = -1

    /**
     * Streak snapshot taken right before a habit is tapped complete so
     * `uncompleteHabit` can restore it bit-for-bit. Lost on relaunch — fine,
     * undo is only meaningful right after an accidental tap.
     */
    private val preCompletionStreaks = HashMap<String, Streak>()
    /** Sync-queue row id of the in-flight `HabitLog` create, keyed by habit id. */
    private val pendingLogQueueIds = HashMap<String, String>()

    val isSelectedDateToday: Boolean get() = ProgramDates.isSameDay(selectedDate.time, System.currentTimeMillis())

    /** "Today's Workout" or "Workouts on Oct 3". */
    val workoutSectionTitle: String
        get() = if (isSelectedDateToday) "Today's Workout" else "Workouts on ${monthDayFormat.format(selectedDate)}"

    private val userId: String
        get() = runCatching { AuthManager.currentUser.value?.id?.toString() }.getOrNull()
            ?: runCatching { DatabaseManager.user()?.id }.getOrNull()
            ?: ""

    private val sessionsUserId: String get() = runCatching { DatabaseManager.user()?.id }.getOrNull() ?: "user1"

    init {
        // Other screens' local writes (calendar removes, program starts,
        // catalog refresh) re-render Home without a round trip.
        viewModelScope.launch { runCatching { CalendarService.changes.collect { hydrateFromCache() } } }
        viewModelScope.launch { runCatching { UserProgramStore.changes.collect { hydrateFromCache() } } }
    }

    // MARK: - Lifecycle

    /** First composition: build everything from the cache, then refresh. Subsequent calls re-centre and re-hydrate. */
    fun onAppear() {
        if (!loaded) {
            loaded = true
            loadData()
        } else {
            selectToday()
            hydrateFromCache()
        }
        // Queued session writes (start / complete / abandon) don't otherwise
        // retry between launches; kick the drain so a failed /complete ships.
        viewModelScope.launch { runCatching { SyncManager.drain(SyncDomain.SESSIONS) } }
    }

    fun loadData() {
        weeklyWorkoutGoal = WeeklyWorkoutGoalPrefs.get()
        val now = Date()
        selectedDate = ProgramDates.startOfDay(now.time).let(::Date)
        val cal = Calendar.getInstance().apply { time = now }
        stripYear = cal.get(Calendar.YEAR); stripMonth = cal.get(Calendar.MONTH)
        scrollToDay = cal.get(Calendar.DAY_OF_MONTH)
        hydrateFromCache()
        viewModelScope.launch { refreshFromServer() }
    }

    /**
     * Cheap, sync DB re-read for every section (iOS `hydrateFromCache`).
     * Safe to call on every appear — no network.
     */
    fun hydrateFromCache() {
        runCatching { CalendarService.ensureRegistered() }
        loadWorkoutsForSelectedDate()
        rebuildDateStrip()
        goalSummaries = goalSummariesFromCache()
        todayHabits = todayHabitsFromCache()
        // Reads `todayHabits`, so it has to run after the habit refresh above.
        dailyProgress = dailyProgressFor(todayHabits)
        dashboards = computeDashboards(selectedDate.time)
    }

    /**
     * Put the strip back on today — coming back to the Home tab or to the
     * app should show the current date, whatever day was browsed (and a day
     * that rolled over at midnight). Rebuilds the strip when the month
     * changed. No-op when today is already selected and centred.
     */
    fun selectToday() {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)
        recenterToken += 1
        val stripIsThisMonth = stripYear == cal.get(Calendar.YEAR) && stripMonth == cal.get(Calendar.MONTH)
        if (isSelectedDateToday && stripIsThisMonth && dateItems.firstOrNull { it.selected }?.dayNumber == today) {
            scrollToDay = today
            return
        }
        if (!stripIsThisMonth) {
            stripYear = cal.get(Calendar.YEAR); stripMonth = cal.get(Calendar.MONTH)
        }
        selectDay(today)
        scrollToDay = today
    }

    /** User tapped a day in the strip: swap the selected date and reload the date-sensitive sections. */
    fun selectDay(day: Int) {
        val cal = Calendar.getInstance().apply {
            clear(); set(stripYear, stripMonth, day)
        }
        selectedDate = cal.time
        loadWorkoutsForSelectedDate()
        rebuildDateStrip()
        dashboards = computeDashboards(selectedDate.time)
    }

    // MARK: - Calendar-derived sections

    private fun loadWorkoutsForSelectedDate() {
        val entries = runCatching { DatabaseManager.calendarContent(selectedDate.time) }.getOrDefault(emptyList())
        todayWorkouts = entries.filter { it.workoutId.isNotEmpty() }.map { entry ->
            // Older rows (or a server that doesn't echo media ids) carry no
            // thumbnail — fall back to the cached workout's image ids.
            if (entry.workoutThumbnailId != null || entry.workoutImageId != null) return@map entry
            val workout = runCatching { DatabaseManager.workout(entry.workoutId) }.getOrNull() ?: return@map entry
            entry.copy(
                workoutImageId = workout.imageId?.toString(),
                workoutThumbnailId = workout.thumbnailId?.toString()
            )
        }
        updateEmptyDayState()
    }

    /** Program rest day vs nothing planned, plus tomorrow's first workout for the rest card. */
    private fun updateEmptyDayState() {
        val dayStart = ProgramDates.startOfDay(selectedDate.time)
        if (todayWorkouts.isNotEmpty()) {
            isProgramRestDay = false
            tomorrowWorkout = null
            return
        }
        val span = runCatching { DatabaseManager.programCalendarSpan() }.getOrNull()
        isProgramRestDay = span != null && dayStart >= ProgramDates.startOfDay(span.first) && dayStart <= span.second
        tomorrowWorkout = runCatching { DatabaseManager.calendarContent(ProgramDates.addDays(dayStart, 1)) }
            .getOrDefault(emptyList())
            .firstOrNull { it.workoutId.isNotEmpty() }
    }

    /** Fill each day of the strip with its completion state and first workout title (PRD §18). */
    private fun rebuildDateStrip() {
        val monthStart = Calendar.getInstance().apply { clear(); set(stripYear, stripMonth, 1) }
        val monthEnd = (monthStart.clone() as Calendar).apply { add(Calendar.MONTH, 1); add(Calendar.MILLISECOND, -1) }
        val rows = runCatching { DatabaseManager.calendarContent(monthStart.timeInMillis, monthEnd.timeInMillis) }.getOrDefault(emptyList())
        val dayCal = Calendar.getInstance()
        val entries = rows.filter { it.workoutId.isNotEmpty() && it.scheduledDate != null }.map { e ->
            dayCal.timeInMillis = e.scheduledDate!!
            DateStripEntry(dayCal.get(Calendar.DAY_OF_MONTH), e.title, e.isCompleted)
        }
        val spanDays = DateStripBuilder.programSpanDays(stripYear, stripMonth, runCatching { DatabaseManager.programCalendarSpan() }.getOrNull())
        val selectedDay = Calendar.getInstance().apply { time = selectedDate }.get(Calendar.DAY_OF_MONTH)
        dateItems = DateStripBuilder.build(stripYear, stripMonth, selectedDay, entries, spanDays)
    }

    // MARK: - Goals / habits

    private fun goalSummariesFromCache(): List<GoalSummary> =
        runCatching { DatabaseManager.homeGoals() }.getOrDefault(emptyList())
            .filter { it.status == GoalStatus.Active }
            .map(::goalSummary)

    private fun goalSummary(goal: Goal): GoalSummary {
        val target = goal.targetValue
        val progress = if (target != null && target > 0) goal.currentValue / target * 100.0 else 0.0
        return GoalSummary(
            id = goal.id, title = goal.title, goalType = goal.goalType,
            progressPercent = progress, trajectoryStatus = goal.trajectoryStatus, targetDate = goal.targetDate
        )
    }

    private fun todayHabitsFromCache(): List<HabitTodayItem> {
        val habits = runCatching { DatabaseManager.homeHabits() }.getOrDefault(emptyList()).filter { it.isActive }
        val completed = runCatching { DatabaseManager.habitIdsCompletedOn() }.getOrDefault(emptySet())
        val goalTitleById = runCatching { DatabaseManager.homeGoals() }.getOrDefault(emptyList())
            .associate { it.id.lowercase(Locale.US) to it.title }
        return habits.sortedBy { it.sortOrder }.map { habit ->
            HabitTodayItem(
                id = habit.id, habitId = habit.id, title = habit.title, habitType = habit.habitType, tier = habit.tier,
                isCompletedToday = habit.id.lowercase(Locale.US) in completed,
                streak = runCatching { DatabaseManager.habitStreak(habit.id) }.getOrNull() ?: Streak(),
                anchorBehavior = habit.anchorBehavior, targetValue = habit.targetValue, targetUnit = habit.targetUnit,
                currentSensorValue = null,
                goalTitle = habit.goalId?.let { goalTitleById[it.lowercase(Locale.US)] },
                sortOrder = habit.sortOrder
            )
        }
    }

    /**
     * Mark a habit done for today: local log + advanced streak now, queued
     * `POST habits/{id}/log` replayed by the habit-logs drain if the inline
     * send fails (iOS `completeHabit`).
     */
    fun completeHabit(habitId: String) {
        val index = todayHabits.indexOfFirst { it.habitId == habitId }
        if (index < 0 || todayHabits[index].isCompletedToday) return
        val habit = todayHabits[index]
        val uid = userId
        preCompletionStreaks[habitId] = habit.streak

        val log = HabitLog(habitId = habitId, userId = uid, completedAt = System.currentTimeMillis())
        runCatching { DatabaseManager.insertHabitLog(log) }
        val advanced = advanceHabitStreak(habit.streak, habitId, uid, log.completedAt)
        runCatching { DatabaseManager.upsertHabitStreak(advanced) }

        todayHabits = todayHabits.toMutableList().also { it[index] = habit.copy(isCompletedToday = true, streak = advanced) }
        dailyProgress = dailyProgressFor(todayHabits)

        val queueId = runCatching {
            DatabaseManager.enqueueSyncEntry(SyncDomain.HABIT_LOGS.wireName, log.id, "create", ModelGson.instance.toJson(log))
        }.getOrNull()
        if (queueId != null) pendingLogQueueIds[habitId] = queueId
        viewModelScope.launch {
            if (!NetworkMonitor.isConnected.value) return@launch
            try {
                withContext(Dispatchers.IO) { ApiClient.goalApi.logHabit(habitId, log) }
                queueId?.let { runCatching { DatabaseManager.markSyncEntrySynced(it) } }
                pendingLogQueueIds.remove(habitId)
            } catch (e: Exception) {
                Log.w(TAG, "habit log send failed (drain will retry): ${e.message}")
            }
        }
    }

    /**
     * Revert today's accidental completion: delete today's log row, restore
     * the pre-tap streak (or best-effort roll back), and cancel the queued
     * POST if the drain hasn't shipped it. The server has no DELETE for habit
     * logs yet — a POST that already landed leaves transient server drift.
     */
    fun uncompleteHabit(habitId: String) {
        val index = todayHabits.indexOfFirst { it.habitId == habitId }
        if (index < 0 || !todayHabits[index].isCompletedToday) return
        val habit = todayHabits[index]

        runCatching { DatabaseManager.habitLogOn(habitId) }.getOrNull()?.let { log ->
            runCatching { DatabaseManager.deleteHabitLogById(log.id) }
        }
        val restored = preCompletionStreaks.remove(habitId) ?: rollBackHabitStreak(habit.streak)
        runCatching { DatabaseManager.upsertHabitStreak(restored) }

        todayHabits = todayHabits.toMutableList().also { it[index] = habit.copy(isCompletedToday = false, streak = restored) }
        dailyProgress = dailyProgressFor(todayHabits)

        pendingLogQueueIds.remove(habitId)?.let { runCatching { DatabaseManager.markSyncEntrySynced(it) } }
    }

    // MARK: - Dashboard

    fun setWeeklyWorkoutGoal(goal: Int) {
        WeeklyWorkoutGoalPrefs.set(goal)
        weeklyWorkoutGoal = goal
        dashboards = computeDashboards(selectedDate.time)
    }

    /**
     * Four tiles: workouts + volume cover the 7-day window ending on the
     * selected day, calories the day itself, streak stays anchored to today.
     */
    private fun computeDashboards(referenceMillis: Long): List<HomeDashboard> {
        val now = System.currentTimeMillis()
        val weekStart = HomeDashboardMath.weekWindowStart(referenceMillis)
        val lookback = minOf(weekStart, ProgramDates.addDays(ProgramDates.startOfDay(now), -90))
        val sessions = runCatching { DatabaseManager.dashboardSessions(sessionsUserId, lookback) }.getOrDefault(emptyList())
        val thisWeek = HomeDashboardMath.sessionsThisWeek(sessions, referenceMillis)

        val calorieGoal = runCatching { DatabaseManager.nutritionGoal().calorieGoal }.getOrDefault(0)
        val dayCalories = runCatching { DatabaseManager.foodEntries(referenceMillis) }.getOrDefault(emptyList()).sumOf { it.calories }

        return HomeDashboardMath.buildTiles(
            workoutCount = thisWeek.size,
            weeklyGoal = weeklyWorkoutGoal,
            volumeDisplay = HomeDashboardMath.volumeDisplay(thisWeek),
            volumeUnitLabel = WeightUnit.current.label,
            dayCalories = dayCalories,
            calorieGoal = calorieGoal,
            calorieUnit = HomeDashboardMath.calorieUnit(referenceMillis, now),
            streak = HomeDashboardMath.workoutStreak(sessions, now)
        )
    }

    // MARK: - Server refresh

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        isRefreshing = true
        try {
            // Push pending goal / habit writes first so a goal just saved
            // can't be clobbered by a stale `list()` response.
            runCatching { SyncManager.drain(SyncDomain.GOALS) }
            runCatching { SyncManager.drain(SyncDomain.HABITS) }
            runCatching { SyncManager.drain(SyncDomain.HABIT_LOGS) }

            var goalsAreAuthoritative = false
            runCatching { withContext(Dispatchers.IO) { ApiClient.goalApi.list() } }.onSuccess { remote ->
                withContext(Dispatchers.IO) { remote.forEach { runCatching { DatabaseManager.insertGoal(it) } } }
                goalsAreAuthoritative = true
                goalSummaries = goalSummariesFromCache()
            }
            runCatching { withContext(Dispatchers.IO) { ApiClient.goalApi.getMyHabits() } }.onSuccess { remote ->
                withContext(Dispatchers.IO) {
                    remote.forEach { runCatching { DatabaseManager.insertHabit(it) } }
                    // Guarded on a successful goal pull so a failed `list()` can't make every habit look orphaned.
                    if (goalsAreAuthoritative) runCatching { DatabaseManager.purgeOrphanedHabits() }
                }
                todayHabits = todayHabitsFromCache()
                dailyProgress = dailyProgressFor(todayHabits)
            }

            // Selected day's calendar from the server (drain-aware: the
            // calendar service pushes queued deletes / moves first and skips
            // rows with pending mutations).
            val dayStart = ProgramDates.startOfDay(selectedDate.time)
            val dayEnd = ProgramDates.addDays(dayStart, 1) - 1
            runCatching { CalendarService.refresh(Date(dayStart), Date(dayEnd)) }
                .onFailure { Log.w(TAG, "calendar refresh failed: ${it.message}") }
            loadWorkoutsForSelectedDate()
            rebuildDateStrip()

            // A program started on another device: pull its enrollments.
            runCatching { UserProgramStore.syncFromServer() }
                .onFailure { Log.w(TAG, "program sync failed: ${it.message}") }
            hydrateFromCache()
        } finally {
            isRefreshing = false
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
        private val monthDayFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    }
}
