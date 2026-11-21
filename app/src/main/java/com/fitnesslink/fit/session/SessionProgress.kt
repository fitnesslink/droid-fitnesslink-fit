package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.model.TaskSetConfig
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
class SessionProgress private constructor(
    private val map: LinkedHashMap<String, Entry>,
    private val plans: LinkedHashMap<String, List<Int>> = LinkedHashMap(),
    private val metrics: LinkedHashMap<String, List<String>> = LinkedHashMap()
) {

    constructor() : this(LinkedHashMap())

    data class Entry(
        val state: SetState,
        val metricsLogged: Boolean = false,
        /** Seconds of work accrued on the set when it finished (the SET timer at Next / Save Set). */
        val durationSeconds: Int? = null,
        /** Logged distance (FTNSSLNK-409) in [distanceUnit] ("km" | "mi"). */
        val distance: Double? = null,
        val distanceUnit: String? = null,
        /**
         * Universal Logging (PRD §6.1, §16.2): values entered on a set that is NOT completed yet —
         * auto-saved, never counted. A completed set keeps its values in the session's logs instead.
         */
        val draft: TaskSetConfig? = null,
        /**
         * The draft holds values the user typed or changed (PRD §16.5, §16.10). Untouched prescribed
         * targets and Quick Start carry-forward values leave it false/null, so they never trigger the
         * workout-end unconfirmed-set alert.
         */
        val userModified: Boolean? = null
    ) {
        /** Nothing worth keeping: no outcome, no log, no draft. */
        val isBlank: Boolean get() = state == SetState.UPCOMING && !metricsLogged && draft == null

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

    /**
     * Session-only set list per task (Universal Logging PRD §6.6, §6.8): each element is the
     * programmed set number that session set stands for, 0 for a set the user added. Absent = the
     * programmed sets 1…n unchanged. Adding / deleting sets edits this, never the workout template.
     */
    val setPlans: Map<String, List<Int>> get() = plans

    /** Optional metrics the user added per task (PRD §5.3), as `LogMetric` raw values, in the order added. */
    val addedMetrics: Map<String, List<String>> get() = metrics

    val isEmpty: Boolean get() = map.isEmpty() && plans.isEmpty() && metrics.isEmpty()

    fun copy(): SessionProgress = SessionProgress(LinkedHashMap(map), LinkedHashMap(plans), LinkedHashMap(metrics))

    override fun equals(other: Any?): Boolean =
        other is SessionProgress && other.map == map && other.plans == plans && other.metrics == metrics
    override fun hashCode(): Int = (map.hashCode() * 31 + plans.hashCode()) * 31 + metrics.hashCode()
    override fun toString(): String = "SessionProgress($map, plans=$plans, metrics=$metrics)"

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
        if (e.isBlank) map.remove(k) else map[k] = e
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
            if (reverted.isBlank) map.remove(k) else map[k] = reverted
        }
    }

    fun removeAll() {
        map.clear()
        plans.clear()
        metrics.clear()
    }

    // MARK: - Universal Logging (drafts, set plans, metrics)

    /** A task + 1-based iteration, as the unconfirmed-set alert lists them. */
    data class TaskIteration(val taskId: String, val iteration: Int)

    /** Values entered on a not-yet-completed set (null when none). */
    fun draft(taskId: String, iteration: Int): TaskSetConfig? = map[key(taskId, iteration)]?.draft

    fun isUserModified(taskId: String, iteration: Int): Boolean = map[key(taskId, iteration)]?.userModified ?: false

    /**
     * Store (or clear, with null) a set's draft. `userModified` only ever latches on: once the user
     * has typed into a set, a later programmatic refill doesn't make it "untouched" again.
     */
    fun setDraft(taskId: String, iteration: Int, values: TaskSetConfig?, userModified: Boolean) {
        val k = key(taskId, iteration)
        var e = (map[k] ?: Entry(SetState.UPCOMING)).copy(draft = values)
        if (values == null) e = e.copy(userModified = null)
        else if (userModified || e.userModified == true) e = e.copy(userModified = true)
        if (e.isBlank) map.remove(k) else map[k] = e
    }

    /** Every draft of a task, by iteration. */
    fun drafts(taskId: String): Map<Int, TaskSetConfig> {
        val prefix = "$taskId#"
        val out = LinkedHashMap<Int, TaskSetConfig>()
        for ((k, e) in map) {
            if (!k.startsWith(prefix)) continue
            val draft = e.draft ?: continue
            val n = k.substring(prefix.length).toIntOrNull() ?: continue
            out[n] = draft
        }
        return out
    }

    /**
     * Incomplete sets holding user-entered values (PRD §11.3 / §16.10): what the workout-end
     * "You Have Unconfirmed Sets" alert lists. Completed sets, untouched prescriptions /
     * carry-forward values and empty sets are excluded. Ordered by task id then iteration.
     */
    fun unconfirmedSets(): List<TaskIteration> = map.mapNotNull { (k, e) ->
        val draft = e.draft
        if (e.state == SetState.COMPLETED || e.userModified != true || draft == null || !UniversalSetValues.hasAnyValue(draft)) return@mapNotNull null
        val hash = k.lastIndexOf('#')
        if (hash < 0) return@mapNotNull null
        val n = k.substring(hash + 1).toIntOrNull() ?: return@mapNotNull null
        TaskIteration(k.substring(0, hash), n)
    }.sortedWith(compareBy<TaskIteration> { it.taskId }.thenBy { it.iteration })

    fun setPlan(taskId: String): List<Int>? = plans[taskId]

    fun setSetPlan(taskId: String, plan: List<Int>?) {
        if (plan == null) plans.remove(taskId) else plans[taskId] = plan.toList()
    }

    fun addedMetrics(taskId: String): List<String> = metrics[taskId] ?: emptyList()

    fun setAddedMetrics(taskId: String, metrics: List<String>) {
        if (metrics.isEmpty()) this.metrics.remove(taskId) else this.metrics[taskId] = metrics.toList()
    }

    /**
     * Delete one session set and close the gap (PRD §6.8): every entry of [taskId] above
     * [iteration] moves down by one. Other tasks are untouched.
     */
    fun removeIteration(taskId: String, iteration: Int, count: Int) {
        if (count < 1 || iteration < 1 || iteration > count) return
        map.remove(key(taskId, iteration))
        if (iteration >= count) return
        for (n in (iteration + 1)..count) {
            val e = map.remove(key(taskId, n)) ?: continue
            map[key(taskId, n - 1)] = e
        }
    }

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
        if (isEmpty) return null
        return gson.toJson(Wire(entries = map, setPlans = plans.takeIf { it.isNotEmpty() }, addedMetrics = metrics.takeIf { it.isNotEmpty() }))
    }

    /** `setPlans` / `addedMetrics` are optional so rows written before Universal Logging decode unchanged. */
    private data class Wire(
        val entries: Map<String, Entry>? = emptyMap(),
        val setPlans: Map<String, List<Int>>? = null,
        val addedMetrics: Map<String, List<String>>? = null
    )

    companion object {
        private val gson: Gson = GsonBuilder().create()

        fun key(taskId: String, iteration: Int): String = "$taskId#$iteration"

        fun decode(json: String?): SessionProgress? {
            if (json.isNullOrBlank()) return null
            return try {
                val wire = gson.fromJson(json, Wire::class.java) ?: return null
                @Suppress("SENSELESS_COMPARISON")
                val entries = (wire.entries ?: emptyMap()).filterValues { it != null && it.state != null }
                val plans = (wire.setPlans ?: emptyMap()).mapNotNull { (k, v) ->
                    @Suppress("SENSELESS_COMPARISON")
                    if (v == null) null else k to v.filterNotNull().map { (it as Number).toInt() }
                }.toMap()
                val metrics = (wire.addedMetrics ?: emptyMap()).mapNotNull { (k, v) ->
                    @Suppress("SENSELESS_COMPARISON")
                    if (v == null) null else k to v.filterNotNull()
                }.toMap()
                SessionProgress(LinkedHashMap(entries), LinkedHashMap(plans), LinkedHashMap(metrics))
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
