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
    }
}
