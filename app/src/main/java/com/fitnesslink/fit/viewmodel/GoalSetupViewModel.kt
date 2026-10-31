package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fitnesslink.fit.goals.GoalFormat
import com.fitnesslink.fit.goals.GoalNutritionDay
import com.fitnesslink.fit.goals.GoalNutritionTargets
import com.fitnesslink.fit.goals.GoalSetRecord
import com.fitnesslink.fit.goals.GoalStore
import com.fitnesslink.fit.goals.LocalGoalDataSource
import com.fitnesslink.fit.goals.StrengthMetrics
import com.fitnesslink.fit.health.HealthConnectService
import com.fitnesslink.fit.model.CustomGoalMetric
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalActivity
import com.fitnesslink.fit.model.GoalConfig
import com.fitnesslink.fit.model.GoalDay
import com.fitnesslink.fit.model.GoalFrequency
import com.fitnesslink.fit.model.GoalMacroTarget
import com.fitnesslink.fit.model.GoalReminder
import com.fitnesslink.fit.model.GoalStatus
import com.fitnesslink.fit.model.GoalSubtype
import com.fitnesslink.fit.model.GoalTemplate
import com.fitnesslink.fit.model.GoalTrackingSource
import com.fitnesslink.fit.model.GoalType
import com.fitnesslink.fit.model.HydrationGoal
import com.fitnesslink.fit.model.MacroKey
import com.fitnesslink.fit.model.NutritionHabit
import com.fitnesslink.fit.model.RecoveryMetric
import com.fitnesslink.fit.model.StrengthMetric
import com.fitnesslink.fit.model.WaterUnit
import com.fitnesslink.fit.model.WeightEntry
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.NutritionTargets
import com.fitnesslink.fit.nutrition.HydrationStore
import com.fitnesslink.fit.nutrition.NutritionGoalsStore
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.programs.UserProgramStore
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Everything the setup flow reads or writes outside itself, so the rules
 * below are unit-testable (iOS reads `DatabaseManager` / stores directly).
 */
interface GoalSetupEnvironment {
    fun weightEntries(): List<WeightEntry>
    fun hydrationGoal(): HydrationGoal
    fun hasPinnedGoal(): Boolean
    fun setRecords(movementName: String?): List<GoalSetRecord>
    fun nutritionGoal(): GoalNutritionTargets
    fun nutritionTargets(): NutritionTargets?
    fun hasNutritionProfile(): Boolean
    fun nutritionDays(since: LocalDate): List<GoalNutritionDay>
    /** (programId, programName) of the active program, if any. */
    fun activeProgram(): Pair<String, String>?
    val healthConnected: Boolean
    /** Health Connect per-day resting HR / HRV averages (recovery baseline prefill); empty when not connected. */
    fun recoveryDailyAverages(metric: RecoveryMetric, from: LocalDate, to: LocalDate): Map<LocalDate, Double> = emptyMap()
    val isOnline: Boolean
    suspend fun overrideNutritionTargets(calories: Int, protein: Double, fat: Double, carbs: Double)
    suspend fun saveHydrationGoal(goal: HydrationGoal)
    suspend fun createGoal(goal: Goal)
}

/** Production environment over the local cache and stores. */
object LocalGoalSetupEnvironment : GoalSetupEnvironment {
    override fun weightEntries(): List<WeightEntry> =
        runCatching { com.fitnesslink.fit.progress.BodyTrackingStore.weightEntries() }.getOrDefault(emptyList())
    override fun hydrationGoal(): HydrationGoal = runCatching { HydrationStore.currentGoal() }.getOrDefault(HydrationGoal())
    override fun hasPinnedGoal(): Boolean = GoalStore.pinnedGoal() != null
    override fun setRecords(movementName: String?): List<GoalSetRecord> = LocalGoalDataSource().setRecords(movementName)
    override fun nutritionGoal(): GoalNutritionTargets = LocalGoalDataSource().nutritionTargets()
    override fun nutritionTargets(): NutritionTargets? = NutritionGoalsStore.targets.value
    override fun hasNutritionProfile(): Boolean = NutritionGoalsStore.hasProfile
    override fun nutritionDays(since: LocalDate): List<GoalNutritionDay> = LocalGoalDataSource().nutritionDays(since)
    override fun activeProgram(): Pair<String, String>? =
        runCatching { UserProgramStore.active() }.getOrNull()?.let { it.programId to it.programName }
    override val healthConnected: Boolean get() = HealthConnectService.isConnected
    override fun recoveryDailyAverages(metric: RecoveryMetric, from: LocalDate, to: LocalDate): Map<LocalDate, Double> =
        LocalGoalDataSource().recoveryDailyAverages(metric, from, to)
    override val isOnline: Boolean get() = NetworkMonitor.isConnected.value
    override suspend fun overrideNutritionTargets(calories: Int, protein: Double, fat: Double, carbs: Double) =
        NutritionGoalsStore.overrideTargets(calories, protein, fat, carbs)
    override suspend fun saveHydrationGoal(goal: HydrationGoal) {
        HydrationStore.updateGoal(goal)
        runCatching { DatabaseManager.saveHydrationGoal(goal) }
    }
    override suspend fun createGoal(goal: Goal) = GoalStore.create(goal)
}

/**
 * Port of iOS `GoalSetupViewModel` (Goals PRD §1.5): one guided flow for all
 * six categories. Steps adapt to the chosen goal type, so irrelevant fields
 * never appear. Nothing is written until the user confirms the review step,
 * and the goal id is minted once per draft so repeated taps can't create
 * duplicates. A plain Compose state holder (the screen `remember`s it).
 */
class GoalSetupViewModel(
    category: GoalType? = null,
    private val env: GoalSetupEnvironment = LocalGoalSetupEnvironment,
    private val today: LocalDate = GoalDay.today()
) {
    enum class Step(val title: String) {
        CATEGORY("What do you want to achieve?"),
        TYPE("Choose a goal"),
        DEFINE("Define your goal"),
        BASELINE("Where are you starting?"),
        TARGET("Set your target"),
        FREQUENCY("How often?"),
        TIME_FRAME("Choose a time frame"),
        TRACKING("Tracking & reminders"),
        REVIEW("Review your goal"),
        CONFIRMATION("Goal created")
    }

    data class TimeFrameOption(val weeks: Int, val isRecommended: Boolean, val inMonths: Boolean = false) {
        /** Routines read in months (3 / 6 / 12); everything else in weeks. */
        val label: String
            get() {
                if (!inMonths) return "$weeks weeks"
                return when (weeks) {
                    52 -> "1 year"
                    26 -> "6 months"
                    13 -> "3 months"
                    else -> "$weeks weeks"
                }
            }
    }

    data class Recommendation(val weeks: Int?, val basis: String, val personalized: Boolean)

    // MARK: Draft state

    val goalId: String = UUID.randomUUID().toString().lowercase(Locale.US)
    var category by mutableStateOf<GoalType?>(null)
        private set
    var subtype by mutableStateOf<GoalSubtype?>(null)
        private set
    var stepIndex by mutableStateOf(0)

    var title by mutableStateOf("")
    // Weight & strength
    var weightUnit by mutableStateOf(WeightUnit.defaultUnit)
    var baselineText by mutableStateOf("")
    var baselineDate by mutableStateOf<Long?>(null)
    var baselineSourceText by mutableStateOf<String?>(null)
    var targetText by mutableStateOf("")
    var movementName by mutableStateOf<String?>(null)
    var movementId by mutableStateOf<String?>(null)
    var strengthMetric by mutableStateOf(StrengthMetric.LOAD)
    var loadText by mutableStateOf("")
    var volumeScopeIsExercise by mutableStateOf(false)
    var volumePerWorkout by mutableStateOf(false)
    // Sleep & recovery
    var sleepHours by mutableStateOf(7)
    var sleepMinutes by mutableStateOf(0)
    var nightsPerWeek by mutableStateOf(5)
    var recoveryMetric by mutableStateOf(RecoveryMetric.RESTING_HEART_RATE)
    var recoveryBaseline by mutableStateOf<Double?>(null)
    // Nutrition
    var calorieUseRange by mutableStateOf(false)
    var calorieMinText by mutableStateOf("")
    var calorieMaxText by mutableStateOf("")
    val selectedMacros = mutableStateListOf(MacroKey.PROTEIN)
    val macroText = mutableStateMapOf<MacroKey, String>()
    var waterUnit by mutableStateOf(WaterUnit.defaultUnit)
    var nutritionHabitId by mutableStateOf<String?>(null)
    // Routine
    var templateId by mutableStateOf<String?>(null)
    val activities = mutableStateListOf<GoalActivity>()
    val steps = mutableStateListOf<String>()
    var useProgram by mutableStateOf(false)
    var sessionMinutesText by mutableStateOf("")
    // Custom
    var customMetric by mutableStateOf(CustomGoalMetric.COUNT)
    var customUnit by mutableStateOf("")
    var actionName by mutableStateOf("")
    // Shared
    var isDaily by mutableStateOf(false)
    var perWeek by mutableStateOf(3)
    var customFrequency by mutableStateOf(GoalFrequency.Daily)
    var flexibleSchedule by mutableStateOf(true)
    val weekdays = mutableStateListOf<Int>()
    var usePreferredTime by mutableStateOf(false)
    var preferredTime by mutableStateOf(LocalTime.of(18, 0))
    var trackingSource by mutableStateOf(GoalTrackingSource.Manual)
    var reminderEnabled by mutableStateOf(false)
    var reminderTime by mutableStateOf(LocalTime.of(19, 0))
    var startDate by mutableStateOf(today)
    var selectedWeeks by mutableStateOf<Int?>(null)
    var useCustomDate by mutableStateOf(false)
    var customTargetDate by mutableStateOf(today.plusMonths(3))
    var pinToHome by mutableStateOf(false)

    var isSaving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var createdGoal by mutableStateOf<Goal?>(null)
        private set
    /** Set when the flow was opened from a category entry point. */
    var fixedCategory = false
        private set

    /** Snapshot of [GoalSetupEnvironment.healthConnected]; [refreshHealthConnection] after Connected Apps changes it. */
    var healthConnected by mutableStateOf(env.healthConnected)
        private set

    fun refreshHealthConnection() {
        healthConnected = env.healthConnected
    }

    /** iOS `GoalTargetStep.prepare`: the last 14 days' average as the recovery baseline, when there are 3+ readings. Blocking; call off the main thread. */
    fun recoveryBaselineFromHealth(): Double? {
        if (!healthConnected) return null
        val today = LocalDate.now()
        val daily = env.recoveryDailyAverages(recoveryMetric, today.minusDays(14), today)
        return if (daily.size >= 3) daily.values.sum() / daily.size else null
    }

    init {
        weightUnit = env.weightEntries().firstOrNull()?.unit ?: WeightUnit.defaultUnit
        waterUnit = env.hydrationGoal().displayUnit.let { if (it == WaterUnit.CUPS) WaterUnit.OZ else it }
        pinToHome = !env.hasPinnedGoal()
        if (category != null) {
            fixedCategory = true
            select(category)
        }
    }

    // MARK: Steps

    val flowSteps: List<Step>
        get() {
            val list = mutableListOf(Step.CATEGORY)
            val category = category ?: return list
            if (category != GoalType.BodyComposition) list.add(Step.TYPE)
            val subtype = subtype ?: return list
            list += when (subtype) {
                GoalSubtype.LoseWeight -> listOf(Step.BASELINE, Step.TARGET)
                GoalSubtype.ImproveLift, GoalSubtype.PersonalRecord, GoalSubtype.RepsAtWeight -> listOf(Step.DEFINE, Step.BASELINE, Step.TARGET)
                GoalSubtype.TrainingVolume -> listOf(Step.DEFINE, Step.TARGET)
                GoalSubtype.SleepDuration, GoalSubtype.SleepConsistency -> listOf(Step.TARGET, Step.TRACKING)
                GoalSubtype.RecoveryMetric -> listOf(Step.DEFINE, Step.TARGET)
                GoalSubtype.CalorieTarget, GoalSubtype.MacroTarget, GoalSubtype.Hydration -> listOf(Step.TARGET, Step.FREQUENCY)
                GoalSubtype.HealthyHabit -> listOf(Step.DEFINE, Step.TARGET)
                GoalSubtype.WorkoutConsistency -> listOf(Step.DEFINE, Step.FREQUENCY, Step.TRACKING)
                GoalSubtype.RecoveryRoutine, GoalSubtype.MovementRoutine, GoalSubtype.WellnessRoutine, GoalSubtype.DailyHabit ->
                    listOf(Step.DEFINE, Step.FREQUENCY)
                GoalSubtype.CustomRoutine -> listOf(Step.DEFINE, Step.FREQUENCY)
                GoalSubtype.CustomGoal -> listOf(Step.DEFINE, Step.TARGET, Step.TRACKING)
            }
            list.add(Step.TIME_FRAME)
            if (Step.TRACKING !in list) list.add(Step.TRACKING)
            list += listOf(Step.REVIEW, Step.CONFIRMATION)
            return list
        }

    val currentStep: Step get() = flowSteps.let { it[min(stepIndex, it.size - 1)] }
    val progressCount: Int get() = max(1, flowSteps.size - 1)

    fun select(category: GoalType) {
        if (this.category == category) return
        this.category = category
        subtype = if (category == GoalType.BodyComposition) GoalSubtype.LoseWeight else null
        subtype?.let { applyDefaults(it) }
    }

    fun select(subtype: GoalSubtype) {
        if (this.subtype == subtype) return
        this.subtype = subtype
        applyDefaults(subtype)
    }

    fun next() {
        if (!canContinue) return
        if (currentStep == Step.REVIEW) return
        stepIndex = min(stepIndex + 1, flowSteps.size - 1)
        onEnter(currentStep)
    }

    fun back() {
        stepIndex = max(0, stepIndex - (if (fixedCategory && stepIndex == 1) 0 else 1))
    }

    val canGoBack: Boolean get() = stepIndex > (if (fixedCategory) 1 else 0) && currentStep != Step.CONFIRMATION

    // MARK: Defaults

    private fun applyDefaults(subtype: GoalSubtype) {
        title = if (subtype == GoalSubtype.CustomGoal) "" else subtype.title
        targetText = ""
        baselineText = ""
        baselineDate = null
        baselineSourceText = null
        templateId = null
        activities.clear()
        steps.clear()
        selectedWeeks = null
        useCustomDate = false
        isDaily = false
        when (subtype) {
            GoalSubtype.LoseWeight -> {
                trackingSource = GoalTrackingSource.WeightLog
                title = "Lose Weight"
                prefillWeightBaseline()
            }
            GoalSubtype.ImproveLift, GoalSubtype.PersonalRecord, GoalSubtype.RepsAtWeight, GoalSubtype.TrainingVolume -> {
                trackingSource = GoalTrackingSource.WorkoutLogs
                strengthMetric = when (subtype) {
                    GoalSubtype.RepsAtWeight -> StrengthMetric.REPS
                    GoalSubtype.TrainingVolume -> StrengthMetric.VOLUME
                    else -> StrengthMetric.LOAD
                }
            }
            GoalSubtype.SleepDuration -> {
                trackingSource = if (healthConnected) GoalTrackingSource.AppleHealth else GoalTrackingSource.Manual
                isDaily = true
            }
            GoalSubtype.SleepConsistency -> {
                trackingSource = if (healthConnected) GoalTrackingSource.AppleHealth else GoalTrackingSource.Manual
                nightsPerWeek = 5
            }
            GoalSubtype.RecoveryMetric -> trackingSource = GoalTrackingSource.AppleHealth
            GoalSubtype.CalorieTarget, GoalSubtype.MacroTarget -> {
                trackingSource = GoalTrackingSource.NutritionLogs
                isDaily = true
                prefillNutrition()
            }
            GoalSubtype.Hydration -> {
                trackingSource = GoalTrackingSource.WaterLog
                isDaily = true
                val goal = env.hydrationGoal()
                targetText = plain(goal.unit.convert(goal.dailyGoal, waterUnit), 0)
            }
            GoalSubtype.HealthyHabit -> trackingSource = GoalTrackingSource.Manual
            GoalSubtype.WorkoutConsistency -> {
                trackingSource = GoalTrackingSource.WorkoutLogs
                perWeek = 3
            }
            GoalSubtype.RecoveryRoutine, GoalSubtype.MovementRoutine, GoalSubtype.WellnessRoutine, GoalSubtype.DailyHabit,
            GoalSubtype.CustomRoutine -> trackingSource = GoalTrackingSource.Manual
            GoalSubtype.CustomGoal -> {
                trackingSource = GoalTrackingSource.Manual
                customMetric = CustomGoalMetric.COUNT
                customUnit = CustomGoalMetric.COUNT.defaultUnit
                customFrequency = GoalFrequency.Weekly
            }
        }
    }

    private fun onEnter(step: Step) {
        val s = subtype
        if (step == Step.BASELINE && s in setOf(GoalSubtype.ImproveLift, GoalSubtype.PersonalRecord, GoalSubtype.RepsAtWeight) && baselineText.isEmpty()) {
            prefillStrengthBaseline()
        }
        if (step == Step.TARGET && s == GoalSubtype.TrainingVolume && baselineText.isEmpty()) prefillVolumeBaseline()
        if (step == Step.TIME_FRAME && selectedWeeks == null && !useCustomDate) {
            selectedWeeks = recommendation.weeks ?: timeFrameOptions.firstOrNull()?.weeks
        }
    }

    // MARK: Prefill

    private fun prefillWeightBaseline() {
        val latest = env.weightEntries().maxByOrNull { it.date.time } ?: return
        weightUnit = latest.unit
        baselineText = plain(latest.weight)
        baselineDate = latest.date.time
        baselineSourceText = "From your Weight Log on ${GoalFormat.shortDate(GoalDay.localDate(latest.date.time))}"
    }

    /** Exercises the user has logged sets for, most recent first. */
    val historyExercises: List<String>
        get() {
            val seen = HashSet<String>()
            return env.setRecords(null).reversed().map { it.exerciseName.trim() }
                .filter { it.isNotEmpty() && seen.add(it.lowercase(Locale.US)) }
        }

    private fun strengthSets(): List<GoalSetRecord> = movementName?.let { env.setRecords(it) } ?: emptyList()

    fun prefillStrengthBaseline() {
        val sets = strengthSets()
        if (subtype == GoalSubtype.RepsAtWeight) {
            val load = loadText.toDoubleOrNull() ?: return
            val loadLbs = weightUnit.convert(load, WeightUnit.LBS)
            val best = sets.filter { StrengthMetrics.sameLoad(it.weightLbs, loadLbs) }.maxByOrNull { it.reps }
            if (best != null) {
                baselineText = "${best.reps}"
                baselineDate = best.date
                baselineSourceText = "Your best set at this load, ${GoalFormat.shortDate(GoalDay.localDate(best.date))} · Workout log"
            } else {
                baselineText = ""
                baselineSourceText = null
            }
            return
        }
        // Recent 90 days for a lift goal; all-time record for a PR goal.
        val cutoff = if (subtype == GoalSubtype.PersonalRecord) Long.MIN_VALUE else GoalDay.startOfDayMillis(today.minusDays(90))
        val scored = sets.filter { it.date >= cutoff }.mapNotNull { set ->
            StrengthMetrics.value(strengthMetric, set)?.let { set to WeightUnit.LBS.convert(it, weightUnit) }
        }
        val best = scored.maxByOrNull { it.second }
        if (best == null) {
            baselineText = ""
            baselineSourceText = null
            return
        }
        baselineText = plain(best.second)
        baselineDate = best.first.date
        val label = if (strengthMetric.isEstimated)
            "Estimated from your ${GoalFormat.weight(WeightUnit.LBS.convert(best.first.weightLbs, weightUnit), weightUnit)} × ${best.first.reps} set"
        else "Your heaviest working set"
        baselineSourceText = "$label, ${GoalFormat.shortDate(GoalDay.localDate(best.first.date))} · Workout log"
    }

    /** Average weekly (or per-workout) volume over the last four weeks with workouts. */
    private fun prefillVolumeBaseline() {
        val since = GoalDay.startOfDayMillis(today.minusDays(28))
        val sets = env.setRecords(if (volumeScopeIsExercise) movementName else null).filter { it.weightLbs > 0 && it.date >= since }
        if (sets.isEmpty()) return
        val sessions = sets.groupBy { it.sessionId }.mapValues { (_, g) -> g.sumOf { it.weightLbs * it.reps } }
        val value = if (volumePerWorkout) sessions.values.sum() / sessions.size
        else sessions.values.sum() / max(1, sets.map { GoalDay.weekStart(GoalDay.localDate(it.date)) }.toSet().size)
        baselineText = plain(WeightUnit.LBS.convert(value, weightUnit), 0)
        baselineSourceText = "Your average over the last 4 weeks with workouts"
    }

    private fun prefillNutrition() {
        val live = env.nutritionGoal()
        if (live.calories > 0) targetText = "${live.calories}"
        macroText[MacroKey.PROTEIN] = if (live.protein > 0) "${live.protein}" else ""
        macroText[MacroKey.CARBS] = if (live.carbs > 0) "${live.carbs}" else ""
        macroText[MacroKey.FAT] = if (live.fat > 0) "${live.fat}" else ""
        env.nutritionTargets()?.let { t ->
            targetText = plain(t.dailyCalorieTarget, 0)
            macroText[MacroKey.PROTEIN] = plain(t.proteinGrams, 0)
            macroText[MacroKey.CARBS] = plain(t.carbGrams, 0)
            macroText[MacroKey.FAT] = plain(t.fatGrams, 0)
        }
    }

    /** Re-reads Nutrition targets after a refresh (the target step refreshes the store on entry). */
    fun refreshNutritionPrefill() {
        if (subtype == GoalSubtype.CalorieTarget || subtype == GoalSubtype.MacroTarget) prefillNutrition()
    }

    fun applyTemplate(template: GoalTemplate) {
        templateId = template.id
        title = template.name
        perWeek = template.perWeek
        isDaily = template.perWeek == 7
        sessionMinutesText = template.minutes?.toString() ?: ""
        steps.clear(); steps.addAll(template.steps)
        if (subtype == GoalSubtype.WorkoutConsistency) trackingSource = if (useProgram) GoalTrackingSource.Program else GoalTrackingSource.WorkoutLogs
    }

    fun applyNutritionHabit(habit: NutritionHabit) {
        nutritionHabitId = habit.id
        title = habit.name
        targetText = habit.defaultTarget?.let { plain(it) } ?: ""
        perWeek = habit.defaultDaysPerWeek
        isDaily = false
        trackingSource = habit.tracking
    }

    /** Clears a picked template when the user renames the routine (iOS `onChange(of: title)`). */
    fun renameRoutine(name: String) {
        title = name
        val template = GoalTemplate.template(templateId)
        if (template != null && template.name != name) templateId = null
    }

    fun toggleMacro(key: MacroKey, on: Boolean) {
        if (on && key !in selectedMacros) selectedMacros.add(key)
        if (!on) selectedMacros.remove(key)
    }

    fun toggleWeekday(day: Int) {
        if (day in weekdays) weekdays.remove(day) else weekdays.add(day)
    }

    fun setWaterUnitConverting(unit: WaterUnit) {
        val old = waterUnit
        targetText.toDoubleOrNull()?.let { targetText = plain(old.convert(it, unit), 0) }
        waterUnit = unit
    }

    // MARK: Recommendation

    data class CalorieRecommendation(val value: Double, val basis: String, val personalized: Boolean)

    val calorieRecommendation: CalorieRecommendation?
        get() {
            val targets = env.nutritionTargets() ?: return null
            val personalized = env.hasNutritionProfile() && !targets.isManualOverride
            val basis = if (personalized)
                "Estimated from your Nutrition profile — age, height, weight, activity level, and goal (maintenance ≈ ${GoalFormat.number(targets.maintenanceCalories, 0)} cal). An estimate, not medical advice."
            else "Your current Nutrition target, which was set manually."
            return CalorieRecommendation(targets.dailyCalorieTarget, basis, personalized)
        }

    /** Average logged intake — observed food logs, not an estimate of needs. */
    val averageLoggedCalories: Pair<Double, Int>?
        get() {
            val rows = env.nutritionDays(today.minusDays(14)).filter { it.calories > 0 }
            if (rows.size < 5) return null
            return rows.sumOf { it.calories } / rows.size to rows.size
        }

    val recommendation: Recommendation
        get() {
            val subtype = subtype ?: return Recommendation(null, "", false)
            val baseline = baselineText.toDoubleOrNull()
            val target = targetText.toDoubleOrNull()
            when (subtype) {
                GoalSubtype.LoseWeight -> if (baseline != null && target != null && baseline > target) {
                    val baselineLbs = weightUnit.convert(baseline, WeightUnit.LBS)
                    val paceLbs = min(1.5, max(0.5, baselineLbs * 0.005))
                    val pace = WeightUnit.LBS.convert(paceLbs, weightUnit)
                    val weeks = ceil(roundNoise((baseline - target) / pace)).toInt()
                    return Recommendation(min(104, max(4, weeks)),
                        "Based on a gradual pace of about ${GoalFormat.weight(pace, weightUnit)} per week (roughly 0.5% of body weight). An estimate, not a guarantee.",
                        true)
                }
                GoalSubtype.ImproveLift, GoalSubtype.PersonalRecord -> if (baseline != null && target != null && baseline > 0 && target > baseline) {
                    val weeks = ceil(roundNoise((target - baseline) / baseline / 0.01)).toInt()
                    return Recommendation(min(52, max(4, weeks)),
                        "Assumes about 1% improvement per week from your baseline. Strength gains vary and slow as you get stronger — this is an estimate.",
                        baselineSourceText != null)
                }
                GoalSubtype.RepsAtWeight -> if (baseline != null && target != null && target > baseline) {
                    return Recommendation(min(26, max(4, ((target - baseline) * 2).toInt())),
                        "Assumes about one extra rep every two weeks. Progress varies — this is an estimate.", baselineSourceText != null)
                }
                GoalSubtype.TrainingVolume -> if (baseline != null && target != null && baseline > 0 && target > baseline) {
                    val weeks = ceil(roundNoise((target - baseline) / baseline / 0.05)).toInt()
                    return Recommendation(min(26, max(4, weeks)),
                        "Assumes volume grows about 5% per week from your recent average. An estimate, not a prescription.", true)
                }
                GoalSubtype.SleepDuration, GoalSubtype.SleepConsistency ->
                    return Recommendation(8, "Eight weeks gives several complete weeks to establish and review a pattern.", false)
                GoalSubtype.RecoveryMetric ->
                    return Recommendation(8, "Eight weeks collects enough readings to see a trend rather than day-to-day noise.", false)
                GoalSubtype.CalorieTarget, GoalSubtype.MacroTarget, GoalSubtype.Hydration -> {
                    val goalDate = GoalDay.parse(env.nutritionTargets()?.estimatedGoalDate)
                    if (subtype == GoalSubtype.CalorieTarget && goalDate != null) {
                        val weeks = (ChronoUnit.DAYS.between(today, goalDate) / 7).toInt()
                        if (weeks >= 4) return Recommendation(min(104, weeks), "Matches the goal date your Nutrition profile estimates.", true)
                    }
                    return Recommendation(12, "Twelve weeks is a common period to build and review a nutrition habit.", false)
                }
                GoalSubtype.HealthyHabit, GoalSubtype.RecoveryRoutine ->
                    return Recommendation(8, "Eight weeks allows time to establish and review the habit.", false)
                GoalSubtype.WorkoutConsistency, GoalSubtype.MovementRoutine, GoalSubtype.WellnessRoutine, GoalSubtype.DailyHabit,
                GoalSubtype.CustomRoutine ->
                    return Recommendation(13, "Three months is long enough to make a routine stick and review how it's going.", false)
                GoalSubtype.CustomGoal -> Unit
            }
            return Recommendation(null, "", false)
        }

    val timeFrameOptions: List<TimeFrameOption>
        get() {
            val recommended = recommendation.weeks
            val isRoutine = subtype?.category == GoalType.Consistency
            val weeks = (if (isRoutine) mutableListOf(13, 26, 52) else mutableListOf(4, 8, 12, 26))
            if (recommended != null && recommended !in weeks) weeks.add(recommended)
            return weeks.sorted().map { TimeFrameOption(it, it == recommended, isRoutine) }
        }

    val targetDate: LocalDate
        get() {
            if (useCustomDate) return customTargetDate
            val weeks = selectedWeeks ?: recommendation.weeks ?: 12
            return startDate.plusDays(weeks * 7L)
        }

    val durationText: String
        get() {
            val days = ChronoUnit.DAYS.between(startDate, targetDate)
            return if (days % 7 == 0L) "${days / 7} weeks" else "$days days"
        }

    /** Rate needed to hit the target by the chosen date (weight / strength). */
    val requiredRateText: String?
        get() {
            val subtype = subtype ?: return null
            val baseline = baselineText.toDoubleOrNull() ?: return null
            val target = targetText.toDoubleOrNull() ?: return null
            val days = max(1L, ChronoUnit.DAYS.between(startDate, targetDate)).toDouble()
            val perWeek = abs(target - baseline) / (days / 7)
            return when (subtype) {
                GoalSubtype.LoseWeight -> {
                    var text = "About ${GoalFormat.weight(perWeek, weightUnit)} per week"
                    if (perWeek > baseline * 0.01) text += " — faster than about 1% of body weight per week"
                    text
                }
                GoalSubtype.ImproveLift, GoalSubtype.PersonalRecord -> "About ${GoalFormat.weight(perWeek, weightUnit)} per week"
                GoalSubtype.RepsAtWeight -> "About ${GoalFormat.number(perWeek)} reps per week"
                else -> null
            }
        }

    // MARK: Validation

    val sleepTargetMinutes: Double get() = (sleepHours * 60 + sleepMinutes).toDouble()

    val canContinue: Boolean
        get() = when (currentStep) {
            Step.CATEGORY -> category != null
            Step.TYPE -> subtype != null
            Step.DEFINE -> defineValid
            Step.BASELINE -> baselineValid
            Step.TARGET -> targetError == null && targetFilled
            Step.FREQUENCY -> !(subtype == GoalSubtype.CustomRoutine && activities.isEmpty()) && (flexibleSchedule || weekdays.isNotEmpty())
            Step.TIME_FRAME -> targetDate.isAfter(startDate)
            Step.TRACKING -> trackingError == null
            Step.REVIEW, Step.CONFIRMATION -> true
        }

    private val defineValid: Boolean
        get() = when (subtype) {
            GoalSubtype.ImproveLift, GoalSubtype.PersonalRecord -> movementName != null
            GoalSubtype.RepsAtWeight -> movementName != null && (loadText.toDoubleOrNull() ?: 0.0) > 0
            GoalSubtype.TrainingVolume -> !volumeScopeIsExercise || movementName != null
            GoalSubtype.RecoveryMetric -> healthConnected
            GoalSubtype.HealthyHabit -> nutritionHabitId != null
            GoalSubtype.WorkoutConsistency, GoalSubtype.RecoveryRoutine, GoalSubtype.MovementRoutine, GoalSubtype.WellnessRoutine,
            GoalSubtype.DailyHabit -> title.isNotBlank()
            GoalSubtype.CustomRoutine -> title.isNotBlank() && activities.isNotEmpty()
            GoalSubtype.CustomGoal -> title.isNotBlank() && (customMetric != CustomGoalMetric.MANUAL || actionName.isNotBlank())
            else -> true
        }

    private val baselineValid: Boolean
        get() = (baselineText.toDoubleOrNull() ?: 0.0) > 0 || (subtype == GoalSubtype.RepsAtWeight && baselineText.toIntOrNull() != null)

    private val targetFilled: Boolean
        get() = when (subtype) {
            GoalSubtype.SleepDuration, GoalSubtype.SleepConsistency -> sleepTargetMinutes > 0
            GoalSubtype.CalorieTarget -> if (calorieUseRange) calorieMinText.toDoubleOrNull() != null && calorieMaxText.toDoubleOrNull() != null
            else targetText.toDoubleOrNull() != null
            GoalSubtype.MacroTarget -> selectedMacros.isNotEmpty() && selectedMacros.all { (macroText[it]?.toDoubleOrNull() ?: 0.0) > 0 }
            GoalSubtype.HealthyHabit -> NutritionHabit.habit(nutritionHabitId)?.defaultTarget == null || targetText.toDoubleOrNull() != null
            GoalSubtype.CustomGoal -> if (customMetric == CustomGoalMetric.MANUAL || customMetric == CustomGoalMetric.FREQUENCY) true
            else targetText.toDoubleOrNull() != null
            else -> targetText.toDoubleOrNull() != null
        }

    /** Validation message for the target step (null = valid). */
    val targetError: String?
        get() {
            val subtype = subtype ?: return null
            val target = targetText.toDoubleOrNull()
            val baseline = baselineText.toDoubleOrNull()
            when (subtype) {
                GoalSubtype.LoseWeight -> {
                    if (target == null || baseline == null) return null
                    if (target >= baseline) return "A Lose Weight goal needs a target below your current weight. To gain or maintain weight, use a Custom Goal."
                    if (target <= 0) return "Enter a target weight."
                }
                GoalSubtype.ImproveLift, GoalSubtype.PersonalRecord, GoalSubtype.TrainingVolume -> {
                    if (target == null || baseline == null) return null
                    if (target <= baseline) return "Your target needs to be higher than your baseline."
                }
                GoalSubtype.RepsAtWeight -> {
                    if (target == null || baseline == null) return null
                    if (target <= baseline) return "Target reps need to be more than your baseline reps."
                }
                GoalSubtype.RecoveryMetric -> if (target != null && target <= 0) return "Enter a target value."
                GoalSubtype.CalorieTarget -> {
                    val low = calorieMinText.toDoubleOrNull()
                    val high = calorieMaxText.toDoubleOrNull()
                    if (calorieUseRange && low != null && high != null && low >= high) return "The lower end of the range needs to be below the upper end."
                    if (target != null && !calorieUseRange && target < 1000) {
                        return "Targets under 1,000 calories a day aren't supported. Talk to a healthcare professional about very low-calorie plans."
                    }
                }
                GoalSubtype.Hydration, GoalSubtype.CustomGoal, GoalSubtype.HealthyHabit -> if (target != null && target <= 0) return "Enter a target above zero."
                else -> Unit
            }
            return null
        }

    val trackingError: String?
        get() = when {
            subtype == GoalSubtype.RecoveryMetric ->
                if (healthConnected) null else "Recovery metric goals need Health Connect. Connect it in Connected Apps first."
            subtype == GoalSubtype.SleepDuration || subtype == GoalSubtype.SleepConsistency ->
                if (trackingSource == GoalTrackingSource.AppleHealth && !healthConnected) "Health Connect isn't connected. Connect it, or choose manual entry." else null
            subtype == GoalSubtype.CustomGoal && customMetric == CustomGoalMetric.STEPS ->
                if (healthConnected) null else "Step goals need Health Connect. Connect it, or choose a different metric."
            else -> null
        }

    // MARK: Build

    val weeklyTarget: Int get() = if (isDaily) 7 else perWeek

    fun buildGoal(): Goal {
        val subtype = subtype ?: return Goal()
        var goal = Goal(
            id = goalId, goalType = subtype.category, title = title.trim(),
            startDate = GoalDay.startOfDayMillis(startDate), targetDate = GoalDay.startOfDayMillis(targetDate),
            status = GoalStatus.Active, subtype = subtype, trackingSource = trackingSource, isPinned = pinToHome,
            movementId = movementId
        )
        var s = GoalConfig()
        val sortedDays = weekdays.sorted()
        if (reminderEnabled) {
            s = s.copy(reminder = GoalReminder(true, reminderTime.hour, reminderTime.minute, if (flexibleSchedule) null else sortedDays))
        }
        if (!flexibleSchedule) s = s.copy(weekdays = sortedDays)
        if (usePreferredTime) s = s.copy(preferredHour = preferredTime.hour, preferredMinute = preferredTime.minute)
        val rec = recommendation
        if (rec.weeks != null && !useCustomDate && selectedWeeks == rec.weeks) {
            s = s.copy(recommendationBasis = rec.basis, recommendationIsPersonalized = rec.personalized)
        }
        val unitLabel = GoalFormat.label(weightUnit)

        when (subtype) {
            GoalSubtype.LoseWeight -> {
                s = s.copy(unit = weightUnit.label)
                goal = goal.copy(metricKey = "bodyWeight", baselineValue = baselineText.toDoubleOrNull(),
                    baselineDate = baselineDate ?: goal.startDate, targetValue = targetText.toDoubleOrNull(), targetUnit = unitLabel)
            }
            GoalSubtype.ImproveLift, GoalSubtype.PersonalRecord, GoalSubtype.RepsAtWeight, GoalSubtype.TrainingVolume -> {
                s = s.copy(unit = weightUnit.label, movementName = movementName)
                goal = goal.copy(metricKey = strengthMetric.rawValue, baselineValue = baselineText.toDoubleOrNull(),
                    baselineDate = baselineDate ?: goal.startDate, targetValue = targetText.toDoubleOrNull())
                val name = movementName
                when (subtype) {
                    GoalSubtype.RepsAtWeight -> {
                        s = s.copy(load = loadText.toDoubleOrNull())
                        goal = goal.copy(targetUnit = "reps")
                        if (title == subtype.title && name != null) goal = goal.copy(title = "$name: $targetText reps")
                    }
                    GoalSubtype.TrainingVolume -> {
                        s = s.copy(volumeScopeIsExercise = volumeScopeIsExercise)
                        goal = goal.copy(frequency = if (volumePerWorkout) GoalFrequency.PerWorkout else GoalFrequency.Weekly, targetUnit = unitLabel)
                        if (title == subtype.title) goal = goal.copy(title = if (volumePerWorkout) "Volume per Workout" else "Weekly Training Volume")
                    }
                    else -> {
                        goal = goal.copy(targetUnit = unitLabel)
                        if (title == subtype.title && name != null) {
                            goal = goal.copy(title = if (subtype == GoalSubtype.PersonalRecord) "$name PR" else "Improve My $name")
                        }
                    }
                }
            }
            GoalSubtype.SleepDuration, GoalSubtype.SleepConsistency -> {
                val duration = subtype == GoalSubtype.SleepDuration
                goal = goal.copy(metricKey = "sleepMinutes", targetValue = sleepTargetMinutes, targetUnit = "min",
                    frequency = if (duration) GoalFrequency.Daily else GoalFrequency.Weekly, frequencyTarget = if (duration) 7 else nightsPerWeek)
            }
            GoalSubtype.RecoveryMetric -> {
                s = s.copy(recoveryMetric = recoveryMetric, unit = recoveryMetric.unit)
                goal = goal.copy(metricKey = recoveryMetric.rawValue, baselineValue = recoveryBaseline, baselineDate = goal.startDate,
                    targetValue = targetText.toDoubleOrNull(), targetUnit = recoveryMetric.unit)
                if (title == subtype.title) goal = goal.copy(title = if (recoveryMetric == RecoveryMetric.RESTING_HEART_RATE) "Resting Heart Rate" else "HRV Target")
            }
            GoalSubtype.CalorieTarget -> {
                goal = goal.copy(metricKey = "calories", targetUnit = "cal")
                goal = if (calorieUseRange) {
                    s = s.copy(calorieMin = calorieMinText.toDoubleOrNull(), calorieMax = calorieMaxText.toDoubleOrNull())
                    goal.copy(targetValue = ((s.calorieMin ?: 0.0) + (s.calorieMax ?: 0.0)) / 2)
                } else goal.copy(targetValue = targetText.toDoubleOrNull())
                goal = goal.copy(frequency = if (isDaily) GoalFrequency.Daily else GoalFrequency.Weekly, frequencyTarget = weeklyTarget)
            }
            GoalSubtype.MacroTarget -> {
                val macros = MacroKey.entries.filter { it in selectedMacros }.map { GoalMacroTarget(it, macroText[it]?.toDoubleOrNull() ?: 0.0) }
                s = s.copy(macros = macros)
                goal = goal.copy(metricKey = "macros", targetValue = macros.firstOrNull()?.grams, targetUnit = "g",
                    frequency = if (isDaily) GoalFrequency.Daily else GoalFrequency.Weekly, frequencyTarget = weeklyTarget)
                val first = macros.firstOrNull()
                if (title == subtype.title && macros.size == 1 && first != null) {
                    goal = goal.copy(title = "${GoalFormat.number(first.grams, 0)} g ${first.key.displayName}")
                }
            }
            GoalSubtype.Hydration -> {
                s = s.copy(unit = waterUnit.rawValue)
                goal = goal.copy(metricKey = "water", targetValue = targetText.toDoubleOrNull(), targetUnit = waterUnit.rawValue,
                    frequency = if (isDaily) GoalFrequency.Daily else GoalFrequency.Weekly, frequencyTarget = weeklyTarget)
            }
            GoalSubtype.HealthyHabit -> {
                val habit = NutritionHabit.habit(nutritionHabitId)
                s = s.copy(habitKey = nutritionHabitId, completionRule = habit?.rule, unit = habit?.unit)
                goal = goal.copy(targetValue = targetText.toDoubleOrNull(), targetUnit = habit?.unit,
                    frequency = if (isDaily) GoalFrequency.Daily else GoalFrequency.Weekly, frequencyTarget = weeklyTarget,
                    trackingSource = habit?.tracking ?: GoalTrackingSource.Manual)
            }
            GoalSubtype.WorkoutConsistency, GoalSubtype.RecoveryRoutine, GoalSubtype.MovementRoutine, GoalSubtype.WellnessRoutine,
            GoalSubtype.DailyHabit -> {
                val template = GoalTemplate.template(templateId)
                s = s.copy(templateId = templateId, completionRule = template?.completionRule ?: "Check in each time you complete it.",
                    sessionMinutes = sessionMinutesText.toIntOrNull(), steps = steps.toList().ifEmpty { null })
                if (subtype == GoalSubtype.WorkoutConsistency && useProgram) {
                    env.activeProgram()?.let { (id, name) ->
                        s = s.copy(programId = id, programName = name)
                        goal = goal.copy(trackingSource = GoalTrackingSource.Program)
                    }
                }
                goal = goal.copy(frequency = if (isDaily) GoalFrequency.Daily else GoalFrequency.Weekly, frequencyTarget = weeklyTarget,
                    targetValue = weeklyTarget.toDouble(), targetUnit = if (subtype == GoalSubtype.WorkoutConsistency) "workouts/wk" else "per week")
            }
            GoalSubtype.CustomRoutine -> {
                val list = activities.toList()
                s = s.copy(activities = list, templateId = templateId)
                val total = list.sumOf { it.perWeek }
                goal = goal.copy(frequency = GoalFrequency.Weekly, frequencyTarget = total, targetValue = total.toDouble(),
                    targetUnit = "activities/wk",
                    trackingSource = if (list.any { it.kind == GoalActivity.Kind.WORKOUT }) GoalTrackingSource.WorkoutLogs else GoalTrackingSource.Manual)
            }
            GoalSubtype.CustomGoal -> {
                val unit = customUnit.ifEmpty { customMetric.defaultUnit }
                s = s.copy(customMetric = customMetric, unit = unit, actionName = actionName.ifEmpty { null })
                goal = goal.copy(metricKey = customMetric.rawValue,
                    trackingSource = if (customMetric == CustomGoalMetric.STEPS) GoalTrackingSource.AppleHealth else GoalTrackingSource.Manual)
                goal = when (customMetric) {
                    CustomGoalMetric.MANUAL -> {
                        val ft = when (customFrequency) { GoalFrequency.Weekly -> perWeek; GoalFrequency.Daily -> 7; else -> null }
                        goal.copy(frequency = customFrequency, frequencyTarget = ft, targetValue = ft?.toDouble() ?: 1.0)
                    }
                    CustomGoalMetric.FREQUENCY -> goal.copy(frequency = GoalFrequency.Weekly, frequencyTarget = perWeek,
                        targetValue = perWeek.toDouble(), targetUnit = "days/wk")
                    CustomGoalMetric.STEPS -> goal.copy(frequency = GoalFrequency.Daily, frequencyTarget = 7,
                        targetValue = targetText.toDoubleOrNull(), targetUnit = "steps")
                    else -> goal.copy(frequency = customFrequency, frequencyTarget = if (customFrequency == GoalFrequency.Daily) 7 else null,
                        targetValue = targetText.toDoubleOrNull(), targetUnit = unit)
                }
            }
        }
        return goal.withSettings(s)
    }

    // MARK: Save

    /**
     * Creates the goal once. Eat Better calorie/macro/hydration goals write
     * through to the Nutrition / water targets first, so the goal and the
     * Nutrition screens share one target.
     */
    suspend fun create() {
        if (createdGoal != null || isSaving) return
        isSaving = true
        errorMessage = null
        try {
            val goal = buildGoal()
            try {
                writeThroughTargets(goal)
            } catch (e: OfflineException) {
                errorMessage = "Setting your nutrition target needs a connection. Try again when you're online."
                return
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "Couldn't update your nutrition target. Please try again."
                return
            }
            env.createGoal(goal)
            createdGoal = goal
            stepIndex = flowSteps.indexOf(Step.CONFIRMATION).takeIf { it >= 0 } ?: stepIndex
        } finally {
            isSaving = false
        }
    }

    private class OfflineException : Exception()

    private suspend fun writeThroughTargets(goal: Goal) {
        when (goal.subtype) {
            GoalSubtype.CalorieTarget, GoalSubtype.MacroTarget -> {
                val live = env.nutritionGoal()
                val current = env.nutritionTargets()
                val baseCalories = current?.dailyCalorieTarget ?: live.calories.toDouble()
                val baseProtein = current?.proteinGrams ?: live.protein.toDouble()
                val baseCarbs = current?.carbGrams ?: live.carbs.toDouble()
                val baseFat = current?.fatGrams ?: live.fat.toDouble()
                var calories = baseCalories
                var protein = baseProtein
                var carbs = baseCarbs
                var fat = baseFat
                val settings = goal.settings
                val target = goal.targetValue
                if (goal.subtype == GoalSubtype.CalorieTarget && settings.calorieMin == null && target != null) calories = target
                for (macro in settings.macros ?: emptyList()) {
                    when (macro.key) {
                        MacroKey.PROTEIN -> protein = macro.grams
                        MacroKey.CARBS -> carbs = macro.grams
                        MacroKey.FAT -> fat = macro.grams
                    }
                }
                val unchanged = abs(calories - baseCalories) < 1 && abs(protein - baseProtein) < 1 &&
                    abs(carbs - baseCarbs) < 1 && abs(fat - baseFat) < 1
                if (unchanged) return
                if (!env.isOnline) throw OfflineException()
                env.overrideNutritionTargets(calories.roundToInt(), protein, fat, carbs)
            }
            GoalSubtype.Hydration -> {
                val target = goal.targetValue ?: return
                val hydration = env.hydrationGoal()
                val current = hydration.unit.convert(hydration.dailyGoal, waterUnit)
                if (abs(current - target) < 0.5) return
                if (!env.isOnline) throw OfflineException()
                env.saveHydrationGoal(hydration.copy(dailyGoal = target, unit = waterUnit))
            }
            else -> Unit
        }
    }

    // MARK: Review rows

    val reviewRows: List<Pair<String, String>>
        get() {
            val goal = buildGoal()
            val rows = mutableListOf<Pair<String, String>>()
            val subtype = subtype ?: return rows
            rows += "Category" to subtype.category.displayName
            if (subtype.category != GoalType.BodyComposition) rows += "Goal type" to subtype.title
            rows += "Name" to goal.title
            val s = goal.settings
            val unitLabel = GoalFormat.label(weightUnit)
            when (subtype) {
                GoalSubtype.LoseWeight -> {
                    rows += "Starting weight" to "$baselineText $unitLabel"
                    rows += "Target weight" to "$targetText $unitLabel"
                    val b = baselineText.toDoubleOrNull(); val t = targetText.toDoubleOrNull()
                    if (b != null && t != null) rows += "Required change" to GoalFormat.weight(b - t, weightUnit)
                }
                GoalSubtype.ImproveLift, GoalSubtype.PersonalRecord -> {
                    rows += "Exercise" to (movementName ?: "—")
                    rows += "Metric" to (strengthMetric.displayName + if (strengthMetric.isEstimated) " (estimated)" else "")
                    rows += "Baseline" to "$baselineText $unitLabel"
                    rows += "Target" to "$targetText $unitLabel"
                    val b = baselineText.toDoubleOrNull(); val t = targetText.toDoubleOrNull()
                    if (b != null && t != null) rows += "Required improvement" to GoalFormat.weight(t - b, weightUnit)
                }
                GoalSubtype.RepsAtWeight -> {
                    rows += "Exercise" to (movementName ?: "—")
                    rows += "Load" to "$loadText $unitLabel"
                    rows += "Baseline" to "$baselineText reps"
                    rows += "Target" to "$targetText reps"
                }
                GoalSubtype.TrainingVolume -> {
                    rows += "Scope" to (if (volumeScopeIsExercise) (movementName ?: "—") else "All working sets")
                    rows += "Target" to "$targetText $unitLabel ${if (volumePerWorkout) "per workout" else "per week"}"
                    if (baselineText.isNotEmpty()) rows += "Recent average" to "$baselineText $unitLabel"
                }
                GoalSubtype.SleepDuration -> rows += "Target" to "${GoalFormat.sleep(sleepTargetMinutes)} per night"
                GoalSubtype.SleepConsistency -> rows += "Target" to "${GoalFormat.sleep(sleepTargetMinutes)}+ on $nightsPerWeek nights per week"
                GoalSubtype.RecoveryMetric -> {
                    rows += "Metric" to recoveryMetric.displayName
                    recoveryBaseline?.let { rows += "Recent average" to "${GoalFormat.number(it)} ${recoveryMetric.unit}" }
                    rows += "Target" to "$targetText ${recoveryMetric.unit}"
                }
                GoalSubtype.CalorieTarget -> rows += "Target" to (if (calorieUseRange) "$calorieMinText–$calorieMaxText cal per day" else "$targetText cal per day")
                GoalSubtype.MacroTarget -> (s.macros ?: emptyList()).forEach { rows += it.key.displayName to "${GoalFormat.number(it.grams, 0)} g per day" }
                GoalSubtype.Hydration -> rows += "Target" to "$targetText ${waterUnit.rawValue} per day"
                GoalSubtype.HealthyHabit -> NutritionHabit.habit(nutritionHabitId)?.let { habit ->
                    rows += "Habit" to habit.name
                    if (habit.unit != null && habit.defaultTarget != null) rows += "Daily target" to "$targetText ${habit.unit}"
                    rows += "Completion rule" to habit.rule
                }
                GoalSubtype.WorkoutConsistency, GoalSubtype.RecoveryRoutine, GoalSubtype.MovementRoutine, GoalSubtype.WellnessRoutine,
                GoalSubtype.DailyHabit -> {
                    GoalTemplate.template(templateId)?.let { rows += "Template" to it.name }
                    sessionMinutesText.toIntOrNull()?.let { rows += "Session length" to "$it min" }
                    if (steps.isNotEmpty()) rows += "Steps" to steps.joinToString(", ")
                    s.completionRule?.let { rows += "Completion rule" to it }
                }
                GoalSubtype.CustomRoutine -> activities.forEach { rows += it.name to "${it.perWeek}× per week · ${it.kind.displayName}" }
                GoalSubtype.CustomGoal -> {
                    rows += "Metric" to customMetric.displayName
                    if (customMetric == CustomGoalMetric.MANUAL) rows += "Completion action" to actionName
                    val target = goal.targetValue
                    if (target != null && customMetric != CustomGoalMetric.MANUAL) rows += "Target" to "${GoalFormat.number(target)} ${goal.targetUnit ?: ""}".trimEnd()
                }
            }
            val frequency = goal.frequency
            if (frequency != null && subtype != GoalSubtype.LoseWeight) {
                when {
                    frequency == GoalFrequency.Daily -> rows += "Frequency" to "Every day"
                    frequency == GoalFrequency.Weekly && subtype != GoalSubtype.TrainingVolume -> rows += "Frequency" to "${goal.frequencyTarget ?: 1} per week"
                    frequency == GoalFrequency.Overall -> rows += "Frequency" to "Over the time frame"
                }
            }
            if (subtype.isFrequencyGoal || subtype.category == GoalType.Consistency) {
                rows += "Schedule" to (if (flexibleSchedule) "Flexible" else weekdays.sorted().joinToString(", ") { weekdayShortName(it) })
            }
            val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            rows += "Start date" to startDate.format(dateFormat)
            rows += "Target date" to targetDate.format(dateFormat)
            rows += "Duration" to durationText
            rows += "Tracking" to (goal.trackingSource?.displayName ?: "Manual check-in")
            rows += "Reminders" to (if (reminderEnabled) reminderTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) else "Off")
            return rows
        }

    companion object {
        /** Ungrouped number for a text field (GoalFormat.number groups thousands, which `toDouble` can't read back). */
        fun plain(value: Double, maxDecimals: Int = 1): String {
            val f = java.text.DecimalFormat("0" + if (maxDecimals > 0) "." + "#".repeat(maxDecimals) else "",
                java.text.DecimalFormatSymbols(Locale.US))
            return f.format(value)
        }

        /** Removes floating-point noise before a ceiling (20.000000000004 must stay 20 weeks). */
        private fun roundNoise(value: Double): Double = Math.round(value * 1e6) / 1e6

        /** 1 = Sunday … 7 = Saturday → "Sun". */
        fun weekdayShortName(day: Int): String =
            DayOfWeek.of(((day + 5) % 7) + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
}
