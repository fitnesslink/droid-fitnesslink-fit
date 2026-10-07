package com.fitnesslink.fit.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.*
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.fullSessionRows
import com.fitnesslink.fit.reports.SessionHistoryHydrator
import com.fitnesslink.fit.session.SessionRecorder
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Port of iOS `WorkoutReportViewModel`. Reads local SQLite; `load()` first
 * shows cached data, then runs the `/sessions/me` backfill
 * ([SessionHistoryHydrator]) and re-reads. Weight totals are summed in
 * stored lbs and converted only when rendered (FTNSSLNK-325).
 */
class WorkoutReportViewModel : ViewModel() {
    var reportData by mutableStateOf(WorkoutReportData())
    var selectedTimeRange by mutableStateOf(ReportTimeRange.THIRTY_DAYS)
    var selectedTab by mutableStateOf(ReportTab.OVERVIEW)
    var sessions by mutableStateOf<List<SessionRow>>(emptyList())
    var volumeEntries by mutableStateOf<List<VolumeEntry>>(emptyList())
    var personalRecords by mutableStateOf<List<PersonalRecord>>(emptyList())
    var streak by mutableStateOf(WorkoutStreak())
    var workoutAggregates by mutableStateOf<List<WorkoutAggregate>>(emptyList())
    var movementAggregates by mutableStateOf<List<MovementAggregate>>(emptyList())

    /** Pull-to-refresh spinner. */
    var isRefreshing by mutableStateOf(false)
        private set
    /** True while the first server hydration is in flight (local data already shown). */
    var isHydrating by mutableStateOf(false)
        private set

    // Chart data
    var completedChartPoints by mutableStateOf<List<ChartPoint>>(emptyList())
    var durationChartPoints by mutableStateOf<List<ChartPoint>>(emptyList())
    var rpeChartPoints by mutableStateOf<List<ChartPoint>>(emptyList())
    var weightChartPoints by mutableStateOf<List<ChartPoint>>(emptyList())
    var caloriesChartPoints by mutableStateOf<List<ChartPoint>>(emptyList())

    // MARK: - Display

    val formattedTotalTime: String get() = formatDuration(reportData.totalDurationSeconds)

    val formattedAvgRPE: String get() =
        if (reportData.averageRPE > 0) String.format(Locale.US, "%.1f", reportData.averageRPE) else "--"

    /** Stored lbs → display unit, k-formatted ("12.4k"). */
    val formattedWeight: String get() = formatWeight(reportData.totalWeightLifted)

    val formattedCalories: String get() = formatCount(reportData.totalCaloriesBurned)

    fun displayValue(metric: ReportMetric): String = when (metric) {
        ReportMetric.WORKOUTS_COMPLETED -> "${reportData.workoutsCompleted}"
        ReportMetric.TOTAL_TIME -> formattedTotalTime
        ReportMetric.INCOMPLETE_WORKOUTS -> "${reportData.incompleteWorkouts}"
        ReportMetric.AVERAGE_RPE -> formattedAvgRPE
        ReportMetric.EXERCISES_COMPLETED -> "${reportData.exercisesCompleted}"
        ReportMetric.TOTAL_WEIGHT_LIFTED -> formattedWeight
        ReportMetric.CALORIES_BURNED -> formattedCalories
        ReportMetric.VOLUME_TRACKING -> formatWeight(volumeEntries.sumOf { it.volume })
        ReportMetric.PERSONAL_RECORDS -> "${personalRecords.size}"
        ReportMetric.STREAKS_FREQUENCY -> "${streak.currentStreak}"
    }

    fun subtitle(metric: ReportMetric): String = when (metric) {
        ReportMetric.WORKOUTS_COMPLETED -> "workouts"
        ReportMetric.TOTAL_TIME -> "total"
        ReportMetric.INCOMPLETE_WORKOUTS -> "workouts"
        ReportMetric.AVERAGE_RPE -> "out of 10"
        ReportMetric.EXERCISES_COMPLETED -> "movements"
        ReportMetric.TOTAL_WEIGHT_LIFTED -> "${WeightUnit.current.label} lifted"
        ReportMetric.CALORIES_BURNED -> "kcal burned"
        ReportMetric.VOLUME_TRACKING -> "${WeightUnit.current.label} volume"
        ReportMetric.PERSONAL_RECORDS -> "PRs set"
        ReportMetric.STREAKS_FREQUENCY -> "day streak"
    }

    /** The series the metric's detail chart renders; empty for list-only metrics. */
    fun chartPoints(metric: ReportMetric): List<ChartPoint> = when (metric) {
        ReportMetric.WORKOUTS_COMPLETED, ReportMetric.EXERCISES_COMPLETED -> completedChartPoints
        ReportMetric.TOTAL_TIME -> durationChartPoints
        ReportMetric.AVERAGE_RPE -> rpeChartPoints
        ReportMetric.TOTAL_WEIGHT_LIFTED, ReportMetric.VOLUME_TRACKING -> weightChartPoints
        ReportMetric.CALORIES_BURNED -> caloriesChartPoints
        ReportMetric.INCOMPLETE_WORKOUTS, ReportMetric.PERSONAL_RECORDS, ReportMetric.STREAKS_FREQUENCY -> emptyList()
    }

    // MARK: - Load

    /**
     * Show cached data instantly, pull the server's sessions and backfill
     * anything missing locally, then refresh. Safe to call repeatedly.
     */
    suspend fun load() {
        loadData()
        isHydrating = true
        try {
            val inserted = SessionHistoryHydrator.hydrate()
            if (inserted > 0) loadData()
        } catch (e: Exception) {
            Log.w("WorkoutReport", "hydrate failed: ${e.message}")
        } finally {
            isHydrating = false
        }
    }

    /** Pull-to-refresh entry point. */
    fun refresh() {
        if (isRefreshing) return
        viewModelScope.launch {
            isRefreshing = true
            try {
                load()
            } finally {
                isRefreshing = false
            }
        }
    }

    /** Re-read SQLite for the selected range. Synchronous (fast local reads). */
    fun loadData() {
        val userId = DatabaseManager.user()?.id ?: "user1"
        val since = sinceDate(selectedTimeRange)

        reportData = DatabaseManager.workoutReportData(userId, since)
        sessions = DatabaseManager.fullSessionRows(userId, since)
        volumeEntries = DatabaseManager.volumeEntries(userId, since)
        personalRecords = DatabaseManager.personalRecords(userId, since)
        workoutAggregates = WorkoutAggregate.build(sessions)
        movementAggregates = DatabaseManager.movementAggregates(userId, since)

        val dates = DatabaseManager.workoutDates(userId, since)
        streak = computeStreak(dates)
        computeChartPoints()
    }

    /**
     * Delete a session from history (soft delete local, revert calendar,
     * queue `DELETE /sessions/{id}`) via the session engine, then re-read.
     */
    fun deleteSession(sessionId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                SessionRecorder.delete(sessionId)
            } catch (e: Exception) {
                Log.w("WorkoutReport", "delete $sessionId failed: ${e.message}")
            }
            loadData()
            onDone()
        }
    }

    // MARK: - Formatting

    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    /**
     * Compact display-unit figure for stat tiles: converts stored lbs into
     * the preferred unit (FTNSSLNK-325), then k-formats large values.
     */
    fun formatWeight(lbs: Double): String = formatCount(WeightUnit.displayValue(lbs))

    private fun formatCount(value: Double): String =
        if (value >= 1000) String.format(Locale.US, "%.1fk", value / 1000) else "${Math.round(value)}"

    // MARK: - Private

    private fun sinceDate(range: ReportTimeRange): Long {
        val days = range.days ?: return 0L
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return cal.timeInMillis
    }

    private fun computeStreak(dates: List<Date>): WorkoutStreak {
        val cal = Calendar.getInstance()
        val now = Date()

        // Workouts this week/month
        cal.time = now
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val weekStart = cal.time
        cal.time = now
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val monthStart = cal.time

        val workoutsThisWeek = dates.count { !it.before(weekStart) }
        val workoutsThisMonth = dates.count { !it.before(monthStart) }

        // Unique days
        val uniqueDays = dates.map { d ->
            cal.time = d
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }.toSortedSet().toList()

        if (uniqueDays.isEmpty()) return WorkoutStreak(workoutsThisWeek = workoutsThisWeek, workoutsThisMonth = workoutsThisMonth, workoutDates = dates)

        var current = 1
        var longest = 1
        for (i in 1 until uniqueDays.size) {
            if (isNextDay(uniqueDays[i - 1], uniqueDays[i])) {
                current++
                longest = maxOf(longest, current)
            } else {
                current = 1
            }
        }
        longest = maxOf(longest, current)

        // Current streak: count backwards from today
        cal.time = now
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val today = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = cal.timeInMillis
        val lastDay = uniqueDays.last()

        var currentStreak = 0
        if (lastDay == today || lastDay == yesterday) {
            currentStreak = 1
            for (i in uniqueDays.size - 2 downTo 0) {
                if (isNextDay(uniqueDays[i], uniqueDays[i + 1])) currentStreak++ else break
            }
        }

        return WorkoutStreak(currentStreak, longest, workoutsThisWeek, workoutsThisMonth, dates)
    }

    /** Calendar-day adjacency that survives DST (a 23 h / 25 h "day"). */
    private fun isNextDay(earlierMidnight: Long, laterMidnight: Long): Boolean {
        val diffHours = (laterMidnight - earlierMidnight) / 3_600_000L
        return diffHours in 23..25
    }

    private fun computeChartPoints() {
        val useWeekly = selectedTimeRange != ReportTimeRange.SEVEN_DAYS
        val completed = sessions.filter { it.isCompleted }

        completedChartPoints = groupChart(completed, useWeekly) { it.size.toDouble() }
        durationChartPoints = groupChart(completed, useWeekly) { list -> list.sumOf { it.durationSeconds }.toDouble() / 60.0 }
        rpeChartPoints = groupChart(completed.filter { it.rpeValue != null }, useWeekly) { list ->
            val rpes = list.mapNotNull { it.rpeValue }
            if (rpes.isEmpty()) 0.0 else rpes.average()
        }
        weightChartPoints = groupChart(completed, useWeekly) { list -> list.sumOf { it.totalWeightLifted } }
        caloriesChartPoints = groupChart(completed, useWeekly) { list -> list.sumOf { it.totalCaloriesBurned } }
    }

    private fun groupChart(
        sessions: List<SessionRow>,
        byWeek: Boolean,
        aggregate: (List<SessionRow>) -> Double
    ): List<ChartPoint> {
        val cal = Calendar.getInstance()
        val grouped = sessions.groupBy { session ->
            cal.time = session.date
            if (byWeek) cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        return grouped.map { (millis, list) -> ChartPoint(date = Date(millis), value = aggregate(list)) }
            .sortedBy { it.date }
    }
}
