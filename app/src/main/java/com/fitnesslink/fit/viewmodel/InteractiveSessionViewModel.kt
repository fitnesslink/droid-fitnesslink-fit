package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.ExerciseProgress
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.model.WorkoutProgress
import com.fitnesslink.fit.model.WorkoutTask
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class InteractiveSessionViewModel : ViewModel() {
    private var workoutTasks = mutableListOf<WorkoutTask>()
    private var position = 0
    private var secondsElapsed = 0
    private var restSecondsElapsed = 0
    private var timerJob: Job? = null
    private var restTimerJob: Job? = null
    private var currentSet = 1

    var workoutTask by mutableStateOf(WorkoutTask())
    var workoutProgress by mutableStateOf<List<WorkoutProgress>>(emptyList())
    var exerciseProgress by mutableStateOf<List<ExerciseProgress>>(emptyList())
    var currentRound by mutableIntStateOf(1)
    var timerText by mutableStateOf("00:00:00")
    var isRest by mutableStateOf(false)
    var isVideoPaused by mutableStateOf(false)
    var rest by mutableStateOf("00:00")
    var restCount by mutableIntStateOf(0)
    var isWorkoutComplete by mutableStateOf(false)
    var workoutName by mutableStateOf("")

    val exerciseCount: Int get() = workoutTasks.count { it.isMovement }
    val totalSets: Int get() = workoutTasks.filter { it.isMovement }.sumOf { maxOf(it.sets, 1) }

    fun loadData(workoutId: String) {
        val detail = DatabaseManager.workout(workoutId) ?: com.fitnesslink.fit.model.Workout()
        workoutName = detail.name
        workoutTasks = detail.phases
            .flatMap { it.taskRows }
            .mapNotNull { it.task }
            .toMutableList()

        for (i in workoutTasks.indices) {
            workoutTasks[i] = workoutTasks[i].copy(order = i)
        }

        start()
        updateUI()
    }

    fun getNext() {
        removeRestTimer()

        if (workoutTask.sets > 0 && currentSet < workoutTask.sets) {
            currentSet++
            updateExerciseProgress()
            return
        }

        currentSet = 1
        position++
        if (position > workoutTasks.size - 1) {
            stop()
            isWorkoutComplete = true
            return
        }
        updateUI()
    }

    fun getPrevious() {
        removeRestTimer()

        if (workoutTask.sets > 0 && currentSet > 1) {
            currentSet--
            updateExerciseProgress()
            return
        }

        currentSet = 1
        position--
        if (position < 0) {
            position = 0
            return
        }
        updateUI()
    }

    fun getNextExercise() {
        removeRestTimer()
        position++
        if (position > workoutTasks.size - 1) {
            position = workoutTasks.size - 1
            return
        }
        currentSet = 1
        updateUI()
    }

    fun toggleVideoState() {
        isVideoPaused = !isVideoPaused
    }

    fun stop() {
        timerJob?.cancel()
        timerJob = null
    }

    fun removeRestTimer() {
        restTimerJob?.cancel()
        restTimerJob = null
    }

    private fun start() {
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                secondsElapsed++
                setTimeLabel()
            }
        }
    }

    private fun updateUI() {
        if (workoutTasks.isEmpty()) return

        workoutTask = workoutTasks[position]
        isRest = workoutTask.isRest

        if (isRest) {
            restSecondsElapsed = workoutTask.restSeconds
            rest = formatRestTime(restSecondsElapsed)
            setRestTimer()
        }

        if (position + 1 < workoutTasks.size) {
            workoutTask = workoutTask.copy(nextImageUrl = workoutTasks[position + 1].iconUrl)
        }

        setWorkoutProgress()
        updateExerciseProgress()
    }

    private fun updateExerciseProgress() {
        val sets = maxOf(workoutTask.sets, 1)
        exerciseProgress = (0 until sets).map { i ->
            ExerciseProgress(
                id = "$i",
                progress = if (i < currentSet) 1 else if (i == currentSet - 1) 2 else 0
            )
        }
    }

    private fun setWorkoutProgress() {
        workoutProgress = workoutTasks.mapIndexed { i, task ->
            WorkoutProgress(
                id = task.id,
                progress = if (i < position) 1 else if (i == position) 2 else 0
            )
        }
    }

    private fun setRestTimer() {
        restTimerJob = viewModelScope.launch {
            while (restSecondsElapsed > 0) {
                delay(1000)
                restSecondsElapsed--
                rest = formatRestTime(restSecondsElapsed)
            }
            getNext()
        }
    }

    private fun setTimeLabel() {
        val h = secondsElapsed / 3600
        val m = (secondsElapsed % 3600) / 60
        val s = secondsElapsed % 60
        timerText = String.format("%02d:%02d:%02d", h, m, s)
    }

    private fun formatRestTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        restTimerJob?.cancel()
    }
}
