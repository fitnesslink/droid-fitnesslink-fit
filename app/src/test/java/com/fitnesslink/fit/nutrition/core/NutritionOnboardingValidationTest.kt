package com.fitnesslink.fit.nutrition.core

import com.fitnesslink.fit.model.api.NutritionGoalTimeline
import com.fitnesslink.fit.model.api.NutritionPrimaryGoal
import com.fitnesslink.fit.model.api.NutritionSex
import com.fitnesslink.fit.ui.nutrition.TdeeMath
import com.fitnesslink.fit.viewmodel.NutritionProfileValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** Onboarding step validation (port of iOS `NutritionOnboardingViewModel` static rules). */
class NutritionOnboardingValidationTest {

    private val asOf: Date = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 6, 12, 0, 0) }.time
    private fun yearsAgo(years: Int): Date = Calendar.getInstance().apply { time = asOf; add(Calendar.YEAR, -years) }.time

    @Test fun `stats step needs sex first`() {
        assertEquals(
            "Select your sex — it changes the calorie equation.",
            NutritionProfileValidation.statsError(yearsAgo(30), null, 175.0, 80.0, 75.0, asOf)
        )
    }

    @Test fun `age bounds mirror the backend`() {
        assertNotNull(NutritionProfileValidation.statsError(yearsAgo(12), NutritionSex.MALE, 175.0, 80.0, 75.0, asOf))
        assertNull(NutritionProfileValidation.statsError(yearsAgo(13), NutritionSex.MALE, 175.0, 80.0, 75.0, asOf))
        assertNotNull(NutritionProfileValidation.statsError(yearsAgo(121), NutritionSex.MALE, 175.0, 80.0, 75.0, asOf))
        assertEquals("Date of birth must be in the past.", NutritionProfileValidation.statsError(asOf, NutritionSex.MALE, 175.0, 80.0, 75.0, asOf))
    }

    @Test fun `height and weight plausibility ranges`() {
        assertEquals("Enter your height.", NutritionProfileValidation.statsError(yearsAgo(30), NutritionSex.FEMALE, null, 80.0, 75.0, asOf))
        assertNotNull(NutritionProfileValidation.statsError(yearsAgo(30), NutritionSex.FEMALE, 49.9, 80.0, 75.0, asOf))
        assertNotNull(NutritionProfileValidation.statsError(yearsAgo(30), NutritionSex.FEMALE, 175.0, 19.0, 75.0, asOf))
        assertNotNull(NutritionProfileValidation.statsError(yearsAgo(30), NutritionSex.FEMALE, 175.0, 80.0, 501.0, asOf))
        assertNull(NutritionProfileValidation.statsError(yearsAgo(30), NutritionSex.FEMALE, 175.0, 80.0, 75.0, asOf))
    }

    @Test fun `goal direction blocks nonsense plans`() {
        assertNotNull(NutritionProfileValidation.goalDirectionError(NutritionPrimaryGoal.LOSE, 80.0, 85.0))
        assertNotNull(NutritionProfileValidation.goalDirectionError(NutritionPrimaryGoal.LOSE, 80.0, 80.0))
        assertNull(NutritionProfileValidation.goalDirectionError(NutritionPrimaryGoal.LOSE, 80.0, 75.0))
        assertNotNull(NutritionProfileValidation.goalDirectionError(NutritionPrimaryGoal.GAIN, 80.0, 75.0))
        assertTrue(NutritionProfileValidation.goalDirectionError(NutritionPrimaryGoal.BUILD_MUSCLE, 80.0, 75.0)!!.contains("build muscle"))
        assertNull(NutritionProfileValidation.goalDirectionError(NutritionPrimaryGoal.MAINTAIN, 80.0, 95.0))
        assertNull(NutritionProfileValidation.goalDirectionError(NutritionPrimaryGoal.RECOMP, 80.0, 60.0))
    }

    @Test fun `timeline needs a choice and a future custom date`() {
        assertEquals("Pick a timeline for your goal.", NutritionProfileValidation.timelineError(null, asOf, asOf))
        assertNull(NutritionProfileValidation.timelineError(NutritionGoalTimeline.NINETY_DAYS, asOf, asOf))
        assertNotNull(NutritionProfileValidation.timelineError(NutritionGoalTimeline.CUSTOM, asOf, asOf))
        val tomorrow = Calendar.getInstance().apply { time = asOf; add(Calendar.DAY_OF_YEAR, 1) }.time
        assertNull(NutritionProfileValidation.timelineError(NutritionGoalTimeline.CUSTOM, tomorrow, asOf))
    }

    @Test fun `imperial conversions`() {
        assertEquals(177.8, NutritionProfileValidation.centimeters("5", "10")!!, 0.01)
        assertEquals(152.4, NutritionProfileValidation.centimeters("5", "")!!, 0.01)
        assertNull(NutritionProfileValidation.centimeters("", "10"))
        assertEquals(68.04, NutritionProfileValidation.kilograms(150.0), 0.01)
        assertEquals(150.0, NutritionProfileValidation.pounds(NutritionProfileValidation.kilograms(150.0)), 0.0001)
        assertEquals(72.6, NutritionProfileValidation.round1(72.56), 0.0001)
    }

    @Test fun `custom allergy text splits on commas and trims`() {
        assertEquals(listOf("avocado", "cilantro"), NutritionProfileValidation.splitCustom(" avocado , cilantro,, "))
        assertTrue(NutritionProfileValidation.splitCustom("").isEmpty())
    }

    @Test fun `local TDEE fallback uses Mifflin-St Jeor`() {
        // 180 lb male, 180 cm, 30 y, moderately active.
        val bmr = NutritionProfileValidation.kilograms(180.0) * 10 + 6.25 * 180 - 5 * 30 + 5
        assertEquals((bmr * 1.55).toInt(), TdeeMath.tdee(180.0, 180.0, 30.0, isMale = true, multiplier = 1.55))
        assertEquals(bmr - 166, TdeeMath.bmr(NutritionProfileValidation.kilograms(180.0), 180.0, 30.0, isMale = false), 0.001)
    }
}
