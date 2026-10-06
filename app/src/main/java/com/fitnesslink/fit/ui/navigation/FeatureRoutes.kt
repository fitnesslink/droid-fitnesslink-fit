package com.fitnesslink.fit.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

/**
 * Aggregates per-domain route registrations so feature work never has to edit
 * `AppNavigation.kt`. Each domain exposes `fun NavGraphBuilder.<domain>Routes(navController)`
 * in its own `ui/navigation/<Domain>Routes.kt` and is wired in here.
 *
 * Routes registered here must not duplicate the ones already declared inline in
 * `MainTabNavigation`; when a domain replaces an existing screen, the inline
 * `composable(...)` is removed from `AppNavigation.kt` at integration time.
 */
fun NavGraphBuilder.featureRoutes(navController: NavHostController) {
    // sessionRoutes(navController)
    // workoutRoutes(navController)
    // movementRoutes(navController)
    // reportRoutes(navController)
}
