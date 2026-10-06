package com.fitnesslink.fit.network.dto

import com.google.gson.annotations.SerializedName

data class PaginatedResponse<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val totalPages: Int
)

// MARK: - Sync (contract mirrors iOS Services/Sync/SyncManager.swift)

/**
 * `GET core/sync/pull?cursor=&entities=a,b`.
 *
 * Gson skips Kotlin default values for absent keys, so every collection is
 * nullable and the reader (`SyncManager.pull`) coalesces to empty.
 */
data class SyncPullResponse(
    val cursor: String?,
    val changes: List<SyncChange>?,
    val hasMore: Boolean = false
)

/**
 * One pulled row. `payload` is the entity encoded as a JSON *string*
 * (not an object) — it is handed verbatim to
 * `DatabaseManager.applySyncPayload(entityType, entityId, payload)`.
 */
data class SyncChange(
    val entityType: String,
    val entityId: String,
    val isDeleted: Boolean = false,
    val payload: String?
)

/** `POST core/sync/push` body. */
data class SyncPushRequest(
    val changes: List<SyncPushChange>
)

/** One queued local write. `payload` is the `sync_queue.payload` JSON string. */
data class SyncPushChange(
    val entityType: String,
    val entityId: String,
    val action: String,
    val payload: String
)

/**
 * `POST core/sync/push` response. The backend returns `{success, syncedAt}`
 * and only sometimes echoes `idMappings`; every field is optional so an
 * absent array cannot fail the decode (the iOS contract originally
 * required it and crashed when it was missing).
 */
data class SyncPushResponse(
    val success: Boolean = false,
    val syncedAt: String? = null,
    val idMappings: List<SyncIdMapping>? = null
)

data class SyncIdMapping(
    val entityType: String,
    val localId: String,
    val serverId: String
)

// Auth
data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val refreshToken: String,
    val expiresIn: String,
    val email: String
)

// User creation
data class CreateUserRequest(
    val firebaseId: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val username: String
)

// Personalization save
data class UserPersonalizationRequest(
    val personalizationId: String,
    val personalizationOptionId: String
)

// Session
data class StartSessionRequest(
    val workoutId: String,
    val programId: String?,
    val userId: String
)

data class CompleteSessionRequest(
    val rpeId: String?
)

data class LogHistoryRequest(
    val workoutTaskId: String,
    val workoutId: String,
    val programId: String?,
    val userId: String,
    val logDate: String,
    val reps: Int,
    @SerializedName("set") val setNumber: Int,
    val intervalSeconds: Int?,
    val weightLifted: Double?
)

data class GoalProgressRequest(
    val currentValue: Double
)

data class UnreadCountResponse(
    val count: Int
)

data class RegisterDeviceTokenRequest(
    val token: String,
    val platform: String = "android"
)

data class ShareContentRequest(
    val contentId: String,
    val userId: String
)
