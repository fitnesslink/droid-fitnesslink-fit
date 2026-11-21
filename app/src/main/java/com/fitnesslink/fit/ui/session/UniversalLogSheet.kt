package com.fitnesslink.fit.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.exerciseHistorySets
import com.fitnesslink.fit.persistence.exerciseNotes
import com.fitnesslink.fit.persistence.sessionExerciseNotes
import com.fitnesslink.fit.persistence.upsertSessionExerciseNote
import com.fitnesslink.fit.session.ActiveWorkoutSession
import com.fitnesslink.fit.session.ExerciseHistorySession
import com.fitnesslink.fit.session.ExercisePerformanceSummary
import com.fitnesslink.fit.session.LogMetric
import com.fitnesslink.fit.session.LogMetricProfile
import com.fitnesslink.fit.session.MovementHistoryStore
import com.fitnesslink.fit.session.RPEScale
import com.fitnesslink.fit.session.SessionExerciseNote
import com.fitnesslink.fit.session.SpeedUnit
import com.fitnesslink.fit.session.UniversalSetValues
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Which mode opened the logging screen. The screen is the same in all three (PRD §1.3, AC-2); the
 * mode only decides whether programmed targets exist (not in Quick Start — the session's
 * `lastMode` carries that) and how Add Set prefills.
 */
enum class UniversalLogMode { QUICK_START, PLAYLIST, INTERACTIVE }

private val RowHeight = 52.dp
private val HeaderHeight = 26.dp
private val SetColumnWidth = 48.dp
private val MetricColumnWidth = 74.dp
private val RpeColumnWidth = 52.dp
private val CircleColumnWidth = 40.dp
private val MenuColumnWidth = 30.dp
private val CellGap = 6.dp

/**
 * Universal Workout Logging (PRD v2.0 §3): one exercise's header and history cards, every set
 * with its metrics, completion circle and ⋯ menu, + Add Set / + Add Metric, Exercise RPE,
 * Training Notes and Done. Port of iOS `UniversalLogSheet`.
 *
 * Everything auto-saves into [ActiveWorkoutSession] as it changes — there is no Save Set (§6.1).
 * Done and the close button only dismiss: they never complete sets or clear values (§6.5). The
 * sheet never touches the workout / set timers, so Interactive Mode keeps running underneath (§2.3).
 *
 * [initialSetNumber] is the set to highlight on open (a tapped circle / the interactive cursor);
 * null → the first incomplete set (§3.2). A number past the last set adds a set first (Quick
 * Start's open ⊕ circle). [onChange] runs after every change so a mode can mirror it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalLogSheet(
    task: WorkoutTask,
    @Suppress("UNUSED_PARAMETER") mode: UniversalLogMode,
    initialSetNumber: Int? = null,
    onChange: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val session = ActiveWorkoutSession
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    // Re-read the session on every mutation (not on every timer tick).
    val sessionVersion by remember { session.state.map { it.version }.distinctUntilChanged() }.collectAsState(initial = session.state.value.version)
    /** Bumped by structural edits (add / delete set, metric changes) so cells re-read their values. */
    var revision by remember { mutableIntStateOf(0) }

    val unit = if (task.groupId.isEmpty()) "Set" else "Round"

    // Setup (once per task).
    val profile = remember(task.id) {
        val anatomy = task.movementId?.let { id -> DatabaseManager.anatomyIds(id).mapNotNull { ClassificationStore.anatomyName(it) } } ?: emptyList()
        LogMetricProfile.forTask(task, anatomy)
    }
    val cardio = profile.defaults.contains(LogMetric.DISTANCE) && profile.defaults.contains(LogMetric.DURATION)
    val historyData = remember(task.id) {
        val userId = DatabaseManager.user()?.id ?: "user1"
        val found = DatabaseManager.exerciseHistorySets(task.movementId, task.name, userId, session.sessionId)
        val summary = ExercisePerformanceSummary.build(found.sets)
        val ids = found.sets.map { it.sessionId }.distinct()
        val notes = DatabaseManager.exerciseNotes(task.movementId, task.name, ids)
        summary to ExerciseHistorySession.group(found.sets, found.names, notes)
    }
    val summary = historyData.first
    val history = historyData.second
    val initialNote = remember(task.id) { DatabaseManager.sessionExerciseNotes(session.sessionId).firstOrNull { it.taskId == task.id } }

    var activeSet by remember(task.id) {
        mutableStateOf(initialSetNumber?.takeIf { it <= session.setCount(task.id) } ?: session.highlightedSet(task.id))
    }
    var expandedRPESet by remember { mutableStateOf<Int?>(null) }
    var durationTarget by remember { mutableStateOf<Int?>(null) }
    var showMetricSheet by remember { mutableStateOf(false) }
    var setPendingDelete by remember { mutableStateOf<Int?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var exerciseRPEExpanded by remember { mutableStateOf(false) }
    var notesExpanded by remember { mutableStateOf(false) }
    var exerciseRPE by remember(task.id) { mutableStateOf(initialNote?.rpe) }
    var notes by remember(task.id) { mutableStateOf(initialNote?.trainingLog ?: "") }

    fun changed() { revision += 1; onChange() }

    // A tapped open ⊕ past the last set adds the set first (Quick Start).
    LaunchedEffect(task.id) {
        if (initialSetNumber != null && initialSetNumber > session.setCount(task.id)) {
            activeSet = session.addSet(task)
            changed()
        }
    }

    // Subscribe this composition to session + structural changes.
    @Suppress("UNUSED_VARIABLE") val readVersion = sessionVersion + revision
    val setCount = session.setCount(task.id)
    val setNumbers = if (setCount > 0) (1..setCount).toList() else emptyList()
    val visibleMetrics = profile.visible(
        session.addedMetrics(task.id),
        UniversalSetValues.metricsWithValues(setNumbers.mapNotNull { session.actualValues(task.id, it) })
    )

    fun value(metric: LogMetric, n: Int): Double? = session.actualValues(task.id, n)?.let { UniversalSetValues.value(metric, it) }
    fun rpeValue(n: Int): Int? = session.actualValues(task.id, n)?.rpe
    fun prescribed(metric: LogMetric, n: Int): Double? = session.prescribedSet(task, n)?.let { UniversalSetValues.value(metric, it) }

    /**
     * Auto-save one edit. The first edit of an untouched set starts from its target, so completing
     * it later keeps every programmed value the user didn't change.
     */
    fun commit(n: Int, edit: (TaskSetConfig) -> TaskSetConfig) {
        var values = session.actualValues(task.id, n) ?: session.prescribedSet(task, n) ?: TaskSetConfig(setNumber = n, reps = 0)
        // Distance shown in the user's unit: normalise an older value.
        val d = values.distanceValue
        if (d != null && d.unit != DistanceUnit.current) {
            val c = d.converted(DistanceUnit.current)
            values = values.copy(distance = c.value, distanceUnit = c.unit.rawValue)
        }
        session.updateSetValues(task, n, edit(values))
        onChange()
    }

    fun toggleCompletion(n: Int) {
        focusManager.clearFocus()
        val wasDone = session.isSetCompleted(task.id, n)
        session.toggleSetCompletion(task, n)
        if (!wasDone) {
            // Move the highlight on to the next incomplete set.
            activeSet = session.highlightedSet(task.id) ?: n
            if (expandedRPESet == n) expandedRPESet = null
        } else {
            activeSet = n
        }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        changed()
    }

    fun saveNote() {
        val trimmed = notes.trim()
        DatabaseManager.upsertSessionExerciseNote(SessionExerciseNote(
            sessionId = session.sessionId, taskId = task.id, movementId = task.movementId, taskName = task.name,
            rpe = exerciseRPE?.let(RPEScale::clamp), trainingLog = if (trimmed.isEmpty()) null else notes,
            updatedAtMs = ActiveWorkoutSession.clock()
        ))
        onChange()
    }

    fun close() { focusManager.clearFocus(); onDismiss() }

    ModalBottomSheet(onDismissRequest = { close() }, sheetState = sheetState, containerColor = FLTheme.colors.background, contentColor = FLTheme.colors.textPrimary) {
        if (showHistory) {
            BackHandler { showHistory = false }
            ExerciseHistoryContent(task, history) { showHistory = false }
            return@ModalBottomSheet
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = FLTheme.colors.textPrimary, modifier = Modifier.size(32.dp).clickable { close() }.padding(4.dp))
                Text("Log Exercise", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(32.dp))
            }

            // A. Header (§3.1 A)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val thumb = (task.movementThumbnailId ?: task.movementImageId)?.let { MediaRef.MovementThumbnail(it) }
                Box(Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)).background(FLTheme.colors.imagePlaceholder)) { if (thumb != null) FLImageView(ref = thumb, modifier = Modifier.size(60.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(task.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, FLTheme.colors.textSecondary.copy(alpha = 0.4f), RoundedCornerShape(50))
                            .clickable(onClickLabel = "Opens your previous sessions of this exercise") { focusManager.clearFocus(); showHistory = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = FLTheme.colors.textPrimary, modifier = Modifier.size(13.dp))
                        Text("View Exercise History", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary)
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = FLTheme.colors.textPrimary, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // B. Performance summary (§4)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                performanceCards(task, profile, visibleMetrics, summary, session.sessionId).forEach { PerformanceCard(it, Modifier.weight(1f)) }
            }

            // C. Set table (§3.1 C, §3.3)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${unit}s ($setCount)", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
                // One horizontal scroll position shared by every row's metric area, so the metric
                // columns scroll together while Set (left) and circle / ⋯ (right) stay fixed.
                val hScroll = rememberScrollState()
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FLTheme.colors.surface).padding(vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth().height(HeaderHeight).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        HeaderCell(unit, SetColumnWidth - 4.dp, TextAlign.Start, Modifier.padding(start = 6.dp))
                        MetricArea(hScroll) {
                            visibleMetrics.forEach { metric ->
                                val removable = session.addedMetrics(task.id).contains(metric) && !profile.defaults.contains(metric)
                                HeaderCell(metric.header, MetricColumnWidth, TextAlign.Center,
                                    if (removable) Modifier.clickable(onClickLabel = "Manage metrics") { showMetricSheet = true } else Modifier)
                            }
                            HeaderCell("RPE", RpeColumnWidth, TextAlign.Center)
                        }
                        Spacer(Modifier.width(CircleColumnWidth + MenuColumnWidth))
                    }
                    setNumbers.forEach { n ->
                        key(n) {
                            val done = session.isSetCompleted(task.id, n)
                            val active = activeSet == n
                            Row(
                                Modifier.fillMaxWidth().height(RowHeight).padding(horizontal = 4.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) PurpleTheme.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent)
                                    .border(1.5.dp, if (active) PurpleTheme else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Fixed: set identifier.
                                Box(Modifier.width(SetColumnWidth - 4.dp).height(RowHeight).clickable { activeSet = n }.padding(start = 6.dp), contentAlignment = Alignment.CenterStart) {
                                    Text("$n", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
                                }
                                // Scrollable: the exercise's metrics + Set RPE.
                                MetricArea(hScroll) {
                                    visibleMetrics.forEach { metric ->
                                        val actual = value(metric, n)
                                        val target = prescribed(metric, n)
                                        if (metric == LogMetric.DURATION) {
                                            Box(
                                                Modifier.width(MetricColumnWidth).height(38.dp).clip(RoundedCornerShape(8.dp)).background(FLTheme.colors.background)
                                                    .clickable(onClickLabel = "$unit $n time") { focusManager.clearFocus(); activeSet = n; durationTarget = n },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text((actual ?: target)?.let { UniversalSetValues.formatDuration(it.toInt()) } ?: "–", fontSize = 16.sp,
                                                    fontWeight = if (actual != null) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (actual != null) FLTheme.colors.textPrimary else FLTheme.colors.textSecondary)
                                            }
                                        } else {
                                            key(metric, revision) {
                                                NumericMetricField(
                                                    value = actual?.let { display(metric, it) },
                                                    placeholder = target?.let { UniversalSetValues.formatNumber(display(metric, it)) } ?: "–",
                                                    decimal = metric != LogMetric.REPS,
                                                    label = "$unit $n ${metric.title}",
                                                    onFocus = { activeSet = n; if (expandedRPESet != n) expandedRPESet = null },
                                                    onCommit = { shown -> commit(n) { store(metric, shown, it) } }
                                                )
                                            }
                                        }
                                    }
                                    val rpe = rpeValue(n)
                                    Box(
                                        Modifier.width(RpeColumnWidth).height(38.dp).clip(RoundedCornerShape(8.dp)).background(FLTheme.colors.background)
                                            .border(1.5.dp, if (expandedRPESet == n) PurpleTheme else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
                                            .clickable { focusManager.clearFocus(); activeSet = n; expandedRPESet = if (expandedRPESet == n) null else n }
                                            .semantics { contentDescription = "$unit $n RPE"; stateDescription = rpe?.let { "$it, ${RPEScale.level(it).label}" } ?: "Not set" },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(rpe?.toString() ?: "–", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (rpe != null) OrangeTheme else FLTheme.colors.textSecondary)
                                    }
                                }
                                // Fixed: completion circle + ⋯ menu.
                                Box(Modifier.width(CircleColumnWidth).height(RowHeight).clickable(onClickLabel = if (done) "Mark $unit $n incomplete" else "Complete ${unit.lowercase()} $n") { toggleCompletion(n) }, contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = if (done) "$unit $n completed" else "Complete ${unit.lowercase()} $n",
                                        tint = if (done) CompletedGreen else FLTheme.colors.textSecondary, modifier = Modifier.size(28.dp)
                                    )
                                }
                                var menu by remember { mutableStateOf(false) }
                                Box(Modifier.width(MenuColumnWidth).height(RowHeight).clickable { menu = true }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.MoreHoriz, contentDescription = "$unit $n options", tint = FLTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Delete $unit", color = if (setCount > 1) FLTheme.colors.danger else FLTheme.colors.textTertiary) },
                                            enabled = setCount > 1,
                                            onClick = { menu = false; setPendingDelete = n }
                                        )
                                    }
                                }
                            }
                            // Inline Set RPE (§7.2): full width beneath its set, outside the column
                            // rows so it never changes the columns' layout.
                            if (expandedRPESet == n) {
                                RPEPanel(
                                    title = "$unit $n – Set RPE", value = rpeValue(n), cardio = cardio,
                                    onValueChange = { v -> commit(n) { it.copy(rpe = v) } },
                                    onClose = { expandedRPESet = null },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // D. Logging controls (§3.1 D)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedCapsuleButton("Add $unit", Modifier.weight(1f), icon = Icons.Filled.Add) {
                    focusManager.clearFocus()
                    activeSet = session.addSet(task)
                    changed()
                }
                Row(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(50)).background(FLPrimary).clickable { focusManager.clearFocus(); showMetricSheet = true },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = FLTheme.colors.onBrand, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Metric", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.onBrand)
                }
            }

            // E. Exercise feedback (§7.3, §8)
            CollapsibleCard(
                title = "Exercise RPE", icon = Icons.Filled.BarChart, tint = FLPrimary,
                summary = exerciseRPE?.let { "$it · ${RPEScale.level(it, cardio).label}" },
                expanded = exerciseRPEExpanded, onToggle = { exerciseRPEExpanded = !exerciseRPEExpanded }
            ) {
                RPEPanel(title = null, value = exerciseRPE, cardio = cardio, onValueChange = { exerciseRPE = it; saveNote() }, onClose = null)
            }
            CollapsibleCard(
                title = "Training Notes", icon = Icons.Filled.Description, tint = FLPrimary,
                summary = if (notes.isBlank()) null else "Added",
                expanded = notesExpanded, onToggle = { notesExpanded = !notesExpanded }
            ) {
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it; saveNote() }, minLines = 3, maxLines = 8,
                    placeholder = { Text("Form, fatigue, discomfort, equipment, anything you noticed…", fontSize = 14.sp) },
                    textStyle = TextStyle(fontSize = 14.sp, color = FLTheme.colors.textPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            PrimaryCapsuleButton("Done", height = 50) { close() }
        }
    }

    // MARK: Sheets & dialogs

    durationTarget?.let { n ->
        DurationPickerSheet(
            title = "$unit $n – Time (Duration)",
            initialSeconds = (value(LogMetric.DURATION, n) ?: prescribed(LogMetric.DURATION, n) ?: 0.0).toInt(),
            onApply = { seconds -> commit(n) { it.copy(durationSeconds = seconds.takeIf { s -> s > 0 }) } },
            onDismiss = { durationTarget = null }
        )
    }
    if (showMetricSheet) {
        MetricSelectorSheet(
            taskName = task.name,
            addable = profile.addable(visibleMetrics),
            added = session.addedMetrics(task.id).filter { !profile.defaults.contains(it) },
            hasValues = { session.hasValues(task.id, it) },
            onAdd = { metrics -> metrics.forEach { session.addMetric(task.id, it) }; changed() },
            onRemove = { metric -> session.removeMetric(task, metric); changed() },
            onDismiss = { showMetricSheet = false }
        )
    }
    setPendingDelete?.let { n ->
        AlertDialog(
            onDismissRequest = { setPendingDelete = null },
            title = { Text("Delete $unit $n?") },
            text = { Text("Are you sure you want to delete this ${unit.lowercase()}? Its recorded performance will be removed from your current workout.") },
            confirmButton = {
                TextButton(onClick = {
                    setPendingDelete = null
                    session.deleteSet(task, n)
                    expandedRPESet = null
                    activeSet = session.highlightedSet(task.id)
                    changed()
                }) { Text("Delete $unit", color = FLTheme.colors.danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { setPendingDelete = null }) { Text("Cancel") } }
        )
    }
}

/** The metric columns between the fixed Set column and the fixed circle / menu columns: the remaining width, scrolled horizontally. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.MetricArea(scroll: ScrollState, content: @Composable () -> Unit) {
    Box(Modifier.weight(1f).fillMaxWidth()) {
        Row(Modifier.horizontalScroll(scroll).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(CellGap), verticalAlignment = Alignment.CenterVertically) {
            content()
        }
    }
}

@Composable
private fun HeaderCell(title: String, width: androidx.compose.ui.unit.Dp, align: TextAlign, modifier: Modifier = Modifier) {
    Box(Modifier.width(width).height(HeaderHeight).then(modifier), contentAlignment = if (align == TextAlign.Start) Alignment.CenterStart else Alignment.Center) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FLTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align)
    }
}

/** The performance cards for this exercise's profile (§4): Last Time + Top Set / Best Time / Best Distance + Suggested (Coming Soon). */
private fun performanceCards(
    task: WorkoutTask, profile: LogMetricProfile, visible: List<LogMetric>, summary: ExercisePerformanceSummary, sessionId: String
): List<PerformanceCardModel> {
    val out = mutableListOf<PerformanceCardModel>()
    fun distance(s: com.fitnesslink.fit.session.HistoricalSet?): String? = s?.distance?.let { DistanceUnit.displayInCurrent(it.value, it.unit) }
    val primary = profile.defaults.firstOrNull() ?: LogMetric.REPS
    when {
        primary == LogMetric.DURATION && profile.defaults.contains(LogMetric.DISTANCE) -> {
            // Cardio: last session's time + distance, best distance.
            val last = listOfNotNull(summary.lastDuration?.durationSeconds?.let(UniversalSetValues::formatDuration), distance(summary.lastDistance)).joinToString(" · ")
            out += PerformanceCardModel("Last Time", Icons.Filled.Schedule, CompletedGreen, last.ifEmpty { null }, (summary.lastDistance ?: summary.lastDuration)?.dateMs)
            out += PerformanceCardModel("Best Distance", Icons.Filled.EmojiEvents, PurpleTheme, distance(summary.bestDistance), summary.bestDistance?.dateMs)
        }
        primary == LogMetric.DURATION -> {
            out += PerformanceCardModel("Last Time", Icons.Filled.Schedule, CompletedGreen, summary.lastDuration?.durationSeconds?.let(UniversalSetValues::formatDuration), summary.lastDuration?.dateMs)
            out += PerformanceCardModel("Best Time", Icons.Filled.EmojiEvents, PurpleTheme, summary.bestTime?.durationSeconds?.let(UniversalSetValues::formatDuration), summary.bestTime?.dateMs)
        }
        else -> {
            // Server-seeded figures (fresh install, FTNSSLNK-408) when no local history exists yet.
            val fallbackLast = MovementHistoryStore.previousWeight(task.movementId, task.name, sessionId)?.let { ExercisePerformanceSummary.weightRepsLine(it.weightLbs, it.reps) }
            out += PerformanceCardModel("Last Time", Icons.Filled.Schedule, CompletedGreen,
                summary.lastTime?.let(ExercisePerformanceSummary::weightRepsLine) ?: fallbackLast, summary.lastTime?.dateMs)
            if (visible.contains(LogMetric.DISTANCE) && summary.bestDistance != null) {
                out += PerformanceCardModel("Best Distance", Icons.Filled.EmojiEvents, PurpleTheme, distance(summary.bestDistance), summary.bestDistance.dateMs)
            } else {
                val fallbackTop = MovementHistoryStore.topSet(task.movementId, task.name, sessionId)?.let { ExercisePerformanceSummary.weightRepsLine(it.maxWeight, it.reps) }
                out += PerformanceCardModel("Top Set", Icons.Filled.EmojiEvents, PurpleTheme,
                    summary.topSet?.let(ExercisePerformanceSummary::weightRepsLine) ?: fallbackTop, summary.topSet?.dateMs)
            }
        }
    }
    out += PerformanceCardModel("Suggested", Icons.Filled.WorkspacePremium, OrangeTheme, null, null, comingSoon = true)
    return out
}

/** Stored value → the number shown in the user's units. */
private fun display(metric: LogMetric, stored: Double): Double = when (metric) {
    LogMetric.WEIGHT -> WeightUnit.displayNumber(fromLbs = stored).toDoubleOrNull() ?: stored
    LogMetric.SPEED -> SpeedUnit.current.fromMph(stored)
    else -> stored
}

/** A number typed in the user's units → the stored set config. */
private fun store(metric: LogMetric, shown: Double, c: TaskSetConfig): TaskSetConfig = when (metric) {
    LogMetric.REPS -> c.copy(reps = shown.toInt())
    LogMetric.WEIGHT -> c.copy(weightKg = if (shown > 0) WeightUnit.storageLbs(fromDisplay = shown) else null)
    LogMetric.DURATION -> c.copy(durationSeconds = if (shown > 0) shown.toInt() else null)
    // Entered in the user's unit; an earlier value in another unit is replaced, not mixed.
    LogMetric.DISTANCE -> c.copy(distance = if (shown > 0) shown else null, distanceUnit = if (shown > 0) DistanceUnit.current.rawValue else null)
    LogMetric.SPEED -> c.copy(speedMph = if (shown > 0) SpeedUnit.current.toMph(shown) else null)
    LogMetric.INCLINE -> c.copy(incline = if (shown > 0) shown else null)
}

/**
 * One manual-entry metric cell (§11.1): digits (and one decimal point where allowed) only, saved
 * on every keystroke, the target shown as a placeholder until the user types. Focus selects the
 * whole value so a prefilled number is easy to replace.
 */
@Composable
private fun NumericMetricField(
    value: Double?, placeholder: String, decimal: Boolean, label: String,
    onFocus: () -> Unit, onCommit: (Double) -> Unit
) {
    var field by remember { mutableStateOf(TextFieldValue(value?.let(UniversalSetValues::formatNumber) ?: "")) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // External change (carry-forward, metric removal, unit normalising): show it unless the user is mid-edit here.
    LaunchedEffect(value) {
        if (focused) return@LaunchedEffect
        val text = value?.let(UniversalSetValues::formatNumber) ?: ""
        if (text != field.text) field = TextFieldValue(text)
    }
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        delay(40)
        field = field.copy(selection = TextRange(0, field.text.length))
    }

    BasicTextField(
        value = field,
        onValueChange = { new ->
            val clean = UniversalSetValues.sanitize(new.text, decimal)
            val textChanged = clean != field.text
            field = if (clean == new.text) new else TextFieldValue(clean, TextRange(clean.length))
            if (!textChanged) return@BasicTextField
            val parsed = if (decimal) UniversalSetValues.parseDecimal(clean) else UniversalSetValues.parseReps(clean)?.toDouble()
            if (parsed != null && (parsed != (value ?: 0.0) || (clean.isEmpty() && value != null))) onCommit(parsed)
        },
        singleLine = true,
        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(PurpleTheme),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = Modifier.width(MetricColumnWidth).height(38.dp)
            .clip(RoundedCornerShape(8.dp)).background(FLTheme.colors.background)
            .border(1.5.dp, if (focused) PurpleTheme else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
            .onFocusChanged { state ->
                if (state.isFocused && !focused) onFocus()
                focused = state.isFocused
            }
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                if (field.text.isEmpty()) Text(placeholder, fontSize = 16.sp, color = FLTheme.colors.textSecondary, textAlign = TextAlign.Center, maxLines = 1)
                inner()
            }
        }
    )
}
