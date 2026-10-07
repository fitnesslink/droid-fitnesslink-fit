package com.fitnesslink.fit.ui.navigation

import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.ui.nutrition.GroceryListScreen
import com.fitnesslink.fit.ui.nutrition.GroceryListsScreen
import com.fitnesslink.fit.ui.nutrition.GroceryMealBuilderScreen
import com.fitnesslink.fit.ui.nutrition.LogWaterScreen
import com.fitnesslink.fit.ui.nutrition.MealBuilderScreen
import com.fitnesslink.fit.ui.nutrition.MealPlanIdeasReviewScreen
import com.fitnesslink.fit.ui.nutrition.MealPlanIdeasScreen
import com.fitnesslink.fit.ui.nutrition.MealPlanScreen
import com.fitnesslink.fit.ui.nutrition.MealSlotDetailScreen
import com.fitnesslink.fit.ui.nutrition.NutritionSummaryScreen
import com.fitnesslink.fit.viewmodel.MealPlanIdeasViewModel
import java.util.Date

/**
 * Meal plan / grocery / hydration routes (Wave 3, agent C).
 *
 * Integration note: `AppNavigation.kt` still declares `mealPlan`,
 * `mealSlotDetail/{day}/{mealType}`, `nutritionSummary`, `groceryList` and
 * `logWater` inline. Those five are re-registered here with the same
 * strings (plus optional query params), so when [mealPlanRoutes] is wired
 * into `FeatureRoutes.kt` the inline copies must be removed, and the inline
 * `MealPlanScreen(...)` call should pass `onNavigate = { navController.navigate(it) }`
 * so the planner can reach the new routes.
 */
object MealPlanRoutes {
    const val MEAL_PLAN = "mealPlan"

    /** Legacy: `day` is a `yyyy-MM-dd` date (or a pre-Wave-3 weekday name), `mealType` a MealType name / raw value. */
    const val LEGACY_MEAL_SLOT_DETAIL = "mealSlotDetail/{day}/{mealType}"

    /** The meal builder for one planner slot (edit when `slotId` is set, else a new entry). */
    const val MEAL_BUILDER = "mealBuilder/{date}/{mealType}?slotId={slotId}&seeded={seeded}&allowLog={allowLog}"

    fun mealBuilder(date: String, mealType: MealType, slotId: String? = null, seeded: Boolean = false, allowLog: Boolean = false): String =
        "mealBuilder/${MealPlanDay.normalize(date)}/${mealType.name}?slotId=${slotId ?: ""}&seeded=$seeded&allowLog=$allowLog"

    const val NUTRITION_SUMMARY = "nutritionSummary?date={date}"
    fun nutritionSummary(date: String? = null): String = if (date == null) "nutritionSummary" else "nutritionSummary?date=${MealPlanDay.normalize(date)}"

    /** Default-list detail (redirects to [GROCERY_LISTS] when several active lists exist). */
    const val GROCERY_LIST = "groceryList"
    const val GROCERY_LISTS = "groceryLists"
    const val GROCERY_LIST_DETAIL = "groceryList/{listId}"
    fun groceryListDetail(listId: String): String = "groceryList/$listId"

    const val GROCERY_MEAL_BUILDER = "groceryMealBuilder/{listId}"
    fun groceryMealBuilder(listId: String): String = "groceryMealBuilder/$listId"

    const val LOG_WATER = "logWater?date={date}"
    fun logWater(date: String? = null): String = if (date == null) "logWater" else "logWater?date=${MealPlanDay.normalize(date)}"

    const val MEAL_PLAN_IDEAS = "mealPlanIdeas?weekStart={weekStart}"
    fun mealPlanIdeas(weekStart: String): String = "mealPlanIdeas?weekStart=${MealPlanDay.normalize(weekStart)}"

    const val MEAL_PLAN_IDEAS_REVIEW = "mealPlanIdeasReview"

    private fun optionalString(name: String) = navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }
}

fun NavGraphBuilder.mealPlanRoutes(navController: NavHostController) {
    val navigate: (String) -> Unit = { navController.navigate(it) }

    composable(MealPlanRoutes.MEAL_PLAN) {
        MealPlanScreen(
            onBack = { navController.popBackStack() },
            onNavigateToMealSlotDetail = { date, mealType -> navController.navigate("mealSlotDetail/$date/$mealType") },
            onNavigateToNutritionSummary = { navController.navigate(MealPlanRoutes.nutritionSummary()) },
            onNavigateToGroceryList = { navController.navigate(MealPlanRoutes.GROCERY_LISTS) },
            onNavigate = navigate
        )
    }

    composable(
        MealPlanRoutes.LEGACY_MEAL_SLOT_DETAIL,
        arguments = listOf(navArgument("day") { type = NavType.StringType }, navArgument("mealType") { type = NavType.StringType })
    ) { entry ->
        MealSlotDetailScreen(
            dayName = entry.arguments?.getString("day") ?: MealPlanDay.string(Date()),
            mealTypeName = entry.arguments?.getString("mealType") ?: MealType.BREAKFAST.name,
            onBack = { navController.popBackStack() },
            onNavigate = navigate
        )
    }

    composable(
        MealPlanRoutes.MEAL_BUILDER,
        arguments = listOf(
            navArgument("date") { type = NavType.StringType },
            navArgument("mealType") { type = NavType.StringType },
            navArgument("slotId") { type = NavType.StringType; defaultValue = "" },
            navArgument("seeded") { type = NavType.BoolType; defaultValue = false },
            navArgument("allowLog") { type = NavType.BoolType; defaultValue = false }
        )
    ) { entry ->
        val args = entry.arguments
        MealBuilderScreen(
            date = args?.getString("date") ?: MealPlanDay.string(Date()),
            mealType = MealType.fromWire(args?.getString("mealType")) ?: MealType.BREAKFAST,
            slotId = args?.getString("slotId")?.ifEmpty { null },
            seeded = args?.getBoolean("seeded") ?: false,
            allowLogDirectly = args?.getBoolean("allowLog") ?: false,
            onBack = { navController.popBackStack() },
            onNavigate = navigate
        )
    }

    composable(
        MealPlanRoutes.NUTRITION_SUMMARY,
        arguments = listOf(navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null })
    ) { entry ->
        NutritionSummaryScreen(onBack = { navController.popBackStack() }, date = entry.arguments?.getString("date"))
    }

    composable(MealPlanRoutes.GROCERY_LIST) {
        GroceryListScreen(onBack = { navController.popBackStack() }, listId = null, onNavigate = navigate)
    }

    composable(MealPlanRoutes.GROCERY_LISTS) {
        GroceryListsScreen(
            onBack = { navController.popBackStack() },
            onOpenList = { navController.navigate(MealPlanRoutes.groceryListDetail(it.id)) }
        )
    }

    composable(MealPlanRoutes.GROCERY_LIST_DETAIL, arguments = listOf(navArgument("listId") { type = NavType.StringType })) { entry ->
        GroceryListScreen(onBack = { navController.popBackStack() }, listId = entry.arguments?.getString("listId"), onNavigate = navigate)
    }

    composable(MealPlanRoutes.GROCERY_MEAL_BUILDER, arguments = listOf(navArgument("listId") { type = NavType.StringType })) { entry ->
        GroceryMealBuilderScreen(
            listId = entry.arguments?.getString("listId") ?: "",
            onBack = { navController.popBackStack() },
            onNavigate = navigate
        )
    }

    composable(
        MealPlanRoutes.LOG_WATER,
        arguments = listOf(navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null })
    ) { entry ->
        LogWaterScreen(onBack = { navController.popBackStack() }, date = entry.arguments?.getString("date"))
    }

    composable(
        MealPlanRoutes.MEAL_PLAN_IDEAS,
        arguments = listOf(navArgument("weekStart") { type = NavType.StringType; nullable = true; defaultValue = null })
    ) { entry ->
        val ideasVm: MealPlanIdeasViewModel = viewModel(entry)
        MealPlanIdeasScreen(
            weekStart = entry.arguments?.getString("weekStart") ?: MealPlanDay.string(Date()),
            viewModel = ideasVm,
            onBack = { navController.popBackStack() },
            onReview = { navController.navigate(MealPlanRoutes.MEAL_PLAN_IDEAS_REVIEW) }
        )
    }

    composable(MealPlanRoutes.MEAL_PLAN_IDEAS_REVIEW) { entry ->
        // Share the request screen's view model so the draft survives the push.
        val parent = remember(entry) { runCatching { navController.getBackStackEntry(MealPlanRoutes.MEAL_PLAN_IDEAS) }.getOrNull() }
        val ideasVm: MealPlanIdeasViewModel = if (parent != null) viewModel(parent) else viewModel(entry)
        MealPlanIdeasReviewScreen(
            viewModel = ideasVm,
            onBack = { navController.popBackStack() },
            onApplied = {
                // Back to the planner: pop the ideas flow off the stack.
                navController.popBackStack(MealPlanRoutes.MEAL_PLAN_IDEAS, inclusive = true)
            }
        )
    }
}
