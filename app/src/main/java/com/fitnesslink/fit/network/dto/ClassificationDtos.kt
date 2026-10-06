package com.fitnesslink.fit.network.dto

import com.fitnesslink.fit.model.api.AuditFields

// Lookup rows the write paths need to resolve by name at save time
// (iOS `ClassificationService.swift`). Lookup tables are enums server-side —
// names are stable, match by string.

/** `GET /classification/workout-task-types` row (Movement / Rest / Interval). */
data class WorkoutTaskTypeDto(
    val id: String,
    val name: String,
    val audit: AuditFields? = null
)

/** `GET /classification/rest-types` row ("Passive" / "Active Recovery"). */
data class RestTypeDto(
    val id: String,
    val name: String,
    val audit: AuditFields? = null
)

/**
 * Hard-coded fallbacks for first-launch-offline, mirroring iOS
 * `WorkoutTaskTypeId` / `RestTypeId` / `WorkoutContentStatusId` /
 * `WorkoutSessionStatusId`. The server lookup is the source of truth as soon
 * as it loads.
 */
object ClassificationFallbackIds {
    const val TASK_TYPE_MOVEMENT = "168d85cd-f0e9-4a75-b3ad-8f4d0583d4fa"
    const val TASK_TYPE_REST = "99d9354b-c3ff-4d15-9578-1d6813c16116"
    const val TASK_TYPE_INTERVAL = TASK_TYPE_MOVEMENT

    const val CONTENT_STATUS_ACTIVE = "ce48aa2c-a1c9-43e1-8081-dcba097ccea1"

    const val REST_TYPE_PASSIVE = "b6d5e0fa-1a22-4a2b-bb59-a2ce3d7f03ca"
    const val REST_TYPE_ACTIVE_RECOVERY = "803a3f01-f4cb-42c6-a690-d076d7d1cb8b"

    const val SESSION_STATUS_IN_PROGRESS = "63ac4e6d-2703-4e9a-a3f9-618d68cee762"
    const val SESSION_STATUS_COMPLETED = "01ba16fe-8df1-49a2-b37b-eb47c6bb5774"
    const val SESSION_STATUS_ABANDONED = "b5844d1a-8970-4ced-8e21-cb87b0ba2dd8"

    /** Canonical production ids for the 3 system phases (last-resort seed). */
    const val PHASE_WARMUP = "55D1584E-BAA2-4D1C-8D7B-C23369DA486D"
    const val PHASE_EXERCISES = "A82EB9A4-A049-45CA-B773-2330115CDCB8"
    const val PHASE_COOL_DOWN = "A31A3A67-804A-4617-8188-C4AD6E68B659"
}
