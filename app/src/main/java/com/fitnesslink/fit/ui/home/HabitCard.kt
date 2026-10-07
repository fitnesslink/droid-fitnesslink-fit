package com.fitnesslink.fit.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.HabitTodayItem
import com.fitnesslink.fit.model.HabitType
import com.fitnesslink.fit.ui.goals.StreakBadge
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

/** Glyph for a habit type (iOS `HabitType.sfSymbolName`). */
fun habitTypeIcon(type: HabitType): ImageVector = when (type) {
    HabitType.ActionBased -> Icons.Filled.Bolt
    HabitType.TimeBased -> Icons.Filled.Timer
    HabitType.PassiveSensor -> Icons.Filled.Sensors
    HabitType.Nutrition -> Icons.Filled.Restaurant
    HabitType.Avoidance -> Icons.Filled.Block
    HabitType.Reflection -> Icons.Filled.EditNote
}

/**
 * One row of Today's Habits (iOS `HabitCard`): a complete button with
 * haptics and a spring check, the title (struck through when done), the
 * anchor behaviour, a passive-sensor progress bar, and the streak badge.
 * Tapping the check on a completed habit calls [onUncomplete] when wired.
 */
@Composable
fun HabitCard(
    habit: HabitTodayItem,
    onComplete: () -> Unit,
    onTap: () -> Unit = {},
    onUncomplete: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val done = habit.isCompletedToday
    val checkScale by animateFloatAsState(
        targetValue = if (done) 1f else 0.5f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "habitCheck"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (done) FLPrimary.copy(alpha = 0.06f) else White)
            .border(1.dp, if (done) FLPrimary.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
            .padding(14.dp)
    ) {
        val description = if (done) "Mark ${habit.title} not done" else "Mark ${habit.title} done"
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .border(2.5.dp, if (done) FLPrimary else MediumGray.copy(alpha = 0.5f), CircleShape)
                .clip(CircleShape)
                .clickable {
                    if (done) {
                        val undo = onUncomplete ?: return@clickable
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        undo()
                    } else {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onComplete()
                    }
                }
                .semantics { contentDescription = description }
        ) {
            if (done) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = FLPrimary,
                    modifier = Modifier
                        .size(18.dp)
                        .scale(checkScale)
                )
            } else {
                Icon(
                    imageVector = habitTypeIcon(habit.habitType),
                    contentDescription = null,
                    tint = MediumGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = habit.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (done) TextSecondaryColor else TextPrimaryColor,
                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
            )
            habit.anchorBehavior?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, fontSize = 12.sp, color = TextSecondaryColor)
            }
            val current = habit.currentSensorValue
            val target = habit.targetValue
            if (habit.habitType == HabitType.PassiveSensor && current != null && target != null && target > 0) {
                LinearProgressIndicator(
                    progress = { (current / target).coerceIn(0.0, 1.0).toFloat() },
                    color = if (current >= target) FLPrimary else BlueTheme,
                    trackColor = MediumGray.copy(alpha = 0.2f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
                Text(
                    text = "${current.toInt()} / ${target.toInt()} ${habit.targetUnit.orEmpty()}".trim(),
                    fontSize = 11.sp,
                    color = TextSecondaryColor
                )
            }
        }

        if (habit.streak.currentCount > 0) {
            StreakBadge(days = habit.streak.currentCount, tier = habit.tier)
        }
    }
}
