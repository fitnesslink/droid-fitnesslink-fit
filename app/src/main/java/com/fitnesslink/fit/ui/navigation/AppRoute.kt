package com.fitnesslink.fit.ui.navigation

sealed class AppRoute(val route: String) {
    data object Login : AppRoute("login")
    data object SignUp : AppRoute("signUp")
    data object Personalization : AppRoute("personalization")
    data object Programs : AppRoute("programs")
    data class ProgramDetail(val programId: String) : AppRoute("programDetail/{programId}") {
        companion object {
            const val ROUTE = "programDetail/{programId}"
        }
    }
    data object Workouts : AppRoute("workouts")
    data class WorkoutDetail(val workoutId: String) : AppRoute("workoutDetail/{workoutId}") {
        companion object {
            const val ROUTE = "workoutDetail/{workoutId}"
        }
    }
    data class PlaylistSession(val workoutId: String) : AppRoute("playlistSession/{workoutId}") {
        companion object {
            const val ROUTE = "playlistSession/{workoutId}"
        }
    }
    data class InteractiveSession(val workoutId: String) : AppRoute("interactiveSession/{workoutId}") {
        companion object {
            const val ROUTE = "interactiveSession/{workoutId}"
        }
    }
    data object PersonalInfo : AppRoute("personalInfo")
    data object PersonalizationProfile : AppRoute("personalizationProfile")
    data object AnalyticsProgress : AppRoute("analyticsProgress")
    data object Preferences : AppRoute("preferences")
    data object Billing : AppRoute("billing")
    data object Goals : AppRoute("goals")
    data object Measurements : AppRoute("measurements")
    data object Photos : AppRoute("photos")
    data object Weight : AppRoute("weight")
    data object WorkoutReport : AppRoute("workoutReport")
}
