package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.goals.GoalStore
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalType
import com.fitnesslink.fit.model.Habit
import com.fitnesslink.fit.model.HabitType
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.progress.BodyTrackingStore
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * iOS `GoalCreationViewModel`: three steps (direction → details → habits).
 * The body-composition path carries an optional "Current Weight" pre-filled
 * from the latest weigh-in (FTNSSLNK-158) and sent as the goal's
 * `currentValue`. Save is local-first through `GoalStore.create` — the
 * network send is fire-and-forget, so the flow closes at once.
 */
class GoalCreationViewModel : ViewModel() {

    // Step 1
    var selectedGoalType by mutableStateOf<GoalType?>(null)

    // Step 2
    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var targetValue by mutableStateOf("")
    var targetUnit by mutableStateOf("")
    /** Optional current body weight (lbs) for the Lose Weight path; never required. */
    var currentValue by mutableStateOf("")
    var targetDate by mutableStateOf(defaultTargetDate())
    var identityStatement by mutableStateOf("")

    // Step 3
    val suggestedHabits = mutableStateListOf<Habit>()
    val selectedHabitIds = mutableStateListOf<String>()

    var currentStep by mutableStateOf(1)
        private set
    var isComplete by mutableStateOf(false)
        private set
    var isSubmitting by mutableStateOf(false)
        private set
    var submitError by mutableStateOf<String?>(null)
        private set

    val canProceedStep1: Boolean get() = selectedGoalType != null
    val canProceedStep2: Boolean get() = title.isNotBlank()

    fun selectGoalType(type: GoalType) {
        selectedGoalType = type
        title = type.displayName
        // Current weight only applies to the body-composition path; clear it
        // for every other type so a stale pre-fill can't leak across a change.
        currentValue = ""
        when (type) {
            GoalType.Performance -> {
                targetUnit = "km"
                identityStatement = "I am someone who pushes my limits"
            }
            GoalType.BodyComposition -> {
                targetUnit = "lbs"
                identityStatement = "I am someone who takes care of my body"
                prefillCurrentWeight()
            }
            GoalType.Consistency -> {
                targetUnit = "days"
                identityStatement = "I am someone who shows up every day"
            }
            GoalType.Nutrition -> {
                targetUnit = "days"
                identityStatement = "I am someone who fuels my body well"
            }
            GoalType.RecoveryWellness -> {
                targetUnit = "hours"
                identityStatement = "I am someone who prioritizes recovery"
            }
            GoalType.Custom -> {
                targetUnit = ""
                identityStatement = ""
            }
        }
    }

    /** iOS `prefillCurrentWeight`: latest weigh-in converted to lbs; blank when none is logged. */
    private fun prefillCurrentWeight() {
        val latest = BodyTrackingStore.weightEntries().firstOrNull() ?: return
        val lbs = latest.unit.convert(latest.weight, WeightUnit.LBS)
        currentValue = if (lbs == Math.rint(lbs)) String.format(Locale.US, "%.0f", lbs) else String.format(Locale.US, "%.1f", lbs)
    }

    fun next() {
        when (currentStep) {
            1 -> {
                if (!canProceedStep1) return
                currentStep = 2
            }
            2 -> {
                if (!canProceedStep2) return
                generateSuggestedHabits()
                currentStep = 3
            }
            3 -> submit()
        }
    }

    fun previous() {
        if (currentStep > 1) currentStep -= 1
    }

    fun toggleHabit(habitId: String) {
        if (selectedHabitIds.contains(habitId)) {
            selectedHabitIds.remove(habitId)
        } else {
            selectedHabitIds.add(habitId)
        }
    }

    private fun generateSuggestedHabits() {
        val type = selectedGoalType ?: return
        suggestedHabits.clear()
        val list = when (type) {
            GoalType.Performance -> listOf(
                habit("Put on workout shoes and stretch", HabitType.ActionBased, "After I finish my morning coffee", 1.0, "reps", 2),
                habit("Walk for 10 minutes", HabitType.TimeBased, "After I put on my shoes", 10.0, "minutes", 10)
            )
            GoalType.BodyComposition -> listOf(
                habit("Log your meals", HabitType.Nutrition, "After each meal", 3.0, "meals", 2),
                // iOS: `autoCompleteSource: "HealthKit_Steps"`; the Android source is Health Connect.
                habit("Walk 5,000 steps", HabitType.PassiveSensor, null, 5000.0, "steps", 0, autoCompleteSource = PASSIVE_STEPS_SOURCE)
            )
            GoalType.Nutrition -> listOf(
                habit("Eat a protein-rich breakfast", HabitType.Nutrition, "After I wake up", 30.0, "grams protein", 1),
                habit("Drink a glass of water", HabitType.ActionBased, "Before each meal", 3.0, "glasses", 1)
            )
            GoalType.Consistency -> listOf(
                habit("Do 5 push-ups", HabitType.ActionBased, "After I brush my teeth", 5.0, "reps", 1),
                habit("Stretch for 2 minutes", HabitType.TimeBased, "After I get out of bed", 2.0, "minutes", 2)
            )
            GoalType.RecoveryWellness -> listOf(
                habit("5-minute meditation", HabitType.TimeBased, "After I sit down in the morning", 5.0, "minutes", 5),
                habit("No screens 30 min before bed", HabitType.Avoidance, "After I set my alarm", 30.0, "minutes", 0)
            )
            GoalType.Custom -> listOf(
                habit("Daily check-in", HabitType.Reflection, "After dinner", 1.0, "entry", 2)
            )
        }
        suggestedHabits.addAll(list)
        selectedHabitIds.clear()
        selectedHabitIds.addAll(list.map { it.id })
    }

    /** iOS `save`: write + queue locally, send without waiting, close the flow. */
    private fun submit() {
        if (isSubmitting) return
        val type = selectedGoalType ?: return
        val now = System.currentTimeMillis()
        val goal = Goal(
            id = UUID.randomUUID().toString().lowercase(Locale.US),
            goalType = type,
            title = title.trim(),
            description = description.trim(),
            targetValue = targetValue.toDoubleOrNull(),
            targetUnit = targetUnit.takeIf { it.isNotBlank() },
            // Optional — defaults to 0 when skipped; progress begins once a body weight is logged.
            currentValue = currentValue.toDoubleOrNull() ?: 0.0,
            startDate = now,
            targetDate = targetDate,
            identityStatement = identityStatement.takeIf { it.isNotBlank() }
        )
        val toLink = suggestedHabits.filter { it.id in selectedHabitIds }

        isSubmitting = true
        submitError = null
        viewModelScope.launch {
            try {
                GoalStore.create(goal, toLink)
                isComplete = true
            } catch (e: Exception) {
                submitError = e.localizedMessage ?: "Could not create goal"
            } finally {
                isSubmitting = false
            }
        }
    }

    private fun habit(
        title: String,
        type: HabitType,
        anchor: String?,
        target: Double,
        unit: String,
        minutes: Int,
        autoCompleteSource: String? = null
    ): Habit = Habit(
        id = UUID.randomUUID().toString(),
        title = title,
        habitType = type,
        anchorBehavior = anchor,
        targetValue = target,
        targetUnit = unit,
        estimatedMinutes = minutes,
        autoCompleteSource = autoCompleteSource
    )

    companion object {
        /** Android counterpart of iOS `"HealthKit_Steps"`. */
        const val PASSIVE_STEPS_SOURCE = "HealthConnect_Steps"

        private fun defaultTargetDate(): Long {
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, 3)
            return cal.timeInMillis
        }
    }
}
