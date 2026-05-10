package com.fitnesslink.fit.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.Habit
import com.fitnesslink.fit.model.Milestone
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.GoalDetailViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GoalDetailScreen(
    goalId: String,
    onBack: () -> Unit,
    onOpenHabit: (String) -> Unit = {}
) {
    val viewModel: GoalDetailViewModel = viewModel()
    LaunchedEffect(goalId) { viewModel.loadData(goalId) }

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
            // Header
            Text(
                text = viewModel.goal.title.ifBlank { viewModel.goal.goalType.displayName },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor
            )
            if (viewModel.goal.description.isNotBlank()) {
                Text(
                    text = viewModel.goal.description,
                    fontSize = 14.sp,
                    color = TextSecondaryColor
                )
            }

            // Habits — light surface so the page isn't milestones-only
            if (viewModel.habits.isNotEmpty()) {
                SectionHeader("Habits")
                viewModel.habits.forEach { habit ->
                    val streak = viewModel.streaks[habit.id]
                    HabitRow(
                        habit = habit,
                        streakDays = streak?.currentCount ?: 0,
                        onClick = { onOpenHabit(habit.id) }
                    )
                }
            }

            // Milestones (FA-94)
            SectionHeader("Milestones")
            MilestonesSection(
                milestones = viewModel.milestones,
                onAchieve = viewModel::achieveMilestone,
                onAdd = { title, target -> viewModel.addMilestone(goalId, title, target) }
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimaryColor,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun HabitRow(habit: Habit, streakDays: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = habit.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimaryColor,
            modifier = Modifier.weight(1f)
        )
        StreakBadge(days = streakDays, tier = habit.tier)
    }
}

@Composable
private fun MilestonesSection(
    milestones: List<Milestone>,
    onAchieve: (String) -> Unit,
    onAdd: (title: String, target: Double) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (milestones.isEmpty()) {
            Text(
                text = "Add a milestone to celebrate progress before the finish line.",
                fontSize = 12.sp,
                color = TextSecondaryColor
            )
        } else {
            milestones.forEach { m -> MilestoneRow(m, onAchieve = { onAchieve(m.id) }) }
        }
        AddMilestoneRow(onAdd = onAdd)
    }
}

@Composable
private fun MilestoneRow(m: Milestone, onAchieve: () -> Unit) {
    val achieved = m.achievedAt != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !achieved, onClick = onAchieve)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (achieved) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (achieved) FLPrimary else MediumGray,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = m.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (achieved) TextSecondaryColor else TextPrimaryColor,
                textDecoration = if (achieved) TextDecoration.LineThrough else TextDecoration.None
            )
            val parts = buildList {
                if (m.targetValue > 0) add("Target: ${formatTarget(m.targetValue)}")
                if (achieved) add("Reached ${dateFormat.format(Date(m.achievedAt!!))}")
            }
            if (parts.isNotEmpty()) {
                Text(
                    text = parts.joinToString(" · "),
                    fontSize = 11.sp,
                    color = TextSecondaryColor
                )
            }
        }
    }
}

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
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = FLPrimary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Add milestone",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = FLPrimary
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MediumGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BasicTextField(
            value = title,
            onValueChange = { title = it },
            singleLine = true,
            cursorBrush = SolidColor(FLPrimary),
            textStyle = TextStyle(fontSize = 14.sp, color = TextPrimaryColor),
            decorationBox = { inner ->
                if (title.isEmpty()) {
                    Text("Milestone name", fontSize = 14.sp, color = TextSecondaryColor)
                }
                inner()
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Target:", fontSize = 12.sp, color = TextSecondaryColor)
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = target,
                onValueChange = { target = it.filter { ch -> ch.isDigit() || ch == '.' } },
                singleLine = true,
                cursorBrush = SolidColor(FLPrimary),
                textStyle = TextStyle(fontSize = 14.sp, color = TextPrimaryColor),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                decorationBox = { inner ->
                    if (target.isEmpty()) {
                        Text("0", fontSize = 14.sp, color = TextSecondaryColor)
                    }
                    inner()
                },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Cancel",
                fontSize = 13.sp,
                color = TextSecondaryColor,
                modifier = Modifier
                    .clickable {
                        expanded = false; title = ""; target = ""
                    }
                    .padding(8.dp)
            )
            Text(
                text = "Save",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
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

private fun formatTarget(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
