package com.fitnesslink.fit.ui.nutrition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.MacroProgress
import com.fitnesslink.fit.model.api.Micronutrient
import com.fitnesslink.fit.network.dto.MicroSummaryLine
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.ProgressBG
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import java.util.Locale

/** Amber over-target accent (iOS `ProgressHighlight`): a signal, never an error. */
val NutritionOverColor = Color(0xFFF2A516)

/**
 * One macro's goal / consumed / remaining line with a progress bar (iOS
 * `MacroBarView`, FTNSSLNK-194). Over-target caps the bar at full, adds an
 * amber outline and flips the trailing label to "Xg over".
 */
@Composable
fun MacroBarView(name: String, line: MacroProgress, color: Color, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(line.fraction.toFloat(), tween(400), label = "macroBar")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
            Spacer(modifier = Modifier.weight(1f))
            val goal = line.goal
            if (goal != null) {
                Text("${Math.round(line.consumed)}/${Math.round(goal)}g", fontSize = 12.sp, color = TextSecondaryColor)
            } else {
                Text("${Math.round(line.consumed)}g", fontSize = 12.sp, color = TextSecondaryColor)
            }
            if (line.isOver) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = NutritionOverColor, modifier = Modifier.size(10.dp))
                    Text("${Math.round(line.overAmount)}g over", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NutritionOverColor)
                }
            } else {
                line.remaining?.let { remaining ->
                    Text("${Math.round(remaining)}g left", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
                }
            }
        }
        ProgressTrack(fraction = fraction, color = color, isOver = line.isOver, height = 8.dp)
    }
}

/** Legacy convenience so older call sites keep working. */
@Composable
fun MacroBarView(name: String, current: Double, target: Int, color: Color) {
    MacroBarView(name = name, line = MacroProgress(goal = if (target > 0) target.toDouble() else null, consumed = current), color = color)
}

/**
 * One micronutrient's consumed-vs-target line (iOS `MicroBarView`,
 * FTNSSLNK-216). `consumed == null` (or no line at all) means NO data was
 * recorded that day: an em-dash, never 0, and the bar stays unfilled. A
 * micro with no target (Sugars) shows the consumed amount only.
 */
@Composable
fun MicroBarView(micro: Micronutrient, line: MicroSummaryLine?, modifier: Modifier = Modifier) {
    val unit = line?.unit?.takeIf { it.isNotEmpty() } ?: micro.unit
    val target = line?.target ?: micro.defaultTarget
    val consumed = line?.consumed
    val remaining: Double? = consumed?.let { c -> line?.remaining ?: target?.let { it - c } }
    val isOver = (remaining ?: 0.0) < 0
    val fraction = if (consumed != null && target != null && target > 0) (consumed / target).coerceAtMost(1.0).toFloat() else 0f
    val animated by animateFloatAsState(fraction, tween(400), label = "microBar")

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(micro.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
            Spacer(modifier = Modifier.weight(1f))
            if (consumed != null) {
                if (target != null) {
                    Text("${formatMicro(consumed)}/${formatMicro(target)} $unit", fontSize = 12.sp, color = TextSecondaryColor)
                } else {
                    Text("${formatMicro(consumed)} $unit", fontSize = 12.sp, color = TextSecondaryColor)
                }
                if (isOver && remaining != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = NutritionOverColor, modifier = Modifier.size(10.dp))
                        Text("${formatMicro(-remaining)} $unit over", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NutritionOverColor)
                    }
                } else if (remaining != null) {
                    Text("${formatMicro(remaining)} $unit left", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
                }
            } else {
                Text("—", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
            }
        }
        ProgressTrack(fraction = animated, color = FLPrimary, isOver = isOver, height = 6.dp)
    }
}

@Composable
private fun ProgressTrack(fraction: Float, color: Color, isOver: Boolean, height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(ProgressBG)
            .then(if (isOver) Modifier.border(2.dp, NutritionOverColor, RoundedCornerShape(4.dp)) else Modifier)
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(height)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

/** Whole numbers drop the decimals (mg values); fractional grams keep one place. */
fun formatMicro(value: Double): String =
    if (value == Math.rint(value)) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
