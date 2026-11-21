package com.fitnesslink.fit.onboarding

import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.onboarding.*
import com.fitnesslink.fit.network.dto.CreateNutritionProfileRequest
import java.util.Calendar
import java.util.Date
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Every screen in the onboarding flow (PRD v1.1 §37 + §37.1A). Port of iOS `OnboardingStep`. */
enum class OnboardingStep(val key: String) {
    WELCOME("welcome"),
    PRIMARY_GOAL("primaryGoal"), SECONDARY_GOAL("secondaryGoal"), EXPERIENCE("experience"),
    BACKGROUND("background"), LOCATION("location"), EQUIPMENT("equipment"),
    FREQUENCY("frequency"), DURATION("duration"), INTERESTS("interests"), ABOUT_YOU("aboutYou"),
    NUTRITION_GOAL("nutritionGoal"), MUSCLE_FOCUS("muscleFocus"), RECOMP_FOCUS("recompFocus"),
    WEIGHT_GOAL("weightGoal"), DIETARY("dietary"),
    NUTRITION_TRANSITION("nutritionTransition"), NUTRITION_MISSING("nutritionMissing"),
    NUTRITION_TARGETS("nutritionTargets"), CUSTOMIZE_TARGETS("customizeTargets"),
    FITNESS_TRANSITION("fitnessTransition");

    /**
     * Position among the 12 PRD questions. Conditional nutrition branches
     * share Question 11's number and transitions/results have none, so the
     * count never shifts with the branch taken (PRD §31.6 / §37.1A).
     */
    val questionNumber: Int?
        get() = when (this) {
            PRIMARY_GOAL -> 1; SECONDARY_GOAL -> 2; EXPERIENCE -> 3; BACKGROUND -> 4; LOCATION -> 5
            EQUIPMENT -> 6; FREQUENCY -> 7; DURATION -> 8; INTERESTS -> 9; ABOUT_YOU -> 10
            NUTRITION_GOAL, MUSCLE_FOCUS, RECOMP_FOCUS, WEIGHT_GOAL -> 11
            DIETARY -> 12
            else -> null
        }

    companion object {
        const val QUESTION_COUNT = 12
        fun fromKey(key: String?): OnboardingStep? = entries.firstOrNull { it.key == key }
    }
}

enum class OnboardingMode {
    /** First run after sign-up: Welcome → questions → nutrition → matching → Home. */
    FULL,
    /** "Update Training Preferences": the nine fitness questions only. */
    EDIT_TRAINING
}

data class WeightPaceOption(val kgPerWeek: Double, val label: String)

data class TimelineEstimate(val minWeeks: Int, val maxWeeks: Int, val from: Date, val to: Date)

/**
 * Pure onboarding rules — branching, validation, pace/timeline math and the
 * nutrition request mapping. Port of the logic in iOS
 * `OnboardingFlowViewModel`; kept free of Android types so it runs in JVM
 * unit tests.
 */
object OnboardingRules {
    const val KG_PER_LB = 0.45359237
    const val CM_PER_INCH = 2.54

    // MARK: Branching

    fun nextStep(after: OnboardingStep, a: OnboardingAnswers, mode: OnboardingMode): OnboardingStep? = when (after) {
        OnboardingStep.WELCOME -> OnboardingStep.PRIMARY_GOAL
        OnboardingStep.PRIMARY_GOAL -> OnboardingStep.SECONDARY_GOAL
        OnboardingStep.SECONDARY_GOAL -> OnboardingStep.EXPERIENCE
        OnboardingStep.EXPERIENCE -> OnboardingStep.BACKGROUND
        OnboardingStep.BACKGROUND -> OnboardingStep.LOCATION
        OnboardingStep.LOCATION -> OnboardingStep.EQUIPMENT
        OnboardingStep.EQUIPMENT -> OnboardingStep.FREQUENCY
        OnboardingStep.FREQUENCY -> OnboardingStep.DURATION
        OnboardingStep.DURATION -> OnboardingStep.INTERESTS
        OnboardingStep.INTERESTS -> if (mode == OnboardingMode.EDIT_TRAINING) null else OnboardingStep.ABOUT_YOU
        OnboardingStep.ABOUT_YOU -> OnboardingStep.NUTRITION_GOAL
        OnboardingStep.NUTRITION_GOAL -> when (a.nutritionGoal) {
            NutritionGoalChoice.LOSE_WEIGHT -> OnboardingStep.WEIGHT_GOAL
            NutritionGoalChoice.BUILD_MUSCLE_GAIN_WEIGHT -> OnboardingStep.MUSCLE_FOCUS
            NutritionGoalChoice.BODY_RECOMPOSITION -> OnboardingStep.RECOMP_FOCUS
            else -> OnboardingStep.DIETARY
        }
        OnboardingStep.MUSCLE_FOCUS -> if (a.muscleFocus == MuscleFocus.GAIN_WEIGHT) OnboardingStep.WEIGHT_GOAL else OnboardingStep.DIETARY
        OnboardingStep.RECOMP_FOCUS -> if (a.recompFocus == RecompFocus.TARGET_WEIGHT) OnboardingStep.WEIGHT_GOAL else OnboardingStep.DIETARY
        OnboardingStep.WEIGHT_GOAL -> OnboardingStep.DIETARY
        OnboardingStep.DIETARY -> OnboardingStep.NUTRITION_TRANSITION
        OnboardingStep.NUTRITION_TRANSITION -> null
        OnboardingStep.NUTRITION_MISSING, OnboardingStep.NUTRITION_TARGETS, OnboardingStep.CUSTOMIZE_TARGETS -> OnboardingStep.FITNESS_TRANSITION
        OnboardingStep.FITNESS_TRANSITION -> null
    }

    /** Rebuilds the back-stack to [target] so a resumed session's Back behaves as if the user never left. */
    fun replayPath(target: OnboardingStep, a: OnboardingAnswers): List<OnboardingStep> {
        val resumable = when (target) {
            OnboardingStep.NUTRITION_TRANSITION, OnboardingStep.NUTRITION_MISSING,
            OnboardingStep.NUTRITION_TARGETS, OnboardingStep.CUSTOMIZE_TARGETS -> OnboardingStep.DIETARY
            else -> target
        }
        val path = mutableListOf(OnboardingStep.WELCOME)
        var guard = 0
        while (path.last() != resumable && guard < 30) {
            val next = nextStep(path.last(), a, OnboardingMode.FULL) ?: break
            path += next
            guard++
        }
        return if (path.last() == resumable) path else listOf(OnboardingStep.WELCOME)
    }

    fun canContinue(step: OnboardingStep, a: OnboardingAnswers): Boolean = when (step) {
        OnboardingStep.PRIMARY_GOAL -> a.primaryGoal != null
        OnboardingStep.SECONDARY_GOAL -> a.secondaryGoal != null || a.noSecondaryGoal == true
        OnboardingStep.EXPERIENCE -> a.experience != null
        OnboardingStep.BACKGROUND -> a.trainingBackground.isNotEmpty()
        OnboardingStep.LOCATION -> a.locations.isNotEmpty()
        OnboardingStep.EQUIPMENT -> a.equipmentAccess != null && (a.equipmentAccess != EquipmentAccess.HOME_GYM || a.equipment.isNotEmpty())
        OnboardingStep.FREQUENCY -> a.weeklyFrequency != null
        OnboardingStep.DURATION -> a.sessionDuration != null
        OnboardingStep.ABOUT_YOU -> aboutYouErrors(a).isEmpty()
        OnboardingStep.NUTRITION_GOAL -> a.nutritionGoal != null
        OnboardingStep.MUSCLE_FOCUS -> a.muscleFocus != null
        OnboardingStep.RECOMP_FOCUS -> a.recompFocus != null
        OnboardingStep.WEIGHT_GOAL -> weightGoalError(a) == null &&
            (a.skipTargetWeight == true || a.targetWeightKg != null) && a.weeklyRateKg != null
        else -> true
    }

    fun isSkippable(step: OnboardingStep): Boolean = step in setOf(
        OnboardingStep.SECONDARY_GOAL, OnboardingStep.INTERESTS, OnboardingStep.ABOUT_YOU,
        OnboardingStep.NUTRITION_GOAL, OnboardingStep.DIETARY, OnboardingStep.WEIGHT_GOAL
    )

    // MARK: Answer helpers (each returns the new answers)

    fun setPrimaryGoal(a: OnboardingAnswers, goal: FitnessGoal) =
        a.copy(primaryGoal = goal, secondaryGoal = if (a.secondaryGoal == goal) null else a.secondaryGoal)

    fun toggleBackground(a: OnboardingAnswers, item: TrainingBackground): OnboardingAnswers {
        val list = if (item == TrainingBackground.NEW_TO_EXERCISE) {
            if (TrainingBackground.NEW_TO_EXERCISE in a.trainingBackground) emptyList() else listOf(TrainingBackground.NEW_TO_EXERCISE)
        } else {
            val l = a.trainingBackground.filter { it != TrainingBackground.NEW_TO_EXERCISE }
            if (item in l) l - item else l + item
        }
        return a.copy(trainingBackground = list)
    }

    fun <T> toggled(list: List<T>, item: T): List<T> = if (item in list) list - item else list + item

    fun setEquipmentAccess(a: OnboardingAnswers, access: EquipmentAccess) =
        a.copy(equipmentAccess = access, equipment = if (access == EquipmentAccess.HOME_GYM) a.equipment else emptyList())

    /** Changing the nutrition goal clears downstream branch answers (PRD §15). */
    fun setNutritionGoal(a: OnboardingAnswers, goal: NutritionGoalChoice): OnboardingAnswers =
        if (a.nutritionGoal == goal) a else a.copy(
            nutritionGoal = goal, muscleFocus = null, recompFocus = null, targetWeightKg = null,
            skipTargetWeight = null, weeklyRateKg = null, targetDate = null
        )

    fun setMuscleFocus(a: OnboardingAnswers, f: MuscleFocus) = if (a.muscleFocus == f) a else
        a.copy(muscleFocus = f, targetWeightKg = null, skipTargetWeight = null, weeklyRateKg = null, targetDate = null)

    fun setRecompFocus(a: OnboardingAnswers, f: RecompFocus) = if (a.recompFocus == f) a else
        a.copy(recompFocus = f, targetWeightKg = null, skipTargetWeight = null, weeklyRateKg = null, targetDate = null)

    // MARK: About You (mirrors backend ProfileValidation)

    fun aboutYouErrors(a: OnboardingAnswers): List<String> = buildList {
        a.age?.let { if (it !in 13..120) add("Age must be between 13 and 120.") }
        a.heightCm?.let { if (it !in 50.0..300.0) add("Height looks out of range — check your entry.") }
        a.weightKg?.let { if (it !in 20.0..500.0) add("Weight must be between 20 and 500 kg (44–1,100 lb).") }
    }

    fun isMinor(a: OnboardingAnswers) = (a.age ?: 18) < 18

    // MARK: Weight goal (PRD §15A / §18.4–18.8)

    /** True when the goal moves weight DOWN (Lose, or a Recomp target below current). */
    fun losing(a: OnboardingAnswers): Boolean = when (a.nutritionGoal) {
        NutritionGoalChoice.BUILD_MUSCLE_GAIN_WEIGHT -> false
        NutritionGoalChoice.BODY_RECOMPOSITION -> !(a.targetWeightKg != null && a.weightKg != null && a.targetWeightKg > a.weightKg)
        else -> true
    }

    /** Pace ceiling shared with the server: 1% of body weight per week, ≤ 1 kg. */
    fun maxWeeklyRateKg(a: OnboardingAnswers): Double = a.weightKg?.let { min(1.0, it * 0.01) } ?: 0.5

    /** Lose: 0.5/1/1.5/2 lb (or 0.25–1 kg) per week within the ceiling; gain: 0.25/0.5/0.75 % of body weight. */
    fun paceOptions(a: OnboardingAnswers): List<WeightPaceOption> {
        val kgUnit = a.usesKilograms
        return if (losing(a)) {
            val candidates = if (kgUnit) listOf(0.25, 0.5, 0.75, 1.0) else listOf(0.5, 1.0, 1.5, 2.0).map { it * KG_PER_LB }
            candidates.filter { it <= maxWeeklyRateKg(a) + 0.0001 }
                .map { kg -> WeightPaceOption((kg * 1000).roundToInt() / 1000.0, paceLabel(kg, kgUnit)) }
        } else {
            val w = a.weightKg ?: return emptyList()
            listOf(0.0025, 0.005, 0.0075).map { pct ->
                val kg = (w * pct * 1000).roundToInt() / 1000.0
                WeightPaceOption(kg, "${paceLabel(kg, kgUnit)} (${formatNumber(pct * 100)}% of body weight)")
            }
        }
    }

    fun paceLabel(kg: Double, kgUnit: Boolean): String =
        if (kgUnit) "${formatNumber(kg)} kg / week" else "${formatNumber(kg / KG_PER_LB)} lb / week"

    fun formatNumber(v: Double): String {
        val r = (v * 100).roundToInt() / 100.0
        return when {
            r == Math.rint(r) -> r.toLong().toString()
            (r * 10) == Math.rint(r * 10) -> String.format(java.util.Locale.US, "%.1f", r)
            else -> String.format(java.util.Locale.US, "%.2f", r)
        }
    }

    fun displayWeight(kg: Double, kgUnit: Boolean): String {
        val v = if (kgUnit) kg else kg / KG_PER_LB
        return "${formatNumber(Math.rint(v * 10) / 10)} ${if (kgUnit) "kg" else "lb"}"
    }

    fun requiredRateForTargetDate(a: OnboardingAnswers, now: Date = Date()): Double? {
        val date = a.targetDate?.let { MealPlanDay.date(it) } ?: return null
        val t = a.targetWeightKg ?: return null
        val c = a.weightKg ?: return null
        val weeks = max(1.0 / 7.0, (date.time - now.time) / (7.0 * 86_400_000))
        return abs(t - c) / weeks
    }

    fun earliestSafeTargetDate(a: OnboardingAnswers, now: Date = Date()): Date? {
        val t = a.targetWeightKg ?: return null
        val c = a.weightKg ?: return null
        val maxRate = maxWeeklyRateKg(a)
        if (maxRate <= 0) return null
        val days = ceil(abs(t - c) / maxRate * 7).toInt()
        return Calendar.getInstance().apply { time = now; add(Calendar.DAY_OF_YEAR, days) }.time
    }

    /** Blocking problem with the target / pace / date, or null. */
    fun weightGoalError(a: OnboardingAnswers, now: Date = Date()): String? {
        val current = a.weightKg ?: return "Add your current weight on About You to set a weight goal."
        val target = a.targetWeightKg
        if (a.skipTargetWeight != true && target != null) {
            if (target !in 20.0..500.0) return "Target weight must be between 20 and 500 kg (44–1,100 lb)."
            when {
                a.nutritionGoal == NutritionGoalChoice.LOSE_WEIGHT && target >= current ->
                    return "To lose weight, your target needs to be below your current weight."
                a.nutritionGoal == NutritionGoalChoice.BUILD_MUSCLE_GAIN_WEIGHT && target <= current ->
                    return "To gain weight, your target needs to be above your current weight."
                a.nutritionGoal == NutritionGoalChoice.BODY_RECOMPOSITION && target == current ->
                    return "Your target matches your current weight — choose \"Maintain Weight While Building Muscle\" instead, or enter a different target."
            }
            val h = a.heightCm
            if (target < current && h != null && h > 0) {
                val bmi = target / (h / 100).pow(2)
                if (bmi < 18.5) return "That target is below the healthy weight range for your height. Choose a higher target."
            }
        }
        val rate = a.weeklyRateKg
        if (rate != null && rate > maxWeeklyRateKg(a) + 0.0001)
            return "That pace is faster than FitnessLink recommends. Choose a later date or a slower pace."
        if (a.targetDate != null) {
            val required = requiredRateForTargetDate(a, now)
            if (required != null && required > maxWeeklyRateKg(a) + 0.0001) {
                val earliest = earliestSafeTargetDate(a, now)?.let {
                    java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(it)
                } ?: "a later date"
                return "Reaching your target by that date would need ${paceLabel(required, a.usesKilograms)} — faster than FitnessLink recommends. Try $earliest or later."
            }
        }
        return null
    }

    /** Requests a target date and derives the pace it needs (validated by [weightGoalError]). */
    fun withTargetDate(a: OnboardingAnswers, date: Date?, now: Date = Date()): OnboardingAnswers {
        var next = a.copy(targetDate = date?.let { MealPlanDay.string(it) })
        if (date != null) {
            requiredRateForTargetDate(next, now)?.let { next = next.copy(weeklyRateKg = max(0.05, (it * 1000).roundToInt() / 1000.0)) }
        }
        return next
    }

    /** Approximate time-to-goal as a range (§18.8): straight-line estimate stretched 30% for slowing progress. */
    fun estimatedTimeline(a: OnboardingAnswers, now: Date = Date()): TimelineEstimate? {
        if (a.skipTargetWeight == true) return null
        val t = a.targetWeightKg ?: return null
        val c = a.weightKg ?: return null
        val rate = a.weeklyRateKg ?: return null
        if (rate <= 0 || abs(t - c) <= 0.05) return null
        val weeks = abs(t - c) / rate
        val lo = max(1, ceil(weeks).toInt())
        val hi = max(lo + 1, ceil(weeks * 1.3).toInt())
        fun plus(w: Int) = Calendar.getInstance().apply { time = now; add(Calendar.DAY_OF_YEAR, w * 7) }.time
        return TimelineEstimate(lo, hi, plus(lo), plus(hi))
    }

    // MARK: Nutrition request (PRD §17–§19)

    fun missingNutritionInputs(a: OnboardingAnswers): List<String> = buildList {
        when (a.sex) {
            null -> add("Sex for the calorie equation")
            MetabolicSexInput.PREFER_NOT_TO_SAY -> add("Female or Male for the calorie equation (used only for the metabolic estimate)")
            else -> {}
        }
        if (a.age == null) add("Age")
        if (a.heightCm == null) add("Height")
        if (a.weightKg == null) add("Current weight")
        if (a.activityLevel == null) add("Daily activity level")
        if (a.nutritionGoal == null) add("Nutrition goal")
        addAll(aboutYouErrors(a))
    }

    fun firstMissingStep(a: OnboardingAnswers): OnboardingStep =
        if (a.sex == null || a.sex == MetabolicSexInput.PREFER_NOT_TO_SAY || a.age == null || a.heightCm == null ||
            a.weightKg == null || a.activityLevel == null || aboutYouErrors(a).isNotEmpty()) OnboardingStep.ABOUT_YOU
        else OnboardingStep.NUTRITION_GOAL

    fun buildNutritionRequest(a: OnboardingAnswers, now: Date = Date()): CreateNutritionProfileRequest? {
        val sex = a.sex?.nutritionSex ?: return null
        val age = a.age ?: return null
        val height = a.heightCm ?: return null
        val weight = a.weightKg ?: return null
        val activity = a.activityLevel ?: return null
        val goal = a.nutritionGoal ?: return null
        if (aboutYouErrors(a).isNotEmpty()) return null
        val dob = Calendar.getInstance().apply { time = now; add(Calendar.YEAR, -age) }.time

        var primary: String
        var subtype: String? = null
        var rate: Double? = null
        var hasTarget = false
        val targetSet = a.skipTargetWeight != true && a.targetWeightKg != null
        when (goal) {
            NutritionGoalChoice.LOSE_WEIGHT -> { primary = "Lose"; rate = a.weeklyRateKg; hasTarget = targetSet }
            NutritionGoalChoice.BUILD_MUSCLE_GAIN_WEIGHT -> {
                primary = "BuildMuscle"
                if (a.muscleFocus == MuscleFocus.GAIN_WEIGHT) { subtype = "GainWeight"; rate = a.weeklyRateKg; hasTarget = targetSet }
                else subtype = "NoWeightGoal"
            }
            NutritionGoalChoice.BODY_RECOMPOSITION -> {
                primary = "Recomp"
                subtype = (a.recompFocus ?: RecompFocus.MAINTAIN_WEIGHT).wire
                if (a.recompFocus == RecompFocus.TARGET_WEIGHT) { rate = a.weeklyRateKg; hasTarget = targetSet }
            }
            NutritionGoalChoice.MAINTAIN_WEIGHT -> primary = "Maintain"
            NutritionGoalChoice.IMPROVE_HEALTH -> primary = "Health"
        }
        // §18.9: no adult weight-change prescriptions for minors.
        if (isMinor(a) && rate != null) { rate = null; hasTarget = false; if (primary == "Lose") primary = "Maintain" }

        val goalWeight = if (hasTarget) (a.targetWeightKg ?: weight) else weight
        var timeline = "NinetyDays"
        var customDate: String? = null
        if (hasTarget) estimatedTimeline(a, now)?.let { timeline = "Custom"; customDate = MealPlanDay.string(it.from) }
        val prefs = a.dietaryPreference?.takeIf { it != DietaryPreferenceChoice.NO_PREFERENCE }?.let { listOf(it.title) }
        val allergies = a.allergies + (a.otherRestrictions?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList())
        fun r1(v: Double) = Math.rint(v * 10) / 10

        return CreateNutritionProfileRequest(
            dateOfBirth = MealPlanDay.string(dob),
            sex = sex,
            heightCm = r1(height),
            currentWeightKg = r1(weight),
            goalWeightKg = r1(goalWeight),
            activityLevel = activity.wire,
            workoutFrequency = a.weeklyFrequency?.nutritionBand ?: "None",
            primaryGoal = primary,
            timeline = timeline,
            timelineCustomDate = customDate,
            dietaryPreferences = prefs,
            allergies = allergies.ifEmpty { null },
            goalSubtype = subtype,
            weeklyRateKg = rate?.let { (it * 1000).roundToInt() / 1000.0 },
            hasGoalWeight = hasTarget
        )
    }
}
