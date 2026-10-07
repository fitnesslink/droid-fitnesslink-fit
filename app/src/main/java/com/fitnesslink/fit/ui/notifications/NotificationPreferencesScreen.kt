package com.fitnesslink.fit.ui.notifications

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.NotificationType
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.NotificationPreferencesViewModel

/**
 * Port of iOS `NotificationPreferencesView` (FTNSSLNK-233): four category
 * toggles plus a link to the goal reminder settings (`notificationSettings`).
 */
@Composable
fun NotificationPreferencesScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val viewModel: NotificationPreferencesViewModel = viewModel()
    LaunchedEffect(Unit) { viewModel.loadData() }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Notification Preferences", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, modifier = Modifier.padding(top = 8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Categories", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White)) {
                    NotificationType.entries.forEachIndexed { index, type ->
                        CategoryRow(
                            type = type,
                            checked = viewModel.isEnabled(type),
                            onChange = { viewModel.setEnabled(type, it) }
                        )
                        if (index != NotificationType.entries.lastIndex) {
                            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(TextSecondaryColor.copy(alpha = 0.1f)))
                        }
                    }
                }
                viewModel.saveErrorMessage?.let {
                    Text(it, fontSize = 12.sp, color = Color.Red, modifier = Modifier.padding(horizontal = 4.dp))
                }
                Text(
                    "Turning a category off stops those notifications entirely — in the inbox and as push alerts.",
                    fontSize = 11.sp, color = TextSecondaryColor, modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Goal Reminders", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(White)
                        .clickable { onNavigate("notificationSettings") }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reminder Settings", fontSize = 15.sp, color = TextPrimaryColor)
                        Text("Coaching tone, habit reminders, and daily limits", fontSize = 11.sp, color = TextSecondaryColor)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CategoryRow(type: NotificationType, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(type.color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(categoryIcon(type), contentDescription = null, tint = type.color, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(type.label, fontSize = 15.sp, color = TextPrimaryColor)
            Text(categoryDescription(type), fontSize = 11.sp, color = TextSecondaryColor)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = FLPrimary, checkedThumbColor = White)
        )
    }
}

private fun categoryIcon(type: NotificationType): ImageVector = when (type) {
    NotificationType.SYSTEM -> Icons.Default.Campaign
    NotificationType.GOALS -> Icons.Default.TrackChanges
    NotificationType.CONTENT -> Icons.Default.MenuBook
    NotificationType.CALENDAR -> Icons.Default.CalendarMonth
}

private fun categoryDescription(type: NotificationType): String = when (type) {
    NotificationType.SYSTEM -> "Announcements and account updates"
    NotificationType.GOALS -> "Habit reminders, streaks, and milestones"
    NotificationType.CONTENT -> "New programs, workouts, and shared content"
    NotificationType.CALENDAR -> "Scheduled workout reminders"
}
