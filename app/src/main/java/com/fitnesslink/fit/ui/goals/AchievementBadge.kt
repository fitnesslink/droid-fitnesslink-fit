package com.fitnesslink.fit.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.WorkspacePremium
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
import com.fitnesslink.fit.model.AchievementType
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor

@Composable
fun AchievementBadge(
    type: AchievementType,
    isEarned: Boolean,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    color = if (isEarned) FLPrimary.copy(alpha = 0.12f) else MediumGray.copy(alpha = 0.20f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = type.iconVector,
                contentDescription = type.title,
                tint = if (isEarned) FLPrimary else MediumGray,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = type.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isEarned) TextPrimaryColor else TextSecondaryColor,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

/** Material icon analog for each iOS SF Symbol used by AchievementType. */
val AchievementType.iconVector: ImageVector
    get() = when (this) {
        AchievementType.FirstStep -> Icons.Filled.Star
        AchievementType.WeekOne -> Icons.Filled.LocalFireDepartment
        AchievementType.HabitStacker -> Icons.Filled.Layers
        AchievementType.TierUp -> Icons.Filled.Upgrade
        AchievementType.CenturyClub -> Icons.Filled.MilitaryTech
        AchievementType.FullCircle -> Icons.Filled.CheckCircle
        AchievementType.DataDriven -> Icons.Filled.BarChart
        AchievementType.ConsistencyRoyalty -> Icons.Filled.WorkspacePremium
    }
