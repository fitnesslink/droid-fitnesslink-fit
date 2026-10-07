package com.fitnesslink.fit.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fitnesslink.fit.ui.progress.MeasurementEntryDetailScreen
import com.fitnesslink.fit.ui.progress.MeasurementsLogScreen
import com.fitnesslink.fit.ui.progress.PhotoCaptureScreen
import com.fitnesslink.fit.ui.progress.WeightLogScreen

/**
 * Goals / progress-tracking routes (parity items 4.1–4.3). Registers the two
 * screens `AppNavigation.kt` doesn't know yet — `measurementEntryDetail/{entryId}`
 * and `photoCapture` — plus the `weightLog` / `measurementsLog` spellings
 * from `AppRoute` (the inline registrations use `weight` / `measurements`
 * and keep working). The inline `measurements` composable only passes
 * `onBack`, so its history rows open the entry detail in place; the
 * `measurementsLog` registration here wires real navigation. Everything
 * else (`goals`, `goalCreation`, `goalDetail/{id}`, `habitDetail/{id}`,
 * `achievements`, `photos`, `photoEntryDetail/{id}`, `photoComparison`)
 * stays registered inline with its existing composable.
 */
object GoalsProgressRoutes {
    const val MEASUREMENT_ENTRY_DETAIL = "measurementEntryDetail/{entryId}"
    const val PHOTO_CAPTURE = "photoCapture"

    fun measurementEntryDetail(entryId: String) = "measurementEntryDetail/$entryId"
}

fun NavGraphBuilder.goalsProgressRoutes(navController: NavHostController) {
    composable(
        GoalsProgressRoutes.MEASUREMENT_ENTRY_DETAIL,
        arguments = listOf(navArgument("entryId") { type = NavType.StringType })
    ) { backStackEntry ->
        MeasurementEntryDetailScreen(
            entryId = backStackEntry.arguments?.getString("entryId") ?: "",
            onBack = { navController.popBackStack() }
        )
    }
    composable(GoalsProgressRoutes.PHOTO_CAPTURE) {
        PhotoCaptureScreen(
            onClose = { navController.popBackStack() },
            onSaved = { navController.popBackStack() }
        )
    }
    composable(AppRoute.WeightLog.route) {
        WeightLogScreen(onBack = { navController.popBackStack() })
    }
    composable(AppRoute.MeasurementsLog.route) {
        MeasurementsLogScreen(
            onBack = { navController.popBackStack() },
            onOpenEntry = { id -> navController.navigate(GoalsProgressRoutes.measurementEntryDetail(id)) }
        )
    }
}
