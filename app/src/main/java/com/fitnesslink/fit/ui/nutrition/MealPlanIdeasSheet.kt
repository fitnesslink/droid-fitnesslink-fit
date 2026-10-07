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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.MealPlanIdeasViewModel
import com.fitnesslink.fit.viewmodel.MealPlanViewModel
import kotlinx.coroutines.launch

/**
 * "Meal Plan Ideas" request form (port of iOS `MealPlanIdeasSheet`):
 * description + which meals + daily targets + diet chips + allergies /
 * avoid / cuisines + prep time / skill / budget chips → one week-sized AI
 * call. A successful draft pushes the review screen ([onReview]); nothing
 * is written until the user approves there.
 */
@Composable
fun MealPlanIdeasScreen(
    weekStart: String,
    viewModel: MealPlanIdeasViewModel = viewModel(),
    onBack: () -> Unit,
    onReview: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isConnected by NetworkMonitor.isConnected.collectAsState()
    val goal = rememberPlannerGoal()

    LaunchedEffect(weekStart) {
        viewModel.seed(weekStart, goal)
        viewModel.seedFromProfile()
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        PlanTopBar(title = "Meal Plan Ideas", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isConnected) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.WifiOff, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(16.dp))
                    Text("You need an active internet connection to generate meal plan ideas.", fontSize = 13.sp, color = TextSecondaryColor)
                }
            }

            PlanSectionLabel("Describe the week")
            PlanCard { PlanTextField(viewModel.prompt, { viewModel.prompt = it }, "e.g. Mediterranean, quick weeknight dinners, big breakfasts, no mushrooms", singleLine = false) }
            PlanFootnote("Ideas for ${MealPlanViewModel.weekRangeLabel(viewModel.weekStart)}. You'll review every meal before anything is added to your plan.")

            PlanSectionLabel("Which meals")
            PlanCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealType.entries.forEach { type ->
                        PlanChipLabel(type.displayName, type in viewModel.mealTypes) {
                            viewModel.mealTypes = if (type in viewModel.mealTypes) viewModel.mealTypes - type else viewModel.mealTypes + type
                        }
                    }
                }
            }

            PlanSectionLabel("Daily targets")
            PlanCard {
                PlanNumberRow("Calories per day", viewModel.dailyCalorieTarget?.toString() ?: "", placeholder = "2000") { viewModel.dailyCalorieTarget = it.toIntOrNull() }
                PlanNumberRow("Protein (g)", viewModel.dailyProteinTarget?.toInt()?.toString() ?: "") { viewModel.dailyProteinTarget = it.toDoubleOrNull() }
                PlanNumberRow("Fat (g)", viewModel.dailyFatTarget?.toInt()?.toString() ?: "") { viewModel.dailyFatTarget = it.toDoubleOrNull() }
                PlanNumberRow("Carbs (g)", viewModel.dailyCarbsTarget?.toInt()?.toString() ?: "") { viewModel.dailyCarbsTarget = it.toDoubleOrNull() }
            }
            PlanFootnote("Seeded from your nutrition goal. Leave blank to let the ideas decide.")

            PlanSectionLabel("Diet style (optional)")
            PlanCard {
                PlanWrapChips(MealPlanIdeasViewModel.dietOptions, viewModel.dietaryPreferences) { opt ->
                    viewModel.dietaryPreferences = if (opt in viewModel.dietaryPreferences) viewModel.dietaryPreferences - opt else viewModel.dietaryPreferences + opt
                }
            }

            PlanSectionLabel("Allergies")
            PlanCard { PlanTextField(viewModel.allergiesText, { viewModel.allergiesText = it }, "e.g. peanuts, shellfish") }
            PlanFootnote("Comma-separated. Ideas that include any of these are rejected outright. Allergies saved on your nutrition profile always apply too.")

            PlanSectionLabel("Ingredients to avoid (optional)")
            PlanCard { PlanTextField(viewModel.avoidIngredientsText, { viewModel.avoidIngredientsText = it }, "e.g. mushrooms, cilantro, tofu") }

            PlanSectionLabel("Cuisines you like (optional)")
            PlanCard { PlanTextField(viewModel.cuisinesText, { viewModel.cuisinesText = it }, "e.g. Mexican, Thai, Italian") }

            PlanSectionLabel("Max time per meal (optional)")
            PlanCard {
                PlanSingleChipRow(MealPlanIdeasViewModel.prepOptions.map { it to "$it min" }, viewModel.maxPrepMinutes) { v ->
                    viewModel.maxPrepMinutes = if (viewModel.maxPrepMinutes == v) null else v
                }
            }
            PlanSectionLabel("Cooking skill (optional)")
            PlanCard {
                PlanSingleChipRow(MealPlanIdeasViewModel.skillOptions.map { it to it }, viewModel.cookingSkill) { v ->
                    viewModel.cookingSkill = if (viewModel.cookingSkill == v) null else v
                }
            }
            PlanSectionLabel("Budget (optional)")
            PlanCard {
                PlanSingleChipRow(MealPlanIdeasViewModel.budgetOptions.map { it to it }, viewModel.budget) { v ->
                    viewModel.budget = if (viewModel.budget == v) null else v
                }
            }

            viewModel.errorMessage?.let { Text(it, fontSize = 13.sp, color = Color.Red) }

            GenerateButton(
                isGenerating = viewModel.isGenerating,
                enabled = viewModel.canGenerate,
                workingLabel = "Cooking up a week of ideas…",
                label = if (viewModel.hasGenerated) "Generate Again" else "Generate Ideas"
            ) {
                scope.launch {
                    viewModel.generate()
                    if (viewModel.draft != null) onReview()
                }
            }
            PlanFootnote("These are ideas to get you started, not nutrition or medical advice. Generating a full week can take up to a minute.")
            Spacer(Modifier.size(8.dp))
        }
    }
}

// MARK: - Chip controls for the AI request forms
//
// Local equivalents of iOS `AIFormChips` (agent B owns `AIFormChips.kt`;
// these carry distinct names so both can coexist until the integrator
// consolidates them).

@Composable
internal fun PlanChipLabel(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = if (isSelected) White else TextPrimaryColor,
        modifier = Modifier
            .background(if (isSelected) FLPrimary else BackgroundColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
internal fun <V> PlanSingleChipRow(options: List<Pair<V, String>>, selected: V?, onSelect: (V) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) -> PlanChipLabel(label, selected == value) { onSelect(value) } }
    }
}

@Composable
internal fun PlanWrapChips(options: List<String>, selected: Set<String>, perRow: Int = 3, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { opt -> PlanChipLabel(opt, opt in selected) { onToggle(opt) } }
            }
        }
    }
}
