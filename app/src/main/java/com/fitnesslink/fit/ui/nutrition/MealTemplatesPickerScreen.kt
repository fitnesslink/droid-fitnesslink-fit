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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.MealSlotFood
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.dto.CreateMealSlotFoodRequest
import com.fitnesslink.fit.network.dto.CreateMealTemplateRequest
import com.fitnesslink.fit.network.dto.MealTemplate
import com.fitnesslink.fit.network.dto.MealTemplateFood
import com.fitnesslink.fit.network.dto.RowVersionBody
import com.fitnesslink.fit.network.dto.UpdateMealTemplateRequest
import com.fitnesslink.fit.nutrition.CapturedFood
import com.fitnesslink.fit.nutrition.FoodCaptureBus
import com.fitnesslink.fit.nutrition.FoodEntryBatch
import com.fitnesslink.fit.nutrition.FoodLogStore
import com.fitnesslink.fit.nutrition.MealBuilderItem
import com.fitnesslink.fit.nutrition.MealBuilderStore
import com.fitnesslink.fit.nutrition.deepCopied
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * FTNSSLNK-219 — Meal templates: picker + management (iOS
 * `MealTemplatesPickerView`). Same-meal-type templates float to the top.
 * Applying is client-side: in a staging flow the template's foods become
 * deep copies for the meal builder / capture bus; in logging mode they
 * become an atomic diary batch for the target day + meal. Management lives
 * inline on user-owned rows (rename / edit items / delete, rowVersion-guarded).
 */
@Composable
fun MealTemplatesPickerScreen(
    mealType: MealType,
    date: String,
    flow: FoodLogFlow,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val logDate = parseLogDate(date)
    var templates by remember { mutableStateOf<List<MealTemplate>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLogging by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<MealTemplate?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<MealTemplate?>(null) }
    var editing by remember { mutableStateOf<MealTemplate?>(null) }

    suspend fun load() {
        isLoading = true
        try {
            templates = ApiClient.nutritionApi.getMealTemplates()
            errorMessage = null
        } catch (_: ApiError.Offline) {
            errorMessage = "You're offline — templates need a connection."
        } catch (_: Exception) {
            errorMessage = "Couldn't load your templates. Try again."
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    val ordered = templates.sortedByDescending { MealType.fromWire(it.mealType) == mealType }

    fun apply(template: MealTemplate) {
        val foods = FoodEntryBatch.templateFoods(template).deepCopied()
        when (flow) {
            FoodLogFlow.ADD_TO_MEAL -> {
                foods.forEach {
                    MealBuilderStore.add(
                        MealBuilderItem(
                            foodId = it.foodId, name = it.name, servings = it.servings, servingSize = it.servingSize,
                            servingUnit = it.servingUnit, calories = it.calories, protein = it.protein, fat = it.fat, carbs = it.carbs
                        )
                    )
                }
                onBack()
            }
            FoodLogFlow.CAPTURE -> {
                foods.forEach {
                    FoodCaptureBus.push(
                        CapturedFood(
                            foodId = it.foodId, name = it.name, servings = it.servings, servingSize = it.servingSize,
                            servingUnit = it.servingUnit, calories = it.calories, protein = it.protein, fat = it.fat, carbs = it.carbs
                        )
                    )
                }
                onBack()
            }
            FoodLogFlow.LOG -> {
                if (isLogging) return
                isLogging = true
                errorMessage = null
                val entries = FoodEntryBatch.fromMealSlotFoods(foods, mealType, logDate)
                scope.launch {
                    try {
                        FoodLogStore.logBatch(entries)
                        onBack()
                    } catch (_: ApiError.Offline) {
                        errorMessage = "You're offline — logging a template needs a connection."
                    } catch (_: Exception) {
                        errorMessage = "Couldn't log that template. Nothing was saved — try again."
                    } finally {
                        isLogging = false
                    }
                }
            }
        }
    }

    suspend fun rename(template: MealTemplate, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == template.name) return
        try {
            ApiClient.nutritionApi.updateMealTemplate(template.id, UpdateMealTemplateRequest(rowVersion = template.rowVersion, name = trimmed))
            load()
        } catch (e: ApiError.ServerError) {
            errorMessage = if (e.isConflict) "That template changed on another device. The list has been refreshed — try again." else "Couldn't rename the template. Try again."
            if (e.isConflict) load()
        } catch (_: Exception) {
            errorMessage = "Couldn't rename the template. Try again."
        }
    }

    suspend fun delete(template: MealTemplate) {
        try {
            ApiClient.nutritionApi.deleteMealTemplate(template.id, RowVersionBody(template.rowVersion))
            load()
        } catch (e: ApiError.ServerError) {
            errorMessage = if (e.isConflict) "That template changed on another device. The list has been refreshed — try again." else "Couldn't delete the template. Try again."
            if (e.isConflict) load()
        } catch (_: ApiError.NotFound) {
            load() // already gone
        } catch (_: Exception) {
            errorMessage = "Couldn't delete the template. Try again."
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Meal Templates", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            Text(
                if (flow.stagesIntoMeal) "Adding to the meal you're building" else "Logging to ${mealType.displayName}${backfillSuffix(logDate)}",
                fontSize = 12.sp, color = TextSecondaryColor
            )
        }
        when {
            isLoading && templates.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FLPrimary) }
            templates.isEmpty() -> Column {
                FoodLogEmptyState(
                    icon = Icons.Default.ViewList, title = "No meal templates yet",
                    message = "Build a meal and choose “Save as Template” to reuse it here in one tap."
                )
                errorMessage?.let { Spacer(Modifier.height(12.dp)); FoodLogErrorText(it, modifier = Modifier.fillMaxWidth(), centered = true) }
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                errorMessage?.let { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = OrangeTheme, modifier = Modifier.size(16.dp))
                        Text(message, fontSize = 13.sp, color = OrangeTheme)
                    }
                }
                ordered.forEach { template ->
                    TemplateCard(
                        template = template,
                        actionLabel = if (flow.stagesIntoMeal) "Add to Meal" else "Log to ${mealType.displayName}",
                        busy = isLogging && flow == FoodLogFlow.LOG,
                        onApply = { apply(template) },
                        onRename = { renameText = template.name; renaming = template },
                        onEdit = { editing = template },
                        onDelete = { deleting = template }
                    )
                }
            }
        }
    }

    renaming?.let { template ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename Template") },
            text = {
                Box(modifier = Modifier.fillMaxWidth().background(BackgroundColor, RoundedCornerShape(8.dp)).padding(12.dp)) {
                    BasicTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = TextPrimaryColor))
                }
            },
            confirmButton = { TextButton(onClick = { val name = renameText; renaming = null; scope.launch { rename(template, name) } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }

    deleting?.let { template ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this template?") },
            text = { Text("Meals already planned or logged from it are unaffected.") },
            confirmButton = { TextButton(onClick = { deleting = null; scope.launch { delete(template) } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    editing?.let { template ->
        MealTemplateEditSheet(template = template, onSaved = { scope.launch { load() } }, onDismiss = { editing = null })
    }
}

@Composable
private fun TemplateCard(
    template: MealTemplate,
    actionLabel: String,
    busy: Boolean,
    onApply: () -> Unit,
    onRename: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp))) {
        Row(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 14.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(template.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (template.isSystem) FoodLogBadge("System", color = BlueTheme)
                    MealType.fromWire(template.mealType)?.let { FoodLogBadge(it.displayName, color = PurpleTheme) }
                    Text("${template.foods.size} item${if (template.foods.size == 1) "" else "s"}", fontSize = 12.sp, color = TextSecondaryColor)
                }
            }
            Text("${template.totalCalories.roundToInt()} cal", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FLPrimary)
            if (!template.isSystem) {
                Box {
                    Icon(Icons.Default.MoreVert, "Manage", tint = TextSecondaryColor, modifier = Modifier.padding(start = 4.dp).size(20.dp).clickable { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename() })
                        DropdownMenuItem(text = { Text("Edit Items") }, onClick = { menuOpen = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            template.foods.sortedBy { it.sortOrder }.forEach { food ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(food.name, fontSize = 13.sp, color = TextPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(portionLabel(food), fontSize = 11.sp, color = TextSecondaryColor)
                    Spacer(Modifier.weight(1f))
                    Text("${(food.calories * food.servings).roundToInt()} cal", fontSize = 12.sp, color = TextSecondaryColor)
                }
            }
        }
        Box(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 10.dp)) {
            FoodLogMacroSummary(protein = template.totalProtein, carbs = template.totalCarbs, fat = template.totalFat)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp).background(if (busy) MediumGray else FLPrimary, RoundedCornerShape(10.dp))
                .then(if (busy) Modifier else Modifier.clickable(onClick = onApply)).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            if (busy) {
                CircularProgressIndicator(color = White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.AddCircle, null, tint = White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(actionLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = White)
            }
        }
    }
}

private fun portionLabel(food: MealTemplateFood): String {
    val unit = food.servingUnit?.takeIf { it.isNotEmpty() } ?: "serving"
    if (food.servings == 1.0) return "1 $unit"
    val qty = if (food.servings % 1.0 == 0.0) food.servings.toInt().toString() else String.format(Locale.US, "%.2g", food.servings)
    return "$qty × $unit"
}

/**
 * Edit a user-owned template: name, meal type, per-food servings and food
 * removal. Saving PUTs the FULL foods replace-set with the template's
 * rowVersion (409 → message). Adding new foods to an existing template isn't
 * wired here — save a fresh template from the meal builder instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealTemplateEditSheet(template: MealTemplate, onSaved: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(template.name) }
    var mealTypeSelection by remember { mutableStateOf(MealType.fromWire(template.mealType)) }
    var foods by remember { mutableStateOf(FoodEntryBatch.templateFoods(template)) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val canSave = name.isNotBlank() && foods.isNotEmpty() && !isSaving

    fun save() {
        if (!canSave) return
        isSaving = true
        errorMessage = null
        val trimmed = name.trim()
        scope.launch {
            try {
                ApiClient.nutritionApi.updateMealTemplate(
                    template.id,
                    UpdateMealTemplateRequest(
                        rowVersion = template.rowVersion,
                        name = if (trimmed == template.name) null else trimmed,
                        // null = unchanged server-side, so a cleared type can't be sent — only a changed, concrete selection goes out.
                        mealType = mealTypeSelection?.wireValue?.takeIf { it != template.mealType },
                        foods = foods.map { it.toCreateRequest() }
                    )
                )
                onSaved()
                onDismiss()
            } catch (e: ApiError.ServerError) {
                errorMessage = if (e.isConflict) "This template changed on another device. Close and reopen it to edit the latest version." else "Couldn't save the template. Try again."
            } catch (_: ApiError.Offline) {
                errorMessage = "You're offline — editing a template needs a connection."
            } catch (_: Exception) {
                errorMessage = "Couldn't save the template. Try again."
            } finally {
                isSaving = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!isSaving) onDismiss() }, containerColor = BackgroundColor) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", fontSize = 15.sp, color = if (isSaving) MediumGray else FLPrimary, modifier = Modifier.clickable(enabled = !isSaving, onClick = onDismiss))
                Spacer(Modifier.weight(1f))
                Text("Edit Template", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                Spacer(Modifier.weight(1f))
                if (isSaving) CircularProgressIndicator(color = FLPrimary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Save", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (canSave) FLPrimary else MediumGray, modifier = Modifier.clickable(enabled = canSave) { save() })
            }
            FoodLogCard {
                Text("Name", fontSize = 12.sp, color = TextSecondaryColor)
                BasicTextField(value = name, onValueChange = { name = it }, singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = TextPrimaryColor), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            FoodLogCard {
                Text("Meal Type", fontSize = 12.sp, color = TextSecondaryColor)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AIChipLabel("Any", isSelected = mealTypeSelection == null, modifier = Modifier.clickable { mealTypeSelection = null })
                    MealType.entries.forEach { meal ->
                        AIChipLabel(meal.displayName, isSelected = mealTypeSelection == meal, modifier = Modifier.clickable { mealTypeSelection = meal })
                    }
                }
            }
            FoodLogCard(padding = 0) {
                Text("Foods", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.padding(16.dp))
                foods.forEach { food ->
                    SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                    StagedFoodRow(
                        food = food,
                        onRemove = { foods = foods.filter { it.id != food.id } },
                        onServings = { s -> foods = foods.map { if (it.id == food.id) it.copy(servings = s) else it } }
                    )
                }
                Text(
                    if (foods.isEmpty()) "A template needs at least one food." else "Tap the trash to remove a food. To add foods, save a new template from the meal builder.",
                    fontSize = 12.sp, color = TextSecondaryColor, modifier = Modifier.padding(16.dp)
                )
            }
            errorMessage?.let { FoodLogErrorText(it, modifier = Modifier.padding(horizontal = 20.dp)) }
        }
    }
}

private fun MealSlotFood.toCreateRequest() = CreateMealSlotFoodRequest(
    name = name, servings = servings, servingSize = servingSize, servingUnit = servingUnit,
    calories = calories, protein = protein, fat = fat, carbs = carbs, foodId = foodId
)

/**
 * "Save as Template" entry point for agent C's meal builder: name + optional
 * meal type → `POST /meal-templates` with the staged items (per-serving
 * macros × servings). Calls [onSaved] after the 201.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveMealTemplateSheet(
    items: List<MealBuilderItem>,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
    defaultName: String = "",
    defaultMealType: MealType? = null
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(defaultName) }
    var mealTypeSelection by remember { mutableStateOf(defaultMealType) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val canSave = name.isNotBlank() && items.isNotEmpty() && !isSaving

    fun save() {
        if (!canSave) return
        isSaving = true
        errorMessage = null
        scope.launch {
            try {
                ApiClient.nutritionApi.createMealTemplate(
                    CreateMealTemplateRequest(
                        name = name.trim(),
                        mealType = mealTypeSelection?.wireValue,
                        foods = items.map {
                            CreateMealSlotFoodRequest(
                                name = it.name, servings = it.servings, servingSize = it.servingSize, servingUnit = it.servingUnit,
                                calories = it.calories, protein = it.protein, fat = it.fat, carbs = it.carbs, foodId = it.foodId
                            )
                        }
                    )
                )
                onSaved()
                onDismiss()
            } catch (_: ApiError.Offline) {
                errorMessage = "You're offline — saving a template needs a connection."
            } catch (_: Exception) {
                errorMessage = "Couldn't save the template. Try again."
            } finally {
                isSaving = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!isSaving) onDismiss() }, containerColor = BackgroundColor) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Save as Template", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, modifier = Modifier.padding(horizontal = 20.dp))
            Text("${items.size} item${if (items.size == 1) "" else "s"} · ${items.sumOf { it.calories * it.servings }.roundToInt()} cal", fontSize = 13.sp, color = TextSecondaryColor, modifier = Modifier.padding(horizontal = 20.dp))
            FoodLogCard {
                Text("Template name", fontSize = 12.sp, color = TextSecondaryColor)
                BasicTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = TextPrimaryColor), modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    decorationBox = { inner -> if (name.isEmpty()) Text("e.g. Weekday breakfast", fontSize = 16.sp, color = MediumGray); inner() }
                )
            }
            FoodLogCard {
                Text("Meal Type", fontSize = 12.sp, color = TextSecondaryColor)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AIChipLabel("Any", isSelected = mealTypeSelection == null, modifier = Modifier.clickable { mealTypeSelection = null })
                    MealType.entries.forEach { meal ->
                        AIChipLabel(meal.displayName, isSelected = mealTypeSelection == meal, modifier = Modifier.clickable { mealTypeSelection = meal })
                    }
                }
            }
            FoodLogPrimaryButton(text = "Save Template", enabled = canSave, loading = isSaving, onClick = ::save)
            errorMessage?.let { FoodLogErrorText(it, modifier = Modifier.padding(horizontal = 20.dp)) }
        }
    }
}
