package com.fitnesslink.fit.model.api

import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.TolerantAdapterFactory
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// MARK: - FTNSSLNK-190/191: nutrition profile + target-calculation contracts
//
//   GET  /profile/me               -> NutritionProfileWithTargets (404 = no profile)
//   POST /profile                  -> create (onboarding), returns profile + targets
//   PUT  /profile                  -> partial update, recalculates, returns both
//   GET  /profile/targets          -> current NutritionTargets only
//   POST /profile/calculate        -> preview targets WITHOUT persisting
//   POST /profile/targets/override -> manual targets (Source becomes "Manual")
//   GET  /profile/goals            -> goal history, newest first
//
// Units are METRIC on the wire (*Cm, *Kg). Calendar dates travel as plain
// "yyyy-MM-dd" strings. Enum-like fields persist the backend's stable member
// NAMES ("LightlyActive", "BuildMuscle", ...) — never ordinals.

enum class NutritionSex(val rawValue: String) {
    MALE("Male"), FEMALE("Female");

    val displayName: String get() = rawValue

    companion object { fun fromRaw(raw: String?) = entries.firstOrNull { it.rawValue == raw } }
}

/** The five standard TDEE tiers. Multipliers mirror the backend's `ActivityMultiplier`; the SERVER does the math. */
enum class NutritionActivityLevel(val rawValue: String, val displayName: String, val detail: String, val multiplier: Double) {
    SEDENTARY("Sedentary", "Sedentary", "Desk job, little or no exercise", 1.2),
    LIGHTLY_ACTIVE("LightlyActive", "Lightly Active", "Light exercise or sports 1–3 days/week", 1.375),
    MODERATELY_ACTIVE("ModeratelyActive", "Moderately Active", "Moderate exercise 3–5 days/week", 1.55),
    VERY_ACTIVE("VeryActive", "Very Active", "Hard exercise 6–7 days/week", 1.725),
    EXTRA_ACTIVE("ExtraActive", "Extra Active", "Very hard exercise and a physical job", 1.9);

    companion object { fun fromRaw(raw: String?) = entries.firstOrNull { it.rawValue == raw } }
}

/** Self-reported training days per week. */
enum class NutritionWorkoutFrequency(val rawValue: String, val displayName: String) {
    NONE("None", "0 days"),
    ONE_TO_TWO("OneToTwo", "1–2 days"),
    THREE_TO_FOUR("ThreeToFour", "3–4 days"),
    FIVE_TO_SIX("FiveToSix", "5–6 days"),
    SEVEN_PLUS("SevenPlus", "7+ days");

    companion object { fun fromRaw(raw: String?) = entries.firstOrNull { it.rawValue == raw } }
}

enum class NutritionPrimaryGoal(val rawValue: String, val displayName: String, val detail: String) {
    LOSE("Lose", "Lose Weight", "Calorie deficit for steady fat loss"),
    MAINTAIN("Maintain", "Maintain Weight", "Hold your current weight"),
    GAIN("Gain", "Gain Weight", "Calorie surplus to add weight"),
    BUILD_MUSCLE("BuildMuscle", "Build Muscle", "Lean surplus with high protein"),
    RECOMP("Recomp", "Body Recomposition", "Trade fat for muscle at maintenance"),
    HEALTH("Health", "General Health", "Balanced nutrition, no weight change");

    /** Direction the goal weight must sit in relative to current weight. */
    enum class WeightDirection { DOWN, UP, NEUTRAL }

    val weightDirection: WeightDirection
        get() = when (this) {
            LOSE -> WeightDirection.DOWN
            GAIN, BUILD_MUSCLE -> WeightDirection.UP
            MAINTAIN, RECOMP, HEALTH -> WeightDirection.NEUTRAL
        }

    companion object { fun fromRaw(raw: String?) = entries.firstOrNull { it.rawValue == raw } }
}

enum class NutritionGoalTimeline(val rawValue: String, val displayName: String) {
    THIRTY_DAYS("ThirtyDays", "30 days"),
    SIXTY_DAYS("SixtyDays", "60 days"),
    NINETY_DAYS("NinetyDays", "90 days"),
    SIX_MONTHS("SixMonths", "6 months"),
    ONE_YEAR("OneYear", "1 year"),
    CUSTOM("Custom", "Custom date");

    companion object { fun fromRaw(raw: String?) = entries.firstOrNull { it.rawValue == raw } }
}

// MARK: - Targets + "why" explanations

/**
 * One per-number explanation. `key` is a stable contract key
 * ("why.bmr.mifflinStJeor", …) the UI maps to a template; `inputs` carries
 * the numbers that template is filled with (heterogeneous JSON values).
 */
data class WhyExplanation(
    val metric: String = "",
    val key: String = "",
    val inputs: Map<String, JsonElement> = emptyMap()
) {
    fun number(key: String): Double? = inputs[key]?.takeIf { it.isJsonPrimitive }?.let { p ->
        if (p.asJsonPrimitive.isNumber) p.asDouble else p.asString.toDoubleOrNull()
    }
    fun string(key: String): String? = inputs[key]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    fun flag(key: String): Boolean = inputs[key]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: false
}

/** The computed (or manually-overridden) plan. */
@JsonAdapter(NutritionTargets.Adapter::class)
data class NutritionTargets(
    val bmr: Double = 0.0,
    val maintenanceCalories: Double = 0.0,
    val dailyCalorieTarget: Double = 0.0,
    val proteinGrams: Double = 0.0,
    val carbGrams: Double = 0.0,
    val fatGrams: Double = 0.0,
    val weeklyWeightChangeKg: Double = 0.0,
    /** `yyyy-MM-dd`; null for maintenance-style plans. */
    val estimatedGoalDate: String? = null,
    /** "Calculated" | "Manual" — provenance. */
    val source: String = "",
    /** `yyyy-MM-dd` — the day this goal takes effect. */
    val effectiveFrom: String = "",
    val explanations: List<WhyExplanation> = emptyList()
) {
    val isManualOverride: Boolean get() = source == "Manual"
    val estimatedGoalDateValue: Date? get() = estimatedGoalDate?.let { MealPlanDay.date(it) }

    fun explanation(forMetric: String): WhyExplanation? =
        explanations.firstOrNull { it.metric.equals(forMetric, ignoreCase = true) }

    class Adapter : TolerantAdapterFactory<NutritionTargets>(NutritionTargets::class.java)
}

/** The stored profile. `age` is server-derived. */
@JsonAdapter(NutritionProfile.Adapter::class)
data class NutritionProfile(
    val id: String = "",
    val userId: String = "",
    /** `yyyy-MM-dd` */
    val dateOfBirth: String = "",
    val age: Int = 0,
    val sex: String = "",
    val heightCm: Double = 0.0,
    val currentWeightKg: Double = 0.0,
    val goalWeightKg: Double = 0.0,
    val activityLevel: String = "",
    val workoutFrequency: String = "",
    val primaryGoal: String = "",
    val timeline: String = "",
    /** `yyyy-MM-dd`, set only when `timeline == "Custom"`. */
    val timelineCustomDate: String? = null,
    val dietaryPreferences: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val createdAtUtc: Date = Date(),
    val updatedAtUtc: Date? = null,
    /** Optimistic-concurrency token (base64) — echo on PUT/DELETE (409 on mismatch). */
    val rowVersion: String = ""
) {
    val sexValue: NutritionSex? get() = NutritionSex.fromRaw(sex)
    val activityLevelValue: NutritionActivityLevel? get() = NutritionActivityLevel.fromRaw(activityLevel)
    val workoutFrequencyValue: NutritionWorkoutFrequency? get() = NutritionWorkoutFrequency.fromRaw(workoutFrequency)
    val primaryGoalValue: NutritionPrimaryGoal? get() = NutritionPrimaryGoal.fromRaw(primaryGoal)
    val timelineValue: NutritionGoalTimeline? get() = NutritionGoalTimeline.fromRaw(timeline)
    val dateOfBirthValue: Date? get() = MealPlanDay.date(dateOfBirth)

    class Adapter : TolerantAdapterFactory<NutritionProfile>(NutritionProfile::class.java)
}

/** Profile create/update responses return the profile AND the freshly computed targets together. */
data class NutritionProfileWithTargets(
    val profile: NutritionProfile = NutritionProfile(),
    val targets: NutritionTargets = NutritionTargets()
)

/** Full create body (onboarding). */
data class CreateNutritionProfileRequest(
    val dateOfBirth: String,
    val sex: String,
    val heightCm: Double,
    val currentWeightKg: Double,
    val goalWeightKg: Double,
    val activityLevel: String,
    val workoutFrequency: String,
    val primaryGoal: String,
    val timeline: String,
    val timelineCustomDate: String? = null,
    val dietaryPreferences: List<String>? = null,
    val allergies: List<String>? = null
)

/**
 * Partial update — only non-null fields change; `rowVersion` is required.
 * Sending only `rowVersion` is a legitimate "recalculate from profile"
 * request (drops a manual override back to Calculated).
 */
data class UpdateNutritionProfileRequest(
    val rowVersion: String,
    val dateOfBirth: String? = null,
    val sex: String? = null,
    val heightCm: Double? = null,
    val currentWeightKg: Double? = null,
    val goalWeightKg: Double? = null,
    val activityLevel: String? = null,
    val workoutFrequency: String? = null,
    val primaryGoal: String? = null,
    val timeline: String? = null,
    val timelineCustomDate: String? = null,
    val dietaryPreferences: List<String>? = null,
    val allergies: List<String>? = null
)

/** Manual override body — provenance becomes "Manual", retained as history. */
data class OverrideNutritionTargetsRequest(
    val calorieGoal: Double,
    val proteinTarget: Double,
    val fatTarget: Double,
    val carbsTarget: Double
)

/** One historical goal row from `GET /profile/goals` (newest first). */
@JsonAdapter(NutritionGoalHistoryEntry.Adapter::class)
data class NutritionGoalHistoryEntry(
    val id: String = "",
    val userId: String = "",
    val calorieGoal: Double = 0.0,
    val proteinTarget: Double = 0.0,
    val fatTarget: Double = 0.0,
    val carbsTarget: Double = 0.0,
    val source: String = "",
    /** `yyyy-MM-dd` */
    val effectiveFrom: String = "",
    val isCurrent: Boolean = false,
    val supersededAtUtc: Date? = null,
    val maintenanceCalories: Double? = null,
    val weeklyWeightChangeKg: Double? = null,
    val estimatedGoalDate: String? = null,
    val createdAtUtc: Date = Date()
) {
    class Adapter : TolerantAdapterFactory<NutritionGoalHistoryEntry>(NutritionGoalHistoryEntry::class.java)
}

// MARK: - Daily summary targets (FTNSSLNK-187/191)

/** One target/consumed/remaining line. `target` / `remaining` are null when no goal was in effect. */
data class MacroSummaryLine(
    val target: Double? = null,
    val consumed: Double = 0.0,
    val remaining: Double? = null
)

data class NutritionDailyTargets(
    val calories: MacroSummaryLine = MacroSummaryLine(),
    val protein: MacroSummaryLine = MacroSummaryLine(),
    val fat: MacroSummaryLine = MacroSummaryLine(),
    val carbs: MacroSummaryLine = MacroSummaryLine(),
    /** "Calculated" | "Manual" | null. */
    val source: String? = null,
    val effectiveFrom: String? = null
) {
    val hasTargets: Boolean get() = calories.target != null
}

/** Water logged for the day vs. the hydration goal. Amounts are in `unit`. */
data class WaterDaySummary(
    val total: Double = 0.0,
    val goal: Double? = null,
    val remaining: Double? = null,
    val unit: String = "fl oz"
)

/** Food entries for one meal type with the meal's macro subtotal. */
data class MealGroupSummary(
    val mealType: String = "",
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val entries: List<FoodEntry> = emptyList()
)

/** One micro line of the daily summary (consumed null = no data that day, NEVER rendered as 0). */
data class MicroSummaryLine(
    val key: String = "",
    val displayName: String = "",
    val unit: String = "",
    val consumed: Double? = null,
    val target: Double? = null,
    val remaining: Double? = null
)

data class DailyMicrosSummary(
    val lines: List<MicroSummaryLine> = emptyList()
)

@JsonAdapter(NutritionDailySummary.Adapter::class)
data class NutritionDailySummary(
    val date: String = "",
    val timeZone: String = "",
    val targets: NutritionDailyTargets = NutritionDailyTargets(),
    val water: WaterDaySummary? = null,
    val meals: List<MealGroupSummary>? = null,
    val micros: DailyMicrosSummary? = null
) {
    class Adapter : TolerantAdapterFactory<NutritionDailySummary>(NutritionDailySummary::class.java)
}

// MARK: - Range summary (FTNSSLNK-193/196)

/** One compact rollup per LOCAL day in an inclusive range (max 92 days). */
data class NutritionDayRollup(
    /** `yyyy-MM-dd` local day key. */
    val date: String = "",
    val calorieTarget: Double? = null,
    val calorieConsumed: Double = 0.0,
    val calorieRemaining: Double? = null,
    val proteinConsumed: Double = 0.0,
    val fatConsumed: Double = 0.0,
    val carbsConsumed: Double = 0.0,
    val waterTotal: Double = 0.0,
    val waterGoal: Double? = null,
    val entryCount: Int = 0,
    /** True when any food or water was logged that day — the completeness dot. */
    val hasData: Boolean = false
) {
    val id: String get() = date
}

data class NutritionSummaryRange(
    val from: String = "",
    val to: String = "",
    val timeZone: String = "",
    /** The unit every day's `waterTotal` / `waterGoal` arrives converted into. */
    val waterUnit: String? = null,
    val days: List<NutritionDayRollup> = emptyList()
)

// MARK: - "Why" explanation rendering

/**
 * The backend ships KEYS + INPUTS, not prose — the client owns one template
 * per key. These templates are the ONLY copy source; an unknown key yields
 * null so the UI shows nothing instead of inventing an explanation.
 */
object WhyExplanationText {

    fun render(explanation: WhyExplanation): String? {
        fun num(key: String): String? = explanation.number(key)?.let { format(it) }
        fun absNum(key: String): String? = explanation.number(key)?.let { format(abs(it)) }
        fun str(key: String): String? = explanation.string(key)
        fun flag(key: String): Boolean = explanation.flag(key)

        return when (explanation.key) {
            "why.bmr.mifflinStJeor" -> {
                val bmr = num("bmr") ?: return null
                val w = num("weightKg") ?: return null
                val h = num("heightCm") ?: return null
                val age = num("age") ?: return null
                val sex = str("sex")?.let { NutritionSex.fromRaw(it)?.displayName?.lowercase(Locale.US) }
                val sexPart = sex?.let { ", $it" } ?: ""
                "Your body burns about $bmr calories a day at complete rest. This is your Basal Metabolic Rate, estimated with the Mifflin-St Jeor equation from your weight ($w kg), height ($h cm), and age ($age$sexPart)."
            }
            "why.maintenance.activityMultiplier" -> {
                val bmr = num("bmr") ?: return null
                val mult = explanation.number("multiplier") ?: return null
                val maint = num("maintenanceCalories") ?: return null
                val level = str("activityLevel")?.let { NutritionActivityLevel.fromRaw(it)?.displayName?.lowercase(Locale.US) } ?: "your activity level"
                val multText = String.format(Locale.US, "%.3g", mult)
                "Multiplying your BMR ($bmr) by $multText for a $level lifestyle gives $maint calories — roughly what you burn in a typical day. Eating this amount holds your weight steady."
            }
            "why.target.deficit" -> {
                val maint = num("maintenanceCalories") ?: return null
                val adjust = absNum("adjustmentKcal") ?: return null
                val target = num("dailyCalorieTarget") ?: return null
                var text = "Your target of $target calories sits $adjust below your maintenance ($maint), creating the daily deficit that drives weight loss on your timeline."
                if (flag("capApplied")) text += " The deficit was capped so the pace stays safe and sustainable."
                if (flag("floorApplied")) text += " It was also raised to the minimum safe daily intake."
                text
            }
            "why.target.surplus" -> {
                val maint = num("maintenanceCalories") ?: return null
                val adjust = absNum("adjustmentKcal") ?: return null
                val target = num("dailyCalorieTarget") ?: return null
                var text = "Your target of $target calories sits $adjust above your maintenance ($maint), providing the daily surplus your goal needs."
                if (flag("capApplied")) text += " The surplus was capped to favor lean gain over excess fat."
                text
            }
            "why.target.maintenance" -> {
                val target = num("dailyCalorieTarget") ?: return null
                "Your goal doesn't involve changing your weight, so your target of $target calories matches your maintenance — enough energy to fuel your day and training without gaining or losing."
            }
            "why.target.manualOverride" -> {
                val target = num("dailyCalorieTarget") ?: return null
                "You set this target of $target calories manually. It overrides the calculated plan until you revert to calculated targets or edit your profile."
            }
            "why.macro.protein" -> {
                val pct = num("percentOfCalories") ?: return null
                val grams = num("grams") ?: return null
                "$pct% of your calories go to protein — $grams g at 4 calories per gram. Protein preserves and builds muscle, and it's the most filling macro."
            }
            "why.macro.carbs" -> {
                val pct = num("percentOfCalories") ?: return null
                val grams = num("grams") ?: return null
                "$pct% of your calories come from carbs — $grams g at 4 calories per gram. Carbs are your body's preferred fuel for training and recovery."
            }
            "why.macro.fat" -> {
                val pct = num("percentOfCalories") ?: return null
                val grams = num("grams") ?: return null
                "$pct% of your calories come from fat — $grams g at 9 calories per gram. Fat supports hormones and helps absorb key vitamins."
            }
            "why.weeklyRate.projected" -> {
                val weekly = explanation.number("weeklyWeightChangeKg") ?: return null
                val daily = absNum("dailyAdjustKcal") ?: return null
                if (weekly == 0.0) return "Your calories match what you burn, so your weight is projected to hold steady week to week."
                val direction = if (weekly < 0) "lose" else "gain"
                "A $daily-calorie daily adjustment projects to about ${format(abs(weekly))} kg per week — roughly 7,700 calories equal 1 kg of body weight. You should $direction at this steady, sustainable pace."
            }
            "why.completion.projected" -> {
                val current = num("currentWeightKg") ?: return null
                val goal = num("goalWeightKg") ?: return null
                val dateStr = str("estimatedGoalDate") ?: return null
                val dateText = MealPlanDay.date(dateStr)?.let {
                    java.text.SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(it)
                } ?: dateStr
                "Moving from $current kg to $goal kg at your projected weekly rate puts your estimated finish around $dateText. Consistency matters more than any single day."
            }
            "why.completion.notApplicable" -> {
                val goal = str("primaryGoal")?.let { NutritionPrimaryGoal.fromRaw(it)?.displayName?.lowercase(Locale.US) } ?: "maintenance-style"
                "A $goal plan doesn't target a weight change, so there's no completion date — the plan simply keeps you fueled at the right level."
            }
            else -> null
        }
    }

    /** Whole numbers drop the decimals, fractional ones keep up to two places. */
    fun format(d: Double): String {
        if (d == Math.rint(d) && abs(d) < 1_000_000_000) return d.toLong().toString()
        return String.format(Locale.US, "%.2f", d)
    }
}

// MARK: - Micronutrients (FTNSSLNK-216/217, iOS `Micronutrient`)

/**
 * The five trackable micronutrients. [key] is the app-side identifier the
 * `DisplayedNutrientsStore` contract exposes (`fiber`, `sugars`, `sodium`,
 * `saturatedFat`, `cholesterol`); [wireName] is the backend's canonical name
 * sent on the preference PUT and matched on GET.
 */
enum class Micronutrient(val key: String, val wireName: String, val displayName: String, val unit: String, val defaultTarget: Double?) {
    FIBER("fiber", "Fiber", "Fiber", "g", 28.0),
    SUGARS("sugars", "Sugars", "Sugars", "g", null),
    SODIUM("sodium", "Sodium", "Sodium", "mg", 2300.0),
    SATURATED_FAT("saturatedFat", "SaturatedFat", "Saturated Fat", "g", 20.0),
    CHOLESTEROL("cholesterol", "Cholesterol", "Cholesterol", "mg", 300.0);

    companion object {
        /** The backend's default displayed set (mirrors `isDefault == true`). */
        val defaultDisplayed: List<Micronutrient> = listOf(FIBER, SUGARS, SODIUM)

        fun fromKey(key: String?): Micronutrient? = entries.firstOrNull { it.key == key }
        fun fromWire(name: String?): Micronutrient? = entries.firstOrNull { it.wireName.equals(name, ignoreCase = true) || it.key.equals(name, ignoreCase = true) }

        /** Canonical display order for a set of keys. */
        fun ordered(keys: Set<String>): List<Micronutrient> = entries.filter { it.key in keys }
    }
}

/** The summary's line for one micro (iOS `DailyMicrosSummary.line(for:)`). */
fun com.fitnesslink.fit.network.dto.DailyMicrosSummary.line(micro: Micronutrient): com.fitnesslink.fit.network.dto.MicroSummaryLine? = when (micro) {
    Micronutrient.FIBER -> fiber
    Micronutrient.SUGARS -> sugars
    Micronutrient.SODIUM -> sodium
    Micronutrient.SATURATED_FAT -> saturatedFat
    Micronutrient.CHOLESTEROL -> cholesterol
}

// MARK: - Bridges onto the wire DTOs
//
// `network/api/NutritionApi.kt` returns the shapes declared in
// `network/dto/NutritionDtos.kt`; these extensions give those DTOs the same
// typed accessors the models above carry, so screens can work directly off
// the API's return types.

fun com.fitnesslink.fit.network.dto.NutritionTargets.explanation(forMetric: String): com.fitnesslink.fit.network.dto.WhyExplanation? =
    explanations.firstOrNull { it.metric.equals(forMetric, ignoreCase = true) }

/** Rendered "why" copy for [metric], or null when the server sent no (known) explanation. */
fun com.fitnesslink.fit.network.dto.NutritionTargets.whyText(metric: String): String? =
    explanation(metric)?.let { WhyExplanationText.render(it) }

val com.fitnesslink.fit.network.dto.NutritionTargets.estimatedGoalDateValue: Date?
    get() = estimatedGoalDate?.let { MealPlanDay.date(it) }

val com.fitnesslink.fit.network.dto.NutritionTargets.effectiveFromValue: Date?
    get() = effectiveFrom.takeIf { it.isNotEmpty() }?.let { MealPlanDay.date(it) }

fun WhyExplanationText.render(explanation: com.fitnesslink.fit.network.dto.WhyExplanation): String? =
    render(WhyExplanation(metric = explanation.metric, key = explanation.key, inputs = explanation.inputs))

val com.fitnesslink.fit.network.dto.NutritionProfile.sexValue: NutritionSex? get() = NutritionSex.fromRaw(sex)
val com.fitnesslink.fit.network.dto.NutritionProfile.activityLevelValue: NutritionActivityLevel? get() = NutritionActivityLevel.fromRaw(activityLevel)
val com.fitnesslink.fit.network.dto.NutritionProfile.workoutFrequencyValue: NutritionWorkoutFrequency? get() = NutritionWorkoutFrequency.fromRaw(workoutFrequency)
val com.fitnesslink.fit.network.dto.NutritionProfile.primaryGoalValue: NutritionPrimaryGoal? get() = NutritionPrimaryGoal.fromRaw(primaryGoal)
val com.fitnesslink.fit.network.dto.NutritionProfile.timelineValue: NutritionGoalTimeline? get() = NutritionGoalTimeline.fromRaw(timeline)
val com.fitnesslink.fit.network.dto.NutritionProfile.dateOfBirthValue: Date? get() = MealPlanDay.date(dateOfBirth)

/** The legacy flat goal row mirrored from a target set (iOS `NutritionGoalsStore.mirrorToLocalGoal`). */
fun com.fitnesslink.fit.network.dto.NutritionTargets.toNutritionGoal(id: String): com.fitnesslink.fit.model.NutritionGoal =
    com.fitnesslink.fit.model.NutritionGoal(
        id = id,
        calorieGoal = Math.round(dailyCalorieTarget).toInt(),
        proteinTarget = Math.round(proteinGrams).toInt(),
        fatTarget = Math.round(fatGrams).toInt(),
        carbsTarget = Math.round(carbGrams).toInt()
    )

/** iOS `NutritionPrimaryGoal.systemImage` equivalent: a Material icon name the option card maps. */
val NutritionPrimaryGoal.iconName: String
    get() = when (this) {
        NutritionPrimaryGoal.LOSE -> "trending_down"
        NutritionPrimaryGoal.MAINTAIN -> "balance"
        NutritionPrimaryGoal.GAIN -> "trending_up"
        NutritionPrimaryGoal.BUILD_MUSCLE -> "fitness_center"
        NutritionPrimaryGoal.RECOMP -> "swap_vert"
        NutritionPrimaryGoal.HEALTH -> "favorite"
    }
