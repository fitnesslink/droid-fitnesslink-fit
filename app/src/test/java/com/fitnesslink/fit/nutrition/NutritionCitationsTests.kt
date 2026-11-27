package com.fitnesslink.fit.nutrition

import com.fitnesslink.fit.model.HydrationGoal
import com.fitnesslink.fit.model.NutritionCitation
import com.fitnesslink.fit.model.NutritionMethod
import com.fitnesslink.fit.model.api.Micronutrient
import com.fitnesslink.fit.model.api.NutritionActivityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every calculated number must keep a source a user can open, and a number the
 * user chose must not imply one (iOS `NutritionCitationsTests`).
 */
class NutritionCitationsTests {

    private val calculatedKeys = listOf(
        "why.bmr.mifflinStJeor",
        "why.maintenance.activityMultiplier",
        "why.target.deficit",
        "why.target.surplus",
        "why.target.maintenance",
        "why.macro.protein",
        "why.macro.carbs",
        "why.macro.fat",
        "why.weeklyRate.projected",
        "why.completion.projected",
    )

    private fun method(title: String) = NutritionMethod.all.first { it.title == title }

    @Test fun everyCalculatedKeyHasACitation() {
        calculatedKeys.forEach { assertFalse("$it has no citation", NutritionCitation.forWhyKey(it).isEmpty()) }
    }

    @Test fun keysWithoutACalculationHaveNoCitation() {
        assertTrue(NutritionCitation.forWhyKey("why.target.manualOverride").isEmpty())
        assertTrue(NutritionCitation.forWhyKey("why.completion.notApplicable").isEmpty())
        assertTrue(NutritionCitation.forWhyKey("why.unknown").isEmpty())
    }

    @Test fun everyMethodHasHttpsCitations() {
        assertFalse(NutritionMethod.all.isEmpty())
        NutritionMethod.all.forEach { method ->
            assertFalse("${method.title} has no citation", method.citations.isEmpty())
            method.citations.forEach { assertTrue(it.name, it.url.startsWith("https://")) }
        }
    }

    /** The Sources screen states these numbers, so they must match the app's constants. */
    @Test fun sourcesCopyMatchesAppConstants() {
        val maintenance = method("Maintenance calories")
        NutritionActivityLevel.entries.forEach {
            assertTrue("$it multiplier missing", maintenance.explanation.contains("${it.multiplier}"))
        }

        val micros = method("Micronutrient targets").explanation
        assertEquals(28.0, Micronutrient.FIBER.defaultTarget!!, 0.0)
        assertTrue(micros.contains("fiber 28 g"))
        assertEquals(2300.0, Micronutrient.SODIUM.defaultTarget!!, 0.0)
        assertTrue(micros.contains("sodium 2,300 mg"))
        assertEquals(20.0, Micronutrient.SATURATED_FAT.defaultTarget!!, 0.0)
        assertTrue(micros.contains("saturated fat 20 g"))
        assertEquals(300.0, Micronutrient.CHOLESTEROL.defaultTarget!!, 0.0)
        assertTrue(micros.contains("cholesterol 300 mg"))
        assertNull(Micronutrient.SUGARS.defaultTarget)

        assertEquals(64.0, HydrationGoal().dailyGoal, 0.0)
        assertTrue(method("Water goal").explanation.contains("64 fl oz"))
    }
}
