package com.fitnesslink.fit.onboarding

import com.fitnesslink.fit.model.onboarding.*
import com.fitnesslink.fit.network.ApiClient
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/** Port of iOS `OnboardingFlowTests`: branch rules, numbering, helpers, pace/timeline, request mapping, wire format. */
class OnboardingRulesTest {

    private fun next(step: OnboardingStep, a: OnboardingAnswers, mode: OnboardingMode = OnboardingMode.FULL) =
        OnboardingRules.nextStep(step, a, mode)

    @Test fun nutritionGoalBranches() {
        assertEquals(OnboardingStep.WEIGHT_GOAL, next(OnboardingStep.NUTRITION_GOAL, OnboardingAnswers(nutritionGoal = NutritionGoalChoice.LOSE_WEIGHT)))
        assertEquals(OnboardingStep.MUSCLE_FOCUS, next(OnboardingStep.NUTRITION_GOAL, OnboardingAnswers(nutritionGoal = NutritionGoalChoice.BUILD_MUSCLE_GAIN_WEIGHT)))
        assertEquals(OnboardingStep.RECOMP_FOCUS, next(OnboardingStep.NUTRITION_GOAL, OnboardingAnswers(nutritionGoal = NutritionGoalChoice.BODY_RECOMPOSITION)))
        assertEquals(OnboardingStep.DIETARY, next(OnboardingStep.NUTRITION_GOAL, OnboardingAnswers(nutritionGoal = NutritionGoalChoice.MAINTAIN_WEIGHT)))
        assertEquals(OnboardingStep.DIETARY, next(OnboardingStep.NUTRITION_GOAL, OnboardingAnswers()))
        assertEquals(OnboardingStep.WEIGHT_GOAL, next(OnboardingStep.MUSCLE_FOCUS, OnboardingAnswers(muscleFocus = MuscleFocus.GAIN_WEIGHT)))
        assertEquals(OnboardingStep.DIETARY, next(OnboardingStep.MUSCLE_FOCUS, OnboardingAnswers(muscleFocus = MuscleFocus.NO_WEIGHT_GOAL)))
        assertEquals(OnboardingStep.WEIGHT_GOAL, next(OnboardingStep.RECOMP_FOCUS, OnboardingAnswers(recompFocus = RecompFocus.TARGET_WEIGHT)))
        assertEquals(OnboardingStep.DIETARY, next(OnboardingStep.RECOMP_FOCUS, OnboardingAnswers(recompFocus = RecompFocus.REDUCE_FAT)))
    }

    @Test fun editModeEndsAfterInterests() {
        assertNull(next(OnboardingStep.INTERESTS, OnboardingAnswers(), OnboardingMode.EDIT_TRAINING))
        assertEquals(OnboardingStep.ABOUT_YOU, next(OnboardingStep.INTERESTS, OnboardingAnswers()))
    }

    @Test fun replayRebuildsBackStack() {
        val a = OnboardingAnswers(nutritionGoal = NutritionGoalChoice.BUILD_MUSCLE_GAIN_WEIGHT, muscleFocus = MuscleFocus.GAIN_WEIGHT)
        val path = OnboardingRules.replayPath(OnboardingStep.WEIGHT_GOAL, a)
        assertEquals(OnboardingStep.WELCOME, path.first())
        assertEquals(OnboardingStep.WEIGHT_GOAL, path.last())
        assertTrue(OnboardingStep.MUSCLE_FOCUS in path)
        assertEquals(OnboardingStep.DIETARY, OnboardingRules.replayPath(OnboardingStep.NUTRITION_TARGETS, a).last())
    }

    @Test fun questionNumbersIgnoreBranchesAndTransitions() {
        assertEquals(11, OnboardingStep.WEIGHT_GOAL.questionNumber)
        assertEquals(11, OnboardingStep.MUSCLE_FOCUS.questionNumber)
        assertEquals(12, OnboardingStep.DIETARY.questionNumber)
        assertNull(OnboardingStep.NUTRITION_TARGETS.questionNumber)
        assertNull(OnboardingStep.FITNESS_TRANSITION.questionNumber)
    }

    @Test fun newToExerciseIsMutuallyExclusive() {
        var a = OnboardingRules.toggleBackground(OnboardingAnswers(), TrainingBackground.RUNNING)
        a = OnboardingRules.toggleBackground(a, TrainingBackground.NEW_TO_EXERCISE)
        assertEquals(listOf(TrainingBackground.NEW_TO_EXERCISE), a.trainingBackground)
        a = OnboardingRules.toggleBackground(a, TrainingBackground.HIIT)
        assertEquals(listOf(TrainingBackground.HIIT), a.trainingBackground)
    }

    @Test fun primaryGoalClearsMatchingSecondary() {
        val a = OnboardingRules.setPrimaryGoal(OnboardingAnswers(secondaryGoal = FitnessGoal.BUILD_STRENGTH), FitnessGoal.BUILD_STRENGTH)
        assertNull(a.secondaryGoal)
    }

    @Test fun changingNutritionGoalClearsBranchAnswers() {
        var a = OnboardingRules.setNutritionGoal(OnboardingAnswers(), NutritionGoalChoice.LOSE_WEIGHT).copy(targetWeightKg = 70.0, weeklyRateKg = 0.5)
        a = OnboardingRules.setNutritionGoal(a, NutritionGoalChoice.MAINTAIN_WEIGHT)
        assertNull(a.targetWeightKg)
        assertNull(a.weeklyRateKg)
    }

    @Test fun losePaceOptionsCappedAtOnePercentBodyWeight() {
        val a = OnboardingAnswers(weightUnit = "lb", weightKg = 84.0, nutritionGoal = NutritionGoalChoice.LOSE_WEIGHT)
        assertEquals(3, OnboardingRules.paceOptions(a).size) // 0.5, 1, 1.5 lb — not 2 lb
    }

    @Test fun targetDirectionAndTooFastDateAreFlagged() {
        var a = OnboardingAnswers(weightKg = 80.0, heightCm = 178.0, nutritionGoal = NutritionGoalChoice.LOSE_WEIGHT, targetWeightKg = 85.0)
        assertNotNull(OnboardingRules.weightGoalError(a))
        a = a.copy(targetWeightKg = 70.0, weeklyRateKg = 0.5)
        assertNull(OnboardingRules.weightGoalError(a))
        val twoWeeks = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 14) }.time
        a = OnboardingRules.withTargetDate(a, twoWeeks)
        assertNotNull(OnboardingRules.weightGoalError(a)) // 10 kg in 2 weeks
    }

    @Test fun timelineIsARange() {
        val est = OnboardingRules.estimatedTimeline(
            OnboardingAnswers(weightKg = 80.0, nutritionGoal = NutritionGoalChoice.LOSE_WEIGHT, targetWeightKg = 75.0, weeklyRateKg = 0.5)
        )!!
        assertEquals(10, est.minWeeks)
        assertTrue(est.maxWeeks > 10)
    }

    private val complete = OnboardingAnswers(
        sex = MetabolicSexInput.FEMALE, age = 30, heightCm = 165.0, weightKg = 70.0,
        activityLevel = DailyActivityLevel.LIGHTLY_ACTIVE, weeklyFrequency = WeeklyFrequency.THREE_TO_FOUR
    )

    @Test fun preferNotToSayBlocksCalculation() {
        val a = complete.copy(sex = MetabolicSexInput.PREFER_NOT_TO_SAY, nutritionGoal = NutritionGoalChoice.MAINTAIN_WEIGHT)
        assertNull(OnboardingRules.buildNutritionRequest(a))
        assertEquals(OnboardingStep.ABOUT_YOU, OnboardingRules.firstMissingStep(a))
    }

    @Test fun missingGoalPointsToNutritionGoal() {
        assertNull(OnboardingRules.buildNutritionRequest(complete))
        assertEquals(OnboardingStep.NUTRITION_GOAL, OnboardingRules.firstMissingStep(complete))
    }

    @Test fun loseWithoutTargetSendsPaceAndNoTarget() {
        val r = OnboardingRules.buildNutritionRequest(
            complete.copy(nutritionGoal = NutritionGoalChoice.LOSE_WEIGHT, skipTargetWeight = true, weeklyRateKg = 0.5)
        )!!
        assertEquals("Lose", r.primaryGoal)
        assertEquals(0.5, r.weeklyRateKg!!, 1e-9)
        assertEquals(false, r.hasGoalWeight)
        assertEquals(70.0, r.goalWeightKg, 1e-9)
        assertEquals("ThreeToFour", r.workoutFrequency)
        assertEquals("LightlyActive", r.activityLevel)
    }

    @Test fun muscleWithoutWeightGoalMapsSubtype() {
        val r = OnboardingRules.buildNutritionRequest(
            complete.copy(nutritionGoal = NutritionGoalChoice.BUILD_MUSCLE_GAIN_WEIGHT, muscleFocus = MuscleFocus.NO_WEIGHT_GOAL)
        )!!
        assertEquals("BuildMuscle", r.primaryGoal)
        assertEquals("NoWeightGoal", r.goalSubtype)
        assertNull(r.weeklyRateKg)
    }

    @Test fun minorsGetNoWeightChangePace() {
        val r = OnboardingRules.buildNutritionRequest(
            complete.copy(age = 16, nutritionGoal = NutritionGoalChoice.LOSE_WEIGHT, targetWeightKg = 65.0, weeklyRateKg = 0.5)
        )!!
        assertNull(r.weeklyRateKg)
        assertEquals("Maintain", r.primaryGoal)
    }

    @Test fun answersWireFormatMatchesServerContract() {
        val json = """{"primaryGoal":"BuildMuscle","locations":["Home","Moon"],"activityLevel":"ModeratelyActive","futureKey":1}"""
        val a = ApiClient.gson.fromJson(json, OnboardingAnswers::class.java).normalized()
        assertEquals(FitnessGoal.BUILD_MUSCLE, a.primaryGoal)
        assertEquals(listOf(WorkoutLocationChoice.HOME), a.locations)
        assertEquals(DailyActivityLevel.MODERATELY_ACTIVE, a.activityLevel)
        assertEquals(emptyList<TrainingInterest>(), a.interests)
        val encoded = ApiClient.gson.toJson(a)
        assertTrue(encoded.contains("\"primaryGoal\":\"BuildMuscle\""))
        assertTrue(encoded.contains("\"activityLevel\":\"ModeratelyActive\""))
        assertEquals(a, ApiClient.gson.fromJson(encoded, OnboardingAnswers::class.java).normalized())
    }
}
