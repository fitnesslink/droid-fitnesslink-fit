package com.fitnesslink.fit.ui.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.ChartPoint
import com.fitnesslink.fit.model.ReportMetric
import com.fitnesslink.fit.model.SessionRow
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.profile.charts.DeleteSessionDialog
import com.fitnesslink.fit.ui.profile.charts.ReportBarChart
import com.fitnesslink.fit.ui.profile.charts.ReportEmptyDetail
import com.fitnesslink.fit.ui.profile.charts.ReportLineChart
import com.fitnesslink.fit.ui.profile.charts.ReportSegmentedPicker
import com.fitnesslink.fit.ui.profile.charts.ReportTimeRangePicker
import com.fitnesslink.fit.ui.profile.charts.RpeBadge
import com.fitnesslink.fit.ui.profile.charts.formatReportDate
import com.fitnesslink.fit.ui.profile.charts.formatReportDuration
import com.fitnesslink.fit.ui.profile.charts.themeColor
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.WorkoutReportViewModel
import java.util.Calendar

/**
 * Port of iOS `ReportDetailView`: one metric's drill-down. Range picker,
 * a chart per metric (bars for completed / time / calories, lines for RPE /
 * weight / volume), then metric-specific content — filtered session list
 * with a per-metric highlight, RPE list with badges, Volume by workout /
 * by movement, PR list, or streak badges + recent workouts. Long-press a
 * session row for "Delete Session".
 *
 * [metric] is the `ReportMetric` enum name (the iOS raw title is accepted too).
 */
@Composable
fun ReportDetailScreen(
    metric: String,
    onBack: () -> Unit,
    onNavigateToSession: (String) -> Unit,
    viewModel: WorkoutReportViewModel = viewModel()
) {
    val resolved = remember(metric) { ReportMetric.fromRoute(metric) }
    var sessionToDelete by remember { mutableStateOf<SessionRow?>(null) }

    LaunchedEffect(Unit) { viewModel.loadData() }
    LaunchedEffect(viewModel.selectedTimeRange) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        if (resolved == null) {
            ReportEmptyDetail("Unknown report")
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MetricHeader(resolved, viewModel)

            ReportTimeRangePicker(
                selected = viewModel.selectedTimeRange,
                onSelect = { viewModel.selectedTimeRange = it },
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MediumGray.copy(alpha = 0.3f))

            MetricChart(resolved, viewModel)

            when (resolved) {
                ReportMetric.WORKOUTS_COMPLETED ->
                    SessionList(viewModel.sessions.filter { it.isCompleted }, null, onNavigateToSession) { sessionToDelete = it }
                ReportMetric.TOTAL_TIME ->
                    SessionList(viewModel.sessions, SessionHighlight.TIME, onNavigateToSession) { sessionToDelete = it }
                ReportMetric.INCOMPLETE_WORKOUTS ->
                    SessionList(viewModel.sessions.filter { !it.isCompleted }, null, onNavigateToSession) { sessionToDelete = it }
                ReportMetric.AVERAGE_RPE -> RpeDetail(viewModel)
                ReportMetric.EXERCISES_COMPLETED ->
                    SessionList(viewModel.sessions.filter { it.isCompleted }, SessionHighlight.EXERCISES, onNavigateToSession) { sessionToDelete = it }
                ReportMetric.TOTAL_WEIGHT_LIFTED ->
                    SessionList(viewModel.sessions.filter { it.totalWeightLifted > 0 }, SessionHighlight.WEIGHT, onNavigateToSession) { sessionToDelete = it }
                ReportMetric.CALORIES_BURNED ->
                    SessionList(viewModel.sessions.filter { it.totalCaloriesBurned > 0 }, SessionHighlight.CALORIES, onNavigateToSession) { sessionToDelete = it }
                ReportMetric.VOLUME_TRACKING -> VolumeDetail(viewModel)
                ReportMetric.PERSONAL_RECORDS -> PersonalRecordsDetail(viewModel)
                ReportMetric.STREAKS_FREQUENCY -> StreakDetail(viewModel)
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

// MARK: - Header + chart

@Composable
private fun MetricHeader(metric: ReportMetric, viewModel: WorkoutReportViewModel) {
    val color = metric.themeColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(metric.imageVector(), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(metric.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(viewModel.subtitle(metric), fontSize = 14.sp, color = TextSecondaryColor)
        }
        Text(viewModel.displayValue(metric), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun MetricChart(metric: ReportMetric, viewModel: WorkoutReportViewModel) {
    val raw = viewModel.chartPoints(metric)
    if (raw.size < 2) return
    val color = metric.themeColor()
    // Weight series are summed in stored lbs; plot the display unit (FTNSSLNK-325).
    val points: List<ChartPoint> = when (metric) {
        ReportMetric.TOTAL_WEIGHT_LIFTED, ReportMetric.VOLUME_TRACKING ->
            raw.map { it.copy(value = WeightUnit.displayValue(it.value)) }
        else -> raw
    }
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .padding(14.dp)
    ) {
        when (metric) {
            ReportMetric.WORKOUTS_COMPLETED, ReportMetric.EXERCISES_COMPLETED,
            ReportMetric.CALORIES_BURNED, ReportMetric.TOTAL_TIME ->
                ReportBarChart(points = points, color = color)
            ReportMetric.AVERAGE_RPE ->
                ReportLineChart(
                    points = points, color = color, fill = false,
                    yRange = 1.0..10.0, referenceY = viewModel.reportData.averageRPE
                )
            ReportMetric.TOTAL_WEIGHT_LIFTED, ReportMetric.VOLUME_TRACKING ->
                ReportLineChart(points = points, color = color, fill = true, showPoints = false)
            else -> Unit
        }
    }
}

// MARK: - Session list

private enum class SessionHighlight { TIME, EXERCISES, WEIGHT, CALORIES }

@Composable
private fun SessionList(
    sessions: List<SessionRow>,
    highlight: SessionHighlight?,
    onOpen: (String) -> Unit,
    onLongPress: (SessionRow) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (sessions.isEmpty()) {
            ReportEmptyDetail()
        } else {
            sessions.forEach { session ->
                SessionRowCard(session, highlight, onClick = { onOpen(session.id) }, onLongClick = { onLongPress(session) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRowCard(
    session: SessionRow,
    highlight: SessionHighlight?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(session.workoutName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            // Completion date when finished; start date for legacy / in-progress rows (FTNSSLNK-319).
            Text(formatReportDate(session.displayDate), fontSize = 12.sp, color = TextSecondaryColor)
            val p = session.completionPercent
            if (session.isCompleted && p != null && p < 100) {
                Text(session.statusLabel, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = OrangeTheme)
            }
        }
        HighlightValue(session, highlight)
        Spacer(modifier = Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun HighlightValue(session: SessionRow, highlight: SessionHighlight?) {
    when (highlight) {
        SessionHighlight.TIME ->
            Text(formatReportDuration(session.durationSeconds), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BlueTheme)
        SessionHighlight.EXERCISES ->
            Text("${session.exerciseCount}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FLPrimary)
        SessionHighlight.WEIGHT ->
            Text(
                "${WeightUnit.displayWholeNumber(session.totalWeightLifted)} ${WeightUnit.current.label}",
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BlueTheme
            )
        SessionHighlight.CALORIES ->
            Text("${session.totalCaloriesBurned.toInt()} kcal", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OrangeTheme)
        null ->
            if (session.isCompleted) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = FLPrimary, modifier = Modifier.size(18.dp))
            } else {
                Icon(Icons.Default.Warning, contentDescription = "Incomplete", tint = OrangeTheme, modifier = Modifier.size(18.dp))
            }
    }
}

// MARK: - RPE

@Composable
private fun RpeDetail(viewModel: WorkoutReportViewModel) {
    val withRpe = viewModel.sessions.filter { it.rpeValue != null && it.isCompleted }
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (withRpe.isEmpty()) {
            ReportEmptyDetail()
        } else {
            withRpe.forEach { session ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(White)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(session.workoutName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(formatReportDate(session.date), fontSize = 12.sp, color = TextSecondaryColor)
                    }
                    RpeBadge(session.rpeValue ?: 0.0)
                }
            }
        }
    }
}

// MARK: - Volume

private enum class VolumeTab(val label: String) { BY_WORKOUT("By Workout"), BY_MOVEMENT("By Movement") }

@Composable
private fun VolumeDetail(viewModel: WorkoutReportViewModel) {
    var tab by remember { mutableStateOf(VolumeTab.BY_WORKOUT) }
    val sessionsWithVolume = viewModel.sessions.filter { it.totalWeightLifted > 0 }
    val grouped = viewModel.volumeEntries.groupBy { it.exerciseName }
    val sortedMovements = grouped.entries.sortedByDescending { e -> e.value.sumOf { it.volume } }
    val unit = WeightUnit.current.label

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReportSegmentedPicker(
            options = VolumeTab.entries, selected = tab, label = { it.label }, onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        if (sessionsWithVolume.isEmpty() && sortedMovements.isEmpty()) {
            ReportEmptyDetail()
            return@Column
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (tab) {
                VolumeTab.BY_WORKOUT -> sessionsWithVolume.forEach { session ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(White)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(session.workoutName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(formatReportDate(session.date), fontSize = 12.sp, color = TextSecondaryColor)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${WeightUnit.displayWholeNumber(session.totalWeightLifted)} $unit",
                                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PurpleTheme
                                )
                                Text("total volume", fontSize = 11.sp, color = TextSecondaryColor)
                            }
                        }
                        // Movement breakdown for this session (same calendar day).
                        val sessionEntries = viewModel.volumeEntries.filter { sameDay(it.date.time, session.date.time) }
                        val byMovement = sessionEntries.groupBy { it.exerciseName }.entries.sortedBy { it.key }
                        if (byMovement.isNotEmpty()) {
                            HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
                            byMovement.forEach { (name, entries) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, fontSize = 13.sp, color = TextSecondaryColor, modifier = Modifier.weight(1f))
                                    Text(
                                        "${WeightUnit.displayWholeNumber(entries.sumOf { it.volume })} $unit",
                                        fontSize = 13.sp, fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
                VolumeTab.BY_MOVEMENT -> sortedMovements.forEach { (name, entries) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(White)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(
                                "${WeightUnit.displayWholeNumber(entries.sumOf { it.volume })} $unit",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PurpleTheme
                            )
                        }
                        entries.take(5).forEach { entry ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(formatReportDate(entry.date), fontSize = 12.sp, color = TextSecondaryColor, modifier = Modifier.weight(1f))
                                Text(
                                    "${entry.reps} reps x ${WeightUnit.displayString(entry.weight)}",
                                    fontSize = 13.sp, fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

// MARK: - Personal records

@Composable
private fun PersonalRecordsDetail(viewModel: WorkoutReportViewModel) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (viewModel.personalRecords.isEmpty()) {
            ReportEmptyDetail()
        } else {
            viewModel.personalRecords.forEach { pr ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(White)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(pr.exerciseName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(formatReportDate(pr.achievedDate), fontSize = 12.sp, color = TextSecondaryColor)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(WeightUnit.displayString(pr.maxWeight), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FLPrimary)
                        Text("${pr.maxReps} reps", fontSize = 12.sp, color = TextSecondaryColor)
                    }
                }
            }
        }
    }
}

// MARK: - Streaks

@Composable
private fun StreakDetail(viewModel: WorkoutReportViewModel) {
    val streak = viewModel.streak
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StreakBadge("Current", streak.currentStreak, "days", FLPrimary, Modifier.weight(1f))
            StreakBadge("Longest", streak.longestStreak, "days", BlueTheme, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StreakBadge("This Week", streak.workoutsThisWeek, "workouts", OrangeTheme, Modifier.weight(1f))
            StreakBadge("This Month", streak.workoutsThisMonth, "workouts", PurpleTheme, Modifier.weight(1f))
        }

        val completed = viewModel.sessions.filter { it.isCompleted }
        if (completed.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Recent Workouts", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                completed.take(10).forEach { session ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(session.workoutName, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(formatReportDate(session.date), fontSize = 12.sp, color = TextSecondaryColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakBadge(title: String, value: Int, unit: String, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("$value", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(unit, fontSize = 11.sp, color = TextSecondaryColor)
        Spacer(modifier = Modifier.height(0.dp))
    }
}
