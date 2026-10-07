package com.fitnesslink.fit.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.calendar.CalendarSyncService
import com.fitnesslink.fit.calendar.tapRoute
import com.fitnesslink.fit.ui.catalog.ProgramEditorScreen
import com.fitnesslink.fit.ui.components.HeaderView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.workout.WorkoutEditorScreen
import com.fitnesslink.fit.viewmodel.CalendarViewModel
import kotlinx.coroutines.delay
import java.util.Date

/**
 * Calendar tab — port of iOS `CalendarTabView`. Month grid with navigation,
 * the anchored add menu (four triggers, four actions), the selected day's
 * list with move / remove / multi-select, and the picker + editor flows.
 *
 * @param onNavigateToWorkoutDetail kept for `AppNavigation.kt`; used when [onNavigate] is not supplied
 * @param onNavigate generic route navigation for `FitnessContent.tapRoute()` (`workoutDetail/…`,
 *   `completedWorkoutReport/{workoutId}/{yyyy-MM-dd}`)
 */
@Composable
fun CalendarScreen(
    onNavigateToWorkoutDetail: (String) -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigate: ((String) -> Unit)? = null
) {
    val viewModel: CalendarViewModel = viewModel()
    val context = LocalContext.current
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        CalendarSyncService.attach(context.applicationContext)
        viewModel.loadData()
    }
    // Re-read the cache on every appearance (iOS `.onAppear { hydrateFromCache() }`).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.hydrateFromCache() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // The chosen sheet opens only once the add menu has gone (iOS `runPendingAddAction`).
    LaunchedEffect(viewModel.addMenuAnchor) {
        if (viewModel.addMenuAnchor == null) { delay(120); viewModel.runPendingAddAction() }
    }

    val navigate: (String) -> Unit = { route ->
        when {
            onNavigate != null -> onNavigate(route)
            route.startsWith("workoutDetail/") -> onNavigateToWorkoutDetail(route.removePrefix("workoutDetail/"))
            route.startsWith("completedWorkoutReport/") -> onNavigateToWorkoutDetail(route.removePrefix("completedWorkoutReport/").substringBefore('/'))
        }
    }

    // MARK: - Sheets

    if (viewModel.showWorkoutPicker) {
        CalendarWorkoutPickerSheet(
            scheduleDate = Date(viewModel.selectedDate),
            onSchedule = { workout, date, duration -> viewModel.scheduleWorkout(workout.id, workout.name, date, duration) },
            onDismiss = { viewModel.showWorkoutPicker = false }
        )
    }
    if (viewModel.showProgramPicker) {
        CalendarProgramPickerSheet(
            scheduleDate = Date(viewModel.selectedDate),
            onSchedule = { program, date -> viewModel.scheduleProgram(program.id, date) },
            onDismiss = { viewModel.showProgramPicker = false }
        )
    }
    viewModel.moveTarget?.let { entry ->
        ScheduleDatePickerSheet(
            initialDate = entry.scheduledDate?.let { Date(it) } ?: Date(viewModel.selectedDate),
            workoutEstimateMinutes = null,
            title = "Move “${entry.title.ifBlank { "Workout" }}”",
            cta = "Move",
            showDurationChips = false,
            onConfirm = { date, _ -> viewModel.moveTarget = null; viewModel.moveEntry(entry.id, date) },
            onDismiss = { viewModel.moveTarget = null }
        )
    }
    if (viewModel.showWorkoutCreator) {
        FullScreenEditor(onDismiss = { viewModel.showWorkoutCreator = false }) {
            WorkoutEditorScreen(
                workoutId = null,
                onBack = { viewModel.showWorkoutCreator = false },
                onSaved = { workoutId -> viewModel.showWorkoutCreator = false; viewModel.scheduleSavedWorkout(workoutId) }
            )
        }
    }
    if (viewModel.showProgramCreator) {
        FullScreenEditor(onDismiss = { viewModel.showProgramCreator = false }) {
            ProgramEditorScreen(
                programId = null,
                onBack = { viewModel.showProgramCreator = false },
                onSaved = { programId -> viewModel.showProgramCreator = false; viewModel.scheduleProgram(programId, Date(viewModel.selectedDate)) }
            )
        }
    }
    if (showBulkDeleteConfirm) {
        val n = viewModel.selectedEntryIds.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Remove $n workout${if (n == 1) "" else "s"} from your calendar?") },
            confirmButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false; viewModel.deleteSelectedEntries() }) {
                    Text("Remove from Calendar", color = Color.Red)
                }
            },
            dismissButton = { TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    // MARK: - Body

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderView(onNotificationsTap = onNavigateToNotifications)

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(25.dp))
            MonthCalendarView(
                cells = viewModel.calendarCells,
                monthMillis = viewModel.currentMonth,
                selectedDateMillis = viewModel.selectedDate,
                addMenuAnchor = viewModel.addMenuAnchor,
                onAddMenuAnchorChange = { viewModel.addMenuAnchor = it },
                onDayTapped = { viewModel.selectDate(it) },
                onChooseAdd = { viewModel.chooseAddAction(it) },
                onPreviousMonth = { viewModel.goToPreviousMonth() },
                onNextMonth = { viewModel.goToNextMonth() },
                onMonthSelected = { viewModel.setMonth(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(viewModel.selectedDayLabel, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                if (viewModel.filteredContent.isNotEmpty()) {
                    Text(
                        if (viewModel.isSelecting) "Done" else "Select",
                        fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FLPrimary,
                        modifier = Modifier.clickable { viewModel.toggleSelectionMode() }.padding(8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (!viewModel.isSelecting) {
                CalendarAddRow(
                    title = "Add to ${viewModel.selectedDayLabel}",
                    isMenuPresented = viewModel.addMenuAnchor == CalendarAddMenuAnchor.InlineRow,
                    onOpenMenu = { viewModel.addMenuAnchor = CalendarAddMenuAnchor.InlineRow },
                    onDismissMenu = { if (viewModel.addMenuAnchor == CalendarAddMenuAnchor.InlineRow) viewModel.addMenuAnchor = null },
                    onChoose = { viewModel.chooseAddAction(it) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (viewModel.filteredContent.isEmpty()) {
                Text(
                    "Nothing scheduled", fontSize = 14.sp, color = CalendarAddMenuPalette.secondaryText,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                )
            } else {
                viewModel.filteredContent.forEach { item ->
                    DateContentView(
                        content = item,
                        deleteScope = viewModel.deleteScope(item),
                        onOpen = { navigate(item.tapRoute()) },
                        onDelete = { viewModel.deleteEntry(item.id) },
                        onMove = { viewModel.moveTarget = item },
                        onRemoveProgram = { viewModel.removeProgram(item) },
                        isSelecting = viewModel.isSelecting,
                        isSelected = viewModel.selectedEntryIds.contains(item.id),
                        onToggleSelect = { viewModel.toggleSelection(item.id) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (viewModel.isSelecting) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.toggleSelectAll() }) {
                            Text(if (viewModel.allSelected) "Deselect All" else "Select All", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        val count = viewModel.selectedEntryIds.size
                        TextButton(onClick = { showBulkDeleteConfirm = true }, enabled = count > 0) {
                            Text("Delete ($count)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (count > 0) Color.Red else MediumGray)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Full-screen modal host for the create-new editors launched from the calendar. */
@Composable
private fun FullScreenEditor(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = BackgroundColor) {
            Box(modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}
