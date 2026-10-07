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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.dto.FoodPortion
import com.fitnesslink.fit.network.dto.FoodPortionConversionResponse
import com.fitnesslink.fit.network.dto.FoodSearchResult
import com.fitnesslink.fit.nutrition.CapturedFood
import com.fitnesslink.fit.nutrition.FoodCaptureBus
import com.fitnesslink.fit.nutrition.FoodDetailHandoff
import com.fitnesslink.fit.nutrition.FoodLogStore
import com.fitnesslink.fit.nutrition.FoodNutrientBasis
import com.fitnesslink.fit.nutrition.FoodPortionMath
import com.fitnesslink.fit.nutrition.MealBuilderItem
import com.fitnesslink.fit.nutrition.MealBuilderStore
import com.fitnesslink.fit.nutrition.NutritionFavoritesStore
import com.fitnesslink.fit.nutrition.nutrientBasis
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How the Food Detail screen was entered (iOS `FoodDetailView.Mode`).
 * Payloads travel by id / token because string routes can't carry values.
 */
sealed class FoodDetailMode {
    /** A catalog food (search / barcode catalog hit / favorite). Looked up in [FoodDetailHandoff], else `GET /foods/{id}`. */
    data class LogFood(val foodId: String) : FoodDetailMode()
    /** A previously-logged food or saved custom food being re-logged (local DB row). Pre-fills last-used unit + quantity. */
    data class LogTemplate(val entryId: String) : FoodDetailMode()
    /** A template entry parked in [FoodDetailHandoff] (barcode product hit, search template row with no local row). */
    data class Handoff(val token: String) : FoodDetailMode()
    /** Edit an existing logged entry. */
    data class Edit(val entryId: String) : FoodDetailMode()

    val isEditing: Boolean get() = this is Edit

    companion object {
        /**
         * Parse the route's `mode` (contract: `log | edit:{entryId} | addToMeal`, extended with `capture`) and
         * optional `source` (`template:{entryId}` | `handoff:{token}`) against the path's `foodId`.
         */
        fun parse(foodId: String, mode: String?, source: String?): Pair<FoodDetailMode, FoodLogFlow> {
            val m = mode ?: "log"
            if (m.startsWith("edit:")) return Edit(m.removePrefix("edit:")) to FoodLogFlow.LOG
            val flow = FoodLogFlow.parse(m)
            val detail = when {
                source != null && source.startsWith("template:") -> LogTemplate(source.removePrefix("template:"))
                source != null && source.startsWith("handoff:") -> Handoff(source.removePrefix("handoff:"))
                else -> LogFood(foodId)
            }
            return detail to flow
        }
    }
}

/**
 * Legacy entry point kept for `AppNavigation.kt` (`foodEntryDetail/{entryId}`):
 * a thin wrapper over [FoodDetailScreen] in edit mode.
 */
@Composable
fun FoodEntryDetailScreen(entryId: String, onBack: () -> Unit) {
    FoodDetailScreen(mode = FoodDetailMode.Edit(entryId), flow = FoodLogFlow.LOG, mealType = MealType.BREAKFAST, logDate = null, onBack = onBack)
}

/**
 * FTNSSLNK-199: the Food Detail & Edit screen — the single funnel every
 * food-selection flow lands on BEFORE anything is saved. Nothing persists
 * until Save (→ `FoodLogStore`, or the meal builder in [FoodLogFlow.ADD_TO_MEAL]).
 */
@Composable
fun FoodDetailScreen(
    mode: FoodDetailMode,
    flow: FoodLogFlow,
    mealType: MealType,
    logDate: Date?,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        FoodDetailContent(mode = mode, flow = flow, mealType = mealType, logDate = logDate, onDone = onBack)
    }
}

private data class Preview(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val grams: Double? = null,
    val usedDensityFallback: Boolean = false
)

private enum class Micro(val displayName: String, val unit: String) {
    FIBER("Fiber", "g"), SUGARS("Sugars", "g"), SODIUM("Sodium", "mg"), SATURATED_FAT("Saturated Fat", "g"), CHOLESTEROL("Cholesterol", "mg")
}

/**
 * The detail body without the header — embedded inline by the barcode
 * scanner (as iOS's container does) and by [FoodDetailScreen]. The
 * nutrition preview recalculates instantly via `FoodPortionMath`, then
 * trues up against `GET /foods/{id}/convert` (debounced) for catalog foods.
 */
@Composable
fun FoodDetailContent(
    mode: FoodDetailMode,
    flow: FoodLogFlow,
    mealType: MealType,
    logDate: Date?,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isEditing = mode.isEditing
    val isCapturing = flow.stagesIntoMeal && !isEditing

    var name by remember { mutableStateOf("") }
    var selectedMeal by remember { mutableStateOf(mealType) }
    var selectedUnit by remember { mutableStateOf("serving") }
    var quantityText by remember { mutableStateOf("1") }
    var portions by remember { mutableStateOf<List<FoodPortion>>(emptyList()) }
    var nativeUnit by remember { mutableStateOf<String?>(null) }
    var basis by remember { mutableStateOf<FoodNutrientBasis?>(null) }
    var foodId by remember { mutableStateOf<String?>(null) }
    var perUnitCalories by remember { mutableStateOf(0.0) }
    var perUnitProtein by remember { mutableStateOf(0.0) }
    var perUnitFat by remember { mutableStateOf(0.0) }
    var perUnitCarbs by remember { mutableStateOf(0.0) }
    var existingEntry by remember { mutableStateOf<FoodEntry?>(null) }
    var entryMissing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var serverConversion by remember { mutableStateOf<FoodPortionConversionResponse?>(null) }
    var convertJob by remember { mutableStateOf<Job?>(null) }
    var unitMenuOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val favoriteIds by NutritionFavoritesStore.foodIds.collectAsState()

    val quantity = FoodPortionMath.parseQuantity(quantityText)

    fun matches(c: FoodPortionConversionResponse) = c.unit.equals(selectedUnit, ignoreCase = true) && abs(c.quantity - quantity) < 0.0001

    fun scheduleServerTrueUp() {
        serverConversion = null
        convertJob?.cancel()
        val id = foodId ?: return
        if (quantity <= 0) return
        val unit = selectedUnit
        val qty = quantity
        convertJob = scope.launch {
            delay(350)
            val result = try { ApiClient.nutritionApi.convertPortion(id, unit, qty) } catch (_: Exception) { null } ?: return@launch
            if (result.unit.equals(unit, true) && abs(result.quantity - qty) < 0.0001) serverConversion = result
        }
    }

    /** Adopt a catalog food's portion list + per-gram basis; never touches the user's unit/quantity selection. */
    fun apply(food: FoodSearchResult) {
        foodId = food.id
        portions = FoodPortionMath.availablePortions(food.portions)
        basis = food.nutrientBasis
        if (name.isEmpty()) name = food.displayName
    }

    /** Default (unit, quantity) for a fresh catalog food: first household portion at 1, else grams matching the stated serving. */
    fun applyDefaultSelection(food: FoodSearchResult) {
        val first = portions.firstOrNull()
        val b = basis
        when {
            first != null && !first.isStandard -> { selectedUnit = first.unit; quantityText = "1" }
            b != null -> { selectedUnit = "g"; quantityText = FoodPortionMath.formatQuantity(maxOf(b.servingGrams.roundToInt().toDouble(), 1.0)) }
            else -> {
                nativeUnit = "serving"; selectedUnit = "serving"; quantityText = "1"
                perUnitCalories = food.calories; perUnitProtein = food.protein; perUnitFat = food.fat; perUnitCarbs = food.carbs
            }
        }
        scheduleServerTrueUp()
    }

    /** Shared template/edit prefill: last-used unit + quantity, per-unit macros, async upgrade when catalog-linked. */
    fun prefill(entry: FoodEntry) {
        name = entry.name
        val qty = entry.quantity ?: entry.servingSize
        val unit = entry.portionUnit ?: entry.servingUnit
        quantityText = FoodPortionMath.formatQuantity(if (qty > 0) qty else 1.0)
        selectedUnit = unit
        nativeUnit = unit
        val divisor = if (qty > 0) qty else 1.0
        perUnitCalories = entry.calories / divisor
        perUnitProtein = entry.protein / divisor
        perUnitFat = entry.fat / divisor
        perUnitCarbs = entry.carbs / divisor
        val linked = entry.foodId
        if (linked != null) {
            foodId = linked
            scope.launch {
                val food = FoodDetailHandoff.food(linked) ?: try { ApiClient.nutritionApi.getFood(linked) } catch (_: Exception) { null } ?: return@launch
                apply(food)
                scheduleServerTrueUp()
            }
        }
    }

    LaunchedEffect(mode) {
        NutritionFavoritesStore.refreshIfNeeded()
    }

    LaunchedEffect(mode) {
        loading = true
        when (mode) {
            is FoodDetailMode.LogFood -> {
                selectedMeal = mealType
                val food = FoodDetailHandoff.food(mode.foodId) ?: try { ApiClient.nutritionApi.getFood(mode.foodId) } catch (_: Exception) { null }
                if (food == null) { entryMissing = true } else { apply(food); applyDefaultSelection(food) }
            }
            is FoodDetailMode.LogTemplate -> {
                selectedMeal = mealType
                val entry = FoodLogStore.entry(mode.entryId)
                if (entry == null) entryMissing = true else prefill(entry)
            }
            is FoodDetailMode.Handoff -> {
                selectedMeal = mealType
                val entry = FoodDetailHandoff.entry(mode.token)
                if (entry == null) entryMissing = true else prefill(entry)
            }
            is FoodDetailMode.Edit -> {
                val entry = FoodLogStore.entry(mode.entryId)
                if (entry == null) entryMissing = true else { existingEntry = entry; selectedMeal = entry.mealType; prefill(entry) }
            }
        }
        loading = false
    }

    // Live preview: server conversion (when it still matches) → client portion math → linear per-unit scaling.
    val preview: Preview = run {
        val s = serverConversion
        if (s != null && matches(s)) return@run Preview(s.calories, s.protein, s.fat, s.carbs, s.grams, s.usedDensityFallback)
        val b = basis
        val converted = if (b != null) FoodPortionMath.convert(b, portions, selectedUnit, quantity) else null
        if (converted != null) return@run Preview(converted.calories, converted.protein, converted.fat, converted.carbs, converted.grams, converted.usedDensityFallback)
        val gramWeight = FoodPortionMath.portion(selectedUnit, portions)?.gramWeight
        Preview(perUnitCalories * quantity, perUnitProtein * quantity, perUnitFat * quantity, perUnitCarbs * quantity, gramWeight?.let { it * quantity }, false)
    }

    val unitOptions: List<String> = buildList {
        nativeUnit?.let { if (FoodPortionMath.portion(it, portions) == null) add(it) }
        addAll(portions.map { it.unit })
    }

    val canSave = quantity > 0 && name.isNotBlank() && !entryMissing && !saving

    fun microAmount(micro: Micro): Double? {
        val s = serverConversion
        if (s != null && matches(s)) {
            return when (micro) {
                Micro.FIBER -> s.fiber; Micro.SUGARS -> s.sugars; Micro.SODIUM -> s.sodium
                Micro.SATURATED_FAT -> s.saturatedFat; Micro.CHOLESTEROL -> s.cholesterol
            }
        }
        // Stored snapshot: only valid for the exact portion it was logged in.
        val entry = existingEntry ?: return null
        if (!selectedUnit.equals(entry.portionUnit ?: entry.servingUnit, true)) return null
        if (abs(quantity - (entry.quantity ?: entry.servingSize)) >= 0.0001) return null
        return when (micro) {
            Micro.FIBER -> entry.fiber; Micro.SUGARS -> entry.sugars; Micro.SODIUM -> entry.sodium
            Micro.SATURATED_FAT -> entry.saturatedFat; Micro.CHOLESTEROL -> entry.cholesterol
        }
    }

    fun stepValue(): Double = when (selectedUnit.lowercase(Locale.US)) { "g", "ml" -> 10.0; else -> 0.5 }

    fun step(direction: Double) {
        val next = quantity + direction * stepValue()
        if (next <= 0) return
        quantityText = FoodPortionMath.formatQuantity(next)
        scheduleServerTrueUp()
    }

    fun save() {
        if (!canSave) return
        val p = preview
        val matchedPortion = FoodPortionMath.portion(selectedUnit, portions)
        val qty = if (quantity > 0) quantity else 1.0

        if (isCapturing) {
            // Stage into a meal: servings = chosen quantity of the unit; per-serving snapshot = preview ÷ qty.
            when (flow) {
                FoodLogFlow.ADD_TO_MEAL -> MealBuilderStore.add(
                    MealBuilderItem(
                        foodId = foodId, name = name, servings = qty,
                        servingSize = matchedPortion?.gramWeight, servingUnit = selectedUnit,
                        calories = p.calories / qty, protein = p.protein / qty, fat = p.fat / qty, carbs = p.carbs / qty
                    )
                )
                else -> FoodCaptureBus.push(
                    CapturedFood(
                        foodId = foodId, name = name, servings = qty,
                        servingSize = matchedPortion?.gramWeight, servingUnit = selectedUnit,
                        calories = p.calories / qty, protein = p.protein / qty, fat = p.fat / qty, carbs = p.carbs / qty
                    )
                )
            }
            onDone()
            return
        }

        val micros = serverConversion?.takeIf { matches(it) }
        saving = true
        scope.launch {
            try {
                val existing = existingEntry
                if (existing != null) {
                    FoodLogStore.update(
                        existing.copy(
                            name = name, mealType = selectedMeal,
                            calories = p.calories.roundToInt(), protein = p.protein, fat = p.fat, carbs = p.carbs,
                            servingSize = quantity, servingUnit = selectedUnit, quantity = quantity, portionUnit = selectedUnit,
                            portionGramWeight = matchedPortion?.gramWeight ?: existing.portionGramWeight,
                            fiber = micros?.fiber ?: existing.fiber, sugars = micros?.sugars ?: existing.sugars,
                            sodium = micros?.sodium ?: existing.sodium, saturatedFat = micros?.saturatedFat ?: existing.saturatedFat,
                            cholesterol = micros?.cholesterol ?: existing.cholesterol
                        )
                    )
                } else {
                    FoodLogStore.log(
                        FoodEntry(
                            id = UUID.randomUUID().toString().lowercase(Locale.US),
                            name = name,
                            calories = p.calories.roundToInt(), protein = p.protein, fat = p.fat, carbs = p.carbs,
                            servingSize = quantity, servingUnit = selectedUnit,
                            mealType = selectedMeal,
                            loggedAt = FoodEntry.timestamp(logDate),
                            foodId = foodId, quantity = quantity, portionUnit = selectedUnit,
                            portionGramWeight = matchedPortion?.gramWeight,
                            fiber = micros?.fiber, sugars = micros?.sugars, sodium = micros?.sodium,
                            saturatedFat = micros?.saturatedFat, cholesterol = micros?.cholesterol,
                            source = if (foodId != null) FoodEntry.Source.library else null
                        )
                    )
                }
                if (mode is FoodDetailMode.Handoff) FoodDetailHandoff.clearEntry(mode.token)
            } finally {
                saving = false
            }
            onDone()
        }
    }

    fun deleteEntry() {
        val id = (mode as? FoodDetailMode.Edit)?.entryId ?: return
        scope.launch {
            FoodLogStore.delete(id)
            onDone()
        }
    }

    if (entryMissing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (isEditing) "Entry not found" else "Food not found", fontSize = 16.sp, color = TextSecondaryColor)
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (loading && name.isEmpty()) "Loading…" else name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                val existing = existingEntry
                when {
                    isEditing && existing != null -> {
                        Text("Logged ${loggedLabel(existing.loggedAt)}", fontSize = 14.sp, color = TextSecondaryColor)
                        existing.aiBadgeText?.let { badge ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.background(OrangeTheme.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, null, tint = OrangeTheme, modifier = Modifier.size(12.dp))
                                Text(badge, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OrangeTheme)
                            }
                        }
                    }
                    isCapturing -> Text("Adding to the meal you're building", fontSize = 13.sp, color = TextSecondaryColor)
                    else -> Text("Nothing is logged until you save${backfillSuffix(logDate, " · logging to ")}", fontSize = 13.sp, color = TextSecondaryColor)
                }
            }
            // FTNSSLNK-215: star/unstar this catalog food (manual / AI / template foods have no foodId).
            foodId?.let { id ->
                val starred = id.lowercase(Locale.US) in favoriteIds
                Icon(
                    if (starred) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (starred) "Unfavorite" else "Favorite",
                    tint = OrangeTheme,
                    modifier = Modifier.size(28.dp).clickable { scope.launch { NutritionFavoritesStore.toggleFood(id) } }
                )
            }
        }

        // In builder capture mode the meal type is fixed by the slot being built.
        if (!isCapturing) {
            FoodLogCard {
                Text("Meal", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                Spacer(Modifier.height(8.dp))
                MealTypeChips(selected = selectedMeal, onSelect = { selectedMeal = it })
            }
        }

        // Serving card
        FoodLogCard {
            Text("Serving", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Unit", fontSize = 14.sp, color = TextSecondaryColor)
                Spacer(Modifier.weight(1f))
                if (unitOptions.size > 1) {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable { unitMenuOpen = true }
                        ) {
                            Text(selectedUnit, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                            Icon(Icons.Default.UnfoldMore, null, tint = FLPrimary, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = unitMenuOpen, onDismissRequest = { unitMenuOpen = false }) {
                            unitOptions.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(if (unit.equals(selectedUnit, true)) "✓ $unit" else unit) },
                                    onClick = { selectedUnit = unit; unitMenuOpen = false; scheduleServerTrueUp() }
                                )
                            }
                        }
                    }
                } else {
                    Text(selectedUnit, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                }
            }
            Spacer(Modifier.height(12.dp))
            SeparatorView()
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Quantity", fontSize = 14.sp, color = TextSecondaryColor)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.RemoveCircle, "Decrease", tint = FLPrimary, modifier = Modifier.size(24.dp).clickable { step(-1.0) })
                    BoxedNumberField(
                        value = quantityText,
                        onValueChange = { quantityText = it; scheduleServerTrueUp() },
                        modifier = Modifier.width(70.dp),
                        fontSize = 16, fontWeight = FontWeight.SemiBold
                    )
                    Icon(Icons.Default.AddCircle, "Increase", tint = FLPrimary, modifier = Modifier.size(24.dp).clickable { step(1.0) })
                }
            }
        }

        // Nutrition preview card
        FoodLogCard {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Nutrition", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                Spacer(Modifier.weight(1f))
                preview.grams?.let { Text("≈ ${FoodPortionMath.formatQuantity((it * 10).roundToInt() / 10.0)} g", fontSize = 13.sp, color = TextSecondaryColor) }
            }
            Spacer(Modifier.height(12.dp))
            NutritionRow("Calories", "${preview.calories.roundToInt()} cal")
            Spacer(Modifier.height(12.dp)); SeparatorView(); Spacer(Modifier.height(12.dp))
            NutritionRow("Protein", "${formatGrams(preview.protein)}g")
            Spacer(Modifier.height(12.dp)); SeparatorView(); Spacer(Modifier.height(12.dp))
            NutritionRow("Fat", "${formatGrams(preview.fat)}g")
            Spacer(Modifier.height(12.dp)); SeparatorView(); Spacer(Modifier.height(12.dp))
            NutritionRow("Carbs", "${formatGrams(preview.carbs)}g")

            // FTNSSLNK-216: micronutrients for the current (unit, quantity). A micro the catalog
            // doesn't provide reads "Not available" — NEVER 0.
            Spacer(Modifier.height(12.dp)); SeparatorView(); Spacer(Modifier.height(12.dp))
            Text("Micronutrients", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
            Micro.entries.forEachIndexed { index, micro ->
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(micro.displayName, fontSize = 14.sp, color = TextSecondaryColor)
                    Spacer(Modifier.weight(1f))
                    val amount = microAmount(micro)
                    if (amount != null) Text("${formatGrams(amount)} ${micro.unit}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                    else Text("Not available", fontSize = 13.sp, color = TextSecondaryColor)
                }
                if (index < Micro.entries.lastIndex) { Spacer(Modifier.height(10.dp)); SeparatorView() }
            }

            if (preview.usedDensityFallback) {
                Spacer(Modifier.height(12.dp))
                Text("Volume estimated at water density (1 g/mL)", fontSize = 11.sp, color = TextSecondaryColor)
            }
        }

        val saveTitle = when {
            isCapturing -> "Add to Meal"
            isEditing -> "Save Changes"
            else -> "Save to ${selectedMeal.displayName}"
        }
        FoodLogPrimaryButton(text = saveTitle, enabled = canSave, loading = saving, onClick = ::save)
        if (isEditing) FoodLogSecondaryButton(text = "Delete Entry", color = Color.Red, onClick = ::deleteEntry)
    }
}

private fun loggedLabel(date: Date): String =
    if (isToday(date)) SimpleDateFormat("MMM d, h:mm a", Locale.US).format(date)
    else SimpleDateFormat("EEEE, MMM d", Locale.US).format(date)

private fun formatGrams(value: Double): String = FoodPortionMath.formatQuantity((value * 10).roundToInt() / 10.0)

@Composable
fun NutritionRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = TextSecondaryColor)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
    }
}
