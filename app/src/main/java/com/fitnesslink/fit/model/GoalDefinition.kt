package com.fitnesslink.fit.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

// Port of iOS `Models/GoalDefinition.swift` (Goals & Progress Tools PRD).
// A goal's category is `GoalType`; the specific, measurable goal is
// `GoalSubtype`. The server stores the definition only — progress is
// computed on device from the user's own logs (`GoalProgressEngine`), so
// editing or deleting a source entry is reflected the next time the goal is
// read. Enum constant names / `@SerializedName`s are the iOS raw values so a
// `Goal.config` blob written by either platform decodes on the other.

// MARK: - Subtype

enum class GoalSubtype(val title: String, val summary: String, val example: String?) {
    // Lose Weight
    LoseWeight("Lose Weight", "Reach a target body weight by a selected date.", null),
    // Get Stronger
    ImproveLift("Improve a Specific Lift", "Increase performance in a selected exercise.", "Squat: 185 lb → 225 lb"),
    PersonalRecord("Achieve a Personal Record", "Achieve a new best in a supported lift metric.", "Deadlift PR of 315 lb"),
    RepsAtWeight("Increase Reps at a Given Weight", "Complete more reps at the same exercise and load.", "Bench press: 8 → 12 reps at 95 lb"),
    TrainingVolume("Increase Training Volume", "Reach a working-set volume target per week or workout.", "30,000 lb of weekly working-set volume"),
    // Sleep & Recover
    SleepDuration("Reach a Sleep Duration Target", "Set how much sleep to get each night.", "At least 7 hours per night"),
    SleepConsistency("Improve Sleep Consistency", "Meet a sleep target a set number of nights per week.", "7+ hours on 5 nights per week"),
    RecoveryMetric("Reach a Recovery Metric Target", "Track resting heart rate or HRV from Health Connect.", "Work toward a personal resting-heart-rate target"),
    RecoveryRoutine("Build a Recovery Routine", "Complete a repeatable recovery habit.", "10-minute wind-down, 5 evenings a week"),
    // Eat Better
    CalorieTarget("Reach a Calorie Target", "Get a personalized daily calorie target.", "Average 2,000 calories per day"),
    MacroTarget("Reach a Macro Target", "Set daily targets for protein, carbohydrates, or fat.", "140 g of protein per day"),
    Hydration("Improve Hydration", "Set a daily water-intake target.", "80 fl oz of water per day"),
    HealthyHabit("Build Healthy Eating Habits", "Choose a recommended nutrition habit and build consistency.", "3 servings of vegetables, 5 days a week"),
    // Build a Routine
    WorkoutConsistency("Work Out Consistently", "Build a repeatable weekly workout routine.", "4 workouts per week"),
    MovementRoutine("Build a Movement Routine", "Stay consistent with a specific type of movement.", "3 runs per week"),
    WellnessRoutine("Build a Wellness Routine", "Establish repeatable wellness and recovery activities.", "Meditate 10 minutes daily"),
    DailyHabit("Build a Daily Habit", "Create consistency with a small, repeatable behavior.", "Read for 10 minutes, 5 days a week"),
    CustomRoutine("Create Your Own Routine", "Build a personalized routine from supported activities.", "Strength 3×, running 2×, mobility 4× a week"),
    // Custom Goal
    CustomGoal("Custom Goal", "Define your own measurable target.", null);

    val category: GoalType
        get() = when (this) {
            LoseWeight -> GoalType.BodyComposition
            ImproveLift, PersonalRecord, RepsAtWeight, TrainingVolume -> GoalType.Performance
            SleepDuration, SleepConsistency, RecoveryMetric, RecoveryRoutine -> GoalType.RecoveryWellness
            CalorieTarget, MacroTarget, Hydration, HealthyHabit -> GoalType.Nutrition
            WorkoutConsistency, MovementRoutine, WellnessRoutine, DailyHabit, CustomRoutine -> GoalType.Consistency
            CustomGoal -> GoalType.Custom
        }

    /** Goal types whose progress is a count of completions per period. */
    val isFrequencyGoal: Boolean
        get() = when (this) {
            SleepConsistency, RecoveryRoutine, HealthyHabit, WorkoutConsistency, MovementRoutine,
            WellnessRoutine, DailyHabit, CustomRoutine -> true
            else -> false
        }

    companion object {
        fun options(category: GoalType): List<GoalSubtype> = entries.filter { it.category == category }

        fun fromWire(raw: String?): GoalSubtype? = raw?.let { r -> entries.firstOrNull { it.name.equals(r, ignoreCase = true) } }
    }
}

// MARK: - Tracking / frequency / metrics

enum class GoalTrackingSource(val displayName: String) {
    WeightLog("Weight Log"),
    WorkoutLogs("Qualifying workout logs"),
    Program("FitnessLink program workouts"),
    /** iOS "Apple Health"; on Android this is Health Connect (not integrated yet). */
    AppleHealth("Health Connect"),
    NutritionLogs("Nutrition logs"),
    WaterLog("Water tracker"),
    Manual("Manual check-in");

    companion object {
        fun fromWire(raw: String?): GoalTrackingSource? = raw?.let { r -> entries.firstOrNull { it.name.equals(r, ignoreCase = true) } }
    }
}

enum class GoalFrequency(val displayName: String) {
    /** Every day. */
    Daily("Every day"),
    /** `frequencyTarget` qualifying days / sessions per week. */
    Weekly("Per week"),
    /** Each workout (training volume). */
    PerWorkout("Per workout"),
    /** Cumulative across the goal's time frame (custom goals). */
    Overall("Over the time frame");

    companion object {
        fun fromWire(raw: String?): GoalFrequency? = raw?.let { r -> entries.firstOrNull { it.name.equals(r, ignoreCase = true) } }
    }
}

/** Strength metric keys (provisional rules until the Strength Metrics Calculation PRD exists — see `StrengthMetrics`). */
enum class StrengthMetric(val rawValue: String, val displayName: String) {
    @SerializedName("load") LOAD("load", "Heaviest working set"),
    @SerializedName("e1rm") ESTIMATED_ONE_REP_MAX("e1rm", "Estimated 1-rep max"),
    @SerializedName("reps") REPS("reps", "Reps at a set load"),
    @SerializedName("volume") VOLUME("volume", "Training volume");

    val isEstimated: Boolean get() = this == ESTIMATED_ONE_REP_MAX

    companion object {
        fun fromRaw(raw: String?): StrengthMetric? = entries.firstOrNull { it.rawValue == raw }
    }
}

enum class RecoveryMetric(val rawValue: String, val displayName: String, val unit: String) {
    @SerializedName("restingHeartRate") RESTING_HEART_RATE("restingHeartRate", "Resting heart rate", "bpm"),
    @SerializedName("hrv") HRV("hrv", "Heart-rate variability (HRV)", "ms");
}

/** Custom Goal metric types (PRD 7.3 step 2). */
enum class CustomGoalMetric(val rawValue: String, val displayName: String, val explanation: String) {
    @SerializedName("count") COUNT("count", "Count", "Number of completions, sessions, or events."),
    @SerializedName("duration") DURATION("duration", "Duration", "Minutes spent on an activity."),
    @SerializedName("distance") DISTANCE("distance", "Distance", "Distance covered, in miles or kilometers."),
    @SerializedName("frequency") FREQUENCY("frequency", "Frequency", "Number of days you complete it each week."),
    @SerializedName("steps") STEPS("steps", "Daily steps", "Average steps per day from Health Connect."),
    @SerializedName("manual") MANUAL("manual", "Manual completion", "A check-in that a clearly defined action was completed.");

    val defaultUnit: String
        get() = when (this) {
            COUNT -> "times"
            DURATION -> "min"
            DISTANCE -> if (Locale.getDefault().country in setOf("US", "LR", "MM", "GB")) "mi" else "km"
            FREQUENCY -> "days"
            STEPS -> "steps"
            MANUAL -> ""
        }
}

enum class MacroKey(val rawValue: String, val displayName: String) {
    @SerializedName("protein") PROTEIN("protein", "Protein"),
    @SerializedName("carbs") CARBS("carbs", "Carbohydrates"),
    @SerializedName("fat") FAT("fat", "Fat");

    /** Protein is a floor; carbs and fat count when within ±10 % of target. */
    val ruleText: String
        get() = if (this == PROTEIN) "Counts days you reach at least the target." else "Counts days within 10% of the target."
}

// MARK: - Config (JSON in Goal.config)

data class GoalReminder(
    val enabled: Boolean = false,
    val hour: Int = 19,
    val minute: Int = 0,
    /** 1 = Sunday … 7 = Saturday; null/empty = every day. */
    val weekdays: List<Int>? = null
)

data class GoalMacroTarget(val key: MacroKey = MacroKey.PROTEIN, val grams: Double = 0.0)

/** One activity inside a routine goal (PRD 6.8). Workout activities count logged workouts; every other kind counts check-ins. */
data class GoalActivity(
    val id: String = UUID.randomUUID().toString().lowercase(Locale.US),
    val name: String = "",
    val kind: Kind = Kind.WORKOUT,
    val perWeek: Int = 1,
    val minutes: Int? = null
) {
    enum class Kind(val displayName: String) {
        @SerializedName("workout") WORKOUT("Workout"),
        @SerializedName("movement") MOVEMENT("Movement"),
        @SerializedName("wellness") WELLNESS("Wellness"),
        @SerializedName("habit") HABIT("Habit");

        val tracking: GoalTrackingSource get() = if (this == WORKOUT) GoalTrackingSource.WorkoutLogs else GoalTrackingSource.Manual
    }
}

/** A replaced target. [changedAt] is ISO-8601 (iOS `.iso8601`). */
data class GoalTargetChange(val value: Double = 0.0, val changedAt: String = "")

/**
 * Subtype-specific settings, stored as JSON in `Goal.config`. Every field is
 * optional so older or newer payloads decode.
 */
data class GoalConfig(
    /** Unit the goal is expressed in (lbs/kg, fl oz/ml, min, mi/km, ...). */
    val unit: String? = null,
    /** Exercise name — workout history matches sets by task name. */
    val movementName: String? = null,
    /** Reps-at-weight: the fixed load, in [unit]. */
    val load: Double? = null,
    /** Volume scope: null/false = all working sets, true = [movementName]. */
    val volumeScopeIsExercise: Boolean? = null,
    val macros: List<GoalMacroTarget>? = null,
    /** Calorie range mode; null = single target (±10 %). */
    val calorieMin: Double? = null,
    val calorieMax: Double? = null,
    val recoveryMetric: RecoveryMetric? = null,
    val customMetric: CustomGoalMetric? = null,
    /** Clear completion action for manual / count goals. */
    val actionName: String? = null,
    val habitKey: String? = null,
    val templateId: String? = null,
    val activities: List<GoalActivity>? = null,
    val steps: List<String>? = null,
    val programId: String? = null,
    val programName: String? = null,
    /** Scheduled weekdays (1 = Sunday); null = flexible. */
    val weekdays: List<Int>? = null,
    val preferredHour: Int? = null,
    val preferredMinute: Int? = null,
    val sessionMinutes: Int? = null,
    val reminder: GoalReminder? = null,
    val targetHistory: List<GoalTargetChange>? = null,
    /** Plain-language basis of the recommended time frame / target. */
    val recommendationBasis: String? = null,
    val recommendationIsPersonalized: Boolean? = null,
    val completionRule: String? = null
) {
    val isFlexibleSchedule: Boolean get() = weekdays.isNullOrEmpty()
}

/** Shared coder for `GoalConfig` JSON (plain Gson: nulls omitted, enum raw values via `@SerializedName`). */
object GoalJson {
    val gson: Gson = GsonBuilder().create()

    fun decodeConfig(json: String?): GoalConfig {
        if (json.isNullOrBlank()) return GoalConfig()
        return runCatching { gson.fromJson(json, GoalConfig::class.java) }.getOrNull() ?: GoalConfig()
    }

    fun encodeConfig(config: GoalConfig): String = gson.toJson(config)
}

// MARK: - Manual entries

/**
 * A manual, dated record toward a goal — a sleep log, a check-in, a custom
 * amount. Mirrors the server's `GoalEntryDto`. [entryDate] is the user-local
 * calendar day (`yyyy-MM-dd`); [createdAt] is epoch millis locally,
 * `createdAtUtc` ISO on the wire.
 */
@com.google.gson.annotations.JsonAdapter(GoalEntry.WireAdapter::class)
data class GoalEntry(
    val id: String = UUID.randomUUID().toString().lowercase(Locale.US),
    val goalId: String = "",
    val entryDate: String = GoalDay.key(LocalDate.now()),
    val activityKey: String? = null,
    val value: Double? = null,
    val source: String = "Manual",
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val day: LocalDate get() = GoalDay.parse(entryDate) ?: GoalDay.localDate(createdAt)

    class WireAdapter : EpochWireAdapter<GoalEntry>(
        GoalEntry::class.java,
        dateKeys = setOf("createdAt"),
        utcSuffixed = setOf("createdAt")
    ) {
        override fun postprocess(raw: com.google.gson.JsonObject, decoded: GoalEntry): GoalEntry =
            decoded.copy(id = decoded.id.lowercase(Locale.US), goalId = decoded.goalId.lowercase(Locale.US),
                entryDate = decoded.entryDate.take(10))
    }
}

/**
 * User-local calendar days and weeks. Every goal uses the same boundaries:
 * local midnight, and weeks as the device locale defines them (Sunday in the
 * US, matching iOS `Calendar.current`).
 */
object GoalDay {
    private val keyFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    @Volatile var zone: ZoneId = ZoneId.systemDefault()
    @Volatile var firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    fun key(day: LocalDate): String = day.format(keyFormat)

    fun parse(key: String?): LocalDate? = key?.take(10)?.let { runCatching { LocalDate.parse(it, keyFormat) }.getOrNull() }

    fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun startOfDayMillis(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

    fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

    fun today(): LocalDate = LocalDate.now(zone)
}

// MARK: - Curated catalog

/** Curated templates (PRD 5.7, 6.4–6.7, 4.4D). Templates are starting points; every value is editable during setup. */
data class GoalTemplate(
    val id: String,
    val subtype: GoalSubtype,
    val name: String,
    val description: String,
    val perWeek: Int,
    val minutes: Int? = null,
    val steps: List<String> = emptyList(),
    val completionRule: String,
    val level: String? = null
) {
    val weeklyMinutes: Int? get() = minutes?.let { it * perWeek }

    companion object {
        fun templates(subtype: GoalSubtype): List<GoalTemplate> = all.filter { it.subtype == subtype }

        fun template(id: String?): GoalTemplate? = id?.let { i -> all.firstOrNull { it.id == i } }

        private const val WORKOUT_RULE = "Each completed FitnessLink workout counts once."

        val all: List<GoalTemplate> = listOf(
            // Work Out Consistently
            GoalTemplate("wo-2day-beginner", GoalSubtype.WorkoutConsistency, "2-Day Beginner Routine", "Two full-body sessions a week to build the habit.",
                perWeek = 2, minutes = 30, completionRule = WORKOUT_RULE, level = "Beginner"),
            GoalTemplate("wo-3day-strength", GoalSubtype.WorkoutConsistency, "3-Day Strength Routine", "Three strength sessions a week with rest days between.",
                perWeek = 3, minutes = 45, completionRule = WORKOUT_RULE, level = "Intermediate"),
            GoalTemplate("wo-4day-balanced", GoalSubtype.WorkoutConsistency, "4-Day Balanced Routine", "Mix strength and conditioning across four days.",
                perWeek = 4, minutes = 45, completionRule = WORKOUT_RULE, level = "Intermediate"),
            GoalTemplate("wo-3day-glute", GoalSubtype.WorkoutConsistency, "3-Day Glute Focus", "Three lower-body focused sessions a week.",
                perWeek = 3, minutes = 40, completionRule = WORKOUT_RULE),
            GoalTemplate("wo-home", GoalSubtype.WorkoutConsistency, "Home Workout Routine", "Three short sessions you can do at home.",
                perWeek = 3, minutes = 25, completionRule = WORKOUT_RULE),
            // Movement
            GoalTemplate("mv-running", GoalSubtype.MovementRoutine, "Running Routine", "Get out for a run a few times a week.",
                perWeek = 3, minutes = 30, completionRule = "Check in each run of at least the set duration."),
            GoalTemplate("mv-walking", GoalSubtype.MovementRoutine, "Walking Routine", "Regular walks to keep you moving.",
                perWeek = 5, minutes = 30, completionRule = "Check in each walk of at least the set duration."),
            GoalTemplate("mv-mobility", GoalSubtype.MovementRoutine, "Mobility Routine", "Short mobility sessions for range of motion.",
                perWeek = 4, minutes = 15, completionRule = "Check in each completed mobility session."),
            GoalTemplate("mv-stretching", GoalSubtype.MovementRoutine, "Stretching Routine", "Stretch regularly to stay loose.",
                perWeek = 5, minutes = 10, completionRule = "Check in each completed stretching session."),
            GoalTemplate("mv-outdoor", GoalSubtype.MovementRoutine, "Outdoor Movement Routine", "Hikes, rides, or any outdoor activity.",
                perWeek = 2, minutes = 45, completionRule = "Check in each outdoor session."),
            // Wellness
            GoalTemplate("wl-meditation", GoalSubtype.WellnessRoutine, "Daily Meditation", "A few quiet minutes every day.",
                perWeek = 7, minutes = 10, completionRule = "Check in each session of at least the set duration."),
            GoalTemplate("wl-wind-down", GoalSubtype.WellnessRoutine, "Evening Wind Down", "A screen-free routine before bed.",
                perWeek = 5, minutes = 10, completionRule = "Check in each evening you complete it."),
            GoalTemplate("wl-mobility", GoalSubtype.WellnessRoutine, "Mobility and Stretching", "Gentle movement to recover.",
                perWeek = 3, minutes = 15, completionRule = "Check in each completed session."),
            GoalTemplate("wl-breathing", GoalSubtype.WellnessRoutine, "Breathing Exercises", "Guided breathing to reset.",
                perWeek = 5, minutes = 5, completionRule = "Check in each completed session."),
            GoalTemplate("wl-gratitude", GoalSubtype.WellnessRoutine, "Gratitude Journaling", "Write down what went well.",
                perWeek = 7, completionRule = "Check in once you've written your entry."),
            // Recovery routine (Sleep & Recover)
            GoalTemplate("rc-wind-down", GoalSubtype.RecoveryRoutine, "10-Minute Wind-Down", "A calm routine before bed.",
                perWeek = 5, minutes = 10, completionRule = "Check in each evening you complete it."),
            GoalTemplate("rc-mobility", GoalSubtype.RecoveryRoutine, "Mobility Routine", "Mobility work for recovery.",
                perWeek = 3, minutes = 15, completionRule = "Check in each completed session."),
            GoalTemplate("rc-breathing", GoalSubtype.RecoveryRoutine, "Relaxation or Breathing", "Breathing or relaxation on selected days.",
                perWeek = 4, minutes = 5, completionRule = "Check in each completed session."),
            // Daily habits
            GoalTemplate("hb-morning", GoalSubtype.DailyHabit, "Morning Routine", "A consistent start to the day.",
                perWeek = 7, steps = listOf("Drink a glass of water", "Make the bed", "5 minutes of stretching", "Plan the day"),
                completionRule = "Counts when you complete the whole routine."),
            GoalTemplate("hb-journal", GoalSubtype.DailyHabit, "Journal", "Write a short journal entry.",
                perWeek = 7, completionRule = "Check in once you've written your entry."),
            GoalTemplate("hb-movement-break", GoalSubtype.DailyHabit, "Take a Movement Break", "Stand up and move during the day.",
                perWeek = 5, minutes = 5, completionRule = "Check in once you've taken a break."),
            GoalTemplate("hb-gratitude", GoalSubtype.DailyHabit, "Practice Gratitude", "Note one thing you're grateful for.",
                perWeek = 7, completionRule = "Check in once per day."),
            GoalTemplate("hb-read", GoalSubtype.DailyHabit, "Read or Learn", "Read or study for a set time.",
                perWeek = 5, minutes = 10, completionRule = "Check in after reading for at least the set time."),
            GoalTemplate("hb-screen-time", GoalSubtype.DailyHabit, "Limit Screen Time", "Stay under a daily screen-time limit you choose.",
                perWeek = 5, completionRule = "Check in on days you stayed under your limit.")
        )
    }
}

/** Curated Eat Better habits (PRD 5.7). Each has a clear completion rule; nutrition-log habits complete automatically. */
data class NutritionHabit(
    val id: String,
    val name: String,
    val description: String,
    val rule: String,
    /** Daily amount the rule counts (servings, meals); null = plain check-in. */
    val defaultTarget: Double?,
    val unit: String?,
    val tracking: GoalTrackingSource,
    val defaultDaysPerWeek: Int
) {
    companion object {
        val all: List<NutritionHabit> = listOf(
            NutritionHabit("fruits-veg", "Eat Fruits and Vegetables", "Reach a daily servings target.",
                "Check in the servings you eat; a day counts when you reach the target.",
                3.0, "servings", GoalTrackingSource.Manual, 5),
            NutritionHabit("log-meals", "Log Meals Consistently", "Log your food on selected days.",
                "A day counts when you log at least the target number of meals or snacks in Nutrition.",
                3.0, "meals", GoalTrackingSource.NutritionLogs, 5),
            NutritionHabit("protein-source", "Include a Protein Source", "Get protein into your meals.",
                "A day counts when the target number of logged meals each have at least 20 g of protein.",
                3.0, "meals", GoalTrackingSource.NutritionLogs, 5),
            NutritionHabit("home-meal", "Choose Whole-Food Options", "Prepare a meal at home from whole foods.",
                "Check in each day you eat a meal you prepared from whole, minimally processed ingredients.",
                null, null, GoalTrackingSource.Manual, 4)
        )

        fun habit(id: String?): NutritionHabit? = id?.let { i -> all.firstOrNull { it.id == i } }
    }
}

/** Meal to suggest when logging from a goal, by time of day (iOS `MealType.forCurrentTime`). */
fun mealTypeForHour(hour: Int): MealType = when (hour) {
    in 4..10 -> MealType.BREAKFAST
    in 11..15 -> MealType.LUNCH
    in 16..21 -> MealType.DINNER
    else -> MealType.SNACK
}
