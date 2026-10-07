package com.fitnesslink.fit.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.profile.charts.ReportBarChart
import com.fitnesslink.fit.ui.profile.charts.ReportEmptyDetail
import com.fitnesslink.fit.ui.profile.charts.ReportTimeRangePicker
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.NutritionReportViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Drill-down for one food from the report's By Food tab (iOS
 * `FoodItemDetailView`): times logged + total calories, time-range picker,
 * avg/total stats, per-day calorie bars and the log history. Route
 * `foodItemDetail/{foodName}` (name arrives URL-decoded).
 */
@Composable
fun FoodItemDetailScreen(
    foodName: String,
    onBack: () -> Unit,
    viewModel: NutritionReportViewModel = viewModel()
) {
    var entries by remember { mutableStateOf<List<FoodEntry>>(emptyList()) }

    fun load() {
        viewModel.loadData()
        entries = DatabaseManager.foodEntriesForName(foodName, sinceMillis(viewModel.selectedTimeRange))
    }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(viewModel.selectedTimeRange) { load() }

    val aggregate = viewModel.foodAggregates.firstOrNull { it.foodName == foodName }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(foodName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                aggregate?.let { Text("${it.timesLogged}x logged · ${it.totalCalories} total cal", fontSize = 14.sp, color = TextSecondaryColor) }
            }

            ReportTimeRangePicker(viewModel.selectedTimeRange, { viewModel.selectedTimeRange = it }, Modifier.padding(horizontal = 20.dp))

            if (aggregate == null && entries.isEmpty()) {
                ReportEmptyDetail("No entries for this food in this window")
                return@Column
            }

            aggregate?.let { agg ->
                Row(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(14.dp)) {
                    StatBlock("${agg.avgCalories.toInt()}", "Avg Cal", OrangeTheme, Modifier.weight(1f))
                    StatBlock("${agg.avgProtein.toInt()}g", "Avg Protein", BlueTheme, Modifier.weight(1f))
                    StatBlock("${agg.totalCalories}", "Total Cal", FLPrimary, Modifier.weight(1f))
                }
            }

            val points = dailyCaloriePoints(entries)
            if (points.size >= 2) {
                Column(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(14.dp)) {
                    ReportBarChart(points = points, color = OrangeTheme, height = 140.dp)
                }
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("History", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                entries.forEach { entry ->
                    Row(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(dateFormat.format(entry.loggedAt), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                            Text(entry.mealType.displayName, fontSize = 12.sp, color = TextSecondaryColor)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${entry.calories} cal", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OrangeTheme)
                            Text("P:${entry.protein.toInt()}g C:${entry.carbs.toInt()}g F:${entry.fat.toInt()}g", fontSize = 11.sp, color = TextSecondaryColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = TextSecondaryColor)
    }
}
