package com.fitnesslink.fit.data

import com.fitnesslink.fit.model.*
import java.util.Calendar

object MockDataProvider {

    val dashboards: List<HomeDashboard> = listOf(
        HomeDashboard(id = "1", name = "Workout Activity", progress = "3", unit = "workouts", goals = "5"),
        HomeDashboard(id = "2", name = "Activity", progress = "1,250", unit = "calories", goals = "2,000"),
        HomeDashboard(id = "3", name = "Current Weight", progress = "175", unit = "lbs", goals = "170"),
        HomeDashboard(id = "4", name = "Hydration", progress = "6", unit = "glasses", goals = "8"),
        HomeDashboard(id = "5", name = "Workout Time", progress = "45", unit = "min", goals = "60")
    )

    fun calendarItems(totalDays: Int, currentDay: Int): List<HorizontalCalendar> {
        val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        return (1..totalDays).map { day ->
            calendar.set(Calendar.DAY_OF_MONTH, day)
            val weekday = calendar.get(Calendar.DAY_OF_WEEK) - 1
            HorizontalCalendar(
                id = "$day",
                dayNumber = day,
                name = "$day",
                status = when {
                    day < currentDay -> "completed"
                    day == currentDay -> "scheduled"
                    else -> ""
                },
                weekDay = weekdays[weekday],
                selected = day == currentDay
            )
        }
    }

    fun calendarCells(totalDays: Int): List<CalendarCell> {
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_MONTH)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val firstWeekday = calendar.get(Calendar.DAY_OF_WEEK)

        val cells = mutableListOf<CalendarCell>()
        for (i in 0 until (firstWeekday - 1)) {
            cells.add(CalendarCell(id = "empty-$i"))
        }
        for (day in 1..totalDays) {
            val status = when {
                day < today -> "completed"
                day == today -> "scheduled"
                else -> ""
            }
            cells.add(CalendarCell(
                id = "$day",
                dayNumber = day,
                name = "$day",
                status = status,
                selected = day == today
            ))
        }
        return cells
    }

    val programs: List<ProgramList> = listOf(
        ProgramList(id = "p1", imageUrl = "", name = "Strength Builder", time = "8 weeks", isFavorite = true),
        ProgramList(id = "p2", imageUrl = "", name = "Lean & Tone", time = "6 weeks", isFavorite = false),
        ProgramList(id = "p3", imageUrl = "", name = "HIIT Cardio Blast", time = "4 weeks", isFavorite = true),
        ProgramList(id = "p4", imageUrl = "", name = "Full Body Transform", time = "12 weeks", isFavorite = false),
        ProgramList(id = "p5", imageUrl = "", name = "Core & Flexibility", time = "6 weeks", isFavorite = false)
    )

    fun programDetail(id: String): Program {
        val name = programs.firstOrNull { it.id == id }?.name ?: "Strength Builder"
        return Program(
            id = id,
            name = name,
            imageUrl = "",
            time = "8 weeks",
            location = "Gym",
            trainingLevel = "Intermediate",
            description = "Build functional strength with progressive overload across compound movements. Each week increases intensity to ensure continuous improvement."
        )
    }

    val workouts: List<WorkoutList> = listOf(
        WorkoutList(id = "w1", imageUrl = "", name = "Upper Body Push", time = "45 min", isFavorite = true),
        WorkoutList(id = "w2", imageUrl = "", name = "Lower Body Strength", time = "50 min", isFavorite = false),
        WorkoutList(id = "w3", imageUrl = "", name = "Full Body HIIT", time = "30 min", isFavorite = true),
        WorkoutList(id = "w4", imageUrl = "", name = "Core & Abs", time = "25 min", isFavorite = false),
        WorkoutList(id = "w5", imageUrl = "", name = "Cardio Endurance", time = "40 min", isFavorite = false),
        WorkoutList(id = "w6", imageUrl = "", name = "Back & Biceps", time = "45 min", isFavorite = true)
    )

    fun workoutDetail(id: String): Workout {
        val name = workouts.firstOrNull { it.id == id }?.name ?: "Upper Body Push"

        val warmup = WorkoutPhase(
            id = "ph1",
            name = "Warm Up",
            taskRows = listOf(
                TaskRow(task = WorkoutTask(id = "t1", name = "Arm Circles", metric = "30 sec", isMovement = true, order = 1)),
                TaskRow(task = WorkoutTask(id = "t2", name = "Jumping Jacks", metric = "1 min", isMovement = true, order = 2))
            )
        )

        val main = WorkoutPhase(
            id = "ph2",
            name = "Main Workout",
            taskRows = listOf(
                TaskRow(task = WorkoutTask(id = "t3", name = "Bench Press", metric = "4 x 10", isMovement = true, sets = 4, reps = 10, restSeconds = 60, rest = "60s", order = 1)),
                TaskRow(task = WorkoutTask(id = "t4", name = "Overhead Press", metric = "3 x 12", isMovement = true, sets = 3, reps = 12, restSeconds = 45, rest = "45s", order = 2)),
                TaskRow(task = WorkoutTask(id = "t5", name = "Incline Dumbbell Fly", metric = "3 x 15", isMovement = true, sets = 3, reps = 15, restSeconds = 45, rest = "45s", order = 3)),
                TaskRow(task = WorkoutTask(id = "t6", name = "Tricep Pushdown", metric = "3 x 12", isMovement = true, sets = 3, reps = 12, restSeconds = 30, rest = "30s", order = 4))
            )
        )

        val cooldown = WorkoutPhase(
            id = "ph3",
            name = "Cool Down",
            taskRows = listOf(
                TaskRow(task = WorkoutTask(id = "t7", name = "Chest Stretch", metric = "30 sec", isMovement = true, order = 1))
            )
        )

        return Workout(
            id = id,
            name = name,
            imageUrl = "",
            time = "45 min",
            location = "Gym",
            trainingLevel = "Intermediate",
            description = "Target your chest, shoulders, and triceps with a mix of compound and isolation exercises.",
            phases = listOf(warmup, main, cooldown)
        )
    }

    val catalogPrograms: List<CatalogItem> = listOf(
        CatalogItem(id = "cp1", title = "Strength Builder", imageUrl = "", caption = "8 weeks"),
        CatalogItem(id = "cp2", title = "Lean & Tone", imageUrl = "", caption = "6 weeks"),
        CatalogItem(id = "cp3", title = "HIIT Cardio Blast", imageUrl = "", caption = "4 weeks")
    )

    val catalogWorkouts: List<CatalogItem> = listOf(
        CatalogItem(id = "cw1", title = "Upper Body Push", imageUrl = "", caption = "45 min"),
        CatalogItem(id = "cw2", title = "Lower Body Strength", imageUrl = "", caption = "50 min"),
        CatalogItem(id = "cw3", title = "Full Body HIIT", imageUrl = "", caption = "30 min")
    )

    val myWorkouts: List<CatalogItem> = listOf(
        CatalogItem(id = "mw1", title = "Morning Routine", imageUrl = "", caption = "20 min"),
        CatalogItem(id = "mw2", title = "Evening Stretch", imageUrl = "", caption = "15 min")
    )

    val personalizations: List<Personalization> = listOf(
        Personalization(id = "1", name = "What is your fitness goal?", singleSelection = true, options = listOf(
            PersonalizationItem(id = "1a", text = "Lose Weight"),
            PersonalizationItem(id = "1b", text = "Build Muscle"),
            PersonalizationItem(id = "1c", text = "Improve Endurance"),
            PersonalizationItem(id = "1d", text = "Stay Active"),
            PersonalizationItem(id = "1e", text = "Increase Flexibility")
        )),
        Personalization(id = "2", name = "What is your training level?", singleSelection = true, options = listOf(
            PersonalizationItem(id = "2a", text = "Beginner"),
            PersonalizationItem(id = "2b", text = "Intermediate"),
            PersonalizationItem(id = "2c", text = "Advanced")
        )),
        Personalization(id = "3", name = "How many days per week do you want to train?", singleSelection = true, options = listOf(
            PersonalizationItem(id = "3a", text = "2-3 days"),
            PersonalizationItem(id = "3b", text = "4-5 days"),
            PersonalizationItem(id = "3c", text = "6-7 days")
        )),
        Personalization(id = "4", name = "What equipment do you have access to?", singleSelection = false, options = listOf(
            PersonalizationItem(id = "4a", text = "No Equipment"),
            PersonalizationItem(id = "4b", text = "Dumbbells"),
            PersonalizationItem(id = "4c", text = "Full Gym"),
            PersonalizationItem(id = "4d", text = "Resistance Bands"),
            PersonalizationItem(id = "4e", text = "Kettlebells")
        )),
        Personalization(id = "5", name = "Where do you prefer to work out?", singleSelection = true, options = listOf(
            PersonalizationItem(id = "5a", text = "At Home"),
            PersonalizationItem(id = "5b", text = "At the Gym"),
            PersonalizationItem(id = "5c", text = "Outdoors")
        ))
    )

    val profileMenuItems: List<ProfileMenu> = listOf(
        ProfileMenu(id = 1, text = "Personal Info"),
        ProfileMenu(id = 2, text = "Personalization"),
        ProfileMenu(id = 3, text = "Analytics & Progress"),
        ProfileMenu(id = 4, text = "Preferences"),
        ProfileMenu(id = 5, text = "Billing"),
        ProfileMenu(id = 6, text = "Goals"),
        ProfileMenu(id = 7, text = "Measurements"),
        ProfileMenu(id = 8, text = "Photos"),
        ProfileMenu(id = 9, text = "Weight"),
        ProfileMenu(id = 10, text = "Workout Report")
    )

    fun workoutSession(workoutId: String): WorkoutSession {
        val detail = workoutDetail(workoutId)
        val tasks = detail.phases.flatMap { phase ->
            phase.taskRows.mapNotNull { it.task }
        }
        return WorkoutSession(
            id = "session-$workoutId",
            workoutId = workoutId,
            name = detail.name,
            description = detail.description,
            estimatedTime = detail.time,
            trainingLevel = detail.trainingLevel,
            workoutTasks = tasks
        )
    }

    val sessionExercises: List<SessionExercise> = listOf(
        SessionExercise(id = "se1", name = "Bench Press", iconUrl = "", metrics = "4 x 10", completed = true),
        SessionExercise(id = "se2", name = "Overhead Press", iconUrl = "", metrics = "3 x 12", completed = true),
        SessionExercise(id = "se3", name = "Incline Fly", iconUrl = "", metrics = "3 x 15", completed = false),
        SessionExercise(id = "se4", name = "Tricep Pushdown", iconUrl = "", metrics = "3 x 12", completed = false)
    )

    val calendarContent: List<FitnessContent> = listOf(
        FitnessContent(id = "fc1", title = "Upper Body Push", programId = "p1", workoutId = "w1", mealPlanId = "", status = "completed"),
        FitnessContent(id = "fc2", title = "Rest Day", programId = "", workoutId = "", mealPlanId = "", status = "rest"),
        FitnessContent(id = "fc3", title = "Lower Body Strength", programId = "p1", workoutId = "w2", mealPlanId = "", status = "scheduled")
    )
}
