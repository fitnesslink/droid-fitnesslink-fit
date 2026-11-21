package com.fitnesslink.fit.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.api.whyText
import com.fitnesslink.fit.model.onboarding.MuscleFocus
import com.fitnesslink.fit.model.onboarding.NutritionGoalChoice
import com.fitnesslink.fit.model.onboarding.RecompFocus
import com.fitnesslink.fit.network.dto.NutritionTargets
import com.fitnesslink.fit.onboarding.OnboardingMode
import com.fitnesslink.fit.onboarding.OnboardingRules
import com.fitnesslink.fit.onboarding.OnboardingStep
import com.fitnesslink.fit.ui.nutrition.NutritionSourcesDialog
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.viewmodel.OnboardingFlowViewModel
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Onboarding PRD v1.1 — the whole flow in one screen. Port of iOS
 * `OnboardingFlowView`: replaces the legacy questionnaire + Get Started on
 * first run, and runs the nine fitness questions alone for "Update Training
 * Preferences" ([OnboardingMode.EDIT_TRAINING]).
 */
@Composable
fun OnboardingFlowScreen(mode: OnboardingMode = OnboardingMode.FULL, onComplete: () -> Unit) {
    val vm: OnboardingFlowViewModel = viewModel(key = "onboarding-${mode.name}")
    LaunchedEffect(Unit) { vm.configure(mode) }

    BackHandler(enabled = true) {
        if (vm.canGoBack) vm.goBack() else if (mode == OnboardingMode.EDIT_TRAINING) onComplete()
    }

    val step = vm.step
    // Fresh composition per step: scroll position and field state never
    // carry over from the previous question.
    key(step) { when (step) {
        OnboardingStep.WELCOME -> OnboardingWelcomeScreen { vm.advance() }
        OnboardingStep.NUTRITION_TRANSITION -> NutritionCalculatingScreen(vm)
        OnboardingStep.NUTRITION_MISSING -> NutritionMissingScreen(vm)
        OnboardingStep.NUTRITION_TARGETS -> vm.previewTargets?.let { NutritionTargetsScreen(vm, it) } ?: NutritionCalculatingScreen(vm, recalc = true)
        OnboardingStep.CUSTOMIZE_TARGETS -> vm.previewTargets?.let { CustomizeTargetsScreen(vm, it) } ?: NutritionCalculatingScreen(vm, recalc = true)
        OnboardingStep.FITNESS_TRANSITION -> FitnessMatchingScreen(vm, onComplete)
        else -> QuestionScreen(vm, step, onComplete)
    } }
}

@Composable
private fun QuestionScreen(vm: OnboardingFlowViewModel, step: OnboardingStep, onComplete: () -> Unit) {
    val lastEdit = vm.mode == OnboardingMode.EDIT_TRAINING && step == OnboardingStep.INTERESTS
    val (title, subtitle) = when (step) {
        OnboardingStep.PRIMARY_GOAL -> "What is your primary fitness goal?" to "Choose the goal that best describes what you want to achieve."
        OnboardingStep.SECONDARY_GOAL -> "Do you have a secondary fitness goal?" to "Choose one additional goal you'd like to work toward."
        OnboardingStep.EXPERIENCE -> "How would you describe your overall training experience?" to "This helps us recommend workouts and programs that match your current experience."
        OnboardingStep.BACKGROUND -> "What types of training do you have experience with?" to "Select the types of training you've participated in."
        OnboardingStep.LOCATION -> "Where do you plan to work out?" to "Select the locations where you plan to exercise."
        OnboardingStep.EQUIPMENT -> "What equipment do you have access to?" to "Select the option that best describes the equipment available to you."
        OnboardingStep.FREQUENCY -> "How many days per week do you plan to work out?" to "Choose how many days you can realistically dedicate to exercise."
        OnboardingStep.DURATION -> "How much time can you dedicate to each workout?" to "Select how much time you typically have available for a workout."
        OnboardingStep.INTERESTS -> "What types of training are you interested in?" to "Select the types of exercise you enjoy or would like to explore."
        OnboardingStep.ABOUT_YOU -> "A Few Basics About You" to "This information helps us estimate your calorie needs and personalize your nutrition recommendations."
        OnboardingStep.NUTRITION_GOAL -> "What is your primary nutrition goal?" to "Your answer helps us recommend daily calories and macronutrients that support your goals."
        OnboardingStep.MUSCLE_FOCUS -> "What's Your Muscle-Building Goal?" to "Choose the approach that best matches your goal."
        OnboardingStep.RECOMP_FOCUS -> "What's Your Recomposition Focus?" to "Each approach pairs strength training with plenty of protein."
        OnboardingStep.WEIGHT_GOAL -> when (vm.answers.nutritionGoal) {
            NutritionGoalChoice.LOSE_WEIGHT -> "Let's Set Your Weight Goal"
            NutritionGoalChoice.BUILD_MUSCLE_GAIN_WEIGHT -> "Your Muscle Gain Goal & Timeline"
            else -> "Recomposition Weight Target"
        } to "Set a target and a realistic pace — or continue without a target."
        OnboardingStep.DIETARY -> "Do you have any dietary preferences or restrictions?" to "We'll use this information to personalize your nutrition experience. You can update it anytime."
        else -> "" to ""
    }
    OnboardingScaffold(
        title = title,
        subtitle = subtitle,
        questionNumber = if (vm.mode == OnboardingMode.FULL) step.questionNumber else null,
        onBack = { if (vm.canGoBack) vm.goBack() else if (vm.mode == OnboardingMode.EDIT_TRAINING) onComplete() },
        footer = {
            if (vm.mode == OnboardingMode.EDIT_TRAINING) vm.saveError?.let { OnboardingNotice(it, warning = true) }
            OnboardingPrimaryButton(
                title = if (lastEdit) "Save Preferences" else "Continue",
                icon = if (lastEdit) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                enabled = vm.canContinue,
                loading = vm.isSaving
            ) { if (lastEdit) vm.saveTrainingPreferences(onComplete) else vm.advance() }
            if (vm.isSkippable && !lastEdit) OnboardingTextButton("Skip for Now") { vm.skip() }
        }
    ) {
        when (step) {
            OnboardingStep.PRIMARY_GOAL -> PrimaryGoalQuestion(vm)
            OnboardingStep.SECONDARY_GOAL -> SecondaryGoalQuestion(vm)
            OnboardingStep.EXPERIENCE -> ExperienceQuestion(vm)
            OnboardingStep.BACKGROUND -> BackgroundQuestion(vm)
            OnboardingStep.LOCATION -> LocationQuestion(vm)
            OnboardingStep.EQUIPMENT -> EquipmentQuestion(vm)
            OnboardingStep.FREQUENCY -> FrequencyQuestion(vm)
            OnboardingStep.DURATION -> DurationQuestion(vm)
            OnboardingStep.INTERESTS -> InterestsQuestion(vm)
            OnboardingStep.ABOUT_YOU -> AboutYouQuestion(vm)
            OnboardingStep.NUTRITION_GOAL -> NutritionGoalQuestion(vm)
            OnboardingStep.MUSCLE_FOCUS -> MuscleFocusQuestion(vm)
            OnboardingStep.RECOMP_FOCUS -> RecompFocusQuestion(vm)
            OnboardingStep.WEIGHT_GOAL -> WeightGoalQuestion(vm)
            OnboardingStep.DIETARY -> DietaryQuestion(vm)
            else -> {}
        }
    }
}

// MARK: Transitions

@Composable
private fun NutritionCalculatingScreen(vm: OnboardingFlowViewModel, recalc: Boolean = false) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (recalc) vm.calculateNutrition()
        while (tick < 3) { delay(420); tick++ }
    }
    OnboardingProcessingView(
        title = "Personalizing Your Nutrition",
        subtitle = "We're calculating your estimated calorie needs and macronutrient targets based on your information and nutrition goals.",
        icon = Icons.Filled.Restaurant,
        steps = listOf(
            Icons.Filled.LocalFireDepartment to "Estimating your basal metabolic rate",
            Icons.Filled.DirectionsWalk to "Calculating your maintenance calories",
            Icons.Filled.TrackChanges to "Adjusting calories for your nutrition goal",
            Icons.Filled.PieChart to "Preparing your macronutrient targets"
        ),
        completedSteps = if (vm.previewTargets != null) 4 else minOf(tick, 3),
        errorText = vm.calculationError,
        onRetry = { vm.calculateNutrition() },
        onSkip = { vm.skipNutrition("Unavailable") },
        skipTitle = "Skip Nutrition Setup"
    )
}

@Composable
private fun FitnessMatchingScreen(vm: OnboardingFlowViewModel, onDone: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (tick < 2 && vm.completionError == null) { delay(600); tick++ }
    }
    LaunchedEffect(attempt) {
        if (vm.finish()) { tick = 3; onDone() }
    }
    OnboardingProcessingView(
        title = "Finding Your Fitness Matches",
        subtitle = "We're matching available programs and workouts to your goals, experience, equipment, and schedule.",
        icon = Icons.Filled.FitnessCenter,
        steps = listOf(
            Icons.Filled.TrackChanges to "Reviewing your fitness goals",
            Icons.Filled.Tune to "Matching your training preferences",
            Icons.Filled.Layers to "Finding suitable programs and workouts"
        ),
        completedSteps = minOf(tick, 2),
        footerTitle = "Your Home Screen Is Next",
        footerText = "Once matching is complete, we'll take you to your Home screen, where you can explore recommendations and choose what works for you.",
        errorText = vm.completionError,
        onRetry = { attempt++ }
    )
}

// MARK: Complete Nutrition Setup

@Composable
private fun NutritionMissingScreen(vm: OnboardingFlowViewModel) {
    OnboardingScaffold(
        title = "Complete Nutrition Setup",
        subtitle = "We need a few more details to estimate your calorie and macro targets reliably.",
        onBack = { vm.goBack() },
        footer = {
            OnboardingPrimaryButton("Complete Missing Information") { vm.returnTo(OnboardingRules.firstMissingStep(vm.answers)) }
            OnboardingTextButton("Skip Nutrition Setup") { vm.skipNutrition("Unavailable") }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnboardingRules.missingNutritionInputs(vm.answers).forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FLTheme.colors.surface).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.RadioButtonUnchecked, null, tint = OrangeTheme, modifier = Modifier.size(20.dp))
                    Text(item, fontSize = 15.sp, color = FLTheme.colors.textPrimary)
                }
            }
            OnboardingNotice("We never guess missing measurements. You can also skip nutrition setup now and set targets later from the Nutrition tab.")
        }
    }
}

// MARK: Personalized Nutrition Targets (PRD §20)

private fun fmt(v: Double): String = NumberFormat.getIntegerInstance(Locale.US).format(Math.round(v))
private val shortFmt = SimpleDateFormat("MMM d", Locale.US)
private val longFmt = SimpleDateFormat("MMM d, yyyy", Locale.US)

@Composable
private fun NutritionTargetsScreen(vm: OnboardingFlowViewModel, t: NutritionTargets) {
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showSources by remember { mutableStateOf(false) }
    val macroKcal = t.proteinGrams * 4 + t.carbGrams * 4 + t.fatGrams * 9
    fun pct(kcal: Double) = if (t.dailyCalorieTarget > 0) Math.round(kcal / t.dailyCalorieTarget * 100).toInt() else 0

    OnboardingScaffold(
        title = "Your Personalized Nutrition Targets",
        subtitle = "Here's how we estimated your daily calorie needs based on your body measurements, activity level, and nutrition goal.",
        onBack = { vm.goBack() },
        footer = {
            OnboardingPrimaryButton("Accept My Targets", Icons.Filled.Check, loading = vm.isSaving) { vm.saveTargets(null) }
            OnboardingSecondaryButton("Customize My Targets", Icons.Filled.Tune) { vm.push(OnboardingStep.CUSTOMIZE_TARGETS) }
            OnboardingTextButton("Skip for Now") { vm.skipNutrition("Skipped") }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GoalSummary(vm)
            MetricCard(Icons.Filled.LocalFireDepartment, "Basal Metabolic Rate (BMR)", t.bmr,
                "Estimated calories your body uses at complete rest. This is not a recommended intake.", t.whyText("Bmr")) { info = it }
            MetricCard(Icons.Filled.DirectionsWalk, "Maintenance Calories (TDEE)", t.maintenanceCalories,
                "Estimated calories to maintain your current weight with your daily activity.", t.whyText("MaintenanceCalories")) { info = it }
            val diff = Math.round(t.dailyCalorieTarget - t.maintenanceCalories).toInt()
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FLPrimary.copy(alpha = 0.08f))
                    .border(1.5.dp, FLPrimary, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.TrackChanges, null, tint = FLPrimary, modifier = Modifier.size(18.dp))
                    Text("Goal Calories", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary)
                    vm.answers.nutritionGoal?.let {
                        Text(it.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary,
                            modifier = Modifier.border(1.dp, FLPrimary.copy(alpha = 0.6f), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    t.whyText("DailyCalorieTarget")?.let { text ->
                        Icon(Icons.Filled.Info, "About goal calories", tint = FLTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp).clickable { info = "Goal Calories" to text })
                    }
                }
                Text("${fmt(t.dailyCalorieTarget)} cal/day", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = FLPrimary)
                Text(
                    when {
                        diff == 0 -> "Matches your estimated maintenance calories."
                        diff < 0 -> "${fmt(-diff.toDouble())} calories below maintenance to support gradual weight loss."
                        else -> "${fmt(diff.toDouble())} calories above maintenance to support your goal."
                    }, fontSize = 13.sp, color = FLTheme.colors.textSecondary
                )
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FLTheme.colors.surface).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row {
                    Text("Your Daily Macro Targets", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                    Text("≈ ${fmt(macroKcal)} cal/day", fontSize = 12.sp, color = FLTheme.colors.textSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MacroRing("Protein", t.proteinGrams, pct(t.proteinGrams * 4), BlueTheme, "Supports muscle growth, repair, and retention.", Modifier.weight(1f))
                    MacroRing("Carbs", t.carbGrams, pct(t.carbGrams * 4), PurpleTheme, "Energy for daily activity, training, and recovery.", Modifier.weight(1f))
                    MacroRing("Fat", t.fatGrams, pct(t.fatGrams * 9), OrangeTheme, "Supports hormone function and overall health.", Modifier.weight(1f))
                }
            }
            Row(
                Modifier.fillMaxWidth().border(1.dp, FLPrimary.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Filled.PhoneAndroid, null, tint = FLPrimary, modifier = Modifier.size(26.dp))
                Column {
                    Text("Your nutrition targets, all in one place", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary)
                    Text("Once you accept your targets, we'll automatically save your daily calorie and macronutrient goals to the Nutrition tab. You can view or update them there anytime.",
                        fontSize = 13.sp, color = FLTheme.colors.textSecondary)
                }
            }
            Text("These are estimates, not medical advice. Sources & Methods", fontSize = 12.sp, color = FLTheme.colors.textSecondary,
                textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { showSources = true })
            vm.saveError?.let { OnboardingNotice(it, warning = true) }
        }
    }
    info?.let { (title, text) ->
        AlertDialog(onDismissRequest = { info = null }, confirmButton = { TextButton({ info = null }) { Text("OK") } },
            title = { Text(title) }, text = { Text(text) })
    }
    if (showSources) NutritionSourcesDialog(onDismiss = { showSources = false })
}

@Composable
private fun GoalSummary(vm: OnboardingFlowViewModel) {
    val a = vm.answers
    val goal = a.nutritionGoal ?: return
    val kg = a.usesKilograms
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FLTheme.colors.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Your Goal", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textSecondary)
        Text(goal.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
        val showsWeights = goal.changesWeight &&
            !(goal == NutritionGoalChoice.BUILD_MUSCLE_GAIN_WEIGHT && a.muscleFocus == MuscleFocus.NO_WEIGHT_GOAL) &&
            !(goal == NutritionGoalChoice.BODY_RECOMPOSITION && a.recompFocus != RecompFocus.TARGET_WEIGHT)
        if (showsWeights) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                a.weightKg?.let { SummaryItem("Current", OnboardingRules.displayWeight(it, kg)) }
                if (a.skipTargetWeight != true) a.targetWeightKg?.let { SummaryItem("Target", OnboardingRules.displayWeight(it, kg)) }
                a.weeklyRateKg?.let { SummaryItem("Pace", OnboardingRules.paceLabel(it, kg)) }
            }
            OnboardingRules.estimatedTimeline(a)?.let { est ->
                Text("Estimated timeframe: about ${est.minWeeks}–${est.maxWeeks} weeks (${shortFmt.format(est.from)} – ${longFmt.format(est.to)}). Projections are estimates, not guarantees.",
                    fontSize = 12.sp, color = FLTheme.colors.textSecondary)
            }
        } else if (goal == NutritionGoalChoice.BODY_RECOMPOSITION) {
            a.recompFocus?.let { Text(it.title, fontSize = 13.sp, color = FLTheme.colors.textSecondary) }
        } else if (goal == NutritionGoalChoice.BUILD_MUSCLE_GAIN_WEIGHT) {
            Text(MuscleFocus.NO_WEIGHT_GOAL.title, fontSize = 13.sp, color = FLTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) = Column {
    Text(label, fontSize = 11.sp, color = FLTheme.colors.textSecondary)
    Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary)
}

@Composable
private fun MetricCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: Double, caption: String,
    why: String?, onInfo: (Pair<String, String>) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FLTheme.colors.surface).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OnboardingIconBadge(icon, 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary)
                if (why != null) Icon(Icons.Filled.Info, "About $title", tint = FLTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp).clickable { onInfo(title to why) })
            }
            Text("${fmt(value)} cal/day", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
            Text(caption, fontSize = 12.sp, color = FLTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun MacroRing(name: String, grams: Double, pct: Int, color: Color, note: String, modifier: Modifier) {
    val track = FLTheme.colors.progressTrack
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$name: ${Math.round(grams)} grams a day, about $pct percent of calories. $note" },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                val inset = 7.dp.toPx() / 2
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = stroke)
                drawArc(color, -90f, 360f * pct.coerceIn(0, 100) / 100f, false, topLeft, arcSize, style = stroke)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${Math.round(grams)} g", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
                Text(name, fontSize = 11.sp, color = FLTheme.colors.textSecondary)
            }
        }
        Text("≈ $pct% of calories", fontSize = 11.sp, color = FLTheme.colors.textSecondary)
        Text(note, fontSize = 11.sp, color = FLTheme.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

// MARK: Customize My Targets (PRD §19.6 / §21.2)

@Composable
private fun CustomizeTargetsScreen(vm: OnboardingFlowViewModel, rec: NutritionTargets) {
    var calories by remember { mutableIntStateOf(Math.round(rec.dailyCalorieTarget).toInt()) }
    var protein by remember { mutableIntStateOf(Math.round(rec.proteinGrams).toInt()) }
    var carbs by remember { mutableIntStateOf(Math.round(rec.carbGrams).toInt()) }
    var fat by remember { mutableIntStateOf(Math.round(rec.fatGrams).toInt()) }
    var confirm by remember { mutableStateOf(false) }
    val macroKcal = protein * 4 + carbs * 4 + fat * 9
    val discrepancy = macroKcal - calories
    val floor = if (vm.answers.sex?.nutritionSex == "Male") 1500 else 1200
    val blocking = when {
        calories < floor -> "Daily calories below ${fmt(floor.toDouble())} aren't supported without medical supervision."
        calories > 6000 -> "That calorie target is higher than FitnessLink supports."
        protein < 20 || fat < 20 -> "Protein and fat each need at least 20 g a day."
        else -> null
    }

    OnboardingScaffold(
        title = "Customize My Targets",
        subtitle = "Adjust your daily calorie and macronutrient targets. These will be saved to your Nutrition tab.",
        onBack = { vm.goBack() },
        footer = {
            OnboardingPrimaryButton("Save Targets", Icons.Filled.Check, enabled = blocking == null, loading = vm.isSaving) { confirm = true }
            OnboardingSecondaryButton("Back", Icons.AutoMirrored.Filled.ArrowBack) { vm.goBack() }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FLTheme.colors.surface).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Daily Calorie Target", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    StepButton(Icons.Filled.Remove, "Decrease calories") { calories = maxOf(800, calories - 50) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 130.dp)) {
                        Text(fmt(calories.toDouble()), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = FLPrimary)
                        Text("calories/day", fontSize = 12.sp, color = FLTheme.colors.textSecondary)
                    }
                    StepButton(Icons.Filled.Add, "Increase calories") { calories = minOf(6000, calories + 50) }
                }
                Text("Recommended: ${fmt(rec.dailyCalorieTarget)} cal/day", fontSize = 12.sp, color = FLTheme.colors.textSecondary)
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FLTheme.colors.surface).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row {
                    Text("Macronutrient Targets", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                    Text("≈ ${fmt(macroKcal.toDouble())} cal/day", fontSize = 12.sp, color = FLTheme.colors.textSecondary)
                }
                MacroRow("Protein", protein, 4, BlueTheme, calories) { protein = it }
                MacroRow("Carbs", carbs, 4, PurpleTheme, calories) { carbs = it }
                MacroRow("Fats", fat, 9, OrangeTheme, calories) { fat = it }
            }
            if (Math.abs(discrepancy) <= 15) {
                OnboardingNotice("Balanced — your macros add up to about ${fmt(macroKcal.toDouble())} calories per day (± rounding).")
            } else {
                OnboardingNotice("Your macros add up to ${fmt(macroKcal.toDouble())} calories — ${fmt(Math.abs(discrepancy).toDouble())} ${if (discrepancy > 0) "more" else "fewer"} than your ${fmt(calories.toDouble())}-calorie target.", warning = true)
                Text("Set calories to match macros", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary,
                    modifier = Modifier.clickable { calories = macroKcal })
            }
            blocking?.let { OnboardingNotice(it, warning = true) }
            vm.saveError?.let { OnboardingNotice(it, warning = true) }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Save these targets?") },
            text = {
                Text("${fmt(calories.toDouble())} cal · $protein g protein · $carbs g carbs · $fat g fat per day. They'll replace any targets on your Nutrition tab." +
                    if (Math.abs(discrepancy) > 15) " Your macros add up to ${fmt(macroKcal.toDouble())} calories." else "")
            },
            confirmButton = { TextButton({ confirm = false; vm.saveTargets(OnboardingFlowViewModel.CustomTargets(calories, protein, carbs, fat)) }) { Text("Save") } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Int = 40, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).border(1.dp, FLTheme.colors.textTertiary.copy(alpha = 0.6f), CircleShape).clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = FLTheme.colors.textPrimary, modifier = Modifier.size((size * 0.45).dp)) }
}

@Composable
private fun MacroRow(name: String, grams: Int, perGram: Int, color: Color, calories: Int, onChange: (Int) -> Unit) {
    val kcal = grams * perGram
    val p = if (calories > 0) Math.round(kcal.toDouble() / calories * 100).toInt() else 0
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary, maxLines = 1)
            Text("$perGram cal per gram", fontSize = 11.sp, color = FLTheme.colors.textSecondary)
        }
        StepButton(Icons.Filled.Remove, "Decrease $name", 30) { onChange(maxOf(0, grams - 5)) }
        Text("$grams g", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary,
            modifier = Modifier.widthIn(min = 54.dp), textAlign = TextAlign.Center)
        StepButton(Icons.Filled.Add, "Increase $name", 30) { onChange(minOf(800, grams + 5)) }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 48.dp)) {
            Text("≈ $kcal", fontSize = 12.sp, color = FLTheme.colors.textPrimary)
            Text("($p%)", fontSize = 11.sp, color = FLTheme.colors.textSecondary)
        }
    }
}
