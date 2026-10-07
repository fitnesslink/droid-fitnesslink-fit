package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.model.ModelGson
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.network.dto.SessionHistoryEntry
import com.fitnesslink.fit.network.dto.SessionHistoryLogRequest
import java.util.Date
import java.util.Locale

/**
 * Wire-level outcome of one set on the sessions API (FTNSSLNK-405 / -406): `POST /sessions/{id}/history`
 * `status`, and the value `GET /sessions/{id}/history` echoes back.
 */
enum class SessionSetStatus(val rawValue: String) {
    COMPLETED("Completed"),
    SKIPPED("Skipped"),
    INCOMPLETE("Incomplete");

    val setState: SetState
        get() = when (this) {
            COMPLETED -> SetState.COMPLETED
            SKIPPED -> SetState.SKIPPED
            INCOMPLETE -> SetState.INCOMPLETE
        }

    companion object {
        /** Server value → status; null / unknown → completed (rows written before the field existed are all performed sets). */
        fun fromWire(wire: String?): SessionSetStatus =
            entries.firstOrNull { it.rawValue.equals(wire?.trim(), ignoreCase = true) } ?: COMPLETED

        /** Terminal per-set states map 1:1; upcoming / active have no wire form. */
        fun fromSetState(state: SetState): SessionSetStatus? = when (state) {
            SetState.COMPLETED -> COMPLETED
            SetState.SKIPPED -> SKIPPED
            SetState.INCOMPLETE -> INCOMPLETE
            SetState.UPCOMING, SetState.ACTIVE -> null
        }
    }
}

/**
 * One set / round of a finished session as it is recorded locally and posted to the server.
 * Built once by [SessionHistoryRows.build] so the local rows and the history API see the same values.
 */
data class SessionHistoryRow(
    val task: WorkoutTask,
    val setNumber: Int,
    val status: SessionSetStatus,
    /** True when the user captured values (Save Set / the log sheet). */
    val metricsLogged: Boolean,
    val reps: Int,
    val weightLbs: Double,
    val durationSeconds: Int?,
    /** Logged distance (FTNSSLNK-409); only a logged set carries one. */
    val distance: DistanceValue? = null
) {
    /** Performed sets — the only ones the local stats tables hold and the only ones that contribute reps / volume. */
    val isPerformed: Boolean get() = status == SessionSetStatus.COMPLETED

    /** `intervalSeconds` on the wire: only timed tasks carry a window. */
    val intervalSeconds: Int? get() = if (task.usesDuration) durationSeconds else null

    /** The history request for this row. Skipped / incomplete sets post their status with every metric null. */
    fun request(workoutId: String, userId: String, logDate: Date): SessionHistoryLogRequest = SessionHistoryLogRequest(
        workoutTaskId = task.id,
        workoutId = workoutId,
        programId = null,
        userId = userId,
        logDate = logDate,
        reps = if (isPerformed) reps else null,
        set = setNumber,
        intervalSeconds = if (isPerformed) intervalSeconds else null,
        weightLifted = if (isPerformed) weightLbs else null,
        status = status.rawValue,
        metricsLogged = metricsLogged,
        distance = if (isPerformed) distance?.value else null,
        distanceUnit = if (isPerformed) distance?.unit?.rawValue else null
    )
}

object SessionHistoryRows {
    /**
     * Every set with a terminal outcome, in task order then set order. Values for a completed set:
     * the log when there is one, else the planned per-set config, else the task-level plan.
     */
    fun build(
        tasks: List<WorkoutTask>,
        actualSetLogs: Map<String, Map<Int, TaskSetConfig>>,
        progress: SessionProgress,
        requiredIterations: Map<String, Int>
    ): List<SessionHistoryRow> {
        val rows = mutableListOf<SessionHistoryRow>()
        for (task in tasks) {
            if (!task.isMovement) continue
            val required = requiredIterations[task.id]
                ?: (if (task.groupId.isEmpty()) maxOf(task.sets, 1) else maxOf(task.rounds ?: task.totalRounds, 1))
            val logs = actualSetLogs[task.id] ?: emptyMap()
            for (setNumber in 1..maxOf(required, 1)) {
                val entry = progress.entry(task.id, setNumber)
                val logged = logs[setNumber]
                val status = SessionSetStatus.fromSetState(entry.state)
                    ?: (if (logged != null) SessionSetStatus.COMPLETED else continue)
                if (status != SessionSetStatus.COMPLETED) {
                    rows.add(SessionHistoryRow(task, setNumber, status, metricsLogged = false, reps = 0, weightLbs = 0.0, durationSeconds = null))
                    continue
                }
                if (logged != null) {
                    rows.add(SessionHistoryRow(
                        task, setNumber, SessionSetStatus.COMPLETED, metricsLogged = true,
                        reps = logged.reps, weightLbs = logged.weightKg ?: 0.0,
                        durationSeconds = logged.durationSeconds ?: entry.durationSeconds,
                        distance = logged.distanceValue ?: entry.distanceValue
                    ))
                    continue
                }
                val planned = task.setConfigs.firstOrNull { it.setNumber == setNumber }
                if (planned != null) {
                    rows.add(SessionHistoryRow(
                        task, setNumber, SessionSetStatus.COMPLETED, metricsLogged = entry.metricsLogged,
                        reps = planned.reps,
                        weightLbs = planned.weightKg ?: (task.weightKg ?: 0).toDouble(),
                        durationSeconds = entry.durationSeconds ?: planned.durationSeconds ?: task.durationSeconds
                    ))
                } else {
                    rows.add(SessionHistoryRow(
                        task, setNumber, SessionSetStatus.COMPLETED, metricsLogged = entry.metricsLogged,
                        reps = if (task.usesDuration) 0 else task.reps,
                        weightLbs = (task.weightKg ?: 0).toDouble(),
                        durationSeconds = entry.durationSeconds ?: task.durationSeconds
                    ))
                }
            }
        }
        return rows
    }
}

// MARK: - Offline queue payloads (FTNSSLNK-406)

/** `sync_queue` payload of a `sessions` / `complete` row. Older rows stored the bare session id — [decode] accepts both. */
data class QueuedCompletePayload(
    val sessionId: String,
    val rpeValue: Int? = null,
    val completionPercent: Int? = null
) {
    fun encoded(): String = ModelGson.instance.toJson(this)

    companion object {
        fun decode(payload: String, entityId: String): QueuedCompletePayload {
            val trimmed = payload.trim()
            if (trimmed.startsWith("{")) {
                runCatching { ModelGson.instance.fromJson(trimmed, QueuedCompletePayload::class.java) }
                    .getOrNull()?.let { if (it.sessionId.isNotEmpty()) return it }
            }
            return QueuedCompletePayload(sessionId = trimmed.ifEmpty { entityId })
        }
    }
}

/** `sync_queue` payload of a `session-progress` / `history` row: one set to replay through `POST /sessions/{id}/history`. */
data class QueuedHistoryPayload(
    val sessionId: String,
    val entry: SessionHistoryLogRequest
) {
    fun encoded(): String? = runCatching { ModelGson.instance.toJson(this) }.getOrNull()

    companion object {
        fun decode(payload: String): QueuedHistoryPayload? =
            runCatching { ModelGson.instance.fromJson(payload, QueuedHistoryPayload::class.java) }.getOrNull()
                ?.takeIf { it.sessionId.isNotEmpty() }
    }
}

/** Wire-shape for a queued progress update. Mirrors the body of `POST /sessions/{id}/progress`. */
data class QueuedProgressPayload(
    val sessionId: String,
    val workoutPhaseId: String?,
    val currentIndex: Int?,
    val nextMovementId: String?
) {
    fun encoded(): String = ModelGson.instance.toJson(this)

    companion object {
        fun decode(payload: String): QueuedProgressPayload? =
            runCatching { ModelGson.instance.fromJson(payload, QueuedProgressPayload::class.java) }.getOrNull()
    }
}

/** `sync_queue` payload of a `sessions` / `start` row — the `POST /sessions` body. */
data class QueuedStartPayload(
    val id: String,
    val workoutId: String,
    val programId: String? = null,
    val userId: String,
    val workoutPhaseId: String? = null,
    val platform: String? = null
) {
    fun encoded(): String = ModelGson.instance.toJson(this)

    companion object {
        fun decode(payload: String): QueuedStartPayload? =
            runCatching { ModelGson.instance.fromJson(payload, QueuedStartPayload::class.java) }.getOrNull()
                ?.takeIf { it.id.isNotEmpty() && it.workoutId.isNotEmpty() }
    }
}

// MARK: - Rebuild from the server (FTNSSLNK-406)

/** Result of [rebuiltProgress]: the per-set state plus the logged values keyed `[taskId][setNumber]`. */
data class RebuiltHistory(
    val progress: SessionProgress,
    val logs: Map<String, Map<Int, TaskSetConfig>>
)

/**
 * Per-set state + logged values rebuilt from `GET /sessions/{id}/history`, for an in-progress
 * session this install has never seen. Entries are applied in `logDate` order so a set logged
 * twice keeps its latest values; a completed outcome is never downgraded by a later skip row.
 * [taskIds] are the session's task ids as the players key them; server ids are matched
 * case-insensitively and stored under the local spelling.
 */
fun rebuiltProgress(entries: List<SessionHistoryEntry>, taskIds: List<String> = emptyList()): RebuiltHistory {
    val progress = SessionProgress()
    val logs = mutableMapOf<String, MutableMap<Int, TaskSetConfig>>()
    val canonical = taskIds.associateBy { it.lowercase(Locale.US) }
    for (entry in entries.sortedBy { it.logDate.time }) {
        val taskId = canonical[entry.workoutTaskId.lowercase(Locale.US)] ?: entry.workoutTaskId.lowercase(Locale.US)
        val setNumber = maxOf(entry.set ?: 1, 1)
        when (SessionSetStatus.fromWire(entry.status)) {
            SessionSetStatus.COMPLETED -> {
                if (entry.hasLoggedMetrics) {
                    progress.log(taskId, setNumber, entry.intervalSeconds, entry.distance, entry.distanceUnit)
                    logs.getOrPut(taskId) { mutableMapOf() }[setNumber] = TaskSetConfig(
                        setNumber = setNumber,
                        reps = entry.reps ?: 0,
                        weightKg = entry.weightLifted,
                        durationSeconds = entry.intervalSeconds,
                        distance = entry.distance,
                        distanceUnit = DistanceUnit.fromWire(entry.distanceUnit)?.rawValue
                    )
                } else {
                    progress.complete(taskId, setNumber, entry.intervalSeconds)
                }
            }
            SessionSetStatus.SKIPPED -> progress.skipRemaining(taskId, setNumber, setNumber)
            SessionSetStatus.INCOMPLETE -> {
                if (progress.state(taskId, setNumber) != SetState.COMPLETED) progress.skipSet(taskId, setNumber)
            }
        }
    }
    return RebuiltHistory(progress, logs)
}
