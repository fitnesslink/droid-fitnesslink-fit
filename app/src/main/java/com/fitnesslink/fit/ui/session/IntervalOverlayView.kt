package com.fitnesslink.fit.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.IntervalPhase
import com.fitnesslink.fit.model.RestType
import com.fitnesslink.fit.session.IntervalRunner
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.White
import java.util.Locale

/**
 * Interval (HIIT / Tabata / EMOM / AMRAP) status strip (port of iOS `IntervalOverlayView`):
 * mode chip + phase badge, "HIIT BLOCK · 13:40 remaining" with a progress bar, phase body beside
 * the big phase countdown with the SET / ROUND counter, plus the AMRAP round button + partial
 * reps stepper and the EMOM "Round Done". [tick] is read only to recompose with the ticker.
 */
@Composable
fun IntervalOverlayView(runner: IntervalRunner, tick: Int, onChanged: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val t = tick
    val mode = runner.intervalMode
    val phaseStyle = phaseStyle(runner)
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(mode.displayName.uppercase(Locale.US), color = White, fontSize = 12.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clip(CircleShape).background(White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp))
            val badge = Modifier.clip(CircleShape).let { m -> if (phaseStyle.outlined) m.border(1.5.dp, phaseStyle.color, CircleShape) else m.background(phaseStyle.color) }
            Text(phaseStyle.text, color = if (phaseStyle.outlined) phaseStyle.color else White, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, maxLines = 1,
                modifier = badge.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${mode.displayName.uppercase(Locale.US)} BLOCK", color = White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Text("·", color = White.copy(alpha = 0.5f))
                Text("${IntervalMath.formatTime(runner.blockRemainingSeconds)} remaining", color = White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            val total = maxOf(1, runner.blockTotalSeconds)
            val done = (total - runner.blockRemainingSeconds).coerceIn(0, total)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(White.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxWidth(done.toFloat() / total).fillMaxHeight().clip(CircleShape).background(OrangeTheme))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { PhaseBody(runner) }
            Column(horizontalAlignment = Alignment.End) {
                val s = maxOf(0, runner.secondsRemaining)
                Text(String.format(Locale.US, "%d:%02d", s / 60, s % 60), color = White, fontSize = 56.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, maxLines = 1)
                RoundIndicator(runner)
            }
        }
        Controls(runner, onChanged)
    }
}

private data class PhaseStyle(val text: String, val color: Color, val outlined: Boolean)

private fun phaseStyle(runner: IntervalRunner): PhaseStyle = when (runner.phase) {
    IntervalPhase.PREPARE -> PhaseStyle("GET READY", BlueTheme, false)
    IntervalPhase.WORK -> PhaseStyle("WORK", OrangeTheme, false)
    IntervalPhase.REST -> if (runner.leadTask?.restType == RestType.ACTIVE_RECOVERY) PhaseStyle("ACTIVE RECOVERY", OrangeTheme, true) else PhaseStyle("REST", Color.Gray, false)
    IntervalPhase.BETWEEN_SETS_REST -> PhaseStyle("REST BETWEEN SETS", BlueTheme, false)
    IntervalPhase.DONE -> PhaseStyle("DONE", Color.Gray, false)
}

@Composable
private fun RoundIndicator(runner: IntervalRunner) {
    val text = when {
        runner.intervalMode == IntervalMode.AMRAP ->
            if (runner.setsTotal > 1) "SET ${maxOf(1, runner.currentSet)} OF ${runner.setsTotal} · ROUNDS: ${runner.roundsCompleted}" else "ROUNDS: ${runner.roundsCompleted}"
        runner.roundsTotal > 0 ->
            if (runner.setsTotal > 1) "SET ${maxOf(1, runner.currentSet)} OF ${runner.setsTotal} · ROUND ${maxOf(1, runner.currentRound)} OF ${runner.roundsTotal}"
            else "ROUND ${maxOf(1, runner.currentRound)} OF ${runner.roundsTotal}"
        else -> null
    } ?: return
    Text(text, color = White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
}

@Composable
private fun PhaseBody(runner: IntervalRunner) {
    val lead = runner.leadTask
    when (runner.phase) {
        IntervalPhase.REST -> {
            val name = lead?.restMovementName
            if (lead?.restType == RestType.ACTIVE_RECOVERY && !name.isNullOrEmpty()) Text(name.uppercase(Locale.US), color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            else Text("REST", color = White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
        IntervalPhase.BETWEEN_SETS_REST -> {
            Text("SET ${maxOf(1, runner.currentSet)} COMPLETE", color = White, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text("Rest, then start set ${runner.currentSet + 1} of ${runner.setsTotal}", color = White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2)
        }
        else -> {
            val exercise = runner.currentExercise ?: return
            if (runner.phase == IntervalPhase.PREPARE) Text("FIRST UP", color = White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Text(exercise.name.uppercase(Locale.US), color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val cue = lead?.instructions?.trim()
            if (runner.phase == IntervalPhase.PREPARE && !cue.isNullOrEmpty()) Text(cue, color = White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 3)
            if (runner.intervalMode == IntervalMode.EMOM && exercise.reps > 0) Text("${exercise.reps} reps", color = White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Controls(runner: IntervalRunner, onChanged: () -> Unit) {
    if (runner.intervalMode == IntervalMode.AMRAP && runner.phase == IntervalPhase.WORK) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 2.dp)) {
            Row(
                modifier = Modifier.clip(CircleShape).background(White).clickable { runner.markAmrapRound(); onChanged() }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.AddCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Text("Round ${runner.roundsCompleted + 1}", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.RemoveCircle, contentDescription = "Fewer partial reps", tint = White.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp).clickable { runner.amrapPartialReps = maxOf(0, runner.amrapPartialReps - 1); onChanged() })
                Text("Partial: ${runner.amrapPartialReps}", color = White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Filled.AddCircle, contentDescription = "More partial reps", tint = White.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp).clickable { runner.amrapPartialReps += 1; onChanged() })
            }
            Spacer(Modifier.width(1.dp))
        }
    } else if (runner.intervalMode == IntervalMode.EMOM && runner.phase == IntervalPhase.WORK && runner.secondsRemaining > 3) {
        Row(
            modifier = Modifier.padding(top = 2.dp).clip(CircleShape).background(White).clickable { runner.markEmomRoundDone(); onChanged() }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
            Text("Round Done", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
