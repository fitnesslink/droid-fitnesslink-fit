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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.WaterUnit
import com.fitnesslink.fit.model.api.Micronutrient
import com.fitnesslink.fit.nutrition.DisplayedNutrientsStore
import com.fitnesslink.fit.nutrition.HydrationStore
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Display & Units settings (iOS `NutritionDisplaySettingsView`,
 * FTNSSLNK-217/221) — reached from the Goals hub and the dashboard micros
 * card's Edit link.
 *
 * - Micronutrients: which of the five micros the dashboard shows; stored
 *   server-side via `DisplayedNutrientsStore` (optimistic, reverts on failure).
 * - Water unit: preferred display unit (Automatic / fl oz / mL) via
 *   `HydrationStore.setPreferredUnit` (agent C). Automatic clears the
 *   preference back to the hydration goal's own unit.
 */
@Composable
fun NutritionDisplaySettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val displayed by DisplayedNutrientsStore.displayed.collectAsState()
    val hydrationGoal by HydrationStore.goal.collectAsState()
    var preferredUnit by remember { mutableStateOf(runCatching { DatabaseManager.hydrationGoal().preferredUnit }.getOrNull()) }
    var isSavingUnit by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        DisplayedNutrientsStore.refresh()
        runCatching { HydrationStore.refresh() }
    }
    LaunchedEffect(hydrationGoal) { hydrationGoal?.let { preferredUnit = it.preferredUnit } }

    val unitChoices: List<WaterUnit?> = listOf(null, WaterUnit.OZ, WaterUnit.ML)

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Display & Units", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            errorMessage?.let { NutritionValidationBanner(it) }

            // Micronutrients
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Micronutrients", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                NutritionFieldCard {
                    Micronutrient.entries.forEachIndexed { index, micro ->
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(micro.displayName, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                                Text(if (micro.unit == "g") "Grams" else "Milligrams", fontSize = 12.sp, color = TextSecondaryColor)
                            }
                            Switch(
                                checked = micro.key in displayed,
                                onCheckedChange = { on ->
                                    errorMessage = null
                                    scope.launch {
                                        val ok = DisplayedNutrientsStore.setDisplayed(micro, on)
                                        if (!ok) errorMessage = "Couldn't save that change. Check your connection and try again."
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = FLPrimary, checkedThumbColor = White)
                            )
                        }
                        if (index < Micronutrient.entries.size - 1) SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                Text(
                    "Choose which micronutrients appear on your nutrition dashboard. Calories, protein, fat, and carbs are always shown. Your choice syncs across devices.",
                    fontSize = 12.sp, color = TextSecondaryColor
                )
            }

            // Water unit
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Water Unit", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                NutritionFieldCard {
                    unitChoices.forEachIndexed { index, choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isSavingUnit) {
                                    if (choice == preferredUnit) return@clickable
                                    val previous = preferredUnit
                                    preferredUnit = choice
                                    errorMessage = null
                                    isSavingUnit = true
                                    scope.launch {
                                        try {
                                            HydrationStore.setPreferredUnit(choice)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            preferredUnit = previous
                                            errorMessage = "Couldn't save the water unit. Check your connection and try again."
                                        } finally {
                                            isSavingUnit = false
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(unitLabel(choice), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                            Spacer(modifier = Modifier.weight(1f))
                            if (choice == preferredUnit) Icon(Icons.Default.Check, contentDescription = "Selected", tint = FLPrimary, modifier = Modifier.size(18.dp))
                        }
                        if (index < unitChoices.size - 1) SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                Text(
                    "Applied everywhere water is shown — the goal progress, entries, and summaries. Automatic uses your hydration goal's unit.",
                    fontSize = 12.sp, color = TextSecondaryColor
                )
            }
        }
    }
}

private fun unitLabel(choice: WaterUnit?): String = when (choice) {
    null -> "Automatic"
    WaterUnit.OZ -> "Fluid ounces (fl oz)"
    WaterUnit.ML -> "Milliliters (mL)"
    WaterUnit.CUPS -> "Cups"
}
