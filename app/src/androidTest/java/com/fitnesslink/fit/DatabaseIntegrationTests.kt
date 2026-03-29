package com.fitnesslink.fit

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnesslink.fit.model.*
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.DatabaseSeeder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseIntegrationTests {

    @Before
    fun setup() {
        DatabaseManager.initializeForTesting()
    }

    // Seeder Tests

    @Test
    fun testSeedCreatesExpectedObjectCounts() {
        DatabaseSeeder.seedIfNeeded()
        assertNotNull(DatabaseManager.user())
        assertEquals(5, DatabaseManager.allPrograms().size)
        assertEquals(6, DatabaseManager.allWorkouts().size)
        assertEquals(10, DatabaseManager.allFoodEntries().size)
        assertEquals(16, DatabaseManager.weeklyMealSlots().size)
        assertEquals(5, DatabaseManager.personalizations().size)
        assertEquals(5, DatabaseManager.dashboards().size)
        assertEquals(3, DatabaseManager.calendarContent().size)
        assertEquals(10, DatabaseManager.profileMenuItems().size)
    }

    @Test
    fun testSeedDoesNotRunTwice() {
        DatabaseSeeder.seedIfNeeded()
        DatabaseSeeder.seedIfNeeded()
        assertEquals(6, DatabaseManager.allWorkouts().size)
    }

    // CRUD Tests

    @Test
    fun testFoodEntryWriteReadDelete() = runBlocking {
        val entry = FoodEntry(id = "test1", name = "Test Food", calories = 200, mealType = MealType.LUNCH)
        DatabaseManager.saveFoodEntry(entry)

        val fetched = DatabaseManager.allFoodEntries().find { it.id == "test1" }
        assertNotNull(fetched)
        assertEquals("Test Food", fetched?.name)
        assertEquals(200, fetched?.calories)

        DatabaseManager.deleteFoodEntry("test1")
        assertNull(DatabaseManager.allFoodEntries().find { it.id == "test1" })
    }

    @Test
    fun testMealSlotRoundTrip() = runBlocking {
        val slot = MealSlot(
            id = "slot1", day = DayOfWeek.WEDNESDAY, mealType = MealType.DINNER,
            recipeName = "Test Meal", calories = 500, protein = 40.0, fat = 20.0, carbs = 35.0,
            ingredients = listOf(GroceryItem(id = "gi1", name = "Chicken", quantity = "6", unit = "oz", category = GroceryCategory.PROTEIN))
        )
        DatabaseManager.saveMealSlot(slot)

        val fetched = DatabaseManager.weeklyMealSlots().find { it.id == "slot1" }
        assertNotNull(fetched)
        assertEquals(DayOfWeek.WEDNESDAY, fetched?.day)
        assertEquals("Test Meal", fetched?.recipeName)
        assertEquals(1, fetched?.ingredients?.size)
        assertEquals("Chicken", fetched?.ingredients?.first()?.name)
    }

    @Test
    fun testGroceryItemToggle() = runBlocking {
        val item = GroceryItem(id = "g1", name = "Eggs", isChecked = false)
        DatabaseManager.saveGroceryItems(listOf(item))

        assertFalse(DatabaseManager.groceryItems().first { it.id == "g1" }.isChecked)

        DatabaseManager.toggleGroceryItem("g1")
        assertTrue(DatabaseManager.groceryItems().first { it.id == "g1" }.isChecked)
    }

    @Test
    fun testPersonalizationWriteAndUpdate() = runBlocking {
        val p = Personalization(
            id = "p1", name = "Test Question", singleSelection = true,
            options = listOf(
                PersonalizationItem(id = "o1", text = "Option A"),
                PersonalizationItem(id = "o2", text = "Option B")
            )
        )
        DatabaseManager.savePersonalizations(listOf(p))

        val fetched = DatabaseManager.personalizations().find { it.id == "p1" }
        assertEquals(2, fetched?.options?.size)
        assertFalse(fetched!!.options[0].selected)

        val updated = p.copy(options = listOf(
            PersonalizationItem(id = "o1", text = "Option A", selected = true),
            PersonalizationItem(id = "o2", text = "Option B")
        ))
        DatabaseManager.savePersonalizations(listOf(updated))

        val refetched = DatabaseManager.personalizations().find { it.id == "p1" }
        assertTrue(refetched!!.options[0].selected)
    }

    // ViewModel Integration Tests

    @Test
    fun testSeededNutritionGoalValues() {
        DatabaseSeeder.seedIfNeeded()
        val goal = DatabaseManager.nutritionGoal()
        assertEquals(2000, goal.calorieGoal)
        assertEquals(150, goal.proteinTarget)
        assertEquals(65, goal.fatTarget)
        assertEquals(250, goal.carbsTarget)
    }

    @Test
    fun testSeededBarcodeProductLookup() {
        DatabaseSeeder.seedIfNeeded()
        val product = DatabaseManager.barcodeProduct("049000006346")
        assertNotNull(product)
        assertEquals("Coca-Cola Classic", product?.name)
    }
}
