package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.DayOfWeek
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.ProgressBG
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.MealPlanViewModel

@Composable
fun MealPlanScreen(
    onBack: () -> Unit,
    onNavigateToMealSlotDetail: (String, String) -> Unit = { _, _ -> },
    onNavigateToNutritionSummary: () -> Unit = {},
    onNavigateToGroceryList: () -> Unit = {}
) {
    val viewModel: MealPlanViewModel = viewModel()

    LaunchedEffect(Unit) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        // Title
        Text(
            text = "Meal Plan",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryColor,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        // Day selector tabs
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DayOfWeek.entries.forEach { day ->
                DayTab(
                    day = day,
                    calories = viewModel.caloriesForDay(day),
                    isSelected = viewModel.selectedDay == day,
                    onClick = { viewModel.selectedDay = day }
                )
            }
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Daily calorie header
            DailyCalorieHeader(
                dayName = viewModel.selectedDay.fullName,
                calories = viewModel.caloriesForDay(viewModel.selectedDay),
                goal = viewModel.goal.calorieGoal
            )

            // Meal slots
            MealType.entries.forEach { meal ->
                val slot = viewModel.slotFor(viewModel.selectedDay, meal)
                MealSlotCard(
                    mealType = meal,
                    slot = slot,
                    onClick = {
                        onNavigateToMealSlotDetail(viewModel.selectedDay.name, meal.name)
                    }
                )
            }

            // Navigation links
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(White, RoundedCornerShape(12.dp))
                        .clickable(onClick = onNavigateToNutritionSummary)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Summary",
                        tint = TextPrimaryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Summary", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(White, RoundedCornerShape(12.dp))
                        .clickable(onClick = onNavigateToGroceryList)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Grocery List",
                        tint = TextPrimaryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Grocery List", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                }
            }
        }
    }
}

@Composable
private fun DayTab(
    day: DayOfWeek,
    calories: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .size(52.dp)
            .background(
                if (isSelected) FLPrimary else White,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = day.short,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) White else TextPrimaryColor
        )
        Text(
            text = "$calories",
            fontSize = 11.sp,
            color = if (isSelected) White else TextSecondaryColor
        )
    }
}

@Composable
private fun DailyCalorieHeader(
    dayName: String,
    calories: Int,
    goal: Int
) {
    val progress = if (goal > 0) (calories.toFloat() / goal).coerceAtMost(1f) else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(White, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = dayName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor
            )
            Text(
                text = "$calories / $goal cal",
                fontSize = 14.sp,
                color = TextSecondaryColor
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp)
        ) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(44.dp),
                color = ProgressBG,
                strokeWidth = 6.dp,
                strokeCap = StrokeCap.Round
            )
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(44.dp),
                color = FLPrimary,
                strokeWidth = 6.dp,
                strokeCap = StrokeCap.Round
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryColor
            )
        }
    }
}

@Composable
private fun MealSlotCard(
    mealType: MealType,
    slot: com.fitnesslink.fit.model.MealSlot?,
    onClick: () -> Unit
) {
    val (mealIcon, mealColor) = when (mealType) {
        MealType.BREAKFAST -> "sunrise" to OrangeTheme
        MealType.LUNCH -> "sun" to FLPrimary
        MealType.DINNER -> "moon" to PurpleTheme
        MealType.SNACK -> "leaf" to BlueTheme
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(White, RoundedCornerShape(12.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = when (mealType) {
                        MealType.BREAKFAST -> "\u2600"
                        MealType.LUNCH -> "\u2600\uFE0F"
                        MealType.DINNER -> "\uD83C\uDF19"
                        MealType.SNACK -> "\uD83C\uDF43"
                    },
                    fontSize = 14.sp
                )
                Text(
                    text = mealType.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryColor
                )
            }
            if (slot != null) {
                Text(
                    text = "${slot.calories} cal",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = FLPrimary
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        if (slot != null) {
            // Filled slot
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = slot.recipeName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimaryColor
                        )
                        if (slot.isAISuggestion) {
                            Text(
                                text = "\u2728",
                                fontSize = 12.sp
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MacroLabel("P", slot.protein, BlueTheme)
                        MacroLabel("F", slot.fat, OrangeTheme)
                        MacroLabel("C", slot.carbs, PurpleTheme)
                    }
                }
                Text(
                    text = ">",
                    fontSize = 14.sp,
                    color = MediumGray
                )
            }
        } else {
            // Empty slot
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add meal",
                    tint = FLPrimary.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Add meal",
                    fontSize = 14.sp,
                    color = TextSecondaryColor
                )
            }
        }
    }
}

@Composable
private fun MacroLabel(
    letter: String,
    value: Double,
    color: androidx.compose.ui.graphics.Color
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = letter,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = "${value.toInt()}g",
            fontSize = 11.sp,
            color = TextSecondaryColor
        )
    }
}
