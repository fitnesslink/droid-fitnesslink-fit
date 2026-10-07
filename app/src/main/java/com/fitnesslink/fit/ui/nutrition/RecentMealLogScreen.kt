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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.MealSlotFood
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.model.mealTotalCalories
import com.fitnesslink.fit.model.mealTotalCarbs
import com.fitnesslink.fit.model.mealTotalFat
import com.fitnesslink.fit.model.mealTotalProtein
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.dto.RecentMeal
import com.fitnesslink.fit.nutrition.CapturedFood
import com.fitnesslink.fit.nutrition.FoodCaptureBus
import com.fitnesslink.fit.nutrition.FoodEntryBatch
import com.fitnesslink.fit.nutrition.FoodLogStore
import com.fitnesslink.fit.nutrition.NavPayloadHandoff
import com.fitnesslink.fit.nutrition.deepCopied
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.navigation.FoodLoggingRoutes
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * FTNSSLNK-214 — edit-before-log for one recent meal. The picked meal's
 * items are staged LOCALLY (deep copies): adjust servings, remove items, or
 * add more from search / barcode / recents / templates / manual entry (those
 * pickers run in `capture` mode and hand the food back via `FoodCaptureBus`).
 * "Log" creates every staged item at once through the atomic
 * `POST /food-entries/batch` — all entries land or none do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentMealLogScreen(
    token: String,
    mealType: MealType,
    date: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val logDate = parseLogDate(date)
    val meal = remember(token) { NavPayloadHandoff.get<RecentMeal>(token) }
    var foods by remember { mutableStateOf<List<MealSlotFood>>(emptyList()) }
    var selectedMeal by remember { mutableStateOf(mealType) }
    var didBegin by remember { mutableStateOf(false) }
    var showSourceSheet by remember { mutableStateOf(false) }
    var isLogging by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(token) {
        if (!didBegin) {
            didBegin = true
            FoodCaptureBus.clear()
            foods = meal?.let { FoodEntryBatch.stagedFoods(it).deepCopied() } ?: emptyList()
        }
    }

    // Foods captured by a picker opened from here are drained whenever this screen is (re)composed.
    LaunchedEffect(Unit) {
        val captured = FoodCaptureBus.take()
        if (captured.isNotEmpty()) {
            foods = foods + captured.map { it.toMealSlotFood() }
            showSourceSheet = false
        }
    }

    if (meal == null) {
        Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
            HeaderBackView(onBack = onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Meal not found", fontSize = 16.sp, color = TextSecondaryColor) }
        }
        return
    }

    val canLog = foods.isNotEmpty() && !isLogging

    fun setServings(id: String, servings: Double) {
        foods = foods.map { if (it.id == id) it.copy(servings = servings) else it }
    }

    fun logMeal() {
        if (!canLog) return
        isLogging = true
        errorMessage = null
        val entries = FoodEntryBatch.fromMealSlotFoods(foods, selectedMeal, logDate)
        scope.launch {
            try {
                FoodLogStore.logBatch(entries)
                NavPayloadHandoff.clear(token)
                onBack()
            } catch (_: ApiError.Offline) {
                errorMessage = "You're offline — re-logging a meal needs a connection."
            } catch (_: Exception) {
                errorMessage = "Couldn't log the meal. Nothing was saved — try again."
            } finally {
                isLogging = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${meal.mealType} · ${MealPlanDay.date(meal.date)?.let { formatMediumDay(it) } ?: meal.date}", fontSize = 14.sp, color = TextSecondaryColor)
                Text("Review & Log", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                Text("Nothing is logged until you tap Log${backfillSuffix(logDate, " · logging to ")}", fontSize = 13.sp, color = TextSecondaryColor)
            }

            FoodLogCard {
                Text("Log to", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                Spacer(Modifier.height(8.dp))
                MealTypeChips(selected = selectedMeal, onSelect = { selectedMeal = it })
            }

            FoodLogCard {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Meal Total", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                    Spacer(Modifier.weight(1f))
                    Text("${foods.mealTotalCalories} cal", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FLPrimary)
                }
                Spacer(Modifier.height(12.dp))
                FoodLogMacroSummary(protein = foods.mealTotalProtein, carbs = foods.mealTotalCarbs, fat = foods.mealTotalFat)
            }

            FoodLogCard(padding = 0) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp, bottom = if (foods.isEmpty()) 14.dp else 8.dp)) {
                    Text("Foods", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                    Spacer(Modifier.weight(1f))
                    if (foods.isNotEmpty()) Text("${foods.size} item${if (foods.size == 1) "" else "s"}", fontSize = 13.sp, color = TextSecondaryColor)
                }
                if (foods.isEmpty()) {
                    Text(
                        "Everything was removed — add a food back or go back to pick another meal.",
                        fontSize = 13.sp, color = TextSecondaryColor, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)
                    )
                } else {
                    foods.forEach { food ->
                        SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                        StagedFoodRow(
                            food = food,
                            onRemove = { foods = foods.filter { it.id != food.id } },
                            onServings = { setServings(food.id, it) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(White, RoundedCornerShape(12.dp))
                    .clickable { showSourceSheet = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.AddCircle, null, tint = FLPrimary, modifier = Modifier.size(20.dp))
                Text("Add Another Food", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary, modifier = Modifier.weight(1f))
                Icon(Icons.Default.Search, null, tint = FLPrimary, modifier = Modifier.size(16.dp))
                Icon(Icons.Default.QrCodeScanner, null, tint = FLPrimary, modifier = Modifier.size(16.dp))
                Icon(Icons.Default.Edit, null, tint = FLPrimary, modifier = Modifier.size(16.dp))
            }

            FoodLogPrimaryButton(
                text = "Log ${foods.size} item${if (foods.size == 1) "" else "s"} to ${selectedMeal.displayName}",
                enabled = canLog, loading = isLogging, onClick = ::logMeal
            )
            errorMessage?.let { FoodLogErrorText(it, modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp), centered = true) }
        }
    }

    if (showSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showSourceSheet = false }, containerColor = BackgroundColor) {
            AddFoodSourceSheetContent(
                onPick = { route -> showSourceSheet = false; onNavigate(route) },
                mealType = selectedMeal,
                date = FoodLoggingRoutes.today(),
                mode = FoodLogFlow.CAPTURE.wire
            )
        }
    }
}

/** The "Add Food" source list (iOS `sourcePickerSheet`): search / scan / recents / templates / manual, all in the given mode. */
@Composable
fun AddFoodSourceSheetContent(onPick: (String) -> Unit, mealType: MealType, date: String, mode: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add Food", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, modifier = Modifier.padding(horizontal = 20.dp))
        SourceRow(Icons.Default.Search, "Search Foods", "Find a food in the database") { onPick(FoodLoggingRoutes.foodSearch(mealType, date, mode = mode)) }
        SourceRow(Icons.Default.QrCodeScanner, "Scan Barcode", "Look up a packaged food") { onPick(FoodLoggingRoutes.barcodeScanner(mealType, date, mode)) }
        SourceRow(Icons.Default.History, "Recent & My Foods", "Reuse something you've logged or saved") { onPick(FoodLoggingRoutes.recentFoods(mealType, date, mode)) }
        SourceRow(Icons.Default.ViewList, "Meal Templates", "Reuse a meal you saved as a template") { onPick(FoodLoggingRoutes.mealTemplates(mealType, date, mode)) }
        SourceRow(Icons.Default.Edit, "Enter Manually", "Type in a food and its macros") { onPick(FoodLoggingRoutes.quickAdd(mealType, date, mode = mode)) }
    }
}

@Composable
private fun SourceRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    FoodLogLinkRow(title = title, subtitle = subtitle, icon = icon, onClick = onClick)
}

/** Same row idiom as the meal builder: portion line, trash, and a servings stepper that rescales the item live. */
@Composable
fun StagedFoodRow(food: MealSlotFood, onRemove: () -> Unit, onServings: (Double) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(food.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                Text(food.portionLabel, fontSize = 12.sp, color = TextSecondaryColor)
            }
            Text("${food.totalCalories.roundToInt()} cal", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Delete, "Remove", tint = Color.Red, modifier = Modifier.size(18.dp).clickable(onClick = onRemove))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Servings", fontSize = 12.sp, color = TextSecondaryColor)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.RemoveCircle, "Fewer", tint = FLPrimary, modifier = Modifier.size(20.dp).clickable { onServings(maxOf(food.servings - 0.5, 0.5)) })
            Text(stagedServingsLabel(food.servings), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.widthIn(min = 34.dp))
            Icon(Icons.Default.AddCircle, "More", tint = FLPrimary, modifier = Modifier.size(20.dp).clickable { onServings(food.servings + 0.5) })
        }
    }
}

fun stagedServingsLabel(servings: Double): String =
    if (servings % 1.0 == 0.0) servings.toInt().toString() else String.format(Locale.US, "%.1f", servings)

fun CapturedFood.toMealSlotFood(): MealSlotFood = MealSlotFood(
    foodId = foodId, name = name, servings = servings, servingSize = servingSize, servingUnit = servingUnit,
    calories = calories, protein = protein, fat = fat, carbs = carbs
)
