package com.fitnesslink.fit.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.MovementAggregate
import com.fitnesslink.fit.model.MovementHistoryEntry
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Per-exercise progression view (FA-82). Shows totals + PRs across the
 * selected window plus a session-by-session breakdown with set details.
 */
@Composable
fun MovementAggregateDetailScreen(
    exerciseName: String,
    onBack: () -> Unit
) {
    var rangeDays by remember { mutableStateOf(90) }
    var aggregate by remember { mutableStateOf<MovementAggregate?>(null) }

    LaunchedEffect(exerciseName, rangeDays) {
        val userId = DatabaseManager.user()?.id ?: "user1"
        val since = if (rangeDays == Int.MAX_VALUE) 0L
        else System.currentTimeMillis() - rangeDays * 86_400_000L
        val all = DatabaseManager.movementAggregates(userId, since)
        aggregate = all.firstOrNull { it.exerciseName.equals(exerciseName, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = exerciseName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor
            )

            RangeChips(
                selectedDays = rangeDays,
                onSelect = { rangeDays = it }
            )

            val agg = aggregate
            if (agg == null || agg.timesPerformed == 0) {
                EmptyState()
            } else {
                StatGrid(agg)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Recent sessions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryColor
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((agg.entries.size.coerceAtMost(10) * 96).dp)
                ) {
                    items(agg.entries.take(20)) { entry ->
                        SessionRow(entry = entry, prWeight = agg.prWeight)
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeChips(selectedDays: Int, onSelect: (Int) -> Unit) {
    val options = listOf(30 to "30d", 90 to "90d", Int.MAX_VALUE to "All")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (days, label) ->
            val selected = days == selectedDays
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) FLPrimary else White)
                    .border(
                        1.dp,
                        if (selected) FLPrimary else TextSecondaryColor.copy(alpha = 0.3f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onSelect(days) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) White else TextSecondaryColor
                )
            }
        }
    }
}

@Composable
private fun StatGrid(agg: MovementAggregate) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile(label = "Sessions", value = agg.timesPerformed.toString(), modifier = Modifier.weight(1f))
            StatTile(label = "Sets", value = agg.totalSets.toString(), modifier = Modifier.weight(1f))
            StatTile(label = "Reps", value = agg.totalReps.toString(), modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile(
                label = "Max weight",
                value = "${agg.maxWeight.toInt()} lb",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "PR · ${agg.prReps} reps",
                value = "${agg.prWeight.toInt()} lb",
                modifier = Modifier.weight(1f),
                accent = FLPrimary
            )
            StatTile(
                label = "Volume",
                value = "${agg.totalVolume.toInt()} lb",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = TextPrimaryColor
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .padding(12.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextSecondaryColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}

@Composable
private fun SessionRow(entry: MovementHistoryEntry, prWeight: Double) {
    val isPrSession = entry.weight >= prWeight && prWeight > 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.workoutName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryColor,
                modifier = Modifier.weight(1f)
            )
            if (isPrSession) {
                Text(
                    text = "PR",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(FLPrimary)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = dateFormat.format(entry.sessionDate),
            fontSize = 11.sp,
            color = TextSecondaryColor
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${entry.sets} sets · ${entry.reps} reps · max ${entry.weight.toInt()} lb",
            fontSize = 12.sp,
            color = TextSecondaryColor
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No history in this window",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimaryColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Try a wider date range, or log a session with this exercise.",
            fontSize = 13.sp,
            color = TextSecondaryColor
        )
    }
}

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
