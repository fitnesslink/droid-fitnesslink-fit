package com.fitnesslink.fit.ui.calendar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.CalendarCell
import com.fitnesslink.fit.model.CalendarHeaderCell
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.model.WorkoutList
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.components.HeaderView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.CalendarLine
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.CalendarViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CalendarScreen(
    onNavigateToWorkoutDetail: (String) -> Unit = {}
) {
    val viewModel: CalendarViewModel = viewModel()

    LaunchedEffect(Unit) { viewModel.loadData() }

    // Workout picker sheet
    if (viewModel.showWorkoutPicker) {
        CalendarWorkoutPickerSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.showWorkoutPicker = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderView()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            MonthCalendarView(
                cells = viewModel.calendarCells,
                onDayTapped = { day -> viewModel.selectDate(day) },
                onAddTapped = { viewModel.showWorkoutPicker = true }
            )
            Spacer(modifier = Modifier.height(16.dp))
            ScheduledContentView(
                content = viewModel.filteredContent,
                onNavigateToWorkoutDetail = onNavigateToWorkoutDetail,
                onDelete = { id -> viewModel.deleteEntry(id) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun MonthCalendarView(
    cells: List<CalendarCell>,
    onDayTapped: (Int) -> Unit = {},
    onAddTapped: () -> Unit = {}
) {
    val headerItems = listOf(
        CalendarHeaderCell(1, "S"), CalendarHeaderCell(2, "M"),
        CalendarHeaderCell(3, "T"), CalendarHeaderCell(4, "W"),
        CalendarHeaderCell(5, "T"), CalendarHeaderCell(6, "F"),
        CalendarHeaderCell(7, "S")
    )

    val monthYear = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date())

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { }
            ) {
                Text(
                    text = monthYear,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FLPrimary
                )
                Image(
                    painter = painterResource(R.drawable.downarrow),
                    contentDescription = "Select Month",
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Image(
                painter = painterResource(R.drawable.greenplus),
                contentDescription = "Add",
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onAddTapped() }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            headerItems.forEach { day ->
                Text(
                    text = day.name,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .height(1.dp)
                .background(CalendarLine)
        )

        val rows = cells.chunked(7)
        rows.forEach { rowCells ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                rowCells.forEach { cell ->
                    Box(modifier = Modifier.weight(1f)) {
                        CalendarCellView(
                            cell = cell,
                            onClick = { if (cell.dayNumber > 0) onDayTapped(cell.dayNumber) }
                        )
                    }
                }
                repeat(7 - rowCells.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun CalendarCellView(cell: CalendarCell, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .height(70.dp)
            .clickable(onClick = onClick)
            .padding(top = 15.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (cell.selected) Modifier
                        .clip(CircleShape)
                        .background(FLPrimary)
                    else Modifier
                )
        ) {
            Text(
                text = cell.name,
                fontSize = 18.sp,
                color = if (cell.selected) White else Color.Unspecified
            )
        }
        if (cell.status == "scheduled" || cell.status == "completed") {
            Spacer(modifier = Modifier.height(4.dp))
            Image(
                painter = painterResource(
                    if (cell.status == "completed") R.drawable.completedstate else R.drawable.scheduledstate
                ),
                contentDescription = cell.status,
                modifier = Modifier.size(10.dp)
            )
        }
    }
}

@Composable
fun ScheduledContentView(
    content: List<FitnessContent>,
    onNavigateToWorkoutDetail: (String) -> Unit,
    onDelete: (String) -> Unit = {}
) {
    Column {
        Text(
            text = "Scheduled",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (content.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MediumGray, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No workouts scheduled", fontSize = 14.sp, color = TextSecondaryColor)
                }
            }
        } else {
            content.forEach { item ->
                DateContentView(
                    content = item,
                    onNavigateToWorkoutDetail = onNavigateToWorkoutDetail,
                    onDelete = { onDelete(item.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun DateContentView(
    content: FitnessContent,
    onNavigateToWorkoutDetail: (String) -> Unit,
    onDelete: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(White, RoundedCornerShape(12.dp))
            .clickable { showMenu = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    if (content.status == "completed") FLPrimary else FLPrimary.copy(alpha = 0.5f),
                    CircleShape
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = content.title,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        if (content.workoutId.isNotEmpty()) {
            Image(
                painter = painterResource(R.drawable.rightarrow),
                contentDescription = "View",
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onNavigateToWorkoutDetail(content.workoutId) }
            )
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Remove from Calendar", color = Color.Red) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
        }
    }
}

// MARK: - Workout Picker Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarWorkoutPickerSheet(
    viewModel: CalendarViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var workouts by remember { mutableStateOf<List<WorkoutList>>(emptyList()) }
    var searchText by remember { mutableStateOf("") }
    var selectedWorkout by remember { mutableStateOf<WorkoutList?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        workouts = DatabaseManager.allWorkouts()
    }

    val filtered = if (searchText.isEmpty()) workouts
    else workouts.filter { it.name.contains(searchText, ignoreCase = true) }

    // Date picker dialog
    if (showDatePicker && selectedWorkout != null) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null && selectedWorkout != null) {
                        viewModel.scheduleWorkout(selectedWorkout!!.id, selectedWorkout!!.name, millis)
                        showDatePicker = false
                        onDismiss()
                    }
                }) { Text("Schedule") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = White
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            Text(
                "Schedule Workout",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
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

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No workouts found", fontSize = 15.sp, color = TextSecondaryColor)
                }
            } else {
                LazyColumn {
                    items(filtered) { workout ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedWorkout = workout
                                    showDatePicker = true
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(workout.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                if (workout.time.isNotEmpty()) {
                                    Text(workout.time, fontSize = 12.sp, color = TextSecondaryColor)
                                }
                            }
                            Icon(Icons.Default.CalendarToday, contentDescription = "Schedule", tint = FLPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
