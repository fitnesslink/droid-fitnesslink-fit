package com.fitnesslink.fit.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fitnesslink.fit.model.ProgressReport
import com.fitnesslink.fit.ui.profile.ConnectedAppsScreen
import com.fitnesslink.fit.ui.progress.AllReportsScreen
import com.fitnesslink.fit.ui.progress.AllStatsScreen
import com.fitnesslink.fit.ui.progress.CategoryReportScreen
import com.fitnesslink.fit.ui.progress.CustomizeQuickStatsScreen
import com.fitnesslink.fit.ui.progress.ProgressRoutes

/**
 * Progress tab sub-screens (FTNSSLNK-553, iOS `AppRoute` additions):
 * `allStats`, `allReports`, `customizeQuickStats`, `progressReport/{report}`
 * and `connectedApps`. The tab root itself (`progress`) is registered inline
 * in `MainTabNavigation`.
 */
fun NavGraphBuilder.progressTabRoutes(navController: NavHostController) {
    val onNavigate: (String) -> Unit = { route -> navController.navigate(route) }
    composable(ProgressRoutes.ALL_STATS) {
        AllStatsScreen(onBack = { navController.popBackStack() }, onNavigate = onNavigate)
    }
    composable(ProgressRoutes.ALL_REPORTS) {
        AllReportsScreen(onBack = { navController.popBackStack() }, onNavigate = onNavigate)
    }
    composable(ProgressRoutes.CUSTOMIZE_QUICK_STATS) {
        CustomizeQuickStatsScreen(onBack = { navController.popBackStack() })
    }
    composable(
        ProgressRoutes.PROGRESS_REPORT,
        arguments = listOf(navArgument("report") { type = NavType.StringType })
    ) { backStackEntry ->
        val report = ProgressReport.fromRaw(backStackEntry.arguments?.getString("report")) ?: ProgressReport.ACTIVITY
        CategoryReportScreen(report = report, onBack = { navController.popBackStack() }, onNavigate = onNavigate)
    }
    composable(ProgressRoutes.CONNECTED_APPS) {
        ConnectedAppsScreen(onBack = { navController.popBackStack() })
    }
}
