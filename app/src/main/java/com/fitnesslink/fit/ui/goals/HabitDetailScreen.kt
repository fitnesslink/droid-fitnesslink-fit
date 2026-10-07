package com.fitnesslink.fit.ui.goals

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.goals.HabitDayStatus
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.HabitDetailViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * iOS `HabitDetailView`: type icon + tier pill, title, anchor behaviour,
 * linked goal, streak panel (current / longest / 3 freeze dots), activity
 * heatmap (done / missed / freeze / pending), statistics grid, Recent
 * Activity. The heatmap and stats read local `habit_logs`; a "Mark done
 * today" button (new on both platforms) writes through `GoalStore.logHabit`.
 */
@Composable
fun HabitDetailScreen(
    habitId: String,
    onBack: () -> Unit
) {
    val viewModel: HabitDetailViewModel = viewModel()
    LaunchedEffect(habitId) { viewModel.loadData(habitId) }

    val habit = viewModel.habit
    val tierColor = habitTierColor(habit.tier)

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
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(habitTypeIcon(habit.habitType), contentDescription = null, tint = tierColor, modifier = Modifier.size(22.dp))
                    HabitTierPill(habit.tier, filled = true)
                }
                Text(habit.title.ifBlank { "Habit" }, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, textAlign = TextAlign.Center)
                habit.anchorBehavior?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 14.sp, color = TextSecondaryColor, textAlign = TextAlign.Center) }
                viewModel.goalTitle?.let { goalTitle ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Flag, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(12.dp))
                        Text(goalTitle, fontSize = 12.sp, color = TextSecondaryColor)
                    }
                }
            }

            // Today toggle (Android improvement)
            val done = viewModel.isCompletedToday
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (done) FLPrimary.copy(alpha = 0.12f) else FLPrimary)
                    .clickable { viewModel.toggleToday() }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (done) Icons.Default.CheckCircle else Icons.Default.Check,
                    contentDescription = null, tint = if (done) FLPrimary else White, modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (done) "Done today — tap to undo" else "Mark done today",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (done) FLPrimary else White
                )
            }

            // Streak panel
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .background(White, RoundedCornerShape(12.dp))
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = OrangeTheme, modifier = Modifier.size(22.dp))
                        Text("${viewModel.streak.currentCount}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                    }
                    Text("Current Streak", fontSize = 12.sp, color = TextSecondaryColor)
                }
                VerticalRule()
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${viewModel.streak.longestCount}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                    Text("Longest", fontSize = 12.sp, color = TextSecondaryColor)
                }
                VerticalRule()
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(3) { i ->
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (i < viewModel.streak.freezesAvailable) BlueTheme else Color.Gray.copy(alpha = 0.2f))
                            )
                        }
                    }
                    Text("Freezes", fontSize = 12.sp, color = TextSecondaryColor)
                }
            }

            // Heatmap
            CalendarHeatmapView(
                days = viewModel.windowDays,
                statuses = viewModel.dayStatuses,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // Statistics
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("Statistics", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GoalStatCard("Completion Rate", "${viewModel.stats.completionRate.toInt()}%", modifier = Modifier.weight(1f))
                    GoalStatCard("Total Days", "${viewModel.stats.totalDays}", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GoalStatCard("Completed", "${viewModel.stats.completedDays}", modifier = Modifier.weight(1f))
                    GoalStatCard("Missed", "${viewModel.stats.missedDays}", modifier = Modifier.weight(1f))
                }
            }

            // Recent activity
            if (viewModel.recentLogs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("Recent Activity", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                    viewModel.recentLogs.take(7).forEach { log ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
                            Text(mediumDate.format(Date(log.completedAt)), fontSize = 14.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                            Text(log.completionType.name, fontSize = 12.sp, color = TextSecondaryColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalRule() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp)
            .background(MediumGray.copy(alpha = 0.3f))
    )
}

/**
 * iOS `CalendarHeatmapView` statuses on the Android week-column grid:
 * 7 rows (Sun..Sat) by N week columns, done / missed / freeze / pending,
 * with the iOS legend.
 */
@Composable
fun CalendarHeatmapView(
    days: List<Long>,
    statuses: Map<Long, HabitDayStatus>,
    modifier: Modifier = Modifier
) {
    val cellSize = 14.dp
    val gap = 4.dp
    val weeks = if (days.isEmpty()) 0 else (days.size + 6) / 7

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Activity", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                    Text(it, fontSize = 9.sp, color = TextSecondaryColor, modifier = Modifier.size(cellSize))
                }
            }
            for (week in 0 until weeks) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (dow in 0..6) {
                        val day = days.getOrNull(week * 7 + dow) ?: continue
                        HeatmapCell(size = cellSize, status = statuses[day] ?: HabitDayStatus.PENDING, dateLabel = labelFormat.format(Date(day)))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            LegendSwatch(FLPrimary, "Done")
            LegendSwatch(Color.Red.copy(alpha = 0.3f), "Missed")
            LegendSwatch(BlueTheme.copy(alpha = 0.3f), "Freeze")
            LegendSwatch(Color.Gray.copy(alpha = 0.12f), "Pending")
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, fontSize = 10.sp, color = TextSecondaryColor)
    }
}

@Composable
private fun HeatmapCell(size: Dp, status: HabitDayStatus, dateLabel: String) {
    val color = when (status) {
        HabitDayStatus.COMPLETED -> FLPrimary
        HabitDayStatus.MISSED -> Color.Red.copy(alpha = 0.3f)
        HabitDayStatus.FREEZE -> BlueTheme.copy(alpha = 0.3f)
        HabitDayStatus.PENDING -> Color.Gray.copy(alpha = 0.12f)
    }
    val descriptor = "$dateLabel — ${status.wire}"
    Box(
        modifier = Modifier
            .size(size)
            .background(color, RoundedCornerShape(3.dp))
            .semantics { contentDescription = descriptor }
    )
}

private val labelFormat = SimpleDateFormat("EEE MMM d", Locale.getDefault())
private val mediumDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
