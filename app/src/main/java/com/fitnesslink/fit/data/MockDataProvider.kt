package com.fitnesslink.fit.data

import com.fitnesslink.fit.model.*
import java.util.Calendar
import java.util.Date
import java.util.UUID

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

    // Meal Plan

    val weeklyMealSlots: List<MealSlot> = listOf(
        // Monday
        MealSlot(id = "ms1", day = DayOfWeek.MONDAY, mealType = MealType.BREAKFAST, recipeName = "Oatmeal with Berries", calories = 320, protein = 12.0, fat = 8.0, carbs = 52.0, ingredients = listOf(
            GroceryItem(name = "Rolled Oats", quantity = "1", unit = "cup", category = GroceryCategory.GRAINS),
            GroceryItem(name = "Mixed Berries", quantity = "0.5", unit = "cup", category = GroceryCategory.PRODUCE),
            GroceryItem(name = "Honey", quantity = "1", unit = "tbsp", category = GroceryCategory.PANTRY)
        )),
        MealSlot(id = "ms2", day = DayOfWeek.MONDAY, mealType = MealType.LUNCH, recipeName = "Grilled Chicken Salad", calories = 420, protein = 45.0, fat = 18.0, carbs = 22.0, ingredients = listOf(
            GroceryItem(name = "Chicken Breast", quantity = "6", unit = "oz", category = GroceryCategory.PROTEIN),
            GroceryItem(name = "Mixed Greens", quantity = "2", unit = "cups", category = GroceryCategory.PRODUCE),
            GroceryItem(name = "Cherry Tomatoes", quantity = "0.5", unit = "cup", category = GroceryCategory.PRODUCE),
            GroceryItem(name = "Olive Oil", quantity = "1", unit = "tbsp", category = GroceryCategory.PANTRY)
        )),
        MealSlot(id = "ms3", day = DayOfWeek.MONDAY, mealType = MealType.DINNER, recipeName = "Salmon with Sweet Potato", calories = 470, protein = 36.0, fat = 22.0, carbs = 30.0, ingredients = listOf(
            GroceryItem(name = "Salmon Fillet", quantity = "6", unit = "oz", category = GroceryCategory.PROTEIN),
            GroceryItem(name = "Sweet Potato", quantity = "1", unit = "medium", category = GroceryCategory.PRODUCE),
            GroceryItem(name = "Broccoli", quantity = "1", unit = "cup", category = GroceryCategory.PRODUCE)
        )),
        MealSlot(id = "ms4", day = DayOfWeek.MONDAY, mealType = MealType.SNACK, recipeName = "Greek Yogurt & Almonds", calories = 200, protein = 18.0, fat = 10.0, carbs = 12.0, ingredients = listOf(
            GroceryItem(name = "Greek Yogurt", quantity = "1", unit = "cup", category = GroceryCategory.DAIRY),
            GroceryItem(name = "Almonds", quantity = "1", unit = "oz", category = GroceryCategory.PANTRY)
        )),
        // Tuesday
        MealSlot(id = "ms5", day = DayOfWeek.TUESDAY, mealType = MealType.BREAKFAST, recipeName = "Scrambled Eggs & Toast", calories = 350, protein = 22.0, fat = 18.0, carbs = 26.0, ingredients = listOf(
            GroceryItem(name = "Eggs", quantity = "3", unit = "large", category = GroceryCategory.DAIRY),
            GroceryItem(name = "Whole Wheat Bread", quantity = "2", unit = "slices", category = GroceryCategory.GRAINS)
        )),
        MealSlot(id = "ms6", day = DayOfWeek.TUESDAY, mealType = MealType.LUNCH, recipeName = "Turkey Wrap", calories = 380, protein = 32.0, fat = 14.0, carbs = 36.0, ingredients = listOf(
            GroceryItem(name = "Turkey Breast", quantity = "4", unit = "oz", category = GroceryCategory.PROTEIN),
            GroceryItem(name = "Whole Wheat Tortilla", quantity = "1", unit = "large", category = GroceryCategory.GRAINS),
            GroceryItem(name = "Avocado", quantity = "0.5", unit = "medium", category = GroceryCategory.PRODUCE)
        )),
        MealSlot(id = "ms7", day = DayOfWeek.TUESDAY, mealType = MealType.DINNER, recipeName = "Chicken Stir Fry", calories = 450, protein = 40.0, fat = 16.0, carbs = 38.0, ingredients = listOf(
            GroceryItem(name = "Chicken Breast", quantity = "6", unit = "oz", category = GroceryCategory.PROTEIN),
            GroceryItem(name = "Brown Rice", quantity = "1", unit = "cup", category = GroceryCategory.GRAINS),
            GroceryItem(name = "Bell Peppers", quantity = "1", unit = "cup", category = GroceryCategory.PRODUCE),
            GroceryItem(name = "Soy Sauce", quantity = "2", unit = "tbsp", category = GroceryCategory.PANTRY)
        )),
        // Wednesday
        MealSlot(id = "ms8", day = DayOfWeek.WEDNESDAY, mealType = MealType.BREAKFAST, recipeName = "Protein Smoothie", calories = 280, protein = 30.0, fat = 6.0, carbs = 32.0, ingredients = listOf(
            GroceryItem(name = "Protein Powder", quantity = "1", unit = "scoop", category = GroceryCategory.PANTRY),
            GroceryItem(name = "Banana", quantity = "1", unit = "medium", category = GroceryCategory.PRODUCE),
            GroceryItem(name = "Almond Milk", quantity = "1", unit = "cup", category = GroceryCategory.DAIRY)
        )),
        MealSlot(id = "ms9", day = DayOfWeek.WEDNESDAY, mealType = MealType.LUNCH, recipeName = "Tuna Poke Bowl", calories = 440, protein = 38.0, fat = 14.0, carbs = 42.0, ingredients = listOf(
            GroceryItem(name = "Ahi Tuna", quantity = "5", unit = "oz", category = GroceryCategory.PROTEIN),
            GroceryItem(name = "Sushi Rice", quantity = "1", unit = "cup", category = GroceryCategory.GRAINS),
            GroceryItem(name = "Edamame", quantity = "0.5", unit = "cup", category = GroceryCategory.FROZEN),
            GroceryItem(name = "Avocado", quantity = "0.5", unit = "medium", category = GroceryCategory.PRODUCE)
        )),
        MealSlot(id = "ms10", day = DayOfWeek.WEDNESDAY, mealType = MealType.DINNER, recipeName = "Lean Beef Tacos", calories = 480, protein = 36.0, fat = 20.0, carbs = 40.0, ingredients = listOf(
            GroceryItem(name = "Lean Ground Beef", quantity = "5", unit = "oz", category = GroceryCategory.PROTEIN),
            GroceryItem(name = "Corn Tortillas", quantity = "3", unit = "small", category = GroceryCategory.GRAINS),
            GroceryItem(name = "Lettuce", quantity = "1", unit = "cup", category = GroceryCategory.PRODUCE),
            GroceryItem(name = "Salsa", quantity = "2", unit = "tbsp", category = GroceryCategory.PANTRY)
        )),
        // Thursday
        MealSlot(id = "ms11", day = DayOfWeek.THURSDAY, mealType = MealType.BREAKFAST, recipeName = "Avocado Toast", calories = 310, protein = 10.0, fat = 18.0, carbs = 30.0, ingredients = listOf(
            GroceryItem(name = "Whole Wheat Bread", quantity = "2", unit = "slices", category = GroceryCategory.GRAINS),
            GroceryItem(name = "Avocado", quantity = "1", unit = "medium", category = GroceryCategory.PRODUCE)
        )),
        MealSlot(id = "ms12", day = DayOfWeek.THURSDAY, mealType = MealType.LUNCH, recipeName = "Chicken Caesar Wrap", calories = 410, protein = 38.0, fat = 16.0, carbs = 32.0, ingredients = listOf(
            GroceryItem(name = "Chicken Breast", quantity = "5", unit = "oz", category = GroceryCategory.PROTEIN),
            GroceryItem(name = "Romaine Lettuce", quantity = "1", unit = "cup", category = GroceryCategory.PRODUCE),
            GroceryItem(name = "Whole Wheat Tortilla", quantity = "1", unit = "large", category = GroceryCategory.GRAINS),
            GroceryItem(name = "Caesar Dressing", quantity = "1", unit = "tbsp", category = GroceryCategory.PANTRY)
        )),
        MealSlot(id = "ms13", day = DayOfWeek.THURSDAY, mealType = MealType.DINNER, recipeName = "Baked Cod with Quinoa", calories = 390, protein = 34.0, fat = 10.0, carbs = 40.0, ingredients = listOf(
            GroceryItem(name = "Cod Fillet", quantity = "6", unit = "oz", category = GroceryCategory.PROTEIN),
            GroceryItem(name = "Quinoa", quantity = "1", unit = "cup", category = GroceryCategory.GRAINS),
            GroceryItem(name = "Asparagus", quantity = "6", unit = "spears", category = GroceryCategory.PRODUCE)
        )),
        // Friday
        MealSlot(id = "ms14", day = DayOfWeek.FRIDAY, mealType = MealType.BREAKFAST, recipeName = "Banana Pancakes", calories = 340, protein = 14.0, fat = 10.0, carbs = 50.0, ingredients = listOf(
            GroceryItem(name = "Banana", quantity = "1", unit = "large", category = GroceryCategory.PRODUCE),
            GroceryItem(name = "Eggs", quantity = "2", unit = "large", category = GroceryCategory.DAIRY),
            GroceryItem(name = "Oat Flour", quantity = "0.5", unit = "cup", category = GroceryCategory.GRAINS)
        )),
        MealSlot(id = "ms15", day = DayOfWeek.FRIDAY, mealType = MealType.LUNCH, recipeName = "Shrimp Rice Bowl", calories = 430, protein = 35.0, fat = 12.0, carbs = 48.0, ingredients = listOf(
            GroceryItem(name = "Shrimp", quantity = "6", unit = "oz", category = GroceryCategory.PROTEIN),
            GroceryItem(name = "Brown Rice", quantity = "1", unit = "cup", category = GroceryCategory.GRAINS),
            GroceryItem(name = "Zucchini", quantity = "1", unit = "medium", category = GroceryCategory.PRODUCE)
        )),
        MealSlot(id = "ms16", day = DayOfWeek.FRIDAY, mealType = MealType.DINNER, recipeName = "Grilled Steak & Veggies", calories = 520, protein = 44.0, fat = 28.0, carbs = 22.0, ingredients = listOf(
            GroceryItem(name = "Sirloin Steak", quantity = "6", unit = "oz", category = GroceryCategory.PROTEIN),
            GroceryItem(name = "Bell Peppers", quantity = "1", unit = "cup", category = GroceryCategory.PRODUCE),
            GroceryItem(name = "Mushrooms", quantity = "1", unit = "cup", category = GroceryCategory.PRODUCE)
        ))
    )

    val aiSuggestedMeals: List<MealSlot> = listOf(
        MealSlot(id = "ai1", day = DayOfWeek.MONDAY, mealType = MealType.BREAKFAST, recipeName = "High-Protein Overnight Oats", calories = 380, protein = 28.0, fat = 12.0, carbs = 44.0, isAISuggestion = true, ingredients = listOf(
            GroceryItem(name = "Rolled Oats", quantity = "1", unit = "cup", category = GroceryCategory.GRAINS),
            GroceryItem(name = "Protein Powder", quantity = "1", unit = "scoop", category = GroceryCategory.PANTRY),
            GroceryItem(name = "Chia Seeds", quantity = "1", unit = "tbsp", category = GroceryCategory.PANTRY),
            GroceryItem(name = "Almond Milk", quantity = "1", unit = "cup", category = GroceryCategory.DAIRY)
        )),
        MealSlot(id = "ai2", day = DayOfWeek.MONDAY, mealType = MealType.LUNCH, recipeName = "Mediterranean Quinoa Bowl", calories = 460, protein = 32.0, fat = 18.0, carbs = 48.0, isAISuggestion = true, ingredients = listOf(
            GroceryItem(name = "Quinoa", quantity = "1", unit = "cup", category = GroceryCategory.GRAINS),
            GroceryItem(name = "Chicken Breast", quantity = "5", unit = "oz", category = GroceryCategory.PROTEIN),
            GroceryItem(name = "Cucumber", quantity = "0.5", unit = "medium", category = GroceryCategory.PRODUCE),
            GroceryItem(name = "Feta Cheese", quantity = "1", unit = "oz", category = GroceryCategory.DAIRY)
        )),
        MealSlot(id = "ai3", day = DayOfWeek.MONDAY, mealType = MealType.DINNER, recipeName = "Teriyaki Salmon Bowl", calories = 490, protein = 38.0, fat = 20.0, carbs = 42.0, isAISuggestion = true, ingredients = listOf(
            GroceryItem(name = "Salmon Fillet", quantity = "6", unit = "oz", category = GroceryCategory.PROTEIN),
            GroceryItem(name = "Sushi Rice", quantity = "1", unit = "cup", category = GroceryCategory.GRAINS),
            GroceryItem(name = "Edamame", quantity = "0.5", unit = "cup", category = GroceryCategory.FROZEN),
            GroceryItem(name = "Teriyaki Sauce", quantity = "2", unit = "tbsp", category = GroceryCategory.PANTRY)
        ))
    )

    // MARK: - Progress Photos

    val progressPhotoEntries: List<ProgressPhotoEntry> = listOf(
        ProgressPhotoEntry(
            id = "pe1",
            userId = "user1",
            date = daysAgo(60),
            photos = listOf(
                AnglePhoto(id = "ap1", angle = PhotoAngle.FRONT, notes = "Starting point"),
                AnglePhoto(id = "ap2", angle = PhotoAngle.SIDE, notes = ""),
                AnglePhoto(id = "ap3", angle = PhotoAngle.BACK, notes = "")
            ),
            notes = "Day 1 — starting my fitness journey"
        ),
        ProgressPhotoEntry(
            id = "pe2",
            userId = "user1",
            date = daysAgo(30),
            photos = listOf(
                AnglePhoto(id = "ap4", angle = PhotoAngle.FRONT, notes = "Seeing some changes"),
                AnglePhoto(id = "ap5", angle = PhotoAngle.SIDE, notes = ""),
                AnglePhoto(id = "ap6", angle = PhotoAngle.BACK, notes = "Back looking wider")
            ),
            notes = "30 day check-in — feeling stronger"
        ),
        ProgressPhotoEntry(
            id = "pe3",
            userId = "user1",
            date = daysAgo(7),
            photos = listOf(
                AnglePhoto(id = "ap7", angle = PhotoAngle.FRONT, notes = "Abs starting to show"),
                AnglePhoto(id = "ap8", angle = PhotoAngle.SIDE, notes = ""),
                AnglePhoto(id = "ap9", angle = PhotoAngle.BACK, notes = "")
            ),
            notes = "Week 8 — big improvements"
        )
    )

    fun photoEntry(id: String): ProgressPhotoEntry? = progressPhotoEntries.firstOrNull { it.id == id }

    fun closestPhotoEntry(date: Date): ProgressPhotoEntry? =
        progressPhotoEntries.minByOrNull { kotlin.math.abs(it.date.time - date.time) }

    // MARK: - Weight Log

    val weightEntries: List<WeightEntry> = run {
        val unit = WeightUnit.defaultUnit
        val baseWeight = if (unit == WeightUnit.KG) 79.4 else 175.0
        (0 until 12).map { weekOffset ->
            val fluctuation = (-1.5..0.5).random()
            val trend = weekOffset * 0.4
            WeightEntry(
                id = "we$weekOffset",
                userId = "user1",
                weight = ((baseWeight + trend + fluctuation) * 10).toLong() / 10.0,
                unit = unit,
                date = weeksAgo(weekOffset)
            )
        }.reversed()
    }

    val latestWeight: WeightEntry? get() = weightEntries.lastOrNull()

    fun closestWeight(date: Date): WeightEntry? =
        weightEntries.minByOrNull { kotlin.math.abs(it.date.time - date.time) }

    val weightChartPoints: List<WeightChartPoint>
        get() = weightEntries.map { WeightChartPoint(id = it.id, date = it.date, weight = it.weight) }

    // MARK: - Measurements

    val measurementEntries: List<MeasurementEntry> = listOf(
        MeasurementEntry(
            id = "me1",
            userId = "user1",
            date = daysAgo(60),
            measurements = listOf(
                BodyMeasurementValue(id = "bm1", bodyPart = BodyPart.CHEST, value = 42.0, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm2", bodyPart = BodyPart.WAIST, value = 34.0, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm3", bodyPart = BodyPart.HIPS, value = 40.0, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm4", bodyPart = BodyPart.LEFT_BICEP, value = 14.0, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm5", bodyPart = BodyPart.RIGHT_BICEP, value = 14.5, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm6", bodyPart = BodyPart.LEFT_THIGH, value = 23.0, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm7", bodyPart = BodyPart.RIGHT_THIGH, value = 23.5, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm8", bodyPart = BodyPart.NECK, value = 15.5, unit = MeasurementUnit.INCHES)
            ),
            notes = "Starting measurements"
        ),
        MeasurementEntry(
            id = "me2",
            userId = "user1",
            date = daysAgo(30),
            measurements = listOf(
                BodyMeasurementValue(id = "bm9", bodyPart = BodyPart.CHEST, value = 42.5, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm10", bodyPart = BodyPart.WAIST, value = 33.0, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm11", bodyPart = BodyPart.HIPS, value = 39.5, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm12", bodyPart = BodyPart.LEFT_BICEP, value = 14.5, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm13", bodyPart = BodyPart.RIGHT_BICEP, value = 15.0, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm14", bodyPart = BodyPart.LEFT_THIGH, value = 23.5, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm15", bodyPart = BodyPart.RIGHT_THIGH, value = 24.0, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm16", bodyPart = BodyPart.NECK, value = 15.5, unit = MeasurementUnit.INCHES)
            ),
            notes = "Month 1 check-in"
        ),
        MeasurementEntry(
            id = "me3",
            userId = "user1",
            date = daysAgo(7),
            measurements = listOf(
                BodyMeasurementValue(id = "bm17", bodyPart = BodyPart.CHEST, value = 43.0, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm18", bodyPart = BodyPart.WAIST, value = 32.0, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm19", bodyPart = BodyPart.HIPS, value = 39.0, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm20", bodyPart = BodyPart.LEFT_BICEP, value = 15.0, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm21", bodyPart = BodyPart.RIGHT_BICEP, value = 15.5, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm22", bodyPart = BodyPart.LEFT_THIGH, value = 24.0, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm23", bodyPart = BodyPart.RIGHT_THIGH, value = 24.5, unit = MeasurementUnit.INCHES),
                BodyMeasurementValue(id = "bm24", bodyPart = BodyPart.NECK, value = 16.0, unit = MeasurementUnit.INCHES)
            ),
            notes = "Looking good — waist down 2 inches"
        )
    )

    fun closestMeasurements(date: Date): MeasurementEntry? =
        measurementEntries.minByOrNull { kotlin.math.abs(it.date.time - date.time) }

    // Helper functions
    private fun daysAgo(days: Int): Date {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -days)
        return calendar.time
    }

    private fun weeksAgo(weeks: Int): Date {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.WEEK_OF_YEAR, -weeks)
        return calendar.time
    }

    private fun ClosedFloatingPointRange<Double>.random(): Double {
        return start + Math.random() * (endInclusive - start)
    }
}
