package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.DisabledButton
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

@Composable
fun CustomFoodFormScreen(
    entryId: String,
    onBack: () -> Unit
) {
    val isEditing = entryId.isNotEmpty()

    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var servingSize by remember { mutableStateOf("1") }
    var servingUnit by remember { mutableStateOf("serving") }
    var selectedMeal by remember { mutableStateOf(MealType.SNACK) }

    val isValid = name.isNotEmpty() && calories.toIntOrNull() != null

    LaunchedEffect(entryId) {
        if (isEditing) {
            val food = MockDataProvider.customFoods.firstOrNull { it.id == entryId }
            if (food != null) {
                name = food.name
                calories = "${food.calories}"
                protein = "${food.protein.toInt()}"
                fat = "${food.fat.toInt()}"
                carbs = "${food.carbs.toInt()}"
                servingSize = "${food.servingSize}"
                servingUnit = food.servingUnit
                selectedMeal = food.mealType
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isEditing) "Edit Custom Food" else "New Custom Food",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp)
            )

            // Form fields
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(White, RoundedCornerShape(12.dp))
            ) {
                FormField("Food Name", name, { name = it })
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormField("Calories", calories, { calories = it }, KeyboardType.Number)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormField("Protein (g)", protein, { protein = it }, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormField("Fat (g)", fat, { fat = it }, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormField("Carbs (g)", carbs, { carbs = it }, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormField("Serving Size", servingSize, { servingSize = it }, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormField("Serving Unit", servingUnit, { servingUnit = it })
            }

            // Default meal type
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(White, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Default Meal",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimaryColor
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MealType.entries.forEach { meal ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (selectedMeal == meal) FLPrimary else BackgroundColor,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedMeal = meal }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = meal.displayName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selectedMeal == meal) White else TextPrimaryColor
                            )
                        }
                    }
                }
            }

            // Save button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(
                        if (isValid) FLPrimary else DisabledButton,
                        RoundedCornerShape(12.dp)
                    )
                    .then(if (isValid) Modifier.clickable(onClick = onBack) else Modifier)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isEditing) "Save Changes" else "Create Food",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = White
                )
            }

            // Delete button (only when editing)
            if (isEditing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .background(White, RoundedCornerShape(12.dp))
                        .clickable(onClick = onBack)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Delete Custom Food",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Red
                    )
                }
            }
        }
    }
}
