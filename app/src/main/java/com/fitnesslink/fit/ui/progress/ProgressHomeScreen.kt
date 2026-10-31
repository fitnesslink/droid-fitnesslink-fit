package com.fitnesslink.fit.ui.progress

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.health.HealthConnectService
import com.fitnesslink.fit.model.MetricResult
import com.fitnesslink.fit.model.ProgressMetric
import com.fitnesslink.fit.model.ProgressReport
import com.fitnesslink.fit.model.QuickStatsStore
import com.fitnesslink.fit.model.ReportTimeRange
import com.fitnesslink.fit.progress.ProgressInsight
import com.fitnesslink.fit.progress.ProgressMetricsEngine
import com.fitnesslink.fit.reports.SessionHistoryHydrator
import com.fitnesslink.fit.ui.components.HeaderView
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme
import kotlinx.coroutines.launch

/** Route strings for the Progress tab screens (iOS `AppRoute` additions). */
object ProgressRoutes {
    const val ALL_STATS = "allStats"
    const val ALL_REPORTS = "allReports"
    const val CUSTOMIZE_QUICK_STATS = "customizeQuickStats"
    const val CONNECTED_APPS = CONNECTED_APPS_ROUTE
    const val PROGRESS_REPORT = "progressReport/{report}"
    fun progressReport(report: ProgressReport) = report.route
}

/**
 * Progress tab landing page (iOS `ProgressHomeView`, Progress PRD §2):
 * header, time filter, insight card, Quick Stats, Progress Tools, Reports —
 * in that order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressHomeScreen(
    onNavigate: (String) -> Unit,
    onNavigateToNotifications: () -> Unit = { onNavigate("notifications") }
) {
    val viewModel: ProgressHomeViewModel = viewModel()

    // Re-reads on every appearance so a saved Customize Quick Stats or a
    // newly logged workout shows on return; a Health Connect change reloads too.
    val healthConnected by HealthConnectService.connected.collectAsState()
    LaunchedEffect(healthConnected) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FLTheme.colors.background)
    ) {
        HeaderView(onNotificationsTap = onNavigateToNotifications)

        PullToRefreshBox(
            isRefreshing = viewModel.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 14.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Progress", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
                    Text("All your progress, in one place.", fontSize = 14.sp, color = FLTheme.colors.textSecondary)
                }

                PeriodFilterBar(selection = viewModel.range, onSelect = { viewModel.select(it) })

                InsightCard(viewModel.insight, onNavigate, Modifier.padding(horizontal = 20.dp))

                QuickStatsSection(viewModel, onNavigate)

                ProgressToolsSection(onNavigate)

                ReportsSection(onNavigate)
            }
        }
    }
}

// MARK: - Insight

@Composable
private fun InsightCard(insight: ProgressInsight, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val report = insight.report
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FLTheme.colors.surface)
            .border(1.dp, FLPrimary.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .then(if (report != null) Modifier.clickable { onNavigate(report.route) } else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(FLPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(progressIcon(insight.icon), contentDescription = null, tint = FLPrimary, modifier = Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(insight.headline, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
            Text(insight.detail, fontSize = 13.sp, color = FLTheme.colors.textSecondary)
        }
        insight.isUp?.let { isUp ->
            Icon(insightTrendIcon(isUp), contentDescription = if (isUp) "Trending up" else "Trending down", tint = FLPrimary, modifier = Modifier.size(20.dp))
        }
        if (report != null) ChevronRight(16.dp)
    }
}

// MARK: - Quick Stats

@Composable
private fun QuickStatsSection(viewModel: ProgressHomeViewModel, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Quick Stats", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            LinkText("Edit") { onNavigate(ProgressRoutes.CUSTOMIZE_QUICK_STATS) }
            Spacer(Modifier.width(10.dp))
            LinkText("View All Stats", arrow = true) { onNavigate(ProgressRoutes.ALL_STATS) }
        }

        // Three across where it fits, two on the narrowest phones (PRD §5.4).
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            val columns = if (maxWidth < 330.dp) 2 else 3
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                viewModel.quickStats.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { metric ->
                            QuickStatCard(viewModel.result(metric), onNavigate, Modifier.weight(1f))
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkText(label: String, arrow: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
        if (arrow) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
        }
    }
}

// MARK: - Progress Tools (PRD §7)

@Composable
private fun ProgressToolsSection(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Progress Tools",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = FLTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolCard("Goals", "target", "goals", onNavigate, Modifier.weight(1f))
            ToolCard("Measurements", "ruler", "measurements", onNavigate, Modifier.weight(1f))
            ToolCard("Weight Log", "scalemass", "weight", onNavigate, Modifier.weight(1f))
            ToolCard("Progress Photos", "camera", "photos", onNavigate, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ToolCard(title: String, icon: String, route: String, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(FLTheme.colors.surface)
            .clickable { onNavigate(route) }
            .semantics(mergeDescendants = true) { contentDescription = title }
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(progressIcon(icon), contentDescription = null, tint = FLPrimary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.weight(1f))
            ChevronRight(12.dp)
        }
        Spacer(Modifier.heightIn(min = 8.dp))
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = FLTheme.colors.textPrimary,
            // A single long word stays on one line rather than breaking mid-word.
            maxLines = if (title.contains(' ')) 2 else 1,
            softWrap = title.contains(' '),
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}

// MARK: - Reports (PRD §8)

@Composable
private fun ReportsSection(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Reports", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            LinkText("View All Reports", arrow = true) { onNavigate(ProgressRoutes.ALL_REPORTS) }
        }
        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressReport.entries.forEach { report ->
                ReportRow(report) { onNavigate(report.route) }
            }
        }
    }
}

// MARK: - View model

class ProgressHomeViewModel(application: Application) : AndroidViewModel(application) {
    var range by mutableStateOf(ReportTimeRange.current)
        private set
    var quickStats by mutableStateOf(QuickStatsStore.load(application))
        private set
    var results by mutableStateOf<Map<ProgressMetric, MetricResult>>(emptyMap())
        private set
    var insight by mutableStateOf(ProgressInsight.empty)
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    fun result(metric: ProgressMetric): MetricResult = results[metric] ?: MetricResult.unavailable(metric)

    fun select(newRange: ReportTimeRange) {
        if (newRange == range) return
        range = newRange
        ReportTimeRange.current = newRange
        viewModelScope.launch { load() }
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing = true
            try { load() } finally { isRefreshing = false }
        }
    }

    suspend fun load() {
        quickStats = QuickStatsStore.load(getApplication())
        // Picks up a period changed inside a report since the last visit.
        if (range != ReportTimeRange.current) range = ReportTimeRange.current
        val requested = range
        val needed = quickStats.toSet() + ProgressMetricsEngine.insightMetrics
        val computed = ProgressMetricsEngine.load(needed, requested)
        // A faster tap on another period may have landed first.
        if (requested != range) return
        results = computed
        insight = ProgressMetricsEngine.insight(computed, requested)
        hydrateSessionsOnce()
    }

    /**
     * Quick Stats read local session rows, which only exist for workouts done
     * on this device until something backfills from `/sessions/me`. Pull the
     * server history once per process so a fresh install isn't stuck at 0
     * (iOS only hydrates from the Workout report).
     */
    private suspend fun hydrateSessionsOnce() {
        if (sessionsHydrated) return
        sessionsHydrated = true
        val inserted = runCatching { SessionHistoryHydrator.hydrate() }
            .onFailure { sessionsHydrated = false }
            .getOrDefault(0)
        if (inserted > 0) load()
    }

    private companion object {
        @Volatile var sessionsHydrated = false
    }
}
