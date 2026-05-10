package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.WaterIntakeEntry
import com.fitnesslink.fit.model.WaterUnit
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.HydrationViewModel
import java.util.Date

@Composable
fun HydrationCard(
    onLogClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HydrationViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.loadData() }

    val quickAmounts: List<Double> =
        if (viewModel.goal.unit == WaterUnit.ML) listOf(250.0, 500.0) else listOf(8.0, 16.0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.WaterDrop,
                    contentDescription = "Water",
                    tint = BlueTheme
                )
                Text(
                    text = "Water",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Log",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = BlueTheme,
                modifier = Modifier.clickable(onClick = onLogClick)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatAmount(viewModel.totalIntake),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlueTheme
                )
                Text(
                    text = " / ${formatAmount(viewModel.goal.dailyGoal)} ${viewModel.goal.unit.rawValue}",
                    fontSize = 13.sp,
                    color = TextSecondaryColor,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            Text(
                text = "${(viewModel.hydrationProgress * 100).toInt()}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondaryColor
            )
        }

        LinearProgressIndicator(
            progress = { viewModel.hydrationProgress.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = BlueTheme,
            trackColor = BlueTheme.copy(alpha = 0.15f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickAmounts.forEach { amount ->
                QuickAddChip(
                    amount = amount,
                    unit = viewModel.goal.unit,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.addEntry(
                            WaterIntakeEntry(
                                amount = amount,
                                unit = viewModel.goal.unit,
                                loggedAt = Date(),
                                notes = ""
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickAddChip(
    amount: Double,
    unit: WaterUnit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Text(
        text = "+${formatAmount(amount)} ${unit.rawValue}",
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = BlueTheme,
        modifier = modifier
            .background(BlueTheme.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

internal fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
