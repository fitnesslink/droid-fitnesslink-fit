package com.fitnesslink.fit.data

import com.fitnesslink.fit.model.*
import com.fitnesslink.fit.model.api.*
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
        ProgramList(id = "p1", name = "Strength Builder", time = "8 weeks", isFavorite = true),
        ProgramList(id = "p2", name = "Lean & Tone", time = "6 weeks", isFavorite = false),
        ProgramList(id = "p3", name = "HIIT Cardio Blast", time = "4 weeks", isFavorite = true),
        ProgramList(id = "p4", name = "Full Body Transform", time = "12 weeks", isFavorite = false),
        ProgramList(id = "p5", name = "Core & Flexibility", time = "6 weeks", isFavorite = false)
    )

    fun programDetail(id: String): Program {
        val name = programs.firstOrNull { it.id == id }?.name ?: "Strength Builder"
        return Program(
            id = id,
            name = name,
            time = "8 weeks",
            location = "Gym",
            trainingLevel = "Intermediate",
            description = "Build functional strength with progressive overload across compound movements. Each week increases intensity to ensure continuous improvement."
        )
    }

    val workouts: List<WorkoutList> = listOf(
        WorkoutList(id = "w1", name = "Upper Body Push", time = "45 min", isFavorite = true),
        WorkoutList(id = "w2", name = "Lower Body Strength", time = "50 min", isFavorite = false),
        WorkoutList(id = "w3", name = "Full Body HIIT", time = "30 min", isFavorite = true),
        WorkoutList(id = "w4", name = "Core & Abs", time = "25 min", isFavorite = false),
        WorkoutList(id = "w5", name = "Cardio Endurance", time = "40 min", isFavorite = false),
        WorkoutList(id = "w6", name = "Back & Biceps", time = "45 min", isFavorite = true)
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
            time = "45 min",
            location = "Gym",
            trainingLevel = "Intermediate",
            description = "Target your chest, shoulders, and triceps with a mix of compound and isolation exercises.",
            phases = listOf(warmup, main, cooldown)
        )
    }

    val catalogPrograms: List<CatalogItem> = listOf(
        CatalogItem(id = "cp1", title = "Strength Builder", kind = CatalogItem.Kind.PROGRAM, caption = "8 weeks"),
        CatalogItem(id = "cp2", title = "Lean & Tone", kind = CatalogItem.Kind.PROGRAM, caption = "6 weeks"),
        CatalogItem(id = "cp3", title = "HIIT Cardio Blast", kind = CatalogItem.Kind.PROGRAM, caption = "4 weeks")
    )

    val catalogWorkouts: List<CatalogItem> = listOf(
        CatalogItem(id = "cw1", title = "Upper Body Push", kind = CatalogItem.Kind.WORKOUT, caption = "45 min"),
        CatalogItem(id = "cw2", title = "Lower Body Strength", kind = CatalogItem.Kind.WORKOUT, caption = "50 min"),
        CatalogItem(id = "cw3", title = "Full Body HIIT", kind = CatalogItem.Kind.WORKOUT, caption = "30 min")
    )

    val myWorkouts: List<CatalogItem> = listOf(
        CatalogItem(id = "mw1", title = "Morning Routine", kind = CatalogItem.Kind.WORKOUT, caption = "20 min"),
        CatalogItem(id = "mw2", title = "Evening Stretch", kind = CatalogItem.Kind.WORKOUT, caption = "15 min")
    )

    val myPrograms: List<CatalogItem> = listOf(
        CatalogItem(id = "mp1", title = "12-Week Strength Builder", kind = CatalogItem.Kind.PROGRAM, caption = "12 wk"),
        CatalogItem(id = "mp2", title = "Couch to 5K", kind = CatalogItem.Kind.PROGRAM, caption = "9 wk")
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
        ProfileMenu(id = 3, text = "Nutrition Report"),
        ProfileMenu(id = 4, text = "Preferences"),
        ProfileMenu(id = 5, text = "Billing"),
        ProfileMenu(id = 6, text = "Goals"),
        ProfileMenu(id = 7, text = "Measurements"),
        ProfileMenu(id = 8, text = "Photos"),
        ProfileMenu(id = 9, text = "Weight"),
        ProfileMenu(id = 10, text = "Workout Report"),
        ProfileMenu(id = 11, text = "Achievements"),
        ProfileMenu(id = 12, text = "Notifications")
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

    // MARK: - API-Aligned Entities

    val contentStatuses: List<ContentStatus> = listOf(
        ContentStatus(id = UUID.fromString("00000000-0000-0000-0000-000000000001"), name = "Draft"),
        ContentStatus(id = UUID.fromString("00000000-0000-0000-0000-000000000002"), name = "Approved"),
        ContentStatus(id = UUID.fromString("00000000-0000-0000-0000-000000000003"), name = "Published"),
    )

    val publishedStatusId: EntityID = contentStatuses[2].id

    val mockContributor = Contributor(
        id = UUID.fromString("10000000-0000-0000-0000-000000000001"),
        userId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
        isApproved = true
    )

    val movements: List<Movement> = listOf(
        Movement(id = UUID.fromString("30000000-0000-0000-0000-000000000001"), name = "Arm Circles", description = "Loosen up shoulder joints", statusId = publishedStatusId, contributorId = mockContributor.id),
        Movement(id = UUID.fromString("30000000-0000-0000-0000-000000000002"), name = "Jumping Jacks", description = "Full body warm-up", statusId = publishedStatusId, contributorId = mockContributor.id),
        Movement(id = UUID.fromString("30000000-0000-0000-0000-000000000003"), name = "Bench Press", description = "Primary chest compound movement", statusId = publishedStatusId, contributorId = mockContributor.id),
        Movement(id = UUID.fromString("30000000-0000-0000-0000-000000000004"), name = "Overhead Press", description = "Shoulder compound movement", statusId = publishedStatusId, contributorId = mockContributor.id),
        Movement(id = UUID.fromString("30000000-0000-0000-0000-000000000005"), name = "Incline Dumbbell Fly", description = "Upper chest isolation", statusId = publishedStatusId, contributorId = mockContributor.id),
        Movement(id = UUID.fromString("30000000-0000-0000-0000-000000000006"), name = "Tricep Pushdown", description = "Tricep isolation", statusId = publishedStatusId, contributorId = mockContributor.id),
        Movement(id = UUID.fromString("30000000-0000-0000-0000-000000000007"), name = "Chest Stretch", description = "Cool down stretch for pecs", statusId = publishedStatusId, contributorId = mockContributor.id),
    )

    val rpeScale: List<Rpe> = listOf(
        Rpe(id = UUID.fromString("40000000-0000-0000-0000-000000000001"), name = "Very Light", fromValue = 1, toValue = 2),
        Rpe(id = UUID.fromString("40000000-0000-0000-0000-000000000002"), name = "Light", fromValue = 3, toValue = 4),
        Rpe(id = UUID.fromString("40000000-0000-0000-0000-000000000003"), name = "Moderate", fromValue = 5, toValue = 6),
        Rpe(id = UUID.fromString("40000000-0000-0000-0000-000000000004"), name = "Hard", fromValue = 7, toValue = 8),
        Rpe(id = UUID.fromString("40000000-0000-0000-0000-000000000005"), name = "Max Effort", fromValue = 9, toValue = 10),
    )

    val advancedMovements: List<AdvancedMovement> = listOf(
        AdvancedMovement(id = UUID.fromString("50000000-0000-0000-0000-000000000001"), name = "Circuit"),
        AdvancedMovement(id = UUID.fromString("50000000-0000-0000-0000-000000000002"), name = "Superset"),
        AdvancedMovement(id = UUID.fromString("50000000-0000-0000-0000-000000000003"), name = "Interval"),
    )

    val mockProgramSchedules: List<ProgramSchedule> = listOf(
        ProgramSchedule(id = UUID.randomUUID(), programId = UUID.randomUUID(), workoutId = UUID.randomUUID(), weekNumber = 1, dayNumber = 1),
        ProgramSchedule(id = UUID.randomUUID(), programId = UUID.randomUUID(), workoutId = UUID.randomUUID(), weekNumber = 1, dayNumber = 3),
        ProgramSchedule(id = UUID.randomUUID(), programId = UUID.randomUUID(), workoutId = UUID.randomUUID(), weekNumber = 1, dayNumber = 5),
    )

    val mockUser = FLUser(
        id = UUID.fromString("20000000-0000-0000-0000-000000000001"),
        firstName = "Demo",
        lastName = "User",
        email = "demo@fitnesslink.app",
        firebaseId = "mock-firebase-id",
        username = "demouser"
    )

    val mockUserPreference = UserPreference(
        id = UUID.randomUUID(),
        userId = mockUser.id,
        language = "en",
        timezone = "America/New_York",
        workoutSessionType = WorkoutSessionType.STANDARD
    )

    // MARK: - Historical Food Entries

    private data class MockFood(val name: String, val cal: Int, val p: Double, val f: Double, val c: Double, val unit: String, val meal: MealType)
    private val bFoods = listOf(
        MockFood("Oatmeal with Berries", 320, 12.0, 8.0, 52.0, "bowl", MealType.BREAKFAST),
        MockFood("Eggs (Scrambled)", 182, 12.0, 13.0, 2.0, "large", MealType.BREAKFAST),
        MockFood("Protein Pancakes", 290, 25.0, 8.0, 32.0, "pancakes", MealType.BREAKFAST),
        MockFood("Avocado Toast", 310, 8.0, 18.0, 30.0, "slices", MealType.BREAKFAST),
        MockFood("Smoothie Bowl", 340, 15.0, 10.0, 50.0, "bowl", MealType.BREAKFAST),
    )
    private val lFoods = listOf(
        MockFood("Grilled Chicken Breast", 284, 53.0, 6.0, 0.0, "oz", MealType.LUNCH),
        MockFood("Turkey Wrap", 380, 28.0, 14.0, 38.0, "wrap", MealType.LUNCH),
        MockFood("Mixed Greens Salad", 120, 3.0, 7.0, 12.0, "bowl", MealType.LUNCH),
        MockFood("Brown Rice", 216, 5.0, 1.8, 45.0, "cup", MealType.LUNCH),
        MockFood("Quinoa Bowl", 350, 14.0, 12.0, 48.0, "bowl", MealType.LUNCH),
        MockFood("Tuna Sandwich", 420, 32.0, 16.0, 38.0, "sandwich", MealType.LUNCH),
    )
    private val dFoods = listOf(
        MockFood("Salmon Fillet", 367, 34.0, 22.0, 0.0, "oz", MealType.DINNER),
        MockFood("Grilled Steak", 450, 42.0, 28.0, 0.0, "oz", MealType.DINNER),
        MockFood("Chicken Stir Fry", 380, 35.0, 12.0, 32.0, "plate", MealType.DINNER),
        MockFood("Sweet Potato", 103, 2.3, 0.1, 24.0, "medium", MealType.DINNER),
        MockFood("Pasta with Meat Sauce", 520, 25.0, 18.0, 62.0, "plate", MealType.DINNER),
        MockFood("Grilled Tilapia", 280, 36.0, 8.0, 0.0, "oz", MealType.DINNER),
    )
    private val sFoods = listOf(
        MockFood("Greek Yogurt", 100, 17.0, 0.7, 6.0, "cup", MealType.SNACK),
        MockFood("Almonds", 164, 6.0, 14.0, 6.0, "oz", MealType.SNACK),
        MockFood("Protein Shake", 180, 30.0, 3.0, 8.0, "shake", MealType.SNACK),
        MockFood("Banana", 105, 1.3, 0.4, 27.0, "medium", MealType.SNACK),
        MockFood("Apple with Peanut Butter", 200, 5.0, 12.0, 24.0, "apple", MealType.SNACK),
        MockFood("Trail Mix", 175, 5.0, 11.0, 16.0, "oz", MealType.SNACK),
    )
    private val skipDays = setOf(5, 14, 27, 35)

    val historicalFoodEntries: List<FoodEntry> by lazy {
        val now = System.currentTimeMillis()
        val dayMs = 86400000L
        buildList {
            for (daysAgo in 1..40) {
                if (daysAgo in skipDays) continue
                val dayStart = now - daysAgo * dayMs
                val idx = daysAgo
                val bf = bFoods[idx % bFoods.size]
                add(FoodEntry("hfe-$daysAgo-b", bf.name, bf.cal, bf.p, bf.f, bf.c, 1.0, bf.unit, MealType.BREAKFAST, Date(dayStart + 8 * 3600000)))
                val ln = lFoods[idx % lFoods.size]
                add(FoodEntry("hfe-$daysAgo-l", ln.name, ln.cal, ln.p, ln.f, ln.c, 1.0, ln.unit, MealType.LUNCH, Date(dayStart + 12 * 3600000 + 1800000)))
                val dn = dFoods[idx % dFoods.size]
                add(FoodEntry("hfe-$daysAgo-d", dn.name, dn.cal, dn.p, dn.f, dn.c, 1.0, dn.unit, MealType.DINNER, Date(dayStart + 18 * 3600000 + 1800000)))
                if (daysAgo % 4 != 0) {
                    val sn = sFoods[idx % sFoods.size]
                    add(FoodEntry("hfe-$daysAgo-s", sn.name, sn.cal, sn.p, sn.f, sn.c, 1.0, sn.unit, MealType.SNACK, Date(dayStart + 15 * 3600000)))
                }
                if (daysAgo % 3 == 0) {
                    val sn2 = sFoods[(idx + 2) % sFoods.size]
                    add(FoodEntry("hfe-$daysAgo-s2", sn2.name, sn2.cal, sn2.p, sn2.f, sn2.c, 1.0, sn2.unit, MealType.SNACK, Date(dayStart + 20 * 3600000)))
                }
            }
        }
    }

    // MARK: - Workout Sessions (for Report)

    data class MockSession(
        val id: String, val workoutId: String, val workoutName: String,
        val daysAgo: Int, val durationSeconds: Int, val isCompleted: Boolean,
        val exerciseCount: Int, val totalSets: Int, val totalReps: Int,
        val totalWeightLifted: Double, val totalCaloriesBurned: Double,
        val rpeValue: Double?
    )

    data class MockHistoryEntry(
        val sessionId: String, val taskName: String, val workoutId: String,
        val reps: Int, val setNumber: Int, val weightLifted: Double, val daysAgo: Int
    )

    val mockSessions = listOf(
        MockSession("s1", "w1", "Upper Body Strength", 1, 2700, true, 8, 24, 192, 4800.0, 320.0, 7.5),
        MockSession("s2", "w2", "Lower Body Power", 2, 3200, true, 7, 21, 168, 6300.0, 410.0, 8.0),
        MockSession("s3", "w3", "HIIT Cardio Blast", 3, 1800, true, 10, 20, 200, 0.0, 520.0, 9.0),
        MockSession("s4", "w4", "Core & Flexibility", 4, 2400, true, 9, 18, 144, 600.0, 240.0, 5.5),
        MockSession("s5", "w5", "Full Body Circuit", 6, 3600, true, 12, 36, 288, 5400.0, 480.0, 8.5),
        MockSession("s6", "w1", "Upper Body Strength", 7, 2850, true, 8, 24, 192, 5000.0, 330.0, 7.0),
        MockSession("s7", "w6", "Push Day", 8, 2500, true, 6, 18, 144, 3600.0, 290.0, 7.5),
        MockSession("s8", "w2", "Lower Body Power", 9, 3100, true, 7, 21, 168, 6500.0, 400.0, 8.0),
        MockSession("s9", "w3", "HIIT Cardio Blast", 10, 1500, false, 6, 12, 120, 0.0, 310.0, null),
        MockSession("s10", "w4", "Core & Flexibility", 11, 2300, true, 9, 18, 144, 650.0, 230.0, 6.0),
        MockSession("s11", "w1", "Upper Body Strength", 12, 2900, true, 8, 24, 192, 5200.0, 340.0, 7.5),
        MockSession("s12", "w5", "Full Body Circuit", 13, 3500, true, 12, 36, 288, 5600.0, 490.0, 8.0),
        MockSession("s13", "w2", "Lower Body Power", 16, 3000, true, 7, 21, 168, 6100.0, 390.0, 7.5),
        MockSession("s14", "w6", "Push Day", 18, 2600, true, 6, 18, 144, 3800.0, 300.0, 7.0),
        MockSession("s15", "w1", "Upper Body Strength", 20, 2800, true, 8, 24, 192, 4600.0, 310.0, 7.0),
        MockSession("s16", "w3", "HIIT Cardio Blast", 22, 1200, false, 4, 8, 80, 0.0, 200.0, null),
        MockSession("s17", "w4", "Core & Flexibility", 24, 2350, true, 9, 18, 144, 700.0, 235.0, 5.0),
        MockSession("s18", "w5", "Full Body Circuit", 26, 3400, true, 12, 36, 288, 5200.0, 470.0, 8.5),
        MockSession("s19", "w2", "Lower Body Power", 28, 3050, true, 7, 21, 168, 5900.0, 380.0, 8.0),
        MockSession("s20", "w1", "Upper Body Strength", 30, 2750, true, 8, 24, 192, 4500.0, 305.0, 6.5),
        MockSession("s21", "w6", "Push Day", 33, 2400, true, 6, 18, 144, 3400.0, 280.0, 6.5),
        MockSession("s22", "w3", "HIIT Cardio Blast", 36, 1850, true, 10, 20, 200, 0.0, 530.0, 9.5),
        MockSession("s23", "w2", "Lower Body Power", 39, 3150, true, 7, 21, 168, 5800.0, 395.0, 7.5),
        MockSession("s24", "w5", "Full Body Circuit", 42, 3300, false, 8, 24, 192, 3600.0, 350.0, null),
    )

    val mockSessionHistory: List<MockHistoryEntry> by lazy {
        val exercises = listOf(
            Triple("s1", "w1", 1) to listOf(Triple("Bench Press", 10, 135.0), Triple("Overhead Press", 8, 85.0), Triple("Dumbbell Row", 10, 50.0), Triple("Lateral Raise", 12, 20.0)),
            Triple("s2", "w2", 2) to listOf(Triple("Squat", 8, 225.0), Triple("Romanian Deadlift", 10, 185.0), Triple("Leg Press", 12, 300.0), Triple("Calf Raise", 15, 100.0)),
            Triple("s5", "w5", 6) to listOf(Triple("Squat", 10, 185.0), Triple("Bench Press", 10, 135.0), Triple("Deadlift", 8, 225.0)),
            Triple("s6", "w1", 7) to listOf(Triple("Bench Press", 10, 140.0), Triple("Overhead Press", 8, 90.0), Triple("Dumbbell Row", 10, 55.0)),
            Triple("s7", "w6", 8) to listOf(Triple("Bench Press", 10, 145.0), Triple("Incline Press", 8, 115.0), Triple("Tricep Pushdown", 12, 50.0)),
            Triple("s8", "w2", 9) to listOf(Triple("Squat", 8, 235.0), Triple("Romanian Deadlift", 10, 195.0), Triple("Leg Press", 12, 315.0)),
            Triple("s11", "w1", 12) to listOf(Triple("Bench Press", 10, 140.0), Triple("Overhead Press", 8, 85.0), Triple("Dumbbell Row", 10, 55.0)),
            Triple("s12", "w5", 13) to listOf(Triple("Squat", 10, 195.0), Triple("Bench Press", 10, 140.0), Triple("Deadlift", 8, 235.0)),
            Triple("s13", "w2", 16) to listOf(Triple("Squat", 8, 225.0), Triple("Romanian Deadlift", 10, 185.0)),
            Triple("s14", "w6", 18) to listOf(Triple("Bench Press", 10, 140.0), Triple("Incline Press", 8, 110.0)),
            Triple("s15", "w1", 20) to listOf(Triple("Bench Press", 10, 130.0), Triple("Overhead Press", 8, 80.0)),
            Triple("s18", "w5", 26) to listOf(Triple("Squat", 10, 185.0), Triple("Bench Press", 10, 130.0), Triple("Deadlift", 8, 220.0)),
            Triple("s19", "w2", 28) to listOf(Triple("Squat", 8, 215.0), Triple("Romanian Deadlift", 10, 175.0)),
            Triple("s20", "w1", 30) to listOf(Triple("Bench Press", 10, 125.0), Triple("Overhead Press", 8, 75.0)),
            Triple("s23", "w2", 39) to listOf(Triple("Squat", 8, 210.0), Triple("Romanian Deadlift", 10, 170.0)),
        )
        buildList {
            exercises.forEach { (session, tasks) ->
                tasks.forEach { (name, reps, weight) ->
                    if (weight > 0) {
                        for (setNum in 1..3) {
                            add(MockHistoryEntry(session.first, name, session.second, reps, setNum, weight, session.third))
                        }
                    }
                }
            }
        }
    }
}
