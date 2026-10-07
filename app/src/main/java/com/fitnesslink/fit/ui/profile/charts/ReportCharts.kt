package com.fitnesslink.fit.ui.profile.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.ChartPoint
import com.fitnesslink.fit.ui.theme.BlendGray
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

/**
 * Canvas-drawn charts standing in for Swift Charts on the report screens
 * (no chart library). Both take date-keyed [ChartPoint]s, draw three
 * horizontal gridlines with y labels on the left, and `MMM d` labels under
 * the first / middle / last point.
 */

/** Y-axis label: whole numbers, "1.2k" past 1000. */
fun compactAxisLabel(value: Double): String =
    if (value >= 1000) String.format(Locale.US, "%.1fk", value / 1000).replace(".0k", "k")
    else "${Math.round(value)}"

private val axisLabelStyle = TextStyle(fontSize = 10.sp, color = TextSecondaryColor)
private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

private const val Y_LABEL_WIDTH_DP = 36f
private const val X_LABEL_HEIGHT_DP = 16f
private const val TOP_INSET_DP = 6f

private class Plot(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Pick a "nice" ceiling for the y axis so gridlines land on round values. */
private fun niceMax(raw: Double): Double {
    if (raw <= 0) return 1.0
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(raw)))
    val normalized = raw / magnitude
    val nice = when {
        normalized <= 1 -> 1.0
        normalized <= 2 -> 2.0
        normalized <= 2.5 -> 2.5
        normalized <= 5 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}

/**
 * Vertical bars, one per point, in a top-to-bottom gradient of [color]
 * with rounded tops. Used for completed / time / calories / duration.
 */
@Composable
fun ReportBarChart(
    points: List<ChartPoint>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    yLabel: (Double) -> String = ::compactAxisLabel
) {
    val textMeasurer = rememberTextMeasurer()
    val sorted = remember(points) { points.sortedBy { it.date } }
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (sorted.isEmpty()) return@Canvas
        val plot = plotArea()
        val yMax = niceMax(sorted.maxOf { it.value })
        drawGrid(textMeasurer, plot, 0.0, yMax, yLabel)

        val slot = plot.width / sorted.size
        val barWidth = (slot * 0.6f).coerceAtMost(28.dp.toPx())
        sorted.forEachIndexed { i, point ->
            val fraction = (point.value / yMax).coerceIn(0.0, 1.0).toFloat()
            val barHeight = max(plot.height * fraction, if (point.value > 0) 2.dp.toPx() else 0f)
            val x = plot.left + slot * i + (slot - barWidth) / 2f
            val y = plot.bottom - barHeight
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(color, color.copy(alpha = 0.55f)), startY = y, endY = plot.bottom),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }
        drawXLabels(textMeasurer, plot, sorted) { i -> plot.left + slot * i + slot / 2f }
    }
}

/**
 * Smoothed line through the points with optional gradient fill, hollow
 * point markers and a dashed reference line. Used for RPE / weight /
 * volume. [yRange] fixes the axis (RPE is 1…10); otherwise it fits the data.
 */
@Composable
fun ReportLineChart(
    points: List<ChartPoint>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    fill: Boolean = true,
    showPoints: Boolean = true,
    yRange: ClosedFloatingPointRange<Double>? = null,
    referenceY: Double? = null,
    yLabel: (Double) -> String = ::compactAxisLabel
) {
    val textMeasurer = rememberTextMeasurer()
    val sorted = remember(points) { points.sortedBy { it.date } }
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (sorted.isEmpty()) return@Canvas
        val plot = plotArea()
        val yMin = yRange?.start ?: 0.0
        val yMax = yRange?.endInclusive ?: niceMax(sorted.maxOf { it.value })
        val span = (yMax - yMin).takeIf { it > 0 } ?: 1.0
        drawGrid(textMeasurer, plot, yMin, yMax, yLabel)

        fun xAt(i: Int): Float =
            if (sorted.size == 1) plot.left + plot.width / 2f else plot.left + plot.width * i / (sorted.size - 1)
        fun yAt(v: Double): Float = plot.bottom - (plot.height * ((v - yMin) / span).coerceIn(0.0, 1.0)).toFloat()

        val pts = sorted.mapIndexed { i, p -> Offset(xAt(i), yAt(p.value)) }
        val line = smoothPath(pts)

        if (fill && pts.size >= 2) {
            val area = Path().apply {
                addPath(line)
                lineTo(pts.last().x, plot.bottom)
                lineTo(pts.first().x, plot.bottom)
                close()
            }
            drawPath(
                area,
                brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.03f)), startY = plot.top, endY = plot.bottom)
            )
        }
        if (pts.size >= 2) {
            drawPath(line, color = color, style = Stroke(width = 2.dp.toPx()))
        }
        referenceY?.takeIf { it > yMin }?.let { ref ->
            val y = yAt(ref)
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(plot.left, y), end = Offset(plot.right, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            )
        }
        if (showPoints) {
            pts.forEach { p ->
                drawCircle(Color.White, radius = 4.dp.toPx(), center = p)
                drawCircle(color, radius = 4.dp.toPx(), center = p, style = Stroke(width = 2.dp.toPx()))
            }
        }
        drawXLabels(textMeasurer, plot, sorted) { i -> xAt(i) }
    }
}

// MARK: - Drawing helpers

private fun DrawScope.plotArea(): Plot = Plot(
    left = Y_LABEL_WIDTH_DP.dp.toPx(),
    top = TOP_INSET_DP.dp.toPx(),
    right = size.width,
    bottom = size.height - X_LABEL_HEIGHT_DP.dp.toPx()
)

private fun DrawScope.drawGrid(
    textMeasurer: TextMeasurer,
    plot: Plot,
    yMin: Double,
    yMax: Double,
    yLabel: (Double) -> String
) {
    val steps = 2
    for (i in 0..steps) {
        val fraction = i.toFloat() / steps
        val y = plot.bottom - plot.height * fraction
        drawLine(BlendGray, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1f)
        val value = yMin + (yMax - yMin) * fraction
        val layout = textMeasurer.measure(yLabel(value), axisLabelStyle)
        drawText(layout, topLeft = Offset(plot.left - layout.size.width - 4.dp.toPx(), y - layout.size.height / 2f))
    }
}

private fun DrawScope.drawXLabels(
    textMeasurer: TextMeasurer,
    plot: Plot,
    points: List<ChartPoint>,
    xAt: (Int) -> Float
) {
    if (points.isEmpty()) return
    val indices = linkedSetOf(0, points.size / 2, points.size - 1)
    var lastRight = Float.NEGATIVE_INFINITY
    indices.forEach { i ->
        val layout = textMeasurer.measure(dateFormat.format(points[i].date), axisLabelStyle)
        val x = (xAt(i) - layout.size.width / 2f).coerceIn(plot.left, max(plot.left, plot.right - layout.size.width))
        if (x < lastRight + 6.dp.toPx()) return@forEach
        drawText(layout, topLeft = Offset(x, plot.bottom + 3.dp.toPx()))
        lastRight = x + layout.size.width
    }
}

/** Catmull-Rom-style smoothing through the points as cubic Béziers. */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 1) return path
    for (i in 0 until points.size - 1) {
        val p0 = points.getOrElse(i - 1) { points[i] }
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points.getOrElse(i + 2) { p2 }
        val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
        val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    return path
}
