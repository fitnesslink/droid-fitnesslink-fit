package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.model.*
import com.fitnesslink.fit.persistence.DatabaseManager
import java.util.Calendar
import java.util.Date

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

    // Chart data
    var completedChartPoints by mutableStateOf<List<ChartPoint>>(emptyList())
    var durationChartPoints by mutableStateOf<List<ChartPoint>>(emptyList())
    var rpeChartPoints by mutableStateOf<List<ChartPoint>>(emptyList())
    var weightChartPoints by mutableStateOf<List<ChartPoint>>(emptyList())
    var caloriesChartPoints by mutableStateOf<List<ChartPoint>>(emptyList())

    // MARK: - Display

    val formattedTotalTime: String get() {
        val h = reportData.totalDurationSeconds / 3600
        val m = (reportData.totalDurationSeconds % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    val formattedAvgRPE: String get() =
        if (reportData.averageRPE > 0) String.format("%.1f", reportData.averageRPE) else "--"

    fun displayValue(metric: ReportMetric): String = when (metric) {
        ReportMetric.WORKOUTS_COMPLETED -> "${reportData.workoutsCompleted}"
        ReportMetric.TOTAL_TIME -> formattedTotalTime
        ReportMetric.INCOMPLETE_WORKOUTS -> "${reportData.incompleteWorkouts}"
        ReportMetric.AVERAGE_RPE -> formattedAvgRPE
        ReportMetric.EXERCISES_COMPLETED -> "${reportData.exercisesCompleted}"
        ReportMetric.TOTAL_WEIGHT_LIFTED -> formatWeight(reportData.totalWeightLifted)
        ReportMetric.CALORIES_BURNED -> formatWeight(reportData.totalCaloriesBurned)
        ReportMetric.VOLUME_TRACKING -> {
            val total = volumeEntries.sumOf { it.volume }
            formatWeight(total)
        }
        ReportMetric.PERSONAL_RECORDS -> "${personalRecords.size}"
        ReportMetric.STREAKS_FREQUENCY -> "${streak.currentStreak}"
    }

    fun subtitle(metric: ReportMetric): String = when (metric) {
        ReportMetric.WORKOUTS_COMPLETED -> "workouts"
        ReportMetric.TOTAL_TIME -> "total"
        ReportMetric.INCOMPLETE_WORKOUTS -> "workouts"
        ReportMetric.AVERAGE_RPE -> "out of 10"
        ReportMetric.EXERCISES_COMPLETED -> "movements"
        ReportMetric.TOTAL_WEIGHT_LIFTED -> "lbs lifted"
        ReportMetric.CALORIES_BURNED -> "kcal burned"
        ReportMetric.VOLUME_TRACKING -> "lbs volume"
        ReportMetric.PERSONAL_RECORDS -> "PRs set"
        ReportMetric.STREAKS_FREQUENCY -> "day streak"
    }

    // MARK: - Load

    fun loadData() {
        val userId = DatabaseManager.user()?.id ?: "user1"
        val since = sinceDate(selectedTimeRange)

        reportData = DatabaseManager.workoutReportData(userId, since)
        sessions = DatabaseManager.sessionRows(userId, since)
        volumeEntries = DatabaseManager.volumeEntries(userId, since)
        personalRecords = DatabaseManager.personalRecords(userId, since)
        workoutAggregates = DatabaseManager.workoutAggregates(userId, since)
        movementAggregates = DatabaseManager.movementAggregates(userId, since)

        val dates = DatabaseManager.workoutDates(userId, since)
        streak = computeStreak(dates)
        computeChartPoints()
    }

    // MARK: - Private

    private fun sinceDate(range: ReportTimeRange): Long {
        val days = range.days ?: return 0L
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return cal.timeInMillis
    }

    private fun formatWeight(value: Double): String =
        if (value >= 1000) String.format("%.1fk", value / 1000) else "${value.toInt()}"

    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun computeStreak(dates: List<Date>): WorkoutStreak {
        val cal = Calendar.getInstance()
        val now = Date()

        // Workouts this week/month
        cal.time = now
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val weekStart = cal.time
        cal.time = now
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val monthStart = cal.time

        val workoutsThisWeek = dates.count { it.after(weekStart) || it == weekStart }
        val workoutsThisMonth = dates.count { it.after(monthStart) || it == monthStart }

        // Unique days
        val uniqueDays = dates.map { d ->
            cal.time = d
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }.toSortedSet().toList()

        if (uniqueDays.isEmpty()) return WorkoutStreak(workoutsThisWeek = workoutsThisWeek, workoutsThisMonth = workoutsThisMonth, workoutDates = dates)

        var current = 1
        var longest = 1
        val dayMs = 86400000L
        for (i in 1 until uniqueDays.size) {
            if (uniqueDays[i] - uniqueDays[i - 1] == dayMs) {
                current++
                longest = maxOf(longest, current)
            } else {
                current = 1
            }
        }
        longest = maxOf(longest, current)

        // Current streak
        cal.time = now
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val today = cal.timeInMillis
        val yesterday = today - dayMs
        val lastDay = uniqueDays.last()

        var currentStreak = 0
        if (lastDay == today || lastDay == yesterday) {
            currentStreak = 1
            for (i in uniqueDays.size - 2 downTo 0) {
                if (uniqueDays[i + 1] - uniqueDays[i] == dayMs) {
                    currentStreak++
                } else break
            }
        }

        return WorkoutStreak(currentStreak, longest, workoutsThisWeek, workoutsThisMonth, dates)
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
            if (byWeek) {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            }
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        return grouped.map { (millis, list) ->
            ChartPoint(date = Date(millis), value = aggregate(list))
        }.sortedBy { it.date }
    }
}
