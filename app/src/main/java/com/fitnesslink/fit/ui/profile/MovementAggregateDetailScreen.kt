package com.fitnesslink.fit.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.ChartPoint
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.MovementAggregate
import com.fitnesslink.fit.model.MovementHistoryEntry
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.movementAggregateDetail
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.profile.charts.ReportBarChart
import com.fitnesslink.fit.ui.profile.charts.ReportLineChart
import com.fitnesslink.fit.ui.profile.charts.ReportStatCard
import com.fitnesslink.fit.ui.profile.charts.formatReportDate
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Port of iOS `MovementAggregateDetailView` (FA-82): one exercise across
 * sessions. Stats grid, PR card, "Weight Progression" and "Volume Per
 * Session" charts, and a weight log with per-set bars. Keeps the Android
 * 30d / 90d / All range chips rather than the report-wide range.
 */
@Composable
fun MovementAggregateDetailScreen(
    exerciseName: String,
    onBack: () -> Unit
) {
    var rangeDays by remember { mutableIntStateOf(90) }
    var aggregate by remember { mutableStateOf<MovementAggregate?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(exerciseName, rangeDays) {
        val userId = DatabaseManager.user()?.id ?: "user1"
        val since = if (rangeDays == Int.MAX_VALUE) 0L else System.currentTimeMillis() - rangeDays * 86_400_000L
        aggregate = DatabaseManager.movementAggregateDetail(userId, since, exerciseName)
        loaded = true
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
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(aggregate?.exerciseName ?: exerciseName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                Text("Performed in ${aggregate?.timesPerformed ?: 0} sessions", fontSize = 14.sp, color = TextSecondaryColor)
            }

            RangeChips(selectedDays = rangeDays, onSelect = { rangeDays = it })

            val agg = aggregate
            if (agg == null || agg.timesPerformed == 0) {
                if (loaded) EmptyMovementState()
            } else {
                StatGrid(agg)
                if (agg.prWeight > 0) PrCard(agg)
                if (agg.entries.size > 1) ProgressionCharts(agg.entries)
                WeightLog(agg.entries)
            }
        }
    }
}

// MARK: - Range chips

@Composable
private fun RangeChips(selectedDays: Int, onSelect: (Int) -> Unit) {
    val options = listOf(30 to "30d", 90 to "90d", Int.MAX_VALUE to "All")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (days, label) ->
            val selected = days == selectedDays
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) FLPrimary else White)
                    .border(1.dp, if (selected) FLPrimary else TextSecondaryColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .clickable { onSelect(days) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) White else TextSecondaryColor
                )
            }
        }
    }
}

// MARK: - Stats

@Composable
private fun StatGrid(agg: MovementAggregate) {
    val avgWeight = if (agg.totalReps > 0) agg.totalVolume / agg.totalReps else 0.0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ReportStatCard(Icons.Default.Repeat, "${agg.totalSets}", "Total Sets", BlueTheme, Modifier.weight(1f))
            ReportStatCard(Icons.Default.Layers, "${agg.totalReps}", "Total Reps", FLPrimary, Modifier.weight(1f))
            ReportStatCard(Icons.Default.FitnessCenter, WeightUnit.displayNumber(agg.maxWeight), "Max ${WeightUnit.current.label}", PurpleTheme, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ReportStatCard(Icons.Default.BarChart, compactWeight(agg.totalVolume), "Total Volume", BlueTheme, Modifier.weight(1f))
            ReportStatCard(Icons.Default.CalendarToday, "${agg.timesPerformed}", "Sessions", OrangeTheme, Modifier.weight(1f))
            ReportStatCard(Icons.Default.Scale, WeightUnit.displayNumber(avgWeight), "Avg ${WeightUnit.current.label}", FLPrimary, Modifier.weight(1f))
        }
    }
}

private fun compactWeight(lbs: Double): String {
    val value = WeightUnit.displayValue(lbs)
    return if (value >= 1000) String.format(Locale.US, "%.1fk", value / 1000) else "${value.roundToInt()}"
}

// MARK: - PR card

@Composable
private fun PrCard(agg: MovementAggregate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FLPrimary.copy(alpha = 0.08f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Personal Record", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(formatReportDate(agg.prDate), fontSize = 12.sp, color = TextSecondaryColor)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(WeightUnit.displayString(agg.prWeight), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FLPrimary)
            Text("${agg.prReps} reps", fontSize = 12.sp, color = TextSecondaryColor)
        }
    }
}

// MARK: - Charts

@Composable
private fun ProgressionCharts(entries: List<MovementHistoryEntry>) {
    val sorted = entries.sortedBy { it.sessionDate }
    val weightPoints = sorted.map { ChartPoint(it.sessionDate, WeightUnit.displayValue(it.weight)) }
    val volumePoints = sorted.map { ChartPoint(it.sessionDate, WeightUnit.displayValue(it.volume)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Weight Progression", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            ReportLineChart(points = weightPoints, color = PurpleTheme, height = 160.dp)
        }
        HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Volume Per Session", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            ReportBarChart(points = volumePoints, color = BlueTheme, height = 140.dp)
        }
    }
}

// MARK: - Weight log

@Composable
private fun WeightLog(entries: List<MovementHistoryEntry>) {
    val unit = WeightUnit.current.label
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Weight Log", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        entries.forEach { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(White)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(entry.workoutName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(formatReportDate(entry.sessionDate), fontSize = 12.sp, color = TextSecondaryColor)
                    }
                    Text(
                        "${WeightUnit.displayWholeNumber(entry.volume)} $unit vol",
                        fontSize = 12.sp, fontWeight = FontWeight.Medium, color = PurpleTheme
                    )
                }
                if (entry.setDetails.isNotEmpty()) {
                    HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
                    val maxW = entry.setDetails.maxOfOrNull { it.weightLifted }?.takeIf { it > 0 } ?: 1.0
                    entry.setDetails.forEach { set ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Set ${set.setNumber}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor, modifier = Modifier.width(46.dp))
                            BoxWithConstraints(modifier = Modifier.weight(1f).height(18.dp)) {
                                val fraction = (set.weightLifted / maxW).coerceIn(0.0, 1.0).toFloat()
                                val barWidth = (maxWidth * fraction).coerceAtLeast(4.dp)
                                Box(
                                    modifier = Modifier
                                        .width(barWidth)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(PurpleTheme.copy(alpha = 0.6f))
                                )
                            }
                            val metric = set.durationSeconds?.takeIf { it > 0 }?.let { WorkoutTask.humanizeDuration(it) }
                                ?: set.distance?.let { DistanceUnit.displayInCurrent(it.value, it.unit) }
                                ?: "${set.reps} reps"
                            Text(metric, fontSize = 12.sp, color = TextSecondaryColor, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
                            Text(WeightUnit.displayString(set.weightLifted), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
                        }
                    }
                } else {
                    Text(
                        "${entry.sets} sets x ${entry.reps} reps @ ${WeightUnit.displayString(entry.weight)}",
                        fontSize = 13.sp, color = TextSecondaryColor
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMovementState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No history in this window", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Try a wider date range, or log a session with this exercise.",
            fontSize = 13.sp, color = TextSecondaryColor, textAlign = TextAlign.Center
        )
    }
}
