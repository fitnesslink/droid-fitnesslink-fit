package com.fitnesslink.fit.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.ChartPoint
import com.fitnesslink.fit.model.DailyNutritionRow
import com.fitnesslink.fit.model.NutritionMetric
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.profile.charts.ReportBarChart
import com.fitnesslink.fit.ui.profile.charts.ReportEmptyDetail
import com.fitnesslink.fit.ui.profile.charts.ReportLineChart
import com.fitnesslink.fit.ui.profile.charts.ReportTimeRangePicker
import com.fitnesslink.fit.ui.profile.charts.reportThemeColor
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlendGray
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.NutritionReportViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * One nutrition report metric (iOS `NutritionMetricDetailView`): header with
 * the current value, the shared time-range picker, a Canvas chart per metric
 * (bars for calories/surplus/goal adherence, stacked macro-calorie bars for
 * the split, smoothed line for protein/carbs/fat) and the daily list.
 *
 * [metricKey] accepts a `NutritionMetric` name (what `NutritionReportScreen`
 * navigates with) or the legacy lowercase keys (`calories` / `protein` …).
 */
@Composable
fun NutritionMetricDetailScreen(
    metricKey: String,
    onBack: () -> Unit,
    viewModel: NutritionReportViewModel = viewModel()
) {
    val metric = remember(metricKey) { resolveMetric(metricKey) }
    LaunchedEffect(Unit) { viewModel.loadData() }
    LaunchedEffect(viewModel.selectedTimeRange) { viewModel.loadData() }

    val color = reportThemeColor(metric.colorName)
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(metric.icon(), contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(metric.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                    Text(viewModel.subtitle(metric), fontSize = 14.sp, color = TextSecondaryColor)
                }
                Text(viewModel.displayValue(metric), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = color)
            }

            ReportTimeRangePicker(viewModel.selectedTimeRange, { viewModel.selectedTimeRange = it }, Modifier.padding(horizontal = 20.dp))

            if (viewModel.dailyRows.isEmpty()) {
                ReportEmptyDetail("No data in this window")
            } else {
                MetricChart(metric, viewModel, color, Modifier.padding(horizontal = 20.dp))

                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    viewModel.dailyRows.forEach { row -> DayRow(row, dateFormat) }
                }
            }
        }
    }
}

@Composable
private fun MetricChart(metric: NutritionMetric, viewModel: NutritionReportViewModel, color: Color, modifier: Modifier = Modifier) {
    val points = viewModel.chartPoints(metric)
    if (points.size < 2) return
    Column(modifier = modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (metric) {
            NutritionMetric.AVG_CALORIES, NutritionMetric.CALORIE_SURPLUS, NutritionMetric.GOAL_ADHERENCE -> {
                CalorieBarsWithGoal(rows = viewModel.dailyRows, goal = viewModel.goal.calorieGoal, highlightAdherence = metric == NutritionMetric.GOAL_ADHERENCE)
                Text("Dashed line = daily goal (${viewModel.goal.calorieGoal} cal); amber bars sit outside the ±10% window.", fontSize = 11.sp, color = TextSecondaryColor)
            }
            NutritionMetric.MACRO_BALANCE -> {
                StackedMacroBars(rows = viewModel.dailyRows)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(BlueTheme, "Protein"); LegendDot(FLPrimary, "Carbs"); LegendDot(PurpleTheme, "Fat")
                }
            }
            NutritionMetric.LOGGING_STREAK -> ReportBarChart(points = points, color = color, height = 180.dp)
            else -> ReportLineChart(points = points, color = color, height = 180.dp)
        }
    }
}

/** Calories per day with a dashed goal rule; bars outside the ±10% window (or off-target days) go amber. */
@Composable
private fun CalorieBarsWithGoal(rows: List<DailyNutritionRow>, goal: Int, highlightAdherence: Boolean) {
    val sorted = rows.sortedBy { it.date }
    val yMax = maxOf(sorted.maxOf { it.calories }.toDouble(), goal.toDouble()) * 1.1
    val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        val left = 0f; val top = 6.dp.toPx(); val right = size.width; val bottom = size.height - 16.dp.toPx()
        val plotH = bottom - top
        val slot = (right - left) / sorted.size
        val barW = (slot * 0.6f).coerceAtMost(28.dp.toPx())
        sorted.forEachIndexed { i, row ->
            val h = (row.calories / yMax).coerceIn(0.0, 1.0).toFloat() * plotH
            val over = if (highlightAdherence) !row.isOnTarget else row.calories > goal * 1.1
            drawRoundRect(
                color = if (over) OrangeTheme else FLPrimary,
                topLeft = Offset(left + slot * i + (slot - barW) / 2f, bottom - h),
                size = Size(barW, h), cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }
        if (goal > 0) {
            val y = bottom - (goal / yMax).coerceIn(0.0, 1.0).toFloat() * plotH
            drawLine(Color(0xFFE53935).copy(alpha = 0.5f), Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        }
        drawLine(BlendGray, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1f)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(dateFormat.format(sorted.first().date), fontSize = 10.sp, color = TextSecondaryColor)
        Text(dateFormat.format(sorted.last().date), fontSize = 10.sp, color = TextSecondaryColor)
    }
}

/** Stacked protein / carbs / fat calories per day (4 / 4 / 9 cal per gram). */
@Composable
private fun StackedMacroBars(rows: List<DailyNutritionRow>) {
    val sorted = rows.sortedBy { it.date }
    val totals = sorted.map { it.protein * 4 + it.carbs * 4 + it.fat * 9 }
    val yMax = (totals.maxOrNull() ?: 1.0).takeIf { it > 0 } ?: 1.0
    val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        val top = 6.dp.toPx(); val bottom = size.height - 16.dp.toPx(); val plotH = bottom - top
        val slot = size.width / sorted.size
        val barW = (slot * 0.6f).coerceAtMost(28.dp.toPx())
        sorted.forEachIndexed { i, row ->
            val x = slot * i + (slot - barW) / 2f
            var y = bottom
            listOf(row.protein * 4 to BlueTheme, row.carbs * 4 to FLPrimary, row.fat * 9 to PurpleTheme).forEach { (cal, color) ->
                val h = (cal / yMax).coerceIn(0.0, 1.0).toFloat() * plotH
                drawRect(color = color, topLeft = Offset(x, y - h), size = Size(barW, h))
                y -= h
            }
        }
        drawLine(BlendGray, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1f)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(dateFormat.format(sorted.first().date), fontSize = 10.sp, color = TextSecondaryColor)
        Text(dateFormat.format(sorted.last().date), fontSize = 10.sp, color = TextSecondaryColor)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.size(8.dp).background(color, androidx.compose.foundation.shape.CircleShape))
        Text(label, fontSize = 11.sp, color = TextSecondaryColor)
    }
}

@Composable
private fun DayRow(row: DailyNutritionRow, dateFormat: SimpleDateFormat) {
    Row(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(dateFormat.format(row.date), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${row.calories} cal", fontSize = 12.sp, color = TextSecondaryColor)
                Text("P:${row.protein.toInt()}g", fontSize = 11.sp, color = BlueTheme)
                Text("C:${row.carbs.toInt()}g", fontSize = 11.sp, color = FLPrimary)
                Text("F:${row.fat.toInt()}g", fontSize = 11.sp, color = PurpleTheme)
            }
        }
        if (row.isOnTarget) {
            Icon(Icons.Default.CheckCircle, contentDescription = "On target", tint = FLPrimary, modifier = Modifier.size(20.dp))
        } else {
            val diff = row.calories - row.goalCalories
            Text(if (diff > 0) "+$diff" else "$diff", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (diff > 0) OrangeTheme else BlueTheme)
        }
        Spacer(modifier = Modifier.width(2.dp))
    }
}

private fun resolveMetric(key: String): NutritionMetric {
    NutritionMetric.entries.firstOrNull { it.name.equals(key, ignoreCase = true) }?.let { return it }
    return when (key.lowercase(Locale.US)) {
        "protein" -> NutritionMetric.AVG_PROTEIN
        "fat" -> NutritionMetric.AVG_FAT
        "carbs" -> NutritionMetric.AVG_CARBS
        "adherence", "goal" -> NutritionMetric.GOAL_ADHERENCE
        "streak" -> NutritionMetric.LOGGING_STREAK
        "surplus" -> NutritionMetric.CALORIE_SURPLUS
        "balance", "split" -> NutritionMetric.MACRO_BALANCE
        else -> NutritionMetric.AVG_CALORIES
    }
}

fun NutritionMetric.icon(): ImageVector = when (this) {
    NutritionMetric.AVG_CALORIES -> Icons.Default.LocalFireDepartment
    NutritionMetric.GOAL_ADHERENCE -> Icons.Default.TrackChanges
    NutritionMetric.AVG_PROTEIN -> Icons.Default.Restaurant
    NutritionMetric.AVG_CARBS -> Icons.Default.Eco
    NutritionMetric.AVG_FAT -> Icons.Default.WaterDrop
    NutritionMetric.MACRO_BALANCE -> Icons.Default.PieChart
    NutritionMetric.LOGGING_STREAK -> Icons.Default.Bolt
    NutritionMetric.CALORIE_SURPLUS -> Icons.Default.SwapVert
}
