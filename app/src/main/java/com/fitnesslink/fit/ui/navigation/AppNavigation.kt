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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

data class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: Int,
    val unselectedIcon: Int
)

@Composable
fun AppNavigation() {
    var isAuthenticated by rememberSaveable { mutableStateOf(false) }
    var showPersonalization by rememberSaveable { mutableStateOf(false) }

    if (!isAuthenticated) {
        AuthNavigation(
            onLogin = { needsPersonalization ->
                showPersonalization = needsPersonalization
                isAuthenticated = true
            }
        )
    } else if (showPersonalization) {
        PersonalizationScreen(
            onComplete = { showPersonalization = false }
        )
    } else {
        MainTabNavigation(
            onLogout = {
                isAuthenticated = false
                showPersonalization = false
            }
        )
    }
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
fun MainTabNavigation(onLogout: () -> Unit) {
    val navController = rememberNavController()
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    val bottomNavItems = listOf(
        BottomNavItem("home", "Home", R.drawable.homeselected, R.drawable.homeicon),
        BottomNavItem("catalog", "Catalog", R.drawable.runningselected, R.drawable.runningicon),
        BottomNavItem("nutrition", "Nutrition", R.drawable.nutrition, R.drawable.nutrition),
        BottomNavItem("calendar", "Calendar", R.drawable.calendarselected, R.drawable.calendaricon),
        BottomNavItem("profile", "Profile", R.drawable.profileselected, R.drawable.profileicon)
    )

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
                        onClick = {
                            selectedTab = index
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
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
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(onNavigateToNotifications = { navController.navigate("notifications") })
            }
            composable("catalog") {
                CatalogScreen(
                    onNavigateToPrograms = { navController.navigate("programs") },
                    onNavigateToWorkouts = { navController.navigate("workouts") },
                    onNavigateToProgramDetail = { navController.navigate("programDetail/$it") },
                    onNavigateToWorkoutDetail = { navController.navigate("workoutDetail/$it") }
                )
            }
            composable("nutrition") {
                NutritionHomeScreen(
                    onNavigateToCalorieTracking = { navController.navigate("calorieTracking") },
                    onNavigateToMealPlan = { navController.navigate("mealPlan") },
                    onNavigateToNutritionSummary = { navController.navigate("nutritionSummary") },
                    onNavigateToGroceryList = { navController.navigate("groceryList") },
                    onNavigateToGoalSettings = { navController.navigate("nutritionGoalSettings") },
                    onNavigateToLogWater = { navController.navigate("logWater") }
                )
            }
            composable("calendar") {
                CalendarScreen(
                    onNavigateToWorkoutDetail = { navController.navigate("workoutDetail/$it") }
                )
            }
            composable("profile") {
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
                ProfileStubScreen(title = backStackEntry.arguments?.getString("metric") ?: "Detail", onBack = { navController.popBackStack() })
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
                ProfileStubScreen(title = "Billing", onBack = { navController.popBackStack() })
            }
            composable("goals") {
                // Until a goals-list screen lands, the Profile menu entry
                // routes straight into the goal-setup flow.
                GoalCreationScreen(onClose = { navController.popBackStack() })
            }
            composable("goalCreation") {
                GoalCreationScreen(onClose = { navController.popBackStack() })
            }
            composable("achievements") {
                AchievementsScreen(onBack = { navController.popBackStack() })
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
                // TODO: MovementAggregateDetailScreen
                ProfileStubScreen(title = backStackEntry.arguments?.getString("exerciseName") ?: "Movement", onBack = { navController.popBackStack() })
            }
        }
    }
}
