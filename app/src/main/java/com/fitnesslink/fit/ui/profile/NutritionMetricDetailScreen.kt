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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.DailyNutritionRow
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import java.text.SimpleDateFormat
import java.util.Locale

/** Drill-down columns surfaced from DailyNutritionRow. */
private enum class NutritionMetric(val label: String, val unit: String) {
    Calories("Calories", "cal"),
    Protein("Protein", "g"),
    Fat("Fat", "g"),
    Carbs("Carbs", "g")
}

private fun metricFor(key: String): NutritionMetric = when (key.lowercase()) {
    "protein" -> NutritionMetric.Protein
    "fat" -> NutritionMetric.Fat
    "carbs" -> NutritionMetric.Carbs
    else -> NutritionMetric.Calories
}

@Composable
fun NutritionMetricDetailScreen(
    metricKey: String,
    onBack: () -> Unit
) {
    val metric = remember(metricKey) { metricFor(metricKey) }
    var rangeDays by remember { mutableStateOf(30) }
    var rows by remember { mutableStateOf<List<DailyNutritionRow>>(emptyList()) }

    // Cached data goes up immediately; same path runs again on range
    // change. No spinner — DB read is local and fast.
    LaunchedEffect(rangeDays) {
        val since = if (rangeDays >= 36500) 0L
        else System.currentTimeMillis() - rangeDays * 86_400_000L
        rows = DatabaseManager.dailyNutritionRows(since)
    }

    val values = rows.map { valueFor(metric, it) }
    val total = values.sum()
    val avg = if (values.isNotEmpty()) total / values.size else 0.0
    val max = values.maxOrNull() ?: 0.0

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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = metric.label,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor
            )

            RangeChips(
                selected = rangeDays,
                onSelect = { rangeDays = it }
            )

            if (rows.isEmpty()) {
                EmptyCard()
                return@Column
            }

            // Totals card.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(White)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCol(label = "Total", value = formatValue(total, metric), modifier = Modifier.weight(1f))
                    StatCol(label = "Daily avg", value = formatValue(avg, metric), modifier = Modifier.weight(1f))
                    StatCol(label = "Peak", value = formatValue(max, metric), modifier = Modifier.weight(1f))
                }
            }

            // Daily breakdown — text + tiny inline bar so a chart lib isn't
            // required to land FA-99.
            Text(
                text = "Daily breakdown",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryColor
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height((rows.size.coerceAtMost(15) * 56).dp)
            ) {
                items(rows.take(60)) { row ->
                    DayRow(
                        row = row,
                        metric = metric,
                        max = max
                    )
                }
            }
        }
    }
}

@Composable
private fun RangeChips(selected: Int, onSelect: (Int) -> Unit) {
    val opts = listOf(7 to "7d", 30 to "30d", 90 to "90d", Int.MAX_VALUE to "All")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opts.forEach { (days, label) ->
            val isSel = days == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSel) FLPrimary else White)
                    .border(
                        1.dp,
                        if (isSel) FLPrimary else TextSecondaryColor.copy(alpha = 0.3f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onSelect(days) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSel) White else TextSecondaryColor
                )
            }
        }
    }
}

@Composable
private fun StatCol(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 11.sp, color = TextSecondaryColor)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
    }
}

@Composable
private fun DayRow(row: DailyNutritionRow, metric: NutritionMetric, max: Double) {
    val v = valueFor(metric, row)
    val pct = if (max > 0) (v / max).toFloat().coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dateFormat.format(row.date),
                fontSize = 12.sp,
                color = TextSecondaryColor,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatValue(v, metric),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryColor
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MediumGray.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct)
                    .height(6.dp)
                    .background(FLPrimary, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun EmptyCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No data in this window",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimaryColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Try a wider range, or log a few meals first.",
            fontSize = 12.sp,
            color = TextSecondaryColor
        )
    }
}

private fun valueFor(metric: NutritionMetric, row: DailyNutritionRow): Double = when (metric) {
    NutritionMetric.Calories -> row.calories.toDouble()
    NutritionMetric.Protein -> row.protein
    NutritionMetric.Fat -> row.fat
    NutritionMetric.Carbs -> row.carbs
}

private fun formatValue(v: Double, metric: NutritionMetric): String =
    if (metric == NutritionMetric.Calories) "${v.toInt()} ${metric.unit}"
    else "%.0f %s".format(v, metric.unit)

private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
