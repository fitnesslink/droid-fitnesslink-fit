package com.fitnesslink.fit.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.MetricAvailability
import com.fitnesslink.fit.model.MetricChange
import com.fitnesslink.fit.model.MetricResult
import com.fitnesslink.fit.model.ProgressReport
import com.fitnesslink.fit.model.ReportTimeRange
import com.fitnesslink.fit.model.percentLabel
import com.fitnesslink.fit.ui.profile.charts.reportThemeColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme

// Shared building blocks for the Progress tab (iOS `ProgressHomeView.swift`
// shared components): period chips, change label, sparkline, Quick Stat
// card, report rows and the health-connection prompt.

/** Navigation target used by every Progress connection prompt. */
const val CONNECTED_APPS_ROUTE = "connectedApps"

/** iOS SF Symbol names used by the metric registry, mapped to Material glyphs. */
fun progressIcon(name: String): ImageVector = when (name) {
    "dumbbell.fill" -> Icons.Filled.FitnessCenter
    "clock" -> Icons.Filled.Schedule
    "chart.bar.fill" -> Icons.Filled.BarChart
    "timer" -> Icons.Filled.Timer
    "gauge.medium" -> Icons.Filled.Speed
    "square.stack.3d.up.fill" -> Icons.Filled.Layers
    "repeat" -> Icons.Filled.Repeat
    "trophy.fill" -> Icons.Filled.EmojiEvents
    "bolt.fill" -> Icons.Filled.Bolt
    "exclamationmark.circle" -> Icons.Filled.ErrorOutline
    "flame.fill" -> Icons.Filled.LocalFireDepartment
    "fork.knife" -> Icons.Filled.Restaurant
    "fish.fill" -> Icons.Filled.SetMeal
    "leaf.fill" -> Icons.Filled.Eco
    "drop.fill" -> Icons.Filled.WaterDrop
    "target" -> Icons.Filled.TrackChanges
    "shoeprints.fill" -> Icons.AutoMirrored.Filled.DirectionsWalk
    "bolt.heart.fill", "waveform.path.ecg" -> Icons.Filled.MonitorHeart
    "figure.run" -> Icons.AutoMirrored.Filled.DirectionsRun
    "moon.fill" -> Icons.Filled.Bedtime
    "heart.fill" -> Icons.Filled.Favorite
    "scalemass.fill", "scalemass" -> Icons.Filled.MonitorWeight
    "arrow.up.arrow.down" -> Icons.Filled.SwapVert
    "figure.stand" -> Icons.Filled.Accessibility
    "ruler" -> Icons.Filled.Straighten
    "scope" -> Icons.Filled.GpsFixed
    "checkmark.seal.fill" -> Icons.Filled.Verified
    "flame" -> Icons.Filled.Whatshot
    "camera" -> Icons.Filled.PhotoCamera
    "rosette" -> Icons.Filled.MilitaryTech
    else -> Icons.Filled.BarChart
}

/** Title bar for pushed Progress screens: back arrow, centred title, optional trailing action. */
@Composable
fun ProgressTitleBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = FLTheme.colors.textPrimary)
        }
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = FLTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp)
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

/** Brand-coloured text action for [ProgressTitleBar] (iOS toolbar "Edit" / "Reset"). */
@Composable
fun ProgressBarAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = FLPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

/** The six period chips (PRD §3.3). Scrolls horizontally when the labels don't fit. */
@Composable
fun PeriodFilterBar(selection: ReportTimeRange, onSelect: (ReportTimeRange) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(FLTheme.colors.surface)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ReportTimeRange.entries.forEach { range ->
                val selected = range == selection
                Text(
                    text = range.label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) FLTheme.colors.onBrand else FLTheme.colors.textPrimary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) FLPrimary else Color.Transparent)
                        .clickable { onSelect(range) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .semantics { contentDescription = if (selected) "${range.label}, selected" else range.label }
                )
            }
        }
    }
}

/**
 * Trend arrow + percentage. Coloured by the metric's accent rather than
 * green/red, so an increase never reads as automatically good (PRD §5.5).
 */
@Composable
fun MetricChangeLabel(result: MetricResult, showsCaption: Boolean = false, range: ReportTimeRange = ReportTimeRange.current) {
    val change = result.change ?: return
    val color = reportThemeColor(result.metric.color)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (change) {
                is MetricChange.Up -> Icon(Icons.Filled.ArrowUpward, contentDescription = "Up", tint = color, modifier = Modifier.size(12.dp))
                is MetricChange.Down -> Icon(Icons.Filled.ArrowDownward, contentDescription = "Down", tint = color, modifier = Modifier.size(12.dp))
                MetricChange.Steady -> Unit
            }
            Spacer(Modifier.width(2.dp))
            Text(change.percentLabel() ?: "No change", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
        if (showsCaption) {
            Text("vs. ${range.previousPeriodPhrase}", fontSize = 10.sp, color = FLTheme.colors.textSecondary)
        }
    }
}

/** Tiny trend line; drawn only when the result has enough points. */
@Composable
fun MetricSparkline(result: MetricResult, height: Dp = 22.dp, modifier: Modifier = Modifier) {
    if (!result.hasTrend) return
    val color = reportThemeColor(result.metric.color)
    val points = result.series
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val min = points.min()
        val max = points.max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / span * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx()))
    }
}

/** Area + line trend for a category report card (iOS `trendChart`). */
@Composable
fun MetricTrendChart(result: MetricResult, modifier: Modifier = Modifier) {
    if (!result.hasTrend) return
    val color = reportThemeColor(result.metric.color)
    val points = result.series
    Canvas(modifier = modifier.fillMaxWidth().height(70.dp)) {
        val min = minOf(points.min(), 0.0)
        val max = points.max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (points.size - 1)
        val offsets = points.mapIndexed { i, v ->
            Offset(i * stepX, size.height - ((v - min) / span * size.height * 0.92).toFloat())
        }
        val line = Path().apply {
            offsets.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(offsets.last().x, size.height)
            lineTo(offsets.first().x, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0.02f))))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx()))
    }
}

/**
 * One Quick Stats card. Unavailable metrics show a dash plus why — never a
 * fabricated zero (PRD §6.4 / §10.3) — and link to their fix.
 */
@Composable
fun QuickStatCard(result: MetricResult, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val destination = if (result.availability == MetricAvailability.NOT_CONNECTED) CONNECTED_APPS_ROUTE
    else result.metric.category.report.route
    val description = "${result.metric.title}, " +
        if (result.availability == MetricAvailability.AVAILABLE) result.displayValue else "no data"
    Column(
        modifier = modifier
            .heightIn(min = 132.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(FLTheme.colors.surface)
            .clickable { onNavigate(destination) }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            progressIcon(result.metric.icon),
            contentDescription = null,
            tint = reportThemeColor(result.metric.color),
            modifier = Modifier.size(22.dp)
        )
        Text(
            result.displayValue,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = FLTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(result.metric.shortTitle, fontSize = 11.sp, color = FLTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        when (result.availability) {
            MetricAvailability.AVAILABLE -> MetricChangeLabel(result)
            MetricAvailability.NOT_CONNECTED -> Text("Not connected", fontSize = 11.sp, color = FLTheme.colors.textSecondary)
            MetricAvailability.NO_DATA -> Text("No data yet", fontSize = 11.sp, color = FLTheme.colors.textSecondary)
        }
        Spacer(Modifier.weight(1f, fill = false))
        MetricSparkline(result, height = 18.dp)
    }
}

@Composable
fun ReportIconTile(report: ProgressReport, size: Dp = 44.dp) {
    val color = reportThemeColor(report.color)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.25f))
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(progressIcon(report.icon), contentDescription = null, tint = color, modifier = Modifier.size(size * 0.45f))
    }
}

/** Compact report row for the landing page. */
@Composable
fun ReportRow(report: ProgressReport, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FLTheme.colors.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ReportIconTile(report, 44.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(report.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary)
            Text(report.summary, fontSize = 12.sp, color = FLTheme.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        ChevronRight()
    }
}

@Composable
fun ChevronRight(size: Dp = 18.dp) {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = FLTheme.colors.textSecondary,
        modifier = Modifier.size(size)
    )
}

/** Neutral Health Connect row wherever a metric needs a health source (PRD §9.2): it states what the source is for and never pushes the user to grant access. */
@Composable
fun HealthConnectPrompt(message: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FLTheme.colors.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.MonitorHeart, contentDescription = null, tint = FLTheme.colors.danger, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Health Connect (optional)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary)
            Text(message, fontSize = 12.sp, color = FLTheme.colors.textSecondary)
        }
        ChevronRight(16.dp)
    }
}

/** Up/down glyph on the insight card. */
fun insightTrendIcon(isUp: Boolean): ImageVector =
    if (isUp) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown
