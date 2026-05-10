package com.fitnesslink.fit.ui.profile

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.CoachingTone
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.NotificationSettingsViewModel

/**
 * FA-100 — per-category notification settings. Groups the existing
 * GoalNotificationPreference toggles into the four categories the
 * AC calls out: System / Goals / Content / Calendar. Coaching tone
 * lives at the top so the per-category toggles read consistently.
 */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val viewModel: NotificationSettingsViewModel = viewModel()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.loadData() }

    val prefs = viewModel.preferences

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Notifications",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor,
                modifier = Modifier.padding(top = 8.dp)
            )

            // System category — links to OS-level settings since the
            // app-level master switch lives there, plus reengagement
            // (mostly system-paced re-activation pushes).
            Section(title = "System") {
                ToggleRow(
                    label = "Re-engagement nudges",
                    sub = "Reminders if you've drifted off your routine.",
                    checked = prefs.enableReengagement,
                    onToggle = viewModel::toggleReengagement
                )
                Divider()
                LinkRow(
                    label = "Open Android settings",
                    sub = "Block all FitnessLink notifications at the OS level.",
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }
                )
            }

            // Goals — habit reminders, streaks, milestones, check-ins,
            // plus AI coaching (also goal-driven).
            Section(title = "Goals") {
                ToggleRow(
                    label = "Habit reminders",
                    sub = "Daily prompts for habits you're tracking.",
                    checked = prefs.enableHabitReminders,
                    onToggle = viewModel::toggleHabitReminders
                )
                Divider()
                ToggleRow(
                    label = "Streak alerts",
                    sub = "Heads-up when a streak is at risk.",
                    checked = prefs.enableStreakAlerts,
                    onToggle = viewModel::toggleStreakAlerts
                )
                Divider()
                ToggleRow(
                    label = "Milestones",
                    sub = "Notifications when a milestone is reached.",
                    checked = prefs.enableMilestones,
                    onToggle = viewModel::toggleMilestones
                )
                Divider()
                ToggleRow(
                    label = "Goal check-ins",
                    sub = "Periodic prompts to review your progress.",
                    checked = prefs.enableGoalCheckIns,
                    onToggle = viewModel::toggleGoalCheckIns
                )
                Divider()
                ToggleRow(
                    label = "AI coaching",
                    sub = "Personalized suggestions based on your data.",
                    checked = prefs.enableAiCoaching,
                    onToggle = viewModel::toggleAiCoaching
                )
                Divider()
                CoachingToneRow(
                    selected = prefs.coachingTone,
                    onSelect = viewModel::updateCoachingTone
                )
            }

            // Content — newly published programs / workouts / tips.
            // No backend toggle today; surfaces as info card so the AC
            // category isn't silently missing. Wires up cleanly when
            // the backend adds an enableContent flag.
            Section(title = "Content") {
                InfoRow(
                    label = "New programs &amp; tips",
                    sub = "Coming soon — managed at the app level for now."
                )
            }

            // Calendar — schedule reminders.
            Section(title = "Calendar") {
                InfoRow(
                    label = "Workout reminders",
                    sub = "Tied to scheduled workouts. Coming soon as a separate toggle."
                )
            }

            // Daily cap.
            Section(title = "Daily cap") {
                MaxDailyRow(
                    selected = prefs.maxDailyNotifications,
                    onSelect = viewModel::updateMaxDailyNotifications
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondaryColor
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(White)
        ) {
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    sub: String?,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
            if (sub != null) {
                Text(sub, fontSize = 11.sp, color = TextSecondaryColor)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = FLPrimary,
                checkedThumbColor = White
            )
        )
    }
}

@Composable
private fun LinkRow(label: String, sub: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
            Text(sub, fontSize = 11.sp, color = TextSecondaryColor)
        }
        Text(text = "›", fontSize = 18.sp, color = MediumGray)
    }
}

@Composable
private fun InfoRow(label: String, sub: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
            Text(sub, fontSize = 11.sp, color = TextSecondaryColor)
        }
    }
}

@Composable
private fun CoachingToneRow(
    selected: CoachingTone,
    onSelect: (CoachingTone) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Coaching tone", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(CoachingTone.entries.toList()) { tone ->
                val isSelected = tone == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) FLPrimary else White)
                        .border(
                            1.dp,
                            if (isSelected) FLPrimary else TextSecondaryColor.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelect(tone) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tone.displayName,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) White else TextSecondaryColor
                    )
                }
            }
        }
    }
}

@Composable
private fun MaxDailyRow(selected: Int, onSelect: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Max per day", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
        Text(
            text = "Cap how many push notifications we send each day.",
            fontSize = 11.sp,
            color = TextSecondaryColor
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(3, 5, 8, 12).forEach { n ->
                val isSelected = n == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) FLPrimary else White)
                        .border(
                            1.dp,
                            if (isSelected) FLPrimary else TextSecondaryColor.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelect(n) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$n",
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) White else TextSecondaryColor
                    )
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 16.dp)
            .background(TextSecondaryColor.copy(alpha = 0.1f))
    )
}

