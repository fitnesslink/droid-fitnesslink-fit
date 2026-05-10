package com.fitnesslink.fit.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.GoalType
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.GoalCreationViewModel

private data class GoalDirection(
    val type: GoalType,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

private val DIRECTIONS = listOf(
    GoalDirection(GoalType.Performance, "Get Stronger", "Build strength and hit PRs", Icons.Filled.FitnessCenter),
    GoalDirection(GoalType.BodyComposition, "Lose Weight", "Sustainable fat loss", Icons.Filled.MonitorWeight),
    GoalDirection(GoalType.Consistency, "Build a Routine", "Show up consistently", Icons.Filled.EventAvailable),
    GoalDirection(GoalType.Nutrition, "Eat Better", "Improve your nutrition", Icons.Filled.Restaurant),
    GoalDirection(GoalType.RecoveryWellness, "Sleep & Recover", "Rest and rejuvenate", Icons.Filled.Bedtime),
    GoalDirection(GoalType.Custom, "Custom Goal", "Define your own path", Icons.Filled.Star)
)

@Composable
fun GoalDirectionStep(viewModel: GoalCreationViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "What's your focus?",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryColor,
            modifier = Modifier.padding(top = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            items(DIRECTIONS, key = { it.type.name }) { direction ->
                DirectionCard(
                    direction = direction,
                    isSelected = viewModel.selectedGoalType == direction.type,
                    onClick = { viewModel.selectedGoalType = direction.type }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        StepPrimaryButton(
            label = "Next",
            enabled = viewModel.canProceedStep1,
            onClick = {
                viewModel.selectedGoalType?.let { viewModel.selectGoalType(it) }
                viewModel.next()
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DirectionCard(
    direction: GoalDirection,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isSelected) FLPrimary.copy(alpha = 0.08f) else White,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) FLPrimary else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp, horizontal = 8.dp)
    ) {
        Icon(
            imageVector = direction.icon,
            contentDescription = direction.title,
            tint = if (isSelected) FLPrimary else MediumGray,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = direction.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimaryColor,
            textAlign = TextAlign.Center
        )
        Text(
            text = direction.subtitle,
            fontSize = 11.sp,
            color = TextSecondaryColor,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
