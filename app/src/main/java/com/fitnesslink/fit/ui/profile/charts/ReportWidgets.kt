package com.fitnesslink.fit.ui.profile.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.ReportMetric
import com.fitnesslink.fit.model.ReportTimeRange
import com.fitnesslink.fit.model.SessionRow
import com.fitnesslink.fit.model.SessionRpeRule
import com.fitnesslink.fit.model.TrendDirection
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small building blocks shared by the report screens. */

private val reportDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

/** "Oct 6, 2026" */
fun formatReportDate(date: Date): String = reportDateFormat.format(date)

/** "1h 12m" / "45m" */
fun formatReportDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** Theme colour for a metric's name-keyed colour (`ReportMetric.color`). */
fun reportThemeColor(name: String): Color = when (name) {
    "FLPrimary" -> FLPrimary
    "BlueTheme" -> BlueTheme
    "OrangeTheme" -> OrangeTheme
    "PurpleTheme" -> PurpleTheme
    "MediumGray" -> MediumGray
    else -> FLPrimary
}

fun ReportMetric.themeColor(): Color = reportThemeColor(color)

/** Segmented control in the report's style (white pill, brand fill on the selection). */
@Composable
fun <T> ReportSegmentedPicker(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(White)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(option) }
                    .background(if (isSelected) FLPrimary else White)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(option),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) White else TextSecondaryColor
                )
            }
        }
    }
}

@Composable
fun ReportTimeRangePicker(selected: ReportTimeRange, onSelect: (ReportTimeRange) -> Unit, modifier: Modifier = Modifier) {
    ReportSegmentedPicker(ReportTimeRange.entries, selected, { it.label }, onSelect, modifier)
}

/** Icon + value + label tile (iOS `statCard`). */
@Composable
fun ReportStatCard(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    annotation: String? = null,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, fontSize = 10.sp, color = TextSecondaryColor, textAlign = TextAlign.Center)
        if (annotation != null) {
            Text(annotation, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

/** Value + label, no icon (card stat rows). */
@Composable
fun ReportMiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 9.sp, color = TextSecondaryColor)
    }
}

fun TrendDirection.themeColor(): Color = when (this) {
    TrendDirection.UP -> FLPrimary
    TrendDirection.DOWN -> OrangeTheme
    TrendDirection.FLAT -> MediumGray
}

fun TrendDirection.imageVector(): ImageVector = when (this) {
    TrendDirection.UP -> Icons.Default.TrendingUp
    TrendDirection.DOWN -> Icons.Default.TrendingDown
    TrendDirection.FLAT -> Icons.Default.TrendingFlat
}

/** Compact "↗ Volume" pill (report cards). */
@Composable
fun ReportTrendPill(label: String, trend: TrendDirection) {
    val color = trend.themeColor()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(trend.imageVector(), contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(3.dp))
        Text(label, fontSize = 11.sp, color = color)
    }
}

/** Full-width trend cell (aggregate detail "Trends" card). */
@Composable
fun ReportTrendRow(label: String, trend: TrendDirection, modifier: Modifier = Modifier) {
    val color = trend.themeColor()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(trend.imageVector(), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

/** Colour band for a numeric RPE (iOS `RPEBadge`). */
fun rpeBandColor(value: Double): Color = when (value.toInt()) {
    in 1..4 -> FLPrimary
    5, 6 -> BlueTheme
    7, 8 -> OrangeTheme
    9, 10 -> Color(0xFFE53935)
    else -> TextSecondaryColor
}

/** Short descriptor used on the RPE list badges. */
fun rpeBandLabel(value: Double): String = when (value.toInt()) {
    1, 2 -> "Very Light"
    3, 4 -> "Light"
    5, 6 -> "Moderate"
    7, 8 -> "Hard"
    9 -> "Very Hard"
    10 -> "Max Effort"
    else -> ""
}

@Composable
fun RpeBadge(value: Double) {
    val color = rpeBandColor(value)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(SessionRpeRule.displayText(value) ?: "--", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        val label = rpeBandLabel(value)
        if (label.isNotEmpty()) Text(label, fontSize = 11.sp, color = color)
    }
}

/** "No data for this period" placeholder for a detail section. */
@Composable
fun ReportEmptyDetail(message: String = "No data for this period") {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.BarChart, contentDescription = null, tint = MediumGray, modifier = Modifier.size(36.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, fontSize = 15.sp, color = TextSecondaryColor)
    }
}

/**
 * "Delete this session?" confirmation (iOS confirmationDialog). [session]
 * null hides it. The message names the session, not the workout.
 */
@Composable
fun DeleteSessionDialog(
    session: SessionRow?,
    onConfirm: (SessionRow) -> Unit,
    onDismiss: () -> Unit
) {
    if (session == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this session?") },
        text = {
            Text(
                "Removes “${session.workoutName}” on ${formatReportDate(session.displayDate)} " +
                    "and its logged sets from your history and reports."
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(session) }) {
                Text("Delete Session", color = Color(0xFFE53935), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondaryColor) }
        }
    )
}
