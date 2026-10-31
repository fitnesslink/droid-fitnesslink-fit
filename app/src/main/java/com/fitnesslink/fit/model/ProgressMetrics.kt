package com.fitnesslink.fit.model

import android.content.Context
import com.fitnesslink.fit.persistence.DatabaseManager
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// MARK: - Shared metric registry (port of iOS `ProgressMetrics.swift`, Progress PRD §6.5)
//
// One definition per statistic — name, icon, accent, category, data source
// and formatting — shared by Quick Stats, All Stats, Customize Quick Stats
// and the category reports, so a metric never shows two different values on
// two screens. Calculations live in `progress/ProgressMetricsEngine`.
//
// `icon` keeps the iOS SF Symbol name; `ui/progress/ProgressUi.kt` maps it to
// a Material icon. `color` is a theme colour name (`reportThemeColor`).

enum class ProgressCategory(val title: String) {
    TRAINING("Training & Performance"),
    NUTRITION("Nutrition"),
    ACTIVITY("Activity & Cardio"),
    RECOVERY("Recovery & Wellness"),
    BODY("Body Metrics"),
    GOALS("Goals & Habits");

    val report: ProgressReport
        get() = when (this) {
            TRAINING -> ProgressReport.WORKOUT
            NUTRITION -> ProgressReport.NUTRITION
            ACTIVITY -> ProgressReport.ACTIVITY
            RECOVERY -> ProgressReport.RECOVERY
            BODY -> ProgressReport.BODY
            GOALS -> ProgressReport.GOALS
        }

    val metrics: List<ProgressMetric> get() = ProgressMetric.entries.filter { it.category == this }
}

/**
 * Where a metric's data comes from — drives the unavailable-data state and
 * the connection prompt (PRD §6.4 / §9.2). `HEALTH` is Apple Health on iOS;
 * on Android it is the device health platform (Health Connect), which is not
 * integrated yet, so those metrics always read as not connected.
 */
enum class MetricSource { WORKOUTS, NUTRITION, HEALTH, BODY_LOG, GOALS }

/** How the current period is compared with the previous one. */
enum class MetricComparison {
    /** Equivalent-period comparison (PRD §10.2). */
    PREVIOUS_PERIOD,
    /** A state or a running count — no meaningful previous value. */
    NONE
}

enum class ProgressMetric(val title: String) {
    // Training & Performance
    TOTAL_WORKOUTS("Total Workouts"),
    TOTAL_TIME("Total Time"),
    TRAINING_VOLUME("Training Volume"),
    AVG_WORKOUT_DURATION("Avg Workout Duration"),
    AVERAGE_RPE("Average RPE"),
    TOTAL_SETS("Total Sets"),
    TOTAL_REPS("Total Reps"),
    PERSONAL_RECORDS("Personal Records"),
    WORKOUT_STREAK("Workout Streak"),
    INCOMPLETE_WORKOUTS("Incomplete Workouts"),
    CALORIES_BURNED("Calories Burned"),
    // Nutrition
    AVG_CALORIES_CONSUMED("Avg Daily Calories"),
    AVG_PROTEIN("Protein"),
    AVG_CARBS("Carbs"),
    AVG_FAT("Fat"),
    CALORIE_GOAL_ADHERENCE("Calorie Goal Adherence"),
    // Activity & Cardio
    AVERAGE_STEPS("Average Steps"),
    TOTAL_STEPS("Total Steps"),
    ACTIVE_CALORIES("Active Calories"),
    ACTIVE_MINUTES("Active Minutes"),
    // Recovery & Wellness
    AVERAGE_SLEEP("Average Sleep"),
    RESTING_HEART_RATE("Resting Heart Rate"),
    HRV("HRV (Avg)"),
    // Body Metrics
    BODY_WEIGHT("Body Weight"),
    WEIGHT_CHANGE("Weight Change"),
    BODY_FAT("Body Fat"),
    WAIST("Waist"),
    HIPS("Hips"),
    // Goals & Habits
    ACTIVE_GOALS("Active Goals"),
    GOALS_COMPLETED("Goals Completed"),
    LONGEST_STREAK("Longest Streak");

    /** iOS raw value (camelCase) — what Quick Stats persists, so both apps agree. */
    val rawValue: String
        get() = when (this) {
            AVERAGE_RPE -> "averageRPE"
            else -> name.lowercase(Locale.US).split('_').mapIndexed { i, part ->
                if (i == 0) part else part.replaceFirstChar { it.uppercase(Locale.US) }
            }.joinToString("")
        }

    /** Label under the value on the compact Quick Stats card. */
    val shortTitle: String
        get() = when (this) {
            TOTAL_WORKOUTS -> "Workouts"
            AVG_WORKOUT_DURATION -> "Avg Duration"
            AVERAGE_STEPS -> "Avg Steps"
            AVERAGE_SLEEP -> "Avg Sleep"
            RESTING_HEART_RATE -> "Resting HR"
            AVG_CALORIES_CONSUMED -> "Calories Eaten"
            CALORIE_GOAL_ADHERENCE -> "Calorie Goal"
            INCOMPLETE_WORKOUTS -> "Incomplete"
            else -> title
        }

    /** One line on how the number is calculated and where it comes from. */
    val definition: String
        get() = when (this) {
            TOTAL_WORKOUTS -> "Completed workouts in the period."
            TOTAL_TIME -> "Time recorded across your workout sessions."
            TRAINING_VOLUME -> "Weight × reps across working sets."
            AVG_WORKOUT_DURATION -> "Average length of a completed workout."
            AVERAGE_RPE -> "Average effort rating of completed workouts."
            TOTAL_SETS -> "Sets completed in finished workouts."
            TOTAL_REPS -> "Reps completed in finished workouts."
            PERSONAL_RECORDS -> "Movements with a weighted top set logged in the period, as counted in the Workout Report."
            WORKOUT_STREAK -> "Consecutive days with a completed workout, ending today or yesterday."
            INCOMPLETE_WORKOUTS -> "Sessions started but not finished."
            CALORIES_BURNED -> "Workout calories from your FitnessLink sessions (estimated)."
            AVG_CALORIES_CONSUMED -> "Average food calories on days you logged food."
            AVG_PROTEIN -> "Average daily protein on days you logged food."
            AVG_CARBS -> "Average daily carbohydrates on days you logged food."
            AVG_FAT -> "Average daily fat on days you logged food."
            CALORIE_GOAL_ADHERENCE -> "Share of logged days within your calorie target."
            AVERAGE_STEPS -> "Average daily steps from Health Connect, on days with data."
            TOTAL_STEPS -> "Steps recorded in Health Connect."
            ACTIVE_CALORIES -> "Average daily active energy from Health Connect (not food, not workouts alone)."
            ACTIVE_MINUTES -> "Average daily exercise minutes from Health Connect."
            AVERAGE_SLEEP -> "Average time asleep per night from Health Connect."
            RESTING_HEART_RATE -> "Average resting heart rate from Health Connect."
            HRV -> "Average heart-rate variability from Health Connect."
            BODY_WEIGHT -> "Your latest logged weight in the period."
            WEIGHT_CHANGE -> "Change between your first and latest weigh-in in the period."
            BODY_FAT -> "Latest body-fat reading from Health Connect."
            WAIST -> "Your latest waist measurement in the period."
            HIPS -> "Your latest hip measurement in the period."
            ACTIVE_GOALS -> "Goals currently in progress."
            GOALS_COMPLETED -> "Goals completed in the period."
            LONGEST_STREAK -> "Longest run of consecutive workout days in the period."
        }

    /** iOS SF Symbol name; mapped to a Material icon in the UI layer. */
    val icon: String
        get() = when (this) {
            TOTAL_WORKOUTS -> "dumbbell.fill"
            TOTAL_TIME -> "clock"
            TRAINING_VOLUME -> "chart.bar.fill"
            AVG_WORKOUT_DURATION -> "timer"
            AVERAGE_RPE -> "gauge.medium"
            TOTAL_SETS -> "square.stack.3d.up.fill"
            TOTAL_REPS -> "repeat"
            PERSONAL_RECORDS -> "trophy.fill"
            WORKOUT_STREAK -> "bolt.fill"
            INCOMPLETE_WORKOUTS -> "exclamationmark.circle"
            CALORIES_BURNED -> "flame.fill"
            AVG_CALORIES_CONSUMED -> "fork.knife"
            AVG_PROTEIN -> "fish.fill"
            AVG_CARBS -> "leaf.fill"
            AVG_FAT -> "drop.fill"
            CALORIE_GOAL_ADHERENCE -> "target"
            AVERAGE_STEPS, TOTAL_STEPS -> "shoeprints.fill"
            ACTIVE_CALORIES -> "bolt.heart.fill"
            ACTIVE_MINUTES -> "figure.run"
            AVERAGE_SLEEP -> "moon.fill"
            RESTING_HEART_RATE -> "heart.fill"
            HRV -> "waveform.path.ecg"
            BODY_WEIGHT -> "scalemass.fill"
            WEIGHT_CHANGE -> "arrow.up.arrow.down"
            BODY_FAT -> "figure.stand"
            WAIST, HIPS -> "ruler"
            ACTIVE_GOALS -> "scope"
            GOALS_COMPLETED -> "checkmark.seal.fill"
            LONGEST_STREAK -> "flame"
        }

    /** Theme accent name; groups metric types visually (PRD §5.4). */
    val color: String
        get() = when (this) {
            TOTAL_WORKOUTS, TRAINING_VOLUME, AVERAGE_RPE, TOTAL_SETS, TOTAL_REPS, INCOMPLETE_WORKOUTS -> "PurpleTheme"
            TOTAL_TIME, AVG_WORKOUT_DURATION, BODY_WEIGHT, WEIGHT_CHANGE, BODY_FAT, WAIST, HIPS -> "BlueTheme"
            CALORIES_BURNED, AVG_CALORIES_CONSUMED, AVG_FAT, ACTIVE_CALORIES, WORKOUT_STREAK, PERSONAL_RECORDS -> "OrangeTheme"
            AVERAGE_SLEEP, HRV -> "PurpleTheme"
            else -> "FLPrimary"
        }

    val category: ProgressCategory
        get() = when (this) {
            TOTAL_WORKOUTS, TOTAL_TIME, TRAINING_VOLUME, AVG_WORKOUT_DURATION, AVERAGE_RPE,
            TOTAL_SETS, TOTAL_REPS, PERSONAL_RECORDS, WORKOUT_STREAK, INCOMPLETE_WORKOUTS -> ProgressCategory.TRAINING
            AVG_CALORIES_CONSUMED, AVG_PROTEIN, AVG_CARBS, AVG_FAT, CALORIE_GOAL_ADHERENCE -> ProgressCategory.NUTRITION
            CALORIES_BURNED, AVERAGE_STEPS, TOTAL_STEPS, ACTIVE_CALORIES, ACTIVE_MINUTES -> ProgressCategory.ACTIVITY
            AVERAGE_SLEEP, RESTING_HEART_RATE, HRV -> ProgressCategory.RECOVERY
            BODY_WEIGHT, WEIGHT_CHANGE, BODY_FAT, WAIST, HIPS -> ProgressCategory.BODY
            ACTIVE_GOALS, GOALS_COMPLETED, LONGEST_STREAK -> ProgressCategory.GOALS
        }

    val source: MetricSource
        get() = when (this) {
            TOTAL_WORKOUTS, TOTAL_TIME, TRAINING_VOLUME, AVG_WORKOUT_DURATION, AVERAGE_RPE,
            TOTAL_SETS, TOTAL_REPS, PERSONAL_RECORDS, WORKOUT_STREAK, INCOMPLETE_WORKOUTS,
            CALORIES_BURNED, LONGEST_STREAK -> MetricSource.WORKOUTS
            AVG_CALORIES_CONSUMED, AVG_PROTEIN, AVG_CARBS, AVG_FAT, CALORIE_GOAL_ADHERENCE -> MetricSource.NUTRITION
            AVERAGE_STEPS, TOTAL_STEPS, ACTIVE_CALORIES, ACTIVE_MINUTES,
            AVERAGE_SLEEP, RESTING_HEART_RATE, HRV, BODY_FAT -> MetricSource.HEALTH
            BODY_WEIGHT, WEIGHT_CHANGE, WAIST, HIPS -> MetricSource.BODY_LOG
            ACTIVE_GOALS, GOALS_COMPLETED -> MetricSource.GOALS
        }

    val comparison: MetricComparison
        get() = when (this) {
            PERSONAL_RECORDS, WORKOUT_STREAK, WEIGHT_CHANGE, ACTIVE_GOALS, LONGEST_STREAK -> MetricComparison.NONE
            else -> MetricComparison.PREVIOUS_PERIOD
        }

    /** Shown when the metric has nothing to display (missing is never rendered as zero). */
    val emptyMessage: String
        get() = when (source) {
            MetricSource.WORKOUTS -> "Complete a workout to see this stat."
            MetricSource.NUTRITION -> "Log food to see this stat."
            MetricSource.HEALTH -> when (this) {
                AVERAGE_SLEEP -> "No sleep data in Health Connect for this period."
                AVERAGE_STEPS, TOTAL_STEPS -> "No step data in Health Connect for this period."
                HRV -> "No HRV data — HRV needs a compatible wearable."
                else -> "No Health Connect data for this period."
            }
            MetricSource.BODY_LOG -> when (this) {
                WAIST, HIPS -> "Log a measurement to see this stat."
                else -> "Log your weight to see this stat."
            }
            MetricSource.GOALS -> "Set a goal to see this stat."
        }

    /** Contextual connection prompt (PRD §9.2). */
    val connectPrompt: String
        get() = when (this) {
            AVERAGE_SLEEP -> "Connect a supported health data source to track your sleep."
            AVERAGE_STEPS, TOTAL_STEPS -> "Connect a supported activity source to view your step trends."
            HRV -> "Connect a compatible device or health data source to view HRV, if supported."
            else -> "Connect Health Connect to see this stat."
        }

    // MARK: Formatting (respects weight / measurement unit preferences, PRD §10.4)

    fun format(value: Double): String = when (this) {
        TOTAL_WORKOUTS, TOTAL_SETS, TOTAL_REPS, PERSONAL_RECORDS, INCOMPLETE_WORKOUTS,
        ACTIVE_GOALS, GOALS_COMPLETED -> grouped(value)
        TOTAL_TIME, AVG_WORKOUT_DURATION, AVERAGE_SLEEP -> duration(value)
        TRAINING_VOLUME -> {
            val display = WeightUnit.displayValue(value)
            val label = WeightUnit.current.label
            if (display >= 1000) String.format(Locale.US, "%.1fk %s", display / 1000, label)
            else "${display.roundToLong()} $label"
        }
        AVERAGE_RPE -> String.format(Locale.US, "%.1f / 10", value)
        WORKOUT_STREAK, LONGEST_STREAK -> if (value == 1.0) "1 day" else "${value.toInt()} days"
        CALORIES_BURNED, AVG_CALORIES_CONSUMED, ACTIVE_CALORIES -> "${grouped(value)} kcal"
        AVG_PROTEIN, AVG_CARBS, AVG_FAT -> "${value.roundToLong()} g"
        CALORIE_GOAL_ADHERENCE -> "${value.roundToLong()}%"
        AVERAGE_STEPS, TOTAL_STEPS -> grouped(value)
        ACTIVE_MINUTES -> "${value.roundToLong()} min"
        RESTING_HEART_RATE -> "${value.roundToLong()} bpm"
        HRV -> "${value.roundToLong()} ms"
        BODY_WEIGHT -> WeightUnit.displayString(value)
        WEIGHT_CHANGE -> {
            val display = WeightUnit.displayValue(value)
            val sign = if (display > 0) "+" else ""
            "$sign${String.format(Locale.US, "%.1f", display)} ${WeightUnit.current.label}"
        }
        BODY_FAT -> String.format(Locale.US, "%.1f%%", value)
        WAIST, HIPS -> {
            // Stored in inches by the engine; shown in the locale unit.
            val unit = MeasurementUnit.defaultUnit
            val display = MeasurementUnit.INCHES.convert(value, unit)
            "${String.format(Locale.US, "%.1f", display)} ${unit.label}"
        }
    }

    companion object {
        /** The six Quick Stats for users who haven't customized (PRD §5.2). */
        val defaultQuickStats: List<ProgressMetric> = listOf(
            TOTAL_WORKOUTS, TOTAL_TIME, TRAINING_VOLUME, CALORIES_BURNED, AVERAGE_STEPS, AVERAGE_SLEEP
        )

        const val MAX_QUICK_STATS = 6

        fun fromRaw(raw: String?): ProgressMetric? =
            raw?.let { r -> entries.firstOrNull { it.rawValue == r || it.name == r } }

        private fun grouped(value: Double): String =
            NumberFormat.getIntegerInstance().format(value.roundToLong())

        private fun duration(seconds: Double): String {
            val total = seconds.roundToLong()
            val h = total / 3600
            val m = (total % 3600) / 60
            return if (h > 0) "${h}h ${m}m" else "${m}m"
        }
    }
}

// MARK: - Computed result

/** Why a metric has no value (PRD §10.3 distinguishes these from zero). */
enum class MetricAvailability {
    AVAILABLE,
    /** Nothing recorded for the period (or not enough to calculate). */
    NO_DATA,
    /** Needs a health-data connection the user hasn't made. */
    NOT_CONNECTED
}

sealed class MetricChange {
    data class Up(val percent: Double) : MetricChange()
    data class Down(val percent: Double) : MetricChange()
    data object Steady : MetricChange()

    companion object {
        /** Below this, the change is reported as "No change". */
        const val STEADY_THRESHOLD_PERCENT = 1.0
    }
}

data class MetricResult(
    val metric: ProgressMetric,
    val current: Double? = null,
    val previous: Double? = null,
    /** Trend points for the current period (daily or weekly buckets). */
    val series: List<Double> = emptyList(),
    val availability: MetricAvailability = MetricAvailability.NO_DATA
) {
    val displayValue: String
        get() {
            val value = current
            if (availability != MetricAvailability.AVAILABLE || value == null) return "—"
            return metric.format(value)
        }

    /**
     * Percentage change vs. the previous equivalent period — only when both
     * values exist and the baseline is non-zero (PRD §5.5 / §10.2).
     */
    val change: MetricChange?
        get() {
            if (metric.comparison != MetricComparison.PREVIOUS_PERIOD || availability != MetricAvailability.AVAILABLE) return null
            val cur = current ?: return null
            val prev = previous ?: return null
            if (prev <= 0) return null
            val percent = (cur - prev) / prev * 100
            if (abs(percent) < MetricChange.STEADY_THRESHOLD_PERCENT) return MetricChange.Steady
            return if (percent > 0) MetricChange.Up(percent) else MetricChange.Down(-percent)
        }

    /** Mini trend only with enough points to mean something (PRD §5.5). */
    val hasTrend: Boolean get() = availability == MetricAvailability.AVAILABLE && series.size >= 3

    companion object {
        fun unavailable(metric: ProgressMetric, availability: MetricAvailability = MetricAvailability.NO_DATA) =
            MetricResult(metric = metric, availability = availability)
    }
}

/** Rounded whole percent for change labels. */
fun MetricChange.percentLabel(): String? = when (this) {
    is MetricChange.Up -> "${percent.roundToInt()}%"
    is MetricChange.Down -> "${percent.roundToInt()}%"
    MetricChange.Steady -> null
}

// MARK: - Reports (Progress PRD §8)

enum class ProgressReport(val title: String, val summary: String, val icon: String, val color: String) {
    WORKOUT("Workout Report", "Workouts, volume, performance and personal records", "dumbbell.fill", "PurpleTheme"),
    NUTRITION("Nutrition Report", "Calories, macros, meal breakdowns and food trends", "fork.knife", "OrangeTheme"),
    ACTIVITY("Activity & Cardio Report", "Steps, active minutes, cardio and heart rate", "figure.run", "FLPrimary"),
    RECOVERY("Recovery & Wellness Report", "Sleep, resting heart rate, HRV and supported recovery metrics", "moon.fill", "PurpleTheme"),
    BODY("Body Metrics Report", "Weight, body composition and measurements", "scalemass.fill", "BlueTheme"),
    GOALS("Goals & Habits Report", "Goal progress, habits, milestones and consistency streaks", "target", "FLPrimary");

    /** iOS raw value — the `progressReport/{report}` route argument. */
    val rawValue: String get() = name.lowercase(Locale.US)

    val category: ProgressCategory
        get() = when (this) {
            WORKOUT -> ProgressCategory.TRAINING
            NUTRITION -> ProgressCategory.NUTRITION
            ACTIVITY -> ProgressCategory.ACTIVITY
            RECOVERY -> ProgressCategory.RECOVERY
            BODY -> ProgressCategory.BODY
            GOALS -> ProgressCategory.GOALS
        }

    /** The two figures shown on the All Reports card. */
    val summaryMetrics: List<ProgressMetric>
        get() = when (this) {
            WORKOUT -> listOf(ProgressMetric.TOTAL_WORKOUTS, ProgressMetric.TRAINING_VOLUME)
            NUTRITION -> listOf(ProgressMetric.AVG_CALORIES_CONSUMED, ProgressMetric.AVG_PROTEIN)
            ACTIVITY -> listOf(ProgressMetric.AVERAGE_STEPS, ProgressMetric.ACTIVE_MINUTES)
            RECOVERY -> listOf(ProgressMetric.AVERAGE_SLEEP, ProgressMetric.RESTING_HEART_RATE)
            BODY -> listOf(ProgressMetric.BODY_WEIGHT, ProgressMetric.BODY_FAT)
            GOALS -> listOf(ProgressMetric.ACTIVE_GOALS, ProgressMetric.LONGEST_STREAK)
        }

    /**
     * Workout and Nutrition keep their existing, richer reports; the other
     * four render from the shared registry (`progressReport/{report}`).
     */
    val route: String
        get() = when (this) {
            WORKOUT -> "workoutReport"
            NUTRITION -> "nutritionReport"
            else -> "progressReport/$rawValue"
        }

    companion object {
        fun fromRaw(raw: String?): ProgressReport? =
            raw?.let { r -> entries.firstOrNull { it.rawValue.equals(r, ignoreCase = true) || it.name.equals(r, ignoreCase = true) } }
    }
}

// MARK: - Quick Stats persistence (PRD §5.3 / §6.3)

/**
 * The user's six Quick Stats, in order. Stored per user on device — there is
 * no backend field yet (iOS is the same), so a reinstall restores the defaults.
 */
object QuickStatsStore {
    private const val PREFS = "fl_progress"

    private fun key(): String {
        val userId = runCatching { DatabaseManager.user()?.id }.getOrNull()
        return "progress_quick_stats_${userId ?: "local"}"
    }

    fun load(context: Context): List<ProgressMetric> {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(), null)
        return sanitize(raw?.split(',')?.filter { it.isNotBlank() })
    }

    fun save(context: Context, metrics: List<ProgressMetric>) {
        val unique = metrics.distinct().take(ProgressMetric.MAX_QUICK_STATS)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key(), unique.joinToString(",") { it.rawValue }).apply()
    }

    /** Stored raw values → metrics: unknowns and duplicates dropped, capped at six, defaults when empty. */
    fun sanitize(raw: List<String>?): List<ProgressMetric> {
        if (raw == null) return ProgressMetric.defaultQuickStats
        val metrics = raw.mapNotNull { ProgressMetric.fromRaw(it) }.distinct()
        return if (metrics.isEmpty()) ProgressMetric.defaultQuickStats else metrics.take(ProgressMetric.MAX_QUICK_STATS)
    }
}
