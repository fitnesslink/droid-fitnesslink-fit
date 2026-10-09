package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.model.NutritionDayTitle
import com.fitnesslink.fit.model.api.Micronutrient
import com.fitnesslink.fit.network.dto.NutritionTargets
import com.fitnesslink.fit.nutrition.DisplayedNutrientsStore
import com.fitnesslink.fit.nutrition.NutritionGoalsStore
import com.fitnesslink.fit.ui.components.HeaderView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.navigation.FoodLoggingRoutes
import com.fitnesslink.fit.ui.navigation.NutritionCoreRoutes
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.NutritionViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The nutrition dashboard (iOS `NutritionDashboardView`, FTNSSLNK-193;
 * route `calorieTracking`). Section order, top to bottom:
 *
 *   1. Date navigation (chevrons + calendar sheet) and settings gear
 *   2. Context banner (backfill on past days / planning on future days)
 *   3. Weekly strip with completeness dots (range summary)
 *   4. Onboarding CTA (before a nutrition profile exists)
 *   5. Calorie ring, 6. Macro bars, 7. Micronutrients card
 *   8. Water tile (`HydrationCardView`, agent C; hidden on future days)
 *   9. Quick actions, 10. Logged meals, 11. Planned meals
 *  12. Goals tile with provenance, 13. Shortcuts
 *
 * Existing callbacks keep their names for `AppNavigation.kt`; [onNavigate]
 * carries every route-string navigation the new sections need and defaults
 * to `AppRouter.push` so it works before the integrator wires it.
 */
@Composable
fun NutritionScreen(
    onNavigateToGoalSettings: () -> Unit = {},
    onNavigateToQuickAdd: (String) -> Unit = {},
    onNavigateToFoodEntryDetail: (String) -> Unit = {},
    onNavigateToRecentFoods: () -> Unit = {},
    onNavigateToWeeklyProgress: () -> Unit = {},
    onNavigateToBarcodeScanner: (String) -> Unit = {},
    onNavigate: (String) -> Unit = { AppRouter.push(it) },
    viewModel: NutritionViewModel = viewModel()
) {
    val goalsState by NutritionGoalsStore.state.collectAsState()
    val targets by NutritionGoalsStore.targets.collectAsState()
    val displayedMicroKeys by DisplayedNutrientsStore.displayed.collectAsState()
    val displayedMicros = remember(displayedMicroKeys) { Micronutrient.ordered(displayedMicroKeys) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMMM d", Locale.getDefault()) }
    val shortDateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val weekdayFormat = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        viewModel.loadData()
        NutritionGoalsStore.refresh()
        DisplayedNutrientsStore.refresh()
    }

    val dayKey = viewModel.selectedDayKey
    val dim = if (viewModel.isViewingFutureDay) 0.55f else 1f

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderView()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Date navigation + settings
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircleIconButton(Icons.Default.ChevronLeft, "Previous day") { viewModel.goToPreviousDay() }
                Column(modifier = Modifier.clickable { showDatePicker = true }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(NutritionDayTitle.title(viewModel.selectedDate) { weekdayFormat.format(it) }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Pick a day", tint = FLPrimary, modifier = Modifier.size(16.dp))
                    }
                    Text(dateFormat.format(viewModel.selectedDate), fontSize = 14.sp, color = TextSecondaryColor)
                }
                CircleIconButton(Icons.Default.ChevronRight, "Next day") { viewModel.goToNextDay() }
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.Settings, contentDescription = "Goals", tint = FLPrimary, modifier = Modifier.size(24.dp).clickable(onClick = onNavigateToGoalSettings))
            }

            // 2. Context banners
            if (viewModel.isViewingFutureDay) {
                ContextBanner(Icons.Default.Schedule, "Planning view — you can log foods once the day arrives") { viewModel.goToToday() }
            } else if (!viewModel.isViewingToday) {
                ContextBanner(Icons.Default.History, "Viewing ${shortDateFormat.format(viewModel.selectedDate)} — new foods will be logged to this day") { viewModel.goToToday() }
            }

            // 3. Week strip
            NutritionWeekStrip(
                weekDates = viewModel.weekDates,
                selectedDate = viewModel.selectedDate,
                dotState = { viewModel.dotState(it) },
                onSelect = { viewModel.select(it) },
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // 4. Onboarding CTA
            if (goalsState == NutritionGoalsStore.State.NO_PROFILE && targets == null) {
                TileRow(
                    icon = Icons.Default.AutoAwesome, iconColor = FLPrimary,
                    title = "Build your nutrition plan", subtitle = "Answer a few questions to get personalized targets",
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) { onNavigate(NutritionCoreRoutes.ONBOARDING) }
            }

            // 5. Calorie ring
            Column(
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().alpha(dim).background(White, RoundedCornerShape(12.dp)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CalorieRingView(
                    consumed = viewModel.displayedCalories, goal = viewModel.effectiveCalorieGoal,
                    remaining = viewModel.remainingCalories, progress = viewModel.calorieProgress
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    RingStat("${viewModel.effectiveCalorieGoal}", "Goal")
                    RingStat("${viewModel.displayedCalories}", "Eaten")
                    val over = viewModel.remainingCalories < 0
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${if (over) -viewModel.remainingCalories else viewModel.remainingCalories}",
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (over) NutritionOverColor else FLPrimary
                        )
                        Text(if (over) "Over" else "Remaining", fontSize = 11.sp, color = TextSecondaryColor)
                    }
                }
            }

            // 6. Macro bars
            Column(
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().alpha(dim).background(White, RoundedCornerShape(12.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Macros", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                MacroBarView("Protein", viewModel.proteinLine, BlueTheme)
                MacroBarView("Fat", viewModel.fatLine, OrangeTheme)
                MacroBarView("Carbs", viewModel.carbsLine, PurpleTheme)
            }

            // 7. Micronutrients
            if (displayedMicros.isNotEmpty()) {
                MicronutrientCard(
                    micros = displayedMicros,
                    lineFor = { viewModel.microLine(it) },
                    onEdit = { onNavigate(NutritionCoreRoutes.DISPLAY_SETTINGS) },
                    modifier = Modifier.padding(horizontal = 20.dp).alpha(dim)
                )
            }

            // 8. Water tile (agent C) — hidden on future days
            if (!viewModel.isViewingFutureDay) {
                HydrationCardView(date = dayKey, modifier = Modifier.padding(horizontal = 20.dp))
            }

            // 9. Quick actions
            if (viewModel.canLogToSelectedDay) {
                val meal = NutritionViewModel.inferredMealType()
                Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickAction(Icons.Default.Search, "Log Food", Modifier.weight(1f)) { onNavigate(FoodLoggingRoutes.foodSearch(meal, dayKey)) }
                    QuickAction(Icons.Default.QrCodeScanner, "Scan", Modifier.weight(1f)) { onNavigateToBarcodeScanner(meal.name) }
                    QuickAction(Icons.Default.WaterDrop, "Add Water", Modifier.weight(1f)) { onNavigate("logWater") }
                }
            }

            // Day-one nudge
            if (viewModel.isViewingToday && viewModel.entries.isEmpty() && viewModel.waterTotalDisplay == 0.0) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
                    Text("Nothing logged yet — add your first food or water and your day fills in here.", fontSize = 13.sp, color = TextSecondaryColor)
                }
            }

            // 10. Logged meals
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Meals", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, modifier = Modifier.padding(horizontal = 20.dp))
                viewModel.quickLogError?.let { error ->
                    Row(modifier = Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = OrangeTheme, modifier = Modifier.size(14.dp))
                        Text(error, fontSize = 12.sp, color = OrangeTheme)
                    }
                }
                MealType.entries.forEach { meal ->
                    val source = viewModel.quickLogSource(meal)
                    MealSectionView(
                        mealType = meal,
                        entries = viewModel.entriesForMeal(meal),
                        totalCalories = viewModel.caloriesForMeal(meal),
                        allowsLogging = viewModel.canLogToSelectedDay,
                        onAdd = { onNavigate(FoodLoggingRoutes.foodSearch(meal, dayKey)) },
                        onScan = { onNavigateToBarcodeScanner(meal.name) },
                        onSnap = { onNavigate(FoodLoggingRoutes.snapMeal(meal, dayKey)) },
                        onEntryTap = { entry ->
                            onNavigate(FoodLoggingRoutes.foodDetail(entry.foodId ?: entry.id, meal, dayKey, mode = "edit:${entry.id}"))
                        },
                        modifier = Modifier.padding(horizontal = 20.dp),
                        quickLogSource = source,
                        quickLogLabel = source?.let { NutritionViewModel.quickLogLabel(it.sourceDate, dayKey) } ?: "",
                        isQuickLogging = meal in viewModel.quickLogInFlight,
                        onQuickLog = { viewModel.performQuickLog(meal) }
                    )
                }
            }

            // 11. Planned meals
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Planned Meals", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                    Spacer(modifier = Modifier.weight(1f))
                    Row(modifier = Modifier.clickable { onNavigate("mealPlan") }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Planner", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
                    }
                }
                if (viewModel.plannedSlots.isEmpty()) {
                    TileRow(
                        icon = Icons.Default.EventNote, iconColor = FLPrimary,
                        title = "No meals planned for this day", subtitle = "Open the planner to add some.",
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) { onNavigate("mealPlan") }
                } else {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(White, RoundedCornerShape(12.dp))) {
                        viewModel.plannedSlots.forEachIndexed { index, slot ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onNavigate("mealSlotDetail/${slot.id}") }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(slot.recipeName.ifEmpty { slot.mealType.displayName }, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                                    Text(slot.mealType.displayName, fontSize = 12.sp, color = TextSecondaryColor)
                                }
                                Text("${slot.calories} cal", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(14.dp))
                            }
                            if (index < viewModel.plannedSlots.size - 1) SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            // 12. Goals tile
            targets?.let { t -> GoalsTile(t, Modifier.padding(horizontal = 20.dp), onNavigateToGoalSettings) }

            // 13. Shortcuts
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Shortcut("Recent", Modifier.weight(1f), painterRes = R.drawable.hourglass, onClick = onNavigateToRecentFoods)
                    Shortcut("Weekly", Modifier.weight(1f), icon = Icons.Default.BarChart, onClick = onNavigateToWeeklyProgress)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Shortcut("Groceries", Modifier.weight(1f), icon = Icons.Default.ShoppingCart) { onNavigate("groceryLists") }
                    Shortcut("Analytics", Modifier.weight(1f), icon = Icons.Default.ShowChart) { onNavigate(NutritionCoreRoutes.ANALYTICS) }
                }
            }
        }
    }

    if (showDatePicker) {
        NutritionCalendarSheet(
            selectedDate = viewModel.selectedDate,
            onDateSelected = { viewModel.select(it) },
            onDismiss = { showDatePicker = false }
        )
    }
}

// MARK: - Pieces

@Composable
private fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(modifier = Modifier.size(32.dp).background(White, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = FLPrimary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ContextBanner(icon: ImageVector, text: String, onToday: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(FLPrimary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 13.sp, color = FLPrimary, modifier = Modifier.weight(1f))
        Text("Today", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary, modifier = Modifier.clickable(onClick = onToday))
    }
}

@Composable
private fun RingStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        Text(label, fontSize = 11.sp, color = TextSecondaryColor)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.background(White, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = title, tint = FLPrimary, modifier = Modifier.size(22.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
    }
}

@Composable
private fun TileRow(icon: ImageVector, iconColor: Color, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(40.dp).background(iconColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Text(subtitle, fontSize = 12.sp, color = TextSecondaryColor)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(16.dp))
    }
}

/** Targets + provenance at a glance (iOS `goalsTile`). */
@Composable
private fun GoalsTile(targets: NutritionTargets, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(40.dp).background(OrangeTheme.copy(alpha = 0.12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.TrackChanges, contentDescription = null, tint = OrangeTheme, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Goals", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                ProvenanceBadge(isManual = targets.isManualOverride, fontSize = 10)
            }
            Text(
                "${Math.round(targets.dailyCalorieTarget)} cal · P ${Math.round(targets.proteinGrams)}g · C ${Math.round(targets.carbGrams)}g · F ${Math.round(targets.fatGrams)}g",
                fontSize = 12.sp, color = TextSecondaryColor
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun Shortcut(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null, painterRes: Int? = null, onClick: () -> Unit) {
    Row(
        modifier = modifier.background(White, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Icon(icon, contentDescription = title, tint = FLPrimary, modifier = Modifier.size(16.dp))
        else if (painterRes != null) Image(painter = painterResource(painterRes), contentDescription = title, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
    }
}
