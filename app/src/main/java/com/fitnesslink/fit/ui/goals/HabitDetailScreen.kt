package com.fitnesslink.fit.ui.goals

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.HabitDetailViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val WEEKS = 12

@Composable
fun HabitDetailScreen(
    habitId: String,
    onBack: () -> Unit
) {
    val viewModel: HabitDetailViewModel = viewModel()
    LaunchedEffect(habitId) { viewModel.loadData(habitId) }

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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = viewModel.habit.title.ifBlank { "Habit" },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryColor,
                    modifier = Modifier.weight(1f)
                )
                StreakBadge(
                    days = viewModel.streak.currentCount,
                    tier = viewModel.habit.tier
                )
            }
            if (viewModel.habit.description.isNotBlank()) {
                Text(
                    text = viewModel.habit.description,
                    fontSize = 14.sp,
                    color = TextSecondaryColor
                )
            }

            Text(
                text = "Last $WEEKS weeks",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryColor
            )
            HeatmapCard(completedDays = viewModel.completedDays)

            // Streak summary card mirrors the badge but in long form.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(White, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${viewModel.streak.currentCount} day current streak",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryColor
                )
                Text(
                    text = "Longest: ${viewModel.streak.longestCount} days",
                    fontSize = 12.sp,
                    color = TextSecondaryColor
                )
            }
        }
    }
}

@Composable
private fun HeatmapCard(completedDays: Set<Long>) {
    val today = remember { startOfDayMillis() }
    val firstSunday = remember(today) { startOfWeekBefore(today, weeksBack = WEEKS - 1) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Heatmap grid: 7 rows (Sun..Sat), WEEKS columns.
        // Rendered column-first so each column is a calendar week. Day cells
        // are tinted FLPrimary if the habit was completed that day, neutral
        // grey otherwise. Future days show empty.
        val cellSize = 14.dp
        val gap = 4.dp
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            // Day-of-week labels column
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                    Text(
                        text = it,
                        fontSize = 9.sp,
                        color = TextSecondaryColor,
                        modifier = Modifier.size(cellSize)
                    )
                }
            }
            // Week columns
            for (week in 0 until WEEKS) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (dow in 0..6) {
                        val cellMs = addDays(firstSunday, week * 7 + dow)
                        val isFuture = cellMs > today
                        val isCompleted = !isFuture && completedDays.contains(cellMs)
                        HeatmapCell(
                            size = cellSize,
                            isCompleted = isCompleted,
                            isFuture = isFuture,
                            dateLabel = labelFormat.format(java.util.Date(cellMs))
                        )
                    }
                }
            }
        }

        // Legend
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text(
                text = "Less",
                fontSize = 10.sp,
                color = TextSecondaryColor
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(MediumGray.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(FLPrimary.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(FLPrimary, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "More",
                fontSize = 10.sp,
                color = TextSecondaryColor
            )
        }
    }
}

@Composable
private fun HeatmapCell(
    size: androidx.compose.ui.unit.Dp,
    isCompleted: Boolean,
    isFuture: Boolean,
    dateLabel: String
) {
    val color = when {
        isFuture -> Color.Transparent
        isCompleted -> FLPrimary
        else -> MediumGray.copy(alpha = 0.25f)
    }
    val descriptor = when {
        isFuture -> "$dateLabel — upcoming"
        isCompleted -> "$dateLabel — completed"
        else -> "$dateLabel — not completed"
    }
    Box(
        modifier = Modifier
            .size(size)
            .background(color, RoundedCornerShape(3.dp))
            .semantics { contentDescription = descriptor }
    )
}

private fun startOfDayMillis(): Long {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

/** Start-of-Sunday `weeksBack` weeks before [today] (inclusive). */
private fun startOfWeekBefore(today: Long, weeksBack: Int): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = today }
    val daysSinceSunday = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7
    cal.add(Calendar.DAY_OF_YEAR, -daysSinceSunday - (weeksBack * 7))
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun addDays(start: Long, days: Int): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = start }
    cal.add(Calendar.DAY_OF_YEAR, days)
    return cal.timeInMillis
}

private val labelFormat = SimpleDateFormat("EEE MMM d", Locale.getDefault())
