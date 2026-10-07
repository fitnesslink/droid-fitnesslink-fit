package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.model.WorkoutTask
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import kotlin.math.roundToInt

/**
 * Completion state of one set (loose movement) or one round of a
 * superset / circuit / interval member (PRD §7.1–7.4, FTNSSLNK-400).
 *
 * [ACTIVE] is never stored: it is what the session reports for the set
 * under the interactive cursor when that set has no recorded outcome yet.
 */
enum class SetState(val rawValue: String) {
    @SerializedName("upcoming") UPCOMING("upcoming"),
    @SerializedName("active") ACTIVE("active"),
    @SerializedName("completed") COMPLETED("completed"),
    @SerializedName("skipped") SKIPPED("skipped"),
    @SerializedName("incomplete") INCOMPLETE("incomplete")
}

/** Roll-up of an exercise's sets (PRD §7.4). */
enum class ExerciseState {
    UPCOMING, IN_PROGRESS, COMPLETED, SKIPPED, INCOMPLETE;

    /** Counts as "done with" for the Finish rule (FTNSSLNK-403): completed or explicitly skipped. */
    val isResolved: Boolean get() = this == COMPLETED || this == SKIPPED
}

/**
 * Per-set completion record of a workout session, keyed by task id +
 * 1-based iteration (set for loose movements, round for group members).
 * Pure value type: every query takes the required iteration count per
 * task from the caller. JSON goes in the `workout_sessions.progressJson`
 * column with the same shape iOS writes (`{"entries":{"taskId#n":{...}}}`).
 */
class SessionProgress private constructor(private val map: LinkedHashMap<String, Entry>) {

    constructor() : this(LinkedHashMap())

    data class Entry(
        val state: SetState,
        val metricsLogged: Boolean = false,
        /** Seconds of work accrued on the set when it finished (the SET timer at Next / Save Set). */
        val durationSeconds: Int? = null,
        /** Logged distance (FTNSSLNK-409) in [distanceUnit] ("km" | "mi"). */
        val distance: Double? = null,
        val distanceUnit: String? = null
    ) {
        val distanceValue: DistanceValue?
            get() {
                val d = distance ?: return null
                if (d <= 0) return null
                val unit = DistanceUnit.fromWire(distanceUnit) ?: return null
                return DistanceValue(d, unit)
            }
    }

    /** `"<taskId>#<iteration>"` → entry. Absent = upcoming + not logged. */
    val entries: Map<String, Entry> get() = map

    val isEmpty: Boolean get() = map.isEmpty()

    fun copy(): SessionProgress = SessionProgress(LinkedHashMap(map))

    override fun equals(other: Any?): Boolean = other is SessionProgress && other.map == map
    override fun hashCode(): Int = map.hashCode()
    override fun toString(): String = "SessionProgress($map)"

    // MARK: - Reads

    fun entry(taskId: String, iteration: Int): Entry = map[key(taskId, iteration)] ?: Entry(SetState.UPCOMING)

    fun state(taskId: String, iteration: Int): SetState = entry(taskId, iteration).state

    fun isLogged(taskId: String, iteration: Int): Boolean = entry(taskId, iteration).metricsLogged

    /** States for iterations 1…[required] of a task. */
    fun states(taskId: String, required: Int): List<SetState> {
        if (required <= 0) return emptyList()
        return (1..required).map { state(taskId, it) }
    }

    fun exerciseState(taskId: String, required: Int): ExerciseState {
        val states = states(taskId, required)
        if (states.isEmpty()) return ExerciseState.UPCOMING
        if (states.all { it == SetState.COMPLETED }) return ExerciseState.COMPLETED
        if (states.all { it == SetState.UPCOMING }) return ExerciseState.UPCOMING
        if (states.contains(SetState.UPCOMING)) return ExerciseState.IN_PROGRESS
        if (states.contains(SetState.SKIPPED)) return ExerciseState.SKIPPED
        return ExerciseState.INCOMPLETE
    }

    fun isExerciseCompleted(taskId: String, required: Int): Boolean =
        exerciseState(taskId, required) == ExerciseState.COMPLETED

    /** Completed sets ÷ required sets, as a whole percentage. Skipped and incomplete sets do not count. */
    fun completionPercent(required: Map<String, Int>): Int {
        val total = required.values.sum()
        if (total <= 0) return 0
        val done = completedSetCount(required)
        return (done.toDouble() * 100 / total.toDouble()).roundToInt()
    }

    fun completedSetCount(required: Map<String, Int>): Int =
        required.entries.sumOf { (id, n) -> states(id, n).count { it == SetState.COMPLETED } }

    /** Task ids whose every required set is completed — the legacy `completedTaskIds` shape, derived. */
    fun completedTaskIds(required: Map<String, Int>): Set<String> =
        required.filter { isExerciseCompleted(it.key, it.value) }.keys

    /** Tasks that are neither completed nor explicitly skipped ("N incomplete exercises remaining"). */
    fun unresolvedTaskIds(required: Map<String, Int>): Set<String> =
        required.filter { !exerciseState(it.key, it.value).isResolved }.keys

    /** A phase is complete when every one of its exercises is completed or skipped (PRD §7.4). */
    fun isPhaseCompleted(taskIds: List<String>, required: Map<String, Int>): Boolean {
        val movements = taskIds.filter { required[it] != null }
        if (movements.isEmpty()) return false
        return movements.all { exerciseState(it, required[it] ?: 1).isResolved }
    }

    // MARK: - Transitions (PRD §7.4 / §9)

    /** Next: the set was done. Keeps an existing logged flag (Save Set then Next stays logged). */
    fun complete(taskId: String, iteration: Int, durationSeconds: Int? = null) {
        var e = entry(taskId, iteration).copy(state = SetState.COMPLETED)
        if (durationSeconds != null) e = e.copy(durationSeconds = durationSeconds)
        map[key(taskId, iteration)] = e
    }

    /** Save Set: completed + logged. A distance is recorded when given; null leaves an earlier one alone, 0 clears it. */
    fun log(taskId: String, iteration: Int, durationSeconds: Int? = null, distance: Double? = null, distanceUnit: String? = null) {
        var e = entry(taskId, iteration).copy(state = SetState.COMPLETED, metricsLogged = true)
        if (durationSeconds != null) e = e.copy(durationSeconds = durationSeconds)
        val unit = DistanceUnit.fromWire(distanceUnit)
        if (distance != null && distance > 0 && unit != null) {
            e = e.copy(distance = distance, distanceUnit = unit.rawValue)
        } else if (distance != null) {
            e = e.copy(distance = null, distanceUnit = null)
        }
        map[key(taskId, iteration)] = e
    }

    /** Sum of every logged distance in [unit] (FTNSSLNK-409). Null when no set carries a distance. */
    fun totalDistance(unit: DistanceUnit): Double? {
        val values = map.values.mapNotNull { it.distanceValue }
        if (values.isEmpty()) return null
        return values.sumOf { it.converted(unit).value }
    }

    /** The logged values were cleared: no longer logged; a completed set goes back to upcoming. Skipped / incomplete outcomes are kept. */
    fun clearLog(taskId: String, iteration: Int) {
        val k = key(taskId, iteration)
        var e = map[k] ?: return
        e = e.copy(metricsLogged = false, durationSeconds = null, distance = null, distanceUnit = null)
        if (e.state == SetState.COMPLETED) e = e.copy(state = SetState.UPCOMING)
        if (e.state == SetState.UPCOMING && !e.metricsLogged) map.remove(k) else map[k] = e
    }

    /** Skip Set: this one set is recorded incomplete. */
    fun skipSet(taskId: String, iteration: Int) {
        map[key(taskId, iteration)] = entry(taskId, iteration).copy(state = SetState.INCOMPLETE)
    }

    /** Skip Exercise / Skip Block: every set from [from] through [required] with no completed outcome yet is recorded skipped. */
    fun skipRemaining(taskId: String, from: Int, required: Int) {
        if (required < 1) return
        val start = maxOf(1, from)
        if (start > required) return
        for (i in start..required) {
            val e = entry(taskId, i)
            if (e.state == SetState.COMPLETED) continue
            map[key(taskId, i)] = e.copy(state = SetState.SKIPPED)
        }
    }

    /** Playlist checkbox on: every required set completed (logged flags untouched). */
    fun completeExercise(taskId: String, required: Int) {
        if (required < 1) return
        for (i in 1..required) complete(taskId, i)
    }

    /** Playlist checkbox off: every set back to upcoming. Logged values stay logged (the flag survives; only the state reverts). */
    fun revertExercise(taskId: String, required: Int) {
        if (required < 1) return
        for (i in 1..required) {
            val k = key(taskId, i)
            val e = map[k] ?: continue
            val reverted = e.copy(state = SetState.UPCOMING, durationSeconds = null)
            if (reverted.metricsLogged) map[k] = reverted else map.remove(k)
        }
    }

    fun removeAll() = map.clear()

    // MARK: - Resume cursor

    /**
     * Where a resumed / re-entered interactive session opens: the first set in playback order with
     * no outcome yet — walking superset / circuit rounds round-major (A#1 B#1 A#2 …) and treating an
     * interval block as resumable from its lead whenever any member round is still upcoming. Null
     * when nothing is upcoming.
     */
    fun firstUpcomingCursor(tasks: List<WorkoutTask>, required: Map<String, Int>): SessionTransitionEngine.Cursor? {
        val engine = SessionTransitionEngine(tasks)
        var i = 0
        while (i < tasks.size) {
            val range = engine.groupRange(i)
            if (range != null) {
                val lead = tasks[range.first]
                val rounds = engine.groupRounds(range)
                if (lead.isInterval) {
                    val anyUpcoming = range.any { p ->
                        val t = tasks[p]
                        t.isMovement && states(t.id, required[t.id] ?: rounds).contains(SetState.UPCOMING)
                    }
                    if (anyUpcoming) return SessionTransitionEngine.Cursor(range.first, 1, 1)
                } else {
                    for (round in 1..rounds) {
                        for (p in range) {
                            if (!tasks[p].isMovement) continue
                            if (state(tasks[p].id, round) == SetState.UPCOMING) {
                                return SessionTransitionEngine.Cursor(p, 1, round)
                            }
                        }
                    }
                }
                i = range.last + 1
                continue
            }
            val task = tasks[i]
            if (task.isMovement) {
                val sets = required[task.id] ?: maxOf(task.sets, 1)
                val set = (1..maxOf(sets, 1)).firstOrNull { state(task.id, it) == SetState.UPCOMING }
                if (set != null) return SessionTransitionEngine.Cursor(i, set, 1)
            }
            i += 1
        }
        return null
    }

    // MARK: - Finish rule (FTNSSLNK-403)

    /**
     * First set the user could still do, for "Continue Workout": the first upcoming set, else the
     * first set of the first exercise that is neither completed nor skipped, else null.
     */
    fun continueCursor(tasks: List<WorkoutTask>, required: Map<String, Int>): SessionTransitionEngine.Cursor? {
        firstUpcomingCursor(tasks, required)?.let { return it }
        val engine = SessionTransitionEngine(tasks)
        for ((i, task) in tasks.withIndex()) {
            if (!task.isMovement) continue
            val n = required[task.id] ?: 1
            if (exerciseState(task.id, n).isResolved) continue
            val range = engine.groupRange(i)
            if (range != null) {
                val round = (1..maxOf(n, 1)).firstOrNull { state(task.id, it) != SetState.COMPLETED } ?: 1
                return SessionTransitionEngine.Cursor(range.first, 1, round)
            }
            val set = (1..maxOf(n, 1)).firstOrNull { state(task.id, it) != SetState.COMPLETED } ?: 1
            return SessionTransitionEngine.Cursor(i, set, 1)
        }
        return null
    }

    // MARK: - JSON

    fun encodedJSON(): String? {
        if (map.isEmpty()) return null
        return gson.toJson(Wire(entries = map))
    }

    private data class Wire(val entries: Map<String, Entry> = emptyMap())

    companion object {
        private val gson: Gson = GsonBuilder().create()

        fun key(taskId: String, iteration: Int): String = "$taskId#$iteration"

        fun decode(json: String?): SessionProgress? {
            if (json.isNullOrBlank()) return null
            return try {
                val wire = gson.fromJson(json, Wire::class.java) ?: return null
                val entries = wire.entries
                SessionProgress(LinkedHashMap(entries.filterValues { it != null }))
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Iterations a movement task must complete: `sets` for a loose movement, the group's rounds
         * for a superset / circuit / interval member — read from the group's lead (the first task
         * with that `groupId`). Never below 1. Non-movement tasks are omitted from the map.
         */
        fun requiredIterations(tasks: List<WorkoutTask>): Map<String, Int> {
            val groupRounds = mutableMapOf<String, Int>()
            for (task in tasks) {
                if (task.groupId.isNotEmpty() && groupRounds[task.groupId] == null) {
                    groupRounds[task.groupId] = maxOf(task.rounds ?: task.totalRounds, 1)
                }
            }
            val out = LinkedHashMap<String, Int>()
            for (task in tasks) {
                if (!task.isMovement) continue
                out[task.id] = if (task.groupId.isEmpty()) maxOf(task.sets, 1)
                else groupRounds[task.groupId] ?: maxOf(task.rounds ?: task.totalRounds, 1)
            }
            return out
        }
    }
}

/** Wording shared by the Playlist and Interactive Finish confirmations (FTNSSLNK-403). */
object SessionFinishRule {
    const val title = "Finish Workout?"
    const val continueLabel = "Continue Workout"
    const val finishAnywayLabel = "Finish Anyway"

    fun message(incompleteCount: Int): String =
        "You have $incompleteCount incomplete ${if (incompleteCount == 1) "exercise" else "exercises"} remaining."
}

/**
 * "Discard this session?" — the copy every discard surface uses (the players, the Workout Detail
 * resume card, Home's resume banner). It says what goes and what stays so nobody reads it as
 * deleting the workout.
 */
object SessionDiscardRule {
    const val title = "Discard this session?"
    const val confirmLabel = "Discard Session"
    const val cancelLabel = "Keep Session"

    fun message(workoutName: String): String {
        val name = if (workoutName.isEmpty()) "The workout" else "“$workoutName”"
        return "Only this session is removed — your progress and logged sets so far. $name stays in your library and on your calendar, ready to start again."
    }
}
