package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

private const val MAX_RENDERED_ROUNDS = 3
private val WorkGreen = Color(0xFF22A35A)

/**
 * Read-only round-by-round preview of an interval block (iOS
 * `IntervalTimelinePreview`): the first set's Work / Rest chips, a
 * between-sets rest divider and a collapsed "repeats" row.
 */
@Composable
fun IntervalTimelinePreview(row: TaskRow) {
    val lead = row.task ?: return
    val mode = row.intervalMode ?: lead.intervalMode ?: return
    val plan = IntervalMath.plan(lead, rowTotalRounds = row.totalRounds, mode = mode)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (plan.mode) {
            IntervalMode.TABATA, IntervalMode.HIIT -> RoundRows(plan.roundsPerSet, plan.workSeconds, plan.restSeconds, "Round", restAfterLast = false)
            IntervalMode.EMOM -> RoundRows(plan.roundsPerSet, plan.workSeconds, plan.restSeconds, "Min", restAfterLast = true)
            IntervalMode.AMRAP -> {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(BlueTheme).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.AllInclusive, contentDescription = null, tint = White, modifier = Modifier.size(16.dp))
                    Text("As many rounds as possible in ${IntervalMath.formatTime(plan.windowSeconds)}", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (plan.mode != IntervalMode.AMRAP && plan.sets > 1) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = OrangeTheme, modifier = Modifier.size(12.dp))
                Text(" ${IntervalMath.formatTime(plan.betweenSetsRestSeconds)} Rest Between Sets ", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = OrangeTheme)
                Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = OrangeTheme, modifier = Modifier.size(12.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(BackgroundColor).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Repeat, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(14.dp))
                Text(
                    text = if (plan.sets == 2) " Set 2 repeats the same rounds" else " Sets 2–${plan.sets} repeat the same rounds",
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor
                )
            }
        }
    }
}

@Composable
private fun RoundRows(rounds: Int, work: Int, rest: Int, roundLabel: String, restAfterLast: Boolean) {
    val shown = minOf(MAX_RENDERED_ROUNDS, maxOf(1, rounds))
    for (n in 1..shown) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$roundLabel $n", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor, modifier = Modifier.width(52.dp))
            TimelineChip("Work ${work}s", WorkGreen)
            if (rest > 0 && (n < rounds || restAfterLast)) TimelineChip("Rest ${rest}s", MediumGray)
        }
    }
    if (rounds > shown) {
        val more = rounds - shown
        Text("⋯ $more more ${if (more == 1) "round" else "rounds"}", fontSize = 11.sp, color = TextSecondaryColor, modifier = Modifier.padding(start = 58.dp))
    }
}

@Composable
private fun TimelineChip(label: String, color: Color) {
    Text(
        text = label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = White,
        modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 10.dp, vertical = 5.dp)
    )
}
