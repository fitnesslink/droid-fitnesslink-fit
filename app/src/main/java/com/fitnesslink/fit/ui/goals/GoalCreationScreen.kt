package com.fitnesslink.fit.ui.goals

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.viewmodel.GoalCreationViewModel

/** Multi-step goal-setup flow (FA-90). Three steps: direction → details → habits. */
@Composable
fun GoalCreationScreen(
    onClose: () -> Unit,
    viewModel: GoalCreationViewModel = viewModel()
) {
    if (viewModel.isComplete) {
        // Auto-dismiss after a successful submit. Calling onClose during
        // composition is ok here — Compose will route it through next frame.
        onClose()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        SetupTopBar(
            currentStep = viewModel.currentStep,
            totalSteps = 3,
            onBack = if (viewModel.currentStep > 1) viewModel::previous else null,
            onCancel = onClose
        )
        StepProgressBar(
            currentStep = viewModel.currentStep,
            totalSteps = 3,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        Crossfade(
            targetState = viewModel.currentStep,
            label = "goal-setup-step"
        ) { step ->
            when (step) {
                1 -> GoalDirectionStep(viewModel = viewModel)
                2 -> GoalDetailsStep(viewModel = viewModel)
                3 -> GoalHabitsStep(viewModel = viewModel)
                else -> Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun SetupTopBar(
    currentStep: Int,
    totalSteps: Int,
    onBack: (() -> Unit)?,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .height(36.dp)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (onBack != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimaryColor,
                    modifier = Modifier.clickable(onClick = onBack)
                )
            }
        }
        Text(
            text = "Step $currentStep of $totalSteps",
            fontSize = 13.sp,
            color = TextSecondaryColor
        )
        Text(
            text = "Cancel",
            fontSize = 14.sp,
            color = TextSecondaryColor,
            modifier = Modifier
                .clickable(onClick = onCancel)
                .padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun StepProgressBar(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(totalSteps) { index ->
            val step = index + 1
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        color = if (step <= currentStep) FLPrimary else MediumGray.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

/** Reusable primary action button used by every step. */
@Composable
internal fun StepPrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sublabel: String? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (enabled) FLPrimary else MediumGray,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            if (sublabel != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = sublabel,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}
