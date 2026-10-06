package com.fitnesslink.fit.media

import java.util.Date

/** Wire DTOs for the /media endpoints. Mirrors iOS `MediaService.swift`. */
data class MediaRefWire(
    val type: String,
    val id: String
)

data class ResolveMediaRequest(
    val items: List<MediaRefWire>
)

/**
 * One row of `POST media/resolve` / `GET media/me/today`. `url` is null when
 * the caller can't see the asset (see `reason`). `expiresAt` is the real
 * SAS / Mux JWT lifetime threaded through from the server.
 */
data class ResolvedMediaItem(
    val type: String,
    val id: String,
    val url: String?,
    val expiresAt: Date?,
    val contentHash: String?,
    val reason: String?
)

data class MediaManifest(
    val generatedAt: Date,
    val validUntil: Date,
    val items: List<ResolvedMediaItem>
)

/**
 * Response from `POST media`. `mediaId` is the unified MediaAsset id and is
 * what every consumer attaches (user.profileImageId, movement.videoId,
 * workout.thumbnailId, ...).
 */
data class MediaUploadResponse(
    val mediaId: String,
    val container: String? = null,
    val blobName: String? = null,
    val url: String? = null,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val kind: String? = null
)

/**
 * Mirrors the backend `MuxAssetStatus` enum (iOS `MuxStatus`). `UNKNOWN` is a
 * forward-compat slot so an unfamiliar wire value never throws.
 */
enum class MuxStatus(val wire: String) {
    NONE("None"),
    UPLOADING("Uploading"),
    PREPARING("Preparing"),
    READY("Ready"),
    ERRORED("Errored"),
    UNKNOWN("");

    val isTerminal: Boolean get() = this == READY || this == ERRORED

    companion object {
        fun fromWire(value: String?): MuxStatus =
            entries.firstOrNull { it.wire.isNotEmpty() && it.wire.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

/** `GET media/{id}` — detail row the upload poller watches until Mux is terminal. */
data class MediaAssetDetail(
    val mediaAssetId: String,
    val kind: String? = null,
    val status: String? = null,
    val ownerId: String? = null,
    val container: String? = null,
    val blobName: String? = null,
    val muxPlaybackId: String? = null,
    val muxAssetId: String? = null,
    val videoUrl: String? = null,
    val muxStatus: String? = null,
    val muxDurationMs: Int? = null,
    val muxAspectRatio: String? = null,
    val muxErrors: List<String>? = null
) {
    val muxStatusEnum: MuxStatus get() = MuxStatus.fromWire(muxStatus)
    val isTerminal: Boolean get() = muxStatusEnum.isTerminal
}
