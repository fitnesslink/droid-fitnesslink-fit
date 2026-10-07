package com.fitnesslink.fit.ui.goals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.GoalType
import com.fitnesslink.fit.model.HabitTier
import com.fitnesslink.fit.model.HabitType
import com.fitnesslink.fit.model.TrajectoryStatus
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

// Goals-domain building blocks shared by the list cards, the detail header,
// habit rows and the habit detail page. Mirrors the iOS enum extensions
// (`TrajectoryStatus.displayColor` / `sfSymbolName`, `HabitTier.displayColor`,
// `GoalType.sfSymbolName`, `HabitType.sfSymbolName`) plus `GoalProgressHeader`,
// `GoalDetailCard`'s ring and `StatCard`.

/** iOS `TrajectoryStatus.displayColor`. */
fun trajectoryColor(status: TrajectoryStatus): Color = when (status) {
    TrajectoryStatus.Ahead -> BlueTheme
    TrajectoryStatus.OnTrack -> FLPrimary
    TrajectoryStatus.Behind -> OrangeTheme
    TrajectoryStatus.AtRisk -> PurpleTheme
}

/** iOS `TrajectoryStatus.sfSymbolName`. */
fun trajectoryIcon(status: TrajectoryStatus): ImageVector = when (status) {
    TrajectoryStatus.Ahead -> Icons.Filled.TrendingUp
    TrajectoryStatus.OnTrack -> Icons.Filled.TrendingFlat
    TrajectoryStatus.Behind -> Icons.Filled.TrendingDown
    TrajectoryStatus.AtRisk -> Icons.Filled.Warning
}

/** iOS `GoalType.sfSymbolName`. */
fun goalTypeIcon(type: GoalType): ImageVector = when (type) {
    GoalType.BodyComposition -> Icons.Filled.MonitorWeight
    GoalType.Performance -> Icons.Filled.FitnessCenter
    GoalType.Consistency -> Icons.Filled.EventAvailable
    GoalType.Nutrition -> Icons.Filled.Restaurant
    GoalType.RecoveryWellness -> Icons.Filled.Bedtime
    GoalType.Custom -> Icons.Filled.Star
}

/** iOS `HabitType.sfSymbolName`. */
fun habitTypeIcon(type: HabitType): ImageVector = when (type) {
    HabitType.ActionBased -> Icons.Filled.DirectionsRun
    HabitType.TimeBased -> Icons.Filled.Timer
    HabitType.PassiveSensor -> Icons.Filled.Watch
    HabitType.Nutrition -> Icons.Filled.Restaurant
    HabitType.Avoidance -> Icons.Filled.PanTool
    HabitType.Reflection -> Icons.Filled.Edit
}

/** iOS `HabitTier.sfSymbolName` (leaf → leaf.fill → tree → tree.fill). */
fun habitTierIcon(tier: HabitTier): ImageVector = when (tier) {
    HabitTier.Seedling -> Icons.Filled.Spa
    HabitTier.Sprout -> Icons.Filled.Grass
    HabitTier.Growing -> Icons.Filled.Park
    HabitTier.Rooted -> Icons.Filled.Nature
}

/** iOS `HabitTier.displayColor`: Seedling / Sprout brand green, Growing / Rooted blue. */
fun habitTierColor(tier: HabitTier): Color = when (tier) {
    HabitTier.Seedling, HabitTier.Sprout -> FLPrimary
    HabitTier.Growing, HabitTier.Rooted -> BlueTheme
}

/**
 * Circular progress ring (iOS `GoalDetailCard` / `GoalProgressHeader`):
 * grey track, coloured arc from 12 o'clock, [content] centred.
 */
@Composable
fun GoalProgressRing(
    percent: Double,
    color: Color,
    size: Dp,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = Color.Gray.copy(alpha = 0.15f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke)
            )
            val sweep = (percent.coerceIn(0.0, 100.0) / 100.0 * 360.0).toFloat()
            if (sweep > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f, sweepAngle = sweep, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

/** iOS trajectory badge: icon + name tinted by status; [filled] adds the tinted pill background (detail header). */
@Composable
fun TrajectoryBadge(status: TrajectoryStatus, filled: Boolean = false, modifier: Modifier = Modifier) {
    val color = trajectoryColor(status)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (filled) 6.dp else 4.dp),
        modifier = if (filled) modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 6.dp) else modifier
    ) {
        Icon(trajectoryIcon(status), contentDescription = null, tint = color, modifier = Modifier.size(if (filled) 14.dp else 11.dp))
        Text(status.displayName, fontSize = if (filled) 14.sp else 11.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

/** iOS tier pill (`HabitRowView` / `HabitDetailView`): tier icon + name in the tier colour. */
@Composable
fun HabitTierPill(tier: HabitTier, filled: Boolean = false, modifier: Modifier = Modifier) {
    val color = habitTierColor(tier)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = if (filled) modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp) else modifier
    ) {
        Icon(habitTierIcon(tier), contentDescription = null, tint = color, modifier = Modifier.size(if (filled) 13.dp else 11.dp))
        Text(tier.name, fontSize = if (filled) 13.sp else 11.sp, fontWeight = if (filled) FontWeight.Medium else FontWeight.Normal, color = color)
    }
}

/** iOS `StatCard`: big rounded value over a small label on a white card. */
@Composable
fun GoalStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(10.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
        Text(label, fontSize = 12.sp, color = TextSecondaryColor)
    }
}
