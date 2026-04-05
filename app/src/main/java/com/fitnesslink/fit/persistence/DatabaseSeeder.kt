package com.fitnesslink.fit.persistence

import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.TaskRow
import java.util.UUID

object DatabaseSeeder {
    fun seedIfNeeded() {
        if (DatabaseManager.user() != null) return

        // User
        DatabaseManager.insertFullUser(
            "user1", "Fitness", "User", "user@fitnesslink.com",
            "+1 (555) 123-4567", "fitnessuser", "United States"
        )

        // Dashboards
        MockDataProvider.dashboards.forEach { DatabaseManager.insertDashboard(it) }

        // Programs
        MockDataProvider.programs.forEach {
            val detail = MockDataProvider.programDetail(it.id)
            DatabaseManager.insertProgram(detail)
        }

        // Workouts - assign unique UUIDs to phases/tasks since
        // workoutDetail() reuses the same IDs across workouts
        MockDataProvider.workouts.forEach {
            val detail = MockDataProvider.workoutDetail(it.id)
            val uniqueDetail = detail.copy(
                phases = detail.phases.map { phase ->
                    phase.copy(
                        id = UUID.randomUUID().toString(),
                        taskRows = phase.taskRows.map { row ->
                            val task = row.task?.copy(id = UUID.randomUUID().toString())
                            TaskRow(task = task, advanced = row.advanced, rounds = row.rounds,
                                totalRounds = row.totalRounds, isSuperset = row.isSuperset,
                                isCircuit = row.isCircuit, advancedTasks = row.advancedTasks)
                        }
                    )
                }
            )
            DatabaseManager.insertWorkout(uniqueDetail)
        }

        // Food Entries (today + historical)
        MockDataProvider.todayFoodEntries.forEach { DatabaseManager.insertFoodEntry(it) }
        MockDataProvider.historicalFoodEntries.forEach { DatabaseManager.insertFoodEntry(it) }

        // Nutrition Goal
        DatabaseManager.insertNutritionGoal(MockDataProvider.nutritionGoal)

        // Meal Slots
        MockDataProvider.weeklyMealSlots.forEach { DatabaseManager.insertMealSlot(it) }

        // Barcode Products
        MockDataProvider.mockBarcodeProducts.values.forEach { DatabaseManager.insertBarcodeProduct(it) }

        // Personalizations
        MockDataProvider.personalizations.forEach { DatabaseManager.insertPersonalization(it) }

        // Calendar Content
        MockDataProvider.calendarContent.forEach { DatabaseManager.insertCalendarContent(it) }

        // Profile Menus
        MockDataProvider.profileMenuItems.forEach { DatabaseManager.insertProfileMenu(it) }

        // Workout Sessions (for Report)
        val now = System.currentTimeMillis()
        val dayMs = 86400000L
        MockDataProvider.mockSessions.forEach { s ->
            val startDate = now - s.daysAgo * dayMs
            val completionDate = if (s.isCompleted) startDate + s.durationSeconds * 1000L else null
            DatabaseManager.insertWorkoutSession(
                s.id, s.workoutId, "user1", null, s.workoutName, startDate, completionDate,
                s.durationSeconds, s.isCompleted, s.exerciseCount, s.totalSets, s.totalReps,
                s.totalWeightLifted, s.totalCaloriesBurned, s.rpeValue
            )
        }
        MockDataProvider.mockSessionHistory.forEach { h ->
            DatabaseManager.insertSessionHistory(
                java.util.UUID.randomUUID().toString(), h.sessionId,
                java.util.UUID.randomUUID().toString(), h.workoutId, null, "user1",
                now - h.daysAgo * dayMs, h.reps, h.setNumber, null, h.weightLifted, h.taskName
            )
        }

        // Movements (exercise library with muscle group & equipment)
        seedMovementLibrary()

        // Notifications
        seedNotifications(now, dayMs)
    }

    private fun seedMovementLibrary() {
        data class M(val name: String, val desc: String, val muscle: String, val equip: String)
        val library = listOf(
            M("Bench Press", "Primary chest compound movement", "Chest", "Barbell"),
            M("Incline Dumbbell Press", "Upper chest focus", "Chest", "Dumbbell"),
            M("Incline Dumbbell Fly", "Upper chest isolation", "Chest", "Dumbbell"),
            M("Cable Crossover", "Chest isolation", "Chest", "Cable"),
            M("Push-Up", "Bodyweight chest exercise", "Chest", "Bodyweight"),
            M("Overhead Press", "Shoulder compound movement", "Shoulders", "Barbell"),
            M("Lateral Raise", "Side delt isolation", "Shoulders", "Dumbbell"),
            M("Face Pull", "Rear delt and upper back", "Shoulders", "Cable"),
            M("Barbell Row", "Back compound movement", "Back", "Barbell"),
            M("Lat Pulldown", "Lat isolation", "Back", "Cable"),
            M("Seated Row", "Mid-back focus", "Back", "Cable"),
            M("Pull-Up", "Bodyweight back exercise", "Back", "Bodyweight"),
            M("Deadlift", "Full body compound", "Back", "Barbell"),
            M("Barbell Squat", "Primary leg compound", "Legs", "Barbell"),
            M("Leg Press", "Quad focus compound", "Legs", "Machine"),
            M("Romanian Deadlift", "Hamstring focus", "Legs", "Barbell"),
            M("Leg Extension", "Quad isolation", "Legs", "Machine"),
            M("Leg Curl", "Hamstring isolation", "Legs", "Machine"),
            M("Calf Raise", "Calf isolation", "Legs", "Machine"),
            M("Barbell Curl", "Bicep compound", "Arms", "Barbell"),
            M("Tricep Pushdown", "Tricep isolation", "Arms", "Cable"),
            M("Hammer Curl", "Bicep brachialis focus", "Arms", "Dumbbell"),
            M("Skull Crusher", "Tricep isolation", "Arms", "Barbell"),
            M("Plank", "Core stabilizer", "Core", "Bodyweight"),
            M("Hanging Leg Raise", "Lower abs", "Core", "Bodyweight"),
            M("Cable Crunch", "Ab isolation", "Core", "Cable"),
            M("Russian Twist", "Oblique focus", "Core", "Bodyweight"),
            M("Arm Circles", "Loosen up shoulder joints", "Shoulders", "Bodyweight"),
            M("Jumping Jacks", "Full body warm-up", "Core", "Bodyweight"),
            M("Chest Stretch", "Cool down stretch for pecs", "Chest", "Bodyweight"),
            M("Band Pull-Apart", "Upper back activation", "Back", "Band")
        )
        library.forEach { m ->
            DatabaseManager.insertMovementFull(
                UUID.randomUUID().toString(), m.name, m.desc, m.muscle, m.equip
            )
        }
    }

    private fun seedNotifications(now: Long, dayMs: Long) {
        val hourMs = 3600000L
        val notifications = listOf(
            Triple("system", "Welcome to FitnessLink!", "Your account is set up. Start exploring workouts and tracking your nutrition."),
            Triple("goals", "Weekly Goal Update", "You've completed 3 of 5 planned workouts this week. Keep pushing!"),
            Triple("content", "New Program Available", "Check out the 8-week Strength Builder program designed for intermediate lifters."),
            Triple("calendar", "Workout Reminder", "You have Upper Body Strength scheduled for today at 6:00 PM."),
            Triple("goals", "Nutrition Streak!", "You've logged your meals for 7 consecutive days. Great consistency!"),
            Triple("system", "App Update", "Version 2.0 includes new workout creation tools and enhanced tracking."),
            Triple("content", "Tip of the Day", "Progressive overload doesn't just mean more weight — try more reps or shorter rest."),
            Triple("calendar", "Rest Day Tomorrow", "Your program schedules a rest day tomorrow. Recovery is part of the process."),
            Triple("goals", "Personal Record!", "You hit a new bench press PR of 185 lbs. Congratulations!"),
            Triple("content", "Nutrition Guide", "Learn how to calculate your daily caloric needs for muscle building.")
        )
        val deepLinks = listOf(null, "workoutReport", "programs", "workouts", "nutritionReport",
            null, null, null, "workoutReport", "nutritionReport")
        val timeOffsets = listOf(
            2 * hourMs, 5 * hourMs,                     // Today
            dayMs + 3 * hourMs, dayMs + 8 * hourMs,     // Yesterday
            2 * dayMs, 3 * dayMs, 4 * dayMs,            // Earlier
            5 * dayMs, 6 * dayMs, 7 * dayMs
        )

        notifications.forEachIndexed { i, (type, title, body) ->
            DatabaseManager.insertNotification(
                id = UUID.randomUUID().toString(),
                type = type,
                title = title,
                body = body,
                isRead = i >= 4,
                createdAt = now - timeOffsets[i],
                deepLink = deepLinks[i]
            )
        }
    }
}
