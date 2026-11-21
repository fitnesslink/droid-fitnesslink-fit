package com.fitnesslink.fit.ui.session

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.session.ExerciseHistorySession
import com.fitnesslink.fit.session.LogMetric
import com.fitnesslink.fit.session.RPEScale
import com.fitnesslink.fit.session.UnconfirmedSetItem
import com.fitnesslink.fit.session.UniversalSetValues
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Building blocks of the Universal Workout Logging screen (`UniversalLogSheet`), PRD v2.0 §4, §5,
// §7–9, §11, §14. Port of iOS `UniversalLogComponents.swift`. Filled primary buttons use the brand
// green ([FLPrimary]); purple is for accents only (active set, RPE, selections, Coming Soon).

internal val CompletedGreen = Color(0xFF34C759)

internal fun formatLogDate(ms: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ms))

internal val LogMetric.icon: ImageVector
    get() = when (this) {
        LogMetric.REPS -> Icons.Filled.Tag
        LogMetric.WEIGHT -> Icons.Filled.FitnessCenter
        LogMetric.DURATION -> Icons.Filled.Timer
        LogMetric.DISTANCE -> Icons.Filled.Route
        LogMetric.SPEED -> Icons.Filled.Speed
        LogMetric.INCLINE -> Icons.AutoMirrored.Filled.TrendingUp
    }

/** Full-width filled capsule in the brand green (Done, Add Metric, Apply, Review Sets). */
@Composable
internal fun PrimaryCapsuleButton(title: String, modifier: Modifier = Modifier, height: Int = 48, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(50)).background(if (enabled) FLPrimary else FLTheme.colors.disabled)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.onBrand) }
}

/** Outlined capsule (Add Set, Finish Without Completing). */
@Composable
internal fun OutlinedCapsuleButton(title: String, modifier: Modifier = Modifier, height: Int = 44, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(50)).border(1.dp, FLTheme.colors.textSecondary.copy(alpha = 0.5f), RoundedCornerShape(50)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = FLTheme.colors.textPrimary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary)
    }
}

// MARK: - Performance card (§3.1 B, §4)

data class PerformanceCardModel(
    val title: String,
    val icon: ImageVector,
    val tint: Color,
    val value: String?,
    val dateMs: Long?,
    val comingSoon: Boolean = false
)

@Composable
fun PerformanceCard(card: PerformanceCardModel, modifier: Modifier = Modifier) {
    Column(
        modifier.heightIn(min = 84.dp).clip(RoundedCornerShape(10.dp)).background(FLTheme.colors.surface).padding(10.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(card.icon, contentDescription = null, tint = card.tint, modifier = Modifier.size(12.dp))
            Text(card.title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = FLTheme.colors.textSecondary, maxLines = 1)
        }
        if (card.comingSoon) {
            Text("Coming Soon", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PurpleTheme,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(PurpleTheme.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 3.dp))
        } else {
            val value = card.value
            Text(value ?: "No history yet", fontSize = if (value == null) 12.sp else 15.sp,
                fontWeight = if (value == null) FontWeight.Normal else FontWeight.Bold,
                color = if (value == null) FLTheme.colors.textSecondary else FLTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            card.dateMs?.let { Text(formatLogDate(it), fontSize = 10.sp, color = FLTheme.colors.textSecondary) }
        }
    }
}

// MARK: - RPE panel (§7.2–7.5)

/**
 * Number, short label, one explanatory sentence and a 1–10 slider. Used inline under a set (Set
 * RPE) and inside the Exercise RPE card; the three update together as the slider moves (QA-066).
 */
@Composable
fun RPEPanel(title: String?, value: Int?, cardio: Boolean, onValueChange: (Int?) -> Unit, onClose: (() -> Unit)?, modifier: Modifier = Modifier) {
    val shown = value ?: 5
    val level = RPEScale.level(shown, cardio)
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FLTheme.colors.background).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                if (value != null) Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FLTheme.colors.textSecondary,
                    modifier = Modifier.clickable { onValueChange(null) }.padding(horizontal = 8.dp, vertical = 4.dp))
                if (onClose != null) Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Close Set RPE", tint = FLTheme.colors.textSecondary,
                    modifier = Modifier.size(28.dp).clickable(onClick = onClose).padding(4.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(48.dp).height(44.dp).border(1.dp, FLTheme.colors.textSecondary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text(value?.toString() ?: "–", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (value == null) "Slide to rate" else level.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (value == null) FLTheme.colors.textSecondary else PurpleTheme).padding(horizontal = 8.dp, vertical = 3.dp))
                Text(if (value == null) "How hard did this feel?" else level.explanation, fontSize = 12.sp, color = FLTheme.colors.textSecondary, maxLines = 2)
            }
        }
        Slider(
            value = shown.toFloat(),
            onValueChange = { raw -> val v = RPEScale.clamp(raw.roundToInt()); if (v != value) onValueChange(v) },
            valueRange = 1f..10f, steps = 8,
            colors = SliderDefaults.colors(
                thumbColor = PurpleTheme, activeTrackColor = PurpleTheme, inactiveTrackColor = PurpleTheme.copy(alpha = 0.18f),
                activeTickColor = Color.White.copy(alpha = 0.6f), inactiveTickColor = PurpleTheme.copy(alpha = 0.6f)
            ),
            modifier = Modifier.semantics {
                contentDescription = "RPE"
                stateDescription = value?.let { "$it, ${RPEScale.level(it, cardio).label}" } ?: "Not set"
            }
        )
        Row(Modifier.fillMaxWidth()) {
            for (n in RPEScale.range) {
                Text("$n", fontSize = 10.sp, fontWeight = if (value == n) FontWeight.Bold else FontWeight.Normal,
                    color = if (value == n) PurpleTheme else FLTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

// MARK: - Collapsible card (§3.1 E)

@Composable
fun CollapsibleCard(title: String, icon: ImageVector, tint: Color, summary: String?, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FLTheme.colors.surface).animateContentSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClickLabel = if (expanded) "Collapse" else "Expand", onClick = onToggle)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            if (summary != null && !expanded) Text(summary, fontSize = 13.sp, color = FLTheme.colors.textSecondary, maxLines = 1)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = FLTheme.colors.textSecondary, modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f))
        }
        if (expanded) content()
    }
}

// MARK: - Duration picker (§11.2, Screen 14.7)

private val WHEEL_ROW_HEIGHT = 40.dp
private const val WHEEL_VISIBLE_ROWS = 5

/**
 * Index of the item resting in the centre slot. The content is padded by two rows, so the item at
 * the top of the content area — `firstVisibleItemIndex` — IS the centred one; items drawn in the
 * padding above it are not (reading `visibleItemsInfo.first()` picked those and lagged two rows).
 */
private fun centeredIndex(state: LazyListState): Int {
    val index = state.firstVisibleItemIndex
    val size = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: return index
    return if (state.firstVisibleItemScrollOffset > size / 2) index + 1 else index
}

@Composable
private fun WheelColumn(label: String, count: Int, state: LazyListState, selectedIndex: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FLTheme.colors.textSecondary)
        LazyColumn(
            state = state, flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
            modifier = Modifier.fillMaxWidth().height(WHEEL_ROW_HEIGHT * WHEEL_VISIBLE_ROWS),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = WHEEL_ROW_HEIGHT * 2)
        ) {
            items(count) { index ->
                val isSelected = index == selectedIndex
                Box(Modifier.fillMaxWidth().height(WHEEL_ROW_HEIGHT), contentAlignment = Alignment.Center) {
                    Text(String.format(Locale.US, "%02d", index), fontSize = if (isSelected) 22.sp else 18.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) FLTheme.colors.textPrimary else FLTheme.colors.textSecondary,
                        modifier = Modifier.alpha(if (isSelected) 1f else 0.6f))
                }
            }
        }
    }
}

/** Minutes / seconds wheels (0–180 min, 0–59 s) with the selected time and Apply. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationPickerSheet(title: String, initialSeconds: Int, onApply: (Int) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val minuteState = rememberLazyListState(initialFirstVisibleItemIndex = (initialSeconds / 60).coerceIn(0, 180))
    val secondState = rememberLazyListState(initialFirstVisibleItemIndex = (initialSeconds % 60).coerceIn(0, 59))
    val minutes by remember { derivedStateOf { centeredIndex(minuteState).coerceIn(0, 180) } }
    val seconds by remember { derivedStateOf { centeredIndex(secondState).coerceIn(0, 59) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = FLTheme.colors.surface, contentColor = FLTheme.colors.textPrimary) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = FLTheme.colors.textSecondary, modifier = Modifier.size(28.dp).clickable(onClick = onDismiss).padding(4.dp))
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(28.dp))
            }
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = WHEEL_ROW_HEIGHT * 2).fillMaxWidth().height(WHEEL_ROW_HEIGHT).background(FLTheme.colors.background, RoundedCornerShape(10.dp)))
                Row(Modifier.fillMaxWidth()) {
                    WheelColumn("Minutes", 181, minuteState, minutes, Modifier.weight(1f))
                    WheelColumn("Seconds", 60, secondState, seconds, Modifier.weight(1f))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Selected Time", fontSize = 12.sp, color = FLTheme.colors.textSecondary)
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = FLTheme.colors.textPrimary, modifier = Modifier.size(16.dp))
                Text(UniversalSetValues.formatDuration(minutes * 60 + seconds), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
            }
            PrimaryCapsuleButton("Apply") { onApply(minutes * 60 + seconds); onDismiss() }
        }
    }
}

// MARK: - Add Metric selector (§5.3–5.6, Screens 14.5–14.6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricSelectorSheet(
    taskName: String,
    addable: List<LogMetric>,
    /** Optional metrics already added — removable here (§5.5). */
    added: List<LogMetric>,
    hasValues: (LogMetric) -> Boolean,
    onAdd: (List<LogMetric>) -> Unit,
    onRemove: (LogMetric) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf(setOf<LogMetric>()) }
    var removed by remember { mutableStateOf(setOf<LogMetric>()) }
    var pendingRemoval by remember { mutableStateOf<LogMetric?>(null) }

    fun remove(metric: LogMetric) { removed = removed + metric; onRemove(metric) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = FLTheme.colors.background, contentColor = FLTheme.colors.textPrimary) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = FLTheme.colors.textSecondary, modifier = Modifier.size(28.dp).clickable(onClick = onDismiss).padding(4.dp))
                Text("Add Metric", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(28.dp))
            }
            Text("Select additional metrics for $taskName.", fontSize = 13.sp, color = FLTheme.colors.textSecondary)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FLTheme.colors.surface)) {
                if (addable.isEmpty()) {
                    Text("All available metrics have been added.", fontSize = 15.sp, color = FLTheme.colors.textSecondary, modifier = Modifier.padding(14.dp))
                } else {
                    addable.forEach { metric ->
                        val on = selected.contains(metric)
                        Row(
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().clickable { selected = if (on) selected - metric else selected + metric }.padding(horizontal = 14.dp, vertical = 12.dp)
                                .semantics { stateDescription = if (on) "Selected" else "Not selected" }
                        ) {
                            Icon(if (on) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, contentDescription = null, tint = if (on) PurpleTheme else FLTheme.colors.textSecondary, modifier = Modifier.size(22.dp))
                            Icon(metric.icon, contentDescription = null, tint = FLTheme.colors.textPrimary, modifier = Modifier.size(20.dp))
                            Text(metric.selectorTitle, fontSize = 15.sp, color = FLTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                            metric.unitLabel?.let { Text(it, fontSize = 13.sp, color = FLTheme.colors.textSecondary) }
                        }
                    }
                }
            }
            val current = added.filter { !removed.contains(it) }
            if (current.isNotEmpty()) {
                Text("Added Metrics", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textSecondary)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FLTheme.colors.surface)) {
                    current.forEach { metric ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Icon(metric.icon, contentDescription = null, tint = FLTheme.colors.textPrimary, modifier = Modifier.size(20.dp))
                            Text(metric.selectorTitle, fontSize = 15.sp, color = FLTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                            Text("Remove", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.danger,
                                modifier = Modifier.clickable { if (hasValues(metric)) pendingRemoval = metric else remove(metric) }.padding(6.dp))
                        }
                    }
                }
            }
            if (addable.isNotEmpty()) {
                PrimaryCapsuleButton(if (selected.size > 1) "Add Metrics" else "Add Metric", enabled = selected.isNotEmpty()) {
                    onAdd(LogMetric.displayOrder.filter { selected.contains(it) })
                    onDismiss()
                }
            }
        }
    }

    pendingRemoval?.let { metric ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove ${metric.selectorTitle}?") },
            text = { Text("This will remove the ${metric.title} column from all sets and delete the recorded ${metric.title.lowercase(Locale.getDefault())} values for this exercise.") },
            confirmButton = { TextButton(onClick = { pendingRemoval = null; remove(metric) }) { Text("Remove", color = FLTheme.colors.danger, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } }
        )
    }
}

// MARK: - Exercise History (§9, Screen 14.9)

/** Full-height page inside the logging sheet: previous sessions, newest first, the most recent expanded. */
@Composable
fun ExerciseHistoryContent(task: WorkoutTask, sessions: List<ExerciseHistorySession>, onBack: () -> Unit) {
    var expanded by remember(sessions) { mutableStateOf(setOfNotNull(sessions.firstOrNull()?.id)) }
    // Full-screen (PRD §9.1): fill the sheet's height even when there is little history, so the
    // sheet doesn't collapse to the content and leave the screen behind it showing.
    Column(Modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FLTheme.colors.textPrimary, modifier = Modifier.size(32.dp).clickable(onClick = onBack).padding(4.dp))
            Text("Exercise History", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(32.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val thumb = (task.movementThumbnailId ?: task.movementImageId)?.let { MediaRef.MovementThumbnail(it) }
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(FLTheme.colors.imagePlaceholder)) { if (thumb != null) FLImageView(ref = thumb, modifier = Modifier.size(48.dp)) }
            Text(task.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
        }
        Text("Previous Sessions", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textSecondary)
        if (sessions.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.History, contentDescription = null, tint = FLTheme.colors.textSecondary, modifier = Modifier.size(28.dp))
                Text("No previous sessions yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary)
                Text("Completed sets of ${task.name} will show here after you finish a workout.", fontSize = 13.sp, color = FLTheme.colors.textSecondary, textAlign = TextAlign.Center)
            }
        } else {
            sessions.forEach { s ->
                val open = expanded.contains(s.id)
                HistorySessionCard(s, open) { expanded = if (open) expanded - s.id else expanded + s.id }
            }
        }
        Text("Full analytics live in Progress → Workout Report → By Exercise.", fontSize = 12.sp, color = FLTheme.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun HistorySessionCard(s: ExerciseHistorySession, open: Boolean, onToggle: () -> Unit) {
    val showWeight = s.sets.any { it.weightLbs > 0 }
    val showReps = s.sets.any { it.reps > 0 }
    val showTime = s.sets.any { (it.durationSeconds ?: 0) > 0 }
    val showDistance = s.sets.any { it.distance != null }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FLTheme.colors.surface).animateContentSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = PurpleTheme, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${formatLogDate(s.dateMs)} – ${s.workoutName}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val parts = mutableListOf("${s.sets.size} ${if (s.sets.size == 1) "set" else "sets"}")
                if (s.volumeLbs > 0) parts.add("${WeightUnit.displayString(fromLbs = s.volumeLbs)} volume")
                Text(parts.joinToString(" · "), fontSize = 12.sp, color = FLTheme.colors.textSecondary)
            }
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = if (open) "Collapse" else "Expand", tint = FLTheme.colors.textSecondary, modifier = Modifier.size(20.dp).rotate(if (open) 180f else 0f))
        }
        if (open) {
            @Composable
            fun Cell(text: String, header: Boolean, modifier: Modifier) =
                Text(text, fontSize = if (header) 11.sp else 14.sp, fontWeight = if (header) FontWeight.Medium else FontWeight.Normal,
                    color = if (header) FLTheme.colors.textSecondary else FLTheme.colors.textPrimary, maxLines = 1, modifier = modifier)
            Row(Modifier.fillMaxWidth()) {
                Cell("Set", true, Modifier.weight(0.6f))
                if (showReps) Cell("Reps", true, Modifier.weight(1f))
                if (showWeight) Cell("Weight (${WeightUnit.current.label})", true, Modifier.weight(1.3f))
                if (showTime) Cell("Time", true, Modifier.weight(1f))
                if (showDistance) Cell("Distance", true, Modifier.weight(1.2f))
                Cell("RPE", true, Modifier.weight(0.7f))
            }
            s.sets.forEach { set ->
                Row(Modifier.fillMaxWidth()) {
                    Cell("${set.setNumber}", false, Modifier.weight(0.6f))
                    if (showReps) Cell(if (set.reps > 0) "${set.reps}" else "–", false, Modifier.weight(1f))
                    if (showWeight) Cell(if (set.weightLbs > 0) WeightUnit.displayNumber(fromLbs = set.weightLbs) else "–", false, Modifier.weight(1.3f))
                    if (showTime) Cell(set.durationSeconds?.let(UniversalSetValues::formatDuration) ?: "–", false, Modifier.weight(1f))
                    if (showDistance) Cell(set.distance?.let { DistanceUnit.displayInCurrent(it.value, it.unit) } ?: "–", false, Modifier.weight(1.2f))
                    Cell(set.rpe?.toString() ?: "–", false, Modifier.weight(0.7f))
                }
            }
            s.exerciseRPE?.let { rpe ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.BarChart, contentDescription = null, tint = FLTheme.colors.textSecondary, modifier = Modifier.size(14.dp))
                    Text("Exercise RPE: $rpe – ${RPEScale.level(rpe).label}", fontSize = 13.sp, color = FLTheme.colors.textPrimary)
                }
            }
            s.trainingNotes?.takeIf { it.isNotEmpty() }?.let { notes ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Description, contentDescription = null, tint = FLTheme.colors.textSecondary, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                    Text("Training Notes: $notes", fontSize = 13.sp, color = FLTheme.colors.textPrimary)
                }
            }
        }
    }
}

// MARK: - Workout-end unconfirmed sets (§11.3, Screen 14.11)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnconfirmedSetsSheet(items: List<UnconfirmedSetItem>, onReview: (UnconfirmedSetItem) -> Unit, onFinish: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val groups = remember(items) {
        val order = LinkedHashMap<String, Pair<WorkoutTask, MutableList<UnconfirmedSetItem>>>()
        for (item in items) order.getOrPut(item.task.id) { item.task to mutableListOf() }.second.add(item)
        order.values.toList()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = FLTheme.colors.background, contentColor = FLTheme.colors.textPrimary) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = OrangeTheme, modifier = Modifier.size(20.dp))
                Text("You Have Unconfirmed Sets", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
            }
            Text(UnconfirmedSetItem.message(items.size), fontSize = 14.sp, color = FLTheme.colors.textSecondary)
            Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                groups.forEach { (task, rows) ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(FLTheme.colors.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(task.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                            Text("${rows.size} unconfirmed", fontSize = 12.sp, color = FLTheme.colors.textSecondary)
                        }
                        rows.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = null, tint = FLTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
                                val unit = if (item.task.groupId.isEmpty()) "Set" else "Round"
                                val detail = item.values?.let { UnconfirmedSetItem.line(it) }?.takeIf { it.isNotEmpty() }?.let { " – $it" } ?: ""
                                Text("$unit ${item.setNumber}$detail", fontSize = 13.sp, color = FLTheme.colors.textPrimary)
                            }
                        }
                    }
                }
            }
            PrimaryCapsuleButton("Review Sets") { items.firstOrNull()?.let(onReview) }
            OutlinedCapsuleButton("Finish Without Completing", onClick = onFinish)
        }
    }
}
