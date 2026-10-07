package com.fitnesslink.fit.ui.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fitnesslink.fit.calendar.CalendarService
import com.fitnesslink.fit.model.ProgramJourneyState
import com.fitnesslink.fit.model.ProgramSnapshot
import com.fitnesslink.fit.programs.UserProgramStore
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home's My Program section (Programs PRD §20–22; iOS `MyProgramHomeCard`):
 * program identity, current week + phase, and the two progress measures —
 * how far through the program, and how much of this week's training is
 * done. Renders nothing when there is no active program. Self-refreshing:
 * re-reads on resume and on every program / calendar change.
 */
@Composable
fun MyProgramHomeCard(
    onViewAll: () -> Unit,
    onViewProgress: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var snapshot by remember { mutableStateOf<ProgramSnapshot?>(null) }

    suspend fun reload() {
        snapshot = withContext(Dispatchers.IO) { UserProgramStore.active()?.let { UserProgramStore.snapshot(it) } }
    }

    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(Unit) {
        merge(UserProgramStore.changes, CalendarService.changes).collect { reload() }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumeTick++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeTick) { if (resumeTick > 0) reload() }

    val current = snapshot ?: return
    MyProgramHomeCardContent(current, onViewAll, onViewProgress, onOpenWorkout, modifier)
}

/** Stateless body — also lets previews / tests render a known snapshot. */
@Composable
fun MyProgramHomeCardContent(
    snapshot: ProgramSnapshot,
    onViewAll: () -> Unit,
    onViewProgress: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val subtitle = when {
        snapshot.state == ProgramJourneyState.COMPLETED -> "Program complete"
        !snapshot.hasStarted -> "Starts " + SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(snapshot.program.startDate))
        else -> snapshot.weekLine()
    }
    val today = snapshot.pendingEntry(System.currentTimeMillis())

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My Program", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.clickable(onClick = onViewAll).padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("View All", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(16.dp))
            }
        }

        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(White)) {
            ProgramHeroView(
                imageId = snapshot.heroImageId(),
                name = snapshot.program.programName,
                subtitle = subtitle,
                modifier = Modifier.clickable(onClick = onViewAll)
            )
            // The whole progress area opens Program Progress (PRD §22).
            Column(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onViewProgress).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ProgramProgressRow(
                    title = "Program Progress",
                    caption = "${snapshot.weeksCompleted} of ${snapshot.totalWeeks} weeks completed",
                    filled = snapshot.weeksCompleted,
                    total = snapshot.totalWeeks,
                    percent = snapshot.weeksPercent
                )
                val week = snapshot.current
                if (week != null && snapshot.state != ProgramJourneyState.COMPLETED) {
                    ProgramProgressRow(
                        title = "This Week's Progress",
                        caption = "${week.completed} of ${week.total} workouts completed",
                        filled = week.completed,
                        total = week.total,
                        percent = week.percent
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (snapshot.state == ProgramJourneyState.COMPLETED) "View Results" else "View Progress",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
                }
            }
            if (today != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenWorkout(today.workoutId) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
                    Text("Today: ${today.title.ifEmpty { "Workout" }}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Start", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                }
            }
        }
    }
}
