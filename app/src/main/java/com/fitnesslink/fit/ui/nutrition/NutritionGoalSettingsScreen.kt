package com.fitnesslink.fit.ui.nutrition

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.NutritionGoal
import com.fitnesslink.fit.model.api.NutritionGoalTimeline
import com.fitnesslink.fit.model.api.activityLevelValue
import com.fitnesslink.fit.model.api.primaryGoalValue
import com.fitnesslink.fit.model.api.timelineValue
import com.fitnesslink.fit.model.api.workoutFrequencyValue
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.dto.NutritionProfile
import com.fitnesslink.fit.network.dto.NutritionTargets
import com.fitnesslink.fit.nutrition.NutritionGoalsStore
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.navigation.NutritionCoreRoutes
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.NutritionProfileValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The Goals hub (iOS `NutritionGoalsView`, FTNSSLNK-191) — the single
 * surface for viewing and editing nutrition targets (route
 * `nutritionGoalSettings`).
 *
 * - No profile yet → onboarding call-to-action plus a manual-targets escape
 *   hatch for users who just want numbers.
 * - Profile exists → current targets with provenance badge ("Calculated" vs
 *   "Manually set"), per-number "why" explanations, profile edit entry,
 *   manual override, and revert-to-calculated.
 *
 * All changes land in `NutritionGoalsStore`, so rings/bars app-wide pick
 * the new targets up immediately. The manual override sheet keeps the
 * Android-only local TDEE calculator as a fill-in helper.
 */
@Composable
fun NutritionGoalSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = { AppRouter.push(it) }
) {
    val scope = rememberCoroutineScope()
    val state by NutritionGoalsStore.state.collectAsState()
    val targets by NutritionGoalsStore.targets.collectAsState()
    val profile by NutritionGoalsStore.profile.collectAsState()
    var showOverrideSheet by remember { mutableStateOf(false) }
    var isReverting by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var actionNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { NutritionGoalsStore.refresh() }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your Targets", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                    targets?.let { ProvenanceBadge(isManual = it.isManualOverride) }
                }
                targets?.effectiveFrom?.takeIf { it.isNotEmpty() }?.let { MealPlanDay.date(it) }?.let { date ->
                    Text("In effect since ${SimpleDateFormat("MMM d", Locale.getDefault()).format(date)}", fontSize = 13.sp, color = TextSecondaryColor)
                }
            }

            actionError?.let { NutritionValidationBanner(it) }
            actionNotice?.let { notice ->
                Text(notice, fontSize = 13.sp, color = FLPrimary, modifier = Modifier.fillMaxWidth().background(FLPrimary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(12.dp))
            }

            val t = targets
            when (state) {
                NutritionGoalsStore.State.UNKNOWN, NutritionGoalsStore.State.LOADING -> {
                    if (t != null) LoadedContent(t, profile, isReverting, onOverride = { showOverrideSheet = true }, onRevert = {
                        scope.launch { isReverting = true; actionError = null
                            try { NutritionGoalsStore.revertToCalculated() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                                actionError = "Couldn't revert to calculated targets. Check your connection and try again."
                            } finally { isReverting = false }
                        }
                    }, onEditProfile = { onNavigate(NutritionCoreRoutes.PROFILE_EDIT) })
                    else Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FLPrimary)
                    }
                }
                NutritionGoalsStore.State.LOADED -> {
                    if (t != null) LoadedContent(t, profile, isReverting, onOverride = { showOverrideSheet = true }, onRevert = {
                        scope.launch { isReverting = true; actionError = null
                            try { NutritionGoalsStore.revertToCalculated() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                                actionError = "Couldn't revert to calculated targets. Check your connection and try again."
                            } finally { isReverting = false }
                        }
                    }, onEditProfile = { onNavigate(NutritionCoreRoutes.PROFILE_EDIT) })
                }
                NutritionGoalsStore.State.NO_PROFILE -> {
                    // Manual-only users (override without onboarding) still have targets to show and re-override.
                    if (t != null) {
                        NutritionTargetsSummaryView(targets = t)
                        OnboardingPrompt { onNavigate(NutritionCoreRoutes.ONBOARDING) }
                        NutritionActionRow(Icons.Default.Tune, "Override Targets Manually", "Set your own calories and macros") { showOverrideSheet = true }
                    } else {
                        OnboardingPrompt { onNavigate(NutritionCoreRoutes.ONBOARDING) }
                        NutritionActionRow(Icons.Default.Tune, "Set Targets Manually", "Skip the questions and enter your own numbers") { showOverrideSheet = true }
                    }
                }
                NutritionGoalsStore.State.FAILED -> {
                    if (t != null) {
                        NutritionTargetsSummaryView(targets = t)
                        NutritionActionRow(Icons.Default.Tune, "Override Targets Manually", "Set your own calories and macros") { showOverrideSheet = true }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Couldn't load your goals. Check your connection.", fontSize = 14.sp, color = TextSecondaryColor)
                            Row(modifier = Modifier.clickable { scope.launch { NutritionGoalsStore.refresh() } }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
                                Text("Retry", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                            }
                        }
                        NutritionActionRow(Icons.Default.Tune, "Set Targets Manually", "Works offline — saved locally and synced later") { showOverrideSheet = true }
                    }
                }
            }

            // Display & Units (FTNSSLNK-217/221)
            NutritionActionRow(Icons.Default.ViewList, "Display & Units", "Micronutrients shown and water unit") {
                onNavigate(NutritionCoreRoutes.DISPLAY_SETTINGS)
            }
        }
    }

    if (showOverrideSheet) {
        NutritionTargetsOverrideSheet(
            currentTargets = targets,
            onDismiss = { showOverrideSheet = false },
            onSaved = { savedLocallyOnly ->
                showOverrideSheet = false
                actionNotice = if (savedLocallyOnly) "Saved on this device — your targets will sync once you're back online." else null
            }
        )
    }
}

@Composable
private fun LoadedContent(
    targets: NutritionTargets,
    profile: NutritionProfile?,
    isReverting: Boolean,
    onOverride: () -> Unit,
    onRevert: () -> Unit,
    onEditProfile: () -> Unit
) {
    NutritionTargetsSummaryView(targets = targets)
    if (targets.isManualOverride) {
        Text("These targets were set manually and override your calculated plan.", fontSize = 13.sp, color = TextSecondaryColor)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NutritionActionRow(Icons.Default.Tune, "Override Targets Manually", "Set your own calories and macros", onClick = onOverride)
        if (targets.isManualOverride && profile != null) {
            if (isReverting) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FLPrimary, modifier = Modifier.size(22.dp), strokeWidth = 2.dp) }
            } else {
                NutritionActionRow(Icons.Default.Refresh, "Revert to Calculated Targets", "Recalculate from your profile", onClick = onRevert)
            }
        }
    }
    if (profile != null) ProfileSection(profile, onEditProfile)
}

@Composable
private fun ProfileSection(profile: NutritionProfile, onEditProfile: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Profile", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.padding(top = 4.dp))
        NutritionFieldCard {
            ProfileRow("Goal", profile.primaryGoalValue?.displayName ?: profile.primaryGoal)
            SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
            ProfileRow("Timeline", timelineText(profile))
            SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
            ProfileRow("Current Weight", String.format(Locale.US, "%.1f kg", profile.currentWeightKg))
            SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
            ProfileRow("Goal Weight", String.format(Locale.US, "%.1f kg", profile.goalWeightKg))
            SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
            ProfileRow("Activity", profile.activityLevelValue?.displayName ?: profile.activityLevel)
            SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
            ProfileRow("Workouts", profile.workoutFrequencyValue?.displayName?.let { "$it/week" } ?: profile.workoutFrequency)
        }
        NutritionPrimaryButton(text = "Edit Profile & Recalculate", enabled = true, onClick = onEditProfile)
        Text("Any profile change recalculates your targets. Past days keep the targets that were in effect on that day.", fontSize = 12.sp, color = TextSecondaryColor)
    }
}

private fun timelineText(profile: NutritionProfile): String {
    val timeline = profile.timelineValue ?: return profile.timeline
    if (timeline == NutritionGoalTimeline.CUSTOM) {
        profile.timelineCustomDate?.let { MealPlanDay.date(it) }?.let { return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(it) }
    }
    return timeline.displayName
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = TextSecondaryColor)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
    }
}

@Composable
fun ProvenanceBadge(isManual: Boolean, fontSize: Int = 11) {
    val color = if (isManual) OrangeTheme else FLPrimary
    Text(
        if (isManual) "Manually set" else "Calculated",
        fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold, color = color,
        modifier = Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun OnboardingPrompt(onStart: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(22.dp))
            Text("Build your nutrition plan", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        }
        Text(
            "Answer a few questions and we'll calculate your maintenance calories, daily target, and macros — with an explanation for every number.",
            fontSize = 13.sp, color = TextSecondaryColor
        )
        Box(
            modifier = Modifier.fillMaxWidth().background(FLPrimary, RoundedCornerShape(10.dp)).clickable(onClick = onStart).padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) { Text("Get Started", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = White) }
    }
}

/** Icon + title + subtitle + chevron action row (iOS `actionRow`). */
@Composable
fun NutritionActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(36.dp).background(FLPrimary.copy(alpha = 0.12f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Text(subtitle, fontSize = 12.sp, color = TextSecondaryColor)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(18.dp))
    }
}

// MARK: - Manual override sheet (+ Android's local TDEE calculator)

/**
 * Manual override editor (iOS `NutritionTargetsOverrideSheet`): exact
 * calories + macros via `POST /profile/targets/override` (provenance =
 * Manual, prior goal retained as history). Offline, the numbers are saved
 * to the legacy local goal row and queued to `/nutrition-goals` so the
 * dashboard still updates. The TDEE expander fills the calorie field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionTargetsOverrideSheet(
    currentTargets: NutritionTargets?,
    onDismiss: () -> Unit,
    onSaved: (savedLocallyOnly: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val localGoal by NutritionGoalsStore.goal.collectAsState()
    var calories by remember { mutableStateOf(currentTargets?.let { NutritionProfileValidation.formatNumber(Math.rint(it.dailyCalorieTarget)) } ?: localGoal.calorieGoal.toString()) }
    var protein by remember { mutableStateOf(currentTargets?.let { NutritionProfileValidation.formatNumber(Math.rint(it.proteinGrams)) } ?: localGoal.proteinTarget.toString()) }
    var fat by remember { mutableStateOf(currentTargets?.let { NutritionProfileValidation.formatNumber(Math.rint(it.fatGrams)) } ?: localGoal.fatTarget.toString()) }
    var carbs by remember { mutableStateOf(currentTargets?.let { NutritionProfileValidation.formatNumber(Math.rint(it.carbGrams)) } ?: localGoal.carbsTarget.toString()) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val cal = calories.toDoubleOrNull()
    val p = protein.toDoubleOrNull()
    val f = fat.toDoubleOrNull()
    val c = carbs.toDoubleOrNull()
    val isValid = cal != null && cal > 0 && p != null && p >= 0 && f != null && f >= 0 && c != null && c >= 0

    fun save() {
        if (!isValid || cal == null || p == null || f == null || c == null) return
        isSaving = true
        errorMessage = null
        scope.launch {
            try {
                NutritionGoalsStore.overrideTargets(Math.round(cal).toInt(), p, f, c)
                onSaved(false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError.Offline) {
                saveLocally(cal, p, f, c, onSaved)
            } catch (e: ApiError.Timeout) {
                saveLocally(cal, p, f, c, onSaved)
            } catch (e: ApiError.ServerError) {
                errorMessage = NutritionProfileValidation.serverErrorText(e, "Couldn't save the override. Please try again.")
            } catch (e: Exception) {
                errorMessage = "Couldn't save the override. Please try again."
            } finally {
                isSaving = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BackgroundColor) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Override Targets", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            Text(
                "These numbers replace your calculated targets and are marked as manually set. You can revert to the calculated plan anytime.",
                fontSize = 13.sp, color = TextSecondaryColor
            )
            NutritionFieldCard {
                NutritionNumberRow("Daily Calories", calories, "cal", { calories = it })
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                NutritionNumberRow("Protein", protein, "g", { protein = it })
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                NutritionNumberRow("Fat", fat, "g", { fat = it })
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                NutritionNumberRow("Carbs", carbs, "g", { carbs = it })
            }
            TdeeCalculatorCard(onUse = { tdee -> calories = tdee.toString() })
            errorMessage?.let { NutritionValidationBanner(it) }
            NutritionPrimaryButton(text = "Save Override", enabled = isValid && !isSaving, loading = isSaving) { save() }
        }
    }
}

private suspend fun saveLocally(cal: Double, p: Double, f: Double, c: Double, onSaved: (Boolean) -> Unit) {
    val existing = NutritionGoalsStore.goal.value
    NutritionGoalsStore.saveManualGoal(
        NutritionGoal(
            id = existing.id,
            calorieGoal = Math.round(cal).toInt(),
            proteinTarget = Math.round(p).toInt(),
            fatTarget = Math.round(f).toInt(),
            carbsTarget = Math.round(c).toInt()
        )
    )
    onSaved(true)
}

/** Android-only Mifflin-St Jeor helper: estimates maintenance calories and offers to fill the calorie field. */
@Composable
private fun TdeeCalculatorCard(onUse: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var weight by remember { mutableStateOf("") }
    var heightCm by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var isMale by remember { mutableStateOf(true) }
    var activityIndex by remember { mutableIntStateOf(2) }
    var calculated by remember { mutableStateOf<Int?>(null) }
    val activityLevels = listOf("Sedentary", "Lightly Active", "Moderately Active", "Very Active", "Extra Active")
    val multipliers = listOf(1.2, 1.375, 1.55, 1.725, 1.9)

    Column(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Text("TDEE Calculator", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Spacer(modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = TextSecondaryColor)
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().background(BackgroundColor, RoundedCornerShape(8.dp))) {
                    NutritionNumberRow("Weight", weight, "lbs", { weight = it })
                    SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                    NutritionNumberRow("Height", heightCm, "cm", { heightCm = it })
                    SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                    NutritionNumberRow("Age", age, "yrs", { age = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NutritionSelectablePill("Male", isMale, Modifier.weight(1f)) { isMale = true }
                    NutritionSelectablePill("Female", !isMale, Modifier.weight(1f)) { isMale = false }
                }
                Text("Activity Level", fontSize = 12.sp, color = TextSecondaryColor)
                activityLevels.forEachIndexed { index, level ->
                    Row(modifier = Modifier.clickable { activityIndex = index }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(14.dp).background(if (activityIndex == index) FLPrimary else BackgroundColor, RoundedCornerShape(7.dp)))
                        Text(level, fontSize = 14.sp, color = TextPrimaryColor)
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().background(FLPrimary, RoundedCornerShape(8.dp)).clickable {
                        val w = weight.toDoubleOrNull() ?: return@clickable
                        val h = heightCm.toDoubleOrNull() ?: return@clickable
                        val a = age.toDoubleOrNull() ?: return@clickable
                        calculated = TdeeMath.tdee(weightLbs = w, heightCm = h, age = a, isMale = isMale, multiplier = multipliers[activityIndex])
                    }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Calculate", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = White) }
                calculated?.let { tdee ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Estimated TDEE: ", fontSize = 14.sp, color = TextSecondaryColor)
                        Text("$tdee cal/day", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FLPrimary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("Use", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary, modifier = Modifier.clickable { onUse(tdee); calculated = null })
                    }
                }
            }
        }
    }
}

/** Mifflin-St Jeor maintenance estimate (pure, unit-tested). */
object TdeeMath {
    fun bmr(weightKg: Double, heightCm: Double, age: Double, isMale: Boolean): Double =
        10 * weightKg + 6.25 * heightCm - 5 * age + (if (isMale) 5 else -161)

    fun tdee(weightLbs: Double, heightCm: Double, age: Double, isMale: Boolean, multiplier: Double): Int =
        (bmr(weightLbs * NutritionProfileValidation.KG_PER_LB, heightCm, age, isMale) * multiplier).toInt()
}
