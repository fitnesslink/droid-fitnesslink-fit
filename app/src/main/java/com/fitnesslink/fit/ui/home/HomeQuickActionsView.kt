package com.fitnesslink.fit.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.White

/**
 * One-tap shortcuts to the app's most common next steps (iOS
 * `HomeQuickActionsView`, FTNSSLNK-364): New Workout, Programs, Log Food,
 * Set Goal. "Set Goal" opens the goal creation flow.
 */
@Composable
fun HomeQuickActionsView(onNavigate: (String) -> Unit, onSetGoal: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        QuickAction(icon = Icons.Filled.Edit, title = "New Workout", modifier = Modifier.weight(1f)) { onNavigate("workoutEditor") }
        QuickAction(icon = Icons.Filled.DateRange, title = "Programs", modifier = Modifier.weight(1f)) { onNavigate("programs") }
        QuickAction(icon = Icons.Filled.Restaurant, title = "Log Food", modifier = Modifier.weight(1f)) { onNavigate("calorieTracking") }
        QuickAction(icon = Icons.Filled.TrackChanges, title = "Set Goal", modifier = Modifier.weight(1f), onClick = onSetGoal)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(22.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
