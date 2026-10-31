package com.fitnesslink.fit.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.health.HealthConnectService
import com.fitnesslink.fit.model.MetricAvailability
import com.fitnesslink.fit.model.MetricResult
import com.fitnesslink.fit.model.MetricSource
import com.fitnesslink.fit.model.ProgressCategory
import com.fitnesslink.fit.model.ProgressMetric
import com.fitnesslink.fit.model.QuickStatsStore
import com.fitnesslink.fit.model.ReportTimeRange
import com.fitnesslink.fit.progress.ProgressMetricsEngine
import com.fitnesslink.fit.ui.profile.charts.reportThemeColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme

/** The full statistics catalog by category (iOS `AllStatsView`, PRD §6.1). Edit opens Customize Quick Stats. */
@Composable
fun AllStatsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    var range by remember { mutableStateOf(ReportTimeRange.current) }
    var results by remember { mutableStateOf<Map<ProgressMetric, MetricResult>>(emptyMap()) }

    val healthConnected by HealthConnectService.connected.collectAsState()
    LaunchedEffect(range, healthConnected) {
        ReportTimeRange.current = range
        results = ProgressMetricsEngine.load(ProgressMetric.entries.toSet(), range)
    }

    Column(modifier = Modifier.fillMaxSize().background(FLTheme.colors.background)) {
        ProgressTitleBar("All Stats", onBack) {
            ProgressBarAction("Edit") { onNavigate(ProgressRoutes.CUSTOMIZE_QUICK_STATS) }
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            PeriodFilterBar(range, { range = it })

            if (!healthConnected) {
                HealthConnectPrompt(
                    "Unlock steps, sleep, heart rate and HRV from Health Connect or a connected wearable.",
                    onClick = { onNavigate(CONNECTED_APPS_ROUTE) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            ProgressCategory.entries.forEach { category ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        category.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = FLTheme.colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        category.metrics.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { metric ->
                                    StatCatalogCard(
                                        results[metric] ?: MetricResult.unavailable(metric),
                                        range,
                                        onNavigate,
                                        Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Catalog card: value, change and the comparison window, or why the metric is empty. */
@Composable
fun StatCatalogCard(result: MetricResult, range: ReportTimeRange, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val destination = if (result.availability == MetricAvailability.NOT_CONNECTED) CONNECTED_APPS_ROUTE
    else result.metric.category.report.route
    Row(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(FLTheme.colors.surface)
            .clickable { onNavigate(destination) }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            progressIcon(result.metric.icon),
            contentDescription = null,
            tint = reportThemeColor(result.metric.color),
            modifier = Modifier.size(22.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(result.metric.title, fontSize = 12.sp, color = FLTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(result.displayValue, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when (result.availability) {
                MetricAvailability.AVAILABLE -> MetricChangeLabel(result, showsCaption = true, range = range)
                MetricAvailability.NOT_CONNECTED -> Text("Needs Health Connect", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                MetricAvailability.NO_DATA -> Text(result.metric.emptyMessage, fontSize = 10.sp, color = FLTheme.colors.textSecondary, maxLines = 2)
            }
        }
    }
}

/**
 * Choose and order the six Quick Stats (iOS `CustomizeQuickStatsView`, PRD
 * §6.3). Changes persist only on Save Changes; leaving discards them.
 * Reordering uses up/down arrows (iOS drag handles).
 */
@Composable
fun CustomizeQuickStatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedRaw by rememberSaveable { mutableStateOf(QuickStatsStore.load(context).map { it.name }) }
    val selected = selectedRaw.mapNotNull { name -> ProgressMetric.entries.firstOrNull { it.name == name } }
    val available = ProgressMetric.entries.filter { it !in selected }
    val isFull = selected.size >= ProgressMetric.MAX_QUICK_STATS
    var showLimitAlert by remember { mutableStateOf(false) }

    fun update(list: List<ProgressMetric>) { selectedRaw = list.map { it.name } }

    Column(modifier = Modifier.fillMaxSize().background(FLTheme.colors.background)) {
        ProgressTitleBar("Customize Quick Stats", onBack) {
            ProgressBarAction("Reset") { update(ProgressMetric.defaultQuickStats) }
        }
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
            item { SectionHeader("Selected (${selected.size}/${ProgressMetric.MAX_QUICK_STATS})") }
            itemsIndexed(selected, key = { _, m -> "s_${m.name}" }) { index, metric ->
                CustomizeRow(
                    metric = metric,
                    leading = {
                        RoundAction(Icons.Filled.RemoveCircle, "Remove ${metric.title}", FLTheme.colors.danger) {
                            update(selected - metric)
                        }
                    },
                    trailing = {
                        RoundAction(Icons.Filled.KeyboardArrowUp, "Move ${metric.title} up", FLTheme.colors.textSecondary, enabled = index > 0) {
                            update(selected.toMutableList().apply { add(index - 1, removeAt(index)) })
                        }
                        RoundAction(Icons.Filled.KeyboardArrowDown, "Move ${metric.title} down", FLTheme.colors.textSecondary, enabled = index < selected.lastIndex) {
                            update(selected.toMutableList().apply { add(index + 1, removeAt(index)) })
                        }
                    }
                )
            }
            if (selected.isEmpty()) {
                item { SectionFooter("Add at least one stat. With none selected, Progress shows the six defaults.") }
            }
            item { SectionHeader("Available Stats (${available.size})") }
            itemsIndexed(available, key = { _, m -> "a_${m.name}" }) { _, metric ->
                CustomizeRow(
                    metric = metric,
                    leading = {
                        RoundAction(
                            Icons.Filled.AddCircle,
                            "Add ${metric.title}",
                            if (isFull) FLTheme.colors.textTertiary else FLPrimary
                        ) {
                            if (isFull) showLimitAlert = true else update(selected + metric)
                        }
                    }
                )
            }
            item {
                SectionFooter(
                    if (isFull) "You've chosen 6 stats. Remove one to add another."
                    else "Stats marked Health Connect need a connection in Connected Apps & Devices; until then they show as unavailable."
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(FLTheme.colors.surface)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                "Save Changes",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = FLTheme.colors.onBrand,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FLPrimary)
                    .clickable {
                        QuickStatsStore.save(context, selected)
                        onBack()
                    }
                    .padding(vertical = 15.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    if (showLimitAlert) {
        AlertDialog(
            onDismissRequest = { showLimitAlert = false },
            title = { Text("Six stats selected") },
            text = { Text("Quick Stats shows up to six. Remove a stat before adding another.") },
            confirmButton = { TextButton(onClick = { showLimitAlert = false }) { Text("OK", color = FLPrimary) } }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = FLTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun SectionFooter(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = FLTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
private fun CustomizeRow(
    metric: ProgressMetric,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FLTheme.colors.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading()
        Icon(progressIcon(metric.icon), contentDescription = null, tint = reportThemeColor(metric.color), modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(metric.title, fontSize = 15.sp, color = FLTheme.colors.textPrimary)
            Text(
                if (metric.source == MetricSource.HEALTH) "${metric.category.title} · Health Connect" else metric.category.title,
                fontSize = 11.sp,
                color = FLTheme.colors.textSecondary
            )
        }
        trailing()
    }
}

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier.size(24.dp)
        )
    }
    Spacer(Modifier.width(0.dp))
}
