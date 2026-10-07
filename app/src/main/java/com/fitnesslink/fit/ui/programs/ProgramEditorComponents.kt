package com.fitnesslink.fit.ui.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.ProgramDaySlot
import com.fitnesslink.fit.model.ProgramWeek
import com.fitnesslink.fit.model.WorkoutList
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.ProgramEditorViewModel

// Port of iOS `WeekSectionView`, `DaySlotView` and `WorkoutPickerSheet`.
// Drag-and-drop between slots is replaced by a "Move Workout" menu item
// that opens [MoveSlotSheet]; both paths end in `viewModel.moveWorkout`.

/** One week of the editor: header with count badge + ••• (Duplicate / Delete), then the 7-day grid. */
@Composable
fun WeekSectionView(week: ProgramWeek, viewModel: ProgramEditorViewModel) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Week ${week.weekNumber}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            if (workoutCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(FLPrimary).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("$workoutCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Week options", tint = TextSecondaryColor)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Duplicate Week") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = { showMenu = false; viewModel.duplicateWeek(week.weekNumber) }
                    )
                    if (viewModel.weeks.size > 1) {
                        DropdownMenuItem(
                            text = { Text("Delete Week", color = Color.Red) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                            onClick = { showMenu = false; viewModel.removeWeek(week.weekNumber) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            week.days.forEach { day ->
                Box(modifier = Modifier.weight(1f)) { DaySlotView(slot = day, viewModel = viewModel) }
            }
        }
    }
}

/** One day cell: an assigned workout (Replace / Move / Remove menu) or an empty "+ Rest" slot that opens the picker. */
@Composable
fun DaySlotView(slot: ProgramDaySlot, viewModel: ProgramEditorViewModel) {
    var showMenu by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }

    if (showMove) {
        MoveSlotSheet(
            weeks = viewModel.weeks,
            from = slot,
            onPick = { toWeek, toDay ->
                viewModel.moveWorkout(slot.weekNumber, slot.dayNumber, toWeek, toDay)
                showMove = false
            },
            onDismiss = { showMove = false }
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(slot.dayLabel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
        Spacer(modifier = Modifier.height(4.dp))

        val workout = slot.workout
        if (workout != null || slot.workoutId != null) {
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
                        text = workout?.name ?: "Workout",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        color = TextPrimaryColor
                    )
                    if (!workout?.time.isNullOrEmpty()) {
                        Text(workout!!.time, fontSize = 8.sp, color = TextSecondaryColor)
                    }
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Replace Workout") },
                        leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                        onClick = { showMenu = false; viewModel.openWorkoutPicker(slot.weekNumber, slot.dayNumber) }
                    )
                    DropdownMenuItem(
                        text = { Text("Move Workout") },
                        leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) },
                        onClick = { showMenu = false; showMove = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove Workout", color = Color.Red) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                        onClick = { showMenu = false; viewModel.removeWorkout(slot.weekNumber, slot.dayNumber) }
                    )
                }
            }
        } else {
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
                    Icon(Icons.Default.Add, contentDescription = "Add workout", tint = MediumGray, modifier = Modifier.size(14.dp))
                    Text("Rest", fontSize = 9.sp, color = MediumGray)
                }
            }
        }
    }
}

/**
 * Pick the destination slot for a move (swap). Lists every week with its
 * seven day chips; the source slot is dimmed, occupied slots show a dot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveSlotSheet(weeks: List<ProgramWeek>, from: ProgramDaySlot, onPick: (toWeek: Int, toDay: Int) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = White) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Move Workout", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Text(
                "Choose where ${from.workout?.name ?: "this workout"} should go. If that day already has a workout, the two swap places.",
                fontSize = 13.sp, color = TextSecondaryColor
            )
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                weeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Week ${week.weekNumber}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            week.days.forEach { day ->
                                val isSource = day.weekNumber == from.weekNumber && day.dayNumber == from.dayNumber
                                val occupied = day.workout != null || day.workoutId != null
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSource) FLPrimary.copy(alpha = 0.25f) else if (occupied) FLPrimary.copy(alpha = 0.08f) else BackgroundColor)
                                        .clickable(enabled = !isSource) { onPick(day.weekNumber, day.dayNumber) }
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(day.dayLabel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                                    Text(if (occupied) "•" else " ", fontSize = 12.sp, color = FLPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Workout picker (iOS `WorkoutPickerSheet`): search, "Create New Workout",
 * the "Week N, Day" label and the catalog list. [onCreateNew] opens the
 * workout editor; its `onSaved` assigns the new workout to the slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPickerSheet(viewModel: ProgramEditorViewModel, onCreateNew: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = White) {
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = FLPrimary) }
                Spacer(modifier = Modifier.weight(1f))
                Text("Add Workout", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(72.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))

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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(FLPrimary.copy(alpha = 0.08f))
                    .clickable(onClick = onCreateNew)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
                Text("Create New Workout", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
            }

            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Week ${viewModel.targetWeekNumber}, ${pickerDayLabel(viewModel.targetDayNumber)}",
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FLPrimary
                )
            }

            HorizontalDivider()

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
                            fontSize = 13.sp, color = TextSecondaryColor, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn {
                    items(filtered, key = { it.id }) { workout ->
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(workout.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
            if (workout.time.isNotEmpty()) Text(workout.time, fontSize = 12.sp, color = TextSecondaryColor)
        }
        Icon(Icons.Default.AddCircleOutline, contentDescription = "Add", tint = FLPrimary, modifier = Modifier.size(22.dp))
    }
}

/** Full weekday name for a `ProgramDaySlot.dayNumber` (1 = Sunday … 7 = Saturday). */
fun pickerDayLabel(dayNumber: Int): String = when (dayNumber) {
    1 -> "Sunday"
    2 -> "Monday"
    3 -> "Tuesday"
    4 -> "Wednesday"
    5 -> "Thursday"
    6 -> "Friday"
    7 -> "Saturday"
    else -> ""
}
