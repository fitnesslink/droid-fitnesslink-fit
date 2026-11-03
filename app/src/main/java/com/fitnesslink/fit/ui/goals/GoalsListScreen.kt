package com.fitnesslink.fit.ui.goals

import com.fitnesslink.fit.ui.theme.FLTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fitnesslink.fit.goals.GoalAction
import com.fitnesslink.fit.goals.GoalPhase
import com.fitnesslink.fit.goals.GoalProgress
import com.fitnesslink.fit.goals.GoalProgressEngine
import com.fitnesslink.fit.goals.GoalReminderScheduler
import com.fitnesslink.fit.goals.GoalStore
import com.fitnesslink.fit.goals.LocalGoalDataSource
import com.fitnesslink.fit.goals.deadlineText
import com.fitnesslink.fit.goals.phase
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalStatus
import com.fitnesslink.fit.ui.theme.FLPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Goals landing screen (iOS `GoalListView`, PRD 1.3): active goals with live
 * progress, or the empty state; paused and finished goals below. "Set Your
 * First Goal" / "+" open the category selection.
 */
@Composable
fun GoalsListScreen(
    onBack: () -> Unit,
    onCreateGoal: () -> Unit,
    onOpenGoal: (String) -> Unit
) {
    val context = LocalContext.current
    var goals by remember { mutableStateOf<List<Goal>>(emptyList()) }
    var progress by remember { mutableStateOf<Map<String, GoalProgress>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var showInactive by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        GoalReminderScheduler.attach(context)
        GoalStore.ensureRegistered()
        withContext(Dispatchers.IO) { runCatching { GoalStore.refresh() } }
        tick++
    }
    LaunchedEffect(Unit) { GoalStore.changes.collect { tick++ } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(tick) {
        val (all, computed) = withContext(Dispatchers.IO) {
            val list = GoalStore.goals().sortedWith(compareByDescending<Goal> { it.isPinned }.thenByDescending { it.createdAt })
            val data = LocalGoalDataSource()
            list to list.associate { it.id to GoalProgressEngine.progress(it, data) }
        }
        goals = all
        progress = computed
        loaded = true
    }

    val active = goals.filter { it.status == GoalStatus.Active }
    val paused = goals.filter { it.status == GoalStatus.Paused }
    val finished = goals.filter { it.status == GoalStatus.Completed || it.status == GoalStatus.Archived }

    Column(Modifier.fillMaxSize().background(FLTheme.colors.background)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(onClick = onBack).padding(6.dp))
            Text("Goals", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.AddCircle, contentDescription = "Add goal", tint = FLPrimary,
                modifier = Modifier.size(36.dp).clickable(onClick = onCreateGoal).padding(4.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (!loaded) {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FLPrimary) }
            } else if (goals.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 80.dp)) {
                    Icon(Icons.Filled.TrackChanges, contentDescription = null, tint = FLTheme.colors.textTertiary, modifier = Modifier.size(48.dp))
                    Text("No active goals", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Set a goal to start tracking your progress.", fontSize = 14.sp, color = goalSecondaryText(), textAlign = TextAlign.Center)
                    GoalPrimaryButton("Set Your First Goal", modifier = Modifier.widthIn(max = 260.dp), onClick = onCreateGoal)
                }
            } else {
                if (active.isEmpty()) Text("No active goals", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = goalSecondaryText())
                active.forEach { GoalSummaryCard(it, progress[it.id], onClick = { onOpenGoal(it.id) }) }
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(CircleShape).border(1.5.dp, FLPrimary, CircleShape)
                        .clickable(onClick = onCreateGoal).padding(vertical = 12.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Another Goal", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                }
                if (paused.isNotEmpty()) {
                    SectionTitle("Paused")
                    paused.forEach { GoalSummaryCard(it, progress[it.id], Modifier.alpha(0.75f), onClick = { onOpenGoal(it.id) }) }
                }
                if (finished.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showInactive = !showInactive }) {
                        SectionTitle("Completed & archived")
                        Icon(if (showInactive) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null,
                            tint = goalSecondaryText(), modifier = Modifier.padding(top = 8.dp))
                    }
                    if (showInactive) finished.forEach { GoalSummaryCard(it, progress[it.id], Modifier.alpha(0.6f), onClick = { onOpenGoal(it.id) }) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = goalSecondaryText(), modifier = Modifier.padding(top = 8.dp))
}

/**
 * Compact goal card shared by the Goals list and Home's My Goals (iOS
 * `GoalSummaryCard`, PRD 8.2). When there's no data for the period it shows
 * the logging action rather than a zero.
 */
@Composable
fun GoalSummaryCard(goal: Goal, progress: GoalProgress?, modifier: Modifier = Modifier, showsAction: Boolean = false, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(goalSurface()).clickable(onClick = onClick).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(FLPrimary.copy(alpha = 0.12f))) {
                Icon(goal.subtype?.let { goalSubtypeIcon(it) } ?: goalTypeIcon(goal.goalType), contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(goal.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val phase = goal.phase(progress)
                val text = if (phase == GoalPhase.IN_PROGRESS) goal.deadlineText() ?: phase.label else phase.label
                Text(text, fontSize = 12.sp, color = phaseColor(phase))
            }
            if (goal.isPinned) Icon(Icons.Filled.PushPin, contentDescription = "Pinned", tint = FLPrimary, modifier = Modifier.size(14.dp))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = goalSecondaryText(), modifier = Modifier.size(16.dp))
        }
        if (progress != null) {
            Text(progress.summary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            progress.fraction?.let { GoalProgressBar(it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                progress.detail?.let { Text(it, fontSize = 13.sp, color = goalSecondaryText(), modifier = Modifier.weight(1f)) } ?: Spacer(Modifier.weight(1f))
                val action = progress.action
                if (showsAction && !progress.hasData && action != null && goal.status == GoalStatus.Active) {
                    Icon(goalActionIcon(action), contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (action is GoalAction.LogAmount) "Log Activity" else action.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                }
            }
        } else {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FLPrimary, modifier = Modifier.size(20.dp)) }
        }
    }
}
