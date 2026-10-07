package com.fitnesslink.fit.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.session.ActiveWorkoutSession
import com.fitnesslink.fit.session.MovementHistoryStore
import com.fitnesslink.fit.session.MovementPerformanceLines
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.InteractiveSessionViewModel
import com.fitnesslink.fit.viewmodel.PlaylistViewModel

/**
 * Playlist log sheet (port of iOS `PlaylistMovementLogSheet`): tap a set circle (one set) or a
 * row (every planned set). Saving touches only edited or previously logged rows — a prefilled row
 * is a plan, not a log (FTNSSLNK-431) — except the one set a circle tap opened. Unlogged rows
 * open with an EMPTY weight field; the plan / last-session load is only a placeholder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistMovementLogSheet(viewModel: PlaylistViewModel, task: WorkoutTask, setNumber: Int?, onDismiss: () -> Unit) {
    val unit = if (task.groupId.isEmpty()) "Set" else "Round"
    val plannedCount = if (task.groupId.isEmpty()) maxOf(task.sets, 1) else maxOf(task.rounds ?: task.totalRounds, 1)
    val previous = remember(task.id) { MovementHistoryStore.previousWeight(task.movementId, task.name, ActiveWorkoutSession.sessionId) }
    val topSet = remember(task.id) { MovementPerformanceLines.topSet(task) }
    var rows by remember(task.id, setNumber) { mutableStateOf(buildRows(viewModel, task, plannedCount, previous?.weightLbs, setNumber)) }

    fun save() {
        for (row in rows) {
            if (!PlaylistLogSheetRules.saveTouches(row.wasLogged, setNumber != null, row.weightText, row.repsText, row.originalWeightText, row.originalRepsText)) continue
            val weight = PlaylistLogSheetRules.resolvedWeight(row.weightText, row.weightHint)?.let { WeightUnit.storageLbs(fromDisplay = it) }
            val reps = row.repsText.trim().toIntOrNull()
            if (task.usesDuration) {
                if (reps == null) { viewModel.clearLoggedSet(task.id, row.setNumber); continue }
                viewModel.logSet(task.id, row.setNumber, weight, reps, row.durationSeconds)
                continue
            }
            if (reps == null || reps <= 0) { viewModel.clearLoggedSet(task.id, row.setNumber); continue }
            viewModel.logSet(task.id, row.setNumber, weight, reps)
        }
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = White) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (!task.isInterval && task.displayMetric.isNotEmpty()) Text(task.displayMetric, fontSize = 13.sp, color = TextSecondaryColor)
                setNumber?.let { Text("Logging ${unit.lowercase()} $it of $plannedCount", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor) }
                previous?.let { Text(it.lastTimeLine, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary) }
                topSet?.let { Text("Top set: ${MovementPerformanceLines.topSetLine(it)}", fontSize = 13.sp, color = TextSecondaryColor) }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BackgroundColor).padding(vertical = 12.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(unit, fontSize = 12.sp, color = TextSecondaryColor, modifier = Modifier.width(44.dp))
                    Text("Weight (${WeightUnit.current.label})", fontSize = 12.sp, color = TextSecondaryColor, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Text("Reps", fontSize = 12.sp, color = TextSecondaryColor, textAlign = TextAlign.Center, modifier = Modifier.width(80.dp))
                    if (task.usesDuration) Text("Time", fontSize = 12.sp, color = TextSecondaryColor, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
                }
                rows.forEachIndexed { index, row ->
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${row.setNumber}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(36.dp))
                        OutlinedTextField(row.weightText, { v -> rows = rows.toMutableList().also { it[index] = row.copy(weightText = v) } },
                            placeholder = { Text(row.weightHint.ifEmpty { "0" }, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(row.repsText, { v -> rows = rows.toMutableList().also { it[index] = row.copy(repsText = v) } },
                            placeholder = { Text("0", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp))
                        if (task.usesDuration) Text(InteractiveSessionViewModel.formatDuration(row.durationSeconds ?: 0), fontSize = 14.sp, color = TextSecondaryColor, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
                    }
                }
            }
            CompletedToggle(checked = viewModel.isCompleted(task.id)) { viewModel.toggleCompleted(task.id) }
            Text("Save", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FLPrimary).clickable { save() }.padding(vertical = 14.dp))
            Text("Cancel", color = TextSecondaryColor, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { onDismiss() }.padding(vertical = 8.dp))
            Spacer(Modifier.height(16.dp))
        }
    }
}

internal data class LogSheetRow(
    val setNumber: Int,
    val weightText: String,
    val repsText: String,
    val weightHint: String = "",
    val durationSeconds: Int? = null,
    val originalWeightText: String = "",
    val originalRepsText: String = "",
    val wasLogged: Boolean = false
)

private fun buildRows(viewModel: PlaylistViewModel, task: WorkoutTask, plannedCount: Int, previousLbs: Double?, setNumber: Int?): List<LogSheetRow> {
    val existing = viewModel.actualSetLogs[task.id] ?: emptyMap()
    fun formatWeight(v: Double?): String = if (v != null && v > 0) WeightUnit.displayNumber(fromLbs = v) else ""
    fun repsPrefill(reps: Int): String = if (task.usesDuration && reps <= 0) "" else reps.toString()
    var fallbackWeight: Double? = null
    val rows = (1..plannedCount).map { n ->
        val planned = task.setConfigs.firstOrNull { it.setNumber == n }
        val plannedDuration = if (task.usesDuration) (planned?.durationSeconds ?: task.durationSeconds) else null
        val logged = existing[n]
        if (logged != null) {
            fallbackWeight = logged.weightKg
            LogSheetRow(n, formatWeight(logged.weightKg), repsPrefill(logged.reps), durationSeconds = if (task.usesDuration) (logged.durationSeconds ?: plannedDuration) else null, wasLogged = true)
        } else if (planned != null) {
            LogSheetRow(n, "", repsPrefill(planned.reps), weightHint = formatWeight(planned.weightKg ?: task.weightKg?.toDouble() ?: fallbackWeight), durationSeconds = plannedDuration)
        } else {
            LogSheetRow(n, "", repsPrefill(if (task.usesDuration) 0 else task.reps), weightHint = formatWeight(task.weightKg?.toDouble() ?: fallbackWeight), durationSeconds = plannedDuration)
        }
    }.map { row ->
        val hint = if (!row.wasLogged && row.weightHint.isEmpty() && previousLbs != null) WeightUnit.displayNumber(fromLbs = previousLbs) else row.weightHint
        row.copy(weightHint = hint, originalWeightText = row.weightText, originalRepsText = row.repsText)
    }
    return if (setNumber != null) rows.filter { it.setNumber == setNumber } else rows
}

/** Which rows a Save on the playlist log sheet writes (FTNSSLNK-431). */
object PlaylistLogSheetRules {
    fun saveTouches(wasLogged: Boolean, scopedToOneSet: Boolean = false, weightText: String, repsText: String, originalWeightText: String, originalRepsText: String): Boolean {
        if (wasLogged || scopedToOneSet) return true
        return weightText.trim() != originalWeightText.trim() || repsText.trim() != originalRepsText.trim()
    }

    /** The weight a row logs, in the display unit: what the user typed, else the placeholder. Null when both are blank. */
    fun resolvedWeight(entered: String, hint: String): Double? {
        val typed = entered.trim()
        if (typed.isNotEmpty()) return typed.toDoubleOrNull()
        return hint.trim().toDoubleOrNull()
    }
}
