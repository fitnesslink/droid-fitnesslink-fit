package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.ImagePlaceholder
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.WorkoutEditorViewModel
import com.fitnesslink.fit.workouts.WorkoutRowOps

/**
 * Loose exercise row in the editor (iOS `MovementEditorRow`): reorder
 * arrows (or the multi-select checkbox), thumbnail, name + summary, the
 * Link button (enter multi-select for grouping), expand / collapse into the
 * inline set editor, and the Replace / Move to phase / Remove menu.
 */
@Composable
fun MovementEditorRow(task: WorkoutTask, phaseIndex: Int, isSelected: Boolean, viewModel: WorkoutEditorViewModel) {
    val taskId = task.id
    val isExpanded = viewModel.expandedTaskId == taskId
    var showMenu by remember(taskId) { mutableStateOf(false) }
    var showMoveMenu by remember(taskId) { mutableStateOf(false) }
    val otherPhases = viewModel.workout.phases.withIndex().filter { it.index != phaseIndex }

    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(White)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (viewModel.isMultiSelectMode) {
                Icon(
                    if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = "Select", tint = if (isSelected) FLPrimary else MediumGray,
                    modifier = Modifier.size(22.dp).clickable { viewModel.toggleTaskSelection(taskId) }
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(22.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up", tint = MediumGray,
                        modifier = Modifier.size(18.dp).clickable { viewModel.moveExerciseUp(taskId, phaseIndex) })
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down", tint = MediumGray,
                        modifier = Modifier.size(18.dp).clickable { viewModel.moveExerciseDown(taskId, phaseIndex) })
                }
            }

            val thumbId = task.movementThumbnailId ?: task.movementImageId
            if (thumbId != null) {
                FLImageView(ref = MediaRef.MovementThumbnail(thumbId), modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
            } else {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(ImagePlaceholder))
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(task.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(summaryText(task), fontSize = 12.sp, color = TextSecondaryColor)
            }

            if (!viewModel.isMultiSelectMode) {
                Icon(Icons.Filled.Link, contentDescription = "Group with others", tint = MediumGray,
                    modifier = Modifier.size(22.dp).clickable { viewModel.enterMultiSelect(taskId) })
            }
            Icon(
                if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand", tint = MediumGray,
                modifier = Modifier.size(22.dp).clickable { viewModel.expandedTaskId = if (isExpanded) null else taskId }
            )
            Box {
                Icon(Icons.Filled.MoreHoriz, contentDescription = "Row actions", tint = MediumGray,
                    modifier = Modifier.size(22.dp).clickable { showMenu = true })
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Replace exercise") }, onClick = { showMenu = false; viewModel.replaceExerciseTaskId = taskId })
                    if (otherPhases.isNotEmpty()) {
                        DropdownMenuItem(text = { Text("Move to phase…") }, onClick = { showMenu = false; showMoveMenu = true })
                    }
                    DropdownMenuItem(text = { Text("Remove", color = Color.Red) }, onClick = { showMenu = false; viewModel.removeExercise(taskId, phaseIndex) })
                }
                DropdownMenu(expanded = showMoveMenu, onDismissRequest = { showMoveMenu = false }) {
                    otherPhases.forEach { (index, phase) ->
                        DropdownMenuItem(text = { Text(phase.name) }, onClick = { showMoveMenu = false; viewModel.moveRow(taskId, index) })
                    }
                }
            }
        }

        if (isExpanded) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
            Box(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                SetEditorView(task = task, viewModel = viewModel)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { viewModel.removeExercise(taskId, phaseIndex) }
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(14.dp))
                    Text("Remove", fontSize = 13.sp, color = Color.Red)
                }
            }
        }
    }
}

private fun summaryText(task: WorkoutTask): String {
    if (task.sameAcrossSets) {
        val w = task.weightKg?.takeIf { it > 0 }?.let { " @ ${WeightUnit.displayString(fromLbs = it.toDouble())}" } ?: ""
        val value = if (task.usesDuration) WorkoutRowOps.formatDuration(task.durationSeconds ?: 0) else "${task.reps}"
        return "${task.sets} x $value$w"
    }
    val drops = task.setConfigs.count { it.isDropSet }
    return if (drops > 0) "${task.setConfigs.size} sets ($drops drop)" else "${task.setConfigs.size} sets (varied)"
}
