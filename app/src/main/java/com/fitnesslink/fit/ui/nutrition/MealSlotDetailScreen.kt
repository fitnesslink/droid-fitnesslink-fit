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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.DayOfWeek
import com.fitnesslink.fit.model.MealSlot
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.DisabledButton
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.MealPlanViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealSlotDetailScreen(
    dayName: String,
    mealTypeName: String,
    onBack: () -> Unit
) {
    val viewModel: MealPlanViewModel = viewModel()
    val day = DayOfWeek.entries.firstOrNull { it.name == dayName } ?: DayOfWeek.MONDAY
    val mealType = MealType.entries.firstOrNull { it.name == mealTypeName } ?: MealType.BREAKFAST

    var recipeName by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var showAISuggestions by remember { mutableStateOf(false) }

    val isValid = recipeName.isNotBlank() && calories.toIntOrNull() != null

    LaunchedEffect(Unit) {
        viewModel.loadData()
        viewModel.slotFor(day, mealType)?.let { slot ->
            recipeName = slot.recipeName
            calories = "${slot.calories}"
            protein = "${slot.protein.toInt()}"
            fat = "${slot.fat.toInt()}"
            carbs = "${slot.carbs.toInt()}"
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
                .padding(top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header info
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = day.fullName,
                    fontSize = 14.sp,
                    color = TextSecondaryColor
                )
                Text(
                    text = mealType.displayName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryColor
                )
            }

            // AI Suggestion Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(
                        Brush.horizontalGradient(listOf(PurpleTheme, BlueTheme)),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { showAISuggestions = true }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "\u2728", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Get AI Suggestion",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = White
                        )
                        Text(
                            text = "Based on your goals and preferences",
                            fontSize = 12.sp,
                            color = White.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Go",
                        tint = White,
                        modifier = Modifier.height(14.dp)
                    )
                }
            }

            // Manual Entry Form
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(White, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Custom Entry",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryColor
                )

                FormField("Recipe Name", recipeName, { recipeName = it }, "e.g., Grilled Chicken Salad")

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormField("Calories", calories, { calories = it }, "0", KeyboardType.Number, Modifier.weight(1f))
                    FormField("Protein (g)", protein, { protein = it }, "0", KeyboardType.Decimal, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormField("Fat (g)", fat, { fat = it }, "0", KeyboardType.Decimal, Modifier.weight(1f))
                    FormField("Carbs (g)", carbs, { carbs = it }, "0", KeyboardType.Decimal, Modifier.weight(1f))
                }
            }

            // Save Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(
                        if (isValid) FLPrimary else DisabledButton,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = isValid) {
                        val slot = MealSlot(
                            day = day,
                            mealType = mealType,
                            recipeName = recipeName,
                            calories = calories.toIntOrNull() ?: 0,
                            protein = protein.toDoubleOrNull() ?: 0.0,
                            fat = fat.toDoubleOrNull() ?: 0.0,
                            carbs = carbs.toDoubleOrNull() ?: 0.0
                        )
                        viewModel.assignSlot(slot)
                        onBack()
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Save Meal",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = White
                )
            }

            // Remove button if slot exists
            if (viewModel.slotFor(day, mealType) != null) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Remove Meal",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Red,
                        modifier = Modifier.clickable {
                            viewModel.removeSlot(day, mealType)
                            onBack()
                        }
                    )
                }
            }
        }
    }

    // AI Suggestions Bottom Sheet
    if (showAISuggestions) {
        val sheetState = rememberModalBottomSheetState()
        val scope = rememberCoroutineScope()

        ModalBottomSheet(
            onDismissRequest = { showAISuggestions = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "AI Suggestions",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryColor
                )

                val suggestions = MockDataProvider.aiSuggestedMeals.filter { it.mealType == mealType }

                if (suggestions.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = "\u2728", fontSize = 36.sp)
                        Text(
                            text = "AI suggestions coming soon",
                            fontSize = 15.sp,
                            color = TextSecondaryColor
                        )
                        Text(
                            text = "Personalized meal recommendations based on your goals, preferences, and dietary needs.",
                            fontSize = 13.sp,
                            color = TextSecondaryColor
                        )
                    }
                } else {
                    suggestions.forEach { suggestion ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BackgroundColor, RoundedCornerShape(12.dp))
                                .clickable {
                                    recipeName = suggestion.recipeName
                                    calories = "${suggestion.calories}"
                                    protein = "${suggestion.protein.toInt()}"
                                    fat = "${suggestion.fat.toInt()}"
                                    carbs = "${suggestion.carbs.toInt()}"
                                    scope.launch {
                                        sheetState.hide()
                                        showAISuggestions = false
                                    }
                                }
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = suggestion.recipeName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimaryColor
                                )
                                Text(
                                    text = "${suggestion.calories} cal",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = FLPrimary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("P: ${suggestion.protein.toInt()}g", fontSize = 12.sp, color = BlueTheme)
                                Text("F: ${suggestion.fat.toInt()}g", fontSize = 12.sp, color = OrangeTheme)
                                Text("C: ${suggestion.carbs.toInt()}g", fontSize = 12.sp, color = PurpleTheme)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormField(
    title: String,
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondaryColor
        )
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = TextPrimaryColor),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .background(BackgroundColor, RoundedCornerShape(8.dp))
                .padding(12.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (text.isEmpty()) {
                        Text(placeholder, fontSize = 15.sp, color = TextSecondaryColor.copy(alpha = 0.5f))
                    }
                    innerTextField()
                }
            }
        )
    }
}
