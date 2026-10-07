package com.fitnesslink.fit.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.fitnesslink.fit.ui.notifications.NotificationPreferencesScreen
import com.fitnesslink.fit.ui.profile.PreferencesScreen
import com.fitnesslink.fit.ui.videos.MyVideosScreen

/** Route strings for the Wave 4 profile screens (`AppRoute.kt` is integrator-owned). */
object ProfileRouteNames {
    const val PREFERENCES = "preferences"
    const val NOTIFICATION_PREFERENCES = "notificationPreferences"
    const val MY_VIDEOS = "myVideos"
}

/**
 * Profile / preferences / notifications / My Videos routes (parity 4.4–4.7).
 *
 * `preferences` replaces the inline `ProfileStubScreen` entry in
 * `AppNavigation.kt` — the integrator removes that `composable("preferences")`
 * when wiring this in. `billing`, `personalInfo`, `notifications`,
 * `notificationSettings`, `developerSettings` and `calendarSyncSettings` stay
 * registered inline with their existing composables.
 */
fun NavGraphBuilder.profileRoutes(navController: NavHostController) {
    composable(ProfileRouteNames.PREFERENCES) {
        PreferencesScreen(
            onBack = { navController.popBackStack() },
            onNavigate = { route -> navController.navigate(route) }
        )
    }
    composable(ProfileRouteNames.NOTIFICATION_PREFERENCES) {
        NotificationPreferencesScreen(
            onBack = { navController.popBackStack() },
            onNavigate = { route -> navController.navigate(route) }
        )
    }
    composable(ProfileRouteNames.MY_VIDEOS) {
        MyVideosScreen(onBack = { navController.popBackStack() })
    }
}
