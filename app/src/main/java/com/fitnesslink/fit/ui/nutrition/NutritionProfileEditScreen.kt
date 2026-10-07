package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.api.NutritionActivityLevel
import com.fitnesslink.fit.model.api.NutritionGoalTimeline
import com.fitnesslink.fit.model.api.NutritionPrimaryGoal
import com.fitnesslink.fit.model.api.NutritionSex
import com.fitnesslink.fit.model.api.NutritionWorkoutFrequency
import com.fitnesslink.fit.model.api.activityLevelValue
import com.fitnesslink.fit.model.api.dateOfBirthValue
import com.fitnesslink.fit.model.api.primaryGoalValue
import com.fitnesslink.fit.model.api.sexValue
import com.fitnesslink.fit.model.api.timelineValue
import com.fitnesslink.fit.model.api.workoutFrequencyValue
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.dto.UpdateNutritionProfileRequest
import com.fitnesslink.fit.nutrition.NutritionGoalsStore
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.viewmodel.NutritionOnboardingViewModel
import com.fitnesslink.fit.viewmodel.NutritionProfileValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/**
 * Edit-anytime profile form (iOS `NutritionProfileEditView`, FTNSSLNK-191).
 * Every field prefilled; Save PUTs the profile (rowVersion-guarded) and the
 * server recalculates targets in the same response, which lands in
 * `NutritionGoalsStore`. Validation mirrors onboarding (shared helpers).
 */
@Composable
fun NutritionProfileEditScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val profile by NutritionGoalsStore.profile.collectAsState()

    var dateOfBirth by remember { mutableStateOf(Date()) }
    var sex by remember { mutableStateOf<NutritionSex?>(null) }
    var useMetricUnits by remember { mutableStateOf(WeightUnit.current == WeightUnit.KG) }
    var heightCmText by remember { mutableStateOf("") }
    var currentWeightKgText by remember { mutableStateOf("") }
    var goalWeightKgText by remember { mutableStateOf("") }
    var heightFeetText by remember { mutableStateOf("") }
    var heightInchesText by remember { mutableStateOf("") }
    var currentWeightLbsText by remember { mutableStateOf("") }
    var goalWeightLbsText by remember { mutableStateOf("") }
    var activityLevel by remember { mutableStateOf(NutritionActivityLevel.MODERATELY_ACTIVE) }
    var workoutFrequency by remember { mutableStateOf(NutritionWorkoutFrequency.THREE_TO_FOUR) }
    var primaryGoal by remember { mutableStateOf(NutritionPrimaryGoal.MAINTAIN) }
    var timeline by remember { mutableStateOf(NutritionGoalTimeline.NINETY_DAYS) }
    var timelineCustomDate by remember { mutableStateOf(Date()) }
    var dietaryPreferences by remember { mutableStateOf<Set<String>>(emptySet()) }
    var extraDietaryPreferences by remember { mutableStateOf<List<String>>(emptyList()) }
    var allergies by remember { mutableStateOf<Set<String>>(emptySet()) }
    var customAllergyText by remember { mutableStateOf("") }
    var prefilled by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { NutritionGoalsStore.refresh() }

    LaunchedEffect(profile) {
        val p = profile ?: return@LaunchedEffect
        if (prefilled) return@LaunchedEffect
        prefilled = true
        dateOfBirth = p.dateOfBirthValue ?: Date()
        sex = p.sexValue
        activityLevel = p.activityLevelValue ?: NutritionActivityLevel.MODERATELY_ACTIVE
        workoutFrequency = p.workoutFrequencyValue ?: NutritionWorkoutFrequency.THREE_TO_FOUR
        primaryGoal = p.primaryGoalValue ?: NutritionPrimaryGoal.MAINTAIN
        timeline = p.timelineValue ?: NutritionGoalTimeline.NINETY_DAYS
        timelineCustomDate = p.timelineCustomDate?.let { MealPlanDay.date(it) }
            ?: Calendar.getInstance().apply { add(Calendar.MONTH, 3) }.time
        heightCmText = NutritionProfileValidation.formatNumber(NutritionProfileValidation.round1(p.heightCm))
        currentWeightKgText = NutritionProfileValidation.formatNumber(NutritionProfileValidation.round1(p.currentWeightKg))
        goalWeightKgText = NutritionProfileValidation.formatNumber(NutritionProfileValidation.round1(p.goalWeightKg))
        val totalInches = p.heightCm / NutritionProfileValidation.CM_PER_INCH
        heightFeetText = (totalInches / 12).toInt().toString()
        heightInchesText = Math.round(totalInches % 12).toString()
        currentWeightLbsText = NutritionProfileValidation.formatNumber(Math.rint(NutritionProfileValidation.pounds(p.currentWeightKg)))
        goalWeightLbsText = NutritionProfileValidation.formatNumber(Math.rint(NutritionProfileValidation.pounds(p.goalWeightKg)))
        val knownPreferences = NutritionOnboardingViewModel.dietaryPreferenceOptions.toSet()
        dietaryPreferences = p.dietaryPreferences.filter { it in knownPreferences }.toSet()
        extraDietaryPreferences = p.dietaryPreferences.filter { it !in knownPreferences }
        val knownAllergies = NutritionOnboardingViewModel.allergyOptions.toSet()
        allergies = p.allergies.filter { it in knownAllergies }.toSet()
        customAllergyText = p.allergies.filter { it !in knownAllergies }.joinToString(", ")
    }

    val heightCm: Double? = if (useMetricUnits) heightCmText.toDoubleOrNull() else NutritionProfileValidation.centimeters(heightFeetText, heightInchesText)
    val currentWeightKg: Double? = if (useMetricUnits) currentWeightKgText.toDoubleOrNull() else currentWeightLbsText.toDoubleOrNull()?.let(NutritionProfileValidation::kilograms)
    val goalWeightKg: Double? = if (useMetricUnits) goalWeightKgText.toDoubleOrNull() else goalWeightLbsText.toDoubleOrNull()?.let(NutritionProfileValidation::kilograms)

    val validationError: String? = NutritionProfileValidation.statsError(dateOfBirth, sex, heightCm, currentWeightKg, goalWeightKg)
        ?: run {
            val cw = currentWeightKg; val gw = goalWeightKg
            if (cw != null && gw != null) NutritionProfileValidation.goalDirectionError(primaryGoal, cw, gw) else null
        }
        ?: NutritionProfileValidation.timelineError(timeline, timelineCustomDate)

    fun save() {
        val p = profile ?: return
        val h = heightCm ?: return
        val cw = currentWeightKg ?: return
        val gw = goalWeightKg ?: return
        val s = sex ?: return
        isSaving = true
        errorMessage = null
        scope.launch {
            try {
                val request = UpdateNutritionProfileRequest(
                    rowVersion = p.rowVersion,
                    dateOfBirth = MealPlanDay.string(dateOfBirth),
                    sex = s.rawValue,
                    heightCm = NutritionProfileValidation.round1(h),
                    currentWeightKg = NutritionProfileValidation.round1(cw),
                    goalWeightKg = NutritionProfileValidation.round1(gw),
                    activityLevel = activityLevel.rawValue,
                    workoutFrequency = workoutFrequency.rawValue,
                    primaryGoal = primaryGoal.rawValue,
                    timeline = timeline.rawValue,
                    timelineCustomDate = if (timeline == NutritionGoalTimeline.CUSTOM) MealPlanDay.string(timelineCustomDate) else null,
                    dietaryPreferences = dietaryPreferences.sorted() + extraDietaryPreferences,
                    allergies = allergies.sorted() + NutritionProfileValidation.splitCustom(customAllergyText)
                )
                NutritionGoalsStore.updateProfile(request)
                onBack()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError.ServerError) {
                if (e.isConflict) {
                    errorMessage = "Your profile changed on another device. Go back to Goals to refresh, then retry."
                    runCatching { NutritionGoalsStore.refresh() }
                } else {
                    errorMessage = NutritionProfileValidation.serverErrorText(e, "Couldn't save your changes. Please try again.")
                }
            } catch (e: ApiError.Offline) {
                errorMessage = "You're offline. Editing goals needs a connection to recalculate."
            } catch (e: Exception) {
                errorMessage = "Couldn't save your changes. Please try again."
            } finally {
                isSaving = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Edit Profile", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            Text(
                "Changing any field recalculates your targets. Days already logged keep the targets that were in effect then.",
                fontSize = 13.sp, color = TextSecondaryColor
            )

            if (profile == null) {
                Text("No nutrition profile yet — complete onboarding from the Goals screen first.", fontSize = 13.sp, color = TextSecondaryColor)
            }

            // Stats
            Text("Stats", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            NutritionFieldCard { NutritionDateRow("Date of Birth", dateOfBirth, maxDate = Date()) { dateOfBirth = it } }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NutritionSex.entries.forEach { option ->
                    NutritionSelectablePill(option.displayName, selected = sex == option, modifier = Modifier.weight(1f)) { sex = option }
                }
            }
            NutritionUnitsRow(useMetric = useMetricUnits) { useMetricUnits = it }
            NutritionBodyStatsCard(
                useMetric = useMetricUnits,
                heightCm = heightCmText, onHeightCm = { heightCmText = it },
                weightKg = currentWeightKgText, onWeightKg = { currentWeightKgText = it },
                goalKg = goalWeightKgText, onGoalKg = { goalWeightKgText = it },
                feet = heightFeetText, onFeet = { heightFeetText = it },
                inches = heightInchesText, onInches = { heightInchesText = it },
                weightLbs = currentWeightLbsText, onWeightLbs = { currentWeightLbsText = it },
                goalLbs = goalWeightLbsText, onGoalLbs = { goalWeightLbsText = it }
            )

            // Plan
            Text("Plan", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            NutritionFieldCard {
                PickerRow("Primary Goal", NutritionPrimaryGoal.entries, primaryGoal, { it.displayName }) { primaryGoal = it }
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                PickerRow("Timeline", NutritionGoalTimeline.entries, timeline, { it.displayName }) { timeline = it }
                if (timeline == NutritionGoalTimeline.CUSTOM) {
                    SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                    NutritionDateRow("Target Date", timelineCustomDate, minDate = MealPlanDay.addDays(Date(), 1)) { timelineCustomDate = it }
                }
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                PickerRow("Activity Level", NutritionActivityLevel.entries, activityLevel, { it.displayName }) { activityLevel = it }
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                PickerRow("Workouts / Week", NutritionWorkoutFrequency.entries, workoutFrequency, { it.displayName }) { workoutFrequency = it }
            }

            // Dietary
            Text("Dietary Preferences", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            NutritionChipGrid(NutritionOnboardingViewModel.dietaryPreferenceOptions, dietaryPreferences) { option ->
                dietaryPreferences = if (option in dietaryPreferences) dietaryPreferences - option else dietaryPreferences + option
            }
            Text("Allergies", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            NutritionChipGrid(NutritionOnboardingViewModel.allergyOptions, allergies) { option ->
                allergies = if (option in allergies) allergies - option else allergies + option
            }
            NutritionFieldCard { NutritionTextRow("Other", customAllergyText, placeholder = "Comma-separated") { customAllergyText = it } }

            (validationError ?: errorMessage)?.let { NutritionValidationBanner(it) }

            NutritionPrimaryButton(
                text = "Save & Recalculate",
                enabled = validationError == null && profile != null && !isSaving,
                loading = isSaving
            ) { save() }
        }
    }
}

@Composable
private fun <T> PickerRow(label: String, options: List<T>, selected: T, labelOf: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = TextSecondaryColor)
        Spacer(modifier = Modifier.weight(1f))
        Text(labelOf(selected), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
        Icon(Icons.Default.ExpandMore, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(labelOf(option)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}
