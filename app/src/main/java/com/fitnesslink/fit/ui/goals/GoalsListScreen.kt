package com.fitnesslink.fit.ui.goals

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalStatus
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.GoalListViewModel

/**
 * iOS `GoalListView`: active goals as `GoalDetailCard`s (progress ring + %,
 * trajectory and type badges), a collapsible Archived section at 60 %
 * opacity, an empty state with a call to action, and a + button. Long-press
 * a card for Pause / Resume / Archive (the ViewModel actions neither
 * platform exposed before).
 */
@Composable
fun GoalsListScreen(
    onBack: () -> Unit,
    onCreateGoal: () -> Unit,
    onOpenGoal: (String) -> Unit
) {
    val viewModel: GoalListViewModel = viewModel()
    LaunchedEffect(Unit) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Goals", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(FLPrimary)
                            .clickable(onClick = onCreateGoal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create goal", tint = White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (viewModel.activeGoals.isEmpty()) {
                item { EmptyGoalsState(onCreateGoal = onCreateGoal) }
            } else {
                items(viewModel.activeGoals, key = { it.id }) { goal ->
                    GoalDetailCard(
                        goal = goal,
                        onClick = { onOpenGoal(goal.id) },
                        onPause = { viewModel.pauseGoal(goal.id) },
                        onResume = { viewModel.resumeGoal(goal.id) },
                        onArchive = { viewModel.archiveGoal(goal.id) },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            if (viewModel.archivedGoals.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.showArchived = !viewModel.showArchived }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Archived", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                        Icon(
                            if (viewModel.showArchived) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(16.dp)
                        )
                        Text("${viewModel.archivedGoals.size}", fontSize = 12.sp, color = TextSecondaryColor)
                    }
                }
                if (viewModel.showArchived) {
                    items(viewModel.archivedGoals, key = { "archived-${it.id}" }) { goal ->
                        GoalDetailCard(
                            goal = goal,
                            onClick = { onOpenGoal(goal.id) },
                            onResume = { viewModel.resumeGoal(goal.id) },
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .alpha(0.6f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * iOS `GoalDetailCard`: 56 dp progress ring with the % inside, title,
 * one-line description, trajectory + goal-type badges, chevron. Long-press
 * opens the status menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GoalDetailCard(
    goal: Goal,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    val percent = goal.progressPercent
    val color = trajectoryColor(goal.trajectoryStatus)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(White)
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GoalProgressRing(percent = percent, color = color, size = 56.dp, strokeWidth = 6.dp) {
                Text("${percent.toInt()}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        goal.title.ifBlank { goal.goalType.displayName },
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                    if (goal.status == GoalStatus.Paused) {
                        Text("Paused", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor,
                            modifier = Modifier.background(MediumGray.copy(alpha = 0.18f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                if (goal.description.isNotBlank()) {
                    Text(goal.description, fontSize = 13.sp, color = TextSecondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TrajectoryBadge(goal.trajectoryStatus)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(goalTypeIcon(goal.goalType), contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(11.dp))
                        Text(goal.goalType.displayName, fontSize = 11.sp, color = TextSecondaryColor)
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(16.dp))
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (goal.status == GoalStatus.Active && onPause != null) {
                DropdownMenuItem(
                    text = { Text("Pause goal") },
                    leadingIcon = { Icon(Icons.Default.Pause, contentDescription = null) },
                    onClick = { menuOpen = false; onPause() }
                )
            }
            if (goal.status != GoalStatus.Active && onResume != null) {
                DropdownMenuItem(
                    text = { Text(if (goal.status == GoalStatus.Archived) "Restore goal" else "Resume goal") },
                    leadingIcon = { Icon(if (goal.status == GoalStatus.Archived) Icons.Default.Unarchive else Icons.Default.PlayArrow, contentDescription = null) },
                    onClick = { menuOpen = false; onResume() }
                )
            }
            if (goal.status != GoalStatus.Archived && onArchive != null) {
                DropdownMenuItem(
                    text = { Text("Archive goal") },
                    leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                    onClick = { menuOpen = false; onArchive() }
                )
            }
        }
    }
}

/** iOS empty state: target glyph, "No active goals", helper copy, "Set Your First Goal" pill. */
@Composable
private fun EmptyGoalsState(onCreateGoal: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.Flag, contentDescription = null, tint = MediumGray, modifier = Modifier.size(48.dp))
        Text("No active goals", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        Text(
            "Set a goal to get started with personalized habits",
            fontSize = 14.sp, color = TextSecondaryColor, textAlign = TextAlign.Center
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(25.dp))
                .background(FLPrimary)
                .clickable(onClick = onCreateGoal)
                .padding(horizontal = 32.dp, vertical = 12.dp)
        ) {
            Text("Set Your First Goal", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White)
        }
    }
}
