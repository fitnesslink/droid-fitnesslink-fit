package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.MealType
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

/**
 * Daily / weekly summary of PLANNED meals (port of iOS `NutritionSummaryView`),
 * keyed by date: the day selector spans the viewed Sunday-start week and
 * [date] (optional) jumps the planner to that day's week.
 */
@Composable
fun NutritionSummaryScreen(onBack: () -> Unit, date: String? = null) {
    val viewModel: MealPlanViewModel = viewModel()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(date) { if (date != null) viewModel.showDate(date) else viewModel.loadData() }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        PlanTopBar(title = "Nutrition Summary", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Daily / Weekly segmented control
            Row(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(White, RoundedCornerShape(10.dp)).padding(4.dp)) {
                listOf("Daily", "Weekly").forEachIndexed { index, label ->
                    val selected = selectedTab == index
                    Box(
                        modifier = Modifier.weight(1f).background(if (selected) FLPrimary else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { selectedTab = index }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) White else TextPrimaryColor) }
                }
            }

            if (selectedTab == 0) DailyView(viewModel) else WeeklyView(viewModel)
        }
    }
}

@Composable
private fun DailyView(viewModel: MealPlanViewModel) {
    val date = viewModel.selectedDate
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(viewModel.weekDates) { d ->
            val selected = d == date
            Column(
                modifier = Modifier.width(44.dp).height(40.dp).background(if (selected) FLPrimary else White, RoundedCornerShape(8.dp)).clickable { viewModel.selectedDate = d },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Text(MealPlanViewModel.weekdayShort(d), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) White else TextPrimaryColor)
                Text(MealPlanViewModel.dayNumber(d), fontSize = 11.sp, color = if (selected) White else TextPrimaryColor)
            }
        }
    }
    DonutChart(
        calories = viewModel.caloriesForDate(date), protein = viewModel.proteinForDate(date), fat = viewModel.fatForDate(date),
        carbs = viewModel.carbsForDate(date), goal = viewModel.goal.calorieGoal, modifier = Modifier.padding(horizontal = 20.dp)
    )
    MacroBreakdown(viewModel.proteinForDate(date), viewModel.fatForDate(date), viewModel.carbsForDate(date), Modifier.padding(horizontal = 20.dp))
    PlanCard(Modifier.padding(horizontal = 20.dp)) {
        Text("By Meal", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        val rows = MealType.entries.flatMap { meal -> viewModel.slotsFor(date, meal).map { meal to it } }
        if (rows.isEmpty()) Text("Nothing planned for this day.", fontSize = 13.sp, color = TextSecondaryColor)
        rows.forEach { (meal, slot) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(slot.recipeName.ifEmpty { meal.displayName }, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                    Text(meal.displayName, fontSize = 12.sp, color = TextSecondaryColor)
                }
                Text("${slot.calories} cal", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
            }
        }
    }
}

@Composable
private fun WeeklyView(viewModel: MealPlanViewModel) {
    val summaries = viewModel.dailySummaries
    PlanCard(Modifier.padding(horizontal = 20.dp)) {
        Text("Calories by Day", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        val maxCal = (summaries.maxOfOrNull { it.totalCalories } ?: 1).coerceAtLeast(1)
        Row(modifier = Modifier.fillMaxWidth().height(160.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            summaries.forEach { s ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text("${s.totalCalories}", fontSize = 9.sp, color = TextSecondaryColor)
                    val h = (s.totalCalories.toFloat() / maxCal * 120).coerceAtLeast(4f)
                    Box(modifier = Modifier.fillMaxWidth().height(h.dp).background(if (s.totalCalories > s.goal) OrangeTheme else FLPrimary, RoundedCornerShape(4.dp)))
                    Text(MealPlanViewModel.weekdayShort(s.date), fontSize = 11.sp, color = TextSecondaryColor)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(8.dp).background(FLPrimary, CircleShape)); Text("Under goal", fontSize = 11.sp, color = TextSecondaryColor)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(8.dp).background(OrangeTheme, CircleShape)); Text("Over goal", fontSize = 11.sp, color = TextSecondaryColor)
        }
    }
    val count = summaries.size.coerceAtLeast(1).toDouble()
    val avgP = summaries.sumOf { it.totalProtein } / count
    val avgF = summaries.sumOf { it.totalFat } / count
    val avgC = summaries.sumOf { it.totalCarbs } / count
    Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Weekly Averages", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        DonutChart(calories = (summaries.sumOf { it.totalCalories } / count).toInt(), protein = avgP, fat = avgF, carbs = avgC, goal = viewModel.goal.calorieGoal)
    }
    MacroBreakdown(avgP, avgF, avgC, Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun DonutChart(calories: Int, protein: Double, fat: Double, carbs: Double, goal: Int, modifier: Modifier = Modifier) {
    val total = protein * 4 + fat * 9 + carbs * 4
    val pFrac = if (total > 0) (protein * 4) / total else 0.0
    val fFrac = if (total > 0) (fat * 9) / total else 0.0
    Box(modifier = modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(160.dp)) {
                val stroke = 20.dp.toPx()
                val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(ProgressBG, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                if (total > 0) {
                    drawArc(BlueTheme, -90f, (360 * pFrac).toFloat(), false, Offset(inset, inset), arc, style = Stroke(stroke))
                    drawArc(OrangeTheme, (-90 + 360 * pFrac).toFloat(), (360 * fFrac).toFloat(), false, Offset(inset, inset), arc, style = Stroke(stroke))
                    drawArc(PurpleTheme, (-90 + 360 * (pFrac + fFrac)).toFloat(), (360 * (1 - pFrac - fFrac)).toFloat(), false, Offset(inset, inset), arc, style = Stroke(stroke))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$calories", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                Text("of $goal cal", fontSize = 12.sp, color = TextSecondaryColor)
            }
        }
    }
}

@Composable
private fun MacroBreakdown(protein: Double, fat: Double, carbs: Double, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MacroCard("Protein", protein, BlueTheme, Modifier.weight(1f))
        MacroCard("Fat", fat, OrangeTheme, Modifier.weight(1f))
        MacroCard("Carbs", carbs, PurpleTheme, Modifier.weight(1f))
    }
}

@Composable
private fun MacroCard(name: String, value: Double, color: Color, modifier: Modifier) {
    Column(modifier = modifier.background(White, RoundedCornerShape(12.dp)).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(planGrams(value), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
        Text(name, fontSize = 12.sp, color = TextSecondaryColor)
    }
}
