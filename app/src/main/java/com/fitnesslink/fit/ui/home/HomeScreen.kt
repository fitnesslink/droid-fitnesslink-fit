package com.fitnesslink.fit.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.HomeDashboard
import com.fitnesslink.fit.model.HorizontalCalendar
import com.fitnesslink.fit.ui.components.HeaderView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.HighlightState
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.HomeViewModel

@Composable
fun HomeScreen(onNavigateToNotifications: () -> Unit = {}) {
    val viewModel: HomeViewModel = viewModel()

    LaunchedEffect(Unit) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderView(onNotificationsTap = onNavigateToNotifications)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            DateScrollView(
                calendarItems = viewModel.calendarItems,
                scrollTo = viewModel.scrollToDay,
                onSelectDay = viewModel::selectDay
            )
            Spacer(modifier = Modifier.height(16.dp))
            TodayWorkoutView(dateLabel = viewModel.selectedDateLabel)
            Spacer(modifier = Modifier.height(16.dp))
            HomeDashboardView(dashboards = viewModel.dashboards)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DateScrollView(
    calendarItems: List<HorizontalCalendar>,
    scrollTo: Int,
    onSelectDay: (Int) -> Unit = {}
) {
    val listState = rememberLazyListState()

    // Center on `scrollTo` when the strip is first populated. Tapping a
    // different day later updates `selected` but should not scroll the
    // strip out from under the user, so we don't re-key on selection.
    LaunchedEffect(scrollTo, calendarItems.isNotEmpty()) {
        if (scrollTo > 0 && calendarItems.isNotEmpty()) {
            listState.animateScrollToItem(maxOf(0, scrollTo - 1))
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(White)
            .padding(vertical = 10.dp)
    ) {
        items(calendarItems, key = { it.dayNumber }) { item ->
            DateCellView(
                item = item,
                onClick = { onSelectDay(item.dayNumber) }
            )
        }
    }
}

@Composable
fun DateCellView(
    item: HorizontalCalendar,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(44.dp)
            .height(72.dp)
            .background(
                if (item.selected) HighlightState else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = item.name,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = item.weekDay,
            fontSize = 11.sp,
            color = TextSecondaryColor
        )
        Spacer(modifier = Modifier.height(7.dp))
        StatusDot(status = item.status)
    }
}

@Composable
private fun StatusDot(status: String) {
    when (status) {
        "completed" -> Image(
            painter = painterResource(R.drawable.completedstate),
            contentDescription = "Completed",
            modifier = Modifier.size(10.dp)
        )
        "scheduled" -> Image(
            painter = painterResource(R.drawable.scheduledstate),
            contentDescription = "Scheduled",
            modifier = Modifier.size(10.dp)
        )
        else -> Spacer(modifier = Modifier.size(10.dp))
    }
}

@Composable
fun TodayWorkoutView(dateLabel: String = "Today") {
    val title = if (dateLabel == "Today") "Today's Workout" else "$dateLabel · Workout"
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        RestView()
    }
}

@Composable
fun RestView() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .padding(vertical = 24.dp, horizontal = 20.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.rest),
            contentDescription = "Rest",
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Rest Day",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Rest days are essential for recovery and muscle growth. Use this time to stretch, hydrate, and prepare for your next workout.",
            fontSize = 14.sp,
            color = TextSecondaryColor,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun HomeDashboardView(dashboards: List<HomeDashboard>) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "Dashboard",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))

        val rows = dashboards.chunked(2)
        rows.forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowItems.forEach { dashboard ->
                    DashboardCard(
                        dashboard = dashboard,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun DashboardCard(
    dashboard: HomeDashboard,
    modifier: Modifier = Modifier
) {
    val progressColor = when (dashboard.name) {
        "Hydration" -> BlueTheme
        "Activity", "Workout Time" -> OrangeTheme
        "Current Weight" -> PurpleTheme
        else -> FLPrimary
    }

    val iconRes = when (dashboard.name) {
        "Hydration" -> R.drawable.hydration
        "Current Weight" -> R.drawable.scale
        "Activity", "Workout Time" -> R.drawable.runningicon
        else -> R.drawable.dumbbell
    }

    Column(
        modifier = modifier
            .background(White, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = dashboard.name,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = dashboard.name,
            fontSize = 12.sp,
            color = TextSecondaryColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = dashboard.progress,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = progressColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = dashboard.unit,
                fontSize = 12.sp,
                color = TextSecondaryColor,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Text(
            text = "Goal: ${dashboard.goals}",
            fontSize = 11.sp,
            color = TextSecondaryColor
        )
    }
}
