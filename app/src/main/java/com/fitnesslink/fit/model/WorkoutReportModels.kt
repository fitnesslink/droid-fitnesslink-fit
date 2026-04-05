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
    val weight: Double
) {
    val volume: Double get() = (sets * reps).toDouble() * weight
}

// MARK: - Personal Record

data class PersonalRecord(
    val id: String,
    val exerciseName: String,
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

// MARK: - Session Row

data class SessionRow(
    val id: String,
    val workoutName: String,
    val date: Date,
    val durationSeconds: Int,
    val isCompleted: Boolean,
    val exerciseCount: Int,
    val totalWeightLifted: Double,
    val totalCaloriesBurned: Double,
    val rpeValue: Double?
)

// MARK: - Session Exercise Row

data class SessionExerciseRow(
    val taskName: String,
    val reps: Int,
    val setNumber: Int,
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
}

// MARK: - Movement Aggregate

data class MovementAggregate(
    val id: String,
    val exerciseName: String,
    val timesPerformed: Int = 0,
    val totalSets: Int = 0,
    val totalReps: Int = 0,
    val maxWeight: Double = 0.0,
    val totalVolume: Double = 0.0,
    val prWeight: Double = 0.0,
    val prReps: Int = 0,
    val prDate: Date = Date(),
    val entries: List<MovementHistoryEntry> = emptyList()
)

data class MovementHistoryEntry(
    val id: String,
    val sessionDate: Date,
    val workoutName: String,
    val sets: Int,
    val reps: Int,
    val weight: Double,
    val setDetails: List<MovementSetDetail> = emptyList()
) {
    val volume: Double get() = setDetails.sumOf { it.reps.toDouble() * it.weightLifted }
}

data class MovementSetDetail(
    val setNumber: Int,
    val reps: Int,
    val weightLifted: Double
)

// MARK: - Trend

enum class TrendDirection(val icon: String, val colorName: String) {
    UP("arrow_upward", "FLPrimary"),
    DOWN("arrow_downward", "OrangeTheme"),
    FLAT("arrow_forward", "MediumGray");
}
