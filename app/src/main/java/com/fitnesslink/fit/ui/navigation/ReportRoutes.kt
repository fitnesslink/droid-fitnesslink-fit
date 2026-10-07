package com.fitnesslink.fit.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fitnesslink.fit.ui.profile.ReportDetailScreen
import com.fitnesslink.fit.ui.profile.WorkoutAggregateDetailScreen

/**
 * Report / analytics routes (parity item 1.11). Registers the two screens
 * `AppNavigation.kt` currently stubs — `reportDetail/{metric}` and
 * `workoutAggregate/{workoutName}` — so the integrator can drop the inline
 * `ProfileStubScreen` entries. `workoutReport`, `sessionDetail/{sessionId}`
 * and `movementAggregate/{exerciseName}` stay registered inline with their
 * existing composables.
 *
 * Path arguments arrive URL-decoded (the report screen encodes workout
 * names on navigation).
 */
fun NavGraphBuilder.reportRoutes(navController: NavHostController) {
    composable(
        AppRoute.ReportDetail.ROUTE,
        arguments = listOf(navArgument("metric") { type = NavType.StringType })
    ) { backStackEntry ->
        ReportDetailScreen(
            metric = backStackEntry.arguments?.getString("metric") ?: "",
            onBack = { navController.popBackStack() },
            onNavigateToSession = { sessionId -> navController.navigate(AppRoute.SessionDetail.createRoute(sessionId)) }
        )
    }
    composable(
        AppRoute.WorkoutAggregateDetail.ROUTE,
        arguments = listOf(navArgument("workoutName") { type = NavType.StringType })
    ) { backStackEntry ->
        WorkoutAggregateDetailScreen(
            workoutName = backStackEntry.arguments?.getString("workoutName") ?: "",
            onBack = { navController.popBackStack() },
            onNavigateToSession = { sessionId -> navController.navigate(AppRoute.SessionDetail.createRoute(sessionId)) }
        )
    }
}
