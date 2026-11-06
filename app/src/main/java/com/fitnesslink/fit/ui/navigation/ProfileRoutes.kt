package com.fitnesslink.fit.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fitnesslink.fit.ui.notifications.NotificationPreferencesScreen
import com.fitnesslink.fit.ui.profile.ConnectTVScreen
import com.fitnesslink.fit.ui.profile.PreferencesScreen
import com.fitnesslink.fit.ui.videos.MyVideosScreen

/** Route strings for the Wave 4 profile screens (`AppRoute.kt` is integrator-owned). */
object ProfileRouteNames {
    const val PREFERENCES = "preferences"
    const val NOTIFICATION_PREFERENCES = "notificationPreferences"
    const val MY_VIDEOS = "myVideos"
    const val CONNECT_TV = "connectTV"

    /** Connect TV with its code filled in, from a TV's pairing QR. */
    fun connectTV(code: String?): String = if (code.isNullOrEmpty()) CONNECT_TV else "$CONNECT_TV?code=$code"
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
    composable(
        "${ProfileRouteNames.CONNECT_TV}?code={code}",
        arguments = listOf(navArgument("code") { type = NavType.StringType; nullable = true; defaultValue = null })
    ) { entry ->
        ConnectTVScreen(
            prefilledCode = entry.arguments?.getString("code"),
            onBack = { navController.popBackStack() }
        )
    }
    composable(ProfileRouteNames.MY_VIDEOS) {
        MyVideosScreen(onBack = { navController.popBackStack() })
    }
}
