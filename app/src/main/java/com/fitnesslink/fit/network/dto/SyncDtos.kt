package com.fitnesslink.fit.network.dto

import com.google.gson.annotations.SerializedName

data class PaginatedResponse<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val totalPages: Int
)

// Sync Pull
data class SyncPullResponse(
    val changes: Map<String, List<SyncChange>>,
    val cursor: String,
    val hasMore: Boolean
)

data class SyncChange(
    val entityId: String,
    val entityType: String,
    val operation: String,
    val changedAt: String,
    val data: Map<String, Any?>?
)

// Sync Push
data class SyncPushRequest(
    val changes: List<SyncPushChange>
)

data class SyncPushChange(
    val entityId: String,
    val entityType: String,
    val operation: String,
    val data: Map<String, Any?>?,
    val idempotencyKey: String,
    val clientTimestamp: String
)

data class SyncPushResponse(
    val accepted: List<String>,
    val rejected: List<SyncRejection>,
    val cursor: String
)

data class SyncRejection(
    val entityId: String,
    val reason: String,
    val serverVersion: String?
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
