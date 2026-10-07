package com.fitnesslink.fit.programs

import com.fitnesslink.fit.model.ProgramJourneyState
import com.fitnesslink.fit.model.ProgramSnapshot
import com.fitnesslink.fit.ui.navigation.ProgramRoutes

/**
 * Program Detail's primary CTA by program state (Programs PRD §51; iOS
 * `ProgramCTA`). Managing a started program (restart / remove) lives in My
 * Program's ••• menu so it never competes with the primary action.
 */
sealed class ProgramCTA {
    data object Start : ProgramCTA()
    data class ViewMyProgram(val enrollmentId: String) : ProgramCTA()
    data class StartTodaysWorkout(val workoutId: String, val enrollmentId: String) : ProgramCTA()
    data class ViewResults(val enrollmentId: String) : ProgramCTA()

    val title: String
        get() = when (this) {
            Start -> "Start Program"
            is ViewMyProgram -> "View My Program"
            is StartTodaysWorkout -> "Start Today's Workout"
            is ViewResults -> "View Results"
        }

    fun route(programId: String): String = when (this) {
        Start -> ProgramRoutes.startProgram(programId)
        is ViewMyProgram -> ProgramRoutes.myProgram(enrollmentId)
        is StartTodaysWorkout -> "workoutDetail/$workoutId"
        is ViewResults -> ProgramRoutes.programProgress(enrollmentId)
    }

    companion object {
        /** State of [programId] for the signed-in user, from local data. */
        fun current(programId: String, now: Long = System.currentTimeMillis()): ProgramCTA {
            val instance = UserProgramStore.instance(programId) ?: return Start
            return from(UserProgramStore.snapshot(instance, now), now)
        }

        /** Pure state machine: no instance → Start; completed → View Results; open workout today → Start Today's Workout; else View My Program. */
        fun from(snapshot: ProgramSnapshot?, now: Long = System.currentTimeMillis()): ProgramCTA {
            if (snapshot == null) return Start
            val enrollmentId = snapshot.program.enrollmentId
            if (snapshot.state == ProgramJourneyState.COMPLETED) return ViewResults(enrollmentId)
            snapshot.pendingEntry(now)?.let { return StartTodaysWorkout(it.workoutId, enrollmentId) }
            return ViewMyProgram(enrollmentId)
        }
    }
}
