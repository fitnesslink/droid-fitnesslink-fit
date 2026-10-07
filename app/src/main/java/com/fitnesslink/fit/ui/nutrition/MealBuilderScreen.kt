package com.fitnesslink.fit.ui.nutrition

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.MealSlotFood
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.dto.DraftMeal
import com.fitnesslink.fit.nutrition.FoodCaptureBus
import com.fitnesslink.fit.nutrition.FoodLogStore
import com.fitnesslink.fit.nutrition.MealBuilderItem
import com.fitnesslink.fit.nutrition.MealBuilderStore
import com.fitnesslink.fit.nutrition.MealPlanStore
import com.fitnesslink.fit.nutrition.NutritionGoalsStore
import com.fitnesslink.fit.nutrition.NutritionSummaryStore
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.navigation.FoodLoggingRoutes
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Meal builder for one planner slot (port of iOS `MealBuilderView`,
 * FTNSSLNK-205/209/219). Stages itemized foods in [MealBuilderStore] from
 * search / barcode / recents / previous meals / templates / manual entry
 * (agent B's pickers capture into the store via `FoodDetailScreen`
 * mode `addToMeal`), shows the live total, and commits through
 * [MealPlanStore.upsert] — or, for a meal composed outside the planner
 * ([allowLogDirectly]), lets the user pick a destination: a planner slot or
 * an immediate diary log via `FoodLogStore.logBatch`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealBuilderScreen(
    date: String,
    mealType: MealType,
    slotId: String? = null,
    seeded: Boolean = false,
    allowLogDirectly: Boolean = false,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val store = MealBuilderStore
    val scope = rememberCoroutineScope()
    val items by store.items.collectAsState()
    val mealName by store.mealName.collectAsState()
    val captureCount by store.captureCount.collectAsState()
    var didBegin by rememberSaveable { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var showAI by remember { mutableStateOf(false) }
    var showDestination by remember { mutableStateOf(false) }
    var showPreviousMeals by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showTemplate by remember { mutableStateOf(false) }
    var templateMessage by remember { mutableStateOf<String?>(null) }
    var templateFailed by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Seed the staging session ONCE per screen instance (survives the picker push/pop).
    LaunchedEffect(Unit) {
        if (didBegin) return@LaunchedEffect
        didBegin = true
        if (!seeded) {
            val existingId = slotId ?: store.activeSlotId
            store.begin(date, mealType, existingId?.let { MealPlanStore.slot(it) })
        }
    }
    // Foods agent B's pickers parked on the capture bus (mode "addToMeal") land here on return.
    LaunchedEffect(Unit) {
        FoodCaptureBus.take().forEach { c ->
            store.add(MealBuilderItem(c.foodId, c.name, c.servings, c.servingSize, c.servingUnit, c.calories, c.protein, c.fat, c.carbs))
        }
    }
    // A captured food closes the picker sheet and reveals the running total.
    LaunchedEffect(captureCount) { if (captureCount > 0) { showSources = false; store.isCapturing = false } }

    fun leave() { store.isCapturing = false; onBack() }
    BackHandler { leave() }

    fun goToPicker(route: String) { store.isCapturing = true; showSources = false; onNavigate(route) }

    fun saveToPlan() {
        if (!store.canSave || isSaving) return
        isSaving = true
        scope.launch {
            MealPlanStore.upsert(store.buildSlot())
            store.clear()
            isSaving = false
            onBack()
        }
    }

    fun logDirectly(targetDate: String, meal: MealType) {
        if (!store.canSave || isSaving) return
        isSaving = true
        scope.launch {
            val entries = store.stagedFoods().map { it.toFoodEntry(meal, targetDate) }
            runCatching { FoodLogStore.logBatch(entries) }
            runCatching { NutritionSummaryStore.invalidate(targetDate) }
            store.clear()
            isSaving = false
            onBack()
        }
    }

    val headerDate = store.date
    val headerMeal = store.mealType

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        PlanTopBar(title = if (store.isEditing) "Edit Meal" else "Build Meal", onBack = ::leave)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (allowLogDirectly) {
                        Text("Build a meal", fontSize = 14.sp, color = TextSecondaryColor)
                        Text("New Meal", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                    } else {
                        Text("${com.fitnesslink.fit.viewmodel.MealPlanViewModel.weekdayFull(headerDate)} · ${com.fitnesslink.fit.viewmodel.MealPlanViewModel.mediumDay(headerDate)}", fontSize = 14.sp, color = TextSecondaryColor)
                        Text(headerMeal.displayName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                    }
                }
                if (!allowLogDirectly) {
                    Box(modifier = Modifier.size(40.dp).background(planMealColor(headerMeal).copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(planMealIcon(headerMeal), contentDescription = null, tint = planMealColor(headerMeal), modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Meal name
            PlanCard(Modifier.padding(horizontal = 20.dp)) {
                Text("Meal name (optional)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
                PlanTextField(mealName, { store.setMealName(it) }, placeholder = if (items.size == 1) items[0].name else "e.g. Power Bowl")
            }

            // Totals
            PlanCard(Modifier.padding(horizontal = 20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Meal Total", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                    Text("${store.totalCalories} cal", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FLPrimary)
                }
                MealMacroSummary(store.totalProtein, store.totalCarbs, store.totalFat)
            }

            // Foods
            Column(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(White, RoundedCornerShape(12.dp))) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp, bottom = if (items.isEmpty()) 14.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Foods", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                    if (items.isNotEmpty()) Text("${items.size} item${if (items.size == 1) "" else "s"}", fontSize = 13.sp, color = TextSecondaryColor)
                }
                if (items.isEmpty()) {
                    Text(
                        "No foods yet — add from search, a barcode, your recents, or enter one manually.",
                        fontSize = 13.sp, color = TextSecondaryColor, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)
                    )
                } else {
                    items.forEachIndexed { index, item ->
                        PlanDivider()
                        StagedFoodRow(item, onRemove = { store.remove(index) }, onServings = { store.setServings(index, it) })
                    }
                }
            }

            // Add sources
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(FLPrimary, RoundedCornerShape(12.dp)).clickable { showSources = true }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.AddCircle, contentDescription = null, tint = White)
                    Text("Add Food", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Search, null, tint = White, modifier = Modifier.size(18.dp))
                    Icon(Icons.Filled.QrCodeScanner, null, tint = White, modifier = Modifier.size(18.dp))
                    Icon(Icons.Filled.Edit, null, tint = White, modifier = Modifier.size(18.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(PurpleTheme, BlueTheme)), RoundedCornerShape(12.dp))
                        .clickable { showAI = true }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = White)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI Suggestion", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = White)
                        Text("Add an idea based on your goals", fontSize = 12.sp, color = White.copy(alpha = 0.85f))
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = White)
                }
            }

            // Save
            val saveTitle = when {
                allowLogDirectly -> "Save Meal…"
                store.isEditing -> "Save Meal"
                else -> "Add to ${headerMeal.displayName}"
            }
            PlanPrimaryButton(saveTitle, enabled = store.canSave && !isSaving, modifier = Modifier.padding(horizontal = 20.dp)) {
                if (allowLogDirectly) showDestination = true else saveToPlan()
            }

            // Save as template (FTNSSLNK-219)
            Column(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.clickable(enabled = store.canSave) { showTemplate = true },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Layers, contentDescription = null, tint = if (store.canSave) FLPrimary else TextSecondaryColor, modifier = Modifier.size(16.dp))
                    Text("Save as Template", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (store.canSave) FLPrimary else TextSecondaryColor)
                }
                templateMessage?.let { Text(it, fontSize = 12.sp, color = if (templateFailed) OrangeTheme else TextSecondaryColor) }
            }

            if (store.isEditing) {
                Text(
                    "Remove Meal", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Red,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clickable {
                        val id = store.activeSlotId
                        scope.launch {
                            if (id != null) MealPlanStore.delete(id)
                            store.clear()
                            onBack()
                        }
                    }
                )
            }
        }
    }

    // Source picker
    if (showSources) {
        ModalBottomSheet(onDismissRequest = { showSources = false }, containerColor = BackgroundColor) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add Food", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.padding(vertical = 8.dp))
                Column(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp))) {
                    SourceRow(Icons.Filled.Search, "Search Foods", "Find a food in the database") { goToPicker(FoodLoggingRoutes.foodSearch(headerMeal, headerDate, mode = ADD_TO_MEAL)) }
                    PlanDivider()
                    SourceRow(Icons.Filled.QrCodeScanner, "Scan Barcode", "Look up a packaged food") { goToPicker(FoodLoggingRoutes.barcodeScanner(headerMeal, headerDate, mode = ADD_TO_MEAL)) }
                    PlanDivider()
                    SourceRow(Icons.Filled.History, "Recent & My Foods", "Reuse something you've logged or saved") { goToPicker(FoodLoggingRoutes.recentFoods(headerMeal, headerDate, mode = ADD_TO_MEAL)) }
                    PlanDivider()
                    SourceRow(Icons.Filled.ViewAgenda, "Previous Meals", "Reuse a meal you logged or planned before") { showSources = false; showPreviousMeals = true }
                    PlanDivider()
                    SourceRow(Icons.Filled.Layers, "Meal Templates", "Reuse a meal you saved as a template") { goToPicker(FoodLoggingRoutes.mealTemplates(headerMeal, headerDate, mode = ADD_TO_MEAL)) }
                    PlanDivider()
                    SourceRow(Icons.Filled.Edit, "Enter Manually", "Type in a food and its macros") { showSources = false; showManual = true }
                }
            }
        }
    }

    if (showManual) {
        PlanManualFoodSheet(prefillName = "", onAdd = { store.add(it); showManual = false }, onDismiss = { showManual = false })
    }

    if (showPreviousMeals) {
        // Agent B's picker: deep-copied foods of a previously logged / planned meal.
        PreviousMealsPickerSheet(
            onPick = { items: List<MealBuilderItem> -> items.forEach { store.add(it) }; showPreviousMeals = false },
            onDismiss = { showPreviousMeals = false },
            mealType = headerMeal
        )
    }

    if (showAI) {
        MealSuggestionsSheet(
            mealType = headerMeal,
            goal = rememberPlannerGoal(),
            onPick = { meal: DraftMeal ->
                store.add(MealBuilderItem(foodId = null, name = meal.recipeName, servings = 1.0, servingSize = null, servingUnit = null,
                    calories = meal.calories.toDouble(), protein = meal.protein, fat = meal.fat, carbs = meal.carbs))
                store.addIngredients(meal.ingredients)
                store.markAISuggested()
                showAI = false
            },
            onDismiss = { showAI = false }
        )
    }

    if (showTemplate) {
        // Agent B's template sheet (POST /meal-templates with the staged foods).
        SaveMealTemplateSheet(
            items = items,
            onSaved = { templateFailed = false; templateMessage = "Saved “${store.derivedName}” to your templates."; showTemplate = false },
            onDismiss = { showTemplate = false },
            defaultName = store.derivedName,
            defaultMealType = if (allowLogDirectly) null else headerMeal
        )
    }

    if (showDestination) {
        MealDestinationSheet(
            initialDate = headerDate, initialMeal = headerMeal,
            onPlan = { d, m -> showDestination = false; store.retarget(d, m); saveToPlan() },
            onLog = { d, m -> showDestination = false; logDirectly(d, m) },
            onDismiss = { showDestination = false }
        )
    }

    if (isSaving) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FLPrimary) }
    }
}

/** `FoodLoggingRoutes` mode that makes agent B's pickers stage into the builder instead of logging. */
private const val ADD_TO_MEAL = "addToMeal"

/** The planner's current calorie / macro goal for seeding AI sheets. */
@Composable
internal fun rememberPlannerGoal(): com.fitnesslink.fit.model.NutritionGoal {
    return remember {
        val local = DatabaseManager.nutritionGoal()
        val engine = runCatching { NutritionGoalsStore.calorieGoal() }.getOrDefault(0)
        if (engine > 0) local.copy(calorieGoal = engine) else local
    }
}

@Composable
internal fun MealMacroSummary(protein: Double, carbs: Double, fat: Double) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MacroPill("Protein", protein, BlueTheme, Modifier.weight(1f))
        MacroPill("Carbs", carbs, PurpleTheme, Modifier.weight(1f))
        MacroPill("Fat", fat, OrangeTheme, Modifier.weight(1f))
    }
}

@Composable
private fun MacroPill(name: String, value: Double, color: Color, modifier: Modifier) {
    Column(modifier = modifier.background(color.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(planGrams(value), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
        Text(name, fontSize = 11.sp, color = TextSecondaryColor)
    }
}

@Composable
private fun StagedFoodRow(item: MealBuilderItem, onRemove: () -> Unit, onServings: (Double) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                Text(portionLabel(item), fontSize = 12.sp, color = TextSecondaryColor)
            }
            Text("${item.totalCalories.roundToInt()} cal", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color.Red, modifier = Modifier.size(18.dp).clickable(onClick = onRemove))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Servings", fontSize = 12.sp, color = TextSecondaryColor, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Fewer", tint = FLPrimary, modifier = Modifier.size(22.dp).clickable { onServings((item.servings - 0.5).coerceAtLeast(0.5)) })
            Text(servingsLabel(item.servings), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Icon(Icons.Filled.AddCircle, contentDescription = "More", tint = FLPrimary, modifier = Modifier.size(22.dp).clickable { onServings(item.servings + 0.5) })
        }
        PlanMacroRow(item.totalProtein, item.totalFat, item.totalCarbs)
    }
}

private fun portionLabel(item: MealBuilderItem): String {
    val unit = item.servingUnit?.takeIf { it.isNotEmpty() } ?: "serving"
    if (item.servings == 1.0) return "1 $unit"
    return "${servingsLabel(item.servings)} × $unit"
}

internal fun servingsLabel(servings: Double): String =
    if (servings % 1.0 == 0.0) servings.toInt().toString() else String.format(Locale.US, "%.1f", servings)

@Composable
private fun SourceRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(22.dp))
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
            Text(subtitle, fontSize = 12.sp, color = TextSecondaryColor)
        }
    }
}

/** A staged food → diary entry (iOS `MealBuilderView.logDirectly`). */
internal fun MealSlotFood.toFoodEntry(meal: MealType, date: String): FoodEntry = FoodEntry(
    id = UUID.randomUUID().toString().lowercase(Locale.US),
    name = name,
    calories = totalCalories.roundToInt(),
    protein = totalProtein, fat = totalFat, carbs = totalCarbs,
    servingSize = servings, servingUnit = servingUnit ?: "serving",
    mealType = meal,
    loggedAt = FoodEntry.timestamp(loggingTo = MealPlanDay.parse(date)),
    foodId = foodId, quantity = servings, portionUnit = servingUnit, portionGramWeight = servingSize
)

// MARK: - Manual food entry (shared with the grocery meal builder)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanManualFoodSheet(prefillName: String, onAdd: (MealBuilderItem) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(prefillName) }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var servingSize by remember { mutableStateOf("1") }
    var servingUnit by remember { mutableStateOf("serving") }
    val isValid = name.trim().isNotEmpty() && (calories.toDoubleOrNull() ?: -1.0) >= 0

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BackgroundColor) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PlanSheetTitle("Enter Manually", onCancel = onDismiss, confirm = "Add", confirmEnabled = isValid) {
                onAdd(
                    MealBuilderItem(
                        foodId = null, name = name.trim(), servings = 1.0,
                        servingSize = servingSize.toDoubleOrNull(), servingUnit = servingUnit.trim().ifEmpty { "serving" },
                        calories = calories.toDoubleOrNull() ?: 0.0, protein = protein.toDoubleOrNull() ?: 0.0,
                        fat = fat.toDoubleOrNull() ?: 0.0, carbs = carbs.toDoubleOrNull() ?: 0.0
                    )
                )
            }
            PlanCard {
                Text("FOOD", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                PlanTextField(name, { name = it }, "Name")
                PlanTextField(calories, { calories = it.filter { c -> c.isDigit() || c == '.' } }, "Calories", keyboardType = KeyboardType.Decimal)
            }
            PlanCard {
                Text("MACROS (GRAMS, OPTIONAL)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                PlanTextField(protein, { protein = it.filter { c -> c.isDigit() || c == '.' } }, "Protein", keyboardType = KeyboardType.Decimal)
                PlanTextField(fat, { fat = it.filter { c -> c.isDigit() || c == '.' } }, "Fat", keyboardType = KeyboardType.Decimal)
                PlanTextField(carbs, { carbs = it.filter { c -> c.isDigit() || c == '.' } }, "Carbs", keyboardType = KeyboardType.Decimal)
            }
            PlanCard {
                Text("SERVING", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                PlanTextField(servingSize, { servingSize = it.filter { c -> c.isDigit() || c == '.' } }, "Serving Size", keyboardType = KeyboardType.Decimal)
                PlanTextField(servingUnit, { servingUnit = it }, "Serving Unit")
            }
        }
    }
}

// MARK: - Destination step (FTNSSLNK-209)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealDestinationSheet(
    initialDate: String,
    initialMeal: MealType,
    onPlan: (String, MealType) -> Unit,
    onLog: (String, MealType) -> Unit,
    onDismiss: () -> Unit
) {
    var meal by remember { mutableStateOf(initialMeal) }
    var date by remember { mutableStateOf(initialDate) }
    var picking by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BackgroundColor) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PlanSheetTitle("Save Meal", onCancel = onDismiss, confirm = null)
            Text("MEAL", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealType.entries.forEach { PlanChipLabel(it.displayName, meal == it) { meal = it } }
            }
            Text("DAY", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
            Column(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp))) {
                PlanValueRow("Date", "${com.fitnesslink.fit.viewmodel.MealPlanViewModel.weekdayFull(date)}, ${com.fitnesslink.fit.viewmodel.MealPlanViewModel.mediumDay(date)}") { picking = true }
            }
            PlanPrimaryButton("Add to ${meal.displayName} Plan") { onPlan(date, meal) }
            PlanPrimaryButton("Log to ${meal.displayName} Now", color = BlueTheme) { onLog(date, meal) }
            Text("Add to plan schedules this meal on the chosen day. Log now records it in your food diary right away.", fontSize = 12.sp, color = TextSecondaryColor)
        }
    }
    if (picking) PlanDatePickerDialog(date, onPick = { date = it; picking = false }, onDismiss = { picking = false })
}

@Suppress("unused")
private fun describeTemplateError(e: Throwable): String = when (e) {
    is ApiError.Offline -> "You're offline — saving a template needs a connection."
    else -> "Couldn't save the template. Try again."
}
