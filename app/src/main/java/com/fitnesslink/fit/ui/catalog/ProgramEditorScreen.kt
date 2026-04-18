package com.fitnesslink.fit.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.ProgramDaySlot
import com.fitnesslink.fit.model.ProgramWeek
import com.fitnesslink.fit.model.WorkoutList
import com.fitnesslink.fit.ui.theme.*
import com.fitnesslink.fit.viewmodel.ProgramEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramEditorScreen(
    programId: String?,
    onBack: () -> Unit
) {
    val viewModel: ProgramEditorViewModel = viewModel()
    var showDiscardAlert by remember { mutableStateOf(false) }

    LaunchedEffect(programId) {
        if (programId != null) {
            viewModel.loadExistingProgram(programId)
        } else {
            viewModel.createNewProgram()
        }
    }

    // Workout Picker bottom sheet
    if (viewModel.showWorkoutPicker) {
        WorkoutPickerSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.showWorkoutPicker = false }
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
                text = if (viewModel.isNewProgram) "New Program" else "Edit Program",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = {
                viewModel.save()
                onBack()
            }) {
                Text("Save", color = FLPrimary, fontWeight = FontWeight.SemiBold)
            }
        }

        // Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 100.dp)
        ) {
            // Program metadata
            ProgramHeaderSection(viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // Weeks
            viewModel.weeks.forEach { week ->
                WeekSection(week = week, viewModel = viewModel)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Add Week button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(FLPrimary.copy(alpha = 0.08f))
                    .clickable { viewModel.addWeek() }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Week", color = FLPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Summary
            if (viewModel.assignedWorkoutCount > 0) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SummaryChip(icon = Icons.Default.DateRange, label = "${viewModel.weeks.size} week${if (viewModel.weeks.size == 1) "" else "s"}")
                    SummaryChip(icon = Icons.Default.FitnessCenter, label = "${viewModel.assignedWorkoutCount} workout${if (viewModel.assignedWorkoutCount == 1) "" else "s"}")
                }
            }
        }
    }
}

// MARK: - Program Header

@Composable
private fun ProgramHeaderSection(viewModel: ProgramEditorViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = viewModel.program.name,
            onValueChange = {
                viewModel.program = viewModel.program.copy(name = it)
                viewModel.isDirty = true
            },
            placeholder = { Text("Program Name", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Time chips
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Estimated Time per Workout", fontSize = 13.sp, color = TextSecondaryColor)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(15, 30, 45, 60, 90).forEach { minutes ->
                val isSelected = viewModel.program.time == "$minutes min"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) FLPrimary else BackgroundColor)
                        .clickable {
                            viewModel.program = viewModel.program.copy(time = "$minutes min")
                            viewModel.isDirty = true
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text("$minutes", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (isSelected) White else Color.Black)
                }
            }
            Text("min", fontSize = 12.sp, color = TextSecondaryColor)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Training level
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Speed, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Training Level", fontSize = 13.sp, color = TextSecondaryColor)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Beginner", "Intermediate", "Specialized", "Advanced").forEach { level ->
                val isSelected = viewModel.program.trainingLevel == level
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) FLPrimary else BackgroundColor)
                        .clickable {
                            viewModel.program = viewModel.program.copy(trainingLevel = level)
                            viewModel.isDirty = true
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(level, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (isSelected) White else Color.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = viewModel.program.description,
            onValueChange = {
                viewModel.program = viewModel.program.copy(description = it)
                viewModel.isDirty = true
            },
            placeholder = { Text("Description (optional)") },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = TextSecondaryColor),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent
            ),
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// MARK: - Week Section

@Composable
private fun WeekSection(week: ProgramWeek, viewModel: ProgramEditorViewModel) {
    var showMenu by remember { mutableStateOf(false) }
    val workoutCount = week.days.count { it.workout != null }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .padding(12.dp)
    ) {
        // Week header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Week ${week.weekNumber}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (workoutCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(FLPrimary)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("$workoutCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextSecondaryColor)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Duplicate Week") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            viewModel.duplicateWeek(week.weekNumber)
                            showMenu = false
                        }
                    )
                    if (viewModel.weeks.size > 1) {
                        DropdownMenuItem(
                            text = { Text("Delete Week", color = Color.Red) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                            onClick = {
                                viewModel.removeWeek(week.weekNumber)
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 7-day grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            week.days.forEach { day ->
                Box(modifier = Modifier.weight(1f)) {
                    DaySlotView(slot = day, viewModel = viewModel)
                }
            }
        }
    }
}

// MARK: - Day Slot

@Composable
private fun DaySlotView(slot: ProgramDaySlot, viewModel: ProgramEditorViewModel) {
    var showMenu by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(slot.dayLabel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
        Spacer(modifier = Modifier.height(4.dp))

        if (slot.workout != null) {
            // Assigned workout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(FLPrimary.copy(alpha = 0.1f))
                    .border(1.dp, FLPrimary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .clickable { showMenu = true }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = slot.workout.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    if (slot.workout.time.isNotEmpty()) {
                        Text(slot.workout.time, fontSize = 8.sp, color = TextSecondaryColor)
                    }
                }

                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Replace Workout") },
                        leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            viewModel.openWorkoutPicker(slot.weekNumber, slot.dayNumber)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove Workout", color = Color.Red) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                        onClick = {
                            showMenu = false
                            viewModel.removeWorkout(slot.weekNumber, slot.dayNumber)
                        }
                    )
                }
            }
        } else {
            // Empty day - Rest / Add
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BackgroundColor)
                    .clickable { viewModel.openWorkoutPicker(slot.weekNumber, slot.dayNumber) }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = MediumGray, modifier = Modifier.size(14.dp))
                    Text("Rest", fontSize = 9.sp, color = MediumGray)
                }
            }
        }
    }
}

// MARK: - Workout Picker Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutPickerSheet(
    viewModel: ProgramEditorViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = White
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            // Title
            Text(
                "Add Workout",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Search
            OutlinedTextField(
                value = viewModel.workoutSearchText,
                onValueChange = { viewModel.workoutSearchText = it },
                placeholder = { Text("Search workouts...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MediumGray) },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedContainerColor = BackgroundColor,
                    focusedContainerColor = BackgroundColor
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Day label
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Week ${viewModel.targetWeekNumber}, ${dayLabel(viewModel.targetDayNumber)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = FLPrimary
                )
            }

            HorizontalDivider()

            // Workout list
            val filtered = viewModel.filteredWorkouts
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = MediumGray, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No workouts found", fontSize = 15.sp, color = TextSecondaryColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Create workouts first, then assign them to your program.",
                            fontSize = 13.sp,
                            color = TextSecondaryColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn {
                    items(filtered) { workout ->
                        WorkoutPickerItem(workout = workout) {
                            viewModel.assignWorkout(workout, viewModel.targetWeekNumber, viewModel.targetDayNumber)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutPickerItem(workout: WorkoutList, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(workout.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (workout.time.isNotEmpty()) {
                Text(workout.time, fontSize = 12.sp, color = TextSecondaryColor)
            }
        }
        Icon(Icons.Default.AddCircleOutline, contentDescription = "Add", tint = FLPrimary, modifier = Modifier.size(22.dp))
    }
}

// MARK: - Summary Chip

@Composable
private fun SummaryChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(FLPrimary.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
    }
}

// MARK: - Helpers

private fun dayLabel(dayNumber: Int): String = when (dayNumber) {
    1 -> "Monday"
    2 -> "Tuesday"
    3 -> "Wednesday"
    4 -> "Thursday"
    5 -> "Friday"
    6 -> "Saturday"
    7 -> "Sunday"
    else -> ""
}
