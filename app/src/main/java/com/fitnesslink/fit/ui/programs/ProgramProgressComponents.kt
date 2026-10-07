package com.fitnesslink.fit.ui.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.ProgramSnapshot
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.ProgressBG
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

// Port of iOS `ProgramProgressView.swift`'s shared pieces: the segmented /
// continuous progress track, the labelled progress row and the hero image,
// used by the Home card, My Program and Program Progress.

/** Rounded progress track. 2–12 segments draw as pills with [filled] lit; otherwise a continuous bar. */
@Composable
fun ProgramProgressBar(filled: Int, total: Int, modifier: Modifier = Modifier, tint: Color = FLPrimary, height: Dp = 8.dp) {
    if (total in 2..12) {
        Row(modifier = modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(total) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (index < filled) tint else ProgressBG)
                )
            }
        }
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxWidth().height(height).clip(CircleShape).background(ProgressBG)) {
            val fraction = if (total > 0) (filled.toFloat() / total).coerceIn(0f, 1f) else 0f
            Box(modifier = Modifier.width(maxWidth * fraction).fillMaxHeight().clip(CircleShape).background(tint))
        }
    }
}

/** One labelled progress line: title, "x of y …" caption, bar and percent. */
@Composable
fun ProgramProgressRow(
    title: String,
    caption: String,
    filled: Int,
    total: Int,
    percent: Int,
    modifier: Modifier = Modifier,
    tint: Color = FLPrimary
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Spacer(modifier = Modifier.weight(1f))
            Text(caption, fontSize = 12.sp, color = TextSecondaryColor)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProgramProgressBar(filled = filled, total = total, tint = tint, modifier = Modifier.weight(1f))
            Text(
                "$percent%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 38.dp)
            )
        }
    }
}

/** Hero image with the program name (and an optional subtitle) over a bottom scrim. */
@Composable
fun ProgramHeroView(
    imageId: String?,
    name: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    height: Dp = 130.dp
) {
    Box(modifier = modifier.fillMaxWidth().height(height)) {
        FLImageView(ref = imageId?.let { MediaRef.ProgramThumbnail(it) }, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = White.copy(alpha = 0.9f))
            }
        }
    }
}

/** "Week 2 of 6 · Base Phase" */
fun ProgramSnapshot.weekLine(week: Int = currentWeek): String {
    val base = "Week $week of $totalWeeks"
    val phase = weeks.firstOrNull { it.weekNumber == week }?.phase ?: return base
    return "$base · $phase Phase"
}

/** Program artwork id: the cached program's, else what the calendar entries carried when they were scheduled. */
fun ProgramSnapshot.heroImageId(): String? {
    val cached = DatabaseManager.program(program.programId)
    (cached?.thumbnailId ?: cached?.imageId)?.let { return it.toString() }
    return weeks.flatMap { it.days }.flatMap { it.entries }.firstNotNullOfOrNull { it.programThumbnailId ?: it.programImageId }
}
