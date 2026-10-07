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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.ChartPoint
import com.fitnesslink.fit.network.dto.NutritionLoggingStreak
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.profile.charts.ReportBarChart
import com.fitnesslink.fit.ui.profile.charts.ReportLineChart
import com.fitnesslink.fit.ui.profile.charts.ReportSegmentedPicker
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.NutritionAnalyticsRange
import com.fitnesslink.fit.viewmodel.NutritionAnalyticsViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Nutrition analytics (iOS `NutritionAnalyticsView`, FTNSSLNK-212):
 * Week / Month / 3 Months; calories vs target, macro lines vs targets,
 * water bars vs goal, consistency stats, and goal progress (weight points,
 * 7-day average, plan projection, estimated goal date). Every number is
 * the analytics endpoints' value verbatim; sparse data renders as gaps.
 */
@Composable
fun NutritionAnalyticsScreen(
    onBack: () -> Unit,
    viewModel: NutritionAnalyticsViewModel = viewModel()
) {
    LaunchedEffect(viewModel.selectedRange) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Nutrition Trends", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, modifier = Modifier.padding(horizontal = 20.dp))
            ReportSegmentedPicker(
                options = NutritionAnalyticsRange.entries, selected = viewModel.selectedRange,
                label = { it.label }, onSelect = { viewModel.selectRange(it) },
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            viewModel.errorMessage?.let { error ->
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(OrangeTheme.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(error, fontSize = 13.sp, color = OrangeTheme, modifier = Modifier.weight(1f))
                }
            }

            if (viewModel.isLoading && !viewModel.hasContent) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FLPrimary) }
            } else if (viewModel.hasContent) {
                CaloriesCard(viewModel)
                MacrosCard(viewModel)
                WaterCard(viewModel)
                ConsistencyCard(viewModel)
                GoalProgressCard(viewModel)
            }
        }
    }
}

@Composable
private fun CaloriesCard(vm: NutritionAnalyticsViewModel) {
    ChartCard("Calories", if (vm.isWeekly) "Avg per logged day, by week" else "Consumed per day") {
        if (vm.hasAnyFoodData) {
            ReportBarChart(points = vm.caloriePoints, color = FLPrimary, height = 200.dp)
            vm.calorieTargetAverage?.let { target ->
                Text("Daily target ≈ ${Math.round(target)} cal (dashed where set)", fontSize = 11.sp, color = TextSecondaryColor)
                LegendRow(listOf(FLPrimary to "Consumed", TextPrimaryColor.copy(alpha = 0.55f) to "Daily target"))
            }
        } else {
            ChartEmptyState(Icons.Default.Restaurant, "No food logged in this range yet.")
        }
    }
}

@Composable
private fun MacrosCard(vm: NutritionAnalyticsViewModel) {
    ChartCard("Macros", if (vm.isWeekly) "Avg grams per logged day, by week" else "Grams per day") {
        if (vm.hasAnyFoodData) {
            val colors = mapOf("Protein" to BlueTheme, "Carbs" to PurpleTheme, "Fat" to OrangeTheme)
            MultiLineChart(
                series = vm.macroLines.map { line -> (colors[line.id] ?: FLPrimary) to line.consumedPoints },
                dashed = vm.macroLines.map { line -> (colors[line.id] ?: FLPrimary).copy(alpha = 0.4f) to line.targetPoints },
                height = 200.dp
            )
            LegendRow(vm.macroLines.map { (colors[it.id] ?: FLPrimary) to it.name })
            Text("Dashed lines show your daily targets where set.", fontSize = 11.sp, color = TextSecondaryColor)
        } else {
            ChartEmptyState(Icons.Default.ShowChart, "Log meals to see macro trends here.")
        }
    }
}

@Composable
private fun WaterCard(vm: NutritionAnalyticsViewModel) {
    val subtitle = if (vm.isWeekly) "Avg ${vm.waterUnit} per logged day, by week" else "${vm.waterUnit.replaceFirstChar { it.uppercase() }} per day"
    ChartCard("Water", subtitle) {
        if (vm.hasAnyWaterData) {
            ReportBarChart(points = vm.waterPoints, color = BlueTheme, height = 160.dp)
            vm.waterDailyGoal?.let { goal -> Text("Goal: ${Math.round(goal)} ${vm.waterUnit} per day", fontSize = 11.sp, color = TextSecondaryColor) }
        } else {
            ChartEmptyState(Icons.Default.WaterDrop, "No water logged in this range yet.")
        }
    }
}

@Composable
private fun ConsistencyCard(vm: NutritionAnalyticsViewModel) {
    val c = vm.consistency ?: return
    Column(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Consistency", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ConsistencyStat(Icons.Default.CalendarMonth, FLPrimary, "${c.daysWithFood} of ${c.daysInRange}", "Days logged", Modifier.weight(1f))
            ConsistencyStat(Icons.Default.Percent, BlueTheme, "${Math.round(c.foodLoggingRate * 100)}%", "Logging rate", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ConsistencyStat(Icons.Default.LocalFireDepartment, OrangeTheme, streakText(c.currentStreak), "Current streak", Modifier.weight(1f))
            ConsistencyStat(Icons.Default.EmojiEvents, PurpleTheme, streakText(c.longestStreak), "Longest streak", Modifier.weight(1f))
        }
    }
}

private fun streakText(streak: NutritionLoggingStreak): String = if (streak.lengthDays == 1) "1 day" else "${streak.lengthDays} days"

@Composable
private fun ConsistencyStat(icon: ImageVector, color: Color, value: String, label: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.size(30.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            Text(label, fontSize = 11.sp, color = TextSecondaryColor)
        }
    }
}

@Composable
private fun GoalProgressCard(vm: NutritionAnalyticsViewModel) {
    val progress = vm.weightProgress ?: return
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    Column(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Goal Progress", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        if (progress.points.isEmpty()) {
            ChartEmptyState(Icons.Default.MonitorWeight, "Log your weight to see progress.")
            return@Column
        }
        Text("Weight in ${progress.unit}, with 7-day average", fontSize = 12.sp, color = TextSecondaryColor)
        val all = vm.weightPoints + vm.movingAvgPoints + vm.planLinePoints
        val minY = (all.minOfOrNull { it.value } ?: 0.0)
        val maxY = (all.maxOfOrNull { it.value } ?: 1.0)
        val pad = ((maxY - minY).takeIf { it > 0 } ?: 2.0) * 0.15
        val yRange = (minY - pad)..(maxY + pad)
        Box {
            ReportLineChart(points = vm.movingAvgPoints, color = OrangeTheme, height = 200.dp, fill = false, showPoints = false, yRange = yRange)
            ReportLineChart(points = vm.weightPoints, color = FLPrimary, height = 200.dp, fill = false, showPoints = true, yRange = yRange)
            if (vm.planLinePoints.size >= 2) {
                DashedOverlayLine(points = vm.planLinePoints, allPoints = vm.weightPoints, yRange = yRange, color = PurpleTheme.copy(alpha = 0.7f), height = 200.dp)
            }
        }
        val legend = mutableListOf(FLPrimary to "Logged", OrangeTheme to "7-day avg")
        if (vm.planLinePoints.isNotEmpty()) legend.add(PurpleTheme.copy(alpha = 0.7f) to "Plan")
        LegendRow(legend)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            progress.totalChange?.let { change ->
                GoalStat(signed(change, progress.unit), "Total change", if (change <= 0) FLPrimary else OrangeTheme, Modifier.weight(1f))
            }
            progress.plan?.let { plan ->
                GoalStat(signed(plan.latestVarianceFromPlan, progress.unit), "Vs plan", if (abs(plan.latestVarianceFromPlan) <= 1) FLPrimary else OrangeTheme, Modifier.weight(1f))
                vm.estimatedGoalDate?.let { GoalStat(dateFormat.format(it), "Est. goal date", TextPrimaryColor, Modifier.weight(1f)) }
            }
        }
        progress.plan?.let { plan ->
            plan.goalWeight?.let { goal ->
                Text("Plan: ${trimmed(plan.weeklyChange)} ${progress.unit}/week toward ${trimmed(goal)} ${progress.unit}", fontSize = 11.sp, color = TextSecondaryColor)
            }
        }
    }
}

/** Dashed plan segment drawn over the weight chart, mapped onto the same x (date) and y ranges. */
@Composable
private fun DashedOverlayLine(points: List<ChartPoint>, allPoints: List<ChartPoint>, yRange: ClosedFloatingPointRange<Double>, color: Color, height: androidx.compose.ui.unit.Dp) {
    val sorted = allPoints.sortedBy { it.date }
    if (sorted.size < 2) return
    val xMin = sorted.first().date.time.toDouble()
    val xMax = sorted.last().date.time.toDouble()
    if (xMax <= xMin) return
    Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
        val left = 36.dp.toPx(); val top = 6.dp.toPx(); val right = size.width; val bottom = size.height - 16.dp.toPx()
        val span = (yRange.endInclusive - yRange.start).takeIf { it > 0 } ?: 1.0
        fun x(p: ChartPoint) = left + ((p.date.time - xMin) / (xMax - xMin)).coerceIn(0.0, 1.0).toFloat() * (right - left)
        fun y(p: ChartPoint) = bottom - (((p.value - yRange.start) / span).coerceIn(0.0, 1.0).toFloat()) * (bottom - top)
        for (i in 0 until points.size - 1) {
            drawLine(color, Offset(x(points[i]), y(points[i])), Offset(x(points[i + 1]), y(points[i + 1])), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
        }
    }
}

/** Several lines on one shared y axis (consumed solid, targets dashed). */
@Composable
private fun MultiLineChart(series: List<Pair<Color, List<ChartPoint>>>, dashed: List<Pair<Color, List<ChartPoint>>>, height: androidx.compose.ui.unit.Dp) {
    val all = (series + dashed).flatMap { it.second }
    if (all.isEmpty()) return
    val yMax = all.maxOf { it.value } * 1.1
    val yRange = 0.0..(if (yMax > 0) yMax else 1.0)
    val xs = all.map { it.date }.distinct().sorted()
    Box {
        series.forEachIndexed { index, (color, points) ->
            if (points.isNotEmpty()) {
                // Pad the series onto the shared x axis so each line keeps the same horizontal scale.
                ReportLineChart(points = padToAxis(points, xs), color = color, height = height, fill = index == 0, showPoints = true, yRange = yRange)
            }
        }
        dashed.forEach { (color, points) ->
            if (points.size >= 2) DashedOverlayLine(points = points.sortedBy { it.date }, allPoints = xs.map { ChartPoint(it, 0.0) }, yRange = yRange, color = color, height = height)
        }
    }
}

/** ReportLineChart spaces points evenly by index; keep every line on the same x axis by filling missing dates with the nearest value. */
private fun padToAxis(points: List<ChartPoint>, axis: List<java.util.Date>): List<ChartPoint> {
    if (points.size == axis.size) return points.sortedBy { it.date }
    val byDate = points.associateBy { it.date.time }
    var last = points.minByOrNull { it.date }?.value ?: 0.0
    return axis.map { date ->
        val v = byDate[date.time]?.value
        if (v != null) last = v
        ChartPoint(date, last)
    }
}

@Composable
private fun GoalStat(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = TextSecondaryColor)
    }
}

private fun signed(value: Double, unit: String): String {
    val text = trimmed(abs(value))
    return when {
        value > 0 -> "+$text $unit"
        value < 0 -> "−$text $unit"
        else -> "0 $unit"
    }
}

private fun trimmed(value: Double): String = if (value == Math.rint(value)) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Text(subtitle, fontSize = 12.sp, color = TextSecondaryColor)
        }
        content()
    }
}

@Composable
private fun LegendRow(entries: List<Pair<Color, String>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        entries.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
                Text(label, fontSize = 11.sp, color = TextSecondaryColor)
            }
        }
    }
}

@Composable
private fun ChartEmptyState(icon: ImageVector, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = MediumGray, modifier = Modifier.size(18.dp))
        Text(text, fontSize = 13.sp, color = TextSecondaryColor)
        Spacer(modifier = Modifier.weight(1f))
    }
}
