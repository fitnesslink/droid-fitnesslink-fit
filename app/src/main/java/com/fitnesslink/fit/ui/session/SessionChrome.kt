package com.fitnesslink.fit.ui.session

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.session.UpNextDescriptor
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.White
import java.util.Locale

// Shared chrome for the interactive session (ports of SessionTopBar / SessionTransportBar /
// SessionDockView / SessionInfoStrip pieces / WorkoutControlTopView / SessionCountdownRing /
// SessionNoteCard / SessionStructureTag / UpNextLabel).

/** One audio channel exposed in the top bar's long-press menu. */
data class SessionAudioChannel(val id: String, val title: String, val isOn: Boolean, val toggle: () -> Unit)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionTopBar(
    timerText: String,
    onPlaylist: (() -> Unit)?,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    audioChannels: List<SessionAudioChannel>,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var channelMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onPlaylist != null) {
            Row(
                modifier = Modifier.height(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                    .clickable { onPlaylist() }.padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = White, modifier = Modifier.size(14.dp))
                Text("Playlist", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 14.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("WORKOUT", color = White.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Text(timerText, color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.weight(1f))
        Box {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                    .combinedClickable(onClick = onToggleMute, onLongClick = { channelMenu = true }),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, contentDescription = if (isMuted) "Unmute all audio" else "Mute all audio", tint = White, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = channelMenu, onDismissRequest = { channelMenu = false }) {
                audioChannels.forEach { channel ->
                    DropdownMenuItem(
                        text = { Text(channel.title) },
                        leadingIcon = { Icon(if (channel.isOn) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, contentDescription = null, tint = if (channel.isOn) FLPrimary else Color.Gray) },
                        onClick = { channel.toggle(); channelMenu = false }
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).clickable { moreMenu = true },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.MoreVert, contentDescription = "Session options", tint = White, modifier = Modifier.size(18.dp)) }
            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Discard Session", color = Color(0xFFE53935)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFE53935)) },
                    onClick = { moreMenu = false; onDiscard() }
                )
            }
        }
    }
}

@Composable
fun PausedBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(CircleShape).background(FLPrimary.copy(alpha = 0.9f)).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Filled.Pause, contentDescription = null, tint = White, modifier = Modifier.size(12.dp))
        Text("PAUSED", color = White, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    }
}

/** Labelled transport bar: Previous · Pause/Resume · Skip (menu) · Next, plus Skip Block inside an interval block. */
@Composable
fun SessionTransportBar(
    isPaused: Boolean,
    canSkipSet: Boolean,
    canSkipExercise: Boolean,
    skipExerciseLabel: String,
    showSkipBlock: Boolean,
    onPrevious: () -> Unit,
    onTogglePause: () -> Unit,
    onSkipSet: () -> Unit,
    onSkipExercise: () -> Unit,
    onSkipBlock: () -> Unit,
    onNext: () -> Unit
) {
    var skipMenu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            TransportButton("Previous", Icons.Filled.FastRewind, Modifier.weight(1f), onClick = onPrevious)
            TransportButton(if (isPaused) "Resume" else "Pause", if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, Modifier.weight(1f), emphasis = true, onClick = onTogglePause)
            Box(Modifier.weight(1f)) {
                TransportButton("Skip", Icons.Filled.SkipNext, Modifier.fillMaxWidth(), enabled = canSkipExercise, onClick = { skipMenu = true })
                DropdownMenu(expanded = skipMenu, onDismissRequest = { skipMenu = false }) {
                    if (canSkipSet) DropdownMenuItem(text = { Text("Skip Set") }, onClick = { skipMenu = false; onSkipSet() })
                    if (canSkipExercise) DropdownMenuItem(text = { Text(skipExerciseLabel) }, onClick = { skipMenu = false; onSkipExercise() })
                }
            }
            TransportButton("Next", Icons.Filled.FastForward, Modifier.weight(1f), onClick = onNext)
        }
        if (showSkipBlock) {
            Row(
                modifier = Modifier.height(32.dp).clip(CircleShape).background(White.copy(alpha = 0.12f)).clickable { onSkipBlock() }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = null, tint = White.copy(alpha = 0.85f), modifier = Modifier.size(12.dp))
                Text("Skip Block", color = White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TransportButton(title: String, icon: ImageVector, modifier: Modifier, emphasis: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier = modifier.height(56.dp).clip(RoundedCornerShape(16.dp))
            .background(if (emphasis) FLPrimary.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = enabled) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = title, tint = White.copy(alpha = if (enabled) 1f else 0.45f), modifier = Modifier.size(if (emphasis) 22.dp else 18.dp))
        Text(title, color = White.copy(alpha = if (enabled) 1f else 0.45f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Floating bottom dock: Start Now / Skip Rest / Log Set N / Continue, plus Finish on the last exercise. */
@Composable
fun SessionDockView(
    isPreparing: Boolean,
    isRest: Boolean,
    isMovement: Boolean,
    logLabel: String,
    isLogged: Boolean,
    hasUpNext: Boolean,
    onStartNow: () -> Unit,
    onSkipRest: () -> Unit,
    onLogSet: () -> Unit,
    onContinue: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            isPreparing -> DockCta("Start Now", Icons.Filled.PlayArrow, Modifier.weight(1f), onClick = onStartNow)
            isRest -> DockCta("Skip Rest", Icons.Filled.FastForward, Modifier.weight(1f), onClick = onSkipRest)
            isMovement -> DockCta(logLabel, if (isLogged) Icons.Filled.CheckCircle else Icons.Filled.Edit, Modifier.weight(1f), muted = isLogged, onClick = onLogSet)
            else -> DockCta("Continue", Icons.Filled.ArrowForward, Modifier.weight(1f), muted = true, onClick = onContinue)
        }
        if (!hasUpNext) {
            Row(
                modifier = Modifier.height(40.dp).clip(CircleShape).background(White.copy(alpha = 0.15f)).clickable { onFinish() }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Flag, contentDescription = null, tint = White, modifier = Modifier.size(14.dp))
                Text("Finish", color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DockCta(label: String, icon: ImageVector, modifier: Modifier, muted: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = modifier.height(44.dp).clip(CircleShape).background(if (muted) White.copy(alpha = 0.15f) else FLPrimary).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Group / interval badges for the current task (the phase itself is the separate "PHASE n — NAME" label). */
@Composable
fun WorkoutControlTopView(task: WorkoutTask, currentRound: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (task.isSuperset || task.isCircuit) {
            val stored = task.advancedMovement.trim()
            Badge(if (stored.isNotEmpty()) stored.uppercase(Locale.US) else if (task.isSuperset) "SUPERSET" else "CIRCUIT", if (task.isSuperset) PurpleTheme else OrangeTheme)
            Badge("ROUND $currentRound OF ${maxOf(task.rounds ?: task.totalRounds, 1)}", White.copy(alpha = 0.18f))
        } else if (task.isInterval) {
            Badge(task.intervalMode?.let { "INTERVAL · ${it.displayName.uppercase(Locale.US)}" } ?: "INTERVAL", BlueTheme)
        }
    }
}

@Composable
fun Badge(text: String, color: Color, textColor: Color = White) {
    Text(
        text, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(color).padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

/** Outlined (standard sets) or filled (block) structure capsule. */
@Composable
fun SessionStructureTag(text: String, fill: Color?) {
    val base = Modifier.clip(CircleShape)
    val styled = if (fill != null) base.background(fill) else base.border(1.dp, White.copy(alpha = 0.6f), CircleShape)
    Text(text, color = White, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, maxLines = 1,
        modifier = styled.padding(horizontal = 14.dp, vertical = 6.dp))
}

/** Coach note, boxed. Renders nothing for a blank cue. */
@Composable
fun SessionNoteCard(text: String?, label: String = "NOTE", maxLines: Int = 3, isBordered: Boolean = true, modifier: Modifier = Modifier) {
    val trimmed = text?.trim() ?: return
    if (trimmed.isEmpty()) return
    val shape = RoundedCornerShape(12.dp)
    val boxed = if (isBordered) modifier.clip(shape).background(Color.Black.copy(alpha = 0.45f)).border(1.dp, FLPrimary.copy(alpha = 0.45f), shape).padding(horizontal = 12.dp, vertical = 10.dp) else modifier
    Row(modifier = boxed, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Filled.Description, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(16.dp))
        Box(Modifier.width(1.dp).height(28.dp).background(White.copy(alpha = 0.25f)))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = FLPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Text(trimmed, color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Circular countdown; the arc derives from the same numbers as the digits. */
@Composable
fun SessionCountdownRing(secondsRemaining: Int, totalSeconds: Int, caption: String, tint: Color = FLPrimary, diameter: Dp = 168.dp) {
    val fraction = if (totalSeconds > 0) (secondsRemaining.toFloat() / totalSeconds).coerceIn(0f, 1f) else 0f
    val s = maxOf(secondsRemaining, 0)
    val display = if (s < 60) "$s" else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            drawCircle(Color.Black.copy(alpha = 0.35f))
            val stroke = 5.dp.toPx()
            drawCircle(White.copy(alpha = 0.18f), radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
            val inset = stroke / 2 + 2.5.dp.toPx()
            drawArc(
                tint, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 18.dp)) {
            Text(display, color = White, fontSize = 56.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(caption, color = White.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/** Full-width thumbnail card inside an interval block while a countdown runs toward the next work phase. */
@Composable
fun UpNextCard(descriptor: UpNextDescriptor, onTap: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "zoom")
    val scale by transition.animateFloat(1f, 1.12f, infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Reverse), label = "zoom")
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.55f)).clickable { onTap() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))) {
            FLImageView(ref = descriptor.mediaId?.let { MediaRef.MovementThumbnail(it) }, modifier = Modifier.size(72.dp).scale(scale))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("UP NEXT", color = White.copy(alpha = 0.65f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Text(descriptor.title, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            descriptor.detail?.let { Text(it, color = White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
        Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
    }
}

/** Set timer pill for the info strip meter row. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun SetTimerLabel(text: String, countsDown: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(Icons.Filled.Timer, contentDescription = null, tint = White, modifier = Modifier.size(15.dp))
        Text(text, color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

/** Structure colors shared with the editor / playlist: purple superset, orange circuit, blue interval. */
fun blockTint(kind: com.fitnesslink.fit.session.PreparationBlockSummary.Kind?): Color = when (kind) {
    com.fitnesslink.fit.session.PreparationBlockSummary.Kind.Superset -> PurpleTheme
    com.fitnesslink.fit.session.PreparationBlockSummary.Kind.Circuit -> OrangeTheme
    is com.fitnesslink.fit.session.PreparationBlockSummary.Kind.Interval -> BlueTheme
    null -> FLPrimary
}

fun intervalModeTint(mode: IntervalMode?): Color = BlueTheme
