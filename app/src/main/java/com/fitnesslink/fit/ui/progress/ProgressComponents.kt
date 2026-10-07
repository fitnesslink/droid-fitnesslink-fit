package com.fitnesslink.fit.ui.progress

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.AnglePhoto
import com.fitnesslink.fit.model.BodyPart
import com.fitnesslink.fit.model.MeasurementEntry
import com.fitnesslink.fit.model.MeasurementUnit
import com.fitnesslink.fit.model.PhotoAngle
import com.fitnesslink.fit.progress.MeasurementMath
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Progress-tracking building blocks shared by the weight / measurement /
// photo screens: body-part icons (iOS `BodyPart.sfSymbolName`), the shared
// `MeasurementBreakdownView`, progress-photo thumbnails rendered through
// `FLImageView(MediaRef.ProgressPhoto)`, a date field with Material's
// `DatePickerDialog`, and a delete confirmation.

/** iOS `BodyPart.sfSymbolName` mapped to Material glyphs. */
fun bodyPartIcon(part: BodyPart): ImageVector = when (part) {
    BodyPart.CHEST -> Icons.Filled.AccessibilityNew
    BodyPart.WAIST -> Icons.Filled.Accessibility
    BodyPart.HIPS -> Icons.Filled.DirectionsWalk
    BodyPart.LEFT_BICEP, BodyPart.RIGHT_BICEP -> Icons.Filled.FitnessCenter
    BodyPart.LEFT_THIGH, BodyPart.RIGHT_THIGH -> Icons.Filled.DirectionsRun
    BodyPart.NECK -> Icons.Filled.Face
    BodyPart.CALVES -> Icons.Filled.DirectionsWalk
    BodyPart.FOREARMS -> Icons.Filled.PanTool
}

fun formatMeasurement(value: Double): String = String.format(Locale.US, "%.1f", value)

fun formatSignedDelta(value: Double): String = "${if (value > 0) "+" else ""}${String.format(Locale.US, "%.1f", value)}"

/**
 * iOS `MeasurementBreakdownView`: Core / Arms / Legs / Other groups, each
 * part with its icon, value in [unit] and a coloured delta vs. [previous]
 * (brand when down, orange when up, grey when unchanged).
 */
@Composable
fun MeasurementBreakdownView(
    entry: MeasurementEntry,
    previous: MeasurementEntry?,
    unit: MeasurementUnit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MeasurementMath.grouped(entry).forEach { (category, parts) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(category, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                parts.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(bodyPartIcon(m.bodyPart), contentDescription = null, tint = FLPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(m.bodyPart.displayName, fontSize = 14.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                        Text(
                            "${formatMeasurement(MeasurementMath.displayValue(m, unit))} ${unit.label}",
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor
                        )
                        MeasurementMath.delta(entry, previous, m.bodyPart, unit)?.let { change ->
                            Spacer(modifier = Modifier.width(8.dp))
                            DeltaChip(change)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeltaChip(change: Double) {
    val color = when {
        change < 0 -> FLPrimary
        change > 0 -> OrangeTheme
        else -> TextSecondaryColor
    }
    Text(
        formatSignedDelta(change),
        fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (change != 0.0) color.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/**
 * One progress-photo tile: the real image through `FLImageView` when the
 * angle has a media id, otherwise a person glyph placeholder; angle label
 * bottom-left (iOS `PhotoEntryCard`).
 */
@Composable
fun ProgressPhotoThumb(photo: AnglePhoto?, angle: PhotoAngle, height: Dp, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(if (photo != null) Color.Gray.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.08f))
    ) {
        val imageId = photo?.imageId
        if (imageId != null) {
            FLImageView(ref = MediaRef.ProgressPhoto(imageId), modifier = Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Person, contentDescription = angle.displayName,
                    tint = if (photo != null) MediumGray else MediumGray.copy(alpha = 0.5f), modifier = Modifier.size(if (height > 150.dp) 40.dp else 20.dp)
                )
                if (photo == null) Text(angle.displayName, fontSize = 9.sp, color = MediumGray.copy(alpha = 0.6f))
            }
        }
        if (showLabel && photo != null) {
            Text(
                angle.displayName, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/** A tappable "Date" row that opens Material's `DatePickerDialog` (iOS `DatePicker(displayedComponents: .date)`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(date: Date, onDateChange: (Date) -> Unit, modifier: Modifier = Modifier, label: String = "Date") {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Gray.copy(alpha = 0.08f))
            .clickable { open = true }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontSize = 14.sp, color = TextSecondaryColor, modifier = Modifier.weight(1f))
        Text(longDate.format(date), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
    }
    if (open) {
        // The picker works in UTC day keys; shift so the shown day matches the local date.
        val state = rememberDatePickerState(initialSelectedDateMillis = localDayToUtcMillis(date))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(utcDayToLocalDate(it, date)) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }
}

/** Material `DatePicker` selections are UTC midnight; map a local date to that key. */
fun localDayToUtcMillis(date: Date): Long {
    val local = Calendar.getInstance().apply { time = date }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** Reverse of [localDayToUtcMillis], keeping [previous]'s time of day. */
fun utcDayToLocalDate(utcMillis: Long, previous: Date): Date {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    val local = Calendar.getInstance().apply { time = previous }
    local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    return local.time
}

/** Confirmation used by every delete action in the progress screens. */
@Composable
fun ConfirmDeleteDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text("Delete", color = Color(0xFFE53935)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

val longDate: SimpleDateFormat get() = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
