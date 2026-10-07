package com.fitnesslink.fit.ui.profile

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.*
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.navigation.AppRoute
import com.fitnesslink.fit.ui.profile.charts.ReportMiniStat
import com.fitnesslink.fit.ui.profile.charts.ReportSegmentedPicker
import com.fitnesslink.fit.ui.profile.charts.ReportTimeRangePicker
import com.fitnesslink.fit.ui.profile.charts.ReportTrendPill
import com.fitnesslink.fit.ui.profile.charts.themeColor
import com.fitnesslink.fit.ui.theme.*
import com.fitnesslink.fit.viewmodel.WorkoutReportViewModel

/**
 * Port of iOS `WorkoutReportView`: range (7d / 30d / All), tabs
 * (Overview / By Workout / By Movement), ten metric cards, aggregate cards
 * with trends and movement cards with PR. Pull-to-refresh re-runs the
 * `/sessions/me` backfill. Cards navigate via [onNavigate] with the route
 * strings `reportDetail/{metric}`, `workoutAggregate/{workoutName}` and
 * `movementAggregate/{exerciseName}` (names URL-encoded).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutReportScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: WorkoutReportViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(viewModel.selectedTimeRange) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        PullToRefreshBox(
            isRefreshing = viewModel.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 10.dp, bottom = 30.dp)
            ) {
                Text(
                    text = "Workout Report",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                ReportTimeRangePicker(
                    selected = viewModel.selectedTimeRange,
                    onSelect = { viewModel.selectedTimeRange = it },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                ReportSegmentedPicker(
                    options = ReportTab.entries,
                    selected = viewModel.selectedTab,
                    label = { it.label },
                    onSelect = { viewModel.selectedTab = it },
                    modifier = Modifier.padding(horizontal = 20.dp)
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
}

// MARK: - Overview

@Composable
private fun OverviewContent(viewModel: WorkoutReportViewModel, onNavigate: (String) -> Unit) {
    if (viewModel.reportData.workoutsCompleted == 0 && viewModel.reportData.incompleteWorkouts == 0) {
        EmptyState(
            "No workout data yet",
            if (viewModel.isHydrating) "Checking your history…" else "Complete a workout to see your report"
        )
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
                        onClick = { onNavigate(AppRoute.ReportDetail.createRoute(metrics[i].name)) },
                        modifier = Modifier.weight(1f)
                    )
                    if (i + 1 < metrics.size) {
                        MetricCard(
                            metric = metrics[i + 1],
                            value = viewModel.displayValue(metrics[i + 1]),
                            subtitle = viewModel.subtitle(metrics[i + 1]),
                            onClick = { onNavigate(AppRoute.ReportDetail.createRoute(metrics[i + 1].name)) },
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
    val color = metric.themeColor()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Icon(
            imageVector = metric.imageVector(),
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
                    onNavigate(AppRoute.WorkoutAggregateDetail.createRoute(Uri.encode(agg.workoutName)))
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
            ReportMiniStat("Avg Time", viewModel.formatDuration(agg.avgDurationSeconds))
            ReportMiniStat("Avg RPE", if (agg.avgRPE > 0) String.format(java.util.Locale.US, "%.1f", agg.avgRPE) else "--")
            ReportMiniStat("Weight", viewModel.formatWeight(agg.totalWeightLifted))
            ReportMiniStat("Cal", "${agg.totalCaloriesBurned.toInt()}")
        }
        if (agg.sessions.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportTrendPill("Volume", agg.volumeTrend)
                ReportTrendPill("RPE", agg.rpeTrend)
                ReportTrendPill("Duration", agg.durationTrend)
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
                MovementAggregateCard(agg, viewModel) {
                    onNavigate(AppRoute.MovementAggregateDetail.createRoute(Uri.encode(agg.exerciseName)))
                }
            }
        }
    }
}

@Composable
private fun MovementAggregateCard(agg: MovementAggregate, viewModel: WorkoutReportViewModel, onClick: () -> Unit) {
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
            ReportMiniStat("Sets", "${agg.totalSets}")
            ReportMiniStat("Reps", "${agg.totalReps}")
            ReportMiniStat("Max ${WeightUnit.current.label}", WeightUnit.displayNumber(agg.maxWeight))
            ReportMiniStat("Volume", viewModel.formatWeight(agg.totalVolume))
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
                Text(
                    "PR: ${WeightUnit.displayString(agg.prWeight)} x ${agg.prReps} reps",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary
                )
            }
        }
    }
}

// MARK: - Shared Components

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

/** Material icon standing in for the metric's SF Symbol. */
fun ReportMetric.imageVector(): ImageVector = when (this) {
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
