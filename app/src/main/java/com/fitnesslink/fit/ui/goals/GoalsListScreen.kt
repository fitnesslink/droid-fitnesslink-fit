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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalStatus
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.GoalListViewModel

@Composable
fun GoalsListScreen(
    onBack: () -> Unit,
    onCreateGoal: () -> Unit,
    onOpenGoal: (String) -> Unit
) {
    val viewModel: GoalListViewModel = viewModel()
    LaunchedEffect(Unit) { viewModel.loadData() }

    val active = viewModel.goals.filter { it.status != GoalStatus.Archived }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Goals",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(FLPrimary)
                    .clickable(onClick = onCreateGoal),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create goal",
                    tint = White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (active.isEmpty()) {
            EmptyGoalsState(onCreateGoal = onCreateGoal)
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                items(active, key = { it.id }) { goal ->
                    GoalRow(goal = goal, onClick = { onOpenGoal(goal.id) })
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun GoalRow(goal: Goal, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = goal.title.ifBlank { goal.goalType.displayName },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryColor,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = goal.status.name,
                fontSize = 11.sp,
                color = TextSecondaryColor
            )
        }
        if (goal.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = goal.description,
                fontSize = 13.sp,
                color = TextSecondaryColor,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun EmptyGoalsState(onCreateGoal: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "No goals yet",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimaryColor
        )
        Text(
            text = "Set a focus and we'll suggest starter habits.",
            fontSize = 13.sp,
            color = TextSecondaryColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(FLPrimary)
                .clickable(onClick = onCreateGoal)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Create a goal",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = White
            )
        }
    }
}
