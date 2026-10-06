package com.fitnesslink.fit.ui.navigation

/**
 * Port of iOS `Services/DeepLinkResolver.swift` (FTNSSLNK-261). Shared by
 * push-notification taps and (eventually) the inbox rows.
 *
 * Server deep links are BARE route strings (no scheme):
 *   Calendar producer: "workoutDetail/{id}" or "calendar" (→ Calendar tab)
 *   Goals producer:    "goals"
 *   Content producer:  "programDetail/{id}", "movementDetail/{id}", "workoutDetail/{id}"
 *   System broadcasts: deepLink is null (tapping just marks the row read).
 * Legacy user-to-user share rows still exist in prod with a scheme:
 *   "fitnesslink://shared/{workout|program|movement|mealplan}/{id}"
 * Those are normalized to the same detail routes below.
 *
 * Returns a Navigation-Compose route string for `MainTabNavigation`, or null
 * when the link is unknown (tapping then just opens the app).
 */
object DeepLinkResolver {
    private const val SCHEME_PREFIX = "fitnesslink://"

    fun resolve(link: String): String? {
        var normalized = link.trim()
        if (normalized.startsWith(SCHEME_PREFIX)) {
            normalized = normalized.removePrefix(SCHEME_PREFIX)
        }
        val parts = normalized.split("/").filter { it.isNotEmpty() }

        // Legacy share links: shared/{kind}/{id}
        if (parts.firstOrNull() == "shared") {
            if (parts.size < 3) return null
            val id = parts[2]
            return when (parts[1]) {
                "workout" -> AppRoute.WorkoutDetail.createRoute(id)
                "program" -> AppRoute.ProgramDetail.createRoute(id)
                // TODO(movements): no movement detail screen exists on Android yet
                // (Wave 1.9). iOS opens movementDetail/{id}.
                "movement" -> null
                // No per-plan detail route exists; the user's meal plan screen is
                // the closest destination for a shared meal plan.
                "mealplan" -> AppRoute.MealPlan.route
                else -> null
            }
        }

        return when (parts.firstOrNull() ?: "") {
            "personalInfo" -> AppRoute.PersonalInfo.route
            "workoutReport" -> AppRoute.WorkoutReport.route
            "nutritionReport" -> AppRoute.NutritionReport.route
            "goals" -> AppRoute.Goals.route
            "programs" -> AppRoute.Programs.route
            "workouts" -> AppRoute.Workouts.route
            // Calendar is a tab, not a pushable screen: MainTabNavigation
            // switches to it (AppRouter.isTabSwitch) instead of pushing.
            "calendar" -> AppRouter.CALENDAR_TAB
            // Android's GroceryListScreen is a single list; iOS lands on the
            // lists overview (FTNSSLNK-208). TODO(grocery): repoint when the
            // multi-list overview exists (Wave 3.8).
            "groceryList" -> AppRoute.GroceryList.route
            "workoutDetail" ->
                if (parts.size > 1) AppRoute.WorkoutDetail.createRoute(parts[1])
                else AppRoute.Workouts.route // id missing — fall back to the list
            "programDetail" ->
                if (parts.size > 1) AppRoute.ProgramDetail.createRoute(parts[1])
                else AppRoute.Programs.route
            // TODO(movements): movementDetail/{id} and the movement library do
            // not exist on Android yet (Wave 1.9). Until then the tap just opens
            // the app (and still marks the row read).
            "movementDetail" -> null
            else -> null
        }
    }
}
