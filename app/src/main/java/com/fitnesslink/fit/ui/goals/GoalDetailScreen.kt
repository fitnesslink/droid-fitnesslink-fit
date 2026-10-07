package com.fitnesslink.fit.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.Habit
import com.fitnesslink.fit.model.Milestone
import com.fitnesslink.fit.model.Streak
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.GoalDetailViewModel
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * iOS `GoalDetailView`: `GoalProgressHeader` (ring + %, title, trajectory
 * pill), identity statement, linked habits (`HabitRowView`), milestone
 * timeline (`MilestoneRowView` with Next / % markers — Android keeps its
 * add-milestone form and tap-to-achieve), Current vs Target card, Target
 * Date row, and a trash button with confirmation that cascades the delete.
 */
@Composable
fun GoalDetailScreen(
    goalId: String,
    onBack: () -> Unit,
    onOpenHabit: (String) -> Unit = {}
) {
    val viewModel: GoalDetailViewModel = viewModel()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(goalId) { viewModel.loadData(goalId) }
    LaunchedEffect(viewModel.isDeleted) { if (viewModel.isDeleted) onBack() }

    val goal = viewModel.goal

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            HeaderBackView(onBack = onBack)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(White)
                    .clickable(enabled = goal.id.isNotEmpty()) { showDeleteConfirm = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete goal", tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            GoalProgressHeader(goal = goal)

            goal.identityStatement?.takeIf { it.isNotBlank() }?.let { identity ->
                Text(
                    "“$identity”",
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic,
                    color = TextSecondaryColor, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )
            }

            if (viewModel.habits.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionTitle("Linked Habits")
                    viewModel.habits.forEach { habit ->
                        HabitRowView(
                            habit = habit,
                            streak = viewModel.streaks[habit.id] ?: Streak(habitId = habit.id),
                            onClick = { onOpenHabit(habit.id) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
                SectionTitle("Milestones")
                viewModel.milestones.forEach { m ->
                    MilestoneRowView(
                        milestone = m,
                        goalProgress = goal.progressPercent,
                        onAchieve = { viewModel.achieveMilestone(m.id) }
                    )
                }
                AddMilestoneRow(onAdd = { title, target -> viewModel.addMilestone(goalId, title, target) })
            }

            val target = goal.targetValue
            val unit = goal.targetUnit
            if (target != null && unit != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionTitle("Target")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(White, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Current", fontSize = 12.sp, color = TextSecondaryColor)
                            Text("${formatOneDecimal(goal.currentValue)} $unit", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
                            Text("Target", fontSize = 12.sp, color = TextSecondaryColor)
                            Text("${formatOneDecimal(target)} $unit", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                        }
                    }
                }
            }

            goal.targetDate?.let { targetDate ->
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .background(White, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Target Date", fontSize = 14.sp, color = TextSecondaryColor, modifier = Modifier.weight(1f))
                    Text(mediumDate.format(Date(targetDate)), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Goal?") },
            text = { Text("This will remove \"${goal.title}\" and its progress. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteGoal()
                }) { Text("Delete", color = Color(0xFFE53935)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

/** iOS `GoalProgressHeader`: 120 dp ring with "% / complete", title, filled trajectory pill. */
@Composable
fun GoalProgressHeader(goal: Goal, modifier: Modifier = Modifier) {
    val percent = goal.progressPercent
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GoalProgressRing(percent = percent, color = trajectoryColor(goal.trajectoryStatus), size = 120.dp, strokeWidth = 10.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${percent.toInt()}%", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                Text("complete", fontSize = 12.sp, color = TextSecondaryColor)
            }
        }
        Text(
            goal.title.ifBlank { goal.goalType.displayName },
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        TrajectoryBadge(goal.trajectoryStatus, filled = true)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
}

/** iOS `HabitRowView`: type icon tile in the tier colour, title, tier badge + anchor, streak badge, chevron. */
@Composable
fun HabitRowView(habit: Habit, streak: Streak, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tierColor = habitTierColor(habit.tier)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(tierColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(habitTypeIcon(habit.habitType), contentDescription = null, tint = tierColor, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(habit.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HabitTierPill(habit.tier)
                habit.anchorBehavior?.takeIf { it.isNotBlank() }?.let { anchor ->
                    Text(anchor, fontSize = 11.sp, color = TextSecondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (streak.currentCount > 0) StreakBadge(days = streak.currentCount, tier = habit.tier)
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(14.dp))
    }
}

/**
 * iOS `MilestoneRowView`: 32 dp circle — brand check when achieved, else the
 * target % in grey; bold title + "Next" pill for the first unmet milestone.
 * Android keeps tap-to-achieve on unmet rows.
 */
@Composable
fun MilestoneRowView(milestone: Milestone, goalProgress: Double, onAchieve: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val achieved = milestone.achievedAt != null
    val isNext = !achieved && goalProgress < milestone.targetValue
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .clickable(enabled = !achieved && onAchieve != null) { onAchieve?.invoke() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (achieved) FLPrimary else Color.Gray.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            if (achieved) {
                Icon(Icons.Default.Check, contentDescription = "Achieved", tint = White, modifier = Modifier.size(16.dp))
            } else {
                Text("${milestone.targetValue.toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondaryColor)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                milestone.title,
                fontSize = 14.sp,
                fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                color = if (achieved) TextPrimaryColor else TextSecondaryColor
            )
            milestone.achievedAt?.let { Text(mediumDate.format(Date(it)), fontSize = 11.sp, color = TextSecondaryColor) }
        }
        if (isNext) {
            Text(
                "Next", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = FLPrimary,
                modifier = Modifier
                    .background(FLPrimary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

/** Android extra: inline "Add milestone" form (name + target %). */
@Composable
private fun AddMilestoneRow(onAdd: (String, Double) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }

    if (!expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
            Text("Add milestone", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(10.dp))
            .border(1.dp, MediumGray.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BasicTextField(
            value = title,
            onValueChange = { title = it },
            singleLine = true,
            cursorBrush = SolidColor(FLPrimary),
            textStyle = TextStyle(fontSize = 14.sp, color = TextPrimaryColor),
            decorationBox = { inner ->
                if (title.isEmpty()) Text("Milestone name", fontSize = 14.sp, color = TextSecondaryColor)
                inner()
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Target %:", fontSize = 12.sp, color = TextSecondaryColor)
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = target,
                onValueChange = { target = it.filter { ch -> ch.isDigit() || ch == '.' } },
                singleLine = true,
                cursorBrush = SolidColor(FLPrimary),
                textStyle = TextStyle(fontSize = 14.sp, color = TextPrimaryColor),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                decorationBox = { inner ->
                    if (target.isEmpty()) Text("50", fontSize = 14.sp, color = TextSecondaryColor)
                    inner()
                },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Cancel", fontSize = 13.sp, color = TextSecondaryColor,
                modifier = Modifier
                    .clickable { expanded = false; title = ""; target = "" }
                    .padding(8.dp)
            )
            Text(
                "Save", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (title.isNotBlank()) FLPrimary else MediumGray,
                modifier = Modifier
                    .clickable(enabled = title.isNotBlank()) {
                        onAdd(title, target.toDoubleOrNull() ?: 0.0)
                        title = ""; target = ""; expanded = false
                    }
                    .padding(8.dp)
            )
        }
    }
}

private fun formatOneDecimal(v: Double): String = String.format(Locale.US, "%.1f", v)

private val mediumDate: DateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
