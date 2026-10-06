package com.fitnesslink.fit.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.push.PushNotificationManager
import com.fitnesslink.fit.push.rememberPushPermissionRequester
import com.fitnesslink.fit.ui.auth.GetStartedScreen
import com.fitnesslink.fit.ui.auth.SplashScreen
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fitnesslink.fit.R
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fitnesslink.fit.ui.auth.LoginScreen
import com.fitnesslink.fit.ui.auth.WelcomeScreen
import com.fitnesslink.fit.ui.calendar.CalendarScreen
import com.fitnesslink.fit.ui.catalog.CatalogScreen
import com.fitnesslink.fit.ui.catalog.ProgramDetailScreen
import com.fitnesslink.fit.ui.catalog.ProgramEditorScreen
import com.fitnesslink.fit.ui.catalog.ProgramsScreen
import com.fitnesslink.fit.ui.catalog.WorkoutsScreen
import com.fitnesslink.fit.ui.home.HomeScreen
import com.fitnesslink.fit.ui.nutrition.BarcodeScannerScreen
import com.fitnesslink.fit.ui.nutrition.CustomFoodFormScreen
import com.fitnesslink.fit.ui.nutrition.FoodEntryDetailScreen
import com.fitnesslink.fit.ui.nutrition.GroceryListScreen
import com.fitnesslink.fit.ui.nutrition.LogWaterScreen
import com.fitnesslink.fit.ui.nutrition.MealPlanScreen
import com.fitnesslink.fit.ui.nutrition.MealSlotDetailScreen
import com.fitnesslink.fit.ui.nutrition.NutritionGoalSettingsScreen
import com.fitnesslink.fit.ui.nutrition.NutritionHomeScreen
import com.fitnesslink.fit.ui.nutrition.NutritionScreen
import com.fitnesslink.fit.ui.nutrition.NutritionSummaryScreen
import com.fitnesslink.fit.ui.nutrition.QuickAddScreen
import com.fitnesslink.fit.ui.nutrition.RecentFoodsScreen
import com.fitnesslink.fit.ui.nutrition.WeeklyProgressScreen
import com.fitnesslink.fit.ui.personalization.PersonalizationScreen
import com.fitnesslink.fit.ui.profile.ProfileScreen
import com.fitnesslink.fit.ui.profile.ProfileStubScreen
import com.fitnesslink.fit.ui.session.InteractiveSessionScreen
import com.fitnesslink.fit.ui.session.PlaylistScreen
import com.fitnesslink.fit.ui.workout.WorkoutDetailScreen
import com.fitnesslink.fit.ui.workout.WorkoutEditorScreen
import com.fitnesslink.fit.ui.notifications.NotificationsScreen
import com.fitnesslink.fit.ui.goals.AchievementsScreen
import com.fitnesslink.fit.ui.goals.GoalCreationScreen
import com.fitnesslink.fit.ui.goals.GoalDetailScreen
import com.fitnesslink.fit.ui.goals.GoalsListScreen
import com.fitnesslink.fit.ui.goals.HabitDetailScreen
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

data class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: Int,
    val unselectedIcon: Int
)

/**
 * Root gating, ported from iOS `FitnessLinkApp.body`:
 *
 *   splash (session restore) → auth → entitlement gate → personalization →
 *   Get Started → main tabs
 *
 * `isAuthenticated` is derived from [AuthManager] state rather than a local
 * flag, so logout (and a server-side session loss) flips the UI back to the
 * welcome flow automatically.
 */
@Composable
fun AppNavigation() {
    val isAuthenticated by AuthManager.isAuthenticated.collectAsState()
    val isRestoringSession by AuthManager.isRestoringSession.collectAsState()
    val currentUser by AuthManager.currentUser.collectAsState()
    var showPersonalization by rememberSaveable { mutableStateOf(false) }
    // FTNSSLNK-356: post-personalization "Get Started" routing screen. Only
    // ever set by PersonalizationScreen's onComplete, which fires once per
    // account — so the screen is inherently show-once.
    var showGetStarted by rememberSaveable { mutableStateOf(false) }

    when {
        isRestoringSession && !isAuthenticated -> SplashScreen()

        isAuthenticated -> EntitlementGate {
            when {
                // Two triggers: the explicit login handshake, and —
                // reactively — the user profile itself, so a restored
                // session honours a server-side requirePersonalization flip.
                showPersonalization || currentUser?.requirePersonalization == true -> {
                    PersonalizationScreen(
                        onComplete = {
                            showPersonalization = false
                            // Flip the in-memory flag NOW so this branch
                            // releases immediately; the async server POST in
                            // the flow's view model re-confirms it later.
                            AuthManager.markPersonalizationComplete()
                            showGetStarted = true
                        }
                    )
                }
                showGetStarted -> GetStartedScreen(
                    onFinish = { route ->
                        // Park the chosen route on the router BEFORE swapping
                        // to the tabs; MainTabNavigation consumes it on appear.
                        route?.let { AppRouter.open(it) }
                        showGetStarted = false
                    }
                )
                else -> MainTabNavigation(
                    onLogout = {
                        showPersonalization = false
                        showGetStarted = false
                    }
                )
            }
        }

        else -> AuthNavigation(
            onLogin = { needsPersonalization ->
                // Best-effort: AuthManager.isAuthenticated usually flips (and
                // this branch leaves composition) before the login screen's
                // effect can call back, so the root ALSO derives
                // personalization from currentUser.requirePersonalization and
                // the post-login hooks (initial sync, media prefetch) run
                // inside AuthManager at the end of every interactive sign-in.
                showPersonalization = needsPersonalization
            }
        )
    }
}

/**
 * FTNSSLNK-283 slot: nothing in FitnessLink is free, so the paywall sits
 * between auth and everything else. Wave 4.5 (Play Billing + entitlement
 * store + 402 gate) fills this in; until then it passes through.
 */
@Composable
fun EntitlementGate(content: @Composable () -> Unit) {
    content()
}

@Composable
fun AuthNavigation(onLogin: (Boolean) -> Unit) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "welcome") {
        composable("welcome") {
            WelcomeScreen(
                onSignUp = { navController.navigate("signUp") },
                onLogin = { navController.navigate("login") }
            )
        }
        composable("login") {
            LoginScreen(
                initialTab = 1,
                onLogin = onLogin
            )
        }
        composable("signUp") {
            LoginScreen(
                initialTab = 0,
                onLogin = onLogin
            )
        }
    }
}

@Composable
fun MainTabNavigation(onLogout: () -> Unit = {}) {
    val navController = rememberNavController()
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current

    // Tab order matches iOS: Home, Catalog, Calendar, Nutrition, Profile.
    val bottomNavItems = listOf(
        BottomNavItem(AppRouter.HOME_TAB, "Home", R.drawable.homeselected, R.drawable.homeicon),
        BottomNavItem(AppRouter.CATALOG_TAB, "Catalog", R.drawable.runningselected, R.drawable.runningicon),
        BottomNavItem(AppRouter.CALENDAR_TAB, "Calendar", R.drawable.calendarselected, R.drawable.calendaricon),
        BottomNavItem(AppRouter.NUTRITION_TAB, "Nutrition", R.drawable.nutrition, R.drawable.nutrition),
        BottomNavItem(AppRouter.PROFILE_TAB, "Profile", R.drawable.profileselected, R.drawable.profileicon)
    )

    fun selectTab(route: String) {
        val index = bottomNavItems.indexOfFirst { it.route == route }
        if (index >= 0) selectedTab = index
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** iOS `consumePendingRoute`: tab routes land on that tab's root; everything else pushes from Home. */
    fun consumePendingRoute() {
        val route = AppRouter.consume() ?: return
        if (AppRouter.isTabSwitch(route)) {
            selectTab(route)
            return
        }
        selectTab(AppRouter.HOME_TAB)
        navController.navigate(route) { launchSingleTop = true }
    }

    // Route parked while the UI was alive (foreground tap, Get Started) →
    // consume as soon as it changes; a cold-start tap parked before this
    // view existed is consumed on first composition.
    val pendingRoute by AppRouter.pendingRoute.collectAsState()
    LaunchedEffect(pendingRoute) { if (pendingRoute != null) consumePendingRoute() }

    DisposableEffect(navController) {
        AppRouter.pushHandler = { route ->
            if (AppRouter.isTabSwitch(route)) selectTab(route)
            else navController.navigate(route) { launchSingleTop = true }
        }
        AppRouter.replaceTopHandler = { route ->
            navController.popBackStack()
            navController.navigate(route) { launchSingleTop = true }
        }
        AppRouter.resetStackHandler = { route ->
            val tabRoute = bottomNavItems.getOrNull(selectedTab)?.route ?: AppRouter.HOME_TAB
            navController.popBackStack(tabRoute, inclusive = false)
            route?.let { navController.navigate(it) { launchSingleTop = true } }
        }
        AppRouter.popHandler = { count ->
            repeat(count) { if (navController.previousBackStackEntry != null) navController.popBackStack() }
        }
        onDispose { AppRouter.clearHandlers() }
    }

    // Push permission (Android 13+). iOS asks contextually rather than at
    // first launch; mirror the Home pre-prompt cadence loosely: once, a
    // couple of seconds after the tabs appear, and never again after an
    // answer (PushNotificationManager.shouldRequestPermission).
    val requestPushPermission = rememberPushPermissionRequester()
    LaunchedEffect(Unit) {
        delay(2_000)
        if (AppRouter.pendingRoute.value == null && PushNotificationManager.shouldRequestPermission(context)) {
            requestPushPermission()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = White) {
                bottomNavItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = {
                            Image(
                                painter = painterResource(
                                    if (selectedTab == index) item.selectedIcon else item.unselectedIcon
                                ),
                                contentDescription = item.title,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text(item.title) },
                        selected = selectedTab == index,
                        onClick = { selectTab(item.route) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = FLPrimary,
                            selectedTextColor = FLPrimary,
                            unselectedIconColor = TextSecondaryColor,
                            unselectedTextColor = TextSecondaryColor,
                            indicatorColor = White
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRouter.HOME_TAB,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppRouter.HOME_TAB) {
                HomeScreen(
                    onNavigateToNotifications = { navController.navigate("notifications") },
                    onNavigateToWorkout = { workoutId -> navController.navigate("workoutDetail/$workoutId") }
                )
            }
            composable(AppRouter.CATALOG_TAB) {
                CatalogScreen(
                    onNavigateToPrograms = { navController.navigate("programs") },
                    onNavigateToWorkouts = { navController.navigate("workouts") },
                    onNavigateToProgramDetail = { navController.navigate("programDetail/$it") },
                    onNavigateToWorkoutDetail = { navController.navigate("workoutDetail/$it") },
                    onNavigateToWorkoutEditor = { navController.navigate("workoutEditor") },
                    onNavigateToProgramEditor = { navController.navigate("programEditor") }
                )
            }
            composable(AppRouter.NUTRITION_TAB) {
                NutritionHomeScreen(
                    onNavigateToCalorieTracking = { navController.navigate("calorieTracking") },
                    onNavigateToMealPlan = { navController.navigate("mealPlan") },
                    onNavigateToNutritionSummary = { navController.navigate("nutritionSummary") },
                    onNavigateToGroceryList = { navController.navigate("groceryList") },
                    onNavigateToGoalSettings = { navController.navigate("nutritionGoalSettings") },
                    onNavigateToLogWater = { navController.navigate("logWater") }
                )
            }
            composable(AppRouter.CALENDAR_TAB) {
                CalendarScreen(
                    onNavigateToWorkoutDetail = { navController.navigate("workoutDetail/$it") }
                )
            }
            composable(AppRouter.PROFILE_TAB) {
                ProfileScreen(
                    onNavigate = { route -> navController.navigate(route) },
                    onLogout = onLogout
                )
            }

            // Programs
            composable("programs") {
                ProgramsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToProgramDetail = { navController.navigate("programDetail/$it") },
                    onNavigateToProgramEditor = { navController.navigate("programEditor") }
                )
            }
            composable(
                "programDetail/{programId}",
                arguments = listOf(navArgument("programId") { type = NavType.StringType })
            ) { backStackEntry ->
                ProgramDetailScreen(
                    programId = backStackEntry.arguments?.getString("programId") ?: "",
                    onBack = { navController.popBackStack() },
                    onNavigateToEditor = { id -> navController.navigate("programEditor/$id") }
                )
            }

            // Program Editor
            composable("programEditor") {
                ProgramEditorScreen(
                    programId = null,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                AppRoute.ProgramEditor.ROUTE,
                arguments = listOf(navArgument("programId") { type = NavType.StringType })
            ) { backStackEntry ->
                ProgramEditorScreen(
                    programId = backStackEntry.arguments?.getString("programId"),
                    onBack = { navController.popBackStack() }
                )
            }

            // Workouts
            composable("workouts") {
                WorkoutsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToWorkoutDetail = { navController.navigate("workoutDetail/$it") },
                    onNavigateToWorkoutEditor = { navController.navigate("workoutEditor") }
                )
            }
            composable(
                "workoutDetail/{workoutId}",
                arguments = listOf(navArgument("workoutId") { type = NavType.StringType })
            ) { backStackEntry ->
                WorkoutDetailScreen(
                    workoutId = backStackEntry.arguments?.getString("workoutId") ?: "",
                    onBack = { navController.popBackStack() },
                    onStartPlaylist = { navController.navigate("playlistSession/$it") },
                    onStartInteractive = { navController.navigate("interactiveSession/$it") }
                )
            }

            // Workout Editor
            composable("workoutEditor") {
                WorkoutEditorScreen(
                    workoutId = null,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                AppRoute.WorkoutEditor.ROUTE,
                arguments = listOf(navArgument("workoutId") { type = NavType.StringType })
            ) { backStackEntry ->
                WorkoutEditorScreen(
                    workoutId = backStackEntry.arguments?.getString("workoutId"),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                AppRoute.WorkoutEditorGenerate.ROUTE,
                arguments = listOf(navArgument(AppRoute.WorkoutEditorGenerate.ARG_SEED_PROMPT) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                // TODO(Wave 1.8): hand `seedPrompt` to the editor so it opens the
                // AI generation sheet pre-seeded (iOS `workoutEditorGenerate`).
                @Suppress("UNUSED_VARIABLE")
                val seedPrompt = backStackEntry.arguments?.getString(AppRoute.WorkoutEditorGenerate.ARG_SEED_PROMPT)
                WorkoutEditorScreen(
                    workoutId = null,
                    onBack = { navController.popBackStack() }
                )
            }

            // Notifications
            composable("notifications") {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigate(route) }
                )
            }

            // Workout Sessions
            composable(
                "playlistSession/{workoutId}",
                arguments = listOf(navArgument("workoutId") { type = NavType.StringType })
            ) { backStackEntry ->
                PlaylistScreen(
                    workoutId = backStackEntry.arguments?.getString("workoutId") ?: "",
                    onBack = { navController.popBackStack() },
                    onNavigateToInteractive = { navController.navigate("interactiveSession/$it") }
                )
            }
            composable(
                "interactiveSession/{workoutId}",
                arguments = listOf(navArgument("workoutId") { type = NavType.StringType })
            ) { backStackEntry ->
                InteractiveSessionScreen(
                    workoutId = backStackEntry.arguments?.getString("workoutId") ?: "",
                    onBack = { navController.popBackStack() }
                )
            }

            // Nutrition sub-screens
            composable("calorieTracking") {
                NutritionScreen(
                    onNavigateToGoalSettings = { navController.navigate("nutritionGoalSettings") },
                    onNavigateToQuickAdd = { mealType -> navController.navigate("quickAdd/$mealType") },
                    onNavigateToFoodEntryDetail = { entryId -> navController.navigate("foodEntryDetail/$entryId") },
                    onNavigateToRecentFoods = { navController.navigate("recentFoods") },
                    onNavigateToWeeklyProgress = { navController.navigate("weeklyProgress") },
                    onNavigateToBarcodeScanner = { mealType -> navController.navigate("barcodeScanner/$mealType") }
                )
            }
            composable("mealPlan") {
                MealPlanScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToMealSlotDetail = { day, mealType -> navController.navigate("mealSlotDetail/$day/$mealType") },
                    onNavigateToNutritionSummary = { navController.navigate("nutritionSummary") },
                    onNavigateToGroceryList = { navController.navigate("groceryList") }
                )
            }
            composable(
                "mealSlotDetail/{day}/{mealType}",
                arguments = listOf(
                    navArgument("day") { type = NavType.StringType },
                    navArgument("mealType") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                MealSlotDetailScreen(
                    dayName = backStackEntry.arguments?.getString("day") ?: "MONDAY",
                    mealTypeName = backStackEntry.arguments?.getString("mealType") ?: "BREAKFAST",
                    onBack = { navController.popBackStack() }
                )
            }
            composable("nutritionSummary") {
                NutritionSummaryScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("groceryList") {
                GroceryListScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("nutritionGoalSettings") {
                NutritionGoalSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("logWater") {
                LogWaterScreen(onBack = { navController.popBackStack() })
            }
            composable(
                "quickAdd/{mealType}",
                arguments = listOf(navArgument("mealType") { type = NavType.StringType })
            ) { backStackEntry ->
                QuickAddScreen(
                    mealTypeName = backStackEntry.arguments?.getString("mealType") ?: "BREAKFAST",
                    onBack = { navController.popBackStack() },
                    onNavigateToBarcodeScanner = { mealType -> navController.navigate("barcodeScanner/$mealType") },
                    onNavigateToRecentFoods = { navController.navigate("recentFoods") }
                )
            }
            composable(
                "foodEntryDetail/{entryId}",
                arguments = listOf(navArgument("entryId") { type = NavType.StringType })
            ) { backStackEntry ->
                FoodEntryDetailScreen(
                    entryId = backStackEntry.arguments?.getString("entryId") ?: "",
                    onBack = { navController.popBackStack() }
                )
            }
            composable("recentFoods") {
                RecentFoodsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToCustomFoodForm = { entryId -> navController.navigate("customFoodForm/$entryId") }
                )
            }
            composable("weeklyProgress") {
                WeeklyProgressScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "customFoodForm/{entryId}",
                arguments = listOf(navArgument("entryId") { type = NavType.StringType })
            ) { backStackEntry ->
                CustomFoodFormScreen(
                    entryId = backStackEntry.arguments?.getString("entryId") ?: "",
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "barcodeScanner/{mealType}",
                arguments = listOf(navArgument("mealType") { type = NavType.StringType })
            ) { backStackEntry ->
                BarcodeScannerScreen(
                    mealTypeName = backStackEntry.arguments?.getString("mealType") ?: "BREAKFAST",
                    onBack = { navController.popBackStack() }
                )
            }

            // Profile sub-screens
            composable("personalInfo") {
                com.fitnesslink.fit.ui.profile.PersonalInfoScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToBilling = { navController.navigate("billing") }
                )
            }
            composable("developerSettings") {
                com.fitnesslink.fit.ui.profile.DeveloperSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("personalizationProfile") {
                PersonalizationScreen(
                    onComplete = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("nutritionReport") {
                com.fitnesslink.fit.ui.profile.NutritionReportScreen(
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable("nutritionMetricDetail/{metric}", arguments = listOf(navArgument("metric") { type = NavType.StringType })) { backStackEntry ->
                com.fitnesslink.fit.ui.profile.NutritionMetricDetailScreen(
                    metricKey = backStackEntry.arguments?.getString("metric") ?: "calories",
                    onBack = { navController.popBackStack() }
                )
            }
            composable("mealTypeDetail/{mealType}", arguments = listOf(navArgument("mealType") { type = NavType.StringType })) { backStackEntry ->
                ProfileStubScreen(title = backStackEntry.arguments?.getString("mealType") ?: "Meal", onBack = { navController.popBackStack() })
            }
            composable("foodItemDetail/{foodName}", arguments = listOf(navArgument("foodName") { type = NavType.StringType })) { backStackEntry ->
                ProfileStubScreen(title = backStackEntry.arguments?.getString("foodName") ?: "Food", onBack = { navController.popBackStack() })
            }
            composable("preferences") {
                ProfileStubScreen(title = "Preferences", onBack = { navController.popBackStack() })
            }
            composable("billing") {
                com.fitnesslink.fit.ui.profile.BillingScreen(onBack = { navController.popBackStack() })
            }
            composable("goals") {
                GoalsListScreen(
                    onBack = { navController.popBackStack() },
                    onCreateGoal = { navController.navigate("goalCreation") },
                    onOpenGoal = { id -> navController.navigate(AppRoute.GoalDetail.createRoute(id)) }
                )
            }
            composable("goalCreation") {
                GoalCreationScreen(onClose = { navController.popBackStack() })
            }
            composable(
                AppRoute.GoalDetail.ROUTE,
                arguments = listOf(navArgument("goalId") { type = NavType.StringType })
            ) { backStackEntry ->
                GoalDetailScreen(
                    goalId = backStackEntry.arguments?.getString("goalId") ?: "",
                    onBack = { navController.popBackStack() },
                    onOpenHabit = { id ->
                        navController.navigate(AppRoute.HabitDetail.createRoute(id))
                    }
                )
            }
            composable(
                AppRoute.HabitDetail.ROUTE,
                arguments = listOf(navArgument("habitId") { type = NavType.StringType })
            ) { backStackEntry ->
                HabitDetailScreen(
                    habitId = backStackEntry.arguments?.getString("habitId") ?: "",
                    onBack = { navController.popBackStack() }
                )
            }
            composable("achievements") {
                AchievementsScreen(onBack = { navController.popBackStack() })
            }
            composable("notificationSettings") {
                com.fitnesslink.fit.ui.profile.NotificationSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("calendarSyncSettings") {
                com.fitnesslink.fit.ui.profile.CalendarSyncSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("measurements") {
                com.fitnesslink.fit.ui.progress.MeasurementsLogScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("photos") {
                com.fitnesslink.fit.ui.progress.ProgressPhotosScreen(
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable("weight") {
                com.fitnesslink.fit.ui.progress.WeightLogScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                AppRoute.PhotoEntryDetail.ROUTE,
                arguments = listOf(navArgument("entryId") { type = NavType.StringType })
            ) { backStackEntry ->
                val entryId = backStackEntry.arguments?.getString("entryId") ?: ""
                com.fitnesslink.fit.ui.progress.PhotoEntryDetailScreen(
                    entryId = entryId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("photoComparison") {
                com.fitnesslink.fit.ui.progress.PhotoComparisonScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("workoutReport") {
                com.fitnesslink.fit.ui.profile.WorkoutReportScreen(
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable(
                AppRoute.ReportDetail.ROUTE,
                arguments = listOf(navArgument("metric") { type = NavType.StringType })
            ) { backStackEntry ->
                // TODO: ReportDetailScreen
                ProfileStubScreen(title = backStackEntry.arguments?.getString("metric") ?: "Detail", onBack = { navController.popBackStack() })
            }
            composable(
                AppRoute.SessionDetail.ROUTE,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) { backStackEntry ->
                com.fitnesslink.fit.ui.profile.SessionDetailScreen(
                    sessionId = backStackEntry.arguments?.getString("sessionId") ?: "",
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                AppRoute.WorkoutAggregateDetail.ROUTE,
                arguments = listOf(navArgument("workoutName") { type = NavType.StringType })
            ) { backStackEntry ->
                // TODO: WorkoutAggregateDetailScreen
                ProfileStubScreen(title = backStackEntry.arguments?.getString("workoutName") ?: "Workout", onBack = { navController.popBackStack() })
            }
            composable(
                AppRoute.MovementAggregateDetail.ROUTE,
                arguments = listOf(navArgument("exerciseName") { type = NavType.StringType })
            ) { backStackEntry ->
                com.fitnesslink.fit.ui.profile.MovementAggregateDetailScreen(
                    exerciseName = backStackEntry.arguments?.getString("exerciseName") ?: "",
                    onBack = { navController.popBackStack() }
                )
            }
            // Feature modules register their own routes here (ui/navigation/*Routes.kt).
            featureRoutes(navController)
        }
    }
}
