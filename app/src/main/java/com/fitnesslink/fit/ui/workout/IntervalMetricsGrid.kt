package com.fitnesslink.fit.ui.workout

import com.fitnesslink.fit.ui.theme.FLTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.EmomMath
import com.fitnesslink.fit.model.EmomText
import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.IntervalMetrics
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme

/** Small value-over-label tile shared by the detail / playlist grids and the Edit Interval sheet. */
@Composable
fun IntervalStatTile(label: String, value: String, emphasized: Boolean = false, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(FLPrimary.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = if (emphasized) 17.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (emphasized) BlueTheme else FLTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(text = label, fontSize = 10.sp, color = FLTheme.colors.textSecondary, maxLines = 1)
    }
}

/**
 * Metric-rich interval header for the detail / playlist cards (HIIT-001):
 * badge row ("INTERVAL · HIIT" + exercise count + recovery movement) over a
 * 3-column grid of Work / Rest / Rounds / Sets / Rest Between Sets / Total.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntervalMetricsGrid(metrics: IntervalMetrics, accent: Color = BlueTheme) {
    if (metrics.emom != null) { EmomMetricsHeader(metrics, accent); return }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Timer, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Text(
                text = metrics.badgeText,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FLTheme.colors.onBrand,
                modifier = Modifier.background(accent, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp)
            )
            val subtitle = buildList {
                add("${metrics.exerciseCount} exercise${if (metrics.exerciseCount == 1) "" else "s"}")
                metrics.recoveryMovementName?.let { add("Recovery: $it") }
            }.joinToString(" · ")
            Text(text = subtitle, fontSize = 12.sp, color = FLTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 3
        ) {
            metrics.tiles.forEach { tile ->
                IntervalStatTile(label = tile.label, value = tile.value, emphasized = tile.label == "Total", modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * EMOM header for Workout Overview and Playlist (PRD §5, §6): "INTERVAL · EMOM" + "2 exercises",
 * Duration / Interval / Rounds / Cycles as a 2×2 grid (one row of three for a single exercise),
 * then "How this EMOM works" and the generated instruction.
 */
@Composable
fun EmomMetricsHeader(metrics: IntervalMetrics, accent: Color = BlueTheme, modifier: Modifier = Modifier) {
    val emom = metrics.emom ?: return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Timer, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Text(
                text = metrics.badgeText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = FLTheme.colors.onBrand,
                modifier = Modifier.background(accent, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Text("${emom.exerciseCount} exercise${if (emom.exerciseCount == 1) "" else "s"}", fontSize = 12.sp, color = FLTheme.colors.textSecondary, maxLines = 1)
        }
        val tiles = EmomMath.overviewTiles(emom)
        val perRow = if (tiles.size == 4) 2 else tiles.size
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(maxOf(1, perRow)).forEach { rowTiles ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    rowTiles.forEach { tile -> IntervalStatTile(label = tile.label, value = tile.value, modifier = Modifier.weight(1f)) }
                }
            }
        }
        metrics.emomInstruction?.let { text ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(EmomText.heading, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
                Text(text, fontSize = 13.sp, color = FLTheme.colors.textSecondary)
            }
        }
    }
}

/**
 * Compact two-line metrics block for the builder's interval card: the dense
 * structure line with the total emphasized, a detail line, and the recovery
 * movement on its own line when set.
 */
@Composable
fun IntervalMetricsLine(metrics: IntervalMetrics, modifier: Modifier = Modifier) {
    val plan = metrics.plan
    val structure = when (metrics.mode) {
        IntervalMode.TABATA, IntervalMode.HIIT -> {
            val core = "${plan.roundsPerSet} × ${plan.workSeconds}s/${plan.restSeconds}s"
            if (plan.sets > 1) "${plan.sets} × ($core)" else core
        }
        IntervalMode.EMOM -> metrics.emom?.let { EmomMath.summaryTitle(it) } ?: "EMOM"
        IntervalMode.AMRAP -> "${if (plan.sets > 1) "${plan.sets} × " else ""}${IntervalMath.formatTime(plan.windowSeconds)} AMRAP"
    }
    val detail = buildList {
        when (metrics.mode) {
            IntervalMode.TABATA, IntervalMode.HIIT -> { add("Work ${plan.workSeconds}s"); add("Rest ${plan.restSeconds}s"); add("${plan.roundsPerSet} rounds") }
            IntervalMode.EMOM -> metrics.emom?.let { add(EmomMath.roundsCyclesLine(it)) }
            IntervalMode.AMRAP -> add("${IntervalMath.formatTime(plan.windowSeconds)} window")
        }
        if (plan.sets > 1) { add("${plan.sets} sets"); add("${metrics.betweenSetsText} between sets") }
    }.joinToString(" · ")

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = structure, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "·", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textSecondary)
            Text(text = metrics.totalText, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BlueTheme)
        }
        Text(text = detail, fontSize = 11.sp, color = FLTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        metrics.recoveryMovementName?.let {
            Text(text = "Recovery: $it", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = OrangeTheme, maxLines = 1)
        }
    }
}
