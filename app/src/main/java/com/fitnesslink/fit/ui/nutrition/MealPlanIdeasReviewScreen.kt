package com.fitnesslink.fit.ui.nutrition

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.network.dto.ApplyMealPlanWeekMode
import com.fitnesslink.fit.network.dto.ApplyMealPlanWeekResult
import com.fitnesslink.fit.network.dto.DraftMeal
import com.fitnesslink.fit.network.dto.DraftMealPlanDay
import com.fitnesslink.fit.nutrition.MealPlanStore
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.MealPlanIdeasViewModel
import com.fitnesslink.fit.viewmodel.MealPlanIdeasViewModel.Companion.allMeals
import com.fitnesslink.fit.viewmodel.MealPlanViewModel
import kotlinx.coroutines.launch

/**
 * Review an AI-drafted week (port of iOS `MealPlanIdeasReviewView`):
 * check / uncheck each idea, expand ingredients, then approve. A week that
 * already has meals asks merge ("Keep mine, add the rest") vs replace.
 * Approval goes through `POST /meal-slots/me/apply-week`; the result lands
 * on the planner as a banner via `MealPlanStore.pendingIdeasResult`.
 */
@Composable
fun MealPlanIdeasReviewScreen(
    viewModel: MealPlanIdeasViewModel = viewModel(),
    onBack: () -> Unit,
    onApplied: (ApplyMealPlanWeekResult) -> Unit
) {
    val scope = rememberCoroutineScope()
    val draft = viewModel.draft
    var showModeConfirm by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    val weekHasMeals = remember(viewModel.weekStart) { MealPlanStore.week(viewModel.weekStart).isNotEmpty() }

    fun apply(mode: String) {
        scope.launch {
            val result = viewModel.apply(mode) ?: return@launch
            MealPlanStore.pendingIdeasResult = result
            onApplied(result)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        PlanTopBar(title = "Review Ideas", onBack = { if (!viewModel.isApplying) onBack() })
        if (draft == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No ideas to review.", color = TextSecondaryColor) }
        } else {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = PurpleTheme)
                        Text("Meal plan ideas", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                    }
                    Text(MealPlanViewModel.weekRangeLabel(draft.weekStart.ifEmpty { viewModel.weekStart }), fontSize = 14.sp, color = TextSecondaryColor)
                    draft.notes?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 14.sp, color = TextSecondaryColor) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${viewModel.selectedCount} of ${draft.allMeals().size} ideas selected", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                        viewModel.averageDailyCalories?.let { Text("· ~$it cal/day", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FLPrimary) }
                    }
                    Text("Tap an idea to include or skip it. You can edit any meal after it's added.", fontSize = 12.sp, color = TextSecondaryColor)
                }

                draft.days.forEach { day ->
                    DayCard(day, viewModel, expanded, onToggleExpanded = { k -> expanded = if (k in expanded) expanded - k else expanded + k })
                }

                Text(
                    "These are ideas to get you started, not nutrition or medical advice. Calories and macros are estimates — check labels and adjust to suit you.",
                    fontSize = 12.sp, color = TextSecondaryColor
                )
            }
        }

        // Action bar
        Column(modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            viewModel.applyErrorMessage?.let { Text(it, fontSize = 13.sp, color = OrangeTheme) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.weight(1f).background(BackgroundColor, RoundedCornerShape(10.dp))
                        .clickable(enabled = !viewModel.isApplying) { viewModel.discard(); onBack() }.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Discard", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor) }
                val canApply = !viewModel.isApplying && draft != null && viewModel.selectedCount > 0
                Box(
                    modifier = Modifier.weight(1f).background(if (canApply) FLPrimary else FLPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable(enabled = canApply) { if (weekHasMeals) showModeConfirm = true else apply(ApplyMealPlanWeekMode.MERGE) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (viewModel.isApplying) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = White)
                            Spacer(Modifier.width(6.dp))
                            Text("Adding…", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White)
                        }
                    } else {
                        Text(if (viewModel.selectedCount == 0) "Add to Plan" else "Add ${viewModel.selectedCount} to Plan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White)
                    }
                }
            }
        }
    }

    if (showModeConfirm) {
        AlertDialog(
            onDismissRequest = { showModeConfirm = false },
            title = { Text("This week already has meals") },
            text = { Text("Replace clears every meal in the week first. Keep mine only adds ideas where a meal isn't already planned.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { showModeConfirm = false; apply(ApplyMealPlanWeekMode.REPLACE) }) { Text("Replace this week", color = OrangeTheme) }
                    TextButton(onClick = { showModeConfirm = false; apply(ApplyMealPlanWeekMode.MERGE) }) { Text("Keep mine, add the rest") }
                }
            },
            dismissButton = { TextButton(onClick = { showModeConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DayCard(day: DraftMealPlanDay, vm: MealPlanIdeasViewModel, expanded: Set<String>, onToggleExpanded: (String) -> Unit) {
    PlanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val title = if (day.day.isNotEmpty()) "${MealPlanViewModel.weekdayFull(day.day)} · ${MealPlanViewModel.mediumDay(day.day)}" else day.day
            Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor, modifier = Modifier.weight(1f))
            val cal = vm.selectedCalories(day)
            if (cal > 0) Text("$cal cal", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
        }
        if (day.meals.isEmpty()) Text("No ideas for this day.", fontSize = 13.sp, color = TextSecondaryColor)
        day.meals.forEachIndexed { index, planned ->
            val key = vm.key(day, index)
            MealRow(
                mealType = planned.mealType, meal = planned.meal,
                selected = vm.isSelected(day, index), isExpanded = key in expanded, enabled = !vm.isApplying,
                onToggle = { vm.toggle(day, index) }, onToggleExpanded = { onToggleExpanded(key) }
            )
        }
    }
}

@Composable
private fun MealRow(mealType: String, meal: DraftMeal, selected: Boolean, isExpanded: Boolean, enabled: Boolean, onToggle: () -> Unit, onToggleExpanded: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle, contentDescription = if (selected) "Included" else "Skipped",
            tint = if (selected) FLPrimary else MediumGray, modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f).alpha(if (selected) 1f else 0.45f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(mealType.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
            Row(verticalAlignment = Alignment.Top) {
                Text(meal.recipeName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                Text("${meal.calories} cal", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
            }
            meal.description?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, color = TextSecondaryColor, maxLines = if (isExpanded) Int.MAX_VALUE else 2) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("P: ${planGrams(meal.protein)}", fontSize = 12.sp, color = BlueTheme)
                Text("F: ${planGrams(meal.fat)}", fontSize = 12.sp, color = OrangeTheme)
                Text("C: ${planGrams(meal.carbs)}", fontSize = 12.sp, color = PurpleTheme)
                meal.prepMinutes?.let {
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(12.dp))
                    Text("${it}m", fontSize = 12.sp, color = TextSecondaryColor)
                }
            }
            if (meal.ingredients.isNotEmpty()) {
                Row(
                    modifier = Modifier.clickable(onClick = onToggleExpanded), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(if (isExpanded) "Hide ingredients" else "${meal.ingredients.size} ingredients", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                    Icon(if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
                }
                if (isExpanded) {
                    Text(
                        meal.ingredients.joinToString(", ") { ing -> listOfNotNull(ing.quantity, ing.unit, ing.name).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ") },
                        fontSize = 12.sp, color = TextSecondaryColor
                    )
                }
            }
        }
    }
}
