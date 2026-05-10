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
    data object NutritionReport : AppRoute("nutritionReport")
    data object Preferences : AppRoute("preferences")
    data object Billing : AppRoute("billing")
    data object Goals : AppRoute("goals")
    data object Measurements : AppRoute("measurements")
    data object Photos : AppRoute("photos")
    data object Weight : AppRoute("weight")
    data object WorkoutReport : AppRoute("workoutReport")
    data class ReportDetail(val metric: String) : AppRoute("reportDetail/{metric}") {
        companion object {
            const val ROUTE = "reportDetail/{metric}"
            fun createRoute(metric: String) = "reportDetail/$metric"
        }
    }
    data class SessionDetail(val sessionId: String) : AppRoute("sessionDetail/{sessionId}") {
        companion object {
            const val ROUTE = "sessionDetail/{sessionId}"
            fun createRoute(sessionId: String) = "sessionDetail/$sessionId"
        }
    }
    data class WorkoutAggregateDetail(val workoutName: String) : AppRoute("workoutAggregate/{workoutName}") {
        companion object {
            const val ROUTE = "workoutAggregate/{workoutName}"
            fun createRoute(workoutName: String) = "workoutAggregate/$workoutName"
        }
    }
    data class MovementAggregateDetail(val exerciseName: String) : AppRoute("movementAggregate/{exerciseName}") {
        companion object {
            const val ROUTE = "movementAggregate/{exerciseName}"
            fun createRoute(exerciseName: String) = "movementAggregate/$exerciseName"
        }
    }
    data class PhotoEntryDetail(val entryId: String) : AppRoute("photoEntryDetail/{entryId}") {
        companion object {
            const val ROUTE = "photoEntryDetail/{entryId}"
            fun createRoute(entryId: String) = "photoEntryDetail/$entryId"
        }
    }
    data object PhotoComparison : AppRoute("photoComparison")
    data object WeightLog : AppRoute("weightLog")
    data object MeasurementsLog : AppRoute("measurementsLog")
    data object Notifications : AppRoute("notifications")
    data object DeveloperSettings : AppRoute("developerSettings")
    data object LogWater : AppRoute("logWater")
    data object Achievements : AppRoute("achievements")
    data object ProgramEditorNew : AppRoute("programEditor")
    data class ProgramEditor(val programId: String) : AppRoute("programEditor/{programId}") {
        companion object {
            const val ROUTE = "programEditor/{programId}"
            fun createRoute(programId: String) = "programEditor/$programId"
        }
    }
    data object WorkoutEditorNew : AppRoute("workoutEditor")
    data class WorkoutEditor(val workoutId: String) : AppRoute("workoutEditor/{workoutId}") {
        companion object {
            const val ROUTE = "workoutEditor/{workoutId}"
            fun createRoute(workoutId: String) = "workoutEditor/$workoutId"
        }
    }
}
