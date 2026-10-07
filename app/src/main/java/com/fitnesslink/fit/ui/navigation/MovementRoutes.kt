package com.fitnesslink.fit.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fitnesslink.fit.ui.movements.MovementDetailScreen
import com.fitnesslink.fit.ui.movements.MovementEditorScreen
import com.fitnesslink.fit.ui.movements.MovementsScreen

/**
 * Movement routes (CONTRACTS.md, Movements block): `movements`,
 * `movementDetail/{movementId}`, `movementEditor`. Wired into
 * `FeatureRoutes.featureRoutes` by the integrator.
 *
 * "Build a Workout" on the detail screen navigates to
 * [WORKOUT_EDITOR_WITH_MOVEMENT_ROUTE]; the workouts agent's
 * `WorkoutEditorScreen(initialMovementId = …)` is the intended target and the
 * integrator registers that route (it is not declared here to avoid a
 * duplicate `composable` once the workouts routes land).
 */
object MovementRoutes {
    const val MOVEMENTS = "movements"
    const val MOVEMENT_DETAIL = "movementDetail/{movementId}"
    const val MOVEMENT_EDITOR = "movementEditor"
    const val ARG_MOVEMENT_ID = "movementId"

    /** Route the editor is expected to expose for a pre-seeded first exercise (iOS `workoutEditorWithMovement`). */
    const val WORKOUT_EDITOR_WITH_MOVEMENT_ROUTE = "workoutEditorWithMovement/{movementId}"

    fun movementDetail(movementId: String) = "movementDetail/$movementId"
    fun workoutEditorWithMovement(movementId: String) = "workoutEditorWithMovement/$movementId"
}

fun NavGraphBuilder.movementRoutes(navController: NavHostController) {
    composable(MovementRoutes.MOVEMENTS) {
        MovementsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToMovementDetail = { id -> navController.navigate(MovementRoutes.movementDetail(id)) },
            onNavigateToEditor = { navController.navigate(MovementRoutes.MOVEMENT_EDITOR) }
        )
    }
    composable(
        MovementRoutes.MOVEMENT_DETAIL,
        arguments = listOf(navArgument(MovementRoutes.ARG_MOVEMENT_ID) { type = NavType.StringType })
    ) { backStackEntry ->
        val movementId = backStackEntry.arguments?.getString(MovementRoutes.ARG_MOVEMENT_ID).orEmpty()
        MovementDetailScreen(
            movementId = movementId,
            onBack = { navController.popBackStack() },
            onBuildWorkout = { id ->
                // Falls back to the plain editor if the seeded route isn't registered yet.
                runCatching { navController.navigate(MovementRoutes.workoutEditorWithMovement(id)) }
                    .onFailure { navController.navigate("workoutEditor") }
            }
        )
    }
    composable(MovementRoutes.MOVEMENT_EDITOR) {
        MovementEditorScreen(onBack = { navController.popBackStack() })
    }
}
