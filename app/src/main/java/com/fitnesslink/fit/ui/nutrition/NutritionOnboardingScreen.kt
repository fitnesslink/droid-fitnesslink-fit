package com.fitnesslink.fit.ui.nutrition

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.api.NutritionActivityLevel
import com.fitnesslink.fit.model.api.NutritionGoalTimeline
import com.fitnesslink.fit.model.api.NutritionPrimaryGoal
import com.fitnesslink.fit.model.api.NutritionSex
import com.fitnesslink.fit.model.api.NutritionWorkoutFrequency
import com.fitnesslink.fit.model.api.estimatedGoalDateValue
import com.fitnesslink.fit.model.api.whyText
import com.fitnesslink.fit.network.dto.NutritionTargets
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.DisabledButton
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.InfoColor
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.ProgressBG
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.NutritionOnboardingViewModel
import com.fitnesslink.fit.viewmodel.NutritionOnboardingViewModel.Step
import com.fitnesslink.fit.viewmodel.NutritionProfileValidation
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Multi-step nutrition onboarding (iOS `NutritionOnboardingView`,
 * FTNSSLNK-190): stats → activity → frequency → goal → timeline → dietary →
 * summary. All entries live on the view model so Back preserves everything.
 * The summary's numbers AND their "why" explanations come from the server's
 * calculation preview; Save persists via `POST /profile` and pushes the
 * targets into `NutritionGoalsStore`.
 */
@Composable
fun NutritionOnboardingScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit = onBack,
    viewModel: NutritionOnboardingViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        // Header: back / step counter / progress capsule
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(White, CircleShape)
                        .clickable { if (viewModel.isFirstStep) onBack() else viewModel.goBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = FLPrimary)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("Step ${viewModel.stepIndex + 1} of ${viewModel.stepCount}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
            }
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(ProgressBG, RoundedCornerShape(3.dp))) {
                Box(modifier = Modifier.fillMaxWidth(viewModel.progress).height(6.dp).background(FLPrimary, RoundedCornerShape(3.dp)))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(viewModel.step.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)

            when (viewModel.step) {
                Step.STATS -> StatsStep(viewModel)
                Step.ACTIVITY -> ActivityStep(viewModel)
                Step.FREQUENCY -> FrequencyStep(viewModel)
                Step.GOAL -> GoalStep(viewModel)
                Step.TIMELINE -> TimelineStep(viewModel)
                Step.DIETARY -> DietaryStep(viewModel)
                Step.SUMMARY -> SummaryStep(viewModel)
            }

            if (viewModel.showValidation) viewModel.currentStepError?.let { NutritionValidationBanner(it) }
            viewModel.errorMessage?.let { NutritionValidationBanner(it) }
        }

        val footerEnabled = when {
            viewModel.isSaving -> false
            viewModel.step == Step.SUMMARY -> !viewModel.isLoadingPreview && viewModel.previewTargets != null
            else -> true
        }
        NutritionPrimaryButton(
            text = if (viewModel.step == Step.SUMMARY) "Save My Plan" else "Continue",
            enabled = footerEnabled,
            loading = viewModel.isSaving || (viewModel.step == Step.SUMMARY && viewModel.isLoadingPreview),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            scope.launch {
                if (viewModel.step == Step.SUMMARY) {
                    if (viewModel.complete()) onComplete()
                } else {
                    viewModel.advance()
                }
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun StatsStep(vm: NutritionOnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("A few basics so we can estimate how much energy your body uses.", fontSize = 14.sp, color = TextSecondaryColor)
        NutritionFieldCard {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Name", fontSize = 14.sp, color = TextSecondaryColor)
                Spacer(modifier = Modifier.weight(1f))
                BasicTextField(
                    value = vm.name, onValueChange = { vm.name = it }, singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, textAlign = TextAlign.End),
                    modifier = Modifier.width(160.dp)
                )
            }
            SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
            NutritionDateRow("Date of Birth", vm.dateOfBirth, maxDate = Date()) { vm.dateOfBirth = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NutritionSex.entries.forEach { option ->
                NutritionSelectablePill(option.displayName, selected = vm.sex == option, modifier = Modifier.weight(1f)) { vm.sex = option }
            }
        }
        NutritionUnitsRow(useMetric = vm.useMetricUnits) { vm.useMetricUnits = it }
        NutritionBodyStatsCard(
            useMetric = vm.useMetricUnits,
            heightCm = vm.heightCmText, onHeightCm = { vm.heightCmText = it },
            weightKg = vm.currentWeightKgText, onWeightKg = { vm.currentWeightKgText = it },
            goalKg = vm.goalWeightKgText, onGoalKg = { vm.goalWeightKgText = it },
            feet = vm.heightFeetText, onFeet = { vm.heightFeetText = it },
            inches = vm.heightInchesText, onInches = { vm.heightInchesText = it },
            weightLbs = vm.currentWeightLbsText, onWeightLbs = { vm.currentWeightLbsText = it },
            goalLbs = vm.goalWeightLbsText, onGoalLbs = { vm.goalWeightLbsText = it }
        )
    }
}

@Composable
private fun ActivityStep(vm: NutritionOnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("How active is your typical day, outside of workouts? This sets the multiplier on your resting burn.", fontSize = 14.sp, color = TextSecondaryColor)
        NutritionActivityLevel.entries.forEach { level ->
            NutritionOptionCard(title = level.displayName, detail = level.detail, selected = vm.activityLevel == level) { vm.activityLevel = level }
        }
    }
}

@Composable
private fun FrequencyStep(vm: NutritionOnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("How many days a week do you work out? This helps us align your nutrition with your training.", fontSize = 14.sp, color = TextSecondaryColor)
        NutritionWorkoutFrequency.entries.forEach { band ->
            NutritionOptionCard(title = "${band.displayName} per week", detail = null, selected = vm.workoutFrequency == band) { vm.workoutFrequency = band }
        }
    }
}

@Composable
private fun GoalStep(vm: NutritionOnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("What's your primary goal? This drives the direction of your calories and your macro split.", fontSize = 14.sp, color = TextSecondaryColor)
        NutritionPrimaryGoal.entries.forEach { goal ->
            NutritionOptionCard(title = goal.displayName, detail = goal.detail, icon = goal.icon(), selected = vm.primaryGoal == goal) { vm.primaryGoal = goal }
        }
    }
}

@Composable
private fun TimelineStep(vm: NutritionOnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("How quickly do you want to get there? A longer timeline means a gentler daily change — we cap the pace either way to keep it safe.", fontSize = 14.sp, color = TextSecondaryColor)
        NutritionGoalTimeline.entries.forEach { option ->
            NutritionOptionCard(title = option.displayName, detail = null, selected = vm.timeline == option) { vm.timeline = option }
        }
        if (vm.timeline == NutritionGoalTimeline.CUSTOM) {
            NutritionFieldCard {
                NutritionDateRow("Target Date", vm.timelineCustomDate, minDate = MealPlanDay.addDays(Date(), 1)) { vm.timelineCustomDate = it }
            }
        }
    }
}

@Composable
private fun DietaryStep(vm: NutritionOnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Optional — used to tailor meal suggestions. Skip if nothing applies.", fontSize = 14.sp, color = TextSecondaryColor)
        Text("Dietary Preferences", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        NutritionChipGrid(NutritionOnboardingViewModel.dietaryPreferenceOptions, vm.dietaryPreferences) { option ->
            vm.dietaryPreferences = if (option in vm.dietaryPreferences) vm.dietaryPreferences - option else vm.dietaryPreferences + option
        }
        Text("Allergies", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.padding(top = 4.dp))
        NutritionChipGrid(NutritionOnboardingViewModel.allergyOptions, vm.allergies) { option ->
            vm.allergies = if (option in vm.allergies) vm.allergies - option else vm.allergies + option
        }
        NutritionFieldCard {
            NutritionTextRow("Other", vm.customAllergyText, placeholder = "e.g. avocado, cilantro") { vm.customAllergyText = it }
        }
    }
}

@Composable
private fun SummaryStep(vm: NutritionOnboardingViewModel) {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val targets = vm.previewTargets
        when {
            vm.isLoadingPreview -> Row(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = FLPrimary, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Calculating your plan…", fontSize = 14.sp, color = TextSecondaryColor)
            }
            targets != null -> {
                val greeting = vm.name.trim()
                Text(
                    if (greeting.isEmpty()) "Here's your personalized plan." else "$greeting, here's your personalized plan.",
                    fontSize = 14.sp, color = TextSecondaryColor
                )
                NutritionTargetsSummaryView(targets = targets, useMetricUnits = vm.useMetricUnits)
                Text(
                    "Save to start tracking against these targets. You can edit your profile or override any number later from Goals.",
                    fontSize = 13.sp, color = TextSecondaryColor
                )
            }
            else -> Row(
                modifier = Modifier.padding(vertical = 24.dp).clickable { scope.launch { vm.loadPreview() } },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
                Text("Retry calculation", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
            }
        }
    }
}

// MARK: - Shared nutrition form components (also used by profile edit + goals hub)

/** Plain surface card wrapper for stacked field rows (iOS `NutritionFieldCard`). */
@Composable
fun NutritionFieldCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)), content = content)
}

@Composable
fun NutritionNumberRow(label: String, value: String, unit: String, onValueChange: (String) -> Unit, width: Int = 70) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = TextSecondaryColor)
        Spacer(modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, textAlign = TextAlign.End),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(width.dp),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text("0", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MediumGray, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                    inner()
                }
            )
            Text(unit, fontSize = 12.sp, color = TextSecondaryColor)
        }
    }
}

@Composable
fun NutritionTextRow(label: String, value: String, placeholder: String, onValueChange: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = TextSecondaryColor)
        Spacer(modifier = Modifier.width(12.dp))
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = TextPrimaryColor, textAlign = TextAlign.End),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = MediumGray, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                inner()
            }
        )
    }
}

/** Label + tappable date that opens a Material date picker dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionDateRow(label: String, date: Date, minDate: Date? = null, maxDate: Date? = null, onDateChange: (Date) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val format = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { showPicker = true }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = TextSecondaryColor)
        Spacer(modifier = Modifier.weight(1f))
        Text(format.format(date), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
    }
    if (showPicker) {
        val utcMillis = date.time + Calendar.getInstance().timeZone.getOffset(date.time)
        val state = rememberDatePickerState(initialSelectedDateMillis = utcMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        // The picker works in UTC midnights; map back onto a local-midnight Date.
                        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
                        var picked = Calendar.getInstance().apply {
                            set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.time
                        if (minDate != null && picked.before(MealPlanDay.dayStart(minDate))) picked = MealPlanDay.dayStart(minDate)
                        if (maxDate != null && picked.after(maxDate)) picked = MealPlanDay.dayStart(maxDate)
                        onDateChange(picked)
                    }
                    showPicker = false
                }) { Text("Done", color = FLPrimary, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel", color = TextSecondaryColor) } }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun NutritionSelectablePill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(if (selected) FLPrimary else White, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (selected) White else TextPrimaryColor)
    }
}

/** "lb / ft" vs "kg / cm" segmented toggle. */
@Composable
fun NutritionUnitsRow(useMetric: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Units", fontSize = 14.sp, color = TextSecondaryColor)
        Spacer(modifier = Modifier.weight(1f))
        Row(modifier = Modifier.width(170.dp).background(White, RoundedCornerShape(8.dp)).padding(2.dp)) {
            listOf(false to "lb / ft", true to "kg / cm").forEach { (metric, label) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (useMetric == metric) FLPrimary else Color.Transparent, RoundedCornerShape(6.dp))
                        .clickable { onChange(metric) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (useMetric == metric) White else TextPrimaryColor)
                }
            }
        }
    }
}

/** Height / current weight / goal weight in the chosen unit system. */
@Composable
fun NutritionBodyStatsCard(
    useMetric: Boolean,
    heightCm: String, onHeightCm: (String) -> Unit,
    weightKg: String, onWeightKg: (String) -> Unit,
    goalKg: String, onGoalKg: (String) -> Unit,
    feet: String, onFeet: (String) -> Unit,
    inches: String, onInches: (String) -> Unit,
    weightLbs: String, onWeightLbs: (String) -> Unit,
    goalLbs: String, onGoalLbs: (String) -> Unit
) {
    NutritionFieldCard {
        if (useMetric) {
            NutritionNumberRow("Height", heightCm, "cm", onHeightCm)
            SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
            NutritionNumberRow("Current Weight", weightKg, "kg", onWeightKg)
            SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
            NutritionNumberRow("Goal Weight", goalKg, "kg", onGoalKg)
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Height", fontSize = 14.sp, color = TextSecondaryColor)
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallNumberField(feet, "5", onFeet)
                    Text("ft", fontSize = 12.sp, color = TextSecondaryColor)
                    SmallNumberField(inches, "10", onInches)
                    Text("in", fontSize = 12.sp, color = TextSecondaryColor)
                }
            }
            SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
            NutritionNumberRow("Current Weight", weightLbs, "lbs", onWeightLbs)
            SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
            NutritionNumberRow("Goal Weight", goalLbs, "lbs", onGoalLbs)
        }
    }
}

@Composable
private fun SmallNumberField(value: String, placeholder: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, textAlign = TextAlign.End),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(40.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MediumGray, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            inner()
        }
    )
}

@Composable
fun NutritionOptionCard(title: String, detail: String?, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) FLPrimary else White, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = if (selected) White else FLPrimary, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (selected) White else TextPrimaryColor)
            if (detail != null) Text(detail, fontSize = 12.sp, color = if (selected) White.copy(alpha = 0.85f) else TextSecondaryColor)
        }
        Icon(
            if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null, tint = if (selected) White else MediumGray, modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NutritionChipGrid(options: List<String>, selection: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option in selection
            Box(
                modifier = Modifier
                    .background(if (isSelected) FLPrimary else White, RoundedCornerShape(9.dp))
                    .clickable { onToggle(option) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(option, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (isSelected) White else TextPrimaryColor)
            }
        }
    }
}

@Composable
fun NutritionValidationBanner(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(OrangeTheme.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(12.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = OrangeTheme, modifier = Modifier.size(16.dp))
        Text(message, fontSize = 13.sp, color = OrangeTheme, modifier = Modifier.weight(1f))
    }
}

@Composable
fun NutritionPrimaryButton(text: String, enabled: Boolean, loading: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (enabled) FLPrimary else DisabledButton, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) CircularProgressIndicator(color = White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        else Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White)
    }
}

fun NutritionPrimaryGoal.icon(): ImageVector = when (this) {
    NutritionPrimaryGoal.LOSE -> Icons.Default.TrendingDown
    NutritionPrimaryGoal.MAINTAIN -> Icons.Default.Balance
    NutritionPrimaryGoal.GAIN -> Icons.Default.TrendingUp
    NutritionPrimaryGoal.BUILD_MUSCLE -> Icons.Default.FitnessCenter
    NutritionPrimaryGoal.RECOMP -> Icons.Default.SwapVert
    NutritionPrimaryGoal.HEALTH -> Icons.Default.Favorite
}

// MARK: - Targets summary (shared by onboarding summary + Goals hub)

/**
 * The goal-summary card stack (iOS `NutritionTargetsSummaryView`):
 * maintenance, daily target, macros, weekly rate, estimated completion —
 * each with an expandable "why" rendered strictly from the API's
 * explanation keys + inputs (`WhyExplanationText`).
 */
@Composable
fun NutritionTargetsSummaryView(targets: NutritionTargets, useMetricUnits: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TargetMetricCard("Maintenance Calories", NutritionProfileValidation.formatNumber(Math.rint(targets.maintenanceCalories)), "cal/day", targets.whyText("MaintenanceCalories"), BlueTheme)
        TargetMetricCard("Daily Target", NutritionProfileValidation.formatNumber(Math.rint(targets.dailyCalorieTarget)), "cal/day", targets.whyText("DailyCalorieTarget"), FLPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MacroTile("Protein", targets.proteinGrams, targets.whyText("ProteinGrams"), BlueTheme, Modifier.weight(1f))
            MacroTile("Carbs", targets.carbGrams, targets.whyText("CarbGrams"), PurpleTheme, Modifier.weight(1f))
            MacroTile("Fat", targets.fatGrams, targets.whyText("FatGrams"), OrangeTheme, Modifier.weight(1f))
        }
        val weekly = targets.weeklyWeightChangeKg
        val rateText = when {
            weekly == 0.0 -> "Steady"
            useMetricUnits -> (if (weekly < 0) "−" else "+") + String.format(Locale.US, "%.2f", abs(weekly))
            else -> (if (weekly < 0) "−" else "+") + String.format(Locale.US, "%.1f", abs(weekly) / NutritionProfileValidation.KG_PER_LB)
        }
        val rateUnit = if (weekly == 0.0) null else if (useMetricUnits) "kg/week" else "lbs/week"
        TargetMetricCard("Weekly Progress Rate", rateText, rateUnit, targets.whyText("WeeklyWeightChangeKg"), PurpleTheme)
        val completion = targets.estimatedGoalDateValue?.let { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(it) } ?: "Not applicable"
        TargetMetricCard("Estimated Completion", completion, null, targets.whyText("EstimatedGoalDate"), OrangeTheme)
    }
}

@Composable
private fun TargetMetricCard(label: String, value: String, unit: String?, why: String?, accent: Color) {
    Column(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
            Spacer(modifier = Modifier.weight(1f))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent)
            if (unit != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(unit, fontSize = 12.sp, color = TextSecondaryColor)
            }
        }
        if (why != null) WhyDisclosure(text = why)
    }
}

@Composable
private fun MacroTile(name: String, grams: Double, why: String?, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(White, RoundedCornerShape(12.dp)).padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("${NutritionProfileValidation.formatNumber(Math.rint(grams))} g", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(name, fontSize = 12.sp, color = TextSecondaryColor)
        if (why != null) WhyDisclosure(text = why, compact = true)
    }
}

/** "Why this number?" expander — collapsed by default. */
@Composable
fun WhyDisclosure(text: String, compact: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Column(horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.HelpOutline, contentDescription = null, tint = InfoColor, modifier = Modifier.size(if (compact) 13.dp else 14.dp))
            if (!compact) Text(if (expanded) "Hide" else "Why this number?", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = InfoColor)
            else Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = InfoColor, modifier = Modifier.size(12.dp))
        }
        AnimatedVisibility(visible = expanded) {
            Text(text, fontSize = 12.sp, color = TextSecondaryColor, textAlign = if (compact) TextAlign.Center else TextAlign.Start)
        }
    }
}
