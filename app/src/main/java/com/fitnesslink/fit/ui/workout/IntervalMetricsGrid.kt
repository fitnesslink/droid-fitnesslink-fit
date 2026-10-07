package com.fitnesslink.fit.ui.workout

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
import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.IntervalMetrics
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

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
            color = if (emphasized) BlueTheme else TextPrimaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(text = label, fontSize = 10.sp, color = TextSecondaryColor, maxLines = 1)
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Timer, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Text(
                text = metrics.badgeText,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = White,
                modifier = Modifier.background(accent, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp)
            )
            val subtitle = buildList {
                add("${metrics.exerciseCount} exercise${if (metrics.exerciseCount == 1) "" else "s"}")
                metrics.recoveryMovementName?.let { add("Recovery: $it") }
            }.joinToString(" · ")
            Text(text = subtitle, fontSize = 12.sp, color = TextSecondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        IntervalMode.EMOM -> "${if (plan.sets > 1) "${plan.sets} × " else ""}${plan.roundsPerSet} min EMOM · ${plan.workSeconds}s work"
        IntervalMode.AMRAP -> "${if (plan.sets > 1) "${plan.sets} × " else ""}${IntervalMath.formatTime(plan.windowSeconds)} AMRAP"
    }
    val detail = buildList {
        when (metrics.mode) {
            IntervalMode.TABATA, IntervalMode.HIIT -> { add("Work ${plan.workSeconds}s"); add("Rest ${plan.restSeconds}s"); add("${plan.roundsPerSet} rounds") }
            IntervalMode.EMOM -> { add("${plan.roundsPerSet} min"); add("Work ${plan.workSeconds}s/min") }
            IntervalMode.AMRAP -> add("${IntervalMath.formatTime(plan.windowSeconds)} window")
        }
        if (plan.sets > 1) { add("${plan.sets} sets"); add("${metrics.betweenSetsText} between sets") }
    }.joinToString(" · ")

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = structure, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "·", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
            Text(text = metrics.totalText, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BlueTheme)
        }
        Text(text = detail, fontSize = 11.sp, color = TextSecondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        metrics.recoveryMovementName?.let {
            Text(text = "Recovery: $it", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = OrangeTheme, maxLines = 1)
        }
    }
}
