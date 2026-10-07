package com.fitnesslink.fit.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.HomeDashboard
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

/** Where each dashboard tile deep-links (iOS `HomeDashboardView.route(for:)`, FTNSSLNK-365). */
fun dashboardRoute(dashboard: HomeDashboard): String = when (dashboard.id) {
    HomeDashboard.ID_CALORIES_TODAY -> AppRouter.NUTRITION_TAB
    HomeDashboard.ID_STREAK -> "achievements"
    else -> "workoutReport"
}

/** Tiles whose goal line / CTA is tappable. */
fun dashboardHasEditableGoal(dashboard: HomeDashboard): Boolean =
    dashboard.id == HomeDashboard.ID_WORKOUTS_WEEK || dashboard.id == HomeDashboard.ID_CALORIES_TODAY

/**
 * 2-column dashboard grid (iOS `HomeDashboardView`). Every tile links
 * somewhere; the Workouts and Calories tiles also expose a goal edit.
 */
@Composable
fun HomeDashboardView(
    dashboards: List<HomeDashboard>,
    onNavigate: (String) -> Unit,
    onGoalEdit: (HomeDashboard) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(text = "Dashboard", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        dashboards.chunked(2).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
            ) {
                rowItems.forEach { dashboard ->
                    DashboardCard(
                        dashboard = dashboard,
                        onClick = { onNavigate(dashboardRoute(dashboard)) },
                        onGoalTap = if (dashboardHasEditableGoal(dashboard)) ({ onGoalEdit(dashboard) }) else null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

/** One stat tile (iOS `DashboardView`). */
@Composable
fun DashboardCard(
    dashboard: HomeDashboard,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onGoalTap: (() -> Unit)? = null
) {
    val progressColor: Color = when (dashboard.name) {
        "Hydration", "Calories" -> BlueTheme
        "Activity", "Workout Time", "Volume" -> OrangeTheme
        "Current Weight", "Streak" -> PurpleTheme
        else -> FLPrimary
    }
    val iconRes = when (dashboard.name) {
        "Hydration", "Calories" -> R.drawable.hydration
        "Current Weight", "Streak" -> R.drawable.scale
        "Activity", "Workout Time", "Volume" -> R.drawable.runningorange
        else -> R.drawable.rungreen
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Image(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(30.dp))
        Text(text = dashboard.name, fontSize = 12.sp, color = TextSecondaryColor)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = dashboard.progress, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = progressColor)
            Text(text = dashboard.unit, fontSize = 12.sp, color = TextSecondaryColor, modifier = Modifier.padding(bottom = 4.dp))
        }
        val cta = dashboard.goalCTA
        when {
            cta != null && onGoalTap != null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.clickable(onClick = onGoalTap)
            ) {
                Text(text = cta, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = FLPrimary,
                    modifier = Modifier.size(12.dp)
                )
            }
            dashboard.goals.isNotEmpty() && onGoalTap != null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.clickable(onClick = onGoalTap)
            ) {
                Text(text = "Goal: ${dashboard.goals}", fontSize = 11.sp, color = TextSecondaryColor)
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit goal",
                    tint = TextSecondaryColor,
                    modifier = Modifier.size(10.dp)
                )
            }
            dashboard.goals.isNotEmpty() -> Text(text = "Goal: ${dashboard.goals}", fontSize = 11.sp, color = TextSecondaryColor)
        }
    }
}
