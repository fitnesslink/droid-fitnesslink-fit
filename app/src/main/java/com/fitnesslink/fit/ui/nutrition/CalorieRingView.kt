package com.fitnesslink.fit.ui.nutrition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.ProgressBG
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor

/**
 * Circular calorie progress: consumed / goal / remaining (iOS
 * `CalorieRingView`, FTNSSLNK-193). [remaining] is SIGNED — negative means
 * over target, which flips the ring stroke and label to the amber
 * over-state (a signal, not an error; shared with `MacroBarView`).
 */
@Composable
fun CalorieRingView(
    consumed: Int,
    goal: Int,
    remaining: Int,
    progress: Double,
    modifier: Modifier = Modifier
) {
    val isOver = remaining < 0
    val ringColor = if (isOver) NutritionOverColor else FLPrimary
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0.0, 1.0).toFloat(),
        animationSpec = tween(durationMillis = 600),
        label = "calorieRing"
    )

    Box(modifier = modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = ProgressBG, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(width = stroke)
            )
            if (animated > 0f) {
                drawArc(
                    color = ringColor, startAngle = -90f, sweepAngle = 360f * animated, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$consumed", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            Text("of $goal cal", fontSize = 12.sp, color = TextSecondaryColor)
            if (isOver) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = ringColor, modifier = Modifier.size(12.dp))
                    Text("${-remaining} over", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ringColor)
                }
            } else {
                Text("$remaining left", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ringColor)
            }
        }
    }
}
