package com.fitnesslink.fit.ui.session

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.model.api.Movement
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.session.MovementPerformanceLines
import com.fitnesslink.fit.session.SessionFinishRule
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.PhaseOption
import com.fitnesslink.fit.viewmodel.SessionEditAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ellipsis menu in the session info strip (Do → Build). Items adapt to the current task. */
@Composable
fun SessionEditMenu(
    phaseOptions: List<PhaseOption>,
    isMovement: Boolean,
    canSwap: Boolean,
    canEditSets: Boolean,
    canRemove: Boolean,
    isWorkoutEditable: Boolean,
    hasSessionOnlyEdits: Boolean,
    onAction: (SessionEditAction) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var phaseMenu by remember { mutableStateOf(false) }
    Box {
        Icon(Icons.Filled.MoreVert, contentDescription = "Edit workout", tint = White.copy(alpha = 0.9f), modifier = Modifier.size(32.dp).clickable { open = true }.padding(6.dp))
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text("BUILD", color = TextSecondaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            DropdownMenuItem(text = { Text("Add exercise after this") }, leadingIcon = { Icon(Icons.Filled.Add, null) }, onClick = { open = false; onAction(SessionEditAction.AddAfterCurrent) })
            DropdownMenuItem(text = { Text("Add exercise at the end") }, leadingIcon = { Icon(Icons.Filled.AddCircle, null) }, onClick = { open = false; onAction(SessionEditAction.AddAtEnd) })
            if (phaseOptions.size > 1) DropdownMenuItem(text = { Text("Add exercise to phase…") }, onClick = { open = false; phaseMenu = true })
            if (isMovement) {
                Text("THIS EXERCISE", color = TextSecondaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                if (canSwap) DropdownMenuItem(text = { Text("Swap movement") }, leadingIcon = { Icon(Icons.Filled.SwapHoriz, null) }, onClick = { open = false; onAction(SessionEditAction.Swap) })
                if (canEditSets) DropdownMenuItem(text = { Text("Edit sets & reps") }, leadingIcon = { Icon(Icons.Filled.Tune, null) }, onClick = { open = false; onAction(SessionEditAction.EditSets) })
                if (canRemove) DropdownMenuItem(text = { Text("Remove exercise", color = Color(0xFFE53935)) }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Color(0xFFE53935)) }, onClick = { open = false; onAction(SessionEditAction.Remove) })
            }
            if (!isWorkoutEditable) {
                DropdownMenuItem(text = { Text("Save as my workout") }, leadingIcon = { Icon(Icons.Filled.Save, null) }, onClick = { open = false; onAction(SessionEditAction.SaveMyVersion) })
                Text(if (hasSessionOnlyEdits) "Your changes apply to this session only" else "Changes to this workout apply to this session only",
                    color = TextSecondaryColor, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
        }
        DropdownMenu(expanded = phaseMenu, onDismissRequest = { phaseMenu = false }) {
            phaseOptions.forEach { phase -> DropdownMenuItem(text = { Text(phase.name) }, onClick = { phaseMenu = false; onAction(SessionEditAction.AddToPhase(phase.id)) }) }
        }
    }
}

/** Sets × reps steppers for an exercise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSetsSheet(sets: Int, reps: Int, exerciseName: String, onSave: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var s by remember { mutableIntStateOf(maxOf(1, sets)) }
    var r by remember { mutableIntStateOf(maxOf(0, reps)) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = White) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Sets & Reps", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(exerciseName, color = TextSecondaryColor, fontSize = 14.sp)
            Stepper("Sets", s, 1, 20) { s = it }
            Stepper("Reps", r, 0, 100) { r = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cancel", color = TextSecondaryColor, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clickable { onDismiss() }.padding(vertical = 12.dp))
                Text("Save", color = White, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(FLPrimary).clickable { onSave(s, r); onDismiss() }.padding(vertical = 12.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("$label: $value", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.RemoveCircle, contentDescription = "Fewer $label", tint = if (value > min) FLPrimary else TextSecondaryColor, modifier = Modifier.size(32.dp).clickable(enabled = value > min) { onChange(value - 1) })
        Spacer(Modifier.size(12.dp))
        Icon(Icons.Filled.AddCircle, contentDescription = "More $label", tint = if (value < max) FLPrimary else TextSecondaryColor, modifier = Modifier.size(32.dp).clickable(enabled = value < max) { onChange(value + 1) })
    }
}

/** "This isn't your workout" — scope choice on a coach / shared-in workout. */
@Composable
fun EditScopeDialog(onSessionOnly: () -> Unit, onSessionAndSave: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("This isn't your workout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Changes here apply to today's session. Saving your own version also creates a personal copy in My Workouts with this change.")
                Text("Just this session", color = FLPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable { onSessionOnly() }.padding(vertical = 8.dp))
                Text("This session + save my own version", color = FLPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable { onSessionAndSave() }.padding(vertical = 8.dp))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Finish Workout? confirmation shared by both players (FTNSSLNK-403). */
@Composable
fun FinishConfirmationDialog(message: String, onFinishAnyway: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = onContinue,
        title = { Text(SessionFinishRule.title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onContinue) { Text(SessionFinishRule.continueLabel, color = FLPrimary, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onFinishAnyway) { Text(SessionFinishRule.finishAnywayLabel, color = Color(0xFFE53935)) } }
    )
}

/** Simple confirm (Remove this exercise? / Save as my workout?). */
@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, destructive: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = if (destructive) Color(0xFFE53935) else FLPrimary, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Movement-info modal: structured detail (Setup / Execution / Breathe / Tips) plus the top set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementInfoSheet(task: WorkoutTask, onDismiss: () -> Unit) {
    var movement by remember(task.movementId) { mutableStateOf<Movement?>(null) }
    var failed by remember(task.movementId) { mutableStateOf(false) }
    val topSet = remember(task.id) { MovementPerformanceLines.topSet(task) }
    LaunchedEffect(task.movementId) {
        val id = task.movementId ?: run { failed = true; return@LaunchedEffect }
        movement = try { withContext(Dispatchers.IO) { ApiClient.movementApi.get(id) } } catch (_: Exception) { failed = true; null }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = White) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FLImageView(ref = task.movementThumbnailId?.let { MediaRef.MovementThumbnail(it) }, height = 180.dp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)))
            Text(task.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            topSet?.let {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BackgroundColor).padding(12.dp)) {
                    Text("Top set", color = TextSecondaryColor, fontSize = 13.sp); Spacer(Modifier.weight(1f))
                    Text(MovementPerformanceLines.topSetLine(it), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
            val m = movement
            when {
                m != null -> {
                    (m.summary ?: m.description)?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 14.sp, color = TextPrimaryColor) }
                    m.sections.sortedBy { it.ordinal }.forEach { section ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(section.type.uppercase(), color = FLPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                            section.note?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, color = TextSecondaryColor) }
                            section.steps.sortedBy { it.ordinal }.forEachIndexed { i, step ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${i + 1}.", color = TextSecondaryColor, fontSize = 14.sp)
                                    Column { step.label?.let { Text(it, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }; Text(step.text, fontSize = 14.sp) }
                                }
                            }
                        }
                    }
                }
                failed -> task.instructions?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 14.sp) } ?: Text("No details available for this movement.", color = TextSecondaryColor, fontSize = 14.sp)
                else -> Text("Loading…", color = TextSecondaryColor, fontSize = 14.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
