package com.fitnesslink.fit.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.HomeDashboard
import com.fitnesslink.fit.ui.components.HeaderView
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.programs.MyProgramHomeCard
import com.fitnesslink.fit.ui.session.ResumeSessionBanner
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.viewmodel.HomeViewModel
import com.fitnesslink.fit.viewmodel.WeeklyWorkoutGoalPrefs

/**
 * Home tab (port of iOS `HomeView`). Section order matches iOS: date strip,
 * Today's Workout, My Program card, quick actions, Continue Where You Left
 * Off, goal cards, Today's Habits + daily footer, dashboard grid.
 *
 * Navigation: the two original callbacks are kept for `AppNavigation.kt`;
 * every other destination goes through [onNavigate] (a Navigation-Compose
 * route string), which defaults to `AppRouter.push` so the screen works
 * before the integrator wires a dedicated callback.
 */
@Composable
fun HomeScreen(
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToWorkout: (String) -> Unit = {},
    onNavigate: (String) -> Unit = { AppRouter.push(it) }
) {
    val viewModel: HomeViewModel = viewModel()
    val context = LocalContext.current
    var showWeeklyGoalEditor by remember { mutableStateOf(false) }

    // `onAppear` runs on every (re)entry into composition — first load, tab
    // re-selection, pop back from a pushed screen — so Home always re-reads
    // the local DB and re-centres on today.
    LaunchedEffect(Unit) {
        WeeklyWorkoutGoalPrefs.initialize(context)
        viewModel.onAppear()
    }
    // App returning to the foreground (also covers the day rolling over).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.selectToday()
                viewModel.hydrateFromCache()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderView(onNotificationsTap = onNavigateToNotifications)

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 24.dp)
        ) {
            DateScrollView(
                items = viewModel.dateItems,
                scrollTo = viewModel.scrollToDay,
                recenter = viewModel.recenterToken,
                onSelect = viewModel::selectDay
            )

            TodayWorkoutView(
                workouts = viewModel.todayWorkouts,
                title = viewModel.workoutSectionTitle,
                isRestDay = viewModel.isProgramRestDay,
                tomorrowWorkout = viewModel.tomorrowWorkout,
                onNavigate = onNavigate
            )

            // Programs agent: renders nothing when no program is active.
            MyProgramHomeCard(
                onViewAll = { onNavigate("myProgram") },
                onViewProgress = { onNavigate("programProgress") },
                onOpenWorkout = { workoutId -> onNavigateToWorkout(workoutId) }
            )

            HomeQuickActionsView(onNavigate = onNavigate, onSetGoal = { onNavigate("goalCreation") })

            // Session agent: "Continue Where You Left Off" (renders nothing without in-progress sessions).
            ResumeSessionBanner(onResume = { entry -> onNavigate(entry.resumeRoute) })

            if (viewModel.goalSummaries.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(viewModel.goalSummaries, key = { it.id }) { goal ->
                        GoalProgressCard(goal = goal, onClick = { onNavigate("goalDetail/${goal.id}") })
                    }
                }
            }

            if (viewModel.todayHabits.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Today's Habits", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "See All",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = FLPrimary,
                            modifier = Modifier.clickable { onNavigate("goals") }
                        )
                    }
                    viewModel.todayHabits.forEach { habit ->
                        HabitCard(
                            habit = habit,
                            onComplete = { viewModel.completeHabit(habit.habitId) },
                            onTap = { onNavigate("habitDetail/${habit.habitId}") },
                            onUncomplete = { viewModel.uncompleteHabit(habit.habitId) }
                        )
                    }
                    DailySummaryFooter(progress = viewModel.dailyProgress)
                }
            }

            HomeDashboardView(
                dashboards = viewModel.dashboards,
                onNavigate = onNavigate,
                onGoalEdit = { dashboard ->
                    when (dashboard.id) {
                        HomeDashboard.ID_WORKOUTS_WEEK -> showWeeklyGoalEditor = true
                        // Calorie targets live in the nutrition goals hub.
                        HomeDashboard.ID_CALORIES_TODAY -> onNavigate("nutritionGoalSettings")
                    }
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    if (showWeeklyGoalEditor) {
        WeeklyGoalSheet(
            current = viewModel.weeklyWorkoutGoal ?: 5,
            onSave = { goal ->
                viewModel.setWeeklyWorkoutGoal(goal)
                showWeeklyGoalEditor = false
            },
            onCancel = { showWeeklyGoalEditor = false }
        )
    }
}
