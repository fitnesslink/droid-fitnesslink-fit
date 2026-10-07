package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.nutrition.FoodLogStore
import com.fitnesslink.fit.nutrition.FoodNutrientBasis
import com.fitnesslink.fit.nutrition.ManualFoodForm
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * FTNSSLNK-202: create / edit / delete a saved custom food (iOS
 * `CustomFoodFormView`). A custom food is its own `FoodEntry` row flagged
 * `isCustomTemplate` with a sentinel `loggedAt` — it never appears in a
 * diary day, and the backend's food search merges it above catalog results.
 * Creating here does NOT log anything; edits and deletes never touch entries
 * already logged from it.
 */
@Composable
fun CustomFoodFormScreen(entryId: String, onBack: () -> Unit) {
    val isEditing = entryId.isNotEmpty()
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(ManualFoodForm()) }
    var selectedMeal by remember { mutableStateOf(MealType.SNACK) }
    var existingEntry by remember { mutableStateOf<FoodEntry?>(null) }
    var entryMissing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(entryId) {
        if (!isEditing) return@LaunchedEffect
        val entry = FoodLogStore.entry(entryId)
        if (entry == null) { entryMissing = true; return@LaunchedEffect }
        existingEntry = entry
        form = ManualFoodForm.from(entry)
        selectedMeal = entry.mealType
    }

    fun save() {
        if (!form.isValid || entryMissing || isSaving) return
        isSaving = true
        scope.launch {
            try {
                val existing = existingEntry
                if (existing != null) {
                    // Update the template row in place — same id, sentinel loggedAt and isCustomTemplate untouched.
                    val size = ManualFoodForm.parse(form.servingSize) ?: 1.0
                    FoodLogStore.update(
                        existing.copy(
                            name = form.trimmedName,
                            calories = (ManualFoodForm.parse(form.calories) ?: 0.0).roundToInt(),
                            protein = ManualFoodForm.parse(form.protein) ?: 0.0,
                            fat = ManualFoodForm.parse(form.fat) ?: 0.0,
                            carbs = ManualFoodForm.parse(form.carbs) ?: 0.0,
                            servingSize = size,
                            servingUnit = form.trimmedUnit,
                            mealType = selectedMeal,
                            quantity = size,
                            portionUnit = form.trimmedUnit,
                            portionGramWeight = FoodNutrientBasis.servingGrams(1.0, form.trimmedUnit)?.first
                        )
                    )
                } else {
                    // Create: template row only — nothing is logged to the diary.
                    val template = form.copy(saveAsCustomFood = true).templateEntry(selectedMeal) ?: return@launch
                    FoodLogStore.log(template)
                }
            } finally {
                isSaving = false
            }
            onBack()
        }
    }

    fun deleteTemplate() {
        if (!isEditing) return
        scope.launch {
            FoodLogStore.delete(entryId)
            onBack()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        if (entryMissing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Custom food not found", fontSize = 16.sp, color = TextSecondaryColor) }
            return
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (isEditing) "Edit Custom Food" else "New Custom Food", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                Text(
                    if (isEditing) "Changes apply to future logs only — entries you already logged keep their values"
                    else "Saved foods rank at the top of Food Search and never expire",
                    fontSize = 13.sp, color = TextSecondaryColor
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(White, RoundedCornerShape(12.dp))) {
                Field("Food Name", form.name, { form = form.copy(name = it) }, form, ManualFoodForm.Field.NAME)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                Field("Calories", form.calories, { form = form.copy(calories = it) }, form, ManualFoodForm.Field.CALORIES, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                Field("Protein (g)", form.protein, { form = form.copy(protein = it) }, form, ManualFoodForm.Field.PROTEIN, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                Field("Fat (g)", form.fat, { form = form.copy(fat = it) }, form, ManualFoodForm.Field.FAT, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                Field("Carbs (g)", form.carbs, { form = form.copy(carbs = it) }, form, ManualFoodForm.Field.CARBS, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                Field("Serving Size", form.servingSize, { form = form.copy(servingSize = it) }, form, ManualFoodForm.Field.SERVING_SIZE, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                Field("Serving Unit", form.servingUnit, { form = form.copy(servingUnit = it) }, form, ManualFoodForm.Field.SERVING_UNIT)
            }

            FoodLogCard {
                Text("Default Meal", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                Spacer(Modifier.height(8.dp))
                MealTypeChips(selected = selectedMeal, onSelect = { selectedMeal = it })
            }

            FoodLogPrimaryButton(text = if (isEditing) "Save Changes" else "Create Food", enabled = form.isValid, loading = isSaving, onClick = ::save)
            if (isEditing) FoodLogSecondaryButton(text = "Delete Custom Food", color = Color.Red, onClick = ::deleteTemplate)
        }
    }
}

@Composable
private fun Field(
    label: String, value: String, onValueChange: (String) -> Unit,
    form: ManualFoodForm, field: ManualFoodForm.Field, keyboardType: KeyboardType = KeyboardType.Text
) {
    val error = if (value.trim().isNotEmpty()) form.error(field) else null
    ValidatedFormField(label = label, value = value, onValueChange = onValueChange, error = error, keyboardType = keyboardType)
}
