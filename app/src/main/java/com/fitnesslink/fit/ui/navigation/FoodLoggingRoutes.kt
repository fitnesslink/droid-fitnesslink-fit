package com.fitnesslink.fit.ui.navigation

import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.ui.nutrition.BarcodeScannerScreen
import com.fitnesslink.fit.ui.nutrition.CustomFoodFormScreen
import com.fitnesslink.fit.ui.nutrition.DescribeMealScreen
import com.fitnesslink.fit.ui.nutrition.FoodDetailMode
import com.fitnesslink.fit.ui.nutrition.FoodDetailScreen
import com.fitnesslink.fit.ui.nutrition.FoodLogFlow
import com.fitnesslink.fit.ui.nutrition.FoodSearchScreen
import com.fitnesslink.fit.ui.nutrition.MealTemplatesPickerScreen
import com.fitnesslink.fit.ui.nutrition.QuickAddScreen
import com.fitnesslink.fit.ui.nutrition.RecentFoodsScreen
import com.fitnesslink.fit.ui.nutrition.RecentMealLogScreen
import com.fitnesslink.fit.ui.nutrition.RecentMealsScreen
import com.fitnesslink.fit.ui.nutrition.SnapMealScreen
import com.fitnesslink.fit.ui.nutrition.parseLogDate
import com.fitnesslink.fit.ui.nutrition.parseMealTypeArg
import java.util.Date

/**
 * Food-logging routes (CONTRACTS Wave 3 / B). `mealType` path segments carry
 * the enum NAME (`BREAKFAST`), `date` is `yyyy-MM-dd` (today = log with the
 * real clock time; a past day = backfill at local noon).
 *
 * `mode` on pickers / detail: `log` (default) | `addToMeal` (stage into agent
 * C's `MealBuilderStore`) | `capture` (push onto `FoodCaptureBus` for the
 * recent-meal editor) | `edit:{entryId}` (detail only). Detail `source`:
 * `template:{entryId}` (re-log a local row) | `handoff:{token}` (parked entry).
 *
 * The legacy `quickAdd/{mealType}`, `recentFoods`, `customFoodForm/{entryId}`,
 * `barcodeScanner/{mealType}` and `foodEntryDetail/{entryId}` composables in
 * `AppNavigation.kt` keep working; the routes below are the full-featured
 * replacements the integrator can point the dashboard at.
 */
object FoodLoggingRoutes {
    const val FOOD_SEARCH = "foodSearch/{mealType}/{date}?barcode={barcode}&mode={mode}"
    const val FOOD_DETAIL = "foodDetail/{foodId}/{mealType}/{date}?mode={mode}&source={source}"
    const val RECENT_FOODS = "recentFoods/{mealType}/{date}?mode={mode}"
    const val RECENT_MEALS = "recentMeals"
    const val RECENT_MEALS_PATTERN = "recentMeals?mealType={mealType}&date={date}"
    const val RECENT_MEAL_LOG = "recentMealLog/{token}/{mealType}/{date}"
    const val MEAL_TEMPLATES = "mealTemplates"
    const val MEAL_TEMPLATES_PATTERN = "mealTemplates?mealType={mealType}&date={date}&mode={mode}"
    const val DESCRIBE_MEAL = "describeMeal/{mealType}/{date}"
    const val DESCRIBE_MEAL_PATTERN = "describeMeal/{mealType}/{date}?prefill={prefill}"
    const val SNAP_MEAL = "snapMeal/{mealType}/{date}"
    const val QUICK_ADD = "quickAddFood/{mealType}/{date}?prefillName={prefillName}&barcode={barcode}&autoFill={autoFill}&mode={mode}"
    const val BARCODE_SCANNER = "scanBarcode/{mealType}/{date}?mode={mode}"
    const val CUSTOM_FOOD = "customFood?entryId={entryId}"

    fun today(): String = MealPlanDay.string(Date())

    fun foodSearch(mealType: MealType, date: String, barcode: String? = null, mode: String = "log"): String =
        "foodSearch/${mealType.name}/$date" + query("barcode" to barcode, "mode" to mode.takeIf { it != "log" })

    fun foodDetail(foodId: String, mealType: MealType, date: String, mode: String = "log", source: String? = null): String =
        "foodDetail/${enc(foodId)}/${mealType.name}/$date" + query("mode" to mode.takeIf { it != "log" }, "source" to source)

    /** Re-log a local food-entry row (recents / My Foods / search template rows). */
    fun foodDetailTemplate(entryId: String, mealType: MealType, date: String, mode: String = "log"): String =
        foodDetail(entryId, mealType, date, mode, source = "template:$entryId")

    /** A parked template entry (barcode product hit) — see `FoodDetailHandoff.putEntry`. */
    fun foodDetailHandoff(token: String, mealType: MealType, date: String, mode: String = "log"): String =
        foodDetail(token, mealType, date, mode, source = "handoff:$token")

    fun foodDetailEdit(entryId: String): String = "foodDetail/${enc(entryId)}/${MealType.BREAKFAST.name}/${today()}?mode=edit:${enc(entryId)}"

    fun recentFoods(mealType: MealType, date: String, mode: String = "log"): String =
        "recentFoods/${mealType.name}/$date" + query("mode" to mode.takeIf { it != "log" })

    fun recentMeals(mealType: MealType, date: String): String = "recentMeals" + query("mealType" to mealType.name, "date" to date)

    fun recentMealLog(token: String, mealType: MealType, date: String): String = "recentMealLog/$token/${mealType.name}/$date"

    fun mealTemplates(mealType: MealType, date: String, mode: String = "log"): String =
        "mealTemplates" + query("mealType" to mealType.name, "date" to date, "mode" to mode.takeIf { it != "log" })

    fun describeMeal(mealType: MealType, date: String, prefillToken: String? = null): String =
        "describeMeal/${mealType.name}/$date" + query("prefill" to prefillToken)

    fun snapMeal(mealType: MealType, date: String): String = "snapMeal/${mealType.name}/$date"

    fun quickAdd(
        mealType: MealType, date: String, prefillName: String? = null, barcode: String? = null,
        autoFillWithAI: Boolean = false, mode: String = "log"
    ): String = "quickAddFood/${mealType.name}/$date" + query(
        "prefillName" to prefillName, "barcode" to barcode,
        "autoFill" to if (autoFillWithAI) "true" else null, "mode" to mode.takeIf { it != "log" }
    )

    fun barcodeScanner(mealType: MealType, date: String, mode: String = "log"): String =
        "scanBarcode/${mealType.name}/$date" + query("mode" to mode.takeIf { it != "log" })

    fun customFood(entryId: String? = null): String = "customFood" + query("entryId" to entryId)

    private fun enc(s: String) = Uri.encode(s)

    private fun query(vararg params: Pair<String, String?>): String {
        val parts = params.filter { !it.second.isNullOrEmpty() }.map { "${it.first}=${enc(it.second!!)}" }
        return if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
    }
}

private fun optionalString(name: String) = navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }

fun NavGraphBuilder.foodLoggingRoutes(navController: NavHostController) {
    val back: () -> Unit = { navController.popBackStack() }
    val navigate: (String) -> Unit = { navController.navigate(it) }

    composable(
        FoodLoggingRoutes.FOOD_SEARCH,
        arguments = listOf(
            navArgument("mealType") { type = NavType.StringType }, navArgument("date") { type = NavType.StringType },
            optionalString("barcode"), optionalString("mode")
        )
    ) { entry ->
        val args = entry.arguments
        FoodSearchScreen(
            mealType = parseMealTypeArg(args?.getString("mealType")),
            date = args?.getString("date") ?: FoodLoggingRoutes.today(),
            barcode = args?.getString("barcode"),
            flow = FoodLogFlow.parse(args?.getString("mode")),
            onBack = back,
            onNavigate = navigate
        )
    }

    composable(
        FoodLoggingRoutes.FOOD_DETAIL,
        arguments = listOf(
            navArgument("foodId") { type = NavType.StringType }, navArgument("mealType") { type = NavType.StringType },
            navArgument("date") { type = NavType.StringType }, optionalString("mode"), optionalString("source")
        )
    ) { entry ->
        val args = entry.arguments
        val (mode, flow) = FoodDetailMode.parse(args?.getString("foodId") ?: "", args?.getString("mode"), args?.getString("source"))
        FoodDetailScreen(
            mode = mode, flow = flow,
            mealType = parseMealTypeArg(args?.getString("mealType")),
            logDate = parseLogDate(args?.getString("date")),
            onBack = back
        )
    }

    composable(
        FoodLoggingRoutes.RECENT_FOODS,
        arguments = listOf(navArgument("mealType") { type = NavType.StringType }, navArgument("date") { type = NavType.StringType }, optionalString("mode"))
    ) { entry ->
        val args = entry.arguments
        RecentFoodsScreen(
            onBack = back,
            onNavigateToCustomFoodForm = { id -> navController.navigate(FoodLoggingRoutes.customFood(id.ifEmpty { null })) },
            mealTypeName = args?.getString("mealType") ?: MealType.BREAKFAST.name,
            date = args?.getString("date"),
            mode = args?.getString("mode") ?: "log",
            onNavigate = navigate
        )
    }

    composable(
        FoodLoggingRoutes.RECENT_MEALS_PATTERN,
        arguments = listOf(optionalString("mealType"), optionalString("date"))
    ) { entry ->
        val args = entry.arguments
        RecentMealsScreen(
            mealType = parseMealTypeArg(args?.getString("mealType")),
            date = args?.getString("date") ?: FoodLoggingRoutes.today(),
            onBack = back,
            onNavigate = navigate
        )
    }

    composable(
        FoodLoggingRoutes.RECENT_MEAL_LOG,
        arguments = listOf(
            navArgument("token") { type = NavType.StringType }, navArgument("mealType") { type = NavType.StringType },
            navArgument("date") { type = NavType.StringType }
        )
    ) { entry ->
        val args = entry.arguments
        RecentMealLogScreen(
            token = args?.getString("token") ?: "",
            mealType = parseMealTypeArg(args?.getString("mealType")),
            date = args?.getString("date") ?: FoodLoggingRoutes.today(),
            onBack = back,
            onNavigate = navigate
        )
    }

    composable(
        FoodLoggingRoutes.MEAL_TEMPLATES_PATTERN,
        arguments = listOf(optionalString("mealType"), optionalString("date"), optionalString("mode"))
    ) { entry ->
        val args = entry.arguments
        MealTemplatesPickerScreen(
            mealType = parseMealTypeArg(args?.getString("mealType")),
            date = args?.getString("date") ?: FoodLoggingRoutes.today(),
            flow = FoodLogFlow.parse(args?.getString("mode")),
            onBack = back
        )
    }

    composable(
        FoodLoggingRoutes.DESCRIBE_MEAL_PATTERN,
        arguments = listOf(navArgument("mealType") { type = NavType.StringType }, navArgument("date") { type = NavType.StringType }, optionalString("prefill"))
    ) { entry ->
        val args = entry.arguments
        DescribeMealScreen(
            mealType = parseMealTypeArg(args?.getString("mealType")),
            date = args?.getString("date") ?: FoodLoggingRoutes.today(),
            prefillToken = args?.getString("prefill"),
            onBack = back,
            onLogged = { fromPhoto ->
                // From a photo this screen sits on top of the Snap a Meal screen; logging ends that flow.
                navController.popBackStack()
                if (fromPhoto) navController.popBackStack()
            }
        )
    }

    composable(
        FoodLoggingRoutes.SNAP_MEAL,
        arguments = listOf(navArgument("mealType") { type = NavType.StringType }, navArgument("date") { type = NavType.StringType })
    ) { entry ->
        val args = entry.arguments
        SnapMealScreen(
            mealType = parseMealTypeArg(args?.getString("mealType")),
            date = args?.getString("date") ?: FoodLoggingRoutes.today(),
            onBack = back,
            onNavigate = navigate
        )
    }

    composable(
        FoodLoggingRoutes.QUICK_ADD,
        arguments = listOf(
            navArgument("mealType") { type = NavType.StringType }, navArgument("date") { type = NavType.StringType },
            optionalString("prefillName"), optionalString("barcode"), optionalString("autoFill"), optionalString("mode")
        )
    ) { entry ->
        val args = entry.arguments
        val mealTypeName = args?.getString("mealType") ?: MealType.BREAKFAST.name
        val date = args?.getString("date")
        QuickAddScreen(
            mealTypeName = mealTypeName,
            onBack = back,
            onNavigateToBarcodeScanner = { mt -> navController.navigate(FoodLoggingRoutes.barcodeScanner(parseMealTypeArg(mt), date ?: FoodLoggingRoutes.today(), args?.getString("mode") ?: "log")) },
            onNavigateToRecentFoods = { navController.navigate(FoodLoggingRoutes.recentFoods(parseMealTypeArg(mealTypeName), date ?: FoodLoggingRoutes.today(), args?.getString("mode") ?: "log")) },
            date = date,
            prefillName = args?.getString("prefillName"),
            barcode = args?.getString("barcode"),
            autoFillWithAI = args?.getString("autoFill") == "true",
            mode = args?.getString("mode") ?: "log",
            onNavigate = navigate
        )
    }

    composable(
        FoodLoggingRoutes.BARCODE_SCANNER,
        arguments = listOf(navArgument("mealType") { type = NavType.StringType }, navArgument("date") { type = NavType.StringType }, optionalString("mode"))
    ) { entry ->
        val args = entry.arguments
        BarcodeScannerScreen(
            mealTypeName = args?.getString("mealType") ?: MealType.BREAKFAST.name,
            onBack = back,
            date = args?.getString("date"),
            mode = args?.getString("mode") ?: "log",
            onNavigate = navigate
        )
    }

    composable(FoodLoggingRoutes.CUSTOM_FOOD, arguments = listOf(optionalString("entryId"))) { entry ->
        CustomFoodFormScreen(entryId = entry.arguments?.getString("entryId") ?: "", onBack = back)
    }
}
