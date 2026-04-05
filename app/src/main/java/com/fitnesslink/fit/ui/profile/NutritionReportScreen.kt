package com.fitnesslink.fit.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.*
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.*
import com.fitnesslink.fit.viewmodel.NutritionReportViewModel

@Composable
fun NutritionReportScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: NutritionReportViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.loadData() }
    LaunchedEffect(viewModel.selectedTimeRange) { viewModel.loadData() }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 30.dp)) {
            Text("Nutrition Report", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(modifier = Modifier.height(16.dp))
            TimeRangePicker(viewModel.selectedTimeRange) { viewModel.selectedTimeRange = it; viewModel.loadData() }
            Spacer(modifier = Modifier.height(12.dp))
            NutritionTabPicker(viewModel.selectedTab) { viewModel.selectedTab = it }
            Spacer(modifier = Modifier.height(16.dp))

            when (viewModel.selectedTab) {
                NutritionReportTab.OVERVIEW -> NutritionOverview(viewModel, onNavigate)
                NutritionReportTab.BY_MEAL -> ByMealContent(viewModel, onNavigate)
                NutritionReportTab.BY_FOOD -> ByFoodContent(viewModel, onNavigate)
            }
        }
    }
}

@Composable
private fun TimeRangePicker(selected: ReportTimeRange, onSelect: (ReportTimeRange) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(8.dp)).background(White)) {
        ReportTimeRange.entries.forEach { range ->
            Box(modifier = Modifier.weight(1f).clickable { onSelect(range) }.background(if (selected == range) FLPrimary else White).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(range.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (selected == range) White else TextSecondaryColor)
            }
        }
    }
}

@Composable
private fun NutritionTabPicker(selected: NutritionReportTab, onSelect: (NutritionReportTab) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(8.dp)).background(White)) {
        NutritionReportTab.entries.forEach { tab ->
            Box(modifier = Modifier.weight(1f).clickable { onSelect(tab) }.background(if (selected == tab) FLPrimary else White).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(tab.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (selected == tab) White else TextSecondaryColor)
            }
        }
    }
}

@Composable
private fun NutritionOverview(viewModel: NutritionReportViewModel, onNavigate: (String) -> Unit) {
    if (viewModel.dailyRows.isEmpty()) {
        EmptyState("No nutrition data yet", "Log your meals to see insights")
    } else {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            val metrics = NutritionMetric.entries
            for (i in metrics.indices step 2) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NutritionMetricCard(metrics[i], viewModel, Modifier.weight(1f)) { onNavigate("nutritionMetricDetail/${metrics[i].name}") }
                    if (i + 1 < metrics.size) NutritionMetricCard(metrics[i + 1], viewModel, Modifier.weight(1f)) { onNavigate("nutritionMetricDetail/${metrics[i + 1].name}") }
                    else Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun NutritionMetricCard(metric: NutritionMetric, viewModel: NutritionReportViewModel, modifier: Modifier, onClick: () -> Unit) {
    val color = when (metric.colorName) { "FLPrimary" -> FLPrimary; "BlueTheme" -> BlueTheme; "OrangeTheme" -> OrangeTheme; "PurpleTheme" -> PurpleTheme; else -> FLPrimary }
    val icon = when (metric) {
        NutritionMetric.AVG_CALORIES -> Icons.Default.LocalFireDepartment
        NutritionMetric.GOAL_ADHERENCE -> Icons.Default.TrackChanges
        NutritionMetric.AVG_PROTEIN -> Icons.Default.Restaurant
        NutritionMetric.AVG_CARBS -> Icons.Default.Eco
        NutritionMetric.AVG_FAT -> Icons.Default.WaterDrop
        NutritionMetric.MACRO_BALANCE -> Icons.Default.PieChart
        NutritionMetric.LOGGING_STREAK -> Icons.Default.Bolt
        NutritionMetric.CALORIE_SURPLUS -> Icons.Default.SwapVert
    }
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(White).clickable(onClick = onClick).padding(16.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(metric.title, fontSize = 12.sp, color = TextSecondaryColor)
        Text(viewModel.displayValue(metric), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        Text(viewModel.subtitle(metric), fontSize = 11.sp, color = TextSecondaryColor)
    }
}

@Composable
private fun ByMealContent(viewModel: NutritionReportViewModel, onNavigate: (String) -> Unit) {
    if (viewModel.mealAggregates.isEmpty()) {
        EmptyState("No nutrition data yet", "Log your meals to see insights")
    } else {
        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            viewModel.mealAggregates.forEach { agg ->
                val icon = when (agg.mealType) { MealType.BREAKFAST -> Icons.Default.WbSunny; MealType.LUNCH -> Icons.Default.LightMode; MealType.DINNER -> Icons.Default.DarkMode; MealType.SNACK -> Icons.Default.Eco }
                val color = when (agg.mealType) { MealType.BREAKFAST -> OrangeTheme; MealType.LUNCH -> FLPrimary; MealType.DINNER -> BlueTheme; MealType.SNACK -> PurpleTheme }
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).clickable { onNavigate("mealTypeDetail/${agg.mealType.name}") }.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(agg.mealType.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${agg.percentOfDailyCalories.toInt()}% of intake", fontSize = 12.sp, color = TextSecondaryColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        MiniS("${agg.avgCalories.toInt()}", "Avg Cal")
                        MiniS("${agg.avgProtein.toInt()}g", "Protein")
                        MiniS("${agg.avgCarbs.toInt()}g", "Carbs")
                        MiniS("${agg.avgFat.toInt()}g", "Fat")
                        MiniS("${agg.timesLogged}x", "Logged")
                    }
                }
            }
        }
    }
}

@Composable
private fun ByFoodContent(viewModel: NutritionReportViewModel, onNavigate: (String) -> Unit) {
    if (viewModel.foodAggregates.isEmpty()) {
        EmptyState("No nutrition data yet", "Log your meals to see insights")
    } else {
        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val unique = viewModel.foodAggregates.size
            val total = viewModel.foodAggregates.sumOf { it.timesLogged }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Eco, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(16.dp))
                Text("Food Variety: $unique unique foods across $total entries", fontSize = 13.sp, color = TextSecondaryColor)
            }
            viewModel.foodAggregates.forEach { agg ->
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).clickable { onNavigate("foodItemDetail/${agg.foodName}") }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(agg.foodName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("${agg.timesLogged}x logged · ${agg.avgCalories.toInt()} avg cal", fontSize = 12.sp, color = TextSecondaryColor)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${agg.totalCalories}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OrangeTheme)
                        Text("total cal", fontSize = 10.sp, color = TextSecondaryColor)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun MiniS(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 9.sp, color = TextSecondaryColor)
    }
}
