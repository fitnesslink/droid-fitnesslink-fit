package com.fitnesslink.fit.ui.programs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.ProgramDaySlot
import com.fitnesslink.fit.model.ProgramWeek
import com.fitnesslink.fit.programs.ScheduledWorkoutInfo
import com.fitnesslink.fit.ui.theme.BlendGray
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLPrimaryDark
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

/**
 * Program Detail schedule section (iOS `ProgramScheduleSectionView`): a
 * horizontal week picker (one chip per week) over a single week's day
 * list, with a Base / Build / Peak phase rail for programs of 3+ weeks.
 * Workout rows expand inline to list the workout's exercise names.
 */
@Composable
fun ProgramScheduleSection(
    weeks: List<ProgramWeek>,
    workoutInfo: Map<String, ScheduledWorkoutInfo>,
    modifier: Modifier = Modifier
) {
    val totalWeeks = weeks.size
    var selectedWeekNumber by rememberSaveable { mutableIntStateOf(1) }
    var expandedDayKey by rememberSaveable { mutableStateOf<String?>(null) }

    // A refresh can shrink the program; snap back into range.
    LaunchedEffect(totalWeeks) {
        if (selectedWeekNumber > totalWeeks) { selectedWeekNumber = 1; expandedDayKey = null }
    }

    val baseEnd = (totalWeeks + 2) / 3
    val buildEnd = (2 * totalWeeks + 2) / 3
    val showPhases = totalWeeks >= 3
    val phaseName = when {
        selectedWeekNumber <= baseEnd -> "Base"
        selectedWeekNumber <= buildEnd -> "Build"
        else -> "Peak"
    }
    val selectedWeek = weeks.firstOrNull { it.weekNumber == selectedWeekNumber } ?: weeks.firstOrNull()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Schedule", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            Spacer(modifier = Modifier.weight(1f))
            if (totalWeeks > 1) {
                Row {
                    Text("Week $selectedWeekNumber of $totalWeeks", fontSize = 13.sp, color = TextSecondaryColor)
                    if (showPhases) {
                        Text(" · ", fontSize = 13.sp, color = TextSecondaryColor)
                        Text(phaseName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimaryDark)
                    }
                }
            }
        }

        if (totalWeeks > 1) {
            Spacer(modifier = Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 20.dp)) {
                items(weeks, key = { it.weekNumber }) { week ->
                    val selected = week.weekNumber == selectedWeekNumber
                    Column(
                        modifier = Modifier
                            .widthIn(min = 46.dp)
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) FLPrimary else White)
                            .clickable { selectedWeekNumber = week.weekNumber; expandedDayKey = null }
                            .padding(horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("WK", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = (if (selected) White else TextPrimaryColor).copy(alpha = 0.75f))
                        Text("${week.weekNumber}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (selected) White else TextPrimaryColor)
                    }
                }
            }
            if (showPhases) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    PhaseSegment("Base", span = baseEnd, total = totalWeeks, active = phaseName == "Base")
                    PhaseSegment("Build", span = buildEnd - baseEnd, total = totalWeeks, active = phaseName == "Build")
                    PhaseSegment("Peak", span = totalWeeks - buildEnd, total = totalWeeks, active = phaseName == "Peak")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            selectedWeek?.days.orEmpty().forEach { day ->
                if (day.workoutId != null) {
                    val key = "${day.weekNumber}-${day.dayNumber}"
                    ScheduleWorkoutCard(
                        day = day,
                        info = day.workoutId?.let { workoutInfo[it] },
                        expanded = expandedDayKey == key,
                        onToggle = { expandedDayKey = if (expandedDayKey == key) null else key }
                    )
                } else {
                    ScheduleRestRow(day)
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PhaseSegment(label: String, span: Int, total: Int, active: Boolean) {
    if (span <= 0 || total <= 0) return
    Column(
        modifier = Modifier.weight(span.toFloat() / total),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (active) FLPrimary else BlendGray)
        )
        Text(
            label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.7.sp,
            color = if (active) FLPrimaryDark else MediumGray
        )
    }
}

@Composable
private fun ScheduleRestRow(day: ProgramDaySlot) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(White.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(day.dayLabel.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MediumGray, modifier = Modifier.width(38.dp))
        Text("Rest", fontSize = 13.sp, color = MediumGray)
    }
}

@Composable
private fun ScheduleWorkoutCard(day: ProgramDaySlot, info: ScheduledWorkoutInfo?, expanded: Boolean, onToggle: () -> Unit) {
    val exercises = info?.exercises.orEmpty()
    // Rows without hydrated exercises render plain (no chevron, not tappable).
    val expandable = exercises.isNotEmpty()
    val name = day.workout?.name ?: "Workout"
    val meta = listOf(day.workout?.time.orEmpty(), info?.focusArea.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .then(if (expandable) Modifier.clickable(onClick = onToggle) else Modifier)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(day.dayLabel.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimaryDark, modifier = Modifier.width(38.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                if (meta.isNotEmpty()) Text(meta, fontSize = 12.sp, color = TextSecondaryColor)
            }
            if (expandable) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MediumGray,
                    modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f)
                )
            }
        }
        AnimatedVisibility(visible = expanded && expandable) {
            // 64dp leading = 14 card padding + 38 day column + 12 gap.
            Column(modifier = Modifier.fillMaxWidth().padding(start = 64.dp, end = 14.dp, bottom = 12.dp)) {
                exercises.forEach { exercise ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(FLPrimary))
                        Text(exercise, fontSize = 13.sp, color = TextSecondaryColor)
                    }
                }
            }
        }
    }
}
