package com.fitnesslink.fit.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.fitnesslink.fit.viewmodel.WorkoutReportViewModel

@Composable
fun WorkoutReportScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: WorkoutReportViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.loadData() }
    LaunchedEffect(viewModel.selectedTimeRange) { viewModel.loadData() }

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
                .padding(top = 10.dp, bottom = 30.dp)
        ) {
            // Title
            Text(
                text = "Workout Report",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Time range picker
            TimeRangePicker(
                selected = viewModel.selectedTimeRange,
                onSelect = { viewModel.selectedTimeRange = it; viewModel.loadData() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tab picker
            TabPicker(
                selected = viewModel.selectedTab,
                onSelect = { viewModel.selectedTab = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (viewModel.selectedTab) {
                ReportTab.OVERVIEW -> OverviewContent(viewModel, onNavigate)
                ReportTab.BY_WORKOUT -> ByWorkoutContent(viewModel, onNavigate)
                ReportTab.BY_MOVEMENT -> ByMovementContent(viewModel, onNavigate)
            }
        }
    }
}

@Composable
private fun TimeRangePicker(selected: ReportTimeRange, onSelect: (ReportTimeRange) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(White)
    ) {
        ReportTimeRange.entries.forEach { range ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(range) }
                    .background(if (selected == range) FLPrimary else White)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = range.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (selected == range) White else TextSecondaryColor
                )
            }
        }
    }
}

@Composable
private fun TabPicker(selected: ReportTab, onSelect: (ReportTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(White)
    ) {
        ReportTab.entries.forEach { tab ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(tab) }
                    .background(if (selected == tab) FLPrimary else White)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (selected == tab) White else TextSecondaryColor
                )
            }
        }
    }
}

// MARK: - Overview

@Composable
private fun OverviewContent(viewModel: WorkoutReportViewModel, onNavigate: (String) -> Unit) {
    if (viewModel.reportData.workoutsCompleted == 0 && viewModel.reportData.incompleteWorkouts == 0) {
        EmptyState("No workout data yet", "Complete a workout to see your report")
    } else {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            val metrics = ReportMetric.entries
            for (i in metrics.indices step 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        metric = metrics[i],
                        value = viewModel.displayValue(metrics[i]),
                        subtitle = viewModel.subtitle(metrics[i]),
                        onClick = { onNavigate("reportDetail/${metrics[i].name}") },
                        modifier = Modifier.weight(1f)
                    )
                    if (i + 1 < metrics.size) {
                        MetricCard(
                            metric = metrics[i + 1],
                            value = viewModel.displayValue(metrics[i + 1]),
                            subtitle = viewModel.subtitle(metrics[i + 1]),
                            onClick = { onNavigate("reportDetail/${metrics[i + 1].name}") },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun MetricCard(
    metric: ReportMetric,
    value: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = metricColor(metric)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Icon(
            imageVector = metricIcon(metric),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(metric.title, fontSize = 12.sp, color = TextSecondaryColor)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        Text(subtitle, fontSize = 11.sp, color = TextSecondaryColor)
    }
}

// MARK: - By Workout

@Composable
private fun ByWorkoutContent(viewModel: WorkoutReportViewModel, onNavigate: (String) -> Unit) {
    if (viewModel.workoutAggregates.isEmpty()) {
        EmptyState("No workout data yet", "Complete a workout to see your report")
    } else {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            viewModel.workoutAggregates.forEach { agg ->
                WorkoutAggregateCard(agg, viewModel) {
                    onNavigate("workoutAggregate/${agg.workoutName}")
                }
            }
        }
    }
}

@Composable
private fun WorkoutAggregateCard(
    agg: WorkoutAggregate,
    viewModel: WorkoutReportViewModel,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(agg.workoutName, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${agg.timesCompleted}x", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MediumGray, modifier = Modifier.size(14.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            MiniStat("Avg Time", viewModel.formatDuration(agg.avgDurationSeconds))
            MiniStat("Avg RPE", if (agg.avgRPE > 0) String.format("%.1f", agg.avgRPE) else "--")
            MiniStat("Weight", formatW(agg.totalWeightLifted))
            MiniStat("Cal", "${agg.totalCaloriesBurned.toInt()}")
        }
        if (agg.sessions.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrendPill("Volume", agg.volumeTrend)
                TrendPill("RPE", agg.rpeTrend)
                TrendPill("Duration", agg.durationTrend)
            }
        }
    }
}

// MARK: - By Movement

@Composable
private fun ByMovementContent(viewModel: WorkoutReportViewModel, onNavigate: (String) -> Unit) {
    if (viewModel.movementAggregates.isEmpty()) {
        EmptyState("No workout data yet", "Complete a workout to see your report")
    } else {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            viewModel.movementAggregates.forEach { agg ->
                MovementAggregateCard(agg) {
                    onNavigate("movementAggregate/${agg.exerciseName}")
                }
            }
        }
    }
}

@Composable
private fun MovementAggregateCard(agg: MovementAggregate, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(agg.exerciseName, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${agg.timesPerformed} sessions", fontSize = 12.sp, color = TextSecondaryColor)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MediumGray, modifier = Modifier.size(14.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            MiniStat("Sets", "${agg.totalSets}")
            MiniStat("Reps", "${agg.totalReps}")
            MiniStat("Max lbs", "${agg.maxWeight.toInt()}")
            MiniStat("Volume", formatW(agg.totalVolume))
        }
        if (agg.prWeight > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(FLPrimary.copy(alpha = 0.1f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("PR: ${agg.prWeight.toInt()} lbs x ${agg.prReps} reps", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
            }
        }
    }
}

// MARK: - Shared Components

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 9.sp, color = TextSecondaryColor)
    }
}

@Composable
private fun TrendPill(label: String, trend: TrendDirection) {
    val color = when (trend) {
        TrendDirection.UP -> FLPrimary
        TrendDirection.DOWN -> OrangeTheme
        TrendDirection.FLAT -> MediumGray
    }
    val icon = when (trend) {
        TrendDirection.UP -> Icons.Default.TrendingUp
        TrendDirection.DOWN -> Icons.Default.TrendingDown
        TrendDirection.FLAT -> Icons.Default.TrendingFlat
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(3.dp))
        Text(label, fontSize = 11.sp, color = color)
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.BarChart, contentDescription = null, tint = MediumGray, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, fontSize = 14.sp, color = TextSecondaryColor)
    }
}

private fun metricIcon(metric: ReportMetric) = when (metric) {
    ReportMetric.WORKOUTS_COMPLETED -> Icons.Default.CheckCircle
    ReportMetric.TOTAL_TIME -> Icons.Default.AccessTime
    ReportMetric.INCOMPLETE_WORKOUTS -> Icons.Default.Warning
    ReportMetric.AVERAGE_RPE -> Icons.Default.Speed
    ReportMetric.EXERCISES_COMPLETED -> Icons.Default.DirectionsRun
    ReportMetric.TOTAL_WEIGHT_LIFTED -> Icons.Default.FitnessCenter
    ReportMetric.CALORIES_BURNED -> Icons.Default.LocalFireDepartment
    ReportMetric.VOLUME_TRACKING -> Icons.Default.BarChart
    ReportMetric.PERSONAL_RECORDS -> Icons.Default.EmojiEvents
    ReportMetric.STREAKS_FREQUENCY -> Icons.Default.Bolt
}

private fun metricColor(metric: ReportMetric) = when (metric.color) {
    "FLPrimary" -> FLPrimary
    "BlueTheme" -> BlueTheme
    "OrangeTheme" -> OrangeTheme
    "PurpleTheme" -> PurpleTheme
    else -> FLPrimary
}

private fun formatW(value: Double): String =
    if (value >= 1000) String.format("%.1fk", value / 1000) else "${value.toInt()}"
