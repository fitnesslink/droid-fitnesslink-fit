package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.model.NutritionGoal
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.DraftMeal
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.AIMealGeneratorViewModel
import kotlinx.coroutines.launch

/**
 * "AI Suggestions" for one meal slot (port of iOS `MealSuggestionsSheet`).
 * The user describes the meal, tunes targets / diet / allergies and taps
 * Generate; tapping a suggestion hands the [DraftMeal] back so the builder
 * stages it. Nothing is persisted here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealSuggestionsSheet(mealType: MealType, goal: NutritionGoal, onPick: (DraftMeal) -> Unit, onDismiss: () -> Unit) {
    val vm: AIMealGeneratorViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val isConnected by NetworkMonitor.isConnected.collectAsState()

    LaunchedEffect(Unit) {
        vm.seed(mealType, goal)
        vm.seedFromProfile()
    }

    ModalBottomSheet(
        onDismissRequest = { if (!vm.isGenerating) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BackgroundColor
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlanSheetTitle("AI Suggestions", onCancel = onDismiss, confirm = null, isWorking = false)

            if (!isConnected) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.WifiOff, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(16.dp))
                    Text("You need an active internet connection to generate meal ideas.", fontSize = 13.sp, color = TextSecondaryColor)
                }
            }

            PlanSectionLabel("Describe your ${mealType.displayName.lowercase()}")
            PlanCard {
                PlanTextField(vm.prompt, { vm.prompt = it }, "e.g. something quick with eggs, or a big salad with chicken", singleLine = false)
            }
            PlanFootnote("You'll get a few ideas to pick from. Tap one to stage it in your meal, then edit it however you like.")

            PlanSectionLabel("Targets for this meal")
            PlanCard {
                PlanNumberRow("Calories", vm.calorieTarget?.toString() ?: "") { vm.calorieTarget = it.toIntOrNull() }
                PlanNumberRow("Protein (g)", vm.proteinTarget?.toInt()?.toString() ?: "") { vm.proteinTarget = it.toDoubleOrNull() }
                PlanNumberRow("Fat (g)", vm.fatTarget?.toInt()?.toString() ?: "") { vm.fatTarget = it.toDoubleOrNull() }
                PlanNumberRow("Carbs (g)", vm.carbsTarget?.toInt()?.toString() ?: "") { vm.carbsTarget = it.toDoubleOrNull() }
            }
            PlanFootnote("A share of your daily goal. Leave blank to let the ideas decide.")

            PlanSectionLabel("Diet style (optional)")
            PlanCard {
                PlanWrapChips(AIMealGeneratorViewModel.dietOptions, vm.dietaryPreferences) { opt ->
                    vm.dietaryPreferences = if (opt in vm.dietaryPreferences) vm.dietaryPreferences - opt else vm.dietaryPreferences + opt
                }
            }

            PlanSectionLabel("Allergies")
            PlanCard { PlanTextField(vm.allergiesText, { vm.allergiesText = it }, "e.g. peanuts, shellfish") }
            PlanFootnote("Comma-separated. Ideas that include any of these are rejected outright. Allergies saved on your nutrition profile always apply too.")

            PlanSectionLabel("Ingredients to avoid (optional)")
            PlanCard { PlanTextField(vm.avoidIngredientsText, { vm.avoidIngredientsText = it }, "e.g. mushrooms, cilantro") }

            vm.errorMessage?.let { Text(it, fontSize = 13.sp, color = Color.Red) }

            GenerateButton(
                isGenerating = vm.isGenerating,
                enabled = vm.canGenerate,
                workingLabel = "Cooking up ideas…",
                label = if (vm.hasGenerated) "Generate Again" else "Generate Ideas"
            ) { scope.launch { vm.generate() } }
            PlanFootnote("These are ideas to get you started, not nutrition or medical advice.")

            if (vm.suggestions.isNotEmpty()) {
                PlanSectionLabel("Ideas")
                Column(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp))) {
                    vm.suggestions.forEachIndexed { index, meal ->
                        SuggestionRow(meal) { onPick(meal) }
                        if (index < vm.suggestions.lastIndex) PlanDivider()
                    }
                }
            }
            if (!vm.isGenerating && vm.hasGenerated && vm.suggestions.isEmpty() && vm.errorMessage == null) {
                Text("No ideas came back. Try a different description.", fontSize = 13.sp, color = TextSecondaryColor)
            }
        }
    }
}

@Composable
private fun SuggestionRow(meal: DraftMeal, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(meal.recipeName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.weight(1f))
            Text("${meal.calories} cal", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
        }
        meal.description?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, color = TextSecondaryColor, maxLines = 2) }
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
            Text(meal.ingredients.joinToString(", ") { it.name }, fontSize = 12.sp, color = TextSecondaryColor, maxLines = 2)
        }
    }
}

// MARK: - Shared AI-form pieces (also used by the week ideas screen)

@Composable
internal fun GenerateButton(isGenerating: Boolean, enabled: Boolean, workingLabel: String, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(if (enabled || isGenerating) PurpleTheme else PurpleTheme.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled && !isGenerating, onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (isGenerating) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = White)
            Spacer(Modifier.width(8.dp))
            Text(workingLabel, fontSize = 15.sp, color = White)
        } else {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = White)
        }
    }
}

@Composable
internal fun PlanSectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor, modifier = Modifier.padding(top = 4.dp))
}

@Composable
internal fun PlanFootnote(text: String) {
    Text(text, fontSize = 12.sp, color = TextSecondaryColor)
}

@Composable
internal fun PlanNumberRow(label: String, value: String, placeholder: String = "—", onChange: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
        PlanTextField(
            value, { onChange(it.filter { c -> c.isDigit() }) }, placeholder,
            modifier = Modifier.width(96.dp), keyboardType = KeyboardType.Number, textAlign = TextAlign.End
        )
    }
}
