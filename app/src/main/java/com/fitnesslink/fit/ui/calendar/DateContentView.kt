package com.fitnesslink.fit.ui.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.calendar.CalendarDeleteScope
import com.fitnesslink.fit.calendar.CalendarDurationChips
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import java.text.DateFormat
import java.util.Date

/**
 * One scheduled entry on the selected day (iOS `DateContentView`): status
 * dot, title, time range (start + duration, falling back to the workout's
 * estimate), program badge with the program's name, a ⋯ menu + long-press
 * with "Move to Another Day" / "Remove from Calendar" (confirmed; program
 * workouts offer a scope choice). The main area navigates via `tapRoute`.
 * In multi-select mode the row becomes a checkbox toggle.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DateContentView(
    content: FitnessContent,
    deleteScope: CalendarDeleteScope,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onRemoveProgram: () -> Unit,
    isSelecting: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val hasWorkout = content.workoutId.isNotEmpty()
    val isProgramWorkout = content.programId.isNotEmpty()

    val programName = remember(content.programId) {
        if (content.programId.isEmpty()) "" else (DatabaseManager.program(content.programId)?.name?.takeIf { it.isNotBlank() } ?: "Program")
    }
    val timeLabel = remember(content.id, content.scheduledDate, content.durationMinutes) { timeRangeLabel(content) }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            content = content, scope = deleteScope, programName = programName,
            onDismiss = { showDeleteConfirm = false },
            onDeleteOne = { showDeleteConfirm = false; onDelete() },
            onDeleteProgram = { showDeleteConfirm = false; onRemoveProgram() }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .then(
                if (isSelecting) Modifier.clickable(onClick = onToggleSelect)
                else Modifier.combinedClickable(onClick = { if (hasWorkout) onOpen() }, onLongClick = { showMenu = true })
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelecting) {
            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) FLPrimary else MediumGray,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Image(
            painter = painterResource(if (content.isCompleted) R.drawable.completedstate else R.drawable.scheduledstate),
            contentDescription = content.status,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(content.title.ifBlank { "Workout" }, fontSize = 15.sp, color = TextPrimaryColor)
            if (timeLabel != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(timeLabel, fontSize = 12.sp, color = TextSecondaryColor)
            }
            if (isProgramWorkout) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(programName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary, maxLines = 2)
                }
            }
        }

        if (!isSelecting) {
            Box {
                Icon(
                    Icons.Default.MoreHoriz, contentDescription = "More", tint = TextSecondaryColor,
                    modifier = Modifier.size(32.dp).clickable { showMenu = true }.padding(4.dp)
                )
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Move to Another Day") },
                        leadingIcon = { Icon(Icons.Default.EventRepeat, contentDescription = null) },
                        onClick = { showMenu = false; onMove() }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from Calendar", color = Color.Red) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                        onClick = { showMenu = false; showDeleteConfirm = true }
                    )
                }
            }
            if (hasWorkout) {
                Spacer(modifier = Modifier.width(4.dp))
                Image(painter = painterResource(R.drawable.rightarrow), contentDescription = "Open", modifier = Modifier.size(width = 6.dp, height = 10.dp))
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    content: FitnessContent,
    scope: CalendarDeleteScope,
    programName: String,
    onDismiss: () -> Unit,
    onDeleteOne: () -> Unit,
    onDeleteProgram: () -> Unit
) {
    when (scope) {
        is CalendarDeleteScope.Single -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Remove this workout from your calendar?") },
            confirmButton = { TextButton(onClick = onDeleteOne) { Text("Remove from Calendar", color = Color.Red) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
        is CalendarDeleteScope.ProgramChoice -> {
            val n = scope.programWorkoutCount
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Remove from Calendar") },
                text = {
                    Column {
                        Text("“${content.title}” is part of “$programName”.", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Remove just this workout", color = Color.Red, fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onDeleteOne).padding(vertical = 10.dp)
                        )
                        Text(
                            "Remove whole program ($n workout${if (n == 1) "" else "s"})", color = Color.Red, fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onDeleteProgram).padding(vertical = 10.dp)
                        )
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
            )
        }
    }
}

/** "7:00 AM – 7:45 AM" when a duration is known, "7:00 AM" otherwise, null when unscheduled. */
private fun timeRangeLabel(content: FitnessContent): String? {
    val start = content.scheduledDate ?: return null
    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
    val workout = if (content.workoutId.isNotEmpty()) DatabaseManager.workout(content.workoutId) else null
    val minutes = CalendarDurationChips.resolvedDurationMinutes(content, workout?.time, workout?.estimatedContentMinutes)
    return if (minutes != null) "${fmt.format(Date(start))} – ${fmt.format(Date(start + minutes * 60_000L))}" else fmt.format(Date(start))
}
