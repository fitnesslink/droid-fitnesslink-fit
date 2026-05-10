package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.ExerciseGroupType
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.ui.theme.*
import com.fitnesslink.fit.viewmodel.WorkoutEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditorScreen(
    workoutId: String?,
    onBack: () -> Unit
) {
    val viewModel: WorkoutEditorViewModel = viewModel()
    var showDiscardAlert by remember { mutableStateOf(false) }
    var showAddPhase by remember { mutableStateOf(false) }
    var newPhaseName by remember { mutableStateOf("") }

    LaunchedEffect(workoutId) {
        if (workoutId != null) {
            viewModel.loadExistingWorkout(workoutId)
        } else {
            viewModel.createNewWorkout()
        }
    }

    // Exercise Browser bottom sheet
    if (viewModel.showExerciseBrowser) {
        ExerciseBrowserSheet(
            onSelect = { movement ->
                viewModel.addExercise(movement, viewModel.activePhaseIndex)
            },
            onCreateNew = {
                viewModel.showExerciseBrowser = false
                viewModel.showCreateExercise = true
            },
            onDismiss = {
                viewModel.showExerciseBrowser = false
                viewModel.finalizePendingGroup()
            },
            pendingGroupType = viewModel.pendingGroupType,
            pendingCount = viewModel.pendingGroupTaskIds.size
        )
    }

    // Create Exercise dialog
    if (viewModel.showCreateExercise) {
        CreateExerciseSheet(
            onCreated = { movement ->
                viewModel.addExercise(movement, viewModel.activePhaseIndex)
                viewModel.showCreateExercise = false
            },
            onDismiss = { viewModel.showCreateExercise = false }
        )
    }

    // Discard alert
    if (showDiscardAlert) {
        AlertDialog(
            onDismissRequest = { showDiscardAlert = false },
            title = { Text("Discard Changes?") },
            text = { Text("You have unsaved changes that will be lost.") },
            confirmButton = {
                TextButton(onClick = { showDiscardAlert = false; onBack() }) {
                    Text("Discard", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardAlert = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }

    // Add Phase dialog
    if (showAddPhase) {
        AlertDialog(
            onDismissRequest = { showAddPhase = false; newPhaseName = "" },
            title = { Text("Add Phase") },
            text = {
                OutlinedTextField(
                    value = newPhaseName,
                    onValueChange = { newPhaseName = it },
                    label = { Text("Phase Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPhaseName.isNotEmpty()) {
                        viewModel.addPhase(newPhaseName)
                        newPhaseName = ""
                    }
                    showAddPhase = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddPhase = false; newPhaseName = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(White)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (viewModel.isDirty) showDiscardAlert = true else onBack()
            }) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (viewModel.isNewWorkout) "New Workout" else "Edit Workout",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { viewModel.save(); onBack() }) {
                Text("Save", color = FLPrimary, fontWeight = FontWeight.SemiBold)
            }
        }

        // Content
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Workout header
            item {
                WorkoutHeaderSection(viewModel)
            }

            // Phases
            itemsIndexed(viewModel.workout.phases) { index, phase ->
                PhaseSection(phase, index, viewModel)
            }

            // Add phase button
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(FLPrimary.copy(alpha = 0.08f))
                        .clickable { showAddPhase = true }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Phase", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                }
            }
        }

        // Multi-select action bar
        if (viewModel.isMultiSelectMode) {
            MultiSelectBar(viewModel)
        }
    }
}

@Composable
private fun WorkoutHeaderSection(viewModel: WorkoutEditorViewModel) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Image placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BackgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MediumGray, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Add Workout Image", fontSize = 13.sp, color = MediumGray)
            }
        }

        // Name
        OutlinedTextField(
            value = viewModel.workout.name,
            onValueChange = {
                viewModel.workout = viewModel.workout.copy(name = it)
                viewModel.isDirty = true
            },
            placeholder = { Text("Workout Name") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = FLPrimary
            )
        )

        // Estimated Time
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Estimated Time", fontSize = 13.sp, color = TextSecondaryColor)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(15, 30, 45, 60, 90).forEach { minutes ->
                    val isSelected = viewModel.workout.time == "$minutes min"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) FLPrimary else BackgroundColor)
                            .clickable {
                                viewModel.workout = viewModel.workout.copy(time = "$minutes min")
                                viewModel.isDirty = true
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("$minutes", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = if (isSelected) White else TextPrimaryColor)
                    }
                }
                Text("min", fontSize = 12.sp, color = TextSecondaryColor)
            }
        }

        // Training Level
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Training Level", fontSize = 13.sp, color = TextSecondaryColor)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Beginner", "Intermediate", "Specialized", "Advanced").forEach { level ->
                    val isSelected = viewModel.workout.trainingLevel == level
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) FLPrimary else BackgroundColor)
                            .clickable {
                                viewModel.workout = viewModel.workout.copy(trainingLevel = level)
                                viewModel.isDirty = true
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(level, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = if (isSelected) White else TextPrimaryColor)
                    }
                }
            }
        }

        // Description
        OutlinedTextField(
            value = viewModel.workout.description,
            onValueChange = {
                viewModel.workout = viewModel.workout.copy(description = it)
                viewModel.isDirty = true
            },
            placeholder = { Text("Description (optional)") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = TextSecondaryColor),
            minLines = 2,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = FLPrimary
            )
        )
    }
}

@Composable
private fun PhaseSection(phase: WorkoutPhase, index: Int, viewModel: WorkoutEditorViewModel) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        // Phase header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(phase.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FLPrimary)

            val exerciseCount = phase.taskRows.mapNotNull { it.task }.count { it.isMovement }
            if (exerciseCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(FLPrimary)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("$exerciseCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (phase.taskRows.isEmpty()) {
                IconButton(onClick = { viewModel.deletePhase(index) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MediumGray, modifier = Modifier.size(14.dp))
                }
            }
        }

        // Task rows
        phase.taskRows.forEachIndexed { _, row ->
            val task = row.task ?: return@forEachIndexed
            if (row.advancedTasks.isNotEmpty()) {
                GroupEditorRow(row, index, viewModel)
            } else if (task.isMovement) {
                MovementEditorRow(task, index, viewModel)
            } else if (task.isRest) {
                RestEditorRow(task, index, viewModel)
            }
        }

        // Add exercise button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(FLPrimary.copy(alpha = 0.06f))
                .clickable {
                    viewModel.activePhaseIndex = index
                    viewModel.showExerciseBrowser = true
                }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Exercise", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
        }

        // Group buttons
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GroupAddButton("Superset", PurpleTheme) {
                viewModel.activePhaseIndex = index
                viewModel.pendingGroupType = ExerciseGroupType.Superset
                viewModel.showExerciseBrowser = true
            }
            GroupAddButton("Circuit", OrangeTheme) {
                viewModel.activePhaseIndex = index
                viewModel.pendingGroupType = ExerciseGroupType.Circuit
                viewModel.showExerciseBrowser = true
            }
            GroupAddButton("Interval", BlueTheme) {
                viewModel.activePhaseIndex = index
                viewModel.pendingGroupType = ExerciseGroupType.Interval
                viewModel.showExerciseBrowser = true
            }
        }
    }
}

@Composable
private fun MovementEditorRow(task: WorkoutTask, phaseIndex: Int, viewModel: WorkoutEditorViewModel) {
    val isSelected = viewModel.selectedTaskIds.contains(task.id)
    var showSetDialog by remember(task.id) { mutableStateOf(false) }

    if (showSetDialog) {
        SetEditorDialog(
            initialSets = task.sets.coerceAtLeast(1),
            initialReps = task.reps.coerceAtLeast(1),
            initialWeightKg = task.weightKg?.toDouble(),
            onConfirm = { sets, reps, weightKg ->
                viewModel.updateTaskSets(task.id, sets, reps, weightKg)
                showSetDialog = false
            },
            onDismiss = { showSetDialog = false }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(White)
            .then(
                if (isSelected) Modifier.border(2.dp, FLPrimary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable {
                if (viewModel.isMultiSelectMode) {
                    viewModel.toggleTaskSelection(task.id)
                } else {
                    viewModel.isMultiSelectMode = true
                    viewModel.toggleTaskSelection(task.id)
                }
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag handle
        Icon(Icons.Default.DragHandle, contentDescription = null, tint = MediumGray, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { if (!viewModel.isMultiSelectMode) showSetDialog = true }
        ) {
            Text(task.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                text = task.metric.ifBlank { "Tap to set reps · sets · weight" },
                fontSize = 12.sp,
                color = if (task.metric.isBlank()) FLPrimary else TextSecondaryColor
            )
        }

        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { viewModel.moveExerciseUp(task.id, phaseIndex) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = { viewModel.moveExerciseDown(task.id, phaseIndex) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = { viewModel.removeExercise(task.id, phaseIndex) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun RestEditorRow(task: WorkoutTask, phaseIndex: Int, viewModel: WorkoutEditorViewModel) {
    var showRestDialog by remember(task.id) { mutableStateOf(false) }

    if (showRestDialog) {
        RestEditorDialog(
            initialSeconds = task.restSeconds.coerceAtLeast(0),
            onConfirm = { seconds ->
                viewModel.updateRest(task.id, seconds)
                showRestDialog = false
            },
            onDismiss = { showRestDialog = false }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(BackgroundColor)
            .clickable { showRestDialog = true }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Timer, contentDescription = null, tint = MediumGray, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Rest", fontSize = 13.sp, color = TextSecondaryColor)
        Spacer(modifier = Modifier.width(4.dp))
        Text(task.rest.ifEmpty { "${task.restSeconds}s" }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = { viewModel.removeRest(task.id, phaseIndex) }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Remove", tint = MediumGray, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun GroupEditorRow(row: TaskRow, phaseIndex: Int, viewModel: WorkoutEditorViewModel) {
    val groupColor = when {
        row.isSuperset -> PurpleTheme
        row.isCircuit -> OrangeTheme
        else -> BlueTheme
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, groupColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .background(groupColor.copy(alpha = 0.04f))
            .padding(12.dp)
    ) {
        // Group header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.advanced, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = groupColor)
            if (row.rounds.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(row.rounds, fontSize = 12.sp, color = TextSecondaryColor)
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { row.task?.id?.let { viewModel.ungroupExercise(it, phaseIndex) } },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                Text("Ungroup", fontSize = 11.sp, color = groupColor)
            }
        }

        // Primary exercise
        row.task?.let { task ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(White)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(task.metric, fontSize = 12.sp, color = TextSecondaryColor)
                }
            }
        }

        // Secondary exercises
        row.advancedTasks.forEach { task ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(White)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(task.metric, fontSize = 12.sp, color = TextSecondaryColor)
                }
            }
        }
    }
}

@Composable
private fun GroupAddButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun MultiSelectBar(viewModel: WorkoutEditorViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(White)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExerciseGroupType.entries.forEach { type ->
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(FLPrimary)
                    .clickable {
                        for ((i, phase) in viewModel.workout.phases.withIndex()) {
                            val ids = phase.taskRows.mapNotNull { it.task?.id }.toSet()
                            if (viewModel.selectedTaskIds.any { ids.contains(it) }) {
                                viewModel.createGroup(type, i)
                                break
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(type.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = White)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = { viewModel.cancelMultiSelect() }) {
            Text("Cancel", fontSize = 14.sp, color = TextSecondaryColor)
        }
    }
}

@Composable
private fun SetEditorDialog(
    initialSets: Int,
    initialReps: Int,
    initialWeightKg: Double?,
    onConfirm: (sets: Int, reps: Int, weightKg: Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var setsText by remember { mutableStateOf(initialSets.toString()) }
    var repsText by remember { mutableStateOf(initialReps.toString()) }
    var weightText by remember {
        mutableStateOf(
            initialWeightKg?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = setsText,
                        onValueChange = { setsText = it.filter(Char::isDigit) },
                        label = { Text("Sets") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = repsText,
                        onValueChange = { repsText = it.filter(Char::isDigit) },
                        label = { Text("Reps") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Weight (kg, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Sets share these values for now — per-set customization is coming.",
                    fontSize = 11.sp,
                    color = TextSecondaryColor
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val sets = setsText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val reps = repsText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val weight = weightText.toDoubleOrNull()
                onConfirm(sets, reps, weight)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RestEditorDialog(
    initialSeconds: Int,
    onConfirm: (seconds: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var secondsText by remember { mutableStateOf(initialSeconds.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rest duration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = secondsText,
                    onValueChange = { secondsText = it.filter(Char::isDigit) },
                    label = { Text("Seconds") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 60, 90, 120).forEach { secs ->
                        TextButton(onClick = { secondsText = secs.toString() }) {
                            Text("${secs}s", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val seconds = secondsText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                onConfirm(seconds)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
