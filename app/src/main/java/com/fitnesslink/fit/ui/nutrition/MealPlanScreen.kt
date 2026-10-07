package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.CopyMealSlotDayMode
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.MealSlot
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.model.PlannedDayTargetState
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.dto.ApplyMealPlanWeekResult
import com.fitnesslink.fit.network.dto.CopyMealSlotDayResult
import com.fitnesslink.fit.network.dto.CopyMealSlotWeekResult
import com.fitnesslink.fit.nutrition.GroceryStore
import com.fitnesslink.fit.nutrition.MealBuilderStore
import com.fitnesslink.fit.nutrition.MealPlanStore
import com.fitnesslink.fit.ui.navigation.MealPlanRoutes
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.DisabledButton
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.ProgressBG
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.MealPlanViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Multi-week meal planner (port of iOS `MealPlanView`): Sunday-start
 * weeks with prev/next + "Jump to this week", per-day planned calories vs
 * target (over-target is an amber signal, never an error), "X cal left to
 * plan", copy last week (overwrite / keep mine), copy a day onto other
 * days (merge / replace), AI week ideas, and range → grocery list.
 *
 * [onNavigateToMealSlotDetail] / [onNavigateToNutritionSummary] /
 * [onNavigateToGroceryList] are the pre-Wave-3 callbacks `AppNavigation`
 * still passes; [onNavigate] (route string) unlocks the new routes and wins
 * when provided.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MealPlanScreen(
    onBack: () -> Unit,
    onNavigateToMealSlotDetail: (String, String) -> Unit = { _, _ -> },
    onNavigateToNutritionSummary: () -> Unit = {},
    onNavigateToGroceryList: () -> Unit = {},
    onNavigate: ((String) -> Unit)? = null
) {
    val viewModel: MealPlanViewModel = viewModel()
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showCopyConfirm by remember { mutableStateOf(false) }
    var showCopyDay by remember { mutableStateOf(false) }
    var showGenerate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadData() }

    fun openBuilder(date: String, meal: MealType, slotId: String?) {
        MealBuilderStore.activeSlotId = slotId
        if (onNavigate != null) onNavigate(MealPlanRoutes.mealBuilder(date, meal, slotId))
        else onNavigateToMealSlotDetail(date, meal.name)
    }

    fun openIdeas() {
        if (onNavigate != null) onNavigate(MealPlanRoutes.mealPlanIdeas(viewModel.weekStart))
        else viewModel.errorMessage = "Meal Plan Ideas needs the Wave 3 routes wired in."
    }

    fun attemptCopyLastWeek() {
        if (!viewModel.previousWeekHasSlots) return
        if (viewModel.isWeekEmpty) viewModel.copyPreviousWeek(overwrite = false) else showCopyConfirm = true
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        PlanTopBar(title = "Meal Plan", onBack = onBack) {
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = TextPrimaryColor) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Meal Plan Ideas") }, leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, tint = PurpleTheme) },
                        onClick = { showMenu = false; openIdeas() })
                    DropdownMenuItem(text = { Text("Generate Grocery List") }, leadingIcon = { Icon(Icons.Filled.ShoppingCart, null) },
                        onClick = { showMenu = false; showGenerate = true })
                    DropdownMenuItem(text = { Text("Copy last week") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                        enabled = viewModel.previousWeekHasSlots, onClick = { showMenu = false; attemptCopyLastWeek() })
                    DropdownMenuItem(text = { Text("Copy this day") }, leadingIcon = { Icon(Icons.Filled.EventRepeat, null) },
                        enabled = !viewModel.isDayEmpty(viewModel.selectedDate), onClick = { showMenu = false; showCopyDay = true })
                }
            }
        }

        WeekHeader(viewModel)

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.weekDates) { date ->
                DayTab(
                    date = date,
                    isSelected = date == viewModel.selectedDate,
                    calories = viewModel.caloriesForDate(date),
                    isOver = viewModel.targetState(date).isOver,
                    onClick = { viewModel.selectedDate = date },
                    onLongPress = { if (!viewModel.isDayEmpty(date)) { viewModel.selectedDate = date; showCopyDay = true } }
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DailyCalorieHeader(viewModel)

            if (viewModel.isWeekEmpty && viewModel.previousWeekHasSlots) {
                PlanActionCard(Icons.Filled.ContentCopy, FLPrimary, "Copy last week's plan", "Start from the meals you had last week") { attemptCopyLastWeek() }
            }
            if (viewModel.isWeekEmpty) {
                PlanActionCard(Icons.Filled.AutoAwesome, PurpleTheme, "Meal plan ideas", "Describe what you like and get a week of ideas to review") { openIdeas() }
            }
            if (viewModel.isDayEmpty(viewModel.selectedDate) && !viewModel.isWeekEmpty) {
                EmptyDayPrompt(MealPlanViewModel.weekdayFull(viewModel.selectedDate))
            }

            viewModel.lastCopyResult?.let { PlanBanner(Icons.Filled.CheckCircle, FLPrimary, copyResultMessage(it)) { viewModel.lastCopyResult = null } }
            viewModel.lastCopyDayResult?.let { PlanBanner(Icons.Filled.CheckCircle, FLPrimary, copyDayResultMessage(it)) { viewModel.lastCopyDayResult = null } }
            viewModel.lastIdeasResult?.let { PlanBanner(Icons.Filled.AutoAwesome, PurpleTheme, ideasResultMessage(it, viewModel.weekRangeLabel)) { viewModel.lastIdeasResult = null } }
            viewModel.errorMessage?.let { PlanBanner(Icons.Filled.Close, OrangeTheme, it) { viewModel.errorMessage = null } }

            MealType.entries.forEach { meal ->
                MealSlotCard(
                    meal = meal,
                    slots = viewModel.slotsFor(viewModel.selectedDate, meal),
                    onOpen = { slot -> openBuilder(viewModel.selectedDate, meal, slot.id) },
                    onAdd = { openBuilder(viewModel.selectedDate, meal, null) }
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlanActionButton(Icons.Filled.PieChart, "Summary", Modifier.weight(1f)) {
                    if (onNavigate != null) onNavigate(MealPlanRoutes.nutritionSummary(viewModel.selectedDate)) else onNavigateToNutritionSummary()
                }
                PlanActionButton(Icons.Filled.ShoppingCart, "Grocery Lists", Modifier.weight(1f)) {
                    if (onNavigate != null) onNavigate(MealPlanRoutes.GROCERY_LISTS) else onNavigateToGroceryList()
                }
            }
            PlanActionButton(Icons.Filled.AddCircle, "Generate Grocery List", Modifier.padding(horizontal = 20.dp)) { showGenerate = true }
        }
    }

    if (showCopyConfirm) {
        AlertDialog(
            onDismissRequest = { showCopyConfirm = false },
            title = { Text("Copy last week into ${viewModel.weekRangeLabel}?") },
            text = { Text("This week already has meals. Overwrite the ones that clash with last week's, or keep yours and only add last week's meals that don't clash.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { showCopyConfirm = false; viewModel.copyPreviousWeek(overwrite = true) }) { Text("Overwrite matching meals", color = OrangeTheme) }
                    TextButton(onClick = { showCopyConfirm = false; viewModel.copyPreviousWeek(overwrite = false) }) { Text("Keep mine, add the rest") }
                }
            },
            dismissButton = { TextButton(onClick = { showCopyConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showCopyDay) {
        CopyDaySheet(
            sourceDay = viewModel.selectedDate,
            sourceMealCount = viewModel.slotsForDate(viewModel.selectedDate).size,
            onCopy = { targets, mode -> viewModel.copyDay(viewModel.selectedDate, targets, mode) },
            onDismiss = { showCopyDay = false }
        )
    }

    if (showGenerate) {
        GenerateGroceryListSheet(
            weekStart = viewModel.weekStart,
            onGenerated = { list ->
                showGenerate = false
                if (onNavigate != null) onNavigate(MealPlanRoutes.groceryListDetail(list.id)) else onNavigateToGroceryList()
            },
            onDismiss = { showGenerate = false }
        )
    }
    // Keep the scope referenced for future async actions launched from menus.
    remember { scope }
}

// MARK: - Week header / day tabs

@Composable
private fun WeekHeader(viewModel: MealPlanViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WeekArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft) { viewModel.goToPreviousWeek() }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(viewModel.weekRangeLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            if (viewModel.isViewingCurrentWeek) {
                Text("This week", fontSize = 11.sp, color = TextSecondaryColor)
            } else {
                Text(
                    "Jump to this week", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = FLPrimary,
                    modifier = Modifier.clickable { viewModel.goToToday() }
                )
            }
        }
        WeekArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight) { viewModel.goToNextWeek() }
    }
}

@Composable
private fun WeekArrow(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).background(White, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = TextPrimaryColor) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayTab(date: String, isSelected: Boolean, calories: Int, isOver: Boolean, onClick: () -> Unit, onLongPress: () -> Unit) {
    val isToday = MealPlanViewModel.isToday(date)
    val calorieColor = if (isSelected) White else if (isOver) OrangeTheme else TextPrimaryColor
    Column(
        modifier = Modifier
            .width(52.dp).height(64.dp)
            .background(if (isSelected) FLPrimary else White, RoundedCornerShape(12.dp))
            .border(if (isToday && !isSelected) 1.5.dp else 0.dp, if (isToday && !isSelected) FLPrimary else Color.Transparent, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val fg = if (isSelected) White else TextPrimaryColor
        Text(MealPlanViewModel.weekdayShort(date), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg)
        Text(MealPlanViewModel.dayNumber(date), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = fg)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isOver) Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Over target", tint = calorieColor, modifier = Modifier.size(10.dp))
            Text("$calories", fontSize = 10.sp, color = calorieColor)
        }
    }
}

// MARK: - Daily header

@Composable
private fun DailyCalorieHeader(viewModel: MealPlanViewModel) {
    val date = viewModel.selectedDate
    val cals = viewModel.caloriesForDate(date)
    val target = viewModel.effectiveCalorieTarget
    val state = viewModel.targetState(date)
    val fraction = if (target != null && target > 0) (cals.toDouble() / target).coerceIn(0.0, 1.0) else 0.0
    val accent = if (state.isOver) OrangeTheme else FLPrimary
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .border(1.dp, if (state.isOver) OrangeTheme.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(MealPlanViewModel.weekdayFull(date), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            Text(
                if (target != null) "${MealPlanViewModel.mediumDay(date)} · $cals / $target cal planned" else "${MealPlanViewModel.mediumDay(date)} · $cals cal planned",
                fontSize = 14.sp, color = TextSecondaryColor
            )
            when (state) {
                PlannedDayTargetState.OVER -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, null, tint = OrangeTheme, modifier = Modifier.size(14.dp))
                    Text("Over by ${cals - (target ?: 0)} cal", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OrangeTheme)
                }
                PlannedDayTargetState.UNDER -> Text("${(target ?: 0) - cals} cal left to plan", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                PlannedDayTargetState.NO_TARGET -> Unit
            }
        }
        PlanRing(fraction = fraction.toFloat(), color = accent, size = 44.dp, stroke = 6.dp) {
            Text("${(fraction * 100).roundToInt()}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent)
        }
    }
}

// MARK: - Meal slot card

@Composable
private fun MealSlotCard(meal: MealType, slots: List<MealSlot>, onOpen: (MealSlot) -> Unit, onAdd: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(White, RoundedCornerShape(12.dp))) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(planMealIcon(meal), contentDescription = null, tint = planMealColor(meal), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(meal.displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.weight(1f))
            if (slots.isNotEmpty()) Text("${slots.sumOf { it.calories }} cal", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
        }
        PlanDivider()
        slots.forEach { slot ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(slot) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(slot.recipeName.ifEmpty { meal.displayName }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                        if (slot.isAISuggestion) Icon(Icons.Filled.AutoAwesome, contentDescription = "AI suggestion", tint = PurpleTheme, modifier = Modifier.size(12.dp))
                    }
                    PlanMacroRow(slot.protein, slot.fat, slot.carbs)
                }
                Text("${slot.calories} cal", fontSize = 13.sp, color = TextSecondaryColor)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MediumGray)
            }
            PlanDivider()
        }
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.AddCircle, contentDescription = null, tint = FLPrimary.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            Text(if (slots.isEmpty()) "Add meal" else "Add another", fontSize = 14.sp, color = TextSecondaryColor)
        }
    }
}

@Composable
private fun EmptyDayPrompt(weekday: String) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(vertical = 20.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.Restaurant, contentDescription = null, tint = FLPrimary.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        Text("No meals planned for $weekday", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
        Text("Use “Add meal” under any section below to start planning this day.", fontSize = 12.sp, color = TextSecondaryColor, textAlign = TextAlign.Center)
    }
}

// MARK: - Result banner copy

private fun copyResultMessage(r: CopyMealSlotWeekResult): String {
    if (r.copiedCount == 0 && r.skippedCount == 0 && r.overwrittenCount == 0) return "Nothing to copy from last week."
    val parts = mutableListOf("Copied ${r.copiedCount} meal${if (r.copiedCount == 1) "" else "s"}")
    if (r.overwrittenCount > 0) parts += "replaced ${r.overwrittenCount}"
    if (r.skippedCount > 0) parts += "kept ${r.skippedCount} of yours"
    return parts.joinToString(" · ")
}

private fun copyDayResultMessage(r: CopyMealSlotDayResult): String {
    val days = r.targets.size
    val parts = mutableListOf("Copied ${r.totalCopiedCount} meal${if (r.totalCopiedCount == 1) "" else "s"} to $days day${if (days == 1) "" else "s"}")
    if (r.totalReplacedCount > 0) parts += "replaced ${r.totalReplacedCount}"
    return parts.joinToString(" · ")
}

private fun ideasResultMessage(r: ApplyMealPlanWeekResult, weekLabel: String): String {
    var text = if (r.createdCount == 1) "Added 1 meal idea to $weekLabel." else "Added ${r.createdCount} meal ideas to $weekLabel."
    if (r.replacedCount > 0) text += " ${r.replacedCount} existing meal${if (r.replacedCount == 1) "" else "s"} replaced."
    if (r.skippedCount > 0) text += " ${r.skippedCount} skipped where you already had a meal."
    return text
}

// MARK: - Copy day sheet (FTNSSLNK-218)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyDaySheet(
    sourceDay: String,
    sourceMealCount: Int,
    onCopy: suspend (List<String>, CopyMealSlotDayMode) -> CopyMealSlotDayResult?,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var occupiedCount by remember { mutableStateOf<Int?>(null) }
    val firstSunday = MealPlanDay.weekStart(MealPlanDay.parse(sourceDay))
    val weeks = remember(sourceDay) {
        (0 until 4).map { w -> MealPlanDay.weekDates(MealPlanDay.addDays(firstSunday, w * 7)).map { MealPlanDay.string(it) } }
    }

    fun perform(mode: CopyMealSlotDayMode) {
        isWorking = true
        scope.launch {
            val result = onCopy(selected.sorted(), mode)
            isWorking = false
            if (result != null) onDismiss() else errorMessage = "Couldn't copy that day. Nothing was changed — try again."
        }
    }

    fun attempt() {
        if (selected.isEmpty() || isWorking) return
        errorMessage = null
        isWorking = true
        scope.launch {
            try {
                val sorted = selected.sorted()
                val occupied = MealPlanStore.occupiedDays(sorted.first(), sorted.last()).intersect(selected)
                isWorking = false
                if (occupied.isEmpty()) perform(CopyMealSlotDayMode.MERGE) else occupiedCount = occupied.size
            } catch (e: ApiError.Offline) {
                isWorking = false; errorMessage = "You're offline — copying a day needs a connection."
            } catch (_: Exception) {
                isWorking = false; errorMessage = "Couldn't check the selected days. Try again."
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!isWorking) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = BackgroundColor) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PlanSheetTitle("Copy Day", onCancel = onDismiss, confirm = if (selected.isEmpty()) "Copy" else "Copy to ${selected.size} day${if (selected.size == 1) "" else "s"}",
                confirmEnabled = selected.isNotEmpty() && !isWorking, isWorking = isWorking, onConfirm = ::attempt)
            PlanCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = FLPrimary)
                    Column {
                        Text("${MealPlanViewModel.weekdayFull(sourceDay)}, ${MealPlanViewModel.mediumDay(sourceDay)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                        Text("$sourceMealCount meal${if (sourceMealCount == 1) "" else "s"} to copy", fontSize = 12.sp, color = TextSecondaryColor)
                    }
                }
            }
            Text("Pick one or more days to copy this day's meals onto.", fontSize = 12.sp, color = TextSecondaryColor)
            weeks.forEach { week ->
                Text(MealPlanViewModel.weekRangeLabel(week.first()).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                Column(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp))) {
                    week.forEachIndexed { index, day ->
                        val isSource = day == sourceDay
                        val isSelected = day in selected
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable(enabled = !isSource) { selected = if (isSelected) selected - day else selected + day }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(MealPlanViewModel.weekdayFull(day), fontSize = 15.sp, color = if (isSource) TextSecondaryColor else TextPrimaryColor)
                                Text(MealPlanViewModel.mediumDay(day), fontSize = 12.sp, color = TextSecondaryColor)
                            }
                            when {
                                isSource -> Text("Copying from", fontSize = 12.sp, color = TextSecondaryColor)
                                isSelected -> Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = FLPrimary)
                                else -> Icon(Icons.Outlined.Circle, contentDescription = null, tint = MediumGray)
                            }
                        }
                        if (index < week.lastIndex) PlanDivider()
                    }
                }
            }
            errorMessage?.let { Text(it, fontSize = 13.sp, color = OrangeTheme) }
        }
    }

    occupiedCount?.let { count ->
        AlertDialog(
            onDismissRequest = { occupiedCount = null },
            title = { Text("$count selected day${if (count == 1) " already has" else "s already have"} meals") },
            text = { Text("Replace clears each selected day before copying. Merge keeps what's already planned and adds this day's meals alongside.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { occupiedCount = null; perform(CopyMealSlotDayMode.REPLACE) }) { Text("Replace those days", color = OrangeTheme) }
                    TextButton(onClick = { occupiedCount = null; perform(CopyMealSlotDayMode.MERGE) }) { Text("Merge with existing meals") }
                }
            },
            dismissButton = { TextButton(onClick = { occupiedCount = null }) { Text("Cancel") } }
        )
    }
}

// MARK: - Generate grocery list sheet (FTNSSLNK-210)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GenerateGroceryListSheet(weekStart: String, onGenerated: (com.fitnesslink.fit.model.GroceryList) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val start = MealPlanStore.weekBounds(weekStart)
    var fromDate by remember { mutableStateOf(start.first) }
    var toDate by remember { mutableStateOf(start.second) }
    var listName by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }
    val dayCount = ((MealPlanDay.parse(toDate).time - MealPlanDay.parse(fromDate).time) / 86_400_000L).toInt() + 1

    ModalBottomSheet(onDismissRequest = { if (!isGenerating) onDismiss() }, containerColor = BackgroundColor) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PlanSheetTitle("Generate Grocery List", onCancel = onDismiss, confirm = "Generate", confirmEnabled = !isGenerating, isWorking = isGenerating) {
                errorMessage = null
                isGenerating = true
                scope.launch {
                    try {
                        onGenerated(GroceryStore.generate(fromDate, toDate, listName.trim().ifEmpty { null }))
                    } catch (e: ApiError.ServerError) {
                        errorMessage = if (e.isBadRequest) (e.envelope?.displayMessage ?: "Nothing planned in this range.") else MealPlanViewModel.describe(e)
                    } catch (e: Exception) {
                        errorMessage = MealPlanViewModel.describe(e)
                    } finally {
                        isGenerating = false
                    }
                }
            }
            Text("DATE RANGE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
            Column(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp))) {
                PlanValueRow("From", MealPlanViewModel.mediumDay(fromDate)) { pickingFrom = true }
                PlanDivider()
                PlanValueRow("To", MealPlanViewModel.mediumDay(toDate)) { pickingTo = true }
            }
            Text("Combines every planned food from $dayCount day${if (dayCount == 1) "" else "s"} into one shopping list.", fontSize = 12.sp, color = TextSecondaryColor)
            Text("LIST NAME", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
            PlanCard { PlanTextField(listName, { listName = it }, placeholder = "List name (optional)") }
            errorMessage?.let { Text(it, fontSize = 13.sp, color = OrangeTheme) }
        }
    }
    if (pickingFrom) PlanDatePickerDialog(fromDate, onPick = { fromDate = it; if (toDate < it) toDate = it; pickingFrom = false }, onDismiss = { pickingFrom = false })
    if (pickingTo) PlanDatePickerDialog(toDate, onPick = { if (it >= fromDate) toDate = it; pickingTo = false }, onDismiss = { pickingTo = false })
}

// MARK: - Shared planner UI helpers (used across the meal plan / grocery / hydration screens)

internal fun planMealColor(meal: MealType): Color = when (meal) {
    MealType.BREAKFAST -> OrangeTheme
    MealType.LUNCH -> FLPrimary
    MealType.DINNER -> PurpleTheme
    MealType.SNACK -> BlueTheme
}

internal fun planMealIcon(meal: MealType): ImageVector = when (meal) {
    MealType.BREAKFAST -> Icons.Filled.WbTwilight
    MealType.LUNCH -> Icons.Filled.WbSunny
    MealType.DINNER -> Icons.Filled.DarkMode
    MealType.SNACK -> Icons.Filled.Spa
}

internal fun planGrams(value: Double): String = "${value.roundToInt()}g"

@Composable
internal fun PlanTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimaryColor) }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
internal fun PlanCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
internal fun PlanDivider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(ProgressBG))
}

@Composable
internal fun PlanMacroRow(protein: Double, fat: Double, carbs: Double, fontSize: Int = 11) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlanMacroTag("P", protein, BlueTheme, fontSize)
        PlanMacroTag("F", fat, OrangeTheme, fontSize)
        PlanMacroTag("C", carbs, PurpleTheme, fontSize)
    }
}

@Composable
internal fun PlanMacroTag(letter: String, value: Double, color: Color, fontSize: Int = 11) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(letter, fontSize = fontSize.sp, fontWeight = FontWeight.Bold, color = color)
        Text(planGrams(value), fontSize = fontSize.sp, color = TextSecondaryColor)
    }
}

@Composable
internal fun PlanBanner(icon: ImageVector, color: Color, text: String, onDismiss: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, fontSize = 13.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
        if (onDismiss != null) Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = TextSecondaryColor, modifier = Modifier.size(16.dp).clickable(onClick = onDismiss))
    }
}

@Composable
internal fun PlanActionCard(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(White, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Text(subtitle, fontSize = 12.sp, color = TextSecondaryColor)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MediumGray)
    }
}

@Composable
internal fun PlanActionButton(icon: ImageVector, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextPrimaryColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
    }
}

@Composable
internal fun PlanPrimaryButton(text: String, enabled: Boolean = true, color: Color = FLPrimary, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.fillMaxWidth().background(if (enabled) color else DisabledButton, RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White) }
}

@Composable
internal fun PlanTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    textAlign: TextAlign = TextAlign.Start
) {
    Box(modifier = modifier.fillMaxWidth().background(BackgroundColor, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = MediumGray, textAlign = textAlign, modifier = Modifier.fillMaxWidth())
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = singleLine,
            textStyle = TextStyle(fontSize = 15.sp, color = TextPrimaryColor, textAlign = textAlign),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun PlanValueRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = if (onClick != null) FLPrimary else TextSecondaryColor)
    }
}

@Composable
internal fun PlanSheetTitle(title: String, onCancel: () -> Unit, confirm: String?, confirmEnabled: Boolean = true, isWorking: Boolean = false, onConfirm: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onCancel, enabled = !isWorking) { Text("Cancel", color = TextSecondaryColor) }
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        if (isWorking) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = FLPrimary)
        else if (confirm != null) TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirm, color = if (confirmEnabled) FLPrimary else MediumGray, fontWeight = FontWeight.SemiBold) }
        else Spacer(Modifier.width(64.dp))
    }
}

/** A ring progress indicator with centered content. */
@Composable
internal fun PlanRing(fraction: Float, color: Color, size: androidx.compose.ui.unit.Dp, stroke: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            drawArc(ProgressBG, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(strokePx))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
        }
        content()
    }
}

/** `yyyy-MM-dd` ⇄ the Material date picker's UTC-midnight millis. */
internal fun isoToPickerMillis(iso: String): Long {
    val local = MealPlanDay.calendar().apply { time = MealPlanDay.parse(iso) }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

internal fun pickerMillisToIso(millis: Long): String {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
    val local = MealPlanDay.calendar().apply { clear(); set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH)) }
    return MealPlanDay.string(local.time)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanDatePickerDialog(initialIso: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = isoToPickerMillis(initialIso))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPick(pickerMillisToIso(it)) } ?: onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state = state) }
}

internal fun todayIso(): String = MealPlanDay.string(Date())
