package com.fitnesslink.fit.media

import java.util.Date

data class MediaRefWire(
    val type: String,
    val id: String
)

data class ResolveMediaRequest(
    val items: List<MediaRefWire>
)

data class ResolvedMediaItem(
    val type: String,
    val id: String,
    val url: String?,
    val expiresAt: Date?,
    val contentHash: String?,
    val reason: String?
)

data class ResolveMediaResponse(
    val items: List<ResolvedMediaItem>
)

data class MediaManifest(
    val generatedAt: Date,
    val validUntil: Date,
    val items: List<ResolvedMediaItem>
)
