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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.workouts.WorkoutRowOps
import com.fitnesslink.fit.model.api.Movement
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.session.MovementPerformanceLines
import com.fitnesslink.fit.session.SessionFinishRule
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme
import com.fitnesslink.fit.viewmodel.SessionEditAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Destructive red for session menus and dialogs (Discard / Remove / Finish anyway). Light keeps the
 * pre-540 `0xFFE53935` so light mode is unchanged; dark uses the theme's danger. theme-gap: the
 * light `danger` token is pure red, not this shade.
 */
internal val sessionDestructive: Color
    @Composable @ReadOnlyComposable
    get() = FLTheme.colors.danger

/**
 * Ellipsis menu in the session info strip (Interactive Mode only — the playlist rows have their own
 * menu). Items adapt to the current task: swap / sets / remove for a movement, "Save as my workout"
 * only when the workout isn't the user's own. Interactive Mode no longer offers the "Build"
 * insertions (add after / at end / to phase — FTNSSLNK-505); Playlist Mode keeps them. Callers hide
 * the menu entirely when it would open empty (FTNSSLNK-515, `InteractiveSessionViewModel.hasEditMenuItems`).
 */
@Composable
fun SessionEditMenu(
    isMovement: Boolean,
    canSwap: Boolean,
    canEditSets: Boolean,
    editSetsTitle: String,
    canRemove: Boolean,
    isWorkoutEditable: Boolean,
    hasSessionOnlyEdits: Boolean,
    onAction: (SessionEditAction) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Icon(Icons.Filled.MoreVert, contentDescription = "Edit workout", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(32.dp).clickable { open = true }.padding(6.dp)) // theme-exempt: always-dark player (sits on the Interactive video)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (isMovement) {
                Text("THIS EXERCISE", color = FLTheme.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                if (canSwap) DropdownMenuItem(text = { Text("Swap Exercise") }, leadingIcon = { Icon(Icons.Filled.SwapHoriz, null) }, onClick = { open = false; onAction(SessionEditAction.Swap) })
                if (canEditSets) DropdownMenuItem(text = { Text(editSetsTitle) }, leadingIcon = { Icon(Icons.Filled.Tune, null) }, onClick = { open = false; onAction(SessionEditAction.EditSets) })
                if (canRemove) DropdownMenuItem(text = { Text("Remove Exercise", color = sessionDestructive) }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = sessionDestructive) }, onClick = { open = false; onAction(SessionEditAction.Remove) })
            }
            if (!isWorkoutEditable) {
                DropdownMenuItem(text = { Text("Save as my workout") }, leadingIcon = { Icon(Icons.Filled.Save, null) }, onClick = { open = false; onAction(SessionEditAction.SaveMyVersion) })
                Text(if (hasSessionOnlyEdits) "Your changes apply to this session only" else "Changes to this workout apply to this session only",
                    color = FLTheme.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
        }
    }
}

/**
 * The sets editor for [task]: sets × reps for rep-based work, sets × time for timed work
 * (FTNSSLNK-512). Shared by Playlist and Interactive so both modes edit the same way.
 */
@Composable
fun SessionSetsEditorSheet(
    task: WorkoutTask,
    onSaveReps: (sets: Int, reps: Int) -> Unit,
    onSaveDuration: (sets: Int, durationSeconds: Int, weightLbs: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    if (task.usesDuration) {
        SessionDurationSetsSheet(
            sets = maxOf(task.sets, 1),
            durationSeconds = maxOf(task.durationSeconds ?: 60, 5),
            weightLbs = task.weightKg?.takeIf { it > 0 },
            exerciseName = task.name,
            onSave = onSaveDuration,
            onDismiss = onDismiss
        )
    } else {
        SessionSetsSheet(maxOf(task.sets, 1), task.reps, task.name, onSave = onSaveReps, onDismiss = onDismiss)
    }
}

/**
 * Sets × duration for a time-based exercise (FTNSSLNK-512): number of sets, seconds per set (the
 * same for every set), and the load when the exercise carries one. No reps field. The new duration
 * drives the Interactive set timer; sets already logged stay logged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDurationSetsSheet(
    sets: Int,
    durationSeconds: Int,
    weightLbs: Int?,
    exerciseName: String,
    onSave: (sets: Int, durationSeconds: Int, weightLbs: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var s by remember { mutableIntStateOf(maxOf(1, sets)) }
    var seconds by remember { mutableIntStateOf(durationSeconds.coerceIn(5, 3600)) }
    var weightText by remember { mutableStateOf(weightLbs?.let { WeightUnit.displayNumber(it.toDouble()) } ?: "") }
    // 5 s steps under two minutes, 15 s above — fine control for short holds without a hundred taps.
    fun step(up: Boolean): Int = if (up) (if (seconds < 120) 5 else 15) else (if (seconds <= 120) 5 else 15)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = FLTheme.colors.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Sets & Time", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(exerciseName, color = FLTheme.colors.textSecondary, fontSize = 14.sp)
            Stepper("Sets", s, 1, 20) { s = it }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Time per set: ${WorkoutRowOps.formatDuration(seconds)}", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.RemoveCircle, contentDescription = "Less time", tint = if (seconds > 5) FLPrimary else FLTheme.colors.textSecondary,
                    modifier = Modifier.size(32.dp).clickable(enabled = seconds > 5) { seconds = maxOf(5, seconds - step(up = false)) })
                Spacer(Modifier.size(12.dp))
                Icon(Icons.Filled.AddCircle, contentDescription = "More time", tint = if (seconds < 3600) FLPrimary else FLTheme.colors.textSecondary,
                    modifier = Modifier.size(32.dp).clickable(enabled = seconds < 3600) { seconds = minOf(3600, seconds + step(up = true)) })
            }
            if (weightLbs != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Weight", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End),
                        modifier = Modifier.width(100.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(WeightUnit.current.label, color = FLTheme.colors.textSecondary, fontSize = 14.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cancel", color = FLTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clickable { onDismiss() }.padding(vertical = 12.dp))
                Text("Save", color = FLTheme.colors.onBrand, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(FLPrimary).clickable {
                        // Null leaves the load alone (no weight row); 0 clears it.
                        val saved = weightLbs?.let {
                            val display = weightText.replace(',', '.').toDoubleOrNull() ?: 0.0
                            Math.round(WeightUnit.storageLbs(maxOf(display, 0.0))).toInt()
                        }
                        onSave(s, seconds, saved); onDismiss()
                    }.padding(vertical = 12.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Sets × reps steppers for an exercise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSetsSheet(sets: Int, reps: Int, exerciseName: String, onSave: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var s by remember { mutableIntStateOf(maxOf(1, sets)) }
    var r by remember { mutableIntStateOf(maxOf(0, reps)) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = FLTheme.colors.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Sets & Reps", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(exerciseName, color = FLTheme.colors.textSecondary, fontSize = 14.sp)
            Stepper("Sets", s, 1, 20) { s = it }
            Stepper("Reps", r, 0, 100) { r = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cancel", color = FLTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clickable { onDismiss() }.padding(vertical = 12.dp))
                Text("Save", color = FLTheme.colors.onBrand, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
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
        Icon(Icons.Filled.RemoveCircle, contentDescription = "Fewer $label", tint = if (value > min) FLPrimary else FLTheme.colors.textSecondary, modifier = Modifier.size(32.dp).clickable(enabled = value > min) { onChange(value - 1) })
        Spacer(Modifier.size(12.dp))
        Icon(Icons.Filled.AddCircle, contentDescription = "More $label", tint = if (value < max) FLPrimary else FLTheme.colors.textSecondary, modifier = Modifier.size(32.dp).clickable(enabled = value < max) { onChange(value + 1) })
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
        dismissButton = { TextButton(onClick = onFinishAnyway) { Text(SessionFinishRule.finishAnywayLabel, color = sessionDestructive) } }
    )
}

/** Simple confirm (Remove this exercise? / Save as my workout?). */
@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, destructive: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = if (destructive) sessionDestructive else FLPrimary, fontWeight = FontWeight.SemiBold) } },
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = FLTheme.colors.surface) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FLImageView(ref = task.movementThumbnailId?.let { MediaRef.MovementThumbnail(it) }, height = 180.dp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)))
            Text(task.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
            topSet?.let {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(FLTheme.colors.background).padding(12.dp)) {
                    Text("Top set", color = FLTheme.colors.textSecondary, fontSize = 13.sp); Spacer(Modifier.weight(1f))
                    Text(MovementPerformanceLines.topSetLine(it), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
            val m = movement
            when {
                m != null -> {
                    (m.summary ?: m.description)?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 14.sp, color = FLTheme.colors.textPrimary) }
                    m.sections.sortedBy { it.ordinal }.forEach { section ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(section.type.uppercase(), color = FLPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                            section.note?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, color = FLTheme.colors.textSecondary) }
                            section.steps.sortedBy { it.ordinal }.forEachIndexed { i, step ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${i + 1}.", color = FLTheme.colors.textSecondary, fontSize = 14.sp)
                                    Column { step.label?.let { Text(it, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }; Text(step.text, fontSize = 14.sp) }
                                }
                            }
                        }
                    }
                }
                failed -> task.instructions?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 14.sp) } ?: Text("No details available for this movement.", color = FLTheme.colors.textSecondary, fontSize = 14.sp)
                else -> Text("Loading…", color = FLTheme.colors.textSecondary, fontSize = 14.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
