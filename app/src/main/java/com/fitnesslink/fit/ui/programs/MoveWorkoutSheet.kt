package com.fitnesslink.fit.ui.programs

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.model.ProgramDates
import com.fitnesslink.fit.programs.UserProgramStore
import com.fitnesslink.fit.ui.components.PrimaryButtonView
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Move Workout (PRD §31–33; iOS `MoveWorkoutSheet`): pick another day for
 * one workout occurrence. A day that already holds a workout asks before
 * double-booking ("Move Anyway" / "Choose Another Day").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveWorkoutSheet(entry: FitnessContent, onMoved: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val todayStart = remember { ProgramDates.startOfDay(System.currentTimeMillis()) }
    val initial = remember(entry.id) { maxOf(entry.scheduledDate?.let { ProgramDates.startOfDay(it) } ?: todayStart, todayStart) }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = toUtcDay(initial),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= toUtcDay(todayStart)
        }
    )
    val selectedDay: Long = pickerState.selectedDateMillis?.let { fromUtcDay(it) } ?: initial
    var showConflict by remember { mutableStateOf(false) }
    var isMoving by remember { mutableStateOf(false) }
    val title = entry.title.ifEmpty { "this workout" }

    fun move() {
        if (isMoving) return
        isMoving = true
        scope.launch {
            runCatching { UserProgramStore.move(entry.id, Date(selectedDay)) }
            isMoving = false
            onMoved()
            onDismiss()
        }
    }

    fun moveTapped() {
        val current = entry.scheduledDate
        if (current != null && ProgramDates.isSameDay(current, selectedDay)) { onDismiss(); return }
        if (UserProgramStore.conflict(selectedDay, excludingEntryId = entry.id) != null) showConflict = true else move()
    }

    if (showConflict) {
        val weekday = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(selectedDay))
        AlertDialog(
            onDismissRequest = { showConflict = false },
            title = { Text("$weekday already has a workout scheduled.") },
            text = { Text("Would you like to move this workout anyway?") },
            confirmButton = { TextButton(onClick = { showConflict = false; move() }) { Text("Move Anyway") } },
            dismissButton = { TextButton(onClick = { showConflict = false }) { Text("Choose Another Day") } }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = White) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = FLPrimary) }
                Spacer(modifier = Modifier.weight(1f))
                Text("Move Workout", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(64.dp))
            }
            Text(
                "Choose a new day for $title. The rest of your program stays where it is.",
                fontSize = 14.sp, color = TextSecondaryColor
            )
            DatePicker(
                state = pickerState,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(containerColor = White, selectedDayContainerColor = FLPrimary, todayDateBorderColor = FLPrimary, todayContentColor = FLPrimary)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(100.dp))
                    .clickable(enabled = !isMoving) { moveTapped() }
            ) { PrimaryButtonView(text = "Move Workout") }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
