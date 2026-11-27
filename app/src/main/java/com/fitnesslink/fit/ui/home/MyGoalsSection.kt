package com.fitnesslink.fit.ui.home

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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fitnesslink.fit.goals.GoalProgress
import com.fitnesslink.fit.goals.GoalProgressEngine
import com.fitnesslink.fit.goals.GoalReminderScheduler
import com.fitnesslink.fit.goals.GoalStore
import com.fitnesslink.fit.goals.LocalGoalDataSource
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalStatus
import com.fitnesslink.fit.ui.goals.GoalSummaryCard
import com.fitnesslink.fit.ui.goals.goalSecondaryText
import com.fitnesslink.fit.ui.goals.goalSurface
import com.fitnesslink.fit.ui.theme.FLPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Home's My Goals section (iOS `MyGoalsSection`, Goals PRD §8): one pinned
 * goal, a prompt to pin one, or a prompt to create the first goal. Named
 * "My Goals" — distinct from "My Program".
 */
@Composable
fun MyGoalsSection(onCreate: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    var pinned by remember { mutableStateOf<Goal?>(null) }
    var progress by remember { mutableStateOf<GoalProgress?>(null) }
    var hasGoals by remember { mutableStateOf(false) }
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
        val result = withContext(Dispatchers.IO) {
            val goals = GoalStore.goals()
            val pin = goals.firstOrNull { it.isPinned && it.status != GoalStatus.Archived && it.status != GoalStatus.Completed }
            Triple(goals.isNotEmpty(), pin, pin?.let { GoalProgressEngine.progress(it, LocalGoalDataSource()) })
        }
        hasGoals = result.first
        pinned = result.second
        progress = result.third
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("My Goals", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (hasGoals) {
                Text("See All", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FLPrimary,
                    modifier = Modifier.clickable { onNavigate("goals") })
            }
        }
        val goal = pinned
        if (goal != null) {
            GoalSummaryCard(goal, progress, showsAction = true, onClick = { onNavigate("goalDetail/${goal.id}") })
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(goalSurface())
                    .clickable { if (hasGoals) onNavigate("goals") else onCreate() }.padding(14.dp)
            ) {
                Icon(if (hasGoals) Icons.Filled.PushPin else Icons.Filled.TrackChanges, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (hasGoals) "Pin a goal to see it here" else "Set a goal", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (hasGoals) "Open a goal and choose Pin to My Goals." else "Track progress toward something that matters to you.",
                        fontSize = 12.sp, color = goalSecondaryText())
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = goalSecondaryText(), modifier = Modifier.size(16.dp))
            }
        }
    }
}
