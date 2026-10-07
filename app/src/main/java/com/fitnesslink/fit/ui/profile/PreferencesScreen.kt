package com.fitnesslink.fit.ui.profile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.calendar.CalendarSyncService
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.session.AudioCueService
import com.fitnesslink.fit.session.CoachVoicePrefs
import com.fitnesslink.fit.session.SessionSettings
import com.fitnesslink.fit.settings.Appearance
import com.fitnesslink.fit.settings.UserSettings
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.navigation.ProfileRouteNames
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Port of iOS `PreferencesView` (parity 4.4): Appearance, a link to
 * Notification Preferences, Workout Sessions (exercise video audio, voice
 * cues + coach voice + preview, countdown beeps, haptics, weight / distance
 * units), Health Connect calories (stored only), and device calendar sync
 * with a target-calendar picker. The iOS "Sync to Reminders" grocery section
 * has no Android equivalent and is omitted.
 */
@Composable
fun PreferencesScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        UserSettings.attach(context)
        SessionSettings.attach(context)
        AudioCueService.attach(context)
    }
    val settingsVersion by UserSettings.version.collectAsState()
    val sessionVersion by SessionSettings.version.collectAsState()
    val appearance by UserSettings.appearanceFlow.collectAsState()

    // Session cue settings (SessionSettings owns the storage).
    val videoAudio = remember(sessionVersion) { SessionSettings.sessionVideoAudioEnabled }
    val voiceCues = remember(sessionVersion) { SessionSettings.intervalVoiceCuesEnabled }
    val countdownBeeps = remember(sessionVersion) { SessionSettings.intervalCuesEnabled }
    val haptics = remember(sessionVersion) { SessionSettings.intervalHapticsEnabled }
    val coachVoiceId = remember(sessionVersion) { SessionSettings.coachVoiceIdentifier }
    val weightUnit = remember(settingsVersion) { UserSettings.weightUnit }
    val distanceUnit = remember(settingsVersion) { UserSettings.distanceUnit }
    val healthConnect = remember(settingsVersion) { UserSettings.useHealthConnectCalories }

    // TTS initialises asynchronously; re-read the voice list once it is up.
    var coachVoices by remember { mutableStateOf<List<CoachVoicePrefs.Option>>(emptyList()) }
    LaunchedEffect(Unit) {
        repeat(5) {
            coachVoices = AudioCueService.availableVoices()
            if (coachVoices.isNotEmpty()) return@LaunchedEffect
            delay(800)
        }
    }
    // Automatic pick title: the best voice is first in the sorted options.
    val automaticVoice = coachVoices.firstOrNull()

    // Calendar sync
    var calendarEnabled by remember { mutableStateOf(CalendarSyncService.isEnabled(context)) }
    var hasCalendarPermission by remember { mutableStateOf(CalendarSyncService.hasPermission(context)) }
    var permissionDenied by remember { mutableStateOf(false) }
    var calendars by remember { mutableStateOf(CalendarSyncService.writableCalendars(context)) }
    var targetCalendarId by remember { mutableStateOf(CalendarSyncService.targetCalendarId(context)) }
    // Access revoked between launches: keep the UI honest with reality.
    LaunchedEffect(Unit) {
        if (calendarEnabled && !hasCalendarPermission) {
            CalendarSyncService.setEnabled(context, false)
            calendarEnabled = false
        }
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.WRITE_CALENDAR] == true && results[Manifest.permission.READ_CALENDAR] == true
        hasCalendarPermission = granted
        permissionDenied = !granted
        if (granted) {
            CalendarSyncService.setEnabled(context, true)
            calendarEnabled = true
            calendars = CalendarSyncService.writableCalendars(context)
            scope.launch(Dispatchers.IO) { CalendarSyncService.backfillUnsynced(context) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Preferences", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, modifier = Modifier.padding(top = 8.dp))

            PrefSection(
                title = "Appearance",
                footer = "Choose how FitnessLink looks. System follows your device's Light/Dark setting."
            ) {
                Appearance.entries.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { UserSettings.appearance = option }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = appearance == option,
                            onClick = { UserSettings.appearance = option },
                            colors = RadioButtonDefaults.colors(selectedColor = FLPrimary)
                        )
                        Text(option.label, fontSize = 15.sp, color = TextPrimaryColor)
                    }
                    if (index != Appearance.entries.lastIndex) PrefDivider()
                }
            }

            PrefSection(footer = "Choose which notification categories you receive.") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(ProfileRouteNames.NOTIFICATION_PREFERENCES) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Notifications", fontSize = 15.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(18.dp))
                }
            }

            PrefSection(
                title = "Workout Sessions",
                footer = "Play sound from exercise demonstration videos during workouts. Voice Cues speak \"Get ready\", \"Begin\", \"Rest\" and \"Final round\" during interval blocks; Countdown Beeps tick through the last three seconds of each phase. Turn them off if you train with your own music — the speaker button during a workout mutes everything at once. Coach Voice picks the most natural English voice on your device; for a less robotic coach, install a higher-quality voice under Settings > Accessibility > Text-to-speech, then choose it here or leave Automatic. Weight Unit applies to every weight you see and enter — switching never changes your saved data. Distance Unit is how you log runs, rows and rides."
            ) {
                PrefToggle("Exercise Video Audio", videoAudio) { SessionSettings.sessionVideoAudioEnabled = it }
                PrefDivider()
                PrefToggle("Voice Cues", voiceCues) { SessionSettings.intervalVoiceCuesEnabled = it }
                if (voiceCues) {
                    PrefDivider()
                    CoachVoiceRow(
                        selectedId = coachVoiceId,
                        options = coachVoices,
                        automatic = automaticVoice,
                        onSelect = { id ->
                            SessionSettings.coachVoiceIdentifier = id
                            AudioCueService.previewVoice()
                        }
                    )
                    PrefDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { AudioCueService.previewVoice() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Preview Voice", fontSize = 15.sp, color = FLPrimary)
                    }
                }
                PrefDivider()
                PrefToggle("Countdown Beeps", countdownBeeps) { SessionSettings.intervalCuesEnabled = it }
                PrefDivider()
                PrefToggle("Haptics", haptics) { SessionSettings.intervalHapticsEnabled = it }
                PrefDivider()
                SegmentedRow(
                    label = "Weight Unit",
                    options = listOf(WeightUnit.LBS to "Pounds (lbs)", WeightUnit.KG to "Kilograms (kg)"),
                    selected = weightUnit,
                    onSelect = { UserSettings.weightUnit = it }
                )
                PrefDivider()
                SegmentedRow(
                    label = "Distance Unit",
                    options = listOf(DistanceUnit.KM to "Kilometres (km)", DistanceUnit.MI to "Miles (mi)"),
                    selected = distanceUnit,
                    onSelect = { UserSettings.distanceUnit = it }
                )
            }

            PrefSection(
                title = "Health Connect",
                footer = if (healthConnect)
                    "Workout reports will show calories recorded by your watch or fitness tracker through Health Connect once that integration ships; until then they keep the estimate."
                else
                    "Show real calories burned from your watch or fitness tracker (via Health Connect) in your workout reports instead of an estimate. Coming soon — your choice is saved."
            ) {
                PrefToggle("Use Health Connect for Calories", healthConnect) { UserSettings.useHealthConnectCalories = it }
            }

            PrefSection(
                title = "Device Calendar",
                footer = when {
                    permissionDenied -> "Calendar access denied. Enable it in Settings to sync."
                    calendarEnabled -> "Workouts you schedule will appear in your device calendar."
                    else -> "Mirror scheduled workouts to your device calendar."
                }
            ) {
                PrefToggle("Sync to Calendar", calendarEnabled) { newValue ->
                    if (newValue) {
                        permissionDenied = false
                        if (hasCalendarPermission) {
                            CalendarSyncService.setEnabled(context, true)
                            calendarEnabled = true
                            calendars = CalendarSyncService.writableCalendars(context)
                            scope.launch(Dispatchers.IO) { CalendarSyncService.backfillUnsynced(context) }
                        } else {
                            calendarPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                        }
                    } else {
                        CalendarSyncService.setEnabled(context, false)
                        calendarEnabled = false
                    }
                }
                if (calendarEnabled && calendars.isNotEmpty()) {
                    PrefDivider()
                    TargetCalendarRow(
                        calendars = calendars,
                        selectedId = targetCalendarId ?: calendars.firstOrNull()?.id,
                        onSelect = { id ->
                            targetCalendarId = id
                            CalendarSyncService.setTargetCalendarId(context, id)
                            // Re-mirror so existing events move to the new calendar (iOS v1 approach).
                            scope.launch { withContext(Dispatchers.IO) { CalendarSyncService.backfillUnsynced(context) } }
                        }
                    )
                }
                if (permissionDenied) {
                    PrefDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("Open Settings", fontSize = 15.sp, color = FLPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PrefSection(title: String? = null, footer: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
        }
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White)) { content() }
        if (footer != null) {
            Text(footer, fontSize = 11.sp, color = TextSecondaryColor, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun PrefToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = FLPrimary, checkedThumbColor = White)
        )
    }
}

@Composable
private fun PrefDivider() {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(TextSecondaryColor.copy(alpha = 0.1f)))
}

@Composable
private fun <T> SegmentedRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 15.sp, color = TextPrimaryColor)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, text) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) FLPrimary else White)
                        .border(1.dp, if (isSelected) FLPrimary else TextSecondaryColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .clickable { onSelect(value) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text,
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
private fun CoachVoiceRow(
    selectedId: String?,
    options: List<CoachVoicePrefs.Option>,
    automatic: CoachVoicePrefs.Option?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle = options.firstOrNull { it.id == selectedId }?.title ?: "Automatic"
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Coach Voice", fontSize = 15.sp, color = TextPrimaryColor)
                if (selectedId == null && automatic != null) {
                    Text(automatic.title, fontSize = 11.sp, color = TextSecondaryColor)
                }
            }
            Text(selectedTitle, fontSize = 14.sp, color = TextSecondaryColor)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MediumGray)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Automatic")
                        automatic?.let { Text(it.title, fontSize = 11.sp, color = TextSecondaryColor) }
                    }
                },
                onClick = { expanded = false; onSelect(null) }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.title)
                            Text(option.languageLabel, fontSize = 11.sp, color = TextSecondaryColor)
                        }
                    },
                    onClick = { expanded = false; onSelect(option.id) }
                )
            }
        }
    }
}

@Composable
private fun TargetCalendarRow(
    calendars: List<CalendarSyncService.DeviceCalendar>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = calendars.firstOrNull { it.id == selectedId }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Target Calendar", fontSize = 15.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
            selected?.color?.let {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(it)))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(selected?.displayName ?: "Default", fontSize = 14.sp, color = TextSecondaryColor)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MediumGray)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            calendars.forEach { cal ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            cal.color?.let { Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(it))) }
                            Column {
                                Text(cal.displayName)
                                if (cal.accountName.isNotBlank()) Text(cal.accountName, fontSize = 11.sp, color = TextSecondaryColor)
                            }
                        }
                    },
                    onClick = { expanded = false; onSelect(cal.id) }
                )
            }
        }
    }
}
