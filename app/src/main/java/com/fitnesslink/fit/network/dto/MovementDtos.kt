package com.fitnesslink.fit.network.dto

import java.util.Date

// Wire shapes for /movements. Mirrors iOS `MovementService.swift`.

/**
 * `POST /movements` body (server `CreateMovementRequest`). The response is
 * the new row's id as a bare JSON string, not the entity. Personal
 * movements leave `contributorId` null; a zero `statusId` lets the server
 * pick its "Draft" status and a zero `createdBy` stamps the caller.
 */
data class CreateMovementRequest(
    val name: String,
    val description: String? = null,
    val contributorId: String? = null,
    val statusId: String = ZERO_GUID,
    val createdBy: String = ZERO_GUID,
    val anatomyIds: List<String> = emptyList(),
    val equipmentIds: List<String> = emptyList(),
    /** MediaAsset ids. Omitting `imageId` falls back to the platform default image. */
    val imageId: String? = null,
    val thumbnailId: String? = null
)

/** `GET /movements/favorites` row. */
data class MovementFavoriteDto(
    val movementId: String,
    val favoritedAt: Date
)

/** `GET /movements/recents?limit=` row (server caps at 50, upsert on (user, movement)). */
data class RecentMovementDto(
    val movementId: String,
    val accessedAt: Date
)

/** `role` query values for `/movements/list-view`. */
object MovementAnatomyRole {
    /** Match on primary anatomy only (default — "Chest" doesn't pull every press). */
    const val PRIMARY = "primary"
    /** Include secondary / stabilizer matches. */
    const val ANY = "any"
}
