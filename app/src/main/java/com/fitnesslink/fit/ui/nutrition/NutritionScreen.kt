package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.ui.components.HeaderView
import com.fitnesslink.fit.ui.components.SeparatorView
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
import com.fitnesslink.fit.viewmodel.NutritionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NutritionScreen(
    onNavigateToGoalSettings: () -> Unit = {},
    onNavigateToQuickAdd: (String) -> Unit = {},
    onNavigateToFoodEntryDetail: (String) -> Unit = {},
    onNavigateToRecentFoods: () -> Unit = {},
    onNavigateToWeeklyProgress: () -> Unit = {},
    onNavigateToBarcodeScanner: (String) -> Unit = {}
) {
    val viewModel: NutritionViewModel = viewModel()

    LaunchedEffect(Unit) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderView()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Date + settings row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Today",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryColor
                    )
                    Text(
                        text = SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date()),
                        fontSize = 14.sp,
                        color = TextSecondaryColor
                    )
                }
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = FLPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onNavigateToGoalSettings)
                )
            }

            // Calorie ring card
            CalorieRingCard(
                consumed = viewModel.totalCalories,
                goal = viewModel.goal.calorieGoal,
                remaining = viewModel.remainingCalories,
                progress = viewModel.calorieProgress
            )

            // Macro bars card
            MacroBarsCard(
                totalProtein = viewModel.totalProtein,
                proteinTarget = viewModel.goal.proteinTarget,
                totalFat = viewModel.totalFat,
                fatTarget = viewModel.goal.fatTarget,
                totalCarbs = viewModel.totalCarbs,
                carbsTarget = viewModel.goal.carbsTarget
            )

            // Meals section
            Column {
                Text(
                    text = "Meals",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryColor,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))

                MealType.entries.forEach { meal ->
                    MealSectionCard(
                        mealType = meal,
                        entries = viewModel.entriesForMeal(meal),
                        totalCalories = viewModel.caloriesForMeal(meal),
                        onAddClick = { onNavigateToQuickAdd(meal.name) },
                        onScanClick = { onNavigateToBarcodeScanner(meal.name) },
                        onEntryClick = { onNavigateToFoodEntryDetail(it) },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                    )
                }
            }

            // Navigation links
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(White, RoundedCornerShape(12.dp))
                        .clickable(onClick = onNavigateToRecentFoods)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.hourglass),
                        contentDescription = "Recent",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Recent",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimaryColor
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(White, RoundedCornerShape(12.dp))
                        .clickable(onClick = onNavigateToWeeklyProgress)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.exercises),
                        contentDescription = "Weekly",
                        tint = TextPrimaryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Weekly",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimaryColor
                    )
                }
            }
        }
    }
}

@Composable
private fun CalorieRingCard(
    consumed: Int,
    goal: Int,
    remaining: Int,
    progress: Double
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(White, RoundedCornerShape(12.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Calorie ring
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(180.dp)
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(180.dp),
                color = ProgressBG,
                strokeWidth = 14.dp,
                strokeCap = StrokeCap.Round
            )
            androidx.compose.material3.CircularProgressIndicator(
                progress = { progress.toFloat() },
                modifier = Modifier.size(180.dp),
                color = FLPrimary,
                strokeWidth = 14.dp,
                strokeCap = StrokeCap.Round
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$consumed",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryColor
                )
                Text(
                    text = "of $goal cal",
                    fontSize = 12.sp,
                    color = TextSecondaryColor
                )
                Text(
                    text = "$remaining left",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FLPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Goal / Eaten / Remaining row
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$goal", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                Text("Goal", fontSize = 11.sp, color = TextSecondaryColor)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$consumed", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                Text("Eaten", fontSize = 11.sp, color = TextSecondaryColor)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$remaining", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                Text("Remaining", fontSize = 11.sp, color = TextSecondaryColor)
            }
        }
    }
}

@Composable
private fun MacroBarsCard(
    totalProtein: Double,
    proteinTarget: Int,
    totalFat: Double,
    fatTarget: Int,
    totalCarbs: Double,
    carbsTarget: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(White, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Macros",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimaryColor
        )
        MacroBarView(name = "Protein", current = totalProtein, target = proteinTarget, color = BlueTheme)
        MacroBarView(name = "Fat", current = totalFat, target = fatTarget, color = OrangeTheme)
        MacroBarView(name = "Carbs", current = totalCarbs, target = carbsTarget, color = PurpleTheme)
    }
}

@Composable
fun MacroBarView(
    name: String,
    current: Double,
    target: Int,
    color: Color
) {
    val fraction = if (target > 0) (current / target).coerceAtMost(1.0).toFloat() else 0f

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
            Text("${current.toInt()}/${target}g", fontSize = 12.sp, color = TextSecondaryColor)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ProgressBG)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun MealSectionCard(
    mealType: MealType,
    entries: List<FoodEntry>,
    totalCalories: Int,
    onAddClick: () -> Unit,
    onScanClick: () -> Unit,
    onEntryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = mealType.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryColor
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "$totalCalories cal",
                    fontSize = 14.sp,
                    color = TextSecondaryColor
                )
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = "Scan",
                    tint = FLPrimary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onScanClick)
                )
                Image(
                    painter = painterResource(R.drawable.greenplus),
                    contentDescription = "Add",
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onAddClick)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (entries.isEmpty()) {
            Text(
                text = "No entries yet",
                fontSize = 14.sp,
                color = MediumGray,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEntryClick(entry.id) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = entry.name,
                            fontSize = 14.sp,
                            color = TextPrimaryColor
                        )
                        Text(
                            text = "${entry.servingSize.toInt()} ${entry.servingUnit}",
                            fontSize = 12.sp,
                            color = TextSecondaryColor
                        )
                    }
                    Text(
                        text = "${entry.calories} cal",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondaryColor
                    )
                }
                if (index < entries.size - 1) {
                    SeparatorView()
                }
            }
        }
    }
}
