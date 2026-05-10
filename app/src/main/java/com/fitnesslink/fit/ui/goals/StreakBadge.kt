package com.fitnesslink.fit.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.HabitTier

/**
 * Reusable streak indicator (FA-95). Pairs a flame with a day count and
 * scales accent + emphasis with the supplied HabitTier — Seedling reads
 * understated, Rooted reads loud. Designed to slot into habit cards,
 * goal cards, and habit-detail headers.
 */
@Composable
fun StreakBadge(
    days: Int,
    tier: HabitTier = tierForStreak(days),
    modifier: Modifier = Modifier
) {
    val accent = tierColor(tier)
    val descriptor = when (days) {
        0 -> "No active streak"
        1 -> "1 day streak"
        else -> "$days day streak"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = descriptor }
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "$days",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
    }
}

/** Default tier inference when caller doesn't track HabitTier explicitly. */
fun tierForStreak(days: Int): HabitTier = when {
    days >= 90 -> HabitTier.Rooted
    days >= 30 -> HabitTier.Growing
    days >= 7 -> HabitTier.Sprout
    else -> HabitTier.Seedling
}

private fun tierColor(tier: HabitTier): Color = when (tier) {
    HabitTier.Seedling -> Color(0xFF94A3B8)   // muted slate
    HabitTier.Sprout -> Color(0xFF22C55E)     // fresh green
    HabitTier.Growing -> Color(0xFFF59E0B)    // amber
    HabitTier.Rooted -> Color(0xFFEF4444)     // hot red — earned
}
