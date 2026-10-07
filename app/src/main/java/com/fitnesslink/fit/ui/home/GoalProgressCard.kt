package com.fitnesslink.fit.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.GoalSummary
import com.fitnesslink.fit.model.TrajectoryStatus
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

/** iOS `TrajectoryStatus.displayColor`. */
fun trajectoryColor(status: TrajectoryStatus): Color = when (status) {
    TrajectoryStatus.Ahead -> Color(0xFF22C55E)
    TrajectoryStatus.OnTrack -> FLPrimary
    TrajectoryStatus.Behind -> OrangeTheme
    TrajectoryStatus.AtRisk -> Color(0xFFEF4444)
}

/** iOS `TrajectoryStatus.sfSymbolName`. */
fun trajectoryIcon(status: TrajectoryStatus): ImageVector = when (status) {
    TrajectoryStatus.Ahead -> Icons.AutoMirrored.Filled.TrendingUp
    TrajectoryStatus.OnTrack -> Icons.AutoMirrored.Filled.TrendingFlat
    TrajectoryStatus.Behind -> Icons.AutoMirrored.Filled.TrendingDown
    TrajectoryStatus.AtRisk -> Icons.Filled.Warning
}

/** Goal card for the Home rail (iOS `GoalProgressCard`): progress ring, title, trajectory, relative target date. */
@Composable
fun GoalProgressCard(goal: GoalSummary, onClick: () -> Unit = {}) {
    val accent = trajectoryColor(goal.trajectoryStatus)
    val percent = goal.progressPercent.coerceIn(0.0, 100.0)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(White)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
            Canvas(modifier = Modifier.size(56.dp)) {
                val stroke = 5.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = MediumGray.copy(alpha = 0.25f), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(width = stroke)
                )
                drawArc(
                    color = accent, startAngle = -90f, sweepAngle = (percent / 100.0 * 360.0).toFloat(), useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Text(text = "${percent.toInt()}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
        }
        Text(
            text = goal.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimaryColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(imageVector = trajectoryIcon(goal.trajectoryStatus), contentDescription = null, tint = accent, modifier = Modifier.size(12.dp))
            Text(text = goal.trajectoryStatus.displayName, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = accent)
        }
        goal.targetDate?.let { target ->
            Text(
                text = DateUtils.getRelativeTimeSpanString(target, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString(),
                fontSize = 11.sp,
                color = TextSecondaryColor
            )
        }
    }
}
