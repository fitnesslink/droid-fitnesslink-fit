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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.dto.ParseMealRequest
import com.fitnesslink.fit.network.dto.ParseMealResponse
import com.fitnesslink.fit.network.dto.ParsedMealItem
import com.fitnesslink.fit.nutrition.AIEstimateErrorCopy
import com.fitnesslink.fit.nutrition.FoodEntryBatch
import com.fitnesslink.fit.nutrition.FoodLogStore
import com.fitnesslink.fit.nutrition.ManualFoodForm
import com.fitnesslink.fit.nutrition.SnapMealPrefill
import com.fitnesslink.fit.nutrition.SnapMealPrefillHandoff
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.DisabledButton
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

/** One editable AI item (text-backed so partial edits never fight the formatter; parsed on Log). */
data class ReviewRow(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val calories: String,
    val protein: String,
    val fat: String,
    val carbs: String,
    val servingSize: String,
    val servingUnit: String,
    val confidence: String
) {
    val isValid: Boolean get() = name.isNotBlank() && (ManualFoodForm.parse(calories) ?: -1.0) >= 0
    val caloriesValue: Int get() = (ManualFoodForm.parse(calories) ?: 0.0).roundToInt()

    fun toReviewedItem() = FoodEntryBatch.ReviewedItem(
        name = name.trim(), calories = caloriesValue,
        protein = ManualFoodForm.parse(protein) ?: 0.0, fat = ManualFoodForm.parse(fat) ?: 0.0, carbs = ManualFoodForm.parse(carbs) ?: 0.0,
        servingSize = ManualFoodForm.parse(servingSize) ?: 1.0, servingUnit = servingUnit, confidence = confidence
    )

    companion object {
        fun from(item: ParsedMealItem) = ReviewRow(
            name = item.food.displayName,
            calories = item.food.calories.roundToInt().toString(),
            protein = ManualFoodForm.format((item.food.protein * 10).roundToInt() / 10.0),
            fat = ManualFoodForm.format((item.food.fat * 10).roundToInt() / 10.0),
            carbs = ManualFoodForm.format((item.food.carbs * 10).roundToInt() / 10.0),
            servingSize = ManualFoodForm.format(item.food.servingSize),
            servingUnit = item.food.servingUnit,
            confidence = item.confidence
        )
    }
}

/**
 * FTNSSLNK-450 "Describe a meal": one line of text → several AI-estimated
 * items the user reviews, edits, prunes, and logs in one tap through the
 * atomic batch endpoint. Rows carry `source = ai_estimate` (or `ai_photo`
 * when arriving from Snap a Meal), the model's confidence and the shared
 * generation id.
 */
@Composable
fun DescribeMealScreen(
    mealType: MealType,
    date: String,
    prefillToken: String?,
    onBack: () -> Unit,
    onLogged: (fromPhoto: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val logDate = parseLogDate(date)
    val photo: SnapMealPrefill? = remember(prefillToken) { prefillToken?.let { SnapMealPrefillHandoff.get(it) } }
    val fromPhoto = photo != null

    var description by remember { mutableStateOf(photo?.description ?: "") }
    var hasAutoParsed by remember { mutableStateOf(false) }
    var knownTotalText by remember { mutableStateOf("") }
    var selectedMeal by remember { mutableStateOf(mealType) }
    var isParsing by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<ReviewRow>>(emptyList()) }
    var generationId by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf<String?>(null) }
    var isLogging by remember { mutableStateOf(false) }
    var logError by remember { mutableStateOf<String?>(null) }

    val canParse = description.isNotBlank() && !isParsing
    val canLog = rows.isNotEmpty() && rows.all { it.isValid } && !isLogging
    val totalCalories = rows.sumOf { it.caloriesValue }

    fun parse() {
        if (!canParse) return
        isParsing = true
        parseError = null
        logError = null
        val text = description.trim()
        val known = ManualFoodForm.parse(knownTotalText)?.takeIf { it > 0 }
        scope.launch {
            try {
                val response = ApiClient.nutritionAiApi.parseMeal(
                    ParseMealRequest(description = text, mealType = selectedMeal.wireValue, knownTotalCalories = known, sourceGenerationId = photo?.sourceGenerationId)
                )
                applyParse(response)?.let { parseError = it } ?: run {
                    rows = response.items.orEmpty().map { ReviewRow.from(it) }
                    generationId = response.generationId
                    notes = response.notes
                }
            } catch (e: ApiError.ServerError) {
                val envelope = e.decodeBody(ApiClient.gson, ParseMealResponse::class.java)
                parseError = AIEstimateErrorCopy.message(envelope?.errorReasonCode ?: e.errorReasonCode, envelope?.errorDetail ?: e.errorDetail)
            } catch (_: ApiError.Offline) {
                parseError = "You're offline — describing a meal needs a connection."
            } catch (_: Exception) {
                parseError = "Couldn't reach the estimate service. Try again."
            } finally {
                isParsing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        // From a photo the description is already confirmed: estimate straight away.
        if (fromPhoto && !hasAutoParsed && rows.isEmpty()) { hasAutoParsed = true; parse() }
    }

    fun logMeal() {
        if (!canLog) return
        isLogging = true
        logError = null
        val source = if (fromPhoto) FoodEntry.Source.aiPhoto else FoodEntry.Source.aiEstimate
        val entries = FoodEntryBatch.fromReviewedItems(rows.map { it.toReviewedItem() }, selectedMeal, logDate, source, generationId)
        scope.launch {
            try {
                FoodLogStore.logBatch(entries)
                onLogged(fromPhoto)
            } catch (_: ApiError.Offline) {
                logError = "You're offline — logging a described meal needs a connection."
            } catch (_: Exception) {
                logError = "Couldn't log the meal. Nothing was saved — try again."
            } finally {
                isLogging = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (fromPhoto) "Snap a Meal" else "Describe a Meal", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                Text(
                    if (fromPhoto) "Macros for the foods you confirmed. Edit any number before logging."
                    else "Say what you ate in one line — AI splits it into items you can edit before logging.",
                    fontSize = 13.sp, color = TextSecondaryColor
                )
            }

            // From a photo the description is built from the confirmed lines and sent
            // behind the scenes — no text box to review. Before results: progress while
            // estimating, or the error with a retry (fixing the list happens back on the
            // confirm screen).
            if (fromPhoto) {
                if (rows.isEmpty()) {
                    FoodLogCard {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val error = parseError
                            if (error != null) {
                                FoodLogErrorText(error, modifier = Modifier.fillMaxWidth(), centered = true)
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(if (canParse) FLPrimary else DisabledButton, RoundedCornerShape(10.dp))
                                        .then(if (canParse) Modifier.clickable { parse() } else Modifier).padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Refresh, null, tint = White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Try again", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = White)
                                }
                            } else {
                                CircularProgressIndicator(color = FLPrimary, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                Text("Estimating macros…", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
                            }
                        }
                    }
                }
            } else FoodLogCard {
                Box(modifier = Modifier.fillMaxWidth().background(BackgroundColor, RoundedCornerShape(10.dp)).padding(12.dp)) {
                    BasicTextField(
                        value = description, onValueChange = { description = it }, minLines = 2, maxLines = 4,
                        textStyle = TextStyle(fontSize = 16.sp, color = TextPrimaryColor), modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner -> if (description.isEmpty()) Text("Chicken burrito bowl with rice, black beans, and guac", fontSize = 16.sp, color = MediumGray); inner() }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Total calories (if you know)", fontSize = 13.sp, color = TextSecondaryColor)
                    Spacer(Modifier.weight(1f))
                    BoxedNumberField(value = knownTotalText, onValueChange = { knownTotalText = it }, modifier = Modifier.width(90.dp), placeholder = "optional", keyboardType = KeyboardType.Number)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().background(if (canParse) FLPrimary else DisabledButton, RoundedCornerShape(10.dp))
                        .then(if (canParse) Modifier.clickable { parse() } else Modifier).padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isParsing) CircularProgressIndicator(color = White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.AutoAwesome, null, tint = White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isParsing) "Working it out…" else if (rows.isEmpty()) "Break it down" else "Try again", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = White)
                }
                parseError?.let { Spacer(Modifier.height(8.dp)); FoodLogErrorText(it) }
            }

            if (rows.isNotEmpty()) {
                FoodLogCard {
                    Text("Meal", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                    Spacer(Modifier.height(8.dp))
                    MealTypeChips(selected = selectedMeal, onSelect = { selectedMeal = it })
                }

                FoodLogCard(padding = 0) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = OrangeTheme, modifier = Modifier.size(14.dp))
                        Text("AI estimate — check the numbers before logging", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OrangeTheme)
                    }
                    notes?.let { Text(it, fontSize = 12.sp, color = TextSecondaryColor, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) }
                    rows.forEach { row ->
                        SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                        ReviewRowEditor(
                            row = row,
                            onChange = { updated -> rows = rows.map { if (it.id == updated.id) updated else it } },
                            onRemove = { rows = rows.filter { it.id != row.id } }
                        )
                    }
                    SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${rows.size} item${if (rows.size == 1) "" else "s"}", fontSize = 13.sp, color = TextSecondaryColor)
                        Spacer(Modifier.weight(1f))
                        Text("$totalCalories cal total", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                    }
                }

                FoodLogPrimaryButton(
                    text = if (isLogging) "Logging…" else "Log ${rows.size} item${if (rows.size == 1) "" else "s"} to ${selectedMeal.displayName}",
                    enabled = canLog, loading = isLogging, onClick = ::logMeal
                )
                logError?.let { FoodLogErrorText(it, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), centered = true) }
            }
        }
    }
}

/** Null when the response succeeded with items; otherwise the user-facing error copy. */
private fun applyParse(response: ParseMealResponse): String? =
    if (response.success && !response.items.isNullOrEmpty()) null
    else AIEstimateErrorCopy.message(response.errorReasonCode, response.errorDetail)

@Composable
fun ReviewRowEditor(row: ReviewRow, onChange: (ReviewRow) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = row.name, onValueChange = { onChange(row.copy(name = it)) }, singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor), modifier = Modifier.weight(1f),
                decorationBox = { inner -> if (row.name.isEmpty()) Text("Food", fontSize = 15.sp, color = MediumGray); inner() }
            )
            Text(
                row.confidence, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor,
                modifier = Modifier.background(TextSecondaryColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Icon(Icons.Default.Cancel, "Remove ${row.name}", tint = TextSecondaryColor, modifier = Modifier.size(18.dp).clickable(onClick = onRemove))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberFieldWithLabel("cal", row.calories, { onChange(row.copy(calories = it)) }, KeyboardType.Number, Modifier.weight(1f))
            NumberFieldWithLabel("P g", row.protein, { onChange(row.copy(protein = it)) }, KeyboardType.Decimal, Modifier.weight(1f))
            NumberFieldWithLabel("F g", row.fat, { onChange(row.copy(fat = it)) }, KeyboardType.Decimal, Modifier.weight(1f))
            NumberFieldWithLabel("C g", row.carbs, { onChange(row.copy(carbs = it)) }, KeyboardType.Decimal, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Serving", fontSize = 12.sp, color = TextSecondaryColor)
            BoxedNumberField(value = row.servingSize, onValueChange = { onChange(row.copy(servingSize = it)) }, modifier = Modifier.width(56.dp), placeholder = "1", fontSize = 13)
            Box(modifier = Modifier.weight(1f).background(BackgroundColor, RoundedCornerShape(8.dp)).padding(vertical = 6.dp, horizontal = 8.dp)) {
                BasicTextField(
                    value = row.servingUnit, onValueChange = { onChange(row.copy(servingUnit = it)) }, singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = TextPrimaryColor), modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner -> if (row.servingUnit.isEmpty()) Text("serving", fontSize = 13.sp, color = MediumGray); inner() }
                )
            }
        }
    }
}

@Composable
private fun NumberFieldWithLabel(label: String, value: String, onValueChange: (String) -> Unit, keyboardType: KeyboardType, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BoxedNumberField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), keyboardType = keyboardType)
        Text(label, fontSize = 10.sp, color = TextSecondaryColor)
    }
}
