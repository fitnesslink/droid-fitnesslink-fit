package com.fitnesslink.fit.ui.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fitnesslink.fit.calendar.tapRoute
import com.fitnesslink.fit.contributors.ContributorStore
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.model.Program
import com.fitnesslink.fit.model.ProgramDayPlan
import com.fitnesslink.fit.model.ProgramJourneyState
import com.fitnesslink.fit.model.ProgramSnapshot
import com.fitnesslink.fit.model.ProgramWorkoutMeta
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.programs.ProgramStore
import com.fitnesslink.fit.programs.UserProgramStore
import com.fitnesslink.fit.ui.components.BackCircleButton
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.LocationInfoView
import com.fitnesslink.fit.ui.components.PrimaryButtonView
import com.fitnesslink.fit.ui.components.SecondaryButtonView
import com.fitnesslink.fit.ui.components.TimeInfoView
import com.fitnesslink.fit.ui.components.TrainingLevelInfoView
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.navigation.ProgramRoutes
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.ui.workout.ContentTagChip
import com.fitnesslink.fit.ui.workout.ContentTagKind
import com.fitnesslink.fit.ui.workout.ContentTagNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class MyProgramTab(val label: String) { WEEK_PLAN("Week Plan"), OVERVIEW("Overview"), COACH("Coach") }

/**
 * My Program (Programs PRD §25–36, §41–45; iOS `MyProgramView`): the user's
 * home base for a started program. Week Plan is the schedule, Overview the
 * permanent reference, Coach who is guiding it. The ••• menu manages the
 * program (Restart / Find Another / Remove).
 *
 * @param enrollmentId null = the active program.
 * @param onNavigate pushes a route string (`tapRoute()`, `programProgress?…`, `startProgram/…`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyProgramScreen(
    enrollmentId: String?,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<ProgramSnapshot?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var program by remember { mutableStateOf(Program()) }
    var meta by remember { mutableStateOf<Map<String, ProgramWorkoutMeta>>(emptyMap()) }
    var tab by rememberSaveable { mutableStateOf(MyProgramTab.WEEK_PLAN) }
    var selectedWeek by rememberSaveable { mutableIntStateOf(1) }
    var didPickInitialWeek by rememberSaveable { mutableStateOf(false) }
    var movingEntry by remember { mutableStateOf<FitnessContent?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showRestartAlert by remember { mutableStateOf(false) }
    var showRemoveAlert by remember { mutableStateOf(false) }

    suspend fun reload() {
        val result = withContext(Dispatchers.IO) {
            val instance = enrollmentId?.takeIf { it.isNotEmpty() }?.let { UserProgramStore.program(it) } ?: UserProgramStore.active()
            instance?.let { it to UserProgramStore.snapshot(it) }
        }
        loaded = true
        if (result == null) { snapshot = null; return }
        val (instance, fresh) = result
        snapshot = fresh
        if (!didPickInitialWeek) { selectedWeek = fresh.currentWeek; didPickInitialWeek = true }
        selectedWeek = selectedWeek.coerceIn(1, maxOf(fresh.totalWeeks, 1))
        withContext(Dispatchers.IO) {
            DatabaseManager.program(instance.programId)?.let { cached ->
                // Keep the live-only fields a previous network refresh grafted on.
                program = cached.copy(contributor = program.contributor, equipmentIds = program.equipmentIds)
            }
            val ids = fresh.weeks.flatMap { it.days }.flatMap { it.entries }.map { it.workoutId.lowercase(Locale.US) }.toSet()
            val extra = ids.filter { meta[it] == null }.mapNotNull { id -> ProgramStore.workoutMeta(id)?.let { id to it } }
            if (extra.isNotEmpty()) meta = meta + extra
        }
    }

    suspend fun refresh() {
        if (!NetworkMonitor.isConnected.value) return
        if (UserProgramStore.syncFromServer()) reload()
        val instance = snapshot?.program ?: return
        ProgramStore.fetchDetail(instance.programId)?.let { if (it.pendingShare == null) program = it.program }
        // Rows show duration / exercises / focus from the cached workout; fetch the ones this device never opened.
        val missing = snapshot?.weeks.orEmpty().flatMap { it.days }.flatMap { it.entries }
            .map { it.workoutId.lowercase(Locale.US) }.toSet()
            .filter { (meta[it]?.exerciseCount ?: 0) == 0 }
        for (id in missing) {
            ProgramStore.fetchWorkout(id) ?: continue
            withContext(Dispatchers.IO) { ProgramStore.workoutMeta(id) }?.let { meta = meta + (id to it) }
        }
    }

    LaunchedEffect(enrollmentId) { reload(); refresh() }
    LaunchedEffect(Unit) { UserProgramStore.changes.collect { reload() } }
    // Re-read on every appearance: coming back from a workout must show the new completion at once.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) scope.launch { reload() } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    movingEntry?.let { entry ->
        MoveWorkoutSheet(entry = entry, onMoved = { scope.launch { reload() } }, onDismiss = { movingEntry = null })
    }
    if (showRestartAlert) {
        AlertDialog(
            onDismissRequest = { showRestartAlert = false },
            title = { Text("Restart Program?") },
            text = { Text("Restarting will reset your current program progress and begin again at Week 1.") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartAlert = false
                    snapshot?.program?.let { onNavigate(ProgramRoutes.startProgram(it.programId, replacing = it.enrollmentId)) }
                }) { Text("Restart", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showRestartAlert = false }) { Text("Cancel") } }
        )
    }
    if (showRemoveAlert) {
        AlertDialog(
            onDismissRequest = { showRemoveAlert = false },
            title = { Text("Remove This Program?") },
            text = { Text("This will remove this program from My Program and remove its scheduled workouts from your Calendar.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveAlert = false
                    val current = snapshot?.program
                    scope.launch {
                        current?.let { UserProgramStore.remove(it.enrollmentId) }
                        onBack()
                    }
                }) { Text("Remove Program", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showRemoveAlert = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackCircleButton(onClick = onBack)
            Spacer(modifier = Modifier.weight(1f))
            Text("My Program", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Spacer(modifier = Modifier.weight(1f))
            if (snapshot != null) {
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Program options", tint = TextPrimaryColor)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Restart Program") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = { showMenu = false; showRestartAlert = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Find Another Program") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            onClick = { showMenu = false; onNavigate(ProgramRoutes.PROGRAMS) }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove Program", color = Color.Red) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                            onClick = { showMenu = false; showRemoveAlert = true }
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.size(36.dp))
            }
        }

        val current = snapshot
        if (current == null) {
            if (loaded) EmptyState(onBrowse = { if (!AppRouter.replaceTop(ProgramRoutes.PROGRAMS)) onNavigate(ProgramRoutes.PROGRAMS) })
            return
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            ProgramHeroView(imageId = current.heroImageId(), name = current.program.programName, height = 170.dp)
            FlowRow(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                program.trainingLevels.filter { it.isNotEmpty() }.forEach { TrainingLevelInfoView(level = it) }
                if (program.time.isNotEmpty()) TimeInfoView(time = program.time)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(14.dp))
                    Text("${current.totalWeeks} Week${if (current.totalWeeks == 1) "" else "s"}", fontSize = 13.sp, color = TextSecondaryColor)
                }
                if (program.location.isNotEmpty()) LocationInfoView(location = program.location)
            }

            // Tabs
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MyProgramTab.entries.forEach { item ->
                    val selected = tab == item
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (selected) FLPrimary else White)
                            .clickable { tab = item }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(item.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) White else TextPrimaryColor)
                    }
                }
            }

            when (tab) {
                MyProgramTab.WEEK_PLAN -> WeekPlan(
                    snapshot = current,
                    selectedWeek = selectedWeek,
                    onSelectWeek = { selectedWeek = it },
                    meta = meta,
                    onOpenEntry = { onNavigate(it.tapRoute()) },
                    onMove = { movingEntry = it },
                    onViewProgress = { onNavigate(ProgramRoutes.programProgress(current.program.enrollmentId)) },
                    onBrowsePrograms = { onNavigate(ProgramRoutes.PROGRAMS) }
                )
                MyProgramTab.OVERVIEW -> Overview(current, program)
                MyProgramTab.COACH -> Coach(program)
            }
        }
    }
}

// MARK: - Week Plan

@Composable
private fun WeekPlan(
    snapshot: ProgramSnapshot,
    selectedWeek: Int,
    onSelectWeek: (Int) -> Unit,
    meta: Map<String, ProgramWorkoutMeta>,
    onOpenEntry: (FitnessContent) -> Unit,
    onMove: (FitnessContent) -> Unit,
    onViewProgress: () -> Unit,
    onBrowsePrograms: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (snapshot.state == ProgramJourneyState.COMPLETED) {
            CompletionCard(snapshot, onViewProgress, onBrowsePrograms)
        }

        WeekSelector(snapshot, selectedWeek, onSelectWeek)

        val week = snapshot.weeks.firstOrNull { it.weekNumber == selectedWeek }
        if (week != null) WeekBody(snapshot, week, selectedWeek, onSelectWeek, meta, onOpenEntry, onMove, onViewProgress)
    }
}

@Composable
private fun WeekBody(
    snapshot: ProgramSnapshot,
    week: com.fitnesslink.fit.model.ProgramWeekPlan,
    selectedWeek: Int,
    onSelectWeek: (Int) -> Unit,
    meta: Map<String, ProgramWorkoutMeta>,
    onOpenEntry: (FitnessContent) -> Unit,
    onMove: (FitnessContent) -> Unit,
    onViewProgress: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(snapshot.weekLine(week.weekNumber), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.clickable(onClick = onViewProgress).padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("View Progress", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
                }
            }
            ProgramProgressRow(
                title = week.rangeLabel,
                caption = "${week.completed} / ${week.total} workouts completed",
                filled = week.completed,
                total = week.total,
                percent = week.percent
            )
        }

        // Swipe between weeks: only a clearly horizontal drag changes the week.
        var dragX by remember(week.weekNumber) { mutableStateOf(0f) }
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .pointerInput(week.weekNumber, snapshot.totalWeeks) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragX = 0f },
                        onHorizontalDrag = { _, delta -> dragX += delta },
                        onDragEnd = {
                            if (abs(dragX) > 70f) {
                                val next = selectedWeek + if (dragX < 0) 1 else -1
                                if (next in 1..snapshot.totalWeeks) onSelectWeek(next)
                            }
                            dragX = 0f
                        }
                    )
                },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            week.days.forEach { day ->
                if (day.isRest) RestRow(day)
                else day.entries.forEach { entry ->
                    WorkoutRow(entry, day.date, meta[entry.workoutId.lowercase(Locale.US)], onOpen = { onOpenEntry(entry) }, onMove = { onMove(entry) })
                }
            }
        }
    }
}

@Composable
private fun WeekSelector(snapshot: ProgramSnapshot, selectedWeek: Int, onSelectWeek: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedWeek) {
        val index = snapshot.weeks.indexOfFirst { it.weekNumber == selectedWeek }
        if (index >= 0) listState.animateScrollToItem(maxOf(index - 2, 0))
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(White),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(snapshot.weeks, key = { it.weekNumber }) { week ->
            val isSelected = week.weekNumber == selectedWeek
            Column(
                modifier = Modifier
                    .width(54.dp)
                    .clickable { onSelectWeek(week.weekNumber) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(if (isSelected) FLPrimary else BackgroundColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${week.weekNumber}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isSelected) White else TextPrimaryColor)
                    }
                    if (week.isComplete) {
                        Icon(
                            Icons.Default.CheckCircle, contentDescription = "Complete", tint = FLPrimary,
                            modifier = Modifier.offset(x = 3.dp, y = (-3).dp).size(14.dp).background(White, CircleShape)
                        )
                    }
                }
                Text(
                    week.phase ?: "Week",
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) FLPrimary else TextSecondaryColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DateColumn(date: Long) {
    val weekday = remember(date) { SimpleDateFormat("EEE", Locale.getDefault()).format(Date(date)).uppercase(Locale.getDefault()) }
    val day = remember(date) { SimpleDateFormat("d", Locale.getDefault()).format(Date(date)) }
    Column(modifier = Modifier.width(38.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(weekday, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
        Text(day, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
    }
}

@Composable
private fun WorkoutRow(entry: FitnessContent, date: Long, info: ProgramWorkoutMeta?, onOpen: () -> Unit, onMove: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Completed → that session's report; otherwise Workout Detail.
        Row(
            modifier = Modifier.weight(1f).clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DateColumn(date)
            Icon(
                if (entry.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (entry.isCompleted) "Completed" else "Not completed",
                tint = if (entry.isCompleted) FLPrimary else MediumGray,
                modifier = Modifier.size(20.dp)
            )
            FLImageView(
                ref = (entry.workoutThumbnailId ?: entry.workoutImageId)?.let { MediaRef.WorkoutThumbnail(it) },
                modifier = Modifier.width(64.dp).height(56.dp).clip(RoundedCornerShape(8.dp))
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.title.ifEmpty { "Workout" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, maxLines = 2)
                info?.summary?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, color = TextSecondaryColor, maxLines = 1) }
                info?.focus?.let { Text(it, fontSize = 12.sp, color = TextSecondaryColor) }
            }
        }
        if (entry.isCompleted) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(18.dp))
        } else {
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options for ${entry.title}", tint = TextSecondaryColor)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Move Workout") },
                        leadingIcon = { Icon(Icons.Default.EventRepeat, contentDescription = null) },
                        onClick = { showMenu = false; onMove() }
                    )
                }
            }
        }
    }
}

@Composable
private fun RestRow(day: ProgramDayPlan) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White.copy(alpha = 0.6f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DateColumn(day.date)
        Icon(Icons.Default.Hotel, contentDescription = null, tint = MediumGray, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Rest Day", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Text("Recovery and light movement.", fontSize = 12.sp, color = TextSecondaryColor)
        }
    }
}

@Composable
private fun CompletionCard(snapshot: ProgramSnapshot, onViewResults: () -> Unit, onBrowse: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(White)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Program Complete", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
        Text(
            "${snapshot.weeksCompleted} of ${snapshot.totalWeeks} weeks completed\n${snapshot.completedWorkouts} of ${snapshot.totalWorkouts} workouts completed",
            fontSize = 14.sp, color = TextSecondaryColor, textAlign = TextAlign.Center
        )
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(100.dp)).clickable(onClick = onViewResults)) { PrimaryButtonView(text = "View Results") }
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(100.dp)).clickable(onClick = onBrowse)) { SecondaryButtonView(text = "Start Another Program") }
    }
}

// MARK: - Overview

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Overview(snapshot: ProgramSnapshot, program: Program) {
    val perWeek = (snapshot.totalWorkouts.toDouble() / maxOf(snapshot.totalWeeks, 1)).roundToInt()
    Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (program.description.isNotEmpty()) {
            OverviewSection("Program Overview") { Text(program.description, fontSize = 14.sp, color = TextSecondaryColor) }
        }
        if (program.whoIsFor.isNotEmpty() || program.bestFor.isNotEmpty()) {
            OverviewSection("Who This Program Is For") { ProgramAudienceView(whoIsFor = program.whoIsFor, bestFor = program.bestFor) }
        }
        OverviewSection("Program Structure") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StructureRow("Weeks", "${snapshot.totalWeeks}")
                StructureRow("Workouts per week", "$perWeek")
                if (program.time.isNotEmpty()) StructureRow("Workout duration", program.time)
                if (program.trainingLevels.isNotEmpty()) StructureRow("Fitness level", program.trainingLevels.joinToString(", "))
                if (program.location.isNotEmpty()) StructureRow("Location", program.location)
            }
        }
        val equipment = ContentTagNames.equipment(program.equipmentIds.map { it.toString() })
        if (equipment.isNotEmpty()) {
            OverviewSection("Equipment Needed") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    equipment.forEach { ContentTagChip(name = it, icon = ContentTagKind.EQUIPMENT.icon) }
                }
            }
        }
    }
}

@Composable
private fun OverviewSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(16.dp)) { content() }
    }
}

@Composable
private fun StructureRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = TextSecondaryColor)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, textAlign = TextAlign.End)
    }
}

// MARK: - Coach

@Composable
private fun Coach(program: Program) {
    val contributor = program.contributor
    val isConnected by NetworkMonitor.isConnected.collectAsState()
    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
        if (contributor != null && !ContributorStore.isHouse(program.contributorId?.toString())) {
            ProgramContributorCard(contributor)
        } else {
            Text(
                text = when {
                    contributor != null -> "This program is coached by the FitnessLink team."
                    isConnected ->"This program doesn't have a coach profile yet."
                    else -> "Your coach's profile will appear when you're back online."
                },
                fontSize = 14.sp,
                color = TextSecondaryColor,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(16.dp)
            )
        }
    }
}

// MARK: - Empty

@Composable
private fun EmptyState(onBrowse: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = MediumGray, modifier = Modifier.size(44.dp))
        Spacer(modifier = Modifier.height(14.dp))
        Text("No program in progress", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Start a program and it will live here — your weeks, workouts and progress in one place.",
            fontSize = 14.sp, color = TextSecondaryColor, textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(100.dp)).clickable(onClick = onBrowse)) { PrimaryButtonView(text = "Browse Programs") }
    }
}
