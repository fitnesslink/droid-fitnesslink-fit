package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.EstimateMacrosRequest
import com.fitnesslink.fit.network.dto.EstimateMacrosResponse
import com.fitnesslink.fit.network.dto.QuickLogCopyRequest
import com.fitnesslink.fit.network.dto.QuickLogMealSource
import com.fitnesslink.fit.nutrition.AIEstimateErrorCopy
import com.fitnesslink.fit.nutrition.CapturedFood
import com.fitnesslink.fit.nutrition.FoodCaptureBus
import com.fitnesslink.fit.nutrition.FoodLogStore
import com.fitnesslink.fit.nutrition.ManualFoodForm
import com.fitnesslink.fit.nutrition.MealBuilderItem
import com.fitnesslink.fit.nutrition.MealBuilderStore
import com.fitnesslink.fit.nutrition.NutritionFeatureFlags
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.navigation.FoodLoggingRoutes
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.DisabledButton
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * FTNSSLNK-202: the manual food entry form (iOS `QuickAddView`) — real
 * numeric validation, "Save to My Foods" (separate `isCustomTemplate` row),
 * Fill with AI (`/foods/estimate-macros`: typed values locked, blanks
 * filled, serving edits rescale), the Quick Log "Copy" card
 * (`/quick-log/sources|copy`) and links to Snap / Describe / Search / Scan /
 * Recent Foods / Recent Meals / Templates. Save actually persists through
 * `FoodLogStore` (or stages into the meal builder in `addToMeal` / `capture`).
 *
 * The legacy `(mealTypeName, onBack, onNavigateToBarcodeScanner,
 * onNavigateToRecentFoods)` signature from `AppNavigation.kt` is kept.
 */
@Composable
fun QuickAddScreen(
    mealTypeName: String,
    onBack: () -> Unit,
    onNavigateToBarcodeScanner: (String) -> Unit = {},
    onNavigateToRecentFoods: () -> Unit = {},
    date: String? = null,
    prefillName: String? = null,
    barcode: String? = null,
    autoFillWithAI: Boolean = false,
    mode: String = "log",
    onNavigate: (String) -> Unit = {}
) {
    val mealType = parseMealTypeArg(mealTypeName)
    val dateArg = date ?: FoodLoggingRoutes.today()
    val logDate = parseLogDate(dateArg)
    val flow = FoodLogFlow.parse(mode)
    val capturing = flow.stagesIntoMeal
    val scope = rememberCoroutineScope()
    val online by NetworkMonitor.isConnected.collectAsState()

    var form by remember { mutableStateOf(ManualFoodForm()) }
    var didSetUp by remember { mutableStateOf(false) }
    var isFilling by remember { mutableStateOf(false) }
    var fillError by remember { mutableStateOf<String?>(null) }
    var pendingServingSize by remember { mutableStateOf<Double?>(null) }
    var servingChangeJob by remember { mutableStateOf<Job?>(null) }
    var quickLogSource by remember { mutableStateOf<QuickLogMealSource?>(null) }
    var isQuickLogging by remember { mutableStateOf(false) }
    var quickLogError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    fun runFill(request: EstimateMacrosRequest, overwriting: Set<ManualFoodForm.Field>) {
        if (form.trimmedName.isEmpty() || isFilling) return
        isFilling = true
        fillError = null
        scope.launch {
            try {
                val response = ApiClient.nutritionAiApi.estimateMacros(request)
                val food = response.food
                if (response.success && food != null) {
                    form = form.applyFill(food, response.source, response.confidence, response.generationId, overwriting)
                } else {
                    fillError = AIEstimateErrorCopy.message(response.errorReasonCode, response.errorDetail)
                }
            } catch (e: ApiError.ServerError) {
                val envelope = e.decodeBody(ApiClient.gson, EstimateMacrosResponse::class.java)
                fillError = AIEstimateErrorCopy.message(envelope?.errorReasonCode ?: e.errorReasonCode, envelope?.errorDetail ?: e.errorDetail)
            } catch (_: ApiError.Offline) {
                fillError = "You're offline — Fill with AI needs a connection."
            } catch (_: Exception) {
                fillError = "Couldn't reach the estimate service. Try again."
            } finally {
                isFilling = false
            }
        }
    }

    /** First fill: every typed number is locked; blanks get filled. */
    fun fill() = runFill(form.estimateRequest(), emptySet())

    /** Fresh call that re-opens the AI-written fields but still locks what the user typed before the first fill. */
    fun reestimate() {
        val current = form.fill ?: return fill()
        runFill(form.estimateRequest(locking = current.lockedFields), current.filledFields)
    }

    fun setUp() {
        if (didSetUp) return
        didSetUp = true
        form = form.copy(
            name = prefillName?.takeIf { it.isNotEmpty() } ?: form.name,
            barcode = barcode,
            // Coming from "we couldn't find this food", saving it for reuse is almost certainly wanted.
            saveAsCustomFood = !prefillName.isNullOrEmpty() || !barcode.isNullOrEmpty()
        )
    }

    LaunchedEffect(Unit) {
        setUp()
        if (autoFillWithAI) fill()
    }

    // FTNSSLNK-213 Quick Log: a copy source for this meal on the target day (diary flows only).
    LaunchedEffect(Unit) {
        if (capturing) return@LaunchedEffect
        val response = try { ApiClient.nutritionApi.getQuickLogSources(dateArg, TimeZone.getDefault().id) } catch (_: Exception) { null }
        quickLogSource = response?.sources?.firstOrNull { MealType.fromWire(it.mealType) == mealType && it.hasSource }
    }

    fun handleServingChange() {
        val current = form.fill ?: return
        val newSize = ManualFoodForm.parse(form.servingSize) ?: return
        if (newSize <= 0 || abs(newSize - current.servingSize) <= 0.0001) return
        if (current.lockedFields.isEmpty()) form = form.rescale(newSize, includingLocked = false)
        else pendingServingSize = newSize
    }

    fun onServingSizeChange(value: String) {
        form = form.copy(servingSize = value)
        // FTNSSLNK-448: debounced so typing "150" doesn't rescale at "1" and "15".
        servingChangeJob?.cancel()
        servingChangeJob = scope.launch { delay(500); handleServingChange() }
    }

    fun onNameChange(value: String) {
        // Renaming after a fill means the estimate no longer describes it: drop the badge + provenance, keep the numbers.
        form = (if (form.fill != null) form.clearFill() else form).copy(name = value)
    }

    fun quickLog() {
        val source = quickLogSource ?: return
        if (isQuickLogging) return
        isQuickLogging = true
        quickLogError = null
        scope.launch {
            try {
                val response = ApiClient.nutritionApi.quickLogCopy(
                    QuickLogCopyRequest(mealType = mealType.wireValue, targetDate = dateArg, sourceDate = source.sourceDate, tz = TimeZone.getDefault().id)
                )
                FoodLogStore.adopt(response.entries)
                onBack()
            } catch (_: ApiError.Offline) {
                quickLogError = "You're offline — quick log needs a connection."
            } catch (_: Exception) {
                quickLogError = "Couldn't copy that meal. Try again."
            } finally {
                isQuickLogging = false
            }
        }
    }

    fun save() {
        val entry = form.loggedEntry(mealType, logDate) ?: return
        if (isSaving) return
        isSaving = true
        scope.launch {
            try {
                val template = form.templateEntry(mealType)
                if (capturing) {
                    // FTNSSLNK-205: stage the manually-entered food (its macros are one serving) instead of logging.
                    val servings = if (entry.servingSize > 0) entry.servingSize else 1.0
                    when (flow) {
                        FoodLogFlow.ADD_TO_MEAL -> MealBuilderStore.add(
                            MealBuilderItem(
                                foodId = null, name = entry.name, servings = servings, servingSize = entry.portionGramWeight, servingUnit = entry.servingUnit,
                                calories = entry.calories / servings, protein = entry.protein / servings, fat = entry.fat / servings, carbs = entry.carbs / servings
                            )
                        )
                        else -> FoodCaptureBus.push(
                            CapturedFood(
                                foodId = null, name = entry.name, servings = servings, servingSize = entry.portionGramWeight, servingUnit = entry.servingUnit,
                                calories = entry.calories / servings, protein = entry.protein / servings, fat = entry.fat / servings, carbs = entry.carbs / servings
                            )
                        )
                    }
                    if (template != null) FoodLogStore.log(template)
                } else {
                    FoodLogStore.log(entry)
                    // Separate IsCustomTemplate row (sentinel loggedAt — never in a diary day).
                    if (template != null) FoodLogStore.log(template)
                    form.cacheableBarcodeProduct()?.let { DatabaseManager.insertBarcodeProduct(it) }
                }
            } finally {
                isSaving = false
            }
            onBack()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (capturing) "Enter Manually" else "Add to ${mealType.displayName}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                if (logDate != null) Text("Logging for ${formatMediumDay(logDate)}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                if (capturing) Text("Adding to the meal you're building", fontSize = 13.sp, color = TextSecondaryColor)
            }

            // Quick Log (FTNSSLNK-213): copy a previous day's whole meal in one tap.
            val source = quickLogSource
            if (!capturing && source != null) {
                QuickLogCard(source, targetDate = logDate ?: Date(), busy = isQuickLogging, error = quickLogError, onCopy = ::quickLog)
            }

            // Form
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(White, RoundedCornerShape(12.dp))) {
                FormRow("Food Name", form.name, ::onNameChange, form, ManualFoodForm.Field.NAME)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormRow("Calories", form.calories, { form = form.copy(calories = it) }, form, ManualFoodForm.Field.CALORIES, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormRow("Protein (g)", form.protein, { form = form.copy(protein = it) }, form, ManualFoodForm.Field.PROTEIN, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormRow("Fat (g)", form.fat, { form = form.copy(fat = it) }, form, ManualFoodForm.Field.FAT, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormRow("Carbs (g)", form.carbs, { form = form.copy(carbs = it) }, form, ManualFoodForm.Field.CARBS, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormRow("Serving Size", form.servingSize, ::onServingSizeChange, form, ManualFoodForm.Field.SERVING_SIZE, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormRow("Serving Unit", form.servingUnit, { form = form.copy(servingUnit = it) }, form, ManualFoodForm.Field.SERVING_UNIT)
            }

            // FTNSSLNK-446: Fill with AI
            if (form.trimmedName.isNotEmpty() || form.fill != null) {
                AIFillCard(form = form, isFilling = isFilling, error = fillError, onFill = ::fill, onReestimate = ::reestimate)
            }

            // FTNSSLNK-492: Snap a Meal (flagged; disabled offline so the user knows why).
            if (!capturing && NutritionFeatureFlags.snapMealEnabled) {
                FoodLogLinkRow(
                    title = "Snap a Meal",
                    subtitle = if (online) "Photo → what's on the plate → items you can edit and log" else "You're offline — Snap a Meal needs a connection",
                    icon = Icons.Default.PhotoCamera, enabled = online
                ) { onNavigate(FoodLoggingRoutes.snapMeal(mealType, dateArg)) }
            }
            // FTNSSLNK-450: one line → several items.
            if (!capturing) {
                FoodLogLinkRow(
                    title = "Describe a Meal with AI",
                    subtitle = "“Chicken burrito bowl with rice and guac” → items you can edit and log",
                    icon = Icons.Default.AutoAwesome
                ) { onNavigate(FoodLoggingRoutes.describeMeal(mealType, dateArg)) }
            }

            // Save to My Foods + barcode note
            FoodLogCard {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Save to My Foods", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                        Text("Reusable later — it'll rank at the top of Food Search", fontSize = 12.sp, color = TextSecondaryColor)
                    }
                    Switch(
                        checked = form.saveAsCustomFood, onCheckedChange = { form = form.copy(saveAsCustomFood = it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = FLPrimary)
                    )
                }
                if (!barcode.isNullOrEmpty()) {
                    Spacer(Modifier.height(8.dp)); SeparatorView(); Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.QrCodeScanner, null, tint = TextSecondaryColor, modifier = Modifier.size(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Barcode $barcode", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                            Text("Scanning this code again will find this food on this device", fontSize = 11.sp, color = TextSecondaryColor)
                        }
                    }
                }
            }

            FoodLogLinkRow(title = "Search Foods", icon = Icons.Default.Search) { onNavigate(FoodLoggingRoutes.foodSearch(mealType, dateArg, barcode, flow.wire)) }
            FoodLogLinkRow(title = "Scan Barcode", icon = Icons.Default.QrCodeScanner) { onNavigateToBarcodeScanner(mealType.name) }
            FoodLogLinkRow(title = "Choose from Recent Foods") { onNavigateToRecentFoods() }
            if (!capturing) {
                // FTNSSLNK-214: browse whole previously-logged meals and re-log one (edit-before-log, batch create).
                FoodLogLinkRow(title = "Re-log a Recent Meal", icon = Icons.Default.History) { onNavigate(FoodLoggingRoutes.recentMeals(mealType, dateArg)) }
                // FTNSSLNK-219: log a whole saved template in one tap (atomic batch).
                FoodLogLinkRow(title = "Log a Meal Template", icon = Icons.Default.ViewList) { onNavigate(FoodLoggingRoutes.mealTemplates(mealType, dateArg)) }
            }

            FoodLogPrimaryButton(text = if (capturing) "Add to Meal" else "Save Entry", enabled = form.isValid, loading = isSaving, onClick = ::save)
        }
    }

    pendingServingSize?.let { newSize ->
        AlertDialog(
            onDismissRequest = { form = form.rescale(newSize, includingLocked = false); pendingServingSize = null },
            title = { Text("Serving size changed") },
            text = { Text("Some values are yours, not the estimate's. Rescale those too, or keep them as typed?") },
            confirmButton = { TextButton(onClick = { form = form.rescale(newSize, includingLocked = true); pendingServingSize = null }) { Text("Rescale everything") } },
            dismissButton = { TextButton(onClick = { form = form.rescale(newSize, includingLocked = false); pendingServingSize = null }) { Text("Keep what I typed") } }
        )
    }
}

@Composable
private fun FormRow(
    label: String, value: String, onValueChange: (String) -> Unit,
    form: ManualFoodForm, field: ManualFoodForm.Field, keyboardType: KeyboardType = KeyboardType.Text
) {
    // Only flag a field once there's something wrong IN it — empty required fields just keep Save disabled.
    val error = if (value.trim().isNotEmpty()) form.error(field) else null
    ValidatedFormField(label = label, value = value, onValueChange = onValueChange, error = error, keyboardType = keyboardType)
}

/** FTNSSLNK-446: before a fill, the button; after, the source / confidence badge, a note on what was kept, and re-estimate. */
@Composable
private fun AIFillCard(form: ManualFoodForm, isFilling: Boolean, error: String?, onFill: () -> Unit, onReestimate: () -> Unit) {
    FoodLogCard {
        val fill = form.fill
        if (fill != null) {
            val color = if (fill.isAiEstimate) OrangeTheme else TextSecondaryColor
            Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(if (fill.isAiEstimate) Icons.Default.AutoAwesome else Icons.Default.History, null, tint = color, modifier = Modifier.size(14.dp))
                Text(fill.badgeText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
            }
            if (fill.isAiEstimate) { Spacer(Modifier.height(8.dp)); Text("AI estimates can be off — check the numbers before saving.", fontSize = 12.sp, color = TextSecondaryColor) }
            if (fill.lockedFields.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text("Kept what you typed: ${ManualFoodForm.lockedFieldNames(fill.lockedFields)}", fontSize = 12.sp, color = TextSecondaryColor) }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isFilling) {
                    CircularProgressIndicator(color = FLPrimary, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text("Re-estimating…", fontSize = 13.sp, color = TextSecondaryColor)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clickable(onClick = onReestimate)) {
                        Icon(Icons.Default.Refresh, null, tint = FLPrimary, modifier = Modifier.size(14.dp))
                        Text("Re-estimate", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().background(if (isFilling) DisabledButton else FLPrimary, RoundedCornerShape(10.dp))
                    .then(if (isFilling) Modifier else Modifier.clickable(onClick = onFill)).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFilling) CircularProgressIndicator(color = White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.AutoAwesome, null, tint = White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isFilling) "Filling in…" else "Fill with AI", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = White)
            }
            Spacer(Modifier.height(8.dp))
            val typed = form.typedNumericFields
            Text(
                if (typed.isEmpty()) "Type a name and any numbers you know — AI fills the rest."
                else "Your ${ManualFoodForm.lockedFieldNames(typed).lowercase(Locale.US)} stay as typed; AI fills the rest to match.",
                fontSize = 12.sp, color = TextSecondaryColor
            )
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FoodLogErrorText(error, modifier = Modifier.weight(1f))
                Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary, modifier = Modifier.clickable { if (form.fill == null) onFill() else onReestimate() })
            }
        }
    }
}

/** FTNSSLNK-213: preview + one-tap copy of this meal from a previous day. */
@Composable
private fun QuickLogCard(source: QuickLogMealSource, targetDate: Date, busy: Boolean, error: String?, onCopy: () -> Unit) {
    FoodLogCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Undo, null, tint = FLPrimary, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(quickLogCopyLabel(source, targetDate), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                Text("${source.entryCount} item${if (source.entryCount == 1) "" else "s"} · ${source.calories.roundToInt()} cal", fontSize = 12.sp, color = TextSecondaryColor)
            }
            if (busy) CircularProgressIndicator(color = FLPrimary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(
                "Copy", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = White,
                modifier = Modifier.background(FLPrimary, RoundedCornerShape(10.dp)).clickable(onClick = onCopy).padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        source.entries.take(4).forEach { entry ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(entry.name, fontSize = 12.sp, color = TextSecondaryColor, maxLines = 1, modifier = Modifier.weight(1f))
                Text("${entry.calories.roundToInt()} cal", fontSize = 12.sp, color = TextSecondaryColor)
            }
        }
        if (source.entries.size > 4) Text("+ ${source.entries.size - 4} more", fontSize = 11.sp, color = TextSecondaryColor)
        error?.let { Spacer(Modifier.height(8.dp)); FoodLogErrorText(it) }
    }
}

/** "Same as yesterday" when the source is the day before the target, "Copy from Jul 12" otherwise (iOS `copyLabel`). */
fun quickLogCopyLabel(source: QuickLogMealSource, target: Date): String {
    val sourceDate = source.sourceDate?.let { MealPlanDay.date(it) } ?: return ""
    val yesterday = Calendar.getInstance().apply { time = target; add(Calendar.DAY_OF_MONTH, -1) }.time
    if (MealPlanDay.string(sourceDate) == MealPlanDay.string(yesterday)) return "Same as yesterday"
    return "Copy from ${SimpleDateFormat("MMM d", Locale.US).format(sourceDate)}"
}

/** Legacy helper kept for callers of the old form (same look as the validated rows, no validation). */
@Composable
fun FormField(label: String, value: String, onValueChange: (String) -> Unit, keyboardType: KeyboardType = KeyboardType.Text) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = TextSecondaryColor)
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, color = TextPrimaryColor),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType), modifier = Modifier.fillMaxWidth()
        )
    }
}
