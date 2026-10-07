package com.fitnesslink.fit.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.api.NutritionActivityLevel
import com.fitnesslink.fit.model.api.NutritionGoalTimeline
import com.fitnesslink.fit.model.api.NutritionPrimaryGoal
import com.fitnesslink.fit.model.api.NutritionSex
import com.fitnesslink.fit.model.api.NutritionWorkoutFrequency
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.dto.CreateNutritionProfileRequest
import com.fitnesslink.fit.network.dto.NutritionTargets
import com.fitnesslink.fit.nutrition.NutritionGoalsStore
import kotlinx.coroutines.CancellationException
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Drives the seven-step nutrition onboarding wizard (port of iOS
 * `NutritionOnboardingViewModel`, FTNSSLNK-190): stats → activity →
 * frequency → goal → timeline → dietary → summary. The server does the math
 * (`POST /profile/calculate` previews; `POST /profile` persists).
 *
 * All entries live here (not in per-step state) so stepping back and
 * forward preserves everything. Validation lives in
 * [NutritionProfileValidation] — static and pure — so the nonsense-input
 * rules are unit-testable.
 */
class NutritionOnboardingViewModel : ViewModel() {

    enum class Step(val title: String) {
        STATS("About You"),
        ACTIVITY("Activity Level"),
        FREQUENCY("Workout Frequency"),
        GOAL("Primary Goal"),
        TIMELINE("Timeline"),
        DIETARY("Food Preferences"),
        SUMMARY("Your Plan");

        val next: Step? get() = entries.getOrNull(ordinal + 1)
        val previous: Step? get() = entries.getOrNull(ordinal - 1)
    }

    // MARK: - Wizard position

    var step by mutableStateOf(Step.STATS)
        private set
    val stepIndex: Int get() = step.ordinal
    val stepCount: Int get() = Step.entries.size
    val progress: Float get() = (stepIndex + 1).toFloat() / stepCount
    val isFirstStep: Boolean get() = step == Step.STATS

    // MARK: - Step 1: stats

    /** Display-only greeting; the account owns the name. */
    var name by mutableStateOf(AuthManager.currentUser.value?.firstName ?: "")
    var dateOfBirth by mutableStateOf(Calendar.getInstance().apply { add(Calendar.YEAR, -30) }.time)
    var sex by mutableStateOf<NutritionSex?>(null)
    var useMetricUnits by mutableStateOf(WeightUnit.current == WeightUnit.KG)
    var heightFeetText by mutableStateOf("")
    var heightInchesText by mutableStateOf("")
    var currentWeightLbsText by mutableStateOf("")
    var goalWeightLbsText by mutableStateOf("")
    var heightCmText by mutableStateOf("")
    var currentWeightKgText by mutableStateOf("")
    var goalWeightKgText by mutableStateOf("")

    // MARK: - Steps 2–5

    var activityLevel by mutableStateOf<NutritionActivityLevel?>(null)
    var workoutFrequency by mutableStateOf<NutritionWorkoutFrequency?>(null)
    var primaryGoal by mutableStateOf<NutritionPrimaryGoal?>(null)
    var timeline by mutableStateOf<NutritionGoalTimeline?>(null)
    var timelineCustomDate by mutableStateOf(Calendar.getInstance().apply { add(Calendar.MONTH, 3) }.time)

    // MARK: - Step 6

    var dietaryPreferences by mutableStateOf<Set<String>>(emptySet())
    var allergies by mutableStateOf<Set<String>>(emptySet())
    var customAllergyText by mutableStateOf("")

    // MARK: - Summary / network state

    var previewTargets by mutableStateOf<NutritionTargets?>(null)
        private set
    var isLoadingPreview by mutableStateOf(false)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var showValidation by mutableStateOf(false)
        private set

    // MARK: - Derived metric values

    val heightCm: Double?
        get() = if (useMetricUnits) heightCmText.toDoubleOrNull()
        else NutritionProfileValidation.centimeters(heightFeetText, heightInchesText)

    val currentWeightKg: Double?
        get() = if (useMetricUnits) currentWeightKgText.toDoubleOrNull()
        else currentWeightLbsText.toDoubleOrNull()?.let(NutritionProfileValidation::kilograms)

    val goalWeightKg: Double?
        get() = if (useMetricUnits) goalWeightKgText.toDoubleOrNull()
        else goalWeightLbsText.toDoubleOrNull()?.let(NutritionProfileValidation::kilograms)

    /** The blocking error for the CURRENT step, or null when it can advance. */
    val currentStepError: String?
        get() = when (step) {
            Step.STATS -> NutritionProfileValidation.statsError(dateOfBirth, sex, heightCm, currentWeightKg, goalWeightKg)
            Step.ACTIVITY -> if (activityLevel == null) "Pick the option closest to your typical week." else null
            Step.FREQUENCY -> if (workoutFrequency == null) "How many days a week do you train?" else null
            Step.GOAL -> {
                val goal = primaryGoal
                val cw = currentWeightKg
                val gw = goalWeightKg
                when {
                    goal == null -> "Pick your primary goal."
                    cw == null || gw == null -> null
                    else -> NutritionProfileValidation.goalDirectionError(goal, cw, gw)
                }
            }
            Step.TIMELINE -> NutritionProfileValidation.timelineError(timeline, timelineCustomDate)
            Step.DIETARY, Step.SUMMARY -> null
        }

    val canAdvance: Boolean get() = currentStepError == null

    val resolvedAllergies: List<String>
        get() = allergies.sorted() + NutritionProfileValidation.splitCustom(customAllergyText)

    // MARK: - Navigation

    /** Advance; entering the summary kicks off the server preview. */
    suspend fun advance() {
        if (!canAdvance) { showValidation = true; return }
        showValidation = false
        val next = step.next ?: return
        step = next
        if (step == Step.SUMMARY) loadPreview()
    }

    fun goBack() {
        showValidation = false
        errorMessage = null
        step = step.previous ?: return
    }

    // MARK: - Requests

    fun buildCreateRequest(): CreateNutritionProfileRequest? {
        val sex = sex ?: return null
        val activity = activityLevel ?: return null
        val frequency = workoutFrequency ?: return null
        val goal = primaryGoal ?: return null
        val timeline = timeline ?: return null
        val h = heightCm ?: return null
        val cw = currentWeightKg ?: return null
        val gw = goalWeightKg ?: return null
        return CreateNutritionProfileRequest(
            dateOfBirth = MealPlanDay.string(dateOfBirth),
            sex = sex.rawValue,
            heightCm = NutritionProfileValidation.round1(h),
            currentWeightKg = NutritionProfileValidation.round1(cw),
            goalWeightKg = NutritionProfileValidation.round1(gw),
            activityLevel = activity.rawValue,
            workoutFrequency = frequency.rawValue,
            primaryGoal = goal.rawValue,
            timeline = timeline.rawValue,
            timelineCustomDate = if (timeline == NutritionGoalTimeline.CUSTOM) MealPlanDay.string(timelineCustomDate) else null,
            dietaryPreferences = dietaryPreferences.takeIf { it.isNotEmpty() }?.sorted(),
            allergies = resolvedAllergies.takeIf { it.isNotEmpty() }
        )
    }

    /** `POST /profile/calculate` — computes WITHOUT persisting. */
    suspend fun loadPreview() {
        val request = buildCreateRequest() ?: return
        isLoadingPreview = true
        errorMessage = null
        try {
            previewTargets = ApiClient.nutritionApi.calculateNutritionTargets(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("NutritionOnboarding", "preview failed", e)
            previewTargets = null
            errorMessage = "Couldn't calculate your plan. Check your connection and try again."
        } finally {
            isLoadingPreview = false
        }
    }

    /** `POST /profile`; the response lands in `NutritionGoalsStore`. True on success. */
    suspend fun complete(): Boolean {
        val request = buildCreateRequest() ?: return false
        isSaving = true
        errorMessage = null
        try {
            NutritionGoalsStore.createProfile(request)
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError.ServerError) {
            if (e.isConflict) {
                runCatching { NutritionGoalsStore.refresh() }
                errorMessage = "You already have a nutrition profile — edit it from the Goals screen instead."
            } else {
                errorMessage = NutritionProfileValidation.serverErrorText(e)
            }
            return false
        } catch (e: ApiError.Offline) {
            errorMessage = "You're offline. Onboarding needs a connection to calculate your plan."
            return false
        } catch (e: Exception) {
            Log.w("NutritionOnboarding", "complete failed", e)
            errorMessage = "Couldn't save your profile. Please try again."
            return false
        } finally {
            isSaving = false
        }
    }

    companion object {
        val dietaryPreferenceOptions = listOf(
            "Vegetarian", "Vegan", "Pescatarian", "Keto", "Paleo",
            "Mediterranean", "Low Carb", "Low Fat", "Halal", "Kosher"
        )
        val allergyOptions = listOf(
            "Dairy", "Eggs", "Peanuts", "Tree Nuts", "Soy",
            "Wheat", "Gluten", "Fish", "Shellfish", "Sesame"
        )
    }
}

/**
 * Pure validation + unit helpers shared by onboarding and the profile edit
 * form. Ranges mirror the backend's `ProfileValidation` exactly — the client
 * just fails faster.
 */
object NutritionProfileValidation {
    const val CM_PER_INCH = 2.54
    const val KG_PER_LB = 0.45359237

    fun centimeters(feetText: String, inchesText: String): Double? {
        val feet = feetText.trim().toDoubleOrNull() ?: return null
        val inches = inchesText.trim().toDoubleOrNull() ?: 0.0
        return (feet * 12 + inches) * CM_PER_INCH
    }

    fun kilograms(pounds: Double): Double = pounds * KG_PER_LB
    fun pounds(kilograms: Double): Double = kilograms / KG_PER_LB

    fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0

    fun ageInYears(dateOfBirth: Date, asOf: Date = Date()): Int {
        val dob = Calendar.getInstance().apply { time = dateOfBirth }
        val now = Calendar.getInstance().apply { time = asOf }
        var age = now.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
        if (now.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) age--
        return age
    }

    fun statsError(
        dateOfBirth: Date,
        sex: NutritionSex?,
        heightCm: Double?,
        currentWeightKg: Double?,
        goalWeightKg: Double?,
        asOf: Date = Date()
    ): String? {
        if (sex == null) return "Select your sex — it changes the calorie equation."
        if (!dateOfBirth.before(asOf)) return "Date of birth must be in the past."
        val age = ageInYears(dateOfBirth, asOf)
        if (age < 13 || age > 120) return "Age must be between 13 and 120."
        val h = heightCm ?: return "Enter your height."
        if (h < 50 || h > 300) return "Height must be between 50 and 300 cm."
        val cw = currentWeightKg ?: return "Enter your current weight."
        if (cw < 20 || cw > 500) return "Current weight must be between 20 and 500 kg (44–1100 lbs)."
        val gw = goalWeightKg ?: return "Enter your goal weight."
        if (gw < 20 || gw > 500) return "Goal weight must be between 20 and 500 kg (44–1100 lbs)."
        return null
    }

    /** A goal weight that contradicts the goal type is blocked with an explanation. */
    fun goalDirectionError(goal: NutritionPrimaryGoal, currentWeightKg: Double, goalWeightKg: Double): String? = when (goal.weightDirection) {
        NutritionPrimaryGoal.WeightDirection.DOWN ->
            if (goalWeightKg >= currentWeightKg) "To lose weight, your goal weight needs to be below your current weight. Adjust your goal weight or pick a different goal." else null
        NutritionPrimaryGoal.WeightDirection.UP ->
            if (goalWeightKg <= currentWeightKg) {
                val verb = if (goal == NutritionPrimaryGoal.BUILD_MUSCLE) "build muscle" else "gain weight"
                "To $verb, your goal weight needs to be above your current weight. Adjust your goal weight or pick a different goal."
            } else null
        NutritionPrimaryGoal.WeightDirection.NEUTRAL -> null
    }

    fun timelineError(timeline: NutritionGoalTimeline?, customDate: Date, asOf: Date = Date()): String? {
        if (timeline == null) return "Pick a timeline for your goal."
        if (timeline == NutritionGoalTimeline.CUSTOM && !customDate.after(asOf)) return "Your custom target date must be in the future."
        return null
    }

    fun splitCustom(text: String): List<String> =
        text.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** Server errors arrive as `{"error":"..."}` — surface the message. */
    fun serverErrorText(error: ApiError.ServerError, fallback: String = "Couldn't save your profile. Please try again."): String {
        error.envelope?.displayMessage?.takeIf { it.isNotBlank() }?.let { return it }
        val body = error.body
        val match = Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(body)
        return match?.groupValues?.getOrNull(1) ?: fallback
    }

    /** Whole numbers drop the decimals, fractional ones keep one place. */
    fun formatNumber(value: Double): String =
        if (value == Math.rint(value)) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
}
