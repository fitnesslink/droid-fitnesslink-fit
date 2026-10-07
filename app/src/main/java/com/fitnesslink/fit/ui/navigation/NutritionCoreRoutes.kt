package com.fitnesslink.fit.ui.navigation

import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.fitnesslink.fit.ui.nutrition.NutritionAnalyticsScreen
import com.fitnesslink.fit.ui.nutrition.NutritionDisplaySettingsScreen
import com.fitnesslink.fit.ui.nutrition.NutritionOnboardingScreen
import com.fitnesslink.fit.ui.nutrition.NutritionProfileEditScreen

/**
 * Nutrition-core routes (CONTRACTS.md, Wave 3 block A). Registers only the
 * NEW destinations — `nutritionOnboarding`, `nutritionProfileEdit`,
 * `nutritionAnalytics`, `nutritionDisplaySettings`; the existing strings
 * (`calorieTracking`, `nutritionGoalSettings`, `weeklyProgress`,
 * `nutritionReport`, `nutritionMetricDetail/{metric}`,
 * `mealTypeDetail/{mealType}`, `foodItemDetail/{foodName}`) stay registered
 * inline in `AppNavigation.kt`, where the integrator should:
 *
 *  - pass `onNavigate = { navController.navigate(it) }` to `NutritionScreen`,
 *    `NutritionHomeScreen` and `NutritionGoalSettingsScreen` (they default to
 *    `AppRouter.push`, so they work either way);
 *  - swap the `ProfileStubScreen` entries for `MealTypeDetailScreen(mealType, onBack)`
 *    and `FoodItemDetailScreen(foodName, onBack)` (`ui/profile`).
 */
object NutritionCoreRoutes {
    const val ONBOARDING = "nutritionOnboarding"
    const val PROFILE_EDIT = "nutritionProfileEdit"
    const val ANALYTICS = "nutritionAnalytics"
    const val DISPLAY_SETTINGS = "nutritionDisplaySettings"

    const val CALORIE_TRACKING = "calorieTracking"
    const val GOAL_SETTINGS = "nutritionGoalSettings"
    const val WEEKLY_PROGRESS = "weeklyProgress"
    const val NUTRITION_REPORT = "nutritionReport"

    fun nutritionMetricDetail(metric: String) = "nutritionMetricDetail/$metric"
    fun mealTypeDetail(mealType: String) = "mealTypeDetail/${Uri.encode(mealType)}"
    fun foodItemDetail(foodName: String) = "foodItemDetail/${Uri.encode(foodName)}"
}

fun NavGraphBuilder.nutritionCoreRoutes(navController: NavHostController) {
    composable(NutritionCoreRoutes.ONBOARDING) {
        NutritionOnboardingScreen(
            onBack = { navController.popBackStack() },
            onComplete = { navController.popBackStack() }
        )
    }
    composable(NutritionCoreRoutes.PROFILE_EDIT) {
        NutritionProfileEditScreen(onBack = { navController.popBackStack() })
    }
    composable(NutritionCoreRoutes.ANALYTICS) {
        NutritionAnalyticsScreen(onBack = { navController.popBackStack() })
    }
    composable(NutritionCoreRoutes.DISPLAY_SETTINGS) {
        NutritionDisplaySettingsScreen(onBack = { navController.popBackStack() })
    }
}
