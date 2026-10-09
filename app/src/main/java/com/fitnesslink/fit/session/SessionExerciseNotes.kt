package com.fitnesslink.fit.session

/**
 * Per-exercise record of a session (Quick Start PRD §34–36): the optional
 * Exercise RPE (1–10, one per exercise — never per set in MVP) and the
 * Training Log, the user's own words about how the exercise felt. Distinct
 * from a task's `instructions` (coach notes) — that is programming, this is
 * experience.
 *
 * Stored locally in `session_exercise_notes`, keyed by session + task. The
 * sessions API carries neither field yet, so these do not sync.
 */
data class SessionExerciseNote(
    val sessionId: String,
    val taskId: String,
    val movementId: String?,
    val taskName: String,
    val rpe: Int?,
    val trainingLog: String?,
    val updatedAtMs: Long
) {
    val id: String get() = "$sessionId#$taskId"

    /** Nothing worth keeping — the row is deleted instead of stored. */
    val isEmpty: Boolean get() = rpe == null && trainingLog.isNullOrBlank()
}

/** The 1–10 Exercise RPE scale (PRD §36). */
object ExerciseRPE {
    val range: IntRange = 1..10
    const val LOW_LABEL = "Very Easy"
    const val HIGH_LABEL = "Very Hard"

    fun clamp(value: Int): Int = value.coerceIn(range.first, range.last)

    /** Short descriptor for a value, for the report ("RPE 8 · Hard"). */
    fun descriptor(value: Int): String = when (clamp(value)) {
        1, 2 -> "Very Easy"
        3, 4 -> "Easy"
        5, 6 -> "Moderate"
        7, 8 -> "Hard"
        else -> "Very Hard"
    }
}
