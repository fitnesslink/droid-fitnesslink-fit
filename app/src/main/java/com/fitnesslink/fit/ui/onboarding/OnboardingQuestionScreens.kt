package com.fitnesslink.fit.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.onboarding.*
import com.fitnesslink.fit.onboarding.OnboardingRules
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme
import com.fitnesslink.fit.viewmodel.OnboardingFlowViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Port of iOS OnboardingQuestionViews / OnboardingAboutYouView / OnboardingNutritionViews.

@Composable
fun OnboardingWelcomeScreen(onStart: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) { // theme-exempt: over media
        Image(
            painterResource(R.drawable.onb_welcome_hero), null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.55f)
        )
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.55f).background(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent, Color.Black)) // theme-exempt: over media
        ))
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                OnboardingWordmark(color = Color.White) // theme-exempt: over media
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("WELCOME TO FITNESSLINK", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = FLPrimary)
                Text("Let's Build Your Fitness Experience", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 38.sp) // theme-exempt: over media
                Text("Tell us a little about your goals and preferences so we can personalize your training and nutrition experience.",
                    fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f)) // theme-exempt: over media
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WelcomeFeature(Icons.Filled.FitnessCenter, "Tailored Training", "Programs & workouts for your goals")
                    WelcomeFeature(Icons.Filled.Restaurant, "Personalized Nutrition", "Calorie & macro targets")
                    WelcomeFeature(Icons.Filled.BarChart, "Track Progress", "See your results")
                }
                OnboardingPrimaryButton("Let's Get Started", Icons.AutoMirrored.Filled.ArrowForward, onClick = onStart)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp)) // theme-exempt: over media
                    Text("  It only takes a few minutes", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f)) // theme-exempt: over media
                }
            }
        }
    }
}

@Composable
private fun RowScope.WelcomeFeature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Column(
        Modifier.weight(1f).fillMaxHeight().heightIn(min = 118.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)).padding(10.dp), // theme-exempt: over media
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = FLPrimary, modifier = Modifier.size(22.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center) // theme-exempt: over media
        Text(text, fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f), textAlign = TextAlign.Center) // theme-exempt: over media
    }
}

@Composable
private fun <T> TwoColumnGrid(items: List<T>, spacing: Int = 10, cell: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.dp)) {
                row.forEach { cell(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// MARK: Q1–Q9

@Composable
fun PrimaryGoalQuestion(vm: OnboardingFlowViewModel) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    FitnessGoal.entries.forEach { goal ->
        OnboardingOptionCard(goal, vm.answers.primaryGoal == goal) { vm.update { OnboardingRules.setPrimaryGoal(it, goal) } }
    }
}

@Composable
fun SecondaryGoalQuestion(vm: OnboardingFlowViewModel) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    FitnessGoal.entries.filter { it != vm.answers.primaryGoal }.forEach { goal ->
        OnboardingOptionCard(goal, vm.answers.secondaryGoal == goal) { vm.update { it.copy(secondaryGoal = goal, noSecondaryGoal = false) } }
    }
    val none = vm.answers.noSecondaryGoal == true
    Row(
        Modifier.fillMaxWidth().selectableCard(none) { vm.update { it.copy(secondaryGoal = null, noSecondaryGoal = true) } }
            .background(FLTheme.colors.surface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OnboardingIconBadge(Icons.Filled.RemoveCircleOutline)
        Text("No Secondary Goal", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
        SelectionIndicator(none)
    }
}

@Composable
fun ExperienceQuestion(vm: OnboardingFlowViewModel) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    TrainingExperience.entries.forEach { level ->
        OnboardingOptionCard(level, vm.answers.experience == level) { vm.update { it.copy(experience = level) } }
    }
}

@Composable
fun BackgroundQuestion(vm: OnboardingFlowViewModel) =
    TwoColumnGrid(TrainingBackground.entries) { item, mod ->
        OnboardingCompactTile(item.title, item.icon, item in vm.answers.trainingBackground, mod) {
            vm.update { OnboardingRules.toggleBackground(it, item) }
        }
    }

@Composable
fun LocationQuestion(vm: OnboardingFlowViewModel) =
    TwoColumnGrid(WorkoutLocationChoice.entries, spacing = 12) { loc, mod ->
        OnboardingPhotoTile(loc, loc in vm.answers.locations, mod) {
            vm.update { it.copy(locations = OnboardingRules.toggled(it.locations, loc)) }
        }
    }

@Composable
fun EquipmentQuestion(vm: OnboardingFlowViewModel) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    EquipmentAccess.entries.forEach { access ->
        OnboardingOptionCard(access, vm.answers.equipmentAccess == access) { vm.update { OnboardingRules.setEquipmentAccess(it, access) } }
        if (access == EquipmentAccess.HOME_GYM) {
            AnimatedVisibility(vm.answers.equipmentAccess == EquipmentAccess.HOME_GYM) {
                Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select the equipment you have", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textSecondary)
                    TwoColumnGrid(HomeEquipment.entries) { item, mod ->
                        OnboardingCompactTile(item.title, item.icon, item in vm.answers.equipment, mod) {
                            vm.update { it.copy(equipment = OnboardingRules.toggled(it.equipment, item)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FrequencyQuestion(vm: OnboardingFlowViewModel) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    WeeklyFrequency.entries.forEach { f ->
        OnboardingOptionCard(f, vm.answers.weeklyFrequency == f, showsPhoto = false) { vm.update { it.copy(weeklyFrequency = f) } }
    }
}

@Composable
fun DurationQuestion(vm: OnboardingFlowViewModel) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    SessionDuration.entries.forEach { d ->
        OnboardingOptionCard(d, vm.answers.sessionDuration == d, showsPhoto = false) { vm.update { it.copy(sessionDuration = d) } }
    }
}

@Composable
fun InterestsQuestion(vm: OnboardingFlowViewModel) {
    var expanded by remember { mutableStateOf(setOf(InterestCategory.STRENGTH)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InterestCategory.entries.forEach { category ->
            val count = category.interests.count { it in vm.answers.interests }
            val open = category in expanded
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FLTheme.colors.surface)) {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) {
                        expanded = if (open) expanded - category else expanded + category
                    }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OnboardingIconBadge(category.icon)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(category.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary)
                        if (count > 0) Text("$count selected", fontSize = 12.sp, color = FLPrimary)
                    }
                    Icon(Icons.Filled.ExpandMore, if (open) "Collapse" else "Expand", tint = FLTheme.colors.textSecondary,
                        modifier = Modifier.rotate(if (open) 180f else 0f))
                }
                if (open) {
                    Box(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                        TwoColumnGrid(category.interests, spacing = 8) { interest, mod ->
                            OnboardingCompactTile(interest.title, null, interest in vm.answers.interests, mod) {
                                vm.update { it.copy(interests = OnboardingRules.toggled(it.interests, interest)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: Q10 — About You

@Composable
fun AboutYouQuestion(vm: OnboardingFlowViewModel) {
    val a = vm.answers
    LaunchedEffect(Unit) {
        val metric = Locale.getDefault().country !in setOf("US", "LR", "MM")
        vm.update {
            it.copy(heightUnit = it.heightUnit ?: if (metric) "metric" else "imperial",
                weightUnit = it.weightUnit ?: if (metric) "kg" else "lb")
        }
    }
    var ageText by remember { mutableStateOf(a.age?.toString() ?: "") }
    var cmText by remember { mutableStateOf(a.heightCm?.let { Math.round(it).toString() } ?: "") }
    val totalInches = a.heightCm?.let { Math.round(it / OnboardingRules.CM_PER_INCH).toInt() }
    var feet by remember { mutableStateOf(totalInches?.div(12)) }
    var inches by remember { mutableStateOf(totalInches?.rem(12)) }
    fun weightDisplay() = a.weightKg?.let { OnboardingRules.formatNumber(Math.rint((if (a.usesKilograms) it else it / OnboardingRules.KG_PER_LB) * 10) / 10) } ?: ""
    var weightText by remember { mutableStateOf(weightDisplay()) }
    var showSexInfo by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FLTheme.colors.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OnboardingSectionHeader("Your Information", "Used to estimate your calorie needs and personalize recommendations.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sex (for calorie equation)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary)
                IconButton(onClick = { showSexInfo = true }) { Icon(Icons.Filled.Info, "Why we ask", tint = FLTheme.colors.textSecondary) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetabolicSexInput.entries.forEach { s -> Segment(s.title, a.sex == s, Modifier.weight(1f)) { vm.update { it.copy(sex = s) } } }
            }
            LabeledRow("Age") {
                NumberField(ageText, "Enter age", KeyboardType.Number, Modifier.weight(1f), focus) {
                    ageText = it.filter(Char::isDigit); vm.update { x -> x.copy(age = ageText.toIntOrNull()) }
                }
            }
            LabeledRow("Height") {
                if (a.usesMetricHeight) {
                    NumberField(cmText, "cm", KeyboardType.Decimal, Modifier.weight(1f), focus) {
                        cmText = it; vm.update { x -> x.copy(heightCm = it.replace(',', '.').toDoubleOrNull()) }
                    }
                } else {
                    fun sync() { vm.update { x -> x.copy(heightCm = feet?.let { f -> (f * 12 + (inches ?: 0)) * OnboardingRules.CM_PER_INCH }) } }
                    Dropdown(feet?.let { "$it ft" } ?: "ft", (3..8).map { "$it ft" }, Modifier.weight(1f)) { i -> feet = 3 + i; sync() }
                    Dropdown(inches?.let { "$it in" } ?: "in", (0..11).map { "$it in" }, Modifier.weight(1f)) { i -> inches = i; sync() }
                }
                UnitToggle("ft / in", "cm", a.usesMetricHeight) { metric -> vm.update { it.copy(heightUnit = if (metric) "metric" else "imperial") } }
            }
            LabeledRow("Weight") {
                NumberField(weightText, "Enter weight", KeyboardType.Decimal, Modifier.weight(1f), focus) {
                    weightText = it
                    val v = it.replace(',', '.').toDoubleOrNull()
                    vm.update { x -> x.copy(weightKg = v?.let { w -> if (x.usesKilograms) w else w * OnboardingRules.KG_PER_LB }) }
                }
                UnitToggle("lb", "kg", a.usesKilograms) { kg ->
                    vm.update { it.copy(weightUnit = if (kg) "kg" else "lb") }
                    weightText = vm.answers.weightKg?.let { w -> OnboardingRules.formatNumber(Math.rint((if (kg) w else w / OnboardingRules.KG_PER_LB) * 10) / 10) } ?: ""
                }
            }
        }
        OnboardingSectionHeader("Daily Activity Level", "How active are you throughout a typical day? Think about your everyday movement outside of planned workouts.")
        DailyActivityLevel.entries.forEach { level ->
            OnboardingOptionCard(level, a.activityLevel == level, showsPhoto = false) { vm.update { it.copy(activityLevel = level) } }
        }
        OnboardingRules.aboutYouErrors(a).forEach { OnboardingNotice(it, warning = true) }
        OnboardingNotice("You can update this information later in your profile. Nutrition targets need all of these details — anything you skip, we'll ask about before calculating.")
    }
    if (showSexInfo) {
        AlertDialog(
            onDismissRequest = { showSexInfo = false },
            confirmButton = { TextButton({ showSexInfo = false }) { Text("OK") } },
            title = { Text("Why we ask") },
            text = { Text("The calorie equation FitnessLink uses (Mifflin-St Jeor) has separate female and male versions. This answer is used only for that estimate and is separate from gender identity. If you prefer not to say, you can still use FitnessLink — we just can't estimate calorie targets.") }
        )
    }
}

@Composable
private fun LabeledRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary, modifier = Modifier.width(58.dp))
        content()
    }
}

@Composable
private fun NumberField(
    value: String, placeholder: String, type: KeyboardType, modifier: Modifier,
    focus: androidx.compose.ui.focus.FocusManager, onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, placeholder = { Text(placeholder) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier
    )
}

@Composable
private fun Segment(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 42.dp).clip(CircleShape)
            .background(if (selected) FLPrimary.copy(alpha = 0.14f) else FLTheme.colors.background)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) FLPrimary else FLTheme.colors.textTertiary.copy(alpha = 0.4f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick).semantics { this.selected = selected },
        contentAlignment = Alignment.Center
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 14.sp,
            color = if (selected) FLPrimary else FLTheme.colors.textPrimary, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun UnitToggle(left: String, right: String, rightSelected: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.clip(CircleShape).background(FLTheme.colors.background).padding(2.dp)) {
        listOf(false, true).forEach { isRight ->
            val sel = isRight == rightSelected
            Box(
                Modifier.width(48.dp).height(36.dp).clip(CircleShape)
                    .background(if (sel) FLPrimary.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable(role = Role.Button) { onChange(isRight) }.semantics { selected = sel },
                contentAlignment = Alignment.Center
            ) { Text(if (isRight) right else left, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (sel) FLPrimary else FLTheme.colors.textSecondary) }
        }
    }
}

@Composable
private fun Dropdown(label: String, options: List<String>, modifier: Modifier, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).background(FLTheme.colors.background)
                .clickable(role = Role.DropdownList) { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = FLTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, null, tint = FLTheme.colors.textSecondary)
        }
        DropdownMenu(open, { open = false }) {
            options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onSelect(i) }) }
        }
    }
}

// MARK: Q11 + conditional branches

@Composable
fun NutritionGoalQuestion(vm: OnboardingFlowViewModel) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    val minor = OnboardingRules.isMinor(vm.answers)
    NutritionGoalChoice.entries.forEach { goal ->
        val blocked = minor && goal.changesWeight
        OnboardingOptionCard(goal, vm.answers.nutritionGoal == goal, enabled = !blocked,
            disabledNote = if (blocked) "Weight-change targets are available for adults 18 and over." else null) {
            vm.update { OnboardingRules.setNutritionGoal(it, goal) }
        }
    }
}

@Composable
fun MuscleFocusQuestion(vm: OnboardingFlowViewModel) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    MuscleFocus.entries.forEach { f ->
        OnboardingOptionCard(f, vm.answers.muscleFocus == f, showsPhoto = false) { vm.update { OnboardingRules.setMuscleFocus(it, f) } }
    }
    OnboardingNotice("Gaining weight and gaining muscle aren't the same thing — scale weight includes water and fat as well as muscle. Training and enough protein drive the muscle part.")
}

@Composable
fun RecompFocusQuestion(vm: OnboardingFlowViewModel) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    RecompFocus.entries.forEach { f ->
        OnboardingOptionCard(f, vm.answers.recompFocus == f, showsPhoto = false) { vm.update { OnboardingRules.setRecompFocus(it, f) } }
    }
    OnboardingNotice("Recomposition results vary and aren't guaranteed. Track measurements, photos and strength — not just the scale.")
}

private val shortDate = SimpleDateFormat("MMM d", Locale.US)
private val longDate = SimpleDateFormat("MMM d, yyyy", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightGoalQuestion(vm: OnboardingFlowViewModel) {
    val a = vm.answers
    val kg = a.usesKilograms
    val unit = if (kg) "kg" else "lb"
    val skipping = a.skipTargetWeight == true
    var targetText by remember {
        mutableStateOf(a.targetWeightKg?.let { OnboardingRules.formatNumber(Math.rint((if (kg) it else it / OnboardingRules.KG_PER_LB) * 10) / 10) } ?: "")
    }
    var useDate by remember { mutableStateOf(a.targetDate != null) }
    var showPicker by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    fun ensurePace(reset: Boolean = false) {
        val now = vm.answers
        if (now.targetDate != null) return
        val opts = OnboardingRules.paceOptions(now)
        if (reset || opts.none { Math.abs(it.kgPerWeek - (now.weeklyRateKg ?: -1.0)) < 0.0005 }) {
            vm.update { it.copy(weeklyRateKg = opts.firstOrNull()?.kgPerWeek) }
        }
    }
    LaunchedEffect(Unit) { ensurePace() }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GoalCard("Current Weight", "From About You") {
            Text(a.weightKg?.let { OnboardingRules.displayWeight(it, kg) } ?: "Not entered", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = if (a.weightKg == null) FLTheme.colors.textSecondary else FLTheme.colors.textPrimary)
        }
        GoalCard("Target Weight", "Enter a target, or continue without one.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = targetText, enabled = !skipping, singleLine = true, placeholder = { Text("Target weight") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    onValueChange = { v ->
                        targetText = v
                        val value = v.replace(',', '.').toDoubleOrNull()
                        vm.update { x -> x.copy(targetWeightKg = value?.let { if (kg) it else it * OnboardingRules.KG_PER_LB }) }
                        if (useDate) vm.update { x -> OnboardingRules.withTargetDate(x, x.targetDate?.let { MealPlanDay.date(it) }) }
                        ensurePace()
                    },
                    modifier = Modifier.weight(1f)
                )
                Text("  $unit", color = FLTheme.colors.textSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("I don't want to set a target weight", fontSize = 14.sp, color = FLTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                Switch(checked = skipping, onCheckedChange = { skip ->
                    if (skip) { targetText = ""; useDate = false }
                    vm.update { x -> if (skip) x.copy(skipTargetWeight = true, targetWeightKg = null, targetDate = null) else x.copy(skipTargetWeight = false) }
                    ensurePace()
                }, colors = SwitchDefaults.colors(checkedTrackColor = FLPrimary))
            }
        }
        GoalCard("Preferred Pace", "Choose a pace you can sustain.") {
            OnboardingRules.paceOptions(a).forEach { option ->
                val sel = a.targetDate == null && Math.abs((a.weeklyRateKg ?: -1.0) - option.kgPerWeek) < 0.0005
                Row(
                    Modifier.fillMaxWidth().selectableCard(sel, 10.dp) {
                        useDate = false
                        vm.update { it.copy(targetDate = null, weeklyRateKg = option.kgPerWeek) }
                    }.background(FLTheme.colors.background).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(option.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = FLTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                    SelectionIndicator(sel)
                }
            }
            if (!skipping) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("I have a target date in mind", fontSize = 14.sp, color = FLTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                    Switch(checked = useDate, onCheckedChange = { on ->
                        useDate = on
                        if (on) showPicker = true else { vm.update { OnboardingRules.withTargetDate(it, null) }; ensurePace(reset = true) }
                    }, colors = SwitchDefaults.colors(checkedTrackColor = FLPrimary))
                }
                val dateValue = a.targetDate?.let { MealPlanDay.date(it) }
                if (useDate && dateValue != null) {
                    Text("Target date: ${longDate.format(dateValue)}  ·  Change", fontSize = 14.sp, color = FLPrimary,
                        modifier = Modifier.clickable { showPicker = true }.padding(vertical = 4.dp))
                    val rate = a.weeklyRateKg
                    if (rate != null && OnboardingRules.weightGoalError(a) == null) {
                        Text("That works out to about ${OnboardingRules.paceLabel(rate, kg)}.", fontSize = 13.sp, color = FLTheme.colors.textSecondary)
                    }
                }
            }
        }
        GoalCard("Estimated Timeline", null) {
            val est = OnboardingRules.estimatedTimeline(a)
            if (est != null) {
                Text("About ${est.minWeeks}–${est.maxWeeks} weeks", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FLPrimary)
                Text("Roughly ${shortDate.format(est.from)} – ${longDate.format(est.to)}", fontSize = 14.sp, color = FLTheme.colors.textSecondary)
            } else {
                Text(if (skipping) "No target weight, so there's no finish date — your plan follows the pace you choose." else "Enter a target weight and pace to see an estimate.",
                    fontSize = 14.sp, color = FLTheme.colors.textSecondary)
            }
            Text("Estimates assume steady progress; real progress usually slows over time and varies week to week. They are not guarantees.",
                fontSize = 12.sp, color = FLTheme.colors.textSecondary)
        }
        OnboardingRules.weightGoalError(a)?.let { OnboardingNotice(it, warning = true) }
    }

    if (showPicker) {
        val minMillis = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }.timeInMillis
        val state = rememberDatePickerState(
            initialSelectedDateMillis = a.targetDate?.let { MealPlanDay.date(it)?.time } ?: Calendar.getInstance().apply { add(Calendar.MONTH, 3) }.timeInMillis,
            selectableDates = object : SelectableDates { override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= minMillis }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false; if (vm.answers.targetDate == null) useDate = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { millis -> vm.update { OnboardingRules.withTargetDate(it, Date(millis + 12 * 3_600_000L)) } }
                    showPicker = false
                }) { Text("Set Date") }
            },
            dismissButton = { TextButton({ showPicker = false; if (vm.answers.targetDate == null) useDate = false }) { Text("Cancel") } }
        ) { DatePicker(state) }
    }
}

@Composable
private fun GoalCard(title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FLTheme.colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = FLTheme.colors.textSecondary)
        }
        content()
    }
}

// MARK: Q12 — dietary

@Composable
fun DietaryQuestion(vm: OnboardingFlowViewModel) {
    var otherOn by remember { mutableStateOf(!vm.answers.otherRestrictions.isNullOrEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OnboardingSectionHeader("Dietary Preference", "Choose the eating style that best fits your lifestyle.", Icons.Filled.Restaurant)
        TwoColumnGrid(DietaryPreferenceChoice.entries) { pref, mod ->
            val sel = vm.answers.dietaryPreference == pref
            Row(
                mod.height(IntrinsicSize.Min).heightIn(min = 96.dp).selectableCard(sel, 12.dp) { vm.update { it.copy(dietaryPreference = pref) } }.background(FLTheme.colors.surface),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(pref.icon, null, tint = FLPrimary, modifier = Modifier.size(18.dp))
                    Text(pref.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary, maxLines = 1)
                    Text(pref.subtitle, fontSize = 11.sp, color = FLTheme.colors.textSecondary)
                }
                pref.photo?.let { Image(painterResource(it), null, contentScale = ContentScale.Crop, modifier = Modifier.width(50.dp).fillMaxHeight()) }
            }
        }
        OnboardingSectionHeader("Allergies & Restrictions", "Select all that apply.", Icons.Filled.Shield)
        TwoColumnGrid(AllergyChoices + "Other", spacing = 8) { name, mod ->
            val sel = if (name == "Other") otherOn else name in vm.answers.allergies
            OnboardingCompactTile(name, null, sel, mod) {
                if (name == "Other") {
                    otherOn = !otherOn
                    if (!otherOn) vm.update { it.copy(otherRestrictions = null) }
                } else vm.update { it.copy(allergies = OnboardingRules.toggled(it.allergies, name)) }
            }
        }
        if (otherOn) {
            OutlinedTextField(
                value = vm.answers.otherRestrictions ?: "",
                onValueChange = { v -> vm.update { it.copy(otherRestrictions = v) } },
                placeholder = { Text("Other allergies, restrictions, or ingredients to avoid") },
                modifier = Modifier.fillMaxWidth(), maxLines = 3
            )
        }
        OnboardingNotice("We use this to personalize your nutrition experience. It doesn't change your calorie targets, and you can update it anytime.")
    }
}
