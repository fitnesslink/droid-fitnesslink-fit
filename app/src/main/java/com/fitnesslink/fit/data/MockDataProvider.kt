package com.fitnesslink.fit.data

import com.fitnesslink.fit.model.*
import java.util.Calendar
import java.util.Date

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

    // Nutrition

    val nutritionGoal = NutritionGoal(
        id = "ng1",
        calorieGoal = 2000,
        proteinTarget = 150,
        fatTarget = 65,
        carbsTarget = 250
    )

    val todayFoodEntries: List<FoodEntry> = listOf(
        FoodEntry(id = "fe1", name = "Oatmeal with Berries", calories = 320, protein = 12.0, fat = 8.0, carbs = 52.0, servingSize = 1.0, servingUnit = "bowl", mealType = MealType.BREAKFAST),
        FoodEntry(id = "fe2", name = "Banana", calories = 105, protein = 1.3, fat = 0.4, carbs = 27.0, servingSize = 1.0, servingUnit = "medium", mealType = MealType.BREAKFAST),
        FoodEntry(id = "fe3", name = "Protein Shake", calories = 180, protein = 30.0, fat = 3.0, carbs = 8.0, servingSize = 1.0, servingUnit = "shake", mealType = MealType.BREAKFAST),
        FoodEntry(id = "fe4", name = "Grilled Chicken Breast", calories = 284, protein = 53.0, fat = 6.0, carbs = 0.0, servingSize = 8.0, servingUnit = "oz", mealType = MealType.LUNCH),
        FoodEntry(id = "fe5", name = "Brown Rice", calories = 216, protein = 5.0, fat = 1.8, carbs = 45.0, servingSize = 1.0, servingUnit = "cup", mealType = MealType.LUNCH),
        FoodEntry(id = "fe6", name = "Mixed Greens Salad", calories = 120, protein = 3.0, fat = 7.0, carbs = 12.0, servingSize = 1.0, servingUnit = "bowl", mealType = MealType.LUNCH),
        FoodEntry(id = "fe7", name = "Salmon Fillet", calories = 367, protein = 34.0, fat = 22.0, carbs = 0.0, servingSize = 6.0, servingUnit = "oz", mealType = MealType.DINNER),
        FoodEntry(id = "fe8", name = "Sweet Potato", calories = 103, protein = 2.3, fat = 0.1, carbs = 24.0, servingSize = 1.0, servingUnit = "medium", mealType = MealType.DINNER),
        FoodEntry(id = "fe9", name = "Greek Yogurt", calories = 100, protein = 17.0, fat = 0.7, carbs = 6.0, servingSize = 1.0, servingUnit = "cup", mealType = MealType.SNACK),
        FoodEntry(id = "fe10", name = "Almonds", calories = 164, protein = 6.0, fat = 14.0, carbs = 6.0, servingSize = 1.0, servingUnit = "oz", mealType = MealType.SNACK)
    )

    val recentFoods: List<FoodEntry> = listOf(
        FoodEntry(id = "rf1", name = "Oatmeal with Berries", calories = 320, protein = 12.0, fat = 8.0, carbs = 52.0, servingSize = 1.0, servingUnit = "bowl", mealType = MealType.BREAKFAST),
        FoodEntry(id = "rf2", name = "Grilled Chicken Breast", calories = 284, protein = 53.0, fat = 6.0, carbs = 0.0, servingSize = 8.0, servingUnit = "oz", mealType = MealType.LUNCH),
        FoodEntry(id = "rf3", name = "Protein Shake", calories = 180, protein = 30.0, fat = 3.0, carbs = 8.0, servingSize = 1.0, servingUnit = "shake", mealType = MealType.SNACK),
        FoodEntry(id = "rf4", name = "Salmon Fillet", calories = 367, protein = 34.0, fat = 22.0, carbs = 0.0, servingSize = 6.0, servingUnit = "oz", mealType = MealType.DINNER),
        FoodEntry(id = "rf5", name = "Greek Yogurt", calories = 100, protein = 17.0, fat = 0.7, carbs = 6.0, servingSize = 1.0, servingUnit = "cup", mealType = MealType.SNACK),
        FoodEntry(id = "rf6", name = "Brown Rice", calories = 216, protein = 5.0, fat = 1.8, carbs = 45.0, servingSize = 1.0, servingUnit = "cup", mealType = MealType.LUNCH),
        FoodEntry(id = "rf7", name = "Banana", calories = 105, protein = 1.3, fat = 0.4, carbs = 27.0, servingSize = 1.0, servingUnit = "medium", mealType = MealType.SNACK),
        FoodEntry(id = "rf8", name = "Eggs (Scrambled)", calories = 182, protein = 12.0, fat = 13.0, carbs = 2.0, servingSize = 2.0, servingUnit = "large", mealType = MealType.BREAKFAST)
    )

    val customFoods: List<FoodEntry> = listOf(
        FoodEntry(id = "cf1", name = "Post-Workout Smoothie", calories = 350, protein = 35.0, fat = 8.0, carbs = 40.0, servingSize = 1.0, servingUnit = "smoothie", mealType = MealType.SNACK, isCustomTemplate = true),
        FoodEntry(id = "cf2", name = "Homemade Granola", calories = 220, protein = 6.0, fat = 10.0, carbs = 30.0, servingSize = 0.5, servingUnit = "cup", mealType = MealType.BREAKFAST, isCustomTemplate = true)
    )

    val weeklyCalories: List<DailyCalorieSummary> = listOf(
        DailyCalorieSummary(id = "wc1", dayLabel = "Mon", calories = 1850, protein = 140.0, fat = 60.0, carbs = 230.0, goal = 2000),
        DailyCalorieSummary(id = "wc2", dayLabel = "Tue", calories = 2100, protein = 155.0, fat = 70.0, carbs = 260.0, goal = 2000),
        DailyCalorieSummary(id = "wc3", dayLabel = "Wed", calories = 1750, protein = 135.0, fat = 55.0, carbs = 220.0, goal = 2000),
        DailyCalorieSummary(id = "wc4", dayLabel = "Thu", calories = 1960, protein = 148.0, fat = 63.0, carbs = 245.0, goal = 2000),
        DailyCalorieSummary(id = "wc5", dayLabel = "Fri", calories = 2200, protein = 160.0, fat = 75.0, carbs = 270.0, goal = 2000),
        DailyCalorieSummary(id = "wc6", dayLabel = "Sat", calories = 1680, protein = 120.0, fat = 58.0, carbs = 210.0, goal = 2000),
        DailyCalorieSummary(id = "wc7", dayLabel = "Sun", calories = 1959, protein = 146.0, fat = 64.0, carbs = 243.0, goal = 2000)
    )

    fun foodEntry(id: String): FoodEntry? = todayFoodEntries.firstOrNull { it.id == id }

    val mockBarcodeProducts: Map<String, BarcodeProduct> = mapOf(
        "049000006346" to BarcodeProduct(barcode = "049000006346", name = "Coca-Cola Classic", brand = "Coca-Cola", caloriesPer100g = 42.0, proteinPer100g = 0.0, fatPer100g = 0.0, carbsPer100g = 10.6, servingSizeGrams = 355.0, servingUnit = "ml"),
        "016000275287" to BarcodeProduct(barcode = "016000275287", name = "Cheerios", brand = "General Mills", caloriesPer100g = 357.0, proteinPer100g = 11.3, fatPer100g = 6.0, carbsPer100g = 71.4, servingSizeGrams = 28.0, servingUnit = "g"),
        "818290014306" to BarcodeProduct(barcode = "818290014306", name = "Greek Yogurt, Strawberry", brand = "Chobani", caloriesPer100g = 83.0, proteinPer100g = 8.3, fatPer100g = 2.5, carbsPer100g = 8.3, servingSizeGrams = 150.0, servingUnit = "g"),
        "038000138416" to BarcodeProduct(barcode = "038000138416", name = "Crunchy Peanut Butter", brand = "Skippy", caloriesPer100g = 588.0, proteinPer100g = 24.0, fatPer100g = 50.0, carbsPer100g = 18.0, servingSizeGrams = 32.0, servingUnit = "g")
    )

    fun barcodeProduct(barcode: String): BarcodeProduct? = mockBarcodeProducts[barcode]
}
