package com.fitnesslink.fit.network.dto

import com.fitnesslink.fit.network.ApiClient
import java.util.Date

// Wire shapes for /sessions. Mirrors iOS `SessionService.swift`. The legacy
// `StartSessionRequest` / `CompleteSessionRequest` / `LogHistoryRequest` in
// SyncDtos.kt predate these fields and are superseded by the types below.

/**
 * `POST /sessions`. The client mints `id`; the server treats the call as a
 * per-id idempotent upsert AND dedups by (userId, workoutId, not abandoned,
 * no completion date) — another device's in-flight session for the same
 * workout wins and comes back with its own id.
 */
data class SessionStartRequest(
    val id: String,
    val workoutId: String,
    val programId: String? = null,
    val userId: String,
    /** Initial phase so phase telemetry has a baseline. */
    val workoutPhaseId: String? = null,
    val platform: String? = ApiClient.PLATFORM
)

/**
 * `POST /sessions/{id}/complete` (204). `rpeId` is written as an explicit
 * JSON null when absent (ApiClient registers a serializer) — the server's
 * positional record binding misbehaves on an empty `{}`. `rpeValue` is
 * clamped 0–10, `completionPercent` 0–100.
 */
data class SessionCompleteRequest(
    val rpeId: String? = null,
    val rpeValue: Int? = null,
    val completionPercent: Int? = null,
    val platform: String? = ApiClient.PLATFORM
) {
    companion object {
        fun of(rpeId: String? = null, rpeValue: Int? = null, completionPercent: Int? = null) =
            SessionCompleteRequest(
                rpeId = rpeId,
                rpeValue = rpeValue?.coerceIn(0, 10),
                completionPercent = completionPercent?.coerceIn(0, 100)
            )
    }
}

/**
 * `POST /sessions/{id}/progress` (204). Partial update: any null field is
 * left untouched server-side.
 */
data class SessionProgressRequest(
    val workoutPhaseId: String? = null,
    val currentIndex: Int? = null,
    val nextMovementId: String? = null
)

/**
 * One set on `POST /sessions/{id}/history` (204). `status` is "Completed" |
 * "Skipped" | "Incomplete" (omitted → Completed); `metricsLogged` omitted →
 * derived from the metrics present. `distanceUnit` ("km" | "mi") is
 * required whenever `distance` is sent. `weightLifted` is lbs (mirrors the
 * set-config `weightKg` convention).
 */
data class SessionHistoryLogRequest(
    val workoutTaskId: String,
    val workoutId: String,
    val programId: String? = null,
    val userId: String,
    val logDate: Date,
    val reps: Int? = null,
    val set: Int,
    val intervalSeconds: Int? = null,
    val weightLifted: Double? = null,
    val status: String? = null,
    val metricsLogged: Boolean? = null,
    val distance: Double? = null,
    val distanceUnit: String? = null
)

/** One logged set from `GET /sessions/{id}/history` (`SessionEntryDto`). */
data class SessionHistoryEntry(
    val workoutTaskId: String,
    val workoutId: String,
    val programId: String? = null,
    val logDate: Date,
    val reps: Int? = null,
    val set: Int? = null,
    val intervalSeconds: Int? = null,
    val weightLifted: Double? = null,
    /** Null from a server that predates FTNSSLNK-405 — treat as Completed. */
    val status: String? = null,
    val metricsLogged: Boolean? = null,
    val distance: Double? = null,
    val distanceUnit: String? = null
) {
    val hasLoggedMetrics: Boolean
        get() = metricsLogged ?: (reps != null || weightLifted != null || intervalSeconds != null || distance != null)
}

/**
 * `GET /sessions/me/movement-history?movementIds=a,b,c` row (FTNSSLNK-410):
 * last / best figures per movement from completed sessions. Only movements
 * with history come back; at most 50 ids per call.
 */
data class MovementHistorySummary(
    val movementId: String,
    val lastWeight: Double? = null,
    val lastReps: Int? = null,
    val lastLoggedAt: Date? = null,
    val bestWeight: Double? = null,
    val bestReps: Int? = null,
    val bestDurationSeconds: Int? = null,
    val bestDistance: Double? = null,
    val distanceUnit: String? = null
)

/** `POST /sessions/{id}/body-data` (204). */
data class SessionBodyDataRequest(
    val heartRate: Int? = null,
    val caloriesBurned: Double? = null
)

/** `POST /sessions/{id}/share-results` (204). */
data class SessionShareResultsRequest(
    val toEmails: List<String>,
    val note: String? = null
)

object SessionSetStatus {
    const val COMPLETED = "Completed"
    const val SKIPPED = "Skipped"
    const val INCOMPLETE = "Incomplete"
}
