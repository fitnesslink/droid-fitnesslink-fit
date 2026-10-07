package com.fitnesslink.fit.ui.session

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.dto.SessionCompleteRequest
import com.fitnesslink.fit.network.dto.SessionShareResultsRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.sessionDetailRow
import com.fitnesslink.fit.persistence.sessionTotalDistance
import com.fitnesslink.fit.session.ActiveWorkoutSession
import com.fitnesslink.fit.session.SessionAchievements
import com.fitnesslink.fit.session.SessionAchievementsBuilder
import com.fitnesslink.fit.session.SessionCelebration
import com.fitnesslink.fit.session.SessionHistoryRows
import com.fitnesslink.fit.session.SessionProgress
import com.fitnesslink.fit.session.SessionRecorder
import com.fitnesslink.fit.session.SummaryRPE
import com.fitnesslink.fit.session.SummaryRPEMode
import com.fitnesslink.fit.session.WorkoutSummaryLayout
import com.fitnesslink.fit.session.WorkoutSummaryStats
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.auth.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Workout Summary (port of iOS `WorkoutCompletedView`): celebration → name → RPE → stats →
 * missed exercises → achievements, with Done and Share. Rendered live at the end of a session and
 * read-only from history ([WorkoutSummaryReopenView]).
 */
@Composable
fun WorkoutCompletedScreen(
    sessionId: String,
    workoutName: String,
    stats: WorkoutSummaryStats,
    achievements: SessionAchievements = SessionAchievements(),
    rpeMode: SummaryRPEMode = SummaryRPEMode.Editable,
    unlocksAchievements: Boolean = true,
    onComplete: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var rpeValue by remember { mutableStateOf<Int?>(null) }
    var showShare by remember { mutableStateOf(false) }
    val effectiveRpe: Int? = when (rpeMode) { SummaryRPEMode.Editable -> rpeValue; is SummaryRPEMode.ReadOnly -> rpeMode.value; SummaryRPEMode.Hidden -> null }
    val sections = WorkoutSummaryLayout.sections(rpeMode, stats, achievements)

    LaunchedEffect(sessionId) {
        if (unlocksAchievements) SessionAchievementsBuilder.unlock(achievements.achievementTypes)
    }

    val shareText = remember(workoutName, stats) {
        "Just crushed $workoutName on FitnessLink 💪 • ${stats.durationText} • ${stats.exercisesCompleted} exercises • ${stats.setsCompleted} sets"
    }

    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.welcome), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Black))))
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Image(painterResource(R.drawable.logo), contentDescription = "FitnessLink", modifier = Modifier.width(134.dp).align(Alignment.Center))
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = White, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).size(28.dp).clickable { onDismiss() })
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                sections.forEach { section ->
                    when (section) {
                        WorkoutSummaryLayout.Section.CELEBRATION -> Celebration(stats)
                        WorkoutSummaryLayout.Section.WORKOUT_NAME -> Text(workoutName, color = White.copy(alpha = 0.8f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                        WorkoutSummaryLayout.Section.RPE -> when (rpeMode) {
                            SummaryRPEMode.Editable -> RPESliderView(rpeValue, { rpeValue = it }, Modifier.padding(horizontal = 32.dp))
                            is SummaryRPEMode.ReadOnly -> Text("RPE ${rpeMode.value} · ${SummaryRPE.label(rpeMode.value)}", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            SummaryRPEMode.Hidden -> Unit
                        }
                        WorkoutSummaryLayout.Section.STATS -> SummaryStatsGrid(stats, Modifier.padding(horizontal = 20.dp))
                        WorkoutSummaryLayout.Section.MISSED_EXERCISES -> stats.missed?.let { MissedExercisesSection(it, Modifier.padding(horizontal = 20.dp)) }
                        WorkoutSummaryLayout.Section.ACHIEVEMENTS -> AchievementsSection(achievements, Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
            Column(Modifier.padding(horizontal = 32.dp).padding(top = 8.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(WorkoutSummaryLayout.primaryAction, color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(FLPrimary).clickable { onComplete(effectiveRpe) }.padding(top = 16.dp))
                Row(Modifier.fillMaxWidth().height(44.dp).clickable { showShare = true }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Share, contentDescription = null, tint = White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(WorkoutSummaryLayout.secondaryAction, color = White.copy(alpha = 0.85f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showShare) {
        ShareResultsSheet(
            sessionId = sessionId, rpeValue = effectiveRpe, completionPercent = stats.completionPercent, shareText = shareText,
            onFinish = { showShare = false; onComplete(effectiveRpe) },
            onDismiss = { showShare = false },
            onSystemShare = {
                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Crushed a workout on FitnessLink"); putExtra(Intent.EXTRA_TEXT, shareText) }
                runCatching { context.startActivity(Intent.createChooser(intent, "Share workout")) }
            }
        )
    }
}

@Composable
private fun Celebration(stats: WorkoutSummaryStats) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(WorkoutSummaryLayout.congratulations, color = White.copy(alpha = 0.9f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(WorkoutSummaryLayout.headline, color = White, fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Box(Modifier.size(56.dp).clip(CircleShape).background(FLPrimary), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, contentDescription = null, tint = White, modifier = Modifier.size(28.dp)) }
        stats.completionText?.let { Text(it, color = OrangeTheme, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
}

/** Duration · Exercises · Sets, plus Total Volume and Distance only when recorded. */
@Composable
fun SummaryStatsGrid(stats: WorkoutSummaryStats, modifier: Modifier = Modifier) {
    val items = buildList {
        add(Triple("Duration", stats.durationText, "⏱"))
        add(Triple("Exercises", "${stats.exercisesCompleted}", "🏃"))
        add(Triple("Sets", "${stats.setsCompleted}", "🔁"))
        stats.volumeText?.let { add(Triple("Total Volume", it, "🏋")) }
        stats.distanceText?.let { add(Triple("Distance", it, "📍")) }
    }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(White.copy(alpha = 0.08f)).padding(vertical = 20.dp, horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (label, value, icon) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(icon, fontSize = 16.sp)
                        Text(value, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(label, color = White.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun MissedExercisesSection(missed: List<com.fitnesslink.fit.session.MissedExercise>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(White.copy(alpha = 0.08f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (missed.isEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FLPrimary); Spacer(Modifier.width(8.dp))
                Text(WorkoutSummaryStats.allCompletedText, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text("MISSED EXERCISES", color = White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            missed.forEach { m ->
                Row(Modifier.fillMaxWidth()) {
                    Text(m.name, color = White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(m.detail, color = OrangeTheme, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** "Your Achievements" with the "+N more" fold over the per-movement lines. */
@Composable
fun AchievementsSection(achievements: SessionAchievements, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val items = achievements.items
    val perMovement = achievements.records.size + achievements.firstTimeLogged.size
    val visible = if (expanded || achievements.foldedRecordCount == 0) items else items.take(SessionAchievements.visibleRecordLimit) + items.drop(perMovement)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(White.copy(alpha = 0.08f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("YOUR ACHIEVEMENTS", color = White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        visible.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = OrangeTheme, modifier = Modifier.size(20.dp))
                Text(item.text, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            }
        }
        if (!expanded && achievements.foldedRecordCount > 0) {
            Text("+${achievements.foldedRecordCount} more", color = FLPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { expanded = true }.padding(top = 2.dp))
        }
    }
}

/** 0–10 integer effort slider; [value] stays null until the user touches the track. */
@Composable
fun RPESliderView(value: Int?, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var trackWidth by remember { mutableStateOf(1) }
    val fraction = SummaryRPE.fraction(value ?: 0).toFloat()
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(SummaryRPE.prompt, color = White.copy(alpha = 0.7f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Box(
            Modifier.fillMaxWidth().height(28.dp).onSizeChanged { trackWidth = maxOf(1, it.width) }
                .pointerInput(Unit) { detectTapGestures { offset -> onChange(SummaryRPE.valueFromFraction((offset.x / trackWidth).toDouble())) } }
                .pointerInput(Unit) { detectDragGestures { change, _ -> onChange(SummaryRPE.valueFromFraction((change.position.x / trackWidth).toDouble())) } },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(FLPrimary.copy(alpha = 0.25f)))
            Box(Modifier.fillMaxWidth(fraction).height(3.dp).clip(CircleShape).background(FLPrimary))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { repeat(11) { Box(Modifier.size(3.dp).clip(CircleShape).background(White.copy(alpha = 0.35f))) } }
            Box(Modifier.fillMaxWidth(fraction), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(if (value == null) White.copy(alpha = 0.6f) else FLPrimary))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(SummaryRPE.minAnchor, color = White.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (value != null) {
                Text("$value RPE · ${SummaryRPE.label(value)}", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(White.copy(alpha = 0.85f)).padding(horizontal = 14.dp, vertical = 6.dp))
            } else Text("Optional", color = White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            Text(SummaryRPE.maxAnchor, color = White.copy(alpha = 0.5f), fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

/** Email up to 10 recipients plus a note via `POST /sessions/{id}/share-results`; the OS share stays as an extra. */
@Composable
fun ShareResultsSheet(
    sessionId: String, rpeValue: Int?, completionPercent: Int?, shareText: String,
    onFinish: () -> Unit, onDismiss: () -> Unit, onSystemShare: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var emails by remember { mutableStateOf(listOfNotNull(AuthManager.currentUser.value?.email?.takeIf { isValidEmail(it) })) }
    var newEmail by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun addEmail() {
        val trimmed = newEmail.trim()
        if (isValidEmail(trimmed) && !emails.contains(trimmed) && emails.size < 10) { emails = emails + trimmed; newEmail = "" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share results") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Send to (up to 10 recipients)", fontSize = 12.sp, color = Color.Gray)
                emails.forEach { email ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(email, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp).clickable { emails = emails - email })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newEmail, { newEmail = it }, placeholder = { Text("Add an email") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { addEmail() }, enabled = isValidEmail(newEmail.trim())) { Text("Add") }
                }
                OutlinedTextField(note, { note = it }, placeholder = { Text("Add a note (optional)") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = Color(0xFFE53935), fontSize = 12.sp) }
                Text("Share via another app…", color = FLPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onSystemShare() }.padding(vertical = 4.dp))
                Text("Skip sharing", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.clickable { onFinish() }.padding(vertical = 4.dp))
            }
        },
        confirmButton = {
            if (sending) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else TextButton(enabled = emails.isNotEmpty(), onClick = {
                sending = true; error = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            // The backend requires a completion before share-results; the call is idempotent.
                            ApiClient.sessionApi.complete(sessionId, SessionCompleteRequest.of(null, SummaryRPE.wireValue(rpeValue), completionPercent))
                            ApiClient.sessionApi.shareResults(sessionId, SessionShareResultsRequest(emails, note.trim().ifEmpty { null }))
                        }
                        onFinish()
                    } catch (e: Exception) {
                        error = "Couldn't send: ${e.message ?: "please try again"}"
                    } finally { sending = false }
                }
            }) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun isValidEmail(s: String): Boolean = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(s)

/** The summary for a session reopened from Workout History — read-only apart from the one-time RPE edit. */
@Composable
fun WorkoutSummaryReopenView(sessionId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<Triple<com.fitnesslink.fit.session.SavedSessionRow, WorkoutSummaryStats, SessionAchievements>?>(null) }
    var missing by remember { mutableStateOf(false) }
    LaunchedEffect(sessionId) {
        val row = DatabaseManager.sessionDetailRow(sessionId)
        if (row == null) { missing = true; return@LaunchedEffect }
        val tasks = DatabaseManager.workout(row.workoutId)?.hydrateGroupRounds()?.allTasks
        val progress = SessionProgress.decode(row.progressJson ?: DatabaseManager.sessionProgressJson(sessionId))
        val logs = DatabaseManager.loadSessionLogs(sessionId).logs
        val distance = DatabaseManager.sessionTotalDistance(sessionId, DistanceUnit.current)?.let { DistanceValue(it, DistanceUnit.current) }
        val stats = WorkoutSummaryStats.saved(row, DatabaseManager.sessionExercises(sessionId), tasks, progress, logs, distance)
        val achievements = if (tasks != null && progress != null) {
            val rows = SessionHistoryRows.build(tasks, logs, progress, SessionProgress.requiredIterations(tasks))
            SessionAchievementsBuilder.build(rows, sessionId, row.workoutId, row.programId, row.completionDateMs ?: row.startDateMs)
        } else SessionAchievements()
        loaded = Triple(row, stats, achievements)
    }
    val data = loaded
    when {
        missing -> LaunchedEffect(Unit) { onBack() }
        data == null -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = White) }
        else -> {
            val (row, stats, achievements) = data
            WorkoutCompletedScreen(
                sessionId = row.id, workoutName = row.workoutName.ifEmpty { "Workout" }, stats = stats, achievements = achievements,
                rpeMode = WorkoutSummaryLayout.rpeMode(row), unlocksAchievements = false,
                onComplete = { rpe ->
                    if (row.canEditRpe() && rpe != null && rpe != row.rpeValue?.toInt()) scope.launch { SessionRecorder.updateRpe(row.id, rpe); onBack() }
                    else onBack()
                },
                onDismiss = onBack
            )
        }
    }
}
