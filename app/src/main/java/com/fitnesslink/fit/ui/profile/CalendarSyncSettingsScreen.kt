package com.fitnesslink.fit.ui.profile

import com.fitnesslink.fit.ui.theme.FLTheme
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.calendar.CalendarSyncService
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.FLPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CalendarSyncSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(CalendarSyncService.isEnabled(context)) }
    var hasPermission by remember { mutableStateOf(CalendarSyncService.hasPermission(context)) }
    var lastSyncCount by remember { mutableIntStateOf(-1) }
    var syncing by remember { mutableStateOf(false) }

    // Multiple-permissions launcher so READ + WRITE land together. Result
    // refreshes our cached permission boolean and turns the toggle on if
    // the user granted access while it was off.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.WRITE_CALENDAR] == true &&
            results[Manifest.permission.READ_CALENDAR] == true
        hasPermission = granted
        if (granted && !enabled) {
            CalendarSyncService.setEnabled(context, true)
            enabled = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FLTheme.colors.background)
    ) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Calendar sync",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = FLTheme.colors.textPrimary,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "Mirror scheduled workouts into your device calendar so they show up alongside your other commitments.",
                fontSize = 13.sp,
                color = FLTheme.colors.textSecondary
            )

            // Master toggle.
            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sync to calendar", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FLTheme.colors.textPrimary)
                        Text(
                            text = if (hasPermission) "Granted" else "Permission needed",
                            fontSize = 11.sp,
                            color = if (hasPermission) FLPrimary else FLTheme.colors.textSecondary
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { newValue ->
                            if (newValue && !hasPermission) {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            } else {
                                CalendarSyncService.setEnabled(context, newValue)
                                enabled = newValue
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = FLPrimary,
                            checkedThumbColor = FLTheme.colors.onBrand
                        )
                    )
                }
            }

            // Permission denied: deep-link into the app's settings screen
            // since the OS won't show the system prompt again after a
            // permanent denial.
            if (!hasPermission) {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Calendar permission needed",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FLTheme.colors.textPrimary
                        )
                        Text(
                            text = "If the system prompt isn't appearing, grant access from app settings.",
                            fontSize = 12.sp,
                            color = FLTheme.colors.textSecondary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(FLPrimary)
                                .clickable {
                                    val uri = Uri.fromParts("package", context.packageName, null)
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                                    )
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Open app settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.onBrand)
                        }
                    }
                }
            }

            // Manual "Sync now" — useful while the WorkManager-driven loop
            // catches up. Disabled when prereqs aren't met.
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sync now",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FLTheme.colors.textPrimary
                    )
                    Text(
                        text = "Push every scheduled workout to your calendar right now.",
                        fontSize = 12.sp,
                        color = FLTheme.colors.textSecondary
                    )
                    val canSync = enabled && hasPermission && !syncing
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (canSync) FLPrimary else FLTheme.colors.textTertiary)
                            .clickable(enabled = canSync) {
                                syncing = true
                                scope.launch {
                                    val n = withContext(Dispatchers.IO) {
                                        CalendarSyncService.syncAll(context)
                                    }
                                    lastSyncCount = n
                                    syncing = false
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (syncing) "Syncing…" else "Sync now",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FLTheme.colors.onBrand
                        )
                    }
                    if (lastSyncCount >= 0) {
                        Text(
                            text = "Last run: $lastSyncCount event${if (lastSyncCount == 1) "" else "s"} mirrored.",
                            fontSize = 11.sp,
                            color = FLTheme.colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Scheduled background sync runs alongside the data sync; manual sync forces it now.",
                fontSize = 11.sp,
                color = FLTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FLTheme.colors.surface)
    ) { content() }
}
