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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.FrequencyType
import com.fitnesslink.fit.model.Habit
import com.fitnesslink.fit.model.HabitType
import java.util.UUID
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.GoalCreationViewModel

@Composable
fun GoalHabitsStep(viewModel: GoalCreationViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Your starter habits",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryColor
        )
        Text(
            text = "Small habits that build toward your goal. You can adjust these later.",
            fontSize = 14.sp,
            color = TextSecondaryColor
        )
        Spacer(modifier = Modifier.height(4.dp))

        viewModel.suggestedHabits.forEach { habit ->
            SuggestedHabitCard(
                habit = habit,
                isSelected = viewModel.selectedHabitIds.contains(habit.id),
                onToggle = { viewModel.toggleHabit(habit.id) }
            )
        }

        // Inline form for adding a habit the AI didn't suggest. Lands in
        // the same suggestedHabits list and is selected by default so it
        // ships with the goal on submit.
        AddCustomHabitRow(
            onAdd = { title ->
                val habit = Habit(
                    id = UUID.randomUUID().toString(),
                    title = title.trim(),
                    habitType = HabitType.ActionBased,
                    frequencyType = FrequencyType.Daily,
                    estimatedMinutes = 5
                )
                viewModel.suggestedHabits.add(habit)
                viewModel.selectedHabitIds.add(habit.id)
            }
        )

        viewModel.submitError?.let { err ->
            Text(
                text = err,
                fontSize = 13.sp,
                color = Color(0xFFD64545),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        val count = viewModel.selectedHabitIds.size
        Box(modifier = Modifier.fillMaxWidth()) {
            StepPrimaryButton(
                label = if (viewModel.isSubmitting) "Creating…" else "Start My Journey",
                sublabel = "$count habit${if (count == 1) "" else "s"} selected",
                enabled = !viewModel.isSubmitting,
                onClick = viewModel::next
            )
            if (viewModel.isSubmitting) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SuggestedHabitCard(
    habit: Habit,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) FLPrimary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onToggle)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = if (isSelected) FLPrimary else MediumGray.copy(alpha = 0.2f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = habit.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryColor
            )
            // Why-this-habit one-liner derived from habit type. Gives the
            // user a reason to keep or drop the suggestion at a glance.
            Text(
                text = rationale(habit),
                fontSize = 12.sp,
                color = TextSecondaryColor
            )
            habit.anchorBehavior?.let {
                Text(text = "When: $it", fontSize = 11.sp, color = TextSecondaryColor)
            }
            // Categorical chips: type + frequency + tier. Keeps the card
            // skimmable without burying the rationale.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HabitChip(text = habit.habitType.name)
                HabitChip(text = habit.frequencyType.name)
                HabitChip(text = habit.tier.name)
            }
            val target = habit.targetValue?.let { tv ->
                "${if (tv % 1.0 == 0.0) tv.toInt().toString() else tv.toString()} ${habit.targetUnit ?: ""}".trim()
            }
            if (!target.isNullOrBlank()) {
                Text(
                    text = "Target: $target  ·  ~${habit.estimatedMinutes} min",
                    fontSize = 11.sp,
                    color = TextSecondaryColor
                )
            }
        }
    }
}

private fun rationale(habit: Habit): String = when (habit.habitType) {
    HabitType.ActionBased -> "Tiny action that builds momentum daily."
    HabitType.TimeBased -> "Short timed effort — easy to start, hard to skip."
    HabitType.PassiveSensor -> "Tracked automatically, no logging needed."
    HabitType.Nutrition -> "Anchors a healthier eating pattern."
    HabitType.Avoidance -> "Removes a friction so the goal sticks."
    HabitType.Reflection -> "Closes the loop and surfaces what's working."
}

@Composable
private fun HabitChip(text: String) {
    Box(
        modifier = Modifier
            .background(MediumGray.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondaryColor
        )
    }
}

@Composable
private fun AddCustomHabitRow(onAdd: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var draftTitle by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .border(1.dp, MediumGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable(enabled = !expanded) { expanded = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = FLPrimary,
            modifier = Modifier.size(20.dp)
        )
        if (!expanded) {
            Text(
                text = "Add your own habit",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = FLPrimary,
                modifier = Modifier.weight(1f)
            )
        } else {
            BasicTextField(
                value = draftTitle,
                onValueChange = { draftTitle = it },
                singleLine = true,
                cursorBrush = SolidColor(FLPrimary),
                textStyle = TextStyle(fontSize = 14.sp, color = TextPrimaryColor),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (draftTitle.isEmpty()) {
                        Text(
                            text = "e.g., 10-minute walk after dinner",
                            fontSize = 14.sp,
                            color = TextSecondaryColor
                        )
                    }
                    inner()
                }
            )
            Text(
                text = "Add",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (draftTitle.isNotBlank()) FLPrimary else MediumGray,
                modifier = Modifier
                    .clickable(enabled = draftTitle.isNotBlank()) {
                        onAdd(draftTitle)
                        draftTitle = ""
                        expanded = false
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
