package com.fitnesslink.fit.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.session.PreparationBlockSummary
import com.fitnesslink.fit.session.SessionRestHeadline
import com.fitnesslink.fit.session.SessionTransitionEngine
import com.fitnesslink.fit.session.UpNextDescriptor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.White
import java.util.Locale

/**
 * Text block at the top of the rest / preparation overlay (port of iOS `RestTextView`). Two forms:
 * preparation (GET READY or the phase-transition header, the exercise or block, structure tag,
 * prescription, coach note, countdown ring) and rest (REST / ACTIVE RECOVERY, ring, the single
 * Up Next block). Exactly one "UP NEXT" appears on each.
 */
@Composable
fun RestTextView(
    task: WorkoutTask,
    isTransientRest: Boolean,
    isGetReady: Boolean,
    upNext: UpNextDescriptor?,
    prescription: String?,
    coachingCue: String?,
    phaseChange: SessionTransitionEngine.PhaseChange?,
    block: PreparationBlockSummary?,
    secondsRemaining: Int,
    totalSeconds: Int,
    phaseName: String?,
    structureLabel: String?,
    upNextStructureLabel: String?,
    upNextPrescription: String?,
    upNextNote: String?
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        if (isGetReady) {
            PreparationBody(upNext, prescription, coachingCue, phaseChange, block, secondsRemaining, totalSeconds, phaseName, structureLabel)
        } else {
            RestBody(task, isTransientRest, upNext, phaseChange, secondsRemaining, totalSeconds, upNextStructureLabel, upNextPrescription, upNextNote)
        }
    }
}

@Composable
private fun PreparationBody(
    upNext: UpNextDescriptor?, prescription: String?, coachingCue: String?, phaseChange: SessionTransitionEngine.PhaseChange?,
    block: PreparationBlockSummary?, secondsRemaining: Int, totalSeconds: Int, phaseName: String?, structureLabel: String?
) {
    val tint = blockTint(block?.kind)
    if (phaseChange != null) {
        PhaseTransitionBlock(phaseChange)
    } else {
        Text(
            "GET READY", color = if (block == null) White else White.copy(alpha = 0.75f),
            fontSize = if (block == null) 34.sp else 13.sp, fontWeight = FontWeight.Black,
            letterSpacing = if (block == null) 0.sp else 3.sp
        )
    }
    if (block != null) {
        BlockBlock(block, upNext?.name, coachingCue, tint)
    } else {
        if (phaseName != null && phaseChange == null) SessionStructureTag(phaseName.uppercase(Locale.US), FLPrimary.copy(alpha = 0.4f))
        upNext?.let { Text(it.name, color = White, fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp)) }
        structureLabel?.let { SessionStructureTag(it, null) }
        if (!prescription.isNullOrEmpty()) Text(prescription, color = White, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp))
        coachingCue?.trim()?.takeIf { it.isNotEmpty() }?.let {
            Text(it, color = White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 3, modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
    Spacer(Modifier.size(6.dp))
    SessionCountdownRing(secondsRemaining, totalSeconds, caption = if (block == null) "SECONDS TO START" else "STARTS IN", tint = if (block == null) FLPrimary else tint)
}

@Composable
private fun RestBody(
    task: WorkoutTask, isTransientRest: Boolean, upNext: UpNextDescriptor?, phaseChange: SessionTransitionEngine.PhaseChange?,
    secondsRemaining: Int, totalSeconds: Int, upNextStructureLabel: String?, upNextPrescription: String?, upNextNote: String?
) {
    when (val headline = SessionRestHeadline.headline(task, isTransientRest)) {
        SessionRestHeadline.Rest -> Text(headline.title, color = White, fontSize = 44.sp, fontWeight = FontWeight.Black)
        is SessionRestHeadline.ActiveRecovery -> {
            Text(headline.title, color = White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            headline.movement?.let { Text(it.uppercase(Locale.US), color = White, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center) }
        }
    }
    SessionCountdownRing(secondsRemaining, totalSeconds, caption = "SECONDS", tint = FLPrimary)
    phaseChange?.let {
        Text("NEXT PHASE · ${it.nextPhaseName.uppercase(Locale.US)}", color = OrangeTheme, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
            textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
    }
    if (upNext != null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("UP NEXT", color = White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text(upNext.name, color = White, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp))
            upNextStructureLabel?.let { SessionStructureTag(it, null) }
            if (!upNextPrescription.isNullOrEmpty()) Text(upNextPrescription, color = White, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp))
        }
        SessionNoteCard(upNextNote, maxLines = 2, modifier = Modifier.padding(horizontal = 28.dp))
    }
}

@Composable
private fun BlockBlock(block: PreparationBlockSummary, upNextName: String?, coachingCue: String?, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SessionStructureTag(block.title, tint)
        upNextName?.let { Text(it, color = White, fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp)) }
        Text(block.detail, color = OrangeTheme, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp))
        if (block.members.size > 1) Text("${block.members.size} EXERCISES", color = White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 6.dp, start = 36.dp, end = 36.dp).fillMaxWidth()) {
            block.members.forEach { member ->
                val isCurrent = member.ordinal == block.currentOrdinal
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(if (isCurrent) tint else White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Text("${member.ordinal}", color = if (isCurrent) White else White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                    Text(member.name, color = if (isCurrent) White else White.copy(alpha = 0.65f), fontSize = 15.sp, fontWeight = if (isCurrent) FontWeight.Black else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    member.metric?.let { Text(it, color = White.copy(alpha = if (isCurrent) 0.9f else 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                }
            }
        }
        SessionNoteCard(coachingCue, maxLines = 2, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
    }
}

@Composable
private fun PhaseTransitionBlock(change: SessionTransitionEngine.PhaseChange) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        if (change.completedPhaseName.isNotEmpty()) {
            Text("${change.completedPhaseName.uppercase(Locale.US)} COMPLETE ✓", color = OrangeTheme, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, textAlign = TextAlign.Center)
        }
        Text("NEXT PHASE", color = White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text(change.nextPhaseName, color = White, fontSize = 34.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp))
        if (change.summary.isNotEmpty()) Text(change.summary, color = White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}
