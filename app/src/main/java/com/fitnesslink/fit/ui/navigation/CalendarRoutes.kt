package com.fitnesslink.fit.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.fitnesslink.fit.ui.calendar.CalendarScreen

/**
 * Calendar-domain routes. The tab itself stays registered in
 * `AppNavigation.kt` as `AppRouter.CALENDAR_TAB`; the integrator should
 * pass `onNavigate = { navController.navigate(it) }` there so
 * `FitnessContent.tapRoute()` can reach `completedWorkoutReport/…`
 * (session routes) as well as `workoutDetail/…`.
 *
 * [CALENDAR_FULL] is a push-able copy of the tab for deep links / Home
 * "View calendar" that need the screen outside the bottom-nav graph.
 */
object CalendarRoutes {
    const val CALENDAR_FULL = "calendarFull"

    /** Route consumed by `tapRoute()` for completed entries (owner: session agent, `SessionRoutes`). */
    fun completedWorkoutReport(workoutId: String, dayKey: String) = "completedWorkoutReport/$workoutId/$dayKey"
}

fun NavGraphBuilder.calendarRoutes(navController: NavHostController) {
    composable(CalendarRoutes.CALENDAR_FULL) {
        CalendarScreen(
            onNavigateToWorkoutDetail = { navController.navigate("workoutDetail/$it") },
            onNavigateToNotifications = { navController.navigate("notifications") },
            onNavigate = { navController.navigate(it) }
        )
    }
}
