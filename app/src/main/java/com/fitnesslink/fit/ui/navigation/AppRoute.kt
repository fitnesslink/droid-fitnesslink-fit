package com.fitnesslink.fit.ui.navigation

import android.net.Uri

sealed class AppRoute(val route: String) {
    // Bottom-tab roots (see AppRouter.TAB_ROUTES)
    data object Home : AppRoute("home")
    data object Catalog : AppRoute("catalog")
    data object Calendar : AppRoute("calendar")
    data object Nutrition : AppRoute("nutrition")
    data object Profile : AppRoute("profile")

    data object Login : AppRoute("login")
    data object SignUp : AppRoute("signUp")
    data object Personalization : AppRoute("personalization")
    data object Programs : AppRoute("programs")
    data class ProgramDetail(val programId: String) : AppRoute("programDetail/{programId}") {
        companion object {
            const val ROUTE = "programDetail/{programId}"
            fun createRoute(programId: String) = "programDetail/$programId"
        }
    }
    data object Workouts : AppRoute("workouts")
    data class WorkoutDetail(val workoutId: String) : AppRoute("workoutDetail/{workoutId}") {
        companion object {
            const val ROUTE = "workoutDetail/{workoutId}"
            fun createRoute(workoutId: String) = "workoutDetail/$workoutId"
        }
    }
    /**
     * Movement library + detail. Not built on Android yet (Wave 1.9); the
     * routes exist so deep links and the router have a stable target name.
     */
    data object Movements : AppRoute("movements")
    data class MovementDetail(val movementId: String) : AppRoute("movementDetail/{movementId}") {
        companion object {
            const val ROUTE = "movementDetail/{movementId}"
            fun createRoute(movementId: String) = "movementDetail/$movementId"
        }
    }
    data object MealPlan : AppRoute("mealPlan")
    data object GroceryList : AppRoute("groceryList")
    data class PlaylistSession(val workoutId: String) : AppRoute("playlistSession/{workoutId}") {
        companion object {
            const val ROUTE = "playlistSession/{workoutId}"
            fun createRoute(workoutId: String) = "playlistSession/$workoutId"
        }
    }
    data class InteractiveSession(val workoutId: String) : AppRoute("interactiveSession/{workoutId}") {
        companion object {
            const val ROUTE = "interactiveSession/{workoutId}"
            fun createRoute(workoutId: String) = "interactiveSession/$workoutId"
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
    data object NotificationSettings : AppRoute("notificationSettings")
    data object CalendarSyncSettings : AppRoute("calendarSyncSettings")
    data object GoalCreation : AppRoute("goalCreation")
    data class GoalDetail(val goalId: String) : AppRoute("goalDetail/{goalId}") {
        companion object {
            const val ROUTE = "goalDetail/{goalId}"
            fun createRoute(goalId: String) = "goalDetail/$goalId"
        }
    }
    data class HabitDetail(val habitId: String) : AppRoute("habitDetail/{habitId}") {
        companion object {
            const val ROUTE = "habitDetail/{habitId}"
            fun createRoute(habitId: String) = "habitDetail/$habitId"
        }
    }
    data object ProgramEditorNew : AppRoute("programEditor")
    data class ProgramEditor(val programId: String) : AppRoute("programEditor/{programId}") {
        companion object {
            const val ROUTE = "programEditor/{programId}"
            fun createRoute(programId: String) = "programEditor/$programId"
        }
    }
    data object WorkoutEditorNew : AppRoute("workoutEditor")
    /**
     * New-workout editor that should immediately open the AI generation
     * sheet with the prompt pre-seeded (FTNSSLNK-357: Get Started's "Build
     * with AI", seeded from the questionnaire answers). The seed rides as an
     * optional query arg; the editor consumes it once Wave 1.8 lands.
     */
    data class WorkoutEditorGenerate(val seedPrompt: String) : AppRoute("workoutEditorGenerate?seedPrompt={seedPrompt}") {
        companion object {
            const val ROUTE = "workoutEditorGenerate?seedPrompt={seedPrompt}"
            const val ARG_SEED_PROMPT = "seedPrompt"
            fun createRoute(seedPrompt: String) = "workoutEditorGenerate?seedPrompt=${Uri.encode(seedPrompt)}"
        }
    }
    data class WorkoutEditor(val workoutId: String) : AppRoute("workoutEditor/{workoutId}") {
        companion object {
            const val ROUTE = "workoutEditor/{workoutId}"
            fun createRoute(workoutId: String) = "workoutEditor/$workoutId"
        }
    }
}
