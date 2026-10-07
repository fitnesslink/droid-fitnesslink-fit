package com.fitnesslink.fit.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.session.ActiveWorkoutSession
import com.fitnesslink.fit.session.InProgressSessionEntry
import com.fitnesslink.fit.session.SessionDiscardRule
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.HighlightState
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Workout Details resume card (FTNSSLNK-404): "62% complete · 12m elapsed" with Resume / Restart /
 * Discard. The host confirms Restart and Discard with its own dialogs.
 */
@Composable
fun SessionResumeCard(entry: InProgressSessionEntry, onResume: () -> Unit, onRestart: () -> Unit, onDiscard: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(HighlightState).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Session in progress", color = TextPrimaryColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(entry.summaryText(), color = TextSecondaryColor, fontSize = 13.sp)
            }
            Icon(Icons.Filled.Close, contentDescription = "Discard session", tint = MediumGray, modifier = Modifier.size(20.dp).clickable { onDiscard() })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(FLPrimary).clickable { onResume() }.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Resume", color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(White).clickable { onRestart() }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("Restart from beginning", color = TextPrimaryColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        }
    }
}

/**
 * Home "Continue where you left off" banner: every unfinished session, newest first, each with a
 * discard X that confirms with [SessionDiscardRule] copy. Renders nothing when there is none.
 */
@Composable
fun ResumeSessionBanner(onResume: (InProgressSessionEntry) -> Unit) {
    val state by ActiveWorkoutSession.state.collectAsState()
    var entries by remember { mutableStateOf(ActiveWorkoutSession.inProgressEntries()) }
    var discardTarget by remember { mutableStateOf<InProgressSessionEntry?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.version, state.sessionId) { entries = ActiveWorkoutSession.inProgressEntries() }
    LaunchedEffect(Unit) {
        ActiveWorkoutSession.events.collect { entries = ActiveWorkoutSession.inProgressEntries() }
    }
    if (entries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Continue where you left off", color = TextPrimaryColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        entries.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(White).clickable { onResume(entry) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(FLPrimary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.workoutName, color = TextPrimaryColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(entry.summaryText(), color = TextSecondaryColor, fontSize = 13.sp)
                }
                Icon(Icons.Filled.Close, contentDescription = "Discard session", tint = MediumGray, modifier = Modifier.size(20.dp).clickable { discardTarget = entry })
            }
        }
    }
    discardTarget?.let { target ->
        SessionDiscardDialog(
            workoutName = target.workoutName,
            onConfirm = {
                discardTarget = null
                scope.launch { ActiveWorkoutSession.discard(target.sessionId); entries = ActiveWorkoutSession.inProgressEntries() }
            },
            onDismiss = { discardTarget = null }
        )
    }
}

/** The one Discard Session confirmation every surface uses. */
@Composable
fun SessionDiscardDialog(workoutName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SessionDiscardRule.title) },
        text = { Text(SessionDiscardRule.message(workoutName)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(SessionDiscardRule.confirmLabel, color = Color(0xFFE53935), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(SessionDiscardRule.cancelLabel) } }
    )
}
