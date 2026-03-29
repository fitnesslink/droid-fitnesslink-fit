package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.ProgressBG
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.MealPlanViewModel

@Composable
fun NutritionSummaryScreen(
    onBack: () -> Unit
) {
    val viewModel: MealPlanViewModel = viewModel()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        // Title
        Text(
            text = "Nutrition Summary",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryColor,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        // Tab selector
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.padding(horizontal = 20.dp),
            containerColor = BackgroundColor,
            contentColor = FLPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = FLPrimary
                )
            }
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("Daily", modifier = Modifier.padding(vertical = 12.dp), fontSize = 14.sp)
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("Weekly", modifier = Modifier.padding(vertical = 12.dp), fontSize = 14.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedTab == 0) {
                DailyView(viewModel)
            } else {
                WeeklyView(viewModel)
            }
        }
    }
}

@Composable
private fun DailyView(viewModel: MealPlanViewModel) {
    // Day selector
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DayOfWeek.entries.forEach { day ->
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 36.dp)
                    .background(
                        if (viewModel.selectedDay == day) FLPrimary else White,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { viewModel.selectedDay = day },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.short,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (viewModel.selectedDay == day) White else TextPrimaryColor
                )
            }
        }
    }

    // Donut chart
    DonutChart(
        calories = viewModel.caloriesForDay(viewModel.selectedDay),
        protein = viewModel.proteinForDay(viewModel.selectedDay),
        fat = viewModel.fatForDay(viewModel.selectedDay),
        carbs = viewModel.carbsForDay(viewModel.selectedDay),
        goal = viewModel.goal.calorieGoal,
        modifier = Modifier.padding(horizontal = 20.dp)
    )

    // Macro breakdown
    MacroBreakdown(
        protein = viewModel.proteinForDay(viewModel.selectedDay),
        fat = viewModel.fatForDay(viewModel.selectedDay),
        carbs = viewModel.carbsForDay(viewModel.selectedDay),
        modifier = Modifier.padding(horizontal = 20.dp)
    )

    // By Meal breakdown
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(White, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "By Meal",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimaryColor
        )
        MealType.entries.forEach { meal ->
            viewModel.slotFor(viewModel.selectedDay, meal)?.let { slot ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = slot.recipeName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimaryColor
                        )
                        Text(
                            text = meal.displayName,
                            fontSize = 12.sp,
                            color = TextSecondaryColor
                        )
                    }
                    Text(
                        text = "${slot.calories} cal",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FLPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyView(viewModel: MealPlanViewModel) {
    val summaries = viewModel.dailySummaries

    // Weekly calorie bar chart
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(White, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Calories by Day",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimaryColor
        )

        val maxCal = summaries.maxOf { it.totalCalories }.coerceAtLeast(1)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            summaries.forEach { summary ->
                val barHeight = (summary.totalCalories.toFloat() / maxCal * 120).coerceAtLeast(4f)
                val barColor = if (summary.totalCalories > summary.goal) OrangeTheme else FLPrimary

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = "${summary.totalCalories}",
                        fontSize = 9.sp,
                        color = TextSecondaryColor
                    )
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(barHeight.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = summary.day.short,
                        fontSize = 11.sp,
                        color = TextSecondaryColor
                    )
                }
            }
        }

        // Legend
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(FLPrimary, CircleShape)
            )
            Text("Under goal", fontSize = 11.sp, color = TextSecondaryColor)
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(OrangeTheme, CircleShape)
            )
            Text("Over goal", fontSize = 11.sp, color = TextSecondaryColor)
        }
    }

    // Weekly averages
    val count = summaries.size.coerceAtLeast(1)
    val avgCalories = summaries.sumOf { it.totalCalories } / count
    val avgProtein = summaries.sumOf { it.totalProtein } / count
    val avgFat = summaries.sumOf { it.totalFat } / count
    val avgCarbs = summaries.sumOf { it.totalCarbs } / count

    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Weekly Averages",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimaryColor
        )

        DonutChart(
            calories = avgCalories,
            protein = avgProtein,
            fat = avgFat,
            carbs = avgCarbs,
            goal = viewModel.goal.calorieGoal
        )
    }

    MacroBreakdown(
        protein = avgProtein,
        fat = avgFat,
        carbs = avgCarbs,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

@Composable
private fun DonutChart(
    calories: Int,
    protein: Double,
    fat: Double,
    carbs: Double,
    goal: Int,
    modifier: Modifier = Modifier
) {
    val total = protein * 4 + fat * 9 + carbs * 4
    val pFrac = if (total > 0) (protein * 4 / total).toFloat() else 0f
    val fFrac = if (total > 0) (fat * 9 / total).toFloat() else 0f
    val cFrac = if (total > 0) (carbs * 4 / total).toFloat() else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(160.dp)
        ) {
            Canvas(modifier = Modifier.size(160.dp)) {
                val strokeWidth = 20.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                val arcSize = Size(diameter, diameter)

                // Background
                drawArc(
                    color = ProgressBG,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )

                if (total > 0) {
                    // Protein arc
                    drawArc(
                        color = BlueTheme,
                        startAngle = -90f,
                        sweepAngle = pFrac * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                    // Fat arc
                    drawArc(
                        color = OrangeTheme,
                        startAngle = -90f + pFrac * 360f,
                        sweepAngle = fFrac * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                    // Carbs arc
                    drawArc(
                        color = PurpleTheme,
                        startAngle = -90f + (pFrac + fFrac) * 360f,
                        sweepAngle = cFrac * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$calories",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryColor
                )
                Text(
                    text = "of $goal cal",
                    fontSize = 12.sp,
                    color = TextSecondaryColor
                )
            }
        }
    }
}

@Composable
private fun MacroBreakdown(
    protein: Double,
    fat: Double,
    carbs: Double,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MacroCard("Protein", protein, "g", BlueTheme, Modifier.weight(1f))
        MacroCard("Fat", fat, "g", OrangeTheme, Modifier.weight(1f))
        MacroCard("Carbs", carbs, "g", PurpleTheme, Modifier.weight(1f))
    }
}

@Composable
private fun MacroCard(
    name: String,
    value: Double,
    unit: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(White, RoundedCornerShape(12.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(
            text = "${value.toInt()}$unit",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryColor
        )
        Text(
            text = name,
            fontSize = 12.sp,
            color = TextSecondaryColor
        )
    }
}
