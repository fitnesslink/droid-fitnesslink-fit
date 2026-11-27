package com.fitnesslink.fit.ui.goals

import com.fitnesslink.fit.ui.theme.FLTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fitnesslink.fit.ui.progress.CONNECTED_APPS_ROUTE
import com.fitnesslink.fit.goals.GoalAction
import com.fitnesslink.fit.goals.GoalChart
import com.fitnesslink.fit.goals.GoalFormat
import com.fitnesslink.fit.goals.GoalPhase
import com.fitnesslink.fit.goals.GoalProgress
import com.fitnesslink.fit.goals.GoalProgressEngine
import com.fitnesslink.fit.goals.GoalReminderScheduler
import com.fitnesslink.fit.goals.GoalStore
import com.fitnesslink.fit.goals.LocalGoalDataSource
import com.fitnesslink.fit.goals.deadlineText
import com.fitnesslink.fit.goals.phase
import com.fitnesslink.fit.model.GoalActivity
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalDay
import com.fitnesslink.fit.model.GoalEntry
import com.fitnesslink.fit.model.GoalFrequency
import com.fitnesslink.fit.model.GoalReminder
import com.fitnesslink.fit.model.GoalStatus
import com.fitnesslink.fit.model.GoalSubtype
import com.fitnesslink.fit.model.GoalTrackingSource
import com.fitnesslink.fit.model.WaterUnit
import com.fitnesslink.fit.model.mealTypeForHour
import com.fitnesslink.fit.nutrition.HydrationStore
import com.fitnesslink.fit.ui.navigation.AppRoute
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.navigation.FoodLoggingRoutes
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.viewmodel.GoalSetupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Port of iOS `TypedGoalDetailView` (Goals PRD §1.9): status, current vs
 * baseline vs target, progress, deadline, trend (only real points), weekly
 * history, recent qualifying entries, the logging action, and edit / pause /
 * resume / pin / delete. Progress is recomputed every time the screen
 * resumes or the goal store changes.
 */
@Composable
fun TypedGoalDetailScreen(goalId: String, onBack: () -> Unit, onNavigate: (String) -> Unit = { AppRouter.push(it) }) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var goal by remember { mutableStateOf(GoalStore.goal(goalId)) }
    var progress by remember { mutableStateOf<GoalProgress?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<DetailSheet?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { GoalReminderScheduler.attach(context) }
    LaunchedEffect(Unit) { GoalStore.changes.collect { tick++ } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(goalId, tick) {
        val (fresh, computed) = withContext(Dispatchers.IO) {
            val g = GoalStore.goal(goalId)
            g to g?.let { GoalProgressEngine.progress(it, LocalGoalDataSource()) }
        }
        goal = fresh
        progress = computed
    }

    val current = goal
    Column(Modifier.fillMaxSize().background(FLTheme.colors.background)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(onClick = onBack).padding(6.dp))
            Text(current?.title ?: "", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            if (current != null) {
                DetailMenu(current,
                    onEdit = { sheet = DetailSheet.Edit(false) },
                    onPin = { scope.launch { GoalStore.setPinned(current, !current.isPinned) } },
                    onStatus = { status -> scope.launch { GoalStore.setStatus(current, status) } },
                    onDelete = { confirmDelete = true })
            }
        }
        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("This goal is no longer available.", color = goalSecondaryText()) }
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Header(current, progress)
            val p = progress
            if (p == null) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FLPrimary) }
            } else {
                PhaseBanner(current, p,
                    onExtend = { sheet = DetailSheet.Edit(true) },
                    onAdjust = { sheet = DetailSheet.Edit(false) },
                    onStatus = { status -> scope.launch { GoalStore.setStatus(current, status) } })
                ProgressCard(current, p)
                val action = p.action
                if (action != null && current.status == GoalStatus.Active) {
                    GoalPrimaryButton(actionTitle(current, action), icon = goalActionIcon(action)) {
                        when (action) {
                            GoalAction.LogWeight -> onNavigate(AppRoute.WeightLog.route)
                            GoalAction.LogFood -> onNavigate(FoodLoggingRoutes.foodSearch(mealTypeForHour(LocalTime.now().hour), GoalDay.key(GoalDay.today())))
                            GoalAction.LogWater -> sheet = DetailSheet.Water
                            GoalAction.LogSleep -> sheet = DetailSheet.Sleep
                            GoalAction.LogWorkout -> onNavigate("quickStart")
                            GoalAction.ConnectHealth -> onNavigate(CONNECTED_APPS_ROUTE)
                            is GoalAction.CheckIn -> sheet = DetailSheet.CheckIn(action.activityKey)
                            is GoalAction.LogAmount -> sheet = DetailSheet.Amount(action.unit)
                        }
                    }
                }
                if (p.activities.isNotEmpty()) ActivitiesCard(current, p) { key -> sheet = DetailSheet.CheckIn(key) }
                p.chart?.let { ChartCard(it) }
                if (p.weeks.isNotEmpty()) WeeksCard(p)
                if (p.notes.isNotEmpty() || current.settings.recommendationBasis != null) NotesCard(current, p)
                if (p.recent.isNotEmpty()) RecentCard(p) { entry -> scope.launch { GoalStore.deleteEntry(entry) } }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    when (val s = sheet) {
        null -> Unit
        is DetailSheet.Edit -> current?.let { GoalEditSheet(it, s.focusDeadline, onDismiss = { sheet = null }) { updated -> scope.launch { GoalStore.update(updated, it) } } }
        is DetailSheet.CheckIn -> current?.let { GoalCheckInSheet(it, s.activityKey, onDismiss = { sheet = null }) }
        is DetailSheet.Amount -> current?.let { GoalAmountSheet(it, s.unit, onDismiss = { sheet = null }) }
        DetailSheet.Sleep -> current?.let { GoalSleepLogSheet(it, onDismiss = { sheet = null }) }
        DetailSheet.Water -> GoalWaterSheet(onDismiss = { sheet = null; tick++ })
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this goal?") },
            text = { Text("Your logged weights, workouts, food and water stay in the app. Check-ins for this goal are removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { GoalStore.delete(current.id) }
                    onBack()
                }) { Text("Delete Goal", color = FLTheme.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

private sealed class DetailSheet {
    data class Edit(val focusDeadline: Boolean) : DetailSheet()
    data class CheckIn(val activityKey: String?) : DetailSheet()
    data class Amount(val unit: String) : DetailSheet()
    object Sleep : DetailSheet()
    object Water : DetailSheet()
}

private fun actionTitle(goal: Goal, action: GoalAction): String {
    val name = goal.settings.actionName
    return when {
        action is GoalAction.CheckIn && name != null -> "Check In: $name"
        action is GoalAction.CheckIn && (goal.subtype == GoalSubtype.DailyHabit || goal.subtype == GoalSubtype.HealthyHabit) -> "Complete Habit"
        action is GoalAction.CheckIn -> "Check In"
        action is GoalAction.LogAmount && action.unit.isNotEmpty() -> "Log ${action.unit.replaceFirstChar { it.titlecase(Locale.getDefault()) }}"
        else -> action.title
    }
}

@Composable
private fun DetailMenu(goal: Goal, onEdit: () -> Unit, onPin: () -> Unit, onStatus: (GoalStatus) -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Icon(Icons.Filled.MoreVert, contentDescription = "Goal actions", tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clickable { open = true }.padding(6.dp))
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Edit Goal") }, onClick = { open = false; onEdit() })
            DropdownMenuItem(text = { Text(if (goal.isPinned) "Unpin from Home" else "Pin to My Goals") }, onClick = { open = false; onPin() })
            if (goal.status == GoalStatus.Active) {
                DropdownMenuItem(text = { Text("Pause") }, onClick = { open = false; onStatus(GoalStatus.Paused) })
                DropdownMenuItem(text = { Text("Mark Complete") }, onClick = { open = false; onStatus(GoalStatus.Completed) })
            } else {
                DropdownMenuItem(text = { Text("Resume") }, onClick = { open = false; onStatus(GoalStatus.Active) })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Delete Goal", color = FLTheme.colors.danger) }, onClick = { open = false; onDelete() })
        }
    }
}

@Composable
private fun Header(goal: Goal, progress: GoalProgress?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(FLPrimary.copy(alpha = 0.12f))) {
            Icon(goal.subtype?.let { goalSubtypeIcon(it) } ?: goalTypeIcon(goal.goalType), contentDescription = null, tint = FLPrimary, modifier = Modifier.size(24.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(goal.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(goal.subtype?.title ?: goal.goalType.displayName, fontSize = 14.sp, color = goalSecondaryText())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val phase = goal.phase(progress)
                val color = phaseColor(phase)
                Text(phase.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color,
                    modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp))
                if (goal.isPinned) {
                    Icon(Icons.Filled.PushPin, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(12.dp))
                    Text("Pinned", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                }
            }
        }
    }
}

@Composable
fun phaseColor(phase: GoalPhase): Color = when (phase) {
    GoalPhase.TARGET_REACHED, GoalPhase.COMPLETED -> FLPrimary
    GoalPhase.DEADLINE_PASSED -> GoalWarning
    else -> goalSecondaryText()
}

@Composable
private fun PhaseBanner(goal: Goal, progress: GoalProgress, onExtend: () -> Unit, onAdjust: () -> Unit, onStatus: (GoalStatus) -> Unit) {
    when (goal.phase(progress)) {
        GoalPhase.DEADLINE_PASSED -> GoalCard {
            GoalNote("The target date passed before the target was reached. Your goal and its history are still here — keep tracking, or adjust it.",
                Icons.Filled.EventBusy, GoalWarning)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GoalSmallButton("Extend Deadline", onExtend)
                GoalSmallButton("Adjust Target", onAdjust)
                GoalSmallButton("Pause") { onStatus(GoalStatus.Paused) }
            }
        }
        GoalPhase.TARGET_REACHED -> GoalCard {
            GoalNote("You reached your target. Mark the goal complete, or keep tracking.", Icons.Filled.EmojiEvents, FLPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GoalSmallButton("Mark Complete") { onStatus(GoalStatus.Completed) }
                GoalSmallButton("Keep Tracking") {}
            }
        }
        GoalPhase.PAUSED -> GoalCard {
            GoalNote("This goal is paused. Your history is kept; reminders are off.", Icons.Filled.PauseCircle)
            GoalSmallButton("Resume") { onStatus(GoalStatus.Active) }
        }
        else -> Unit
    }
}

@Composable
private fun ProgressCard(goal: Goal, progress: GoalProgress) {
    GoalCard {
        Text(progress.summary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        progress.detail?.let { Text(it, fontSize = 14.sp, color = if (progress.hasData) goalSecondaryText() else GoalWarning) }
        progress.fraction?.let { f ->
            GoalProgressBar(f, Modifier.semantics { contentDescription = "Progress ${(f * 100).toInt()} percent" })
            Text("${Math.round(f * 100)}% complete", fontSize = 12.sp, color = goalSecondaryText())
        }
        if (progress.stats.isNotEmpty()) {
            progress.stats.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEach { GoalStatText(it.label, it.value, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            goal.deadlineText()?.let {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = goalSecondaryText(), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(it, fontSize = 13.sp, color = goalSecondaryText())
            }
            Spacer(Modifier.weight(1f))
            progress.latestEntryText?.let { Text(it, fontSize = 12.sp, color = goalSecondaryText()) }
        }
    }
}

@Composable
private fun ActivitiesCard(goal: Goal, progress: GoalProgress, onCheckIn: (String) -> Unit) {
    val isMacro = goal.subtype == GoalSubtype.MacroTarget
    GoalCard(if (isMacro) "Today" else "This week") {
        progress.activities.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(if (isMacro) "${row.completed} / ${row.required} g" else "${row.completed} of ${row.required}", fontSize = 14.sp, color = goalSecondaryText())
                    if (row.tracking == GoalTrackingSource.Manual && goal.status == GoalStatus.Active) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.AddCircle, contentDescription = "Check in ${row.name}", tint = FLPrimary,
                            modifier = Modifier.clickable { onCheckIn(row.id) })
                    }
                }
                GoalProgressBar(row.completed.toDouble() / maxOf(1, row.required))
            }
        }
    }
}

@Composable
private fun ChartCard(chart: GoalChart) {
    GoalCard(chart.title) {
        val points = chart.points
        val values = points.map { it.value } + listOfNotNull(chart.target)
        val minY = if (chart.style == GoalChart.Style.BAR) 0.0 else (values.minOrNull() ?: 0.0)
        val maxY = (values.maxOrNull() ?: 1.0).let { if (it <= minY) minY + 1 else it }
        val span = maxY - minY
        val pad = if (chart.style == GoalChart.Style.LINE) span * 0.1 else 0.0
        val lo = minY - pad
        val hi = maxY + pad
        val firstDay = points.minOf { it.date.toEpochDay() }
        val lastDay = points.maxOf { it.date.toEpochDay() }
        val primary = FLPrimary
        val targetColor = GoalWarning
        Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = "${chart.title} chart, ${points.size} values" }) {
            fun x(day: Long): Float = if (lastDay == firstDay) size.width / 2 else ((day - firstDay).toFloat() / (lastDay - firstDay)) * (size.width - 16f) + 8f
            fun y(v: Double): Float = (size.height - ((v - lo) / (hi - lo)).toFloat() * size.height)
            if (chart.style == GoalChart.Style.BAR) {
                val barW = (size.width / (points.size * 1.6f)).coerceIn(3f, 24f)
                points.forEach { p ->
                    val cx = x(p.date.toEpochDay())
                    drawRect(primary, topLeft = Offset(cx - barW / 2, y(p.value)), size = androidx.compose.ui.geometry.Size(barW, size.height - y(p.value)))
                }
            } else {
                points.zipWithNext().forEach { (a, b) ->
                    drawLine(primary, Offset(x(a.date.toEpochDay()), y(a.value)), Offset(x(b.date.toEpochDay()), y(b.value)), strokeWidth = 4f)
                }
                points.forEach { drawCircle(primary, 6f, Offset(x(it.date.toEpochDay()), y(it.value))) }
            }
            chart.target?.let { t ->
                drawLine(targetColor.copy(alpha = 0.8f), Offset(0f, y(t)), Offset(size.width, y(t)), strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f)))
            }
        }
        Row {
            Text(GoalFormat.shortDate(points.first().date), fontSize = 11.sp, color = goalSecondaryText())
            Spacer(Modifier.weight(1f))
            chart.target?.let { Text("Target ${GoalFormat.number(it)} ${chart.unit}", fontSize = 11.sp, color = GoalWarning) }
            Spacer(Modifier.weight(1f))
            Text(GoalFormat.shortDate(points.last().date), fontSize = 11.sp, color = goalSecondaryText())
        }
        Text("Only recorded values are plotted — gaps mean no data.", fontSize = 11.sp, color = goalSecondaryText())
    }
}

@Composable
private fun WeeksCard(progress: GoalProgress) {
    GoalCard("Weekly history") {
        progress.weeks.reversed().forEach { week ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (week.inProgress) "This week" else "Week of ${GoalFormat.shortDate(week.weekStart)}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (week.daysWithData == 0 && !week.inProgress) {
                    Text("No data", fontSize = 13.sp, color = goalSecondaryText())
                } else {
                    Text("${week.completed} of ${week.required}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    val (icon, tint, label) = when {
                        week.met -> Triple(Icons.Filled.CheckCircle, FLPrimary, "Met")
                        week.inProgress -> Triple(Icons.Filled.RadioButtonUnchecked, goalSecondaryText(), "In progress")
                        else -> Triple(Icons.Filled.Cancel, GoalWarning, "Missed")
                    }
                    Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun NotesCard(goal: Goal, progress: GoalProgress) {
    GoalCard("How this is tracked") {
        progress.notes.forEach { GoalNote(it) }
        goal.settings.recommendationBasis?.let { GoalNote("Time frame: $it", Icons.Filled.AutoAwesome) }
        goal.settings.targetHistory?.takeIf { it.isNotEmpty() }?.let { history ->
            val text = history.joinToString(", ") { change ->
                val day = runCatching { java.time.OffsetDateTime.parse(change.changedAt).toLocalDate() }.getOrNull()
                "${GoalFormat.number(change.value)}" + (day?.let { " (until ${GoalFormat.shortDate(it)})" } ?: "")
            }
            GoalNote("Previous targets: $text", Icons.Filled.History)
        }
    }
}

@Composable
private fun RecentCard(progress: GoalProgress, onDelete: (GoalEntry) -> Unit) {
    val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    GoalCard("Recent entries") {
        progress.recent.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(item.date.format(dayFormat), item.detail).joinToString(" · "), fontSize = 12.sp, color = goalSecondaryText())
                }
                item.entry?.let { entry ->
                    Icon(Icons.Filled.Delete, contentDescription = "Delete entry", tint = goalSecondaryText(),
                        modifier = Modifier.size(20.dp).clickable { onDelete(entry) })
                }
            }
        }
    }
}

// MARK: - Sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalSheet(title: String, onDismiss: () -> Unit, confirmLabel: String = "Save", confirmEnabled: Boolean = true,
                      onConfirm: (() -> Unit)?, content: @Composable () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(if (onConfirm == null) "Done" else "Cancel") }
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (onConfirm != null) TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
            }
            content()
        }
    }
}

/** Check-in for a habit / routine / custom goal. Multi-step routines count only once every step is ticked. */
@Composable
fun GoalCheckInSheet(goal: Goal, activityKey: String?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings = goal.settings
    val steps = settings.steps ?: emptyList()
    val manualActivities = (settings.activities ?: emptyList()).filter { it.kind != GoalActivity.Kind.WORKOUT }
    var date by remember { mutableStateOf(GoalDay.today()) }
    var minutesText by remember { mutableStateOf("") }
    val done = remember { mutableStateOf(setOf<String>()) }
    var activity by remember { mutableStateOf(activityKey ?: manualActivities.firstOrNull()?.id) }
    val today = GoalDay.today()
    GoalSheet(settings.actionName ?: "Check In", onDismiss, confirmEnabled = steps.isEmpty() || done.value.size >= steps.size, onConfirm = {
        val key = GoalDay.key(date)
        scope.launch {
            // One check-in per day per activity — a second tap doesn't double count.
            val exists = GoalStore.goalEntries(goal.id).any { it.entryDate == key && it.activityKey == activity }
            if (!exists) GoalStore.addEntry(GoalEntry(goalId = goal.id, entryDate = key, activityKey = activity, value = minutesText.toDoubleOrNull()))
        }
        onDismiss()
    }) {
        if (manualActivities.isNotEmpty()) {
            GoalSegmented(manualActivities.map { it.id to it.name }, activity ?: manualActivities.first().id) { activity = it }
        }
        GoalDateRow("Date", date, { date = it }, min = today.minusDays(14), max = today)
        if (steps.isNotEmpty()) {
            Text("Steps", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = goalSecondaryText())
            steps.forEach { step ->
                val on = step in done.value
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable { done.value = if (on) done.value - step else done.value + step }.padding(vertical = 4.dp)) {
                    Icon(if (on) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, contentDescription = null,
                        tint = if (on) FLPrimary else goalSecondaryText())
                    Text(step)
                }
            }
            Text("The routine counts once every step is done.", fontSize = 12.sp, color = goalSecondaryText())
        }
        if (settings.sessionMinutes != null) GoalNumberField("Minutes (optional)", minutesText, { minutesText = it }, "min", decimal = false)
        settings.completionRule?.let { Text(it, fontSize = 12.sp, color = goalSecondaryText()) }
    }
}

/** Log an amount toward a custom or servings goal. */
@Composable
fun GoalAmountSheet(goal: Goal, unit: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(GoalDay.today()) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val today = GoalDay.today()
    val value = amountText.toDoubleOrNull() ?: 0.0
    GoalSheet("Log ${if (unit.isEmpty()) "Activity" else unit.replaceFirstChar { it.titlecase(Locale.getDefault()) }}", onDismiss,
        confirmEnabled = value > 0, onConfirm = {
            scope.launch { GoalStore.addEntry(GoalEntry(goalId = goal.id, entryDate = GoalDay.key(date), value = value, note = note.ifEmpty { null })) }
            onDismiss()
        }) {
        GoalNumberField("Amount", amountText, { amountText = it }, unit)
        GoalDateRow("Date", date, { date = it }, min = today.minusDays(14), max = today)
        GoalTextField(note, { note = it }, "Note (optional)")
        goal.settings.actionName?.let { Text("Counts toward: $it", fontSize = 12.sp, color = goalSecondaryText()) }
    }
}

/** Manual sleep log for one night, keyed by the morning it ended. */
@Composable
fun GoalSleepLogSheet(goal: Goal, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var morning by remember { mutableStateOf(GoalDay.today()) }
    var hours by remember { mutableIntStateOf(7) }
    var minutes by remember { mutableIntStateOf(0) }
    val today = GoalDay.today()
    GoalSheet("Log Sleep", onDismiss, confirmEnabled = hours * 60 + minutes > 0, onConfirm = {
        val key = GoalDay.key(morning)
        scope.launch {
            // Replace an earlier manual log for the same night.
            GoalStore.goalEntries(goal.id).filter { it.entryDate == key }.forEach { GoalStore.deleteEntry(it) }
            GoalStore.addEntry(GoalEntry(goalId = goal.id, entryDate = key, value = (hours * 60 + minutes).toDouble()))
        }
        onDismiss()
    }) {
        GoalDateRow("Night ending", morning, { morning = it }, min = today.minusDays(14), max = today)
        GoalStepper("$hours h", hours, 0..14, { hours = it })
        GoalStepper("$minutes min", minutes, 0..55, { minutes = it }, step = 5)
        Text("Manual entries are labelled as manual. If Health Connect has data for the same night, its value is used.",
            fontSize = 12.sp, color = goalSecondaryText())
    }
}

/** Quick-add water through the existing water tracker. */
@Composable
fun GoalWaterSheet(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val unit = remember { HydrationStore.displayUnit }
    var customText by remember { mutableStateOf("") }
    fun add(amount: Double) {
        scope.launch {
            runCatching { HydrationStore.log(amount, unit, GoalDay.key(GoalDay.today())) }
            onDismiss()
        }
    }
    GoalSheet("Log Water", onDismiss, onConfirm = null) {
        Text("Quick add", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = goalSecondaryText())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (if (unit == WaterUnit.ML) listOf(250.0, 500.0, 750.0) else listOf(8.0, 16.0, 24.0)).forEach { amount ->
                GoalChip("+${GoalFormat.number(amount, 0)}") { add(amount) }
            }
        }
        Text("Custom", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = goalSecondaryText())
        GoalNumberField("Amount", customText, { customText = it }, unit.rawValue)
        GoalPrimaryButton("Add", enabled = (customText.toDoubleOrNull() ?: 0.0) > 0) { customText.toDoubleOrNull()?.let { add(it) } }
    }
}

/** Edit name, target, deadline, weekly frequency, and reminders. History is never rewritten; a target change is kept in the target history. */
@Composable
fun GoalEditSheet(goal: Goal, focusDeadline: Boolean, onDismiss: () -> Unit, onSave: (Goal) -> Unit) {
    val subtype = goal.subtype
    var title by remember { mutableStateOf(goal.title) }
    var targetText by remember { mutableStateOf(goal.targetValue?.let { GoalSetupViewModel.plain(it) } ?: "") }
    var sleepTarget by remember { mutableStateOf(goal.targetValue ?: 420.0) }
    var frequencyTarget by remember { mutableIntStateOf(goal.frequencyTarget ?: 1) }
    var hasDeadline by remember { mutableStateOf(goal.targetDate != null) }
    val baseDeadline = goal.targetDate?.let { GoalDay.localDate(it) } ?: GoalDay.today()
    var deadline by remember {
        mutableStateOf(if (focusDeadline) maxOf(baseDeadline, GoalDay.today()).plusWeeks(4) else baseDeadline)
    }
    val reminder = goal.settings.reminder
    var reminderOn by remember { mutableStateOf(reminder?.enabled ?: false) }
    var reminderTime by remember { mutableStateOf(LocalTime.of(reminder?.hour ?: 19, reminder?.minute ?: 0)) }

    val editsTarget = subtype in setOf(GoalSubtype.LoseWeight, GoalSubtype.ImproveLift, GoalSubtype.PersonalRecord, GoalSubtype.RepsAtWeight,
        GoalSubtype.TrainingVolume, GoalSubtype.RecoveryMetric, GoalSubtype.Hydration, GoalSubtype.CustomGoal, GoalSubtype.HealthyHabit)
    val editsFrequency = subtype != null && subtype.isFrequencyGoal && subtype != GoalSubtype.CustomRoutine && goal.frequency == GoalFrequency.Weekly
    val startDay = GoalDay.localDate(goal.startDate)

    GoalSheet(if (focusDeadline) "Extend Deadline" else "Edit Goal", onDismiss, confirmEnabled = title.isNotBlank(), onConfirm = {
        var updated = goal.copy(title = title.trim())
        val value = targetText.toDoubleOrNull()
        if (editsTarget && value != null && value > 0) updated = updated.copy(targetValue = value)
        if (subtype == GoalSubtype.SleepDuration || subtype == GoalSubtype.SleepConsistency) updated = updated.copy(targetValue = sleepTarget)
        if (editsFrequency) updated = updated.copy(frequencyTarget = frequencyTarget)
        updated = updated.copy(targetDate = if (hasDeadline) GoalDay.startOfDayMillis(deadline) else null)
        if (subtype != null && subtype.isFrequencyGoal && subtype !in setOf(GoalSubtype.SleepConsistency, GoalSubtype.HealthyHabit, GoalSubtype.CustomRoutine)) {
            updated = updated.copy(targetValue = (updated.frequencyTarget ?: 1).toDouble())
        }
        val settings = updated.settings
        updated = updated.withSettings(settings.copy(reminder = GoalReminder(reminderOn, reminderTime.hour, reminderTime.minute, settings.weekdays)))
        onSave(updated)
        onDismiss()
    }) {
        Text("GOAL", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = goalSecondaryText())
        GoalTextField(title, { title = it }, "Name")
        if (editsTarget) GoalNumberField("Target", targetText, { targetText = it }, goal.targetUnit)
        if (subtype == GoalSubtype.SleepDuration || subtype == GoalSubtype.SleepConsistency) {
            GoalStepper("Target: ${GoalFormat.sleep(sleepTarget)}", sleepTarget.toInt(), 240..720, { sleepTarget = it.toDouble() }, step = 15)
        }
        if (editsFrequency) GoalStepper("$frequencyTarget per week", frequencyTarget, 1..7, { frequencyTarget = it })
        if (subtype == GoalSubtype.CalorieTarget || subtype == GoalSubtype.MacroTarget) {
            GoalLinkButton("Edit Nutrition targets") { onDismiss(); AppRouter.push("nutritionGoalSettings") }
            Text("This goal uses your Nutrition targets.", fontSize = 12.sp, color = goalSecondaryText())
        }
        Text("TIME FRAME", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = goalSecondaryText())
        GoalToggle("Target date", hasDeadline, { hasDeadline = it })
        if (hasDeadline) GoalDateRow("Deadline", deadline, { deadline = it }, min = startDay)
        Text("REMINDERS", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = goalSecondaryText())
        GoalToggle("Remind me", reminderOn, { reminderOn = it })
        if (reminderOn) GoalTimeRow("Time", reminderTime) { reminderTime = it }
        Text("Editing keeps your history. Past entries aren't changed.", fontSize = 12.sp, color = goalSecondaryText())
    }
}
