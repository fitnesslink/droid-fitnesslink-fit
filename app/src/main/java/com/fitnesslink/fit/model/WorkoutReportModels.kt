package com.fitnesslink.fit.model

import java.util.Date

// MARK: - Time Range

enum class ReportTimeRange(val label: String, val days: Int?) {
    SEVEN_DAYS("7 Days", 7),
    THIRTY_DAYS("30 Days", 30),
    ALL_TIME("All Time", null);
}

// MARK: - Report Tab

enum class ReportTab(val label: String) {
    OVERVIEW("Overview"),
    BY_WORKOUT("By Workout"),
    BY_MOVEMENT("By Movement");
}

// MARK: - Metric

enum class ReportMetric(val title: String, val icon: String, val color: String) {
    WORKOUTS_COMPLETED("Completed", "checkmark.circle.fill", "FLPrimary"),
    TOTAL_TIME("Total Time", "clock.fill", "BlueTheme"),
    INCOMPLETE_WORKOUTS("Incomplete", "exclamationmark.circle.fill", "OrangeTheme"),
    AVERAGE_RPE("Avg RPE", "gauge.medium", "PurpleTheme"),
    EXERCISES_COMPLETED("Exercises", "figure.run", "FLPrimary"),
    TOTAL_WEIGHT_LIFTED("Weight Lifted", "scalemass.fill", "BlueTheme"),
    CALORIES_BURNED("Calories", "flame.fill", "OrangeTheme"),
    VOLUME_TRACKING("Volume", "chart.bar.fill", "PurpleTheme"),
    PERSONAL_RECORDS("Records", "trophy.fill", "FLPrimary"),
    STREAKS_FREQUENCY("Streaks", "bolt.fill", "BlueTheme");

    companion object {
        /**
         * Route argument → metric. Accepts the Android enum name
         * ("TOTAL_TIME") and the iOS raw value ("Total Time") so deep links
         * written against either contract resolve.
         */
        fun fromRoute(raw: String?): ReportMetric? {
            if (raw.isNullOrBlank()) return null
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: entries.firstOrNull { it.title.equals(raw, ignoreCase = true) }
        }
    }
}

// MARK: - Aggregate Data

data class WorkoutReportData(
    val workoutsCompleted: Int = 0,
    val totalDurationSeconds: Int = 0,
    val incompleteWorkouts: Int = 0,
    val averageRPE: Double = 0.0,
    val exercisesCompleted: Int = 0,
    val totalWeightLifted: Double = 0.0,
    val totalCaloriesBurned: Double = 0.0
)

// MARK: - Volume Entry

data class VolumeEntry(
    val id: String,
    val exerciseName: String,
    val date: Date,
    val sets: Int,
    val reps: Int,
    /** Stored lbs. */
    val weight: Double
) {
    val volume: Double get() = (sets * reps).toDouble() * weight
}

// MARK: - Personal Record

data class PersonalRecord(
    val id: String,
    val exerciseName: String,
    /** Stored lbs. */
    val maxWeight: Double,
    val maxReps: Int,
    val achievedDate: Date
)

// MARK: - Streak

data class WorkoutStreak(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val workoutsThisWeek: Int = 0,
    val workoutsThisMonth: Int = 0,
    val workoutDates: List<Date> = emptyList()
)

// MARK: - RPE rules (port of iOS `SummaryRPE`, FTNSSLNK-411)

object SessionRpeRule {
    val range: IntRange = 0..10
    const val PROMPT = "How did it feel?"
    const val MIN_ANCHOR = "0 = Not difficult"
    const val MAX_ANCHOR = "10 = Maximum difficulty"
    /** How long after completion the rating may still be changed. */
    const val EDIT_WINDOW_MS: Long = 24L * 60L * 60L * 1000L

    fun clamp(value: Int): Int = value.coerceIn(range.first, range.last)

    /** Descriptor beside the number. */
    fun label(value: Int): String = when (clamp(value)) {
        0 -> "Not difficult"
        1, 2 -> "Very light"
        3, 4 -> "Light"
        5, 6 -> "Moderate"
        7, 8 -> "Hard"
        9 -> "Very hard"
        else -> "Maximum difficulty"
    }

    /** What the local row stores (the `rpeValue` REAL column). */
    fun storedValue(value: Int?): Double? = value?.let { clamp(it).toDouble() }

    /**
     * Numeric text for history — `7`, or `7.5` for a legacy half-step row.
     * Null when the session carries no rating.
     */
    fun displayText(stored: Double?): String? = stored?.let { WeightUnit.format(it) }

    /**
     * The rating may be changed once, within 24 h of completion. A row with
     * no completion date (legacy / in progress) is never editable; a row that
     * was already edited (`editedAt` set) is closed for good.
     */
    fun isEditable(completionDate: Date?, editedAt: Date?, now: Date = Date()): Boolean {
        if (completionDate == null || editedAt != null) return false
        val age = now.time - completionDate.time
        return age >= 0 && age <= EDIT_WINDOW_MS
    }
}

// MARK: - Session Row

data class SessionRow(
    val id: String,
    val workoutName: String,
    /** Session start date (`startDate` column): the sort key and the display fallback. */
    val date: Date,
    val durationSeconds: Int,
    val isCompleted: Boolean,
    val exerciseCount: Int,
    /** Stored lbs. */
    val totalWeightLifted: Double,
    val totalCaloriesBurned: Double,
    val rpeValue: Double?,
    // FTNSSLNK-319 — columns that exist in SQLite but the Wave 0 reader
    // doesn't surface. Defaulted so `DatabaseManager.readSessionRow` keeps
    // compiling; `ReportDao.fullSessionRow(s)` populates them.
    val completionDate: Date? = null,
    val totalSets: Int = 0,
    val totalReps: Int = 0,
    /** Completed ÷ required sets at save time (FTNSSLNK-403); null = unknown / 100. */
    val completionPercent: Int? = null,
    /** When the RPE was changed from history (FTNSSLNK-411); null = never. */
    val rpeEditedAt: Date? = null,
    val workoutId: String = "",
    val programId: String? = null
) {
    /** `7` / `7.5`; null when unrated. */
    val rpeText: String? get() = SessionRpeRule.displayText(rpeValue)

    /** Whether the rating can still be changed (FTNSSLNK-411). */
    fun canEditRpe(now: Date = Date()): Boolean =
        isCompleted && SessionRpeRule.isEditable(completionDate, rpeEditedAt, now)

    /** "Completed · 62%" / "Completed" / "Incomplete". */
    val statusLabel: String
        get() {
            if (!isCompleted) return "Incomplete"
            val p = completionPercent
            if (p != null && p < 100) return "Completed · $p%"
            return "Completed"
        }

    /** Completion date when the session finished, else the start date. */
    val displayDate: Date get() = completionDate ?: date
}

// MARK: - Session Exercise Row (per-set history)

data class SessionExerciseRow(
    val taskName: String,
    val reps: Int,
    val setNumber: Int,
    /** Stored lbs. */
    val weightLifted: Double,
    val logDate: Date
)

// MARK: - Chart Data Point

data class ChartPoint(
    val date: Date,
    val value: Double,
    val label: String = ""
)

// MARK: - Workout Aggregate

data class WorkoutAggregate(
    val id: String,
    val workoutName: String,
    val timesCompleted: Int = 0,
    val avgDurationSeconds: Int = 0,
    val avgRPE: Double = 0.0,
    val totalWeightLifted: Double = 0.0,
    val totalVolume: Double = 0.0,
    val totalCaloriesBurned: Double = 0.0,
    val sessions: List<SessionRow> = emptyList()
) {
    val latestSession: SessionRow? get() = sessions.firstOrNull()
    val previousSession: SessionRow? get() = sessions.getOrNull(1)
    val bestSession: SessionRow? get() = sessions.maxByOrNull { it.totalWeightLifted }

    val volumeTrend: TrendDirection get() {
        val latest = latestSession ?: return TrendDirection.FLAT
        val prev = previousSession ?: return TrendDirection.FLAT
        return when {
            latest.totalWeightLifted > prev.totalWeightLifted * 1.05 -> TrendDirection.UP
            latest.totalWeightLifted < prev.totalWeightLifted * 0.95 -> TrendDirection.DOWN
            else -> TrendDirection.FLAT
        }
    }

    val rpeTrend: TrendDirection get() {
        val latest = latestSession?.rpeValue ?: return TrendDirection.FLAT
        val prev = previousSession?.rpeValue ?: return TrendDirection.FLAT
        return when {
            latest > prev + 0.5 -> TrendDirection.UP
            latest < prev - 0.5 -> TrendDirection.DOWN
            else -> TrendDirection.FLAT
        }
    }

    val durationTrend: TrendDirection get() {
        val latest = latestSession ?: return TrendDirection.FLAT
        val prev = previousSession ?: return TrendDirection.FLAT
        return when {
            latest.durationSeconds > prev.durationSeconds + 120 -> TrendDirection.UP
            latest.durationSeconds < prev.durationSeconds - 120 -> TrendDirection.DOWN
            else -> TrendDirection.FLAT
        }
    }

    companion object {
        /**
         * Group session rows by workout name the way the Wave 0
         * `DatabaseManager.workoutAggregates` does, but over rows that carry
         * the full column set (completionPercent etc.). Sorted by times
         * completed, sessions newest first.
         */
        fun build(rows: List<SessionRow>): List<WorkoutAggregate> =
            rows.groupBy { it.workoutName }.map { (name, group) ->
                val completed = group.filter { it.isCompleted }
                val avgDur = if (completed.isEmpty()) 0 else completed.sumOf { it.durationSeconds } / completed.size
                val rpes = completed.mapNotNull { it.rpeValue }
                WorkoutAggregate(
                    id = name,
                    workoutName = name,
                    timesCompleted = completed.size,
                    avgDurationSeconds = avgDur,
                    avgRPE = if (rpes.isEmpty()) 0.0 else rpes.average(),
                    totalWeightLifted = completed.sumOf { it.totalWeightLifted },
                    totalVolume = completed.sumOf { it.totalWeightLifted },
                    totalCaloriesBurned = completed.sumOf { it.totalCaloriesBurned },
                    sessions = group.sortedByDescending { it.date }
                )
            }.sortedByDescending { it.timesCompleted }
    }
}

// MARK: - Movement Aggregate

data class MovementAggregate(
    val id: String,
    val exerciseName: String,
    val timesPerformed: Int = 0,
    val totalSets: Int = 0,
    val totalReps: Int = 0,
    /** Stored lbs. */
    val maxWeight: Double = 0.0,
    val totalVolume: Double = 0.0,
    val prWeight: Double = 0.0,
    val prReps: Int = 0,
    val prDate: Date = Date(),
    val entries: List<MovementHistoryEntry> = emptyList()
)

data class MovementHistoryEntry(
    /** Session id. */
    val id: String,
    val sessionDate: Date,
    val workoutName: String,
    val sets: Int,
    val reps: Int,
    /** Max stored lbs across the session's sets. */
    val weight: Double,
    val setDetails: List<MovementSetDetail> = emptyList()
) {
    val volume: Double get() = setDetails.sumOf { it.reps.toDouble() * it.weightLifted }
}

data class MovementSetDetail(
    val setNumber: Int,
    val reps: Int,
    /** Stored lbs. */
    val weightLifted: Double,
    /** Set on time-based tasks (plank, treadmill); null for rep sets. */
    val durationSeconds: Int? = null,
    /** Logged distance, when the set recorded one (FTNSSLNK-409). */
    val distance: DistanceValue? = null
)

// MARK: - Trend

enum class TrendDirection(val icon: String, val colorName: String) {
    UP("arrow_upward", "FLPrimary"),
    DOWN("arrow_downward", "OrangeTheme"),
    FLAT("arrow_forward", "MediumGray");
}
