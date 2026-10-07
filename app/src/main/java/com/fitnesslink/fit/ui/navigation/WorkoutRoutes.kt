package com.fitnesslink.fit.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fitnesslink.fit.ui.workout.WorkoutEditorScreen

/**
 * Workout-domain routes added by the parity work. The pre-existing
 * `workouts`, `workoutDetail/{workoutId}`, `workoutEditor`,
 * `workoutEditor/{workoutId}` and `workoutEditorGenerate?seedPrompt=` routes
 * stay in `AppNavigation.kt`; the integrator should pass `seedPrompt` through
 * on the last one (`WorkoutEditorScreen(workoutId = null, seedPrompt = …)`).
 */
object WorkoutRoutes {
    /** New-workout editor pre-filled with one movement (Movement Detail → "Build a Workout"). */
    const val EDITOR_WITH_MOVEMENT = "workoutEditorWithMovement/{movementId}"
    fun editorWithMovement(movementId: String) = "workoutEditorWithMovement/$movementId"
}

fun NavGraphBuilder.workoutRoutes(navController: NavHostController) {
    composable(
        WorkoutRoutes.EDITOR_WITH_MOVEMENT,
        arguments = listOf(navArgument("movementId") { type = NavType.StringType })
    ) { backStackEntry ->
        WorkoutEditorScreen(
            workoutId = null,
            onBack = { navController.popBackStack() },
            initialMovementId = backStackEntry.arguments?.getString("movementId")
        )
    }
}
