package com.fitnesslink.fit.network.dto

import java.util.Date

// Wire shapes for /measurements and /progress-photos. Mirrors iOS
// `BodyTrackingService.swift`. Weight (`/weight`) uses the `WeightEntry`
// model directly on both platforms.

/**
 * `POST /measurements` (204). The server stores ONE row per (anatomy,
 * value); a multi-part entry fans out N requests. `value` is centimetres.
 * The client-supplied `id` makes a replay upsert the same row.
 */
data class CreateMeasurementRequest(
    val anatomyId: String,
    val value: Double,
    val logDate: Date,
    val id: String? = null
)

/** `GET /measurements/me` row. Group by `logDate` day to rebuild an entry. */
data class BodyMeasurementDto(
    val id: String,
    val userId: String = "",
    val anatomyId: String,
    val value: Double,
    val logDate: Date,
    val createdAt: Date? = null
)

/**
 * `POST /progress-photos` — step 2 of the two-hop upload (step 1 is
 * `POST /media` which yields the `imageId`). The response is the new row id
 * as a bare JSON string. `angle` is "Front" | "Side" | "Back" | null.
 */
data class CreateProgressPhotoRequest(
    val imageId: String,
    val takenAt: Date,
    val notes: String = "",
    val angle: String? = null
)

/**
 * `GET /progress-photos/me` row: one image per row. `angle` is null for
 * legacy rows (reconstruct from upload order front → side → back within the
 * day). `logDate` drives day grouping; `createdAt` intra-day order.
 */
data class ProgressPhotoDto(
    val id: String,
    val userId: String = "",
    val beforeImageId: String? = null,
    val afterImageId: String? = null,
    val logDate: Date? = null,
    val angle: String? = null,
    val createdAt: Date? = null
)
