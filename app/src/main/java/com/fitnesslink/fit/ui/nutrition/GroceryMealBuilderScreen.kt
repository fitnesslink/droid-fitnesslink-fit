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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.GroceryItem
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.nutrition.GroceryStore
import com.fitnesslink.fit.nutrition.MealBuilderStore
import com.fitnesslink.fit.ui.navigation.MealPlanRoutes
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.GroceryMealBuilderViewModel
import com.fitnesslink.fit.viewmodel.GroceryMealBuilderViewModel.Resolution
import com.fitnesslink.fit.viewmodel.MatchConfidence
import kotlinx.coroutines.launch

/**
 * Build a meal from grocery items (FTNSSLNK-209, port of iOS
 * `GroceryMealBuilderView`): pick items (all pre-selected) → each is
 * matched to a catalog food the user accepts, replaces, enters manually or
 * skips → Build seeds [MealBuilderStore] and opens the meal builder in
 * destination-choosing mode (plan a slot or log now).
 */
@Composable
fun GroceryMealBuilderScreen(listId: String, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val vm: GroceryMealBuilderViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val items = remember(listId) { GroceryStore.items(listId) }
    var selected by remember(listId) { mutableStateOf(items.map { it.id }.toSet()) }
    var reviewing by remember { mutableStateOf(false) }
    var manualFor by remember { mutableStateOf<GroceryMealBuilderViewModel.ItemState?>(null) }
    var replaceFor by remember { mutableStateOf<GroceryMealBuilderViewModel.ItemState?>(null) }

    fun startReview() {
        vm.start(items.filter { it.id in selected })
        reviewing = true
        scope.launch { vm.matchAll() }
    }

    fun build() {
        if (!vm.canBuild) return
        MealBuilderStore.beginSeeded(todayIso(), MealType.DINNER, vm.suggestedName, vm.resolvedFoods)
        onNavigate(MealPlanRoutes.mealBuilder(todayIso(), MealType.DINNER, seeded = true, allowLog = true))
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        PlanTopBar(title = if (reviewing) "Match Foods" else "Select Items", onBack = { if (reviewing) reviewing = false else onBack() }) {
            if (reviewing) TextButton(onClick = ::build, enabled = vm.canBuild) { Text("Build", fontWeight = FontWeight.SemiBold, color = if (vm.canBuild) FLPrimary else MediumGray) }
            else TextButton(onClick = ::startReview, enabled = selected.isNotEmpty()) { Text("Next", fontWeight = FontWeight.SemiBold, color = if (selected.isNotEmpty()) FLPrimary else MediumGray) }
        }
        if (!reviewing) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PlanSectionLabel("${selected.size} of ${items.size} selected")
                Column(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp))) {
                    items.forEachIndexed { index, item ->
                        SelectRow(item, item.id in selected) { selected = if (item.id in selected) selected - item.id else selected + item.id }
                        if (index < items.lastIndex) PlanDivider()
                    }
                }
                PlanFootnote("Pick the items you want in this meal. Each one is matched to a food you can confirm, swap, or fill in — nothing is added without your say-so.")
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (vm.unresolvedCount > 0) {
                    ReviewBanner(Icons.Filled.Error, OrangeTheme, "${vm.unresolvedCount} item${if (vm.unresolvedCount == 1) "" else "s"} still need a decision.")
                } else {
                    ReviewBanner(Icons.Filled.CheckCircle, FLPrimary, "${vm.includedCount} food${if (vm.includedCount == 1) "" else "s"} ready. Build to review and save or log.")
                }
                vm.items.forEach { item ->
                    ReviewCard(item, onAccept = { vm.accept(item.id) }, onSkip = { vm.skip(item.id) }, onManual = { manualFor = item }, onReplace = { replaceFor = item })
                }
            }
        }
    }

    manualFor?.let { item ->
        PlanManualFoodSheet(prefillName = item.itemName, onAdd = { vm.setManual(item.id, it); manualFor = null }, onDismiss = { manualFor = null })
    }
    replaceFor?.let { item ->
        PlanFoodSearchSheet(title = "Choose a Food", initialQuery = item.itemName, stayOpenAfterPick = false,
            onPick = { vm.replaceCandidate(item.id, it) }, onDismiss = { replaceFor = null })
    }
}

@Composable
private fun SelectRow(item: GroceryItem, selected: Boolean, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle, contentDescription = null, tint = if (selected) FLPrimary else MediumGray, modifier = Modifier.size(24.dp))
        Column {
            Text(item.name, fontSize = 15.sp, color = TextPrimaryColor)
            val amount = "${item.quantity} ${item.unit}".trim()
            if (amount.isNotEmpty()) Text(amount, fontSize = 12.sp, color = TextSecondaryColor)
        }
    }
}

@Composable
private fun ReviewBanner(icon: ImageVector, color: Color, text: String) {
    Row(modifier = Modifier.fillMaxWidth().background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = color)
        Text(text, fontSize = 13.sp, color = TextPrimaryColor)
    }
}

@Composable
private fun ReviewCard(item: GroceryMealBuilderViewModel.ItemState, onAccept: () -> Unit, onSkip: () -> Unit, onManual: () -> Unit, onReplace: () -> Unit) {
    PlanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.itemName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.weight(1f))
            val (label, color) = when (item.resolution) {
                Resolution.NeedsReview -> "Needs review" to OrangeTheme
                Resolution.Accepted, is Resolution.Manual -> "Included" to FLPrimary
                Resolution.Skipped -> "Skipped" to MediumGray
            }
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color,
                modifier = Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        if (item.isSearching) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = FLPrimary)
                Text("Finding a match…", fontSize = 13.sp, color = TextSecondaryColor)
            }
        } else {
            when (val r = item.resolution) {
                is Resolution.Manual -> ResolvedLine(Icons.Filled.Edit, r.food.name, "Manual entry · ${Math.round(r.food.totalCalories)} cal")
                Resolution.Skipped -> ResolvedLine(Icons.Filled.RemoveCircleOutline, "Skipped", "Not included in the meal")
                else -> {
                    val c = item.candidate
                    if (c != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(c.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                            Text("${Math.round(c.calories)} cal · P ${planGrams(c.protein)} · C ${planGrams(c.carbs)} · F ${planGrams(c.fat)}", fontSize = 12.sp, color = TextSecondaryColor)
                            item.confidence?.let { conf ->
                                val (icon, color) = when (conf) {
                                    MatchConfidence.HIGH -> Icons.Filled.Verified to FLPrimary
                                    MatchConfidence.MEDIUM -> Icons.Filled.HelpOutline to BlueTheme
                                    MatchConfidence.LOW -> Icons.Filled.Warning to OrangeTheme
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                                    Text(conf.label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
                                }
                            }
                        }
                    } else {
                        Text("No food-database match found. Enter it manually or skip it.", fontSize = 13.sp, color = TextSecondaryColor)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.candidate != null && item.resolution != Resolution.Accepted) Pill("Accept", filled = true, onClick = onAccept)
            Pill(if (item.candidate == null) "Search" else "Replace", onClick = onReplace)
            Pill("Manual", onClick = onManual)
            if (item.resolution != Resolution.Skipped) Pill("Skip", onClick = onSkip)
        }
    }
}

@Composable
private fun ResolvedLine(icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
            Text(detail, fontSize = 12.sp, color = TextSecondaryColor)
        }
    }
}

@Composable
private fun Pill(title: String, filled: Boolean = false, onClick: () -> Unit) {
    Text(
        title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (filled) White else FLPrimary,
        modifier = Modifier.background(if (filled) FLPrimary else FLPrimary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp)
    )
}
