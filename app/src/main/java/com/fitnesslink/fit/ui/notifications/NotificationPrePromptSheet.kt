package com.fitnesslink.fit.ui.notifications

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.push.PushNotificationManager
import com.fitnesslink.fit.push.rememberPushPermissionRequester
import com.fitnesslink.fit.ui.components.ActionButtonView
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import kotlinx.coroutines.delay

/**
 * Persistence for the pre-prompt cadence. Lives in its own prefs file
 * because the push package is owned by the push agent; the "has the system prompt
 * been used" signal is read from `PushNotificationManager`.
 */
object NotificationPrePromptStore {
    private const val PREFS = "fl_notification_preprompt"
    private const val KEY_OFFER_COUNT = "preprompt.offerCount"
    private const val KEY_LAST_OFFERED_AT = "preprompt.lastOfferedAt"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Android has no "undetermined" state to query; the closest honest
     * approximation is: runtime permission exists (13+), not granted, and
     * the system prompt has never been shown. Below 13 notifications are
     * on by default, so the sheet is never needed.
     */
    fun permissionUndetermined(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PushNotificationManager.isPermissionGranted(context) &&
            !PushNotificationManager.hasPromptedForPush()

    fun shouldOffer(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(context)
        return NotificationPrePromptRule.shouldOffer(
            permissionUndetermined = permissionUndetermined(context),
            offerCount = p.getInt(KEY_OFFER_COUNT, 0),
            lastOfferedAtMs = p.getLong(KEY_LAST_OFFERED_AT, 0L),
            nowMs = nowMs
        )
    }

    /** The sheet is on screen — count it regardless of which button the user picks. */
    fun recordOffered(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val p = prefs(context)
        p.edit()
            .putInt(KEY_OFFER_COUNT, p.getInt(KEY_OFFER_COUNT, 0) + 1)
            .putLong(KEY_LAST_OFFERED_AT, nowMs)
            .apply()
    }
}

/**
 * Port of iOS `NotificationPrePromptSheet`: explains what notifications are
 * for, then hands off to the one-shot system prompt on "Turn on". "Not now"
 * is respected via the cadence rule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPrePromptSheet(onTurnOn: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { NotificationPrePromptStore.recordOffered(context) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BackgroundColor) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(84.dp).background(FLPrimary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text("Get a nudge when it counts", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Turn on notifications and FitnessLink will keep you on track without you having to remember.",
                fontSize = 15.sp, color = TextSecondaryColor, textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                Benefit(Icons.Default.CalendarMonth, "Workout day reminders", "A heads-up on the days you've scheduled training.")
                Benefit(Icons.Default.CheckCircle, "Habit check-ins and streaks", "A quick prompt so a good streak doesn't slip.")
                Benefit(Icons.Default.AutoAwesome, "New content for your goals", "Programs and workouts matched to your onboarding answers.")
            }
            Spacer(modifier = Modifier.height(28.dp))
            ActionButtonView(text = "Turn on notifications", isEnabled = true, isLoading = false, onClick = onTurnOn)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Not now",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondaryColor,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(vertical = 12.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(28.dp))
        Column {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Text(detail, fontSize = 14.sp, color = TextSecondaryColor)
        }
    }
}

/**
 * Drop-in for the tab host (iOS MainTabView): [delayMs] after the tabs
 * appear, offers the pre-prompt sheet when the cadence rule allows and no
 * cold-start deep link is pending. Replaces the raw system prompt the host
 * currently fires from `MainTabNavigation`.
 */
@Composable
fun NotificationPrePromptHost(delayMs: Long = 2_000L) {
    val context = LocalContext.current
    val requestPermission = rememberPushPermissionRequester()
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delayMs)
        if (AppRouter.pendingRoute.value == null && NotificationPrePromptStore.shouldOffer(context)) show = true
    }

    if (show) {
        NotificationPrePromptSheet(
            onTurnOn = {
                show = false
                requestPermission()
            },
            onDismiss = { show = false }
        )
    }
}
