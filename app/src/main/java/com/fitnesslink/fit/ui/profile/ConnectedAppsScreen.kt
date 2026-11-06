package com.fitnesslink.fit.ui.profile

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.progress.ProgressTitleBar
import com.fitnesslink.fit.health.HealthConnectService
import com.fitnesslink.fit.health.HealthConnectService.Availability
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme
import kotlinx.coroutines.launch

/**
 * Connected Apps & Devices (iOS `ConnectedAppsView`, Navigation PRD §6.3) —
 * the permanent home for health-data integrations, reached from the Profile
 * menu and from every Progress / goal connection prompt.
 *
 * Health Connect is Android's Apple Health: Wear OS, Garmin, Oura, WHOOP and
 * Fitbit all write into it, so connecting it covers them without vendor
 * SDKs. Continue asks for every read type FitnessLink uses; Disconnect stops
 * reading and revokes those permissions ([HealthConnectService]).
 */
@Composable
fun ConnectedAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connected by HealthConnectService.connected.collectAsState()
    var availability by remember { mutableStateOf(HealthConnectService.availability(context)) }
    var isWorking by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var declined by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(HealthConnectService.permissionContract()) { granted ->
        scope.launch {
            declined = !HealthConnectService.onPermissionResult(granted)
            isWorking = false
        }
    }
    // Re-check after the user comes back from installing Health Connect or changing access in its settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                availability = HealthConnectService.availability(context)
                scope.launch { HealthConnectService.refresh() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize().background(FLTheme.colors.background)) {
        ProgressTitleBar("Connected Apps & Devices", onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GroupHeader("Health Platforms")
            Group {
                if (availability == Availability.UNAVAILABLE) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.HeartBroken, contentDescription = null, tint = FLTheme.colors.textSecondary)
                        Text("Health Connect isn't available on this device.", fontSize = 15.sp, color = FLTheme.colors.textSecondary)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(FLTheme.colors.danger),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Favorite, contentDescription = null, tint = FLTheme.colors.onBrand, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Health Connect", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary)
                            Text(
                                when {
                                    availability == Availability.NEEDS_INSTALL_OR_UPDATE -> "Install or update Health Connect"
                                    connected -> "Connected"
                                    else -> "Not connected"
                                },
                                fontSize = 13.sp,
                                color = if (connected) FLPrimary else FLTheme.colors.textSecondary
                            )
                        }
                        when {
                            isWorking -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = FLPrimary)
                            availability == Availability.NEEDS_INSTALL_OR_UPDATE -> Button(
                                onClick = { runCatching { context.startActivity(HealthConnectService.installIntent()) } },
                                colors = ButtonDefaults.buttonColors(containerColor = FLPrimary, contentColor = FLTheme.colors.onBrand)
                            ) { Text("Get App") }
                            connected -> OutlinedButton(onClick = { confirmDisconnect = true }) {
                                Text("Disconnect", color = FLTheme.colors.danger)
                            }
                            else -> Button(
                                onClick = {
                                    isWorking = true
                                    declined = false
                                    try {
                                        permissionLauncher.launch(HealthConnectService.readPermissions)
                                    } catch (_: ActivityNotFoundException) {
                                        isWorking = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = FLPrimary, contentColor = FLTheme.colors.onBrand)
                            ) { Text("Continue") }
                        }
                    }
                    if (availability == Availability.AVAILABLE) {
                        SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                        TextButton(
                            onClick = { runCatching { context.startActivity(HealthConnectService.settingsIntent()) } },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) { Text("Manage access in Health Connect", color = FLPrimary, fontSize = 14.sp) }
                    }
                }
            }
            if (declined) {
                GroupFooter("No access was granted, so FitnessLink can't read Health Connect. Tap Continue to choose again, or allow access in Health Connect's settings.")
            }
            GroupFooter(
                "Health Connect is optional. FitnessLink reads steps, active calories, exercise minutes, sleep, resting heart rate, HRV and body fat " +
                    "from Health Connect to power your Progress stats and goals. It never writes to Health Connect. " +
                    "Continue opens the Health Connect permission screen, where you choose what to share. To change this later, tap Manage access."
            )

            GroupHeader("Wearables")
            Group {
                val wearables: List<Pair<String, ImageVector>> = listOf(
                    "Wear OS / Pixel Watch" to Icons.Filled.Watch,
                    "Garmin" to Icons.Filled.Watch,
                    "Oura" to Icons.Filled.RadioButtonChecked,
                    "WHOOP" to Icons.Filled.MonitorHeart,
                    "Fitbit" to Icons.AutoMirrored.Filled.DirectionsWalk
                )
                wearables.forEachIndexed { index, (name, icon) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(22.dp))
                        Text(name, fontSize = 15.sp, color = FLTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                        Text(
                            if (connected) "Via Health Connect" else "Needs Health Connect",
                            fontSize = 13.sp,
                            color = FLTheme.colors.textSecondary
                        )
                    }
                    if (index != wearables.lastIndex) SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            GroupFooter(
                "These devices sync to FitnessLink through Health Connect. If you'd like to use one, turn on Health Connect sharing in the device's own app " +
                    "and set up Health Connect above. " +
                    "Not every device records every metric — HRV and sleep stages, for example, need a compatible wearable."
            )
        }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect Health Connect?") },
            text = { Text("FitnessLink will stop reading Health Connect and give up its access. Stats and goals that need it will show as unavailable.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    isWorking = true
                    scope.launch {
                        HealthConnectService.disconnect()
                        isWorking = false
                    }
                }) { Text("Disconnect", color = FLTheme.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = FLTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 32.dp, end = 20.dp, top = 12.dp)
    )
}

@Composable
private fun GroupFooter(text: String) {
    Text(text, fontSize = 12.sp, color = FLTheme.colors.textSecondary, modifier = Modifier.padding(horizontal = 32.dp))
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(FLTheme.colors.surface)
    ) { content() }
}
