package com.fitnesslink.fit.nutrition.plan

import com.fitnesslink.fit.model.MealPlanCopyRules
import com.fitnesslink.fit.model.MealSlot
import com.fitnesslink.fit.model.MealSlotFood
import com.fitnesslink.fit.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealPlanCopyRulesTests {

    private fun slot(date: String, meal: MealType, name: String = "x", foods: List<MealSlotFood> = emptyList()) =
        MealSlot(date = date, mealType = meal, recipeName = name, calories = 500, foods = foods, rowVersion = "abc")

    // Source week: Sun 2026-09-27 … Sat 2026-10-03. Target week: Sun 2026-10-04 … Sat 2026-10-10.
    private val source = listOf(
        slot("2026-09-28", MealType.BREAKFAST, "oats", foods = listOf(MealSlotFood(name = "Oats", servings = 2.0, calories = 150.0))),
        slot("2026-09-28", MealType.LUNCH, "salad"),
        slot("2026-09-30", MealType.DINNER, "pasta")
    )

    @Test
    fun `empty source copies nothing`() {
        val plan = MealPlanCopyRules.planWeekCopy(emptyList(), listOf(slot("2026-10-05", MealType.LUNCH)), overwrite = true)
        assertEquals(0, plan.copiedCount); assertEquals(0, plan.overwrittenCount); assertEquals(0, plan.skippedCount)
    }

    @Test
    fun `copy into an empty week shifts every slot forward seven days with fresh ids`() {
        val plan = MealPlanCopyRules.planWeekCopy(source, emptyList(), overwrite = false)
        assertEquals(3, plan.copiedCount)
        assertEquals(listOf("2026-10-05", "2026-10-05", "2026-10-07"), plan.create.map { it.date })
        plan.create.zip(source).forEach { (copy, original) ->
            assertNotEquals(original.id, copy.id)
            assertEquals("", copy.rowVersion)
            assertEquals(original.mealType, copy.mealType)
            assertEquals(original.foods, copy.foods) // itemized foods ride along
        }
        assertTrue(plan.remove.isEmpty())
    }

    @Test
    fun `keep mine skips colliding weekday and meal, adds the rest`() {
        val mine = slot("2026-10-05", MealType.BREAKFAST, "my eggs") // Monday breakfast clashes
        val plan = MealPlanCopyRules.planWeekCopy(source, listOf(mine), overwrite = false)
        assertEquals(2, plan.copiedCount)
        assertEquals(1, plan.skippedCount)
        assertEquals(0, plan.overwrittenCount)
        assertTrue(plan.create.none { it.date == "2026-10-05" && it.mealType == MealType.BREAKFAST })
    }

    @Test
    fun `overwrite removes only colliding targets and keeps non-colliding ones`() {
        val mine = slot("2026-10-05", MealType.BREAKFAST, "my eggs")
        val untouched = slot("2026-10-09", MealType.SNACK, "friday snack")
        val plan = MealPlanCopyRules.planWeekCopy(source, listOf(mine, untouched), overwrite = true)
        assertEquals(3, plan.copiedCount)
        assertEquals(listOf(mine), plan.remove)
        assertEquals(0, plan.skippedCount)
    }

    // MARK: - Apply-week (AI ideas) collision rules

    private fun idea(day: String, meal: MealType?) =
        MealPlanCopyRules.PlannedIdea(day, meal, slot(day, meal ?: MealType.BREAKFAST, "idea"))

    @Test
    fun `merge skips occupied day and meal`() {
        val existing = listOf(slot("2026-10-05", MealType.LUNCH))
        val plan = MealPlanCopyRules.planIdeasApply(existing, listOf(idea("2026-10-05", MealType.LUNCH), idea("2026-10-05", MealType.DINNER)), replace = false)
        assertEquals(1, plan.create.size); assertEquals(1, plan.skippedCount); assertTrue(plan.remove.isEmpty())
        assertEquals(MealType.DINNER, plan.create[0].mealType)
    }

    @Test
    fun `replace clears the week first`() {
        val existing = listOf(slot("2026-10-05", MealType.LUNCH), slot("2026-10-06", MealType.BREAKFAST))
        val plan = MealPlanCopyRules.planIdeasApply(existing, listOf(idea("2026-10-05", MealType.LUNCH)), replace = true)
        assertEquals(existing, plan.remove)
        assertEquals(1, plan.create.size); assertEquals(0, plan.skippedCount)
    }

    @Test
    fun `duplicate ideas for one slot and unresolvable ideas are skipped`() {
        val plan = MealPlanCopyRules.planIdeasApply(emptyList(), listOf(idea("2026-10-05", MealType.LUNCH), idea("2026-10-05", MealType.LUNCH), idea("2026-10-05", null), idea("not-a-date", MealType.SNACK)), replace = false)
        assertEquals(1, plan.create.size); assertEquals(3, plan.skippedCount)
    }
}
