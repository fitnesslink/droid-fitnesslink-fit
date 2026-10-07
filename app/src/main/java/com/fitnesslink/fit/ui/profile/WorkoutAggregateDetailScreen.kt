package com.fitnesslink.fit.ui.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.ChartPoint
import com.fitnesslink.fit.model.SessionRow
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutAggregate
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.profile.charts.DeleteSessionDialog
import com.fitnesslink.fit.ui.profile.charts.ReportBarChart
import com.fitnesslink.fit.ui.profile.charts.ReportEmptyDetail
import com.fitnesslink.fit.ui.profile.charts.ReportLineChart
import com.fitnesslink.fit.ui.profile.charts.ReportStatCard
import com.fitnesslink.fit.ui.profile.charts.ReportTimeRangePicker
import com.fitnesslink.fit.ui.profile.charts.ReportTrendRow
import com.fitnesslink.fit.ui.profile.charts.formatReportDate
import com.fitnesslink.fit.ui.profile.charts.formatReportDuration
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.WorkoutReportViewModel
import java.util.Locale

/**
 * Port of iOS `WorkoutAggregateDetailView`: every session of one workout.
 * Stats grid, trends (latest vs previous), Volume / RPE / Duration trend
 * charts, best session, and the session history (tap → session detail,
 * long-press → Delete Session).
 */
@Composable
fun WorkoutAggregateDetailScreen(
    workoutName: String,
    onBack: () -> Unit,
    onNavigateToSession: (String) -> Unit,
    viewModel: WorkoutReportViewModel = viewModel()
) {
    var loaded by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<SessionRow?>(null) }

    LaunchedEffect(Unit) { viewModel.loadData(); loaded = true }
    LaunchedEffect(viewModel.selectedTimeRange) { viewModel.loadData() }

    val aggregate: WorkoutAggregate? = viewModel.workoutAggregates.firstOrNull { it.workoutName == workoutName }
        ?: viewModel.workoutAggregates.firstOrNull { it.workoutName.equals(workoutName, ignoreCase = true) }

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
                .padding(top = 10.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(aggregate?.workoutName ?: workoutName, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("${aggregate?.timesCompleted ?: 0} completed sessions", fontSize = 14.sp, color = TextSecondaryColor)
            }

            ReportTimeRangePicker(
                selected = viewModel.selectedTimeRange,
                onSelect = { viewModel.selectedTimeRange = it },
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            when {
                aggregate != null -> {
                    StatsGrid(aggregate, viewModel)
                    if (aggregate.sessions.size > 1) TrendsCard(aggregate)
                    if (aggregate.sessions.count { it.isCompleted } >= 2) PerformanceCharts(aggregate)
                    aggregate.bestSession?.let { BestSessionCard(it) }
                    SessionHistory(aggregate.sessions, onNavigateToSession) { sessionToDelete = it }
                }
                !loaded -> Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FLPrimary)
                }
                else -> ReportEmptyDetail("No sessions of this workout in this period")
            }
        }
    }

    DeleteSessionDialog(
        session = sessionToDelete,
        onConfirm = { session ->
            sessionToDelete = null
            viewModel.deleteSession(session.id)
        },
        onDismiss = { sessionToDelete = null }
    )
}

// MARK: - Stats

@Composable
private fun StatsGrid(agg: WorkoutAggregate, viewModel: WorkoutReportViewModel) {
    Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReportStatCard(Icons.Default.AccessTime, formatReportDuration(agg.avgDurationSeconds), "Avg Duration", BlueTheme, Modifier.weight(1f))
            ReportStatCard(
                Icons.Default.Speed,
                if (agg.avgRPE > 0) String.format(Locale.US, "%.1f", agg.avgRPE) else "--",
                "Avg RPE", PurpleTheme, Modifier.weight(1f)
            )
            ReportStatCard(Icons.Default.DirectionsRun, "${agg.timesCompleted}", "Completed", FLPrimary, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReportStatCard(Icons.Default.FitnessCenter, viewModel.formatWeight(agg.totalWeightLifted), "Total Weight", BlueTheme, Modifier.weight(1f))
            ReportStatCard(Icons.Default.BarChart, viewModel.formatWeight(agg.totalVolume), "Total Volume", PurpleTheme, Modifier.weight(1f))
            ReportStatCard(Icons.Default.LocalFireDepartment, "${agg.totalCaloriesBurned.toInt()}", "Calories", OrangeTheme, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TrendsCard(agg: WorkoutAggregate) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Trends", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ReportTrendRow("Volume", agg.volumeTrend, Modifier.weight(1f))
            ReportTrendRow("RPE", agg.rpeTrend, Modifier.weight(1f))
            ReportTrendRow("Duration", agg.durationTrend, Modifier.weight(1f))
        }
    }
}

// MARK: - Charts

@Composable
private fun PerformanceCharts(agg: WorkoutAggregate) {
    val completed = agg.sessions.filter { it.isCompleted }.sortedBy { it.date }
    val volumePoints = completed.map { ChartPoint(it.date, WeightUnit.displayValue(it.totalWeightLifted)) }
    val withRpe = completed.filter { it.rpeValue != null }
    val rpePoints = withRpe.map { ChartPoint(it.date, it.rpeValue ?: 0.0) }
    val durationPoints = completed.map { ChartPoint(it.date, it.durationSeconds / 60.0) }

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Volume Trend", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            ReportLineChart(points = volumePoints, color = PurpleTheme, height = 140.dp)
        }
        HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
        if (withRpe.size >= 2) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("RPE Trend", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                ReportLineChart(points = rpePoints, color = OrangeTheme, height = 140.dp, fill = false, yRange = 1.0..10.0)
            }
            HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Duration Trend", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            ReportBarChart(points = durationPoints, color = BlueTheme, height = 140.dp)
        }
    }
}

// MARK: - Best session

@Composable
private fun BestSessionCard(session: SessionRow) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
            Text("Best Session", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatReportDate(session.date), fontSize = 13.sp, color = TextSecondaryColor)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${WeightUnit.displayWholeNumber(session.totalWeightLifted)} ${WeightUnit.current.label}",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text("${session.totalCaloriesBurned.toInt()} cal", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(formatReportDuration(session.durationSeconds), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            session.rpeValue?.let { rpe ->
                Text(String.format(Locale.US, "%.1f RPE", rpe), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PurpleTheme)
            }
        }
    }
}

// MARK: - History

@Composable
private fun SessionHistory(
    sessions: List<SessionRow>,
    onOpen: (String) -> Unit,
    onLongPress: (SessionRow) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Session History", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        sessions.forEach { session ->
            HistoryRow(session, onClick = { onOpen(session.id) }, onLongClick = { onLongPress(session) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(session: SessionRow, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatReportDate(session.date), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(formatReportDuration(session.durationSeconds), fontSize = 12.sp, color = TextSecondaryColor)
                if (session.totalWeightLifted > 0) {
                    Text(
                        "${WeightUnit.displayWholeNumber(session.totalWeightLifted)} ${WeightUnit.current.label}",
                        fontSize = 12.sp, color = TextSecondaryColor
                    )
                }
            }
        }
        session.rpeText?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PurpleTheme) }
        if (session.isCompleted) {
            val p = session.completionPercent
            if (p != null && p < 100) {
                Text("$p%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OrangeTheme)
            }
            Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = FLPrimary, modifier = Modifier.size(16.dp))
        } else {
            Icon(Icons.Default.Warning, contentDescription = "Incomplete", tint = OrangeTheme, modifier = Modifier.size(16.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(18.dp))
    }
}
