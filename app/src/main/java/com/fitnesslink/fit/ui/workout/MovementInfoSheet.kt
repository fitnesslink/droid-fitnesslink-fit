package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.model.api.Movement
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.session.MuxVideoPlayer
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Movement-info modal (iOS `MovementInfoSheet`): hero (thumbnail + Watch
 * Demo), title + prescribed metric, anatomy / equipment chips, the
 * prescription quick-stats, the task's coaching cue, and the structured
 * Setup / Execution / Breathe / Tips content. Driven off a `WorkoutTask`;
 * the full `Movement` is fetched on open.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MovementInfoSheet(task: WorkoutTask, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var movement by remember(task.movementId) { mutableStateOf<Movement?>(null) }
    var showVideo by remember(task.id) { mutableStateOf(false) }

    LaunchedEffect(task.movementId) {
        val id = task.movementId?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        if (!NetworkMonitor.isConnected.value) return@LaunchedEffect
        movement = withContext(Dispatchers.IO) { runCatching { ApiClient.movementApi.get(id) }.getOrNull() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BackgroundColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Done", color = FLPrimary, fontWeight = FontWeight.SemiBold) }
            }

            // Hero
            val videoId = task.inlineMovementVideoId
            if (showVideo && videoId != null) {
                MuxVideoPlayer(
                    ref = MediaRef.MovementVideo(videoId),
                    url = task.inlineMovementVideoUrl,
                    fill = false,
                    muted = true,
                    showControls = true,
                    videoStatus = task.inlineMovementVideoStatus?.let { com.fitnesslink.fit.media.MuxStatus.fromWire(it.wire) },
                    posterRef = task.movementThumbnailId?.let { MediaRef.MovementThumbnail(it) },
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))) {
                    FLImageView(
                        ref = (task.movementThumbnailId ?: task.movementImageId)?.let { MediaRef.MovementThumbnail(it) },
                        height = 220.dp
                    )
                    if (task.effectiveMovementVideoId != null) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(FLPrimary)
                                .clickable { showVideo = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = White, modifier = Modifier.size(14.dp))
                            Text("Watch Demo", color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Title
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = task.name.ifEmpty { "Movement" }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (!task.isInterval && task.displayMetric.isNotEmpty()) {
                    Text(text = task.displayMetric, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                }
            }

            // Chips
            val anatomyNames = movement?.anatomyIds?.mapNotNull { ClassificationStore.anatomyName(it.toString()) } ?: emptyList()
            val equipmentNames = movement?.equipmentIds?.mapNotNull { ClassificationStore.equipmentName(it.toString()) } ?: emptyList()
            if (anatomyNames.isNotEmpty() || equipmentNames.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    anatomyNames.forEach { ContentTagChip(name = it, icon = Icons.Outlined.Accessibility) }
                    equipmentNames.forEach { ContentTagChip(name = it, icon = Icons.Filled.FitnessCenter) }
                }
            }

            // Quick stats
            val stats = buildStats(task)
            if (stats.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 2
                ) {
                    stats.forEach { (label, value) ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(White, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(text = label.uppercase(Locale.US), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (task.hasPerSetBreakdown) SetBreakdownView(task = task, collapseThreshold = Int.MAX_VALUE)

            // Coaching cue
            val cue = task.instructions
            if (!cue.isNullOrEmpty()) {
                HorizontalDivider()
                Text("Instructions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(cue, fontSize = 15.sp, color = TextPrimaryColor)
            }

            // Structured content
            val summary = movement?.summary
            val sections = movement?.sections ?: emptyList()
            if (!summary.isNullOrEmpty() || sections.isNotEmpty()) {
                HorizontalDivider()
                if (!summary.isNullOrEmpty()) Text(summary, fontSize = 14.sp, color = TextPrimaryColor)
                sections.sortedBy { it.ordinal }.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(section.type, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        section.note?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, color = TextSecondaryColor) }
                        section.steps.sortedBy { it.ordinal }.forEachIndexed { i, step ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = step.label?.takeIf { it.isNotBlank() } ?: "${i + 1}.",
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary
                                )
                                Text(step.text, fontSize = 14.sp, color = TextPrimaryColor)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun buildStats(task: WorkoutTask): List<Pair<String, String>> {
    val s = mutableListOf<Pair<String, String>>()
    if (task.sets > 0) s.add("Sets" to "${task.sets}")
    val secs = task.durationSeconds
    if (task.usesDuration && secs != null && secs > 0) s.add("Duration" to WorkoutTask.humanizeDuration(secs))
    else if (task.reps > 0) s.add("Reps" to "${task.reps}")
    if (task.rest.isNotEmpty()) s.add("Rest" to task.rest)
    task.weightSummary?.let { s.add("Weight" to it) }
    if (task.totalRounds > 0) s.add("Rounds" to "${task.totalRounds}")
    val rrs = task.roundRestSeconds
    if ((task.isSuperset || task.isCircuit) && rrs != null && rrs > 0) {
        s.add("Round Rest" to String.format(Locale.US, "%d:%02d", rrs / 60, rrs % 60))
    }
    return s
}
