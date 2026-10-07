package com.fitnesslink.fit.ui.session

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.IntervalMetrics
import com.fitnesslink.fit.model.RestType
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.session.ActiveWorkoutSession
import com.fitnesslink.fit.session.ExerciseState
import com.fitnesslink.fit.session.MovementHistoryStore
import com.fitnesslink.fit.session.SetState
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.InfoColor
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.PlaylistEditRequest
import com.fitnesslink.fit.viewmodel.PlaylistViewModel
import com.fitnesslink.fit.viewmodel.SessionWorkoutEditor
import java.util.Locale

// Playlist rows (ports of PlaylistWorkoutMovementView / PlaylistSetStatusRow /
// AdvancedPlaylistMovementsView / WorkoutRestView).

/** One movement row: checkbox, thumbnail (tap = fullscreen video), info, name + metric, set rings, previous weight, note, edit menu. */
@Composable
fun PlaylistMovementRow(
    task: WorkoutTask,
    viewModel: PlaylistViewModel,
    isGrouped: Boolean = false,
    onPlayVideo: (WorkoutTask) -> Unit,
    onInfo: (WorkoutTask) -> Unit
) {
    @Suppress("UNUSED_VARIABLE") val tick = viewModel.tick
    val historyVersion by MovementHistoryStore.version.collectAsState()
    val isCompleted = viewModel.isCompleted(task.id)
    val states = viewModel.setStates(task.id)
    val exerciseState = viewModel.exerciseState(task.id)
    val loggedCount = viewModel.actualSetLogs[task.id]?.size ?: 0
    val previous = remember(task.id, task.movementId, loggedCount, historyVersion) {
        MovementHistoryStore.previousWeight(task.movementId, task.name, ActiveWorkoutSession.sessionId)
    }
    val unit = if (task.groupId.isEmpty()) "Set" else "Round"
    var menu by remember { mutableStateOf(false) }
    val hasVideo = task.effectiveMovementVideoId != null

    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            if (isCompleted) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, contentDescription = if (isCompleted) "Completed" else "Mark completed",
            tint = if (isCompleted) FLPrimary else MediumGray, modifier = Modifier.size(24.dp).clickable { viewModel.toggleCompleted(task.id) }
        )
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = hasVideo) { onPlayVideo(task) }, contentAlignment = Alignment.Center) {
            FLImageView(ref = (task.movementThumbnailId ?: task.movementImageId)?.let { MediaRef.MovementThumbnail(it) }, modifier = Modifier.size(50.dp))
            if (hasVideo) Icon(Icons.Filled.PlayCircle, contentDescription = "Play video", tint = White.copy(alpha = 0.95f), modifier = Modifier.size(22.dp))
        }
        if (task.movementId != null) Icon(Icons.Filled.Info, contentDescription = "Movement info", tint = FLPrimary, modifier = Modifier.size(20.dp).padding(top = 2.dp).clickable { onInfo(task) })
        Column(Modifier.weight(1f).clickable { viewModel.openLogSheet(task.id) }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(task.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, textDecoration = if (isCompleted) TextDecoration.LineThrough else null)
            if (!task.isInterval) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(task.displayMetric, fontSize = 13.sp, color = TextSecondaryColor)
                    if (task.rest.isNotEmpty()) Text("• Rest ${task.rest}", fontSize = 13.sp, color = TextSecondaryColor)
                }
            }
            if (states.isNotEmpty()) {
                PlaylistSetStatusRow(states, exerciseState, unit) { viewModel.openLogSheet(task.id, it) }
                val latest = viewModel.latestLoggedSetLine(task.id, unit)
                if (latest != null) Text(latest, fontSize = 13.sp, color = TextSecondaryColor, modifier = Modifier.padding(top = 8.dp))
                else if (viewModel.showsLoggingHint(task.id)) Text("↑ Tap a set to log your weight & reps", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary, modifier = Modifier.padding(top = 10.dp))
            }
            SetBreakdown(task)
            previous?.let { Text(it.previousLine, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FLPrimary) }
            task.instructions?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = TextSecondaryColor, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        if (viewModel.onEditRequest != null && task.isMovement) {
            Box {
                Icon(Icons.Filled.MoreVert, contentDescription = "Edit ${task.name}", tint = MediumGray, modifier = Modifier.size(28.dp).clickable { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (SessionWorkoutEditor.canSwap(task)) DropdownMenuItem(text = { Text("Swap movement") }, onClick = { menu = false; viewModel.requestEdit(PlaylistEditRequest.Swap(task.id)) })
                    if (!isGrouped) {
                        if (SessionWorkoutEditor.canEditSets(task)) DropdownMenuItem(text = { Text("Edit sets & reps") }, onClick = { menu = false; viewModel.requestEdit(PlaylistEditRequest.EditSets(task.id)) })
                        DropdownMenuItem(text = { Text("Add exercise after") }, onClick = { menu = false; viewModel.requestEdit(PlaylistEditRequest.Add(task.id)) })
                        if (SessionWorkoutEditor.canRemove(task)) DropdownMenuItem(text = { Text("Remove exercise", color = Color(0xFFE53935)) }, onClick = { menu = false; viewModel.requestEdit(PlaylistEditRequest.Remove(task.id)) })
                    }
                }
            }
        }
    }
}

/** Per-set breakdown ("Set 1  12 reps  122 lbs") for individually-edited sets. */
@Composable
private fun SetBreakdown(task: WorkoutTask) {
    if (!task.hasPerSetBreakdown) return
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(if (expanded) "Hide sets" else "Show sets", fontSize = 12.sp, color = FLPrimary, modifier = Modifier.clickable { expanded = !expanded })
        if (expanded) task.setConfigs.sortedBy { it.setNumber }.forEach { c ->
            val work = c.durationSeconds?.takeIf { it > 0 }?.let { WorkoutTask.humanizeDuration(it) } ?: "${c.reps} reps"
            val load = c.weightKg?.takeIf { it > 0 }?.let { "  ${com.fitnesslink.fit.model.WeightUnit.displayString(fromLbs = it)}" } ?: ""
            Text("${if (task.groupId.isEmpty()) "Set" else "Round"} ${c.setNumber}  $work$load", fontSize = 12.sp, color = TextSecondaryColor)
        }
    }
}

/**
 * One ring per set / round (FTNSSLNK-444): filled check once done, the next set carries a `+` with a
 * pulsing halo, Skipped / Incomplete tag once the exercise resolves that way, and long lists
 * collapse to "3 of 8 ✓".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistSetStatusRow(states: List<SetState>, exerciseState: ExerciseState, unit: String = "Set", glyphLimit: Int = 12, onTapSet: ((Int) -> Unit)? = null) {
    val transition = rememberInfiniteTransition(label = "halo")
    val pulse by transition.animateFloat(1f, 1.2f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "halo")
    val nextIndex = states.indexOfFirst { it == SetState.UPCOMING || it == SetState.ACTIVE }.takeIf { it >= 0 }
    val ringSize = if (onTapSet == null) 20.dp else 32.dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) {
        if (states.size <= glyphLimit) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(if (onTapSet == null) 4.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                states.forEachIndexed { index, state ->
                    val isNext = index == nextIndex && onTapSet != null
                    val color = if (isNext) FLPrimary else stateColor(state)
                    val filled = state == SetState.COMPLETED
                    Box(
                        Modifier.size(ringSize).clickable(enabled = onTapSet != null) { onTapSet?.invoke(index + 1) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isNext) Box(Modifier.size(ringSize).scale(pulse).clip(CircleShape).border(4.dp, FLPrimary.copy(alpha = 0.25f), CircleShape))
                        Box(Modifier.size(ringSize).clip(CircleShape).background(if (filled) color else Color.Transparent).border(1.5.dp, color, CircleShape), contentAlignment = Alignment.Center) {
                            val icon = when (state) {
                                SetState.UPCOMING -> if (isNext) Icons.Filled.Add else null
                                SetState.ACTIVE -> if (isNext) Icons.Filled.Add else Icons.Filled.PlayArrow
                                SetState.COMPLETED -> Icons.Filled.Check
                                SetState.SKIPPED -> Icons.Filled.Block
                                SetState.INCOMPLETE -> Icons.Filled.PriorityHigh
                            }
                            icon?.let { Icon(it, contentDescription = "$unit ${index + 1} ${state.rawValue}", tint = if (filled) White else color, modifier = Modifier.size(ringSize * 0.5f)) }
                        }
                    }
                }
            }
        } else {
            val done = states.count { it == SetState.COMPLETED }
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = if (done == states.size) FLPrimary else TextSecondaryColor, modifier = Modifier.size(14.dp))
            Text("$done of ${states.size}", fontSize = 11.sp, color = if (done == states.size) FLPrimary else TextSecondaryColor)
        }
        when (exerciseState) {
            ExerciseState.SKIPPED -> Tag("SKIPPED")
            ExerciseState.INCOMPLETE -> Tag("INCOMPLETE")
            else -> Unit
        }
    }
}

private fun stateColor(state: SetState): Color = when (state) {
    SetState.UPCOMING -> MediumGray
    SetState.ACTIVE, SetState.COMPLETED -> FLPrimary
    SetState.SKIPPED, SetState.INCOMPLETE -> OrangeTheme
}

@Composable
private fun Tag(text: String) {
    Text(text, color = White, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.clip(CircleShape).background(OrangeTheme).padding(horizontal = 6.dp, vertical = 2.dp))
}

/** Standalone rest row. */
@Composable
fun PlaylistRestRow(task: WorkoutTask, tint: Color = FLPrimary) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Filled.Timer, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(if (task.restType == RestType.ACTIVE_RECOVERY) "Active Recovery${task.restMovementName?.let { " · $it" } ?: ""}" else "Rest", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        Spacer(Modifier.weight(1f))
        Text(formatSeconds(task.restSeconds), fontSize = 13.sp, color = TextSecondaryColor)
    }
}

/** Superset / circuit / interval container with badges, rounds, member rest chips, round-rest chip, interval metrics grid and RECOVERY row. */
@Composable
fun PlaylistGroupContainer(row: TaskRow, viewModel: PlaylistViewModel, onPlayVideo: (WorkoutTask) -> Unit, onInfo: (WorkoutTask) -> Unit) {
    val isInterval = (row.task?.isInterval ?: false) || row.isInterval
    val movementCount = (if (row.task?.isMovement == true) 1 else 0) + row.advancedTasks.count { it.isMovement }
    val flaggedSuperset = (row.task?.isSuperset ?: false) || row.isSuperset
    val flaggedCircuit = (row.task?.isCircuit ?: false) || row.isCircuit
    val isSuperset = !isInterval && (flaggedSuperset || (!flaggedCircuit && movementCount == 2))
    val isCircuit = !isInterval && (flaggedCircuit || (!flaggedSuperset && movementCount >= 3))
    val badgeColor = when { isSuperset -> PurpleTheme; isCircuit -> OrangeTheme; isInterval -> BlueTheme; else -> FLPrimary }
    val badgeText = when { isInterval -> "INTERVAL"; row.advanced.isNotEmpty() -> row.advanced.uppercase(Locale.US); isSuperset -> "SUPERSET"; isCircuit -> "CIRCUIT"; else -> "" }
    val summary = if (isInterval) "" else row.rounds.ifEmpty { maxOf(row.totalRounds, row.task?.totalRounds ?: 0).takeIf { it > 0 }?.let { "$it rounds" } ?: "" }
    val roundRest = row.task?.roundRestSeconds?.takeIf { (isSuperset || isCircuit) && it > 0 }
    val lastMovementId = row.advancedTasks.lastOrNull { it.isMovement }?.id ?: row.task?.takeIf { it.isMovement }?.id
    fun display(t: WorkoutTask) = if (isInterval) t.copy(isInterval = true) else t

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(badgeColor.copy(alpha = 0.07f)).border(1.dp, badgeColor.copy(alpha = 0.22f), RoundedCornerShape(10.dp))) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(badgeColor))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val metrics = if (isInterval) IntervalMetrics.from(row) else null
            if (metrics != null) {
                IntervalMetricsGrid(metrics, badgeColor, Modifier.padding(horizontal = 10.dp).padding(top = 10.dp))
            } else {
                Row(Modifier.padding(horizontal = 10.dp).padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (badgeText.isNotEmpty()) Badge(badgeText, badgeColor)
                    if (summary.isNotEmpty()) Badge(summary, InfoColor)
                    roundRest?.let { Badge("${formatSeconds(it)} rest", InfoColor) }
                }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val lead = row.task
                if (lead != null && lead.isMovement) {
                    PlaylistMovementRow(display(lead), viewModel, isGrouped = true, onPlayVideo, onInfo)
                    MemberRestChip(lead, isSuperset || isCircuit, lastMovementId)
                }
                row.advancedTasks.forEach { task ->
                    if (task.isMovement) {
                        PlaylistMovementRow(display(task), viewModel, isGrouped = true, onPlayVideo, onInfo)
                        MemberRestChip(task, isSuperset || isCircuit, lastMovementId)
                    } else if (task.isRest) PlaylistRestRow(task, badgeColor)
                }
                if (isInterval && lead != null && lead.restType == RestType.ACTIVE_RECOVERY && lead.restMovementId != null) {
                    Row(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Spacer(Modifier.width(22.dp))
                        FLImageView(ref = lead.restMovementThumbnailId?.let { MediaRef.MovementThumbnail(it) }, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)))
                        Text("RECOVERY", color = White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clip(CircleShape).background(OrangeTheme).padding(horizontal = 6.dp, vertical = 2.dp))
                        Text(lead.restMovementName ?: "Recovery", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRestChip(task: WorkoutTask, isGroup: Boolean, lastMovementId: String?) {
    if (!isGroup || task.restSeconds <= 0 || task.id == lastMovementId) return
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Spacer(Modifier.width(22.dp))
        Row(Modifier.clip(RoundedCornerShape(5.dp)).background(InfoColor).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Timer, contentDescription = null, tint = White, modifier = Modifier.size(10.dp))
            Text("Rest ${formatSeconds(task.restSeconds)}", color = White, fontSize = 12.sp)
        }
    }
}

/** Work / Rest / Rounds / Sets / Total tiles from the same math the runner uses. */
@Composable
fun IntervalMetricsGrid(metrics: IntervalMetrics, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Timer, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Badge(metrics.badgeText, accent)
            metrics.recoveryMovementName?.let { Badge("Recovery · $it", InfoColor) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            metrics.tiles.forEach { tile ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(BackgroundColor).padding(vertical = 6.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tile.value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, maxLines = 1)
                    Text(tile.label, fontSize = 9.sp, color = TextSecondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

internal fun formatSeconds(s: Int): String = String.format(Locale.US, "%d:%02d", s / 60, s % 60)
