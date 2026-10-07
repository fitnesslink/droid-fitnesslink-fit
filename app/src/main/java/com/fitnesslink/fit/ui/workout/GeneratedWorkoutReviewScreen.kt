package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.network.dto.DraftWorkoutTask
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.AIWorkoutGeneratorViewModel
import com.fitnesslink.fit.viewmodel.AIWorkoutGeneratorViewModel.DraftBlockKind
import com.fitnesslink.fit.viewmodel.AIWorkoutGeneratorViewModel.DraftBlockRun
import kotlinx.coroutines.launch
import java.util.Locale

private val LibraryGreen = Color(0xFF22A35A)

/**
 * Read-only review of an AI-generated draft (iOS `GeneratedWorkoutReviewView`).
 * Approve resolves movements and hands an editable `Workout` to [onApprove];
 * nothing is persisted until the editor's Save.
 */
@Composable
fun GeneratedWorkoutReviewScreen(
    aiVM: AIWorkoutGeneratorViewModel,
    onBack: () -> Unit,
    onDiscard: () -> Unit,
    onApprove: (Workout) -> Unit
) {
    val scope = rememberCoroutineScope()
    val draft = aiVM.draft
    val resolutions = aiVM.resolutionsByIndex
    var approving by remember { mutableStateOf(false) }

    val phaseGroups: List<Pair<String, List<DraftWorkoutTask>>> = remember(draft) {
        val order = mutableListOf<String>()
        val byPhase = LinkedHashMap<String, MutableList<DraftWorkoutTask>>()
        draft?.tasks?.forEach { t ->
            val key = t.phase.ifEmpty { "Exercises" }
            if (!byPhase.containsKey(key)) { order.add(key); byPhase[key] = mutableListOf() }
            byPhase[key]!!.add(t)
        }
        order.map { it to (byPhase[it] ?: emptyList()) }
    }
    val exerciseTasks = draft?.tasks?.filter { !AIWorkoutGeneratorViewModel.isRestTask(it) } ?: emptyList()
    val libraryCount = exerciseTasks.count { AIWorkoutGeneratorViewModel.isFromLibrary(resolutions[it.taskIndex]) }
    val newCount = exerciseTasks.size - libraryCount
    val blockCount = phaseGroups.sumOf { AIWorkoutGeneratorViewModel.blockRuns(it.second).size }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        Row(modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, enabled = !approving) { Text("Back", color = TextSecondaryColor) }
            Spacer(modifier = Modifier.weight(1f))
            Text("Review Workout", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(64.dp))
        }

        if (draft == null) {
            Column(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("No workout to review.", color = TextSecondaryColor)
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(draft.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull("${draft.durationMinutes} min", draft.level?.takeIf { it.isNotEmpty() }).joinToString(" · "),
                        fontSize = 14.sp, color = TextSecondaryColor
                    )
                    draft.description?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 14.sp, color = TextSecondaryColor) }
                    Text(
                        "${exerciseTasks.size} exercises · $libraryCount from your library · $newCount new" +
                            (if (blockCount > 0) " · $blockCount block${if (blockCount == 1) "" else "s"}" else ""),
                        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FLPrimary
                    )
                }
                phaseGroups.forEach { (name, tasks) -> PhaseCard(name, tasks, resolutions) }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Discard", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(BackgroundColor)
                    .clickable(enabled = !approving, onClick = onDiscard).padding(vertical = 14.dp)
            )
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(FLPrimary)
                    .clickable(enabled = !approving && draft != null) {
                        approving = true
                        scope.launch {
                            val workout = aiVM.buildWorkout()
                            approving = false
                            if (workout != null) onApprove(workout)
                        }
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                if (approving) {
                    CircularProgressIndicator(color = White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Adding…", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Approve", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PhaseCard(name: String, tasks: List<DraftWorkoutTask>, resolutions: Map<Int, com.fitnesslink.fit.network.dto.MovementResolution>) {
    val runs = AIWorkoutGeneratorViewModel.blockRuns(tasks)
    val runByLead = runs.associateBy { it.taskIndexes.first() }
    val folded = runs.flatMap { it.taskIndexes.drop(1) }.toSet()

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(name.uppercase(Locale.US), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
        tasks.forEach { task ->
            val run = runByLead[task.taskIndex]
            when {
                run != null -> {
                    val members = run.taskIndexes.mapNotNull { idx -> tasks.firstOrNull { it.taskIndex == idx } }
                    Column(
                        modifier = Modifier.fillMaxWidth().border(1.dp, FLPrimary.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(FLPrimary.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(if (run.kind == DraftBlockKind.INTERVAL) Icons.Filled.Timer else Icons.Filled.Link, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(12.dp))
                            Text(run.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                        }
                        members.forEach { TaskRowView(it, run, resolutions[it.taskIndex]) }
                    }
                }
                folded.contains(task.taskIndex) -> Unit
                else -> TaskRowView(task, null, resolutions[task.taskIndex])
            }
        }
    }
}

@Composable
private fun TaskRowView(task: DraftWorkoutTask, run: DraftBlockRun?, resolution: com.fitnesslink.fit.network.dto.MovementResolution?) {
    val isRest = AIWorkoutGeneratorViewModel.isRestTask(task)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (isRest) "Rest" else task.movementName, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(metric(task, run), fontSize = 13.sp, color = TextSecondaryColor)
        }
        if (!isRest) {
            val inLibrary = AIWorkoutGeneratorViewModel.isFromLibrary(resolution)
            val color = if (inLibrary) LibraryGreen else Color(0xFFF69833)
            Text(
                if (inLibrary) "In library" else "New", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

private fun metric(task: DraftWorkoutTask, run: DraftBlockRun?): String {
    if (AIWorkoutGeneratorViewModel.isRestTask(task)) return "${task.restSeconds ?: 60}s"
    val timed = (task.durationSeconds ?: 0) > 0
    if (run != null) {
        return when (run.kind) {
            DraftBlockKind.INTERVAL ->
                if (IntervalMath.usesRepTargets(run.intervalMode)) task.reps?.let { "$it reps per round" } ?: "each round" else "timed work"
            DraftBlockKind.SUPERSET, DraftBlockKind.CIRCUIT ->
                if (timed) "${task.durationSeconds}s" else task.reps?.let { "$it reps" } ?: "1 round"
        }
    }
    val sets = task.sets ?: 3
    var parts = if (timed) "$sets × ${task.durationSeconds}s" else task.reps?.let { "$sets × $it" } ?: "$sets sets"
    task.restSeconds?.takeIf { it > 0 }?.let { parts += " · ${it}s rest" }
    return parts
}
