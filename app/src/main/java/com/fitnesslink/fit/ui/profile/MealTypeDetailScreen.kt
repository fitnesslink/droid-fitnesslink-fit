package com.fitnesslink.fit.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.ChartPoint
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.model.ReportTimeRange
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.profile.charts.ReportBarChart
import com.fitnesslink.fit.ui.profile.charts.ReportEmptyDetail
import com.fitnesslink.fit.ui.profile.charts.ReportMiniStat
import com.fitnesslink.fit.ui.profile.charts.ReportTimeRangePicker
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.NutritionReportViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Drill-down for one meal type from the report's By Meal tab (iOS
 * `MealTypeDetailView`): header with entry count and share of intake,
 * time-range picker, average stats, per-day calorie chart and the entry
 * list. Route `mealTypeDetail/{mealType}` ([mealType] is the enum name
 * `BREAKFAST` or the wire value `Breakfast`).
 */
@Composable
fun MealTypeDetailScreen(
    mealType: String,
    onBack: () -> Unit,
    viewModel: NutritionReportViewModel = viewModel()
) {
    val type = remember(mealType) { MealType.fromWire(mealType) }
    var entries by remember { mutableStateOf<List<FoodEntry>>(emptyList()) }

    fun load() {
        viewModel.loadData()
        val since = sinceMillis(viewModel.selectedTimeRange)
        entries = type?.let { DatabaseManager.foodEntriesForMealType(it.name, since) } ?: emptyList()
    }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(viewModel.selectedTimeRange) { load() }

    val aggregate = viewModel.mealAggregates.firstOrNull { it.mealType == type }
    val color = mealColor(type)
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(mealIcon(type), contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(type?.displayName ?: mealType, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                    aggregate?.let {
                        Text("${it.timesLogged} entries · ${it.percentOfDailyCalories.toInt()}% of daily intake", fontSize = 14.sp, color = TextSecondaryColor)
                    }
                }
            }

            ReportTimeRangePicker(viewModel.selectedTimeRange, { viewModel.selectedTimeRange = it }, Modifier.padding(horizontal = 20.dp))

            if (aggregate == null && entries.isEmpty()) {
                ReportEmptyDetail("No ${type?.displayName?.lowercase(Locale.getDefault()) ?: "meal"} entries in this window")
                return@Column
            }

            aggregate?.let { agg ->
                Row(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(14.dp)) {
                    ReportMiniStat("Avg Cal", "${agg.avgCalories.toInt()}", Modifier.weight(1f))
                    ReportMiniStat("Avg Protein", "${agg.avgProtein.toInt()}g", Modifier.weight(1f))
                    ReportMiniStat("Avg Carbs", "${agg.avgCarbs.toInt()}g", Modifier.weight(1f))
                    ReportMiniStat("Avg Fat", "${agg.avgFat.toInt()}g", Modifier.weight(1f))
                }
            }

            val points = dailyCaloriePoints(entries)
            if (points.size >= 2) {
                Column(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(14.dp)) {
                    ReportBarChart(points = points, color = color, height = 160.dp)
                }
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Entries", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                entries.forEach { entry ->
                    Row(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                            Text(dateFormat.format(entry.loggedAt), fontSize = 12.sp, color = TextSecondaryColor)
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("${entry.calories} cal", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OrangeTheme)
                    }
                }
            }
        }
    }
}

/** One point per local day: the summed calories of that day's entries. */
internal fun dailyCaloriePoints(entries: List<FoodEntry>): List<ChartPoint> {
    val cal = Calendar.getInstance()
    return entries.groupBy { e ->
        cal.time = e.loggedAt
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }.map { (day, rows) -> ChartPoint(java.util.Date(day), rows.sumOf { it.calories }.toDouble()) }.sortedBy { it.date }
}

internal fun sinceMillis(range: ReportTimeRange): Long {
    val days = range.days ?: return 0L
    return Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }.timeInMillis
}

private fun mealIcon(type: MealType?) = when (type) {
    MealType.BREAKFAST -> Icons.Default.WbSunny
    MealType.LUNCH -> Icons.Default.LightMode
    MealType.DINNER -> Icons.Default.DarkMode
    MealType.SNACK -> Icons.Default.Eco
    null -> Icons.Default.Restaurant
}

private fun mealColor(type: MealType?): Color = when (type) {
    MealType.BREAKFAST -> OrangeTheme
    MealType.LUNCH -> FLPrimary
    MealType.DINNER -> BlueTheme
    MealType.SNACK -> PurpleTheme
    null -> MediumGray
}
