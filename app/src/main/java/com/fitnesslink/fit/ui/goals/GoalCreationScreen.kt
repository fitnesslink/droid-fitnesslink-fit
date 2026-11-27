package com.fitnesslink.fit.ui.goals

import com.fitnesslink.fit.ui.theme.FLTheme
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.goals.GoalFormat
import com.fitnesslink.fit.goals.GoalReminderScheduler
import com.fitnesslink.fit.goals.GoalStore
import com.fitnesslink.fit.model.CustomGoalMetric
import com.fitnesslink.fit.model.GoalActivity
import com.fitnesslink.fit.model.GoalFrequency
import com.fitnesslink.fit.model.GoalSubtype
import com.fitnesslink.fit.model.GoalTemplate
import com.fitnesslink.fit.model.GoalTrackingSource
import com.fitnesslink.fit.model.GoalType
import com.fitnesslink.fit.model.MacroKey
import com.fitnesslink.fit.model.NutritionHabit
import com.fitnesslink.fit.model.RecoveryMetric
import com.fitnesslink.fit.model.StrengthMetric
import com.fitnesslink.fit.model.WaterUnit
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.nutrition.NutritionGoalsStore
import com.fitnesslink.fit.ui.navigation.AppRoute
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.nutrition.NutritionSourcesLink
import com.fitnesslink.fit.ui.progress.CONNECTED_APPS_ROUTE
import com.fitnesslink.fit.health.HealthConnectService
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.workout.BrowserMode
import com.fitnesslink.fit.ui.workout.ExerciseBrowserSheet
import com.fitnesslink.fit.viewmodel.GoalSetupViewModel
import com.fitnesslink.fit.viewmodel.GoalSetupViewModel.Step
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Port of iOS `GoalSetupFlowView` (Goals PRD §1.4–1.8): category → type →
 * adaptive steps → time frame → tracking & reminders → review →
 * confirmation. Keeps the `goalCreation` route's composable name; the old
 * three-step direction/details/habits flow is gone (as on iOS).
 */
@Composable
fun GoalCreationScreen(
    onClose: () -> Unit,
    category: GoalType? = null,
    onOpenGoal: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { GoalReminderScheduler.attach(context); GoalStore.ensureRegistered() }
    var generation by remember { mutableIntStateOf(0) }
    val model = remember(generation) { GoalSetupViewModel(if (generation == 0) category else null) }
    val scope = rememberCoroutineScope()
    val step = model.currentStep
    // Returning from Connected Apps unlocks the Health Connect goals without restarting the flow.
    val healthConnected by HealthConnectService.connected.collectAsState()
    LaunchedEffect(healthConnected, model) { model.refreshHealthConnection() }

    BackHandler {
        if (model.canGoBack) model.back() else onClose()
    }

    Column(modifier = Modifier.fillMaxSize().background(FLTheme.colors.background).imePadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                if (model.canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.clickable { model.back() })
                }
            }
            Spacer(Modifier.weight(1f))
            if (step != Step.CONFIRMATION) {
                Text("Cancel", fontSize = 14.sp, color = goalSecondaryText(),
                    modifier = Modifier.clickable(onClick = onClose).padding(horizontal = 8.dp))
            }
        }
        if (step != Step.CONFIRMATION) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                repeat(model.progressCount) { index ->
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape)
                        .background(if (index <= model.stepIndex) FLPrimary else Color.Gray.copy(alpha = 0.2f)))
                }
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (step != Step.CONFIRMATION) {
                Text(stepTitle(model), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                stepSubtitle(step)?.let { Text(it, fontSize = 14.sp, color = goalSecondaryText()) }
            }
            when (step) {
                Step.CATEGORY -> CategoryStep(model)
                Step.TYPE -> TypeStep(model)
                Step.DEFINE -> DefineStep(model)
                Step.BASELINE -> BaselineStep(model)
                Step.TARGET -> TargetStep(model)
                Step.FREQUENCY -> FrequencyStep(model)
                Step.TIME_FRAME -> TimeFrameStep(model)
                Step.TRACKING -> TrackingStep(model)
                Step.REVIEW -> ReviewStep(model)
                Step.CONFIRMATION -> ConfirmationStep(
                    model,
                    onView = { id ->
                        onClose()
                        (onOpenGoal ?: { gid -> AppRouter.push(AppRoute.GoalDetail.createRoute(gid)) })(id)
                    },
                    onAnother = { generation += 1 }
                )
            }
        }
        when (step) {
            Step.CONFIRMATION -> Unit
            Step.REVIEW -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                model.errorMessage?.let { Text(it, fontSize = 13.sp, color = FLTheme.colors.danger, textAlign = TextAlign.Center) }
                GoalPrimaryButton(if (model.isSaving) "Creating…" else "Create Goal", enabled = !model.isSaving) {
                    scope.launch { model.create() }
                }
            }
            else -> GoalPrimaryButton("Continue", enabled = model.canContinue,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { model.next() }
        }
    }
}

private fun stepTitle(model: GoalSetupViewModel): String = when (model.currentStep) {
    Step.TYPE -> when (model.category) {
        GoalType.Performance -> "How do you want to get stronger?"
        GoalType.RecoveryWellness -> "How do you want to sleep & recover?"
        GoalType.Nutrition -> "How do you want to eat better?"
        GoalType.Consistency -> "What routine do you want to build?"
        else -> Step.TYPE.title
    }
    Step.DEFINE -> when (model.subtype) {
        GoalSubtype.ImproveLift, GoalSubtype.PersonalRecord, GoalSubtype.RepsAtWeight -> "Choose an exercise"
        GoalSubtype.TrainingVolume -> "What volume counts?"
        GoalSubtype.RecoveryMetric -> "Choose a metric"
        GoalSubtype.HealthyHabit -> "Choose a habit"
        GoalSubtype.CustomRoutine -> "Build your routine"
        GoalSubtype.CustomGoal -> "Name your goal"
        else -> "Choose a routine"
    }
    else -> model.currentStep.title
}

private fun stepSubtitle(step: Step): String? = when (step) {
    Step.CATEGORY -> "Choose a goal to get started. You can always add more later."
    Step.TIME_FRAME -> "Recommendations are estimates, not guarantees. You can change this later."
    else -> null
}

private val HEALTH_UNAVAILABLE = "This goal needs a compatible data source. Connect Health Connect (with data from a device such as a Wear OS watch) in Connected Apps, then come back."

/** iOS "Open Connected Apps": the Connect action wherever a goal needs Health Connect. */
@Composable
private fun OpenConnectedAppsButton() {
    GoalLinkButton("Open Connected Apps") { AppRouter.push(CONNECTED_APPS_ROUTE) }
}

// MARK: - Steps

@Composable
private fun CategoryStep(model: GoalSetupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GoalType.entries.forEach { category ->
            GoalOptionCard(goalCategoryIcon(category), category.displayName, category.subtitle, model.category == category) {
                model.select(category)
            }
        }
    }
}

@Composable
private fun TypeStep(model: GoalSetupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GoalSubtype.options(model.category ?: GoalType.Custom).forEach { subtype ->
            GoalOptionCard(goalSubtypeIcon(subtype), subtype.title, subtype.summary, model.subtype == subtype,
                example = subtype.example?.let { "e.g. $it" }) { model.select(subtype) }
        }
    }
}

@Composable
private fun WeightUnitPicker(model: GoalSetupViewModel) {
    GoalSegmented(listOf(WeightUnit.LBS to "lb", WeightUnit.KG to "kg"), model.weightUnit) { model.weightUnit = it }
}

@Composable
private fun DefineStep(model: GoalSetupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (model.subtype) {
            GoalSubtype.ImproveLift, GoalSubtype.PersonalRecord, GoalSubtype.RepsAtWeight -> {
                ExercisePicker(model)
                if (model.subtype != GoalSubtype.RepsAtWeight) {
                    GoalCard("Performance metric") {
                        GoalSegmented(listOf(StrengthMetric.LOAD to "Load lifted", StrengthMetric.ESTIMATED_ONE_REP_MAX to "Estimated 1RM"),
                            model.strengthMetric) { model.strengthMetric = it; model.baselineText = "" }
                        WeightUnitPicker(model)
                        GoalNote(if (model.strengthMetric == StrengthMetric.LOAD) "Your heaviest completed working set."
                        else "Estimated from sets of 1–12 reps (Epley formula). A multi-rep set is never treated as a tested max.")
                    }
                } else {
                    GoalCard("Load") {
                        GoalNumberField("Weight", model.loadText, { model.loadText = it }, GoalFormat.label(model.weightUnit))
                        WeightUnitPicker(model)
                        GoalNote("Only sets at this load count toward the goal.")
                    }
                }
            }
            GoalSubtype.TrainingVolume -> {
                GoalCard("Scope") {
                    GoalSegmented(listOf(false to "All training", true to "One exercise"), model.volumeScopeIsExercise) { model.volumeScopeIsExercise = it }
                    GoalSegmented(listOf(false to "Per week", true to "Per workout"), model.volumePerWorkout) { model.volumePerWorkout = it }
                    WeightUnitPicker(model)
                    GoalNote("Volume is weight × reps of completed working sets. Warm-up and cool-down sets don't count.")
                }
                if (model.volumeScopeIsExercise) ExercisePicker(model)
            }
            GoalSubtype.RecoveryMetric -> {
                GoalCard {
                    RecoveryMetric.entries.forEach { metric ->
                        GoalOptionCard(if (metric == RecoveryMetric.HRV) Icons.Filled.MonitorHeart else Icons.Filled.Favorite,
                            metric.displayName, "From Health Connect, in ${metric.unit}.", model.recoveryMetric == metric) { model.recoveryMetric = metric }
                    }
                }
                if (!model.healthConnected) {
                    GoalCard {
                        GoalNote(HEALTH_UNAVAILABLE, Icons.Filled.Warning, GoalWarning)
                        OpenConnectedAppsButton()
                    }
                } else {
                    GoalNote("Readings from different devices or methods may not be directly comparable. No single reading says whether you're recovered.")
                }
            }
            GoalSubtype.HealthyHabit -> NutritionHabit.all.forEach { habit ->
                GoalOptionCard(if (habit.tracking == GoalTrackingSource.NutritionLogs) goalActionIcon(com.fitnesslink.fit.goals.GoalAction.LogFood) else Icons.Filled.CheckCircle,
                    habit.name, habit.description, model.nutritionHabitId == habit.id, example = habit.rule,
                    badge = if (habit.tracking == GoalTrackingSource.NutritionLogs) "AUTO" else null) { model.applyNutritionHabit(habit) }
            }
            GoalSubtype.CustomRoutine -> CustomRoutineBuilder(model)
            GoalSubtype.CustomGoal -> CustomGoalDefinition(model)
            else -> TemplatePicker(model)
        }
    }
}

@Composable
private fun ExercisePicker(model: GoalSetupViewModel) {
    var showBrowser by remember { mutableStateOf(false) }
    GoalCard("Exercise") {
        val name = model.movementName
        if (name != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FLPrimary)
                Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                GoalLinkButton("Change") { model.movementName = null; model.baselineText = "" }
            }
        } else {
            val history = remember { model.historyExercises.take(8) }
            if (history.isNotEmpty()) {
                Text("From your workouts", fontSize = 13.sp, color = goalSecondaryText())
                history.forEach { exercise ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { model.movementName = exercise; model.movementId = null }.padding(vertical = 6.dp)) {
                        Text(exercise, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = goalSecondaryText(), modifier = Modifier.size(16.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable { showBrowser = true }.padding(vertical = 4.dp)) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
                Text("Browse exercise library", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
            }
        }
    }
    if (showBrowser) {
        ExerciseBrowserSheet(
            onSelect = { item ->
                model.movementName = item.name.trim()
                model.movementId = item.id.lowercase()
                showBrowser = false
            },
            onCreateNew = {},
            onDismiss = { showBrowser = false },
            pendingGroupType = null,
            pendingCount = 0,
            mode = BrowserMode.Single("Choose Exercise"),
            allowCreateNew = false
        )
    }
}

@Composable
private fun TemplatePicker(model: GoalSetupViewModel) {
    val program = remember { com.fitnesslink.fit.viewmodel.LocalGoalSetupEnvironment.activeProgram() }
    if (model.subtype == GoalSubtype.WorkoutConsistency && program != null) {
        GoalCard("Tracking") {
            GoalToggle("Count workouts from ${program.second}", model.useProgram, {
                model.useProgram = it
                model.trackingSource = if (it) GoalTrackingSource.Program else GoalTrackingSource.WorkoutLogs
            }, subtitle = "Only completed workouts from your current program count. You won't be enrolled in anything new.")
        }
    }
    GoalTemplate.templates(model.subtype ?: GoalSubtype.DailyHabit).forEach { template ->
        GoalOptionCard(goalSubtypeIcon(model.subtype ?: GoalSubtype.CustomGoal), template.name, template.description,
            model.templateId == template.id, example = templateDetail(template), badge = template.level?.uppercase()) { model.applyTemplate(template) }
    }
    GoalCard("Or name your own") {
        GoalTextField(model.title, { model.renameRoutine(it) }, "Routine name")
    }
    if (model.steps.isNotEmpty()) {
        GoalCard("Routine steps") {
            model.steps.toList().forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}. $step", fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Remove $step", tint = goalSecondaryText(),
                        modifier = Modifier.clickable { model.steps.removeAt(index) })
                }
            }
            GoalNote("The goal counts a day when you complete the whole routine.")
        }
    }
}

private fun templateDetail(template: GoalTemplate): String {
    val parts = mutableListOf(if (template.perWeek == 7) "Daily" else "${template.perWeek}× per week")
    template.minutes?.let { parts.add("$it min") }
    template.weeklyMinutes?.let { parts.add("~$it min/week") }
    return parts.joinToString(" · ")
}

@Composable
private fun CustomRoutineBuilder(model: GoalSetupViewModel) {
    var newName by remember { mutableStateOf("") }
    var newKind by remember { mutableStateOf(GoalActivity.Kind.WORKOUT) }
    var newPerWeek by remember { mutableIntStateOf(2) }
    GoalCard("Routine") { GoalTextField(model.title, { model.title = it }, "Routine name") }
    GoalCard("Start from a template") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            GoalTemplate.all.filter { it.subtype != GoalSubtype.DailyHabit && it.subtype != GoalSubtype.RecoveryRoutine }.take(8).forEach { template ->
                GoalChip(template.name) {
                    val kind = when (template.subtype) {
                        GoalSubtype.WorkoutConsistency -> GoalActivity.Kind.WORKOUT
                        GoalSubtype.MovementRoutine -> GoalActivity.Kind.MOVEMENT
                        else -> GoalActivity.Kind.WELLNESS
                    }
                    model.activities.add(GoalActivity(name = template.name, kind = kind, perWeek = template.perWeek, minutes = template.minutes))
                }
            }
        }
    }
    GoalCard("Activities") {
        if (model.activities.isEmpty()) Text("Add at least one activity.", fontSize = 13.sp, color = goalSecondaryText())
        model.activities.toList().forEachIndexed { index, activity ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(activity.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("${activity.kind.displayName} · ${activity.kind.tracking.displayName}", fontSize = 12.sp, color = goalSecondaryText())
                    }
                    Icon(Icons.Filled.Delete, contentDescription = "Remove ${activity.name}", tint = goalSecondaryText(),
                        modifier = Modifier.clickable { model.activities.removeAt(index) })
                }
                GoalStepper("${activity.perWeek}×/wk", activity.perWeek, 1..7, { v -> model.activities[index] = activity.copy(perWeek = v) })
            }
        }
        HorizontalDivider()
        GoalTextField(newName, { newName = it }, "Activity name")
        GoalSegmented(GoalActivity.Kind.entries.map { it to it.displayName }, newKind) { newKind = it }
        GoalStepper("$newPerWeek× per week", newPerWeek, 1..7, { newPerWeek = it })
        GoalLinkButton("+ Add Activity") {
            val name = newName.trim()
            model.activities.add(GoalActivity(name = name.ifEmpty { newKind.displayName }, kind = newKind, perWeek = newPerWeek))
            newName = ""
        }
        GoalNote("Workout activities count completed workouts; other activities count your check-ins.")
    }
}

@Composable
private fun CustomGoalDefinition(model: GoalSetupViewModel) {
    GoalCard("Goal name") { GoalTextField(model.title, { model.title = it }, "e.g. Walk more consistently") }
    Text("How will success be measured?", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    CustomGoalMetric.entries.forEach { metric ->
        val unavailable = metric == CustomGoalMetric.STEPS && !model.healthConnected
        val icon = when (metric) {
            CustomGoalMetric.COUNT -> Icons.Filled.Numbers
            CustomGoalMetric.DURATION -> Icons.Filled.Timer
            CustomGoalMetric.DISTANCE -> Icons.Filled.Map
            CustomGoalMetric.FREQUENCY -> Icons.Filled.CalendarMonth
            CustomGoalMetric.STEPS -> Icons.Filled.DirectionsWalk
            CustomGoalMetric.MANUAL -> Icons.Filled.CheckCircle
        }
        Box(Modifier.then(if (unavailable) Modifier.background(Color.Transparent) else Modifier)) {
            GoalOptionCard(icon, metric.displayName,
                if (unavailable) "Needs Health Connect — connect it in Connected Apps." else metric.explanation,
                model.customMetric == metric, enabled = !unavailable) {
                model.customMetric = metric
                model.customUnit = metric.defaultUnit
                model.trackingSource = if (metric == CustomGoalMetric.STEPS) GoalTrackingSource.AppleHealth else GoalTrackingSource.Manual
            }
        }
    }
    if (!model.healthConnected) OpenConnectedAppsButton()
    if (model.customMetric in setOf(CustomGoalMetric.MANUAL, CustomGoalMetric.FREQUENCY, CustomGoalMetric.COUNT)) {
        GoalCard("Completion action") {
            GoalTextField(model.actionName, { model.actionName = it }, "e.g. Complete a mobility session")
            GoalNote("Describe exactly what counts, so each check-in has a clear meaning.")
        }
    }
}

@Composable
private fun BaselineStep(model: GoalSetupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (model.subtype) {
            GoalSubtype.LoseWeight -> GoalCard("Current weight") {
                GoalNumberField("Current weight", model.baselineText, {
                    model.baselineText = it
                    if (model.baselineSourceText == null) model.baselineDate = System.currentTimeMillis()
                }, GoalFormat.label(model.weightUnit))
                WeightUnitPicker(model)
                val source = model.baselineSourceText
                if (source != null) GoalNote("$source. Confirm it or enter today's weight.", Icons.Filled.History)
                else GoalNote("No weight logged yet — enter your current weight.")
            }
            GoalSubtype.RepsAtWeight -> GoalCard("Current reps at ${model.loadText} ${GoalFormat.label(model.weightUnit)}") {
                GoalNumberField("Best reps", model.baselineText, { model.baselineText = it }, "reps", decimal = false)
                val source = model.baselineSourceText
                if (source != null) GoalNote(source, Icons.Filled.History)
                else GoalNote("No sets at this load in your history — enter your current best.")
            }
            else -> GoalCard(if (model.subtype == GoalSubtype.PersonalRecord) "Current best" else "Baseline") {
                GoalNumberField(if (model.strengthMetric.isEstimated) "Estimated 1RM" else "Load lifted", model.baselineText,
                    { model.baselineText = it }, GoalFormat.label(model.weightUnit))
                val source = model.baselineSourceText
                if (source != null) GoalNote(source, Icons.Filled.History)
                else GoalNote("No reliable value in your workout history — enter your current ${if (model.strengthMetric.isEstimated) "estimate" else "best"}.")
                if (model.strengthMetric.isEstimated) GoalNote("Estimated values are labelled as estimates throughout.")
            }
        }
    }
}

@Composable
private fun TargetStep(model: GoalSetupViewModel) {
    LaunchedEffect(model.subtype, model.recoveryMetric, model.healthConnected) {
        if (model.subtype == GoalSubtype.RecoveryMetric) {
            model.recoveryBaseline = withContext(Dispatchers.IO) { model.recoveryBaselineFromHealth() }
        }
        if (model.subtype == GoalSubtype.CalorieTarget || model.subtype == GoalSubtype.MacroTarget) {
            runCatching { NutritionGoalsStore.refresh() }
            model.refreshNutritionPrefill()
        }
    }
    val unitLabel = GoalFormat.label(model.weightUnit)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (model.subtype) {
            GoalSubtype.LoseWeight -> GoalCard("Target weight") {
                GoalNumberField("Target", model.targetText, { model.targetText = it }, unitLabel)
                val b = model.baselineText.toDoubleOrNull(); val t = model.targetText.toDoubleOrNull()
                if (b != null && t != null && t < b) {
                    Text("Required change: ${GoalFormat.weight(b - t, model.weightUnit)} decrease", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                }
            }
            GoalSubtype.ImproveLift, GoalSubtype.PersonalRecord -> GoalCard("Target") {
                GoalNumberField(if (model.strengthMetric.isEstimated) "Target estimated 1RM" else "Target load", model.targetText, { model.targetText = it }, unitLabel)
                val b = model.baselineText.toDoubleOrNull(); val t = model.targetText.toDoubleOrNull()
                if (b != null && t != null && t > b) {
                    Text("Required improvement: ${GoalFormat.weight(t - b, model.weightUnit)}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                }
            }
            GoalSubtype.RepsAtWeight -> GoalCard("Target") {
                GoalNumberField("Target reps", model.targetText, { model.targetText = it }, "reps", decimal = false)
                Text("At ${model.loadText} $unitLabel, starting from ${model.baselineText} reps", fontSize = 13.sp, color = goalSecondaryText())
            }
            GoalSubtype.TrainingVolume -> GoalCard(if (model.volumePerWorkout) "Volume per workout" else "Weekly volume") {
                GoalNumberField("Target", model.targetText, { model.targetText = it }, unitLabel)
                if (model.baselineText.isNotEmpty()) GoalNote("Your recent average: ${model.baselineText} $unitLabel.", Icons.Filled.History)
                else GoalNote("No recent weighted workouts to compare against.")
            }
            GoalSubtype.SleepDuration, GoalSubtype.SleepConsistency -> GoalCard(if (model.subtype == GoalSubtype.SleepDuration) "Sleep each night" else "Sleep threshold") {
                GoalStepper("${model.sleepHours} h", model.sleepHours, 4..11, { model.sleepHours = it })
                GoalSegmented(listOf(0, 15, 30, 45).map { it to "$it min" }, model.sleepMinutes) { model.sleepMinutes = it }
                if (model.subtype == GoalSubtype.SleepConsistency) {
                    GoalStepper("${model.nightsPerWeek} nights per week", model.nightsPerWeek, 1..7, { model.nightsPerWeek = it })
                    GoalNote("A night counts when you sleep at least ${GoalFormat.sleep(model.sleepTargetMinutes)}. Weeks run on your calendar's week.")
                } else {
                    GoalNote("A nightly target — each night is compared to it. Nights without data are shown as missing, not zero.")
                }
            }
            GoalSubtype.RecoveryMetric -> GoalCard(model.recoveryMetric.displayName) {
                val baseline = model.recoveryBaseline
                if (baseline != null) Text("Your 14-day average: ${GoalFormat.number(baseline)} ${model.recoveryMetric.unit}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                else GoalNote("Not enough recent readings for a baseline yet — progress appears once there are at least 3 readings in a week.")
                GoalNumberField("Target", model.targetText, { model.targetText = it }, model.recoveryMetric.unit)
                GoalNote("Neither lower nor higher is better for everyone. Choose a target that makes sense for you, ideally with a professional's input.")
            }
            GoalSubtype.CalorieTarget -> CalorieTarget(model)
            GoalSubtype.MacroTarget -> MacroTarget(model)
            GoalSubtype.Hydration -> GoalCard("Daily water target") {
                GoalSegmented(listOf(WaterUnit.OZ to "fl oz", WaterUnit.ML to "ml"), model.waterUnit) { model.setWaterUnitConverting(it) }
                GoalNumberField("Target", model.targetText, { model.targetText = it }, model.waterUnit.rawValue)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (if (model.waterUnit == WaterUnit.ML) listOf(2000.0, 2500.0, 3000.0) else listOf(64.0, 80.0, 96.0)).forEach { preset ->
                        val text = GoalSetupViewModel.plain(preset, 0)
                        GoalChip(GoalFormat.number(preset, 0), selected = model.targetText == text) { model.targetText = text }
                    }
                }
                GoalNote("These are general starting points, not personalized amounts — needs vary with body size, activity, and climate. This also sets your water tracker's daily goal.")
            }
            GoalSubtype.HealthyHabit -> NutritionHabit.habit(model.nutritionHabitId)?.let { habit ->
                GoalCard(habit.name) {
                    if (habit.defaultTarget != null) GoalNumberField("Daily target", model.targetText, { model.targetText = it }, habit.unit, decimal = false)
                    GoalStepper("${model.perWeek} days per week", model.perWeek, 1..7, { model.perWeek = it })
                    GoalNote(habit.rule)
                    GoalNote(if (habit.tracking == GoalTrackingSource.NutritionLogs) "Tracked from your Nutrition logs." else "Tracked by check-in.", Icons.Filled.Verified)
                }
            }
            GoalSubtype.CustomGoal -> CustomTarget(model)
            else -> Unit
        }
        model.targetError?.let { GoalNote(it, Icons.Filled.Warning, GoalWarning) }
    }
}

@Composable
private fun CalorieTarget(model: GoalSetupViewModel) {
    val rec = model.calorieRecommendation
    if (rec != null) {
        GoalCard(if (rec.personalized) "Recommended for you" else "Current target") {
            Text("${GoalFormat.number(rec.value, 0)} cal per day", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            GoalNote(rec.basis)
            if (rec.personalized) NutritionSourcesLink()
            GoalLinkButton("Use this target") { model.calorieUseRange = false; model.targetText = GoalSetupViewModel.plain(rec.value, 0) }
        }
    } else {
        GoalCard("Preliminary") {
            GoalNote("We don't have enough information for a personalized recommendation. Complete Nutrition setup for one, or enter your own target.")
            GoalLinkButton("Set up Nutrition profile") { AppRouter.push("nutritionOnboarding") }
        }
    }
    model.averageLoggedCalories?.let { (avg, days) ->
        GoalNote("You've logged an average of ${GoalFormat.number(avg, 0)} cal over $days days. That's what you logged — not an estimate of what you need.", Icons.Filled.BarChart)
    }
    GoalCard("Your target") {
        GoalSegmented(listOf(false to "Single target", true to "Range"), model.calorieUseRange) { model.calorieUseRange = it }
        if (model.calorieUseRange) {
            GoalNumberField("From", model.calorieMinText, { model.calorieMinText = it }, "cal", decimal = false)
            GoalNumberField("To", model.calorieMaxText, { model.calorieMaxText = it }, "cal", decimal = false)
        } else {
            GoalNumberField("Daily target", model.targetText, { model.targetText = it }, "cal", decimal = false)
            GoalNote("A day counts when logged calories are within 10% of this target.")
        }
        GoalNote("This becomes your Nutrition daily target, so both stay in step.", Icons.Filled.Link)
    }
}

@Composable
private fun MacroTarget(model: GoalSetupViewModel) {
    GoalCard("Which macros?") {
        MacroKey.entries.forEach { key -> GoalToggle(key.displayName, key in model.selectedMacros, { model.toggleMacro(key, it) }) }
        GoalNote("Only selected macros are tracked — you don't need to set the others.")
    }
    MacroKey.entries.filter { it in model.selectedMacros }.forEach { key ->
        GoalCard(key.displayName) {
            GoalNumberField("Daily target", model.macroText[key] ?: "", { model.macroText[key] = it }, "g", decimal = false)
            if (NutritionGoalsStore.hasProfile && NutritionGoalsStore.targets.value?.isManualOverride == false) {
                GoalNote("Prefilled with the recommendation from your Nutrition profile.", Icons.Filled.AutoAwesome)
            }
            GoalNote(key.ruleText)
        }
    }
    GoalNote("These become your Nutrition macro targets, so both stay in step.", Icons.Filled.Link)
}

@Composable
private fun CustomTarget(model: GoalSetupViewModel) {
    GoalCard("Target") {
        when (model.customMetric) {
            CustomGoalMetric.MANUAL -> {
                GoalSegmented(listOf(GoalFrequency.Daily to "Daily", GoalFrequency.Weekly to "Weekly", GoalFrequency.Overall to "Once"),
                    model.customFrequency) { model.customFrequency = it }
                if (model.customFrequency == GoalFrequency.Weekly) GoalStepper("${model.perWeek} times per week", model.perWeek, 1..7, { model.perWeek = it })
                GoalNote("Check in “${model.actionName}” when it's done.")
            }
            CustomGoalMetric.FREQUENCY -> GoalStepper("${model.perWeek} days per week", model.perWeek, 1..7, { model.perWeek = it })
            CustomGoalMetric.STEPS -> GoalNumberField("Steps per day", model.targetText, { model.targetText = it }, "steps", decimal = false)
            else -> {
                GoalNumberField("Target", model.targetText, { model.targetText = it })
                GoalTextField(model.customUnit, { model.customUnit = it }, "Unit")
                if (model.customMetric == CustomGoalMetric.DISTANCE) {
                    GoalSegmented(listOf("mi" to "mi", "km" to "km"), model.customUnit) { model.customUnit = it }
                }
                GoalSegmented(listOf(GoalFrequency.Daily to "Per day", GoalFrequency.Weekly to "Per week", GoalFrequency.Overall to "Total"),
                    model.customFrequency) { model.customFrequency = it }
                model.targetText.toDoubleOrNull()?.let { value ->
                    val period = when (model.customFrequency) { GoalFrequency.Daily -> "per day"; GoalFrequency.Weekly -> "per week"; else -> "in total" }
                    Text("${GoalFormat.number(value)} ${model.customUnit} $period", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                }
            }
        }
    }
}

@Composable
private fun FrequencyStep(model: GoalSetupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (model.subtype != GoalSubtype.CustomRoutine) {
            GoalCard("Frequency") {
                if (model.subtype in setOf(GoalSubtype.CalorieTarget, GoalSubtype.MacroTarget, GoalSubtype.Hydration)) {
                    GoalSegmented(listOf(true to "Every day", false to "Days per week"), model.isDaily) { model.isDaily = it }
                    if (!model.isDaily) GoalStepper("${model.perWeek} days per week", model.perWeek, 1..7, { model.perWeek = it })
                } else {
                    val presets = if (model.subtype == GoalSubtype.WorkoutConsistency) listOf(2, 3, 4, 5) else listOf(3, 5, 7)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEach { count ->
                            val on = model.perWeek == count && !(model.isDaily && count != 7)
                            GoalChip(if (count == 7) "Daily" else "$count×", selected = on, modifier = Modifier.weight(1f)) {
                                model.perWeek = count; model.isDaily = count == 7
                            }
                        }
                    }
                    GoalStepper("Custom: ${model.perWeek} per week", model.perWeek, 1..7, { model.perWeek = it; model.isDaily = it == 7 })
                    GoalNote("Presets are suggestions. Your weekly target doesn't require specific days.")
                }
                if (model.subtype in setOf(GoalSubtype.MovementRoutine, GoalSubtype.WellnessRoutine, GoalSubtype.RecoveryRoutine,
                        GoalSubtype.DailyHabit, GoalSubtype.WorkoutConsistency)) {
                    GoalNumberField("Session length", model.sessionMinutesText, { model.sessionMinutesText = it }, "min", decimal = false)
                }
            }
        }
        GoalCard("Schedule (optional)") {
            GoalSegmented(listOf(true to "Keep it flexible", false to "Specific days"), model.flexibleSchedule) { model.flexibleSchedule = it }
            if (model.flexibleSchedule) GoalNote("Any qualifying completion during the week counts.")
            else {
                WeekdayPicker(model.weekdays.toList()) { model.toggleWeekday(it) }
                if (model.weekdays.isEmpty()) GoalNote("Pick at least one day.")
            }
            GoalToggle("Preferred time of day", model.usePreferredTime, { model.usePreferredTime = it })
            if (model.usePreferredTime) GoalTimeRow("Time", model.preferredTime) { model.preferredTime = it }
            if (model.subtype == GoalSubtype.WorkoutConsistency) GoalNote("Choosing days doesn't add anything to your calendar.")
        }
    }
}

@Composable
private fun TimeFrameStep(model: GoalSetupViewModel) {
    val rec = model.recommendation
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GoalCard {
            val subtype = model.subtype
            if (subtype in setOf(GoalSubtype.LoseWeight, GoalSubtype.ImproveLift, GoalSubtype.PersonalRecord, GoalSubtype.RepsAtWeight)) {
                val unit = if (subtype == GoalSubtype.RepsAtWeight) "reps" else GoalFormat.label(model.weightUnit)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    GoalStatText(if (subtype == GoalSubtype.LoseWeight) "Starting" else "Baseline", "${model.baselineText} $unit")
                    GoalStatText("Target", "${model.targetText} $unit")
                    val b = model.baselineText.toDoubleOrNull(); val t = model.targetText.toDoubleOrNull()
                    if (b != null && t != null) GoalStatText("Change", "${GoalFormat.number(kotlin.math.abs(t - b))} $unit")
                }
                HorizontalDivider()
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                GoalStatText("Start", GoalFormat.shortDate(model.startDate))
                GoalStatText("Target date", model.targetDate.format(dateFormat))
                GoalStatText("Duration", model.durationText)
            }
            model.requiredRateText?.let { rate ->
                GoalNote("Required pace: $rate", Icons.Filled.Speed, if (rate.contains("faster")) GoalWarning else null)
            }
        }
        model.timeFrameOptions.forEach { option ->
            val end = model.startDate.plusDays(option.weeks * 7L)
            GoalOptionCard(if (option.isRecommended) Icons.Filled.AutoAwesome else Icons.Filled.CalendarMonth, option.label,
                "Ends ${end.format(dateFormat)}", !model.useCustomDate && model.selectedWeeks == option.weeks,
                badge = if (option.isRecommended) (if (rec.personalized) "RECOMMENDED" else "SUGGESTED") else null) {
                model.useCustomDate = false
                model.selectedWeeks = option.weeks
            }
        }
        GoalOptionCard(Icons.Filled.EditCalendar, "Custom date", "Pick your own target date", model.useCustomDate) { model.useCustomDate = true }
        if (model.useCustomDate) {
            GoalCard {
                GoalDateRow("Target date", model.customTargetDate, { model.customTargetDate = it }, min = model.startDate.plusDays(1))
            }
        }
        if (rec.basis.isNotEmpty()) GoalNote(if (rec.personalized) rec.basis else "${rec.basis} This is a general suggestion, not a personalized prediction.")
        else GoalNote("Choose any duration that suits you — there isn't enough information for a recommendation.")
        // This step recommends a weight-loss pace, so its source stays easy to find here.
        if (model.subtype == GoalSubtype.LoseWeight) NutritionSourcesLink(title = "How this pace is estimated · Sources")
    }
}

@Composable
private fun TrackingStep(model: GoalSetupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GoalCard("Tracking source") {
            if (model.subtype == GoalSubtype.SleepDuration || model.subtype == GoalSubtype.SleepConsistency) {
                GoalSegmented(listOf(GoalTrackingSource.AppleHealth to "Health Connect", GoalTrackingSource.Manual to "Manual entry"),
                    model.trackingSource) { model.trackingSource = it }
                GoalNote(if (model.trackingSource == GoalTrackingSource.AppleHealth)
                    "Sleep comes from Health Connect. You can still log a night manually; manual entries are labelled."
                else "Log each night's sleep yourself.")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Verified, contentDescription = null, tint = FLPrimary)
                    Text(trackingText(model), fontSize = 15.sp)
                }
            }
            model.trackingError?.let {
                GoalNote(it, Icons.Filled.Warning, GoalWarning)
                if (!model.healthConnected) OpenConnectedAppsButton()
            }
        }
        GoalCard("Reminders") {
            GoalToggle("Remind me", model.reminderEnabled, { model.reminderEnabled = it })
            if (model.reminderEnabled) {
                GoalTimeRow("Time", model.reminderTime) { model.reminderTime = it }
                GoalNote(if (model.flexibleSchedule) "Every day at this time." else "On your scheduled days at this time.")
            }
        }
    }
}

private fun trackingText(model: GoalSetupViewModel): String {
    val source = model.buildGoal().trackingSource ?: model.trackingSource
    return when (model.subtype) {
        GoalSubtype.LoseWeight -> "Weight Log entries, manual or synced"
        GoalSubtype.ImproveLift, GoalSubtype.PersonalRecord, GoalSubtype.RepsAtWeight, GoalSubtype.TrainingVolume -> "Qualifying workout logs"
        GoalSubtype.CustomRoutine -> "Workout logs and check-ins, per activity"
        GoalSubtype.CustomGoal -> if (model.customMetric == CustomGoalMetric.STEPS) "Health Connect" else "Manual entry or check-in"
        else -> source.displayName
    }
}

@Composable
private fun ReviewStep(model: GoalSetupViewModel) {
    val hasPinned = remember { GoalStore.pinnedGoal() != null }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GoalCard {
            model.reviewRows.forEach { (label, value) ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(label, fontSize = 14.sp, color = goalSecondaryText())
                    Spacer(Modifier.weight(1f).widthIn(min = 16.dp))
                    Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 220.dp))
                }
            }
        }
        GoalCard {
            GoalToggle("Pin to My Goals on Home", model.pinToHome, { model.pinToHome = it },
                subtitle = if (hasPinned) "Replaces your current pinned goal. Nothing is deleted." else null)
        }
        GoalNote("Tap back to change anything before creating your goal.")
    }
}

@Composable
private fun ConfirmationStep(model: GoalSetupViewModel, onView: (String) -> Unit, onAnother: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(64.dp))
        Text("Goal created", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        val goal = model.createdGoal ?: return@Column
        Text(goal.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        goal.targetDate?.let {
            Text("Target date ${com.fitnesslink.fit.model.GoalDay.localDate(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))}",
                fontSize = 14.sp, color = goalSecondaryText())
        }
        Text(if (goal.isPinned) "Pinned to My Goals on Home." else "You can pin it to Home from the goal screen.", fontSize = 13.sp, color = goalSecondaryText())
        GoalPrimaryButton("View Goal") { onView(goal.id) }
        GoalLinkButton("Create Another Goal", onAnother)
    }
}
