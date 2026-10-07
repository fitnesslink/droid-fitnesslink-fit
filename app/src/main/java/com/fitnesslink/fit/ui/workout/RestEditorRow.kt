package com.fitnesslink.fit.ui.workout

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.RestType
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.ImagePlaceholder
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.WorkoutEditorViewModel
import com.fitnesslink.fit.workouts.WorkoutRowOps

/**
 * Rest row in the editor (iOS `RestEditorRow`): Passive / Active Recovery
 * toggle, tap-to-edit duration, remove, and the recovery-movement picker
 * row when active.
 */
@Composable
fun RestEditorRow(task: WorkoutTask, phaseIndex: Int, viewModel: WorkoutEditorViewModel) {
    var showTimePicker by remember(task.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BackgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(White)) {
                SegmentPill("Passive Rest", task.restType == RestType.PASSIVE, Modifier.weight(1f)) {
                    viewModel.updateRestType(task.id, RestType.PASSIVE)
                }
                SegmentPill("Active Recovery", task.restType == RestType.ACTIVE_RECOVERY, Modifier.weight(1f)) {
                    viewModel.updateRestType(task.id, RestType.ACTIVE_RECOVERY)
                    if (task.restMovementId == null) viewModel.restMovementPickerTaskId = task.id
                }
            }
            Text(
                text = task.rest.ifEmpty { "1:00" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary,
                modifier = Modifier.clickable { showTimePicker = true }
            )
            Icon(
                Icons.Filled.Cancel, contentDescription = "Remove rest", tint = MediumGray,
                modifier = Modifier.size(18.dp).clickable { viewModel.removeRest(task.id, phaseIndex) }
            )
        }
        if (task.restType == RestType.ACTIVE_RECOVERY) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(White)
                    .clickable { viewModel.restMovementPickerTaskId = task.id }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RecoveryThumbnail(task.restMovementThumbnailId, 32)
                Text(
                    text = task.restMovementName ?: "Choose Recovery Movement",
                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = if (task.restMovementName == null) TextSecondaryColor else TextPrimaryColor,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MediumGray)
            }
        }
    }

    if (showTimePicker) {
        RestDurationPickerSheet(
            initialSeconds = if (task.restSeconds > 0) task.restSeconds else 60,
            title = "Rest Duration",
            confirmLabel = "Save",
            onConfirm = { viewModel.updateRest(task.id, it); showTimePicker = false },
            onCancel = { showTimePicker = false }
        )
    }
}

@Composable
internal fun SegmentPill(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(
        text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1,
        color = if (active) White else TextPrimaryColor,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) FLPrimary else White)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
internal fun RecoveryThumbnail(thumbnailId: String?, sizeDp: Int) {
    if (thumbnailId != null) {
        FLImageView(ref = MediaRef.MovementThumbnail(thumbnailId), modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(6.dp)))
    } else {
        Box(
            modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(6.dp)).background(ImagePlaceholder),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = MediumGray, modifier = Modifier.size((sizeDp / 2).dp))
        }
    }
}

/**
 * Seconds entry with quick presets (iOS `RestDurationPickerSheet`). Used for
 * Add Rest, rest duration, round rest, member rest and between-sets rest.
 */
@Composable
fun RestDurationPickerSheet(
    initialSeconds: Int = 60,
    title: String = "Rest Duration",
    confirmLabel: String = "Save",
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit
) {
    var secondsText by remember { mutableStateOf(maxOf(0, initialSeconds).toString()) }
    val presets = listOf(30, 60, 90, 120, 180)
    val valid = (secondsText.toIntOrNull() ?: 0) > 0

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = secondsText,
                        onValueChange = { secondsText = it.filter(Char::isDigit) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("seconds", fontSize = 16.sp, color = TextSecondaryColor)
                }
                Text("Quick presets", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    presets.forEach { secs ->
                        Text(
                            text = WorkoutRowOps.formatRest(secs), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FLPrimary, textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(FLPrimary.copy(alpha = 0.08f))
                                .clickable { secondsText = secs.toString() }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(maxOf(0, secondsText.toIntOrNull() ?: 0)) }, enabled = valid) { Text(confirmLabel, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}
