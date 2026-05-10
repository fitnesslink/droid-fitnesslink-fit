package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalType
import com.fitnesslink.fit.model.Habit
import com.fitnesslink.fit.model.HabitType
import com.fitnesslink.fit.network.ApiClient
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

class GoalCreationViewModel : ViewModel() {

    // Step 1
    var selectedGoalType by mutableStateOf<GoalType?>(null)

    // Step 2
    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var targetValue by mutableStateOf("")
    var targetUnit by mutableStateOf("")
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
        when (type) {
            GoalType.Performance -> {
                targetUnit = "km"
                identityStatement = "I am someone who pushes my limits"
            }
            GoalType.BodyComposition -> {
                targetUnit = "lbs"
                identityStatement = "I am someone who takes care of my body"
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
                habit("Walk 5,000 steps", HabitType.PassiveSensor, null, 5000.0, "steps", 0)
            )
            GoalType.Nutrition -> listOf(
                habit("Eat a protein-rich breakfast", HabitType.Nutrition, "After I wake up", 30.0, "g protein", 1),
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

    private fun submit() {
        if (isSubmitting) return
        val type = selectedGoalType ?: return
        val target = targetValue.toDoubleOrNull()
        val goal = Goal(
            id = UUID.randomUUID().toString(),
            goalType = type,
            title = title.trim(),
            description = description.trim(),
            targetValue = target,
            targetUnit = targetUnit.takeIf { it.isNotBlank() },
            targetDate = targetDate,
            identityStatement = identityStatement.takeIf { it.isNotBlank() }
        )

        isSubmitting = true
        submitError = null
        viewModelScope.launch {
            try {
                val created = ApiClient.goalApi.create(goal)
                val toLink = suggestedHabits.filter { it.id in selectedHabitIds }
                toLink.forEach { habit ->
                    runCatching {
                        ApiClient.goalApi.createHabit(habit.copy(goalId = created.id))
                    }
                }
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
        minutes: Int
    ): Habit = Habit(
        id = UUID.randomUUID().toString(),
        title = title,
        habitType = type,
        anchorBehavior = anchor,
        targetValue = target,
        targetUnit = unit,
        estimatedMinutes = minutes
    )

    companion object {
        private fun defaultTargetDate(): Long {
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, 3)
            return cal.timeInMillis
        }
    }
}
