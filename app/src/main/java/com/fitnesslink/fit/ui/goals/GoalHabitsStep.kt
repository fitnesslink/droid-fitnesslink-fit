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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.Habit
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
            habit.anchorBehavior?.let {
                Text(text = it, fontSize = 12.sp, color = TextSecondaryColor)
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
