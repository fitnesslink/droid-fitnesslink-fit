package com.fitnesslink.fit.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fitnesslink.fit.ui.session.CompletedWorkoutReportView
import com.fitnesslink.fit.ui.session.WorkoutSummaryReopenView

/** Session-domain routes (CONTRACTS): the completed-workout report and the reopened summary. */
object SessionRoutes {
    const val COMPLETED_WORKOUT_REPORT = "completedWorkoutReport/{workoutId}/{day}"
    const val WORKOUT_SUMMARY = "workoutSummary/{sessionId}"

    /** [day] is `yyyy-MM-dd` (local). */
    fun completedWorkoutReport(workoutId: String, day: String) = "completedWorkoutReport/$workoutId/$day"
    fun workoutSummary(sessionId: String) = "workoutSummary/$sessionId"
}

fun NavGraphBuilder.sessionRoutes(navController: NavHostController) {
    composable(
        SessionRoutes.COMPLETED_WORKOUT_REPORT,
        arguments = listOf(navArgument("workoutId") { type = NavType.StringType }, navArgument("day") { type = NavType.StringType })
    ) { entry ->
        val workoutId = entry.arguments?.getString("workoutId") ?: ""
        val day = entry.arguments?.getString("day") ?: ""
        CompletedWorkoutReportView(
            workoutId = workoutId, day = day,
            onBack = { navController.popBackStack() },
            onOpenWorkout = { id ->
                navController.popBackStack()
                navController.navigate("workoutDetail/$id") { launchSingleTop = true }
            }
        )
    }
    composable(SessionRoutes.WORKOUT_SUMMARY, arguments = listOf(navArgument("sessionId") { type = NavType.StringType })) { entry ->
        WorkoutSummaryReopenView(sessionId = entry.arguments?.getString("sessionId") ?: "", onBack = { navController.popBackStack() })
    }
}
