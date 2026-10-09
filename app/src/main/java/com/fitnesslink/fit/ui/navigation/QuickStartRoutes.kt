package com.fitnesslink.fit.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fitnesslink.fit.ui.quickstart.QuickStartCanvasScreen
import com.fitnesslink.fit.ui.quickstart.QuickStartFocusScreen

/**
 * Quick Start routes (Quick Start PRD §5–6): the focus screen and the live
 * canvas (also the Resume target for a Quick Start session).
 */
object QuickStartRoutes {
    const val QUICK_START = "quickStart"
    const val CANVAS = "quickStartCanvas/{workoutId}"
    fun canvas(workoutId: String) = "quickStartCanvas/$workoutId"
}

fun NavGraphBuilder.quickStartRoutes(navController: NavHostController) {
    composable(QuickStartRoutes.QUICK_START) {
        QuickStartFocusScreen(
            onBack = { navController.popBackStack() },
            onStarted = { id ->
                // Swap the focus screen for the canvas so Back from the canvas returns to the launcher.
                navController.popBackStack()
                navController.navigate(QuickStartRoutes.canvas(id)) { launchSingleTop = true }
            }
        )
    }
    composable(QuickStartRoutes.CANVAS, arguments = listOf(navArgument("workoutId") { type = NavType.StringType })) { entry ->
        QuickStartCanvasScreen(
            workoutId = entry.arguments?.getString("workoutId") ?: "",
            onBack = { navController.popBackStack() },
            onOpenMovement = { id -> navController.navigate(MovementRoutes.movementDetail(id)) }
        )
    }
}
