package com.fitnesslink.fit.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.health.HealthConnectService
import com.fitnesslink.fit.model.MetricAvailability
import com.fitnesslink.fit.model.MetricResult
import com.fitnesslink.fit.model.ProgressMetric
import com.fitnesslink.fit.model.ProgressReport
import com.fitnesslink.fit.model.ReportTimeRange
import com.fitnesslink.fit.progress.ProgressMetricsEngine
import com.fitnesslink.fit.ui.profile.charts.reportThemeColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme

/** Every report in one list (iOS `AllReportsView`, PRD §8.5), each with two summary figures and a small trend. */
@Composable
fun AllReportsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    var range by remember { mutableStateOf(ReportTimeRange.current) }
    var results by remember { mutableStateOf<Map<ProgressMetric, MetricResult>>(emptyMap()) }

    val healthConnected by HealthConnectService.connected.collectAsState()
    LaunchedEffect(range, healthConnected) {
        ReportTimeRange.current = range
        val needed = ProgressReport.entries.flatMap { it.summaryMetrics }.toSet()
        results = ProgressMetricsEngine.load(needed, range)
    }

    Column(modifier = Modifier.fillMaxSize().background(FLTheme.colors.background)) {
        ProgressTitleBar("All Reports", onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Key metrics and recent trends for each area.",
                fontSize = 14.sp,
                color = FLTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            PeriodFilterBar(range, { range = it })
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProgressReport.entries.forEach { report ->
                    AllReportsCard(report, report.summaryMetrics.map { results[it] ?: MetricResult.unavailable(it) }) {
                        onNavigate(report.route)
                    }
                }
            }
        }
    }
}

@Composable
private fun AllReportsCard(report: ProgressReport, summaries: List<MetricResult>, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FLTheme.colors.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ReportIconTile(report, 52.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(report.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary)
            Text(report.summary, fontSize = 12.sp, color = FLTheme.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                summaries.forEach { result ->
                    Column {
                        Text(result.displayValue, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary, maxLines = 1)
                        Text(result.metric.shortTitle, fontSize = 10.sp, color = FLTheme.colors.textSecondary)
                    }
                }
            }
            summaries.firstOrNull()?.let { MetricSparkline(it, height = 16.dp) }
        }
        ChevronRight()
    }
}

/**
 * A report rendered from the shared registry: Activity & Cardio, Recovery &
 * Wellness, Body Metrics, Goals & Habits (iOS `CategoryReportView`, PRD §8).
 * Stays reachable with no data — empty and connection states explain how to
 * start collecting it (§8.6).
 */
@Composable
fun CategoryReportScreen(report: ProgressReport, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    var range by remember { mutableStateOf(ReportTimeRange.current) }
    var results by remember { mutableStateOf<Map<ProgressMetric, MetricResult>>(emptyMap()) }
    var isLoaded by remember { mutableStateOf(false) }
    val metrics = report.category.metrics

    val healthConnected by HealthConnectService.connected.collectAsState()
    LaunchedEffect(range, healthConnected) {
        ReportTimeRange.current = range
        results = ProgressMetricsEngine.load(metrics.toSet(), range)
        isLoaded = true
    }

    val needsHealth = metrics.any { results[it]?.availability == MetricAvailability.NOT_CONNECTED }
    val connectMessage = when (report) {
        ProgressReport.RECOVERY -> ProgressMetric.AVERAGE_SLEEP.connectPrompt
        ProgressReport.ACTIVITY -> ProgressMetric.AVERAGE_STEPS.connectPrompt
        else -> "Connect Health Connect to add body-fat readings from a smart scale or other source."
    }
    val tools: List<Triple<String, String, String>> = when (report) {
        ProgressReport.BODY -> listOf(
            Triple("Weight Log", "scalemass", "weight"),
            Triple("Measurements", "ruler", "measurements"),
            Triple("Progress Photos", "camera", "photos")
        )
        ProgressReport.GOALS -> listOf(Triple("Goals", "target", "goals"), Triple("Achievements", "rosette", "achievements"))
        ProgressReport.ACTIVITY -> listOf(Triple("Workout Report", "dumbbell.fill", "workoutReport"))
        else -> emptyList()
    }

    Column(modifier = Modifier.fillMaxSize().background(FLTheme.colors.background)) {
        ProgressTitleBar(report.title, onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ReportIconTile(report, 44.dp)
                Text(report.summary, fontSize = 14.sp, color = FLTheme.colors.textSecondary)
            }

            PeriodFilterBar(range, { range = it })

            if (needsHealth) {
                HealthConnectPrompt(connectMessage, onClick = { onNavigate(CONNECTED_APPS_ROUTE) }, modifier = Modifier.padding(horizontal = 20.dp))
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                metrics.forEach { metric ->
                    DetailCard(results[metric] ?: MetricResult.unavailable(metric), range, isLoaded)
                }
            }

            if (tools.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Track", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
                    tools.forEach { (title, icon, route) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(FLTheme.colors.surface)
                                .clickable { onNavigate(route) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(progressIcon(icon), contentDescription = null, tint = FLPrimary, modifier = Modifier.size(22.dp))
                            Text(title, fontSize = 15.sp, color = FLTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                            ChevronRight(16.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCard(result: MetricResult, range: ReportTimeRange, isLoaded: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FLTheme.colors.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(progressIcon(result.metric.icon), contentDescription = null, tint = reportThemeColor(result.metric.color), modifier = Modifier.size(20.dp))
            Text(
                result.metric.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = FLTheme.colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (result.availability == MetricAvailability.AVAILABLE) MetricChangeLabel(result)
        }
        when (result.availability) {
            MetricAvailability.AVAILABLE -> {
                Text(result.displayValue, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
                MetricTrendChart(result)
                if (result.change != null) {
                    Text("vs. ${range.previousPeriodPhrase}", fontSize = 11.sp, color = FLTheme.colors.textSecondary)
                }
            }
            MetricAvailability.NOT_CONNECTED ->
                Text(result.metric.connectPrompt, fontSize = 13.sp, color = FLTheme.colors.textSecondary)
            MetricAvailability.NO_DATA ->
                Text(if (isLoaded) result.metric.emptyMessage else " ", fontSize = 13.sp, color = FLTheme.colors.textSecondary)
        }
        Text(result.metric.definition, fontSize = 11.sp, color = FLTheme.colors.textTertiary)
    }
}
