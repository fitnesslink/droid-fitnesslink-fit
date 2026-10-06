package com.fitnesslink.fit.model.api

import com.fitnesslink.fit.model.TolerantAdapterFactory
import com.google.gson.JsonObject
import com.google.gson.annotations.JsonAdapter
import java.util.UUID

@JsonAdapter(Movement.Adapter::class)
data class Movement(
    val id: EntityID = UUID(0L, 0L),
    val name: String = "",
    val description: String? = null,
    val videoId: EntityID? = null,
    val statusId: EntityID? = null,
    val contributorId: EntityID? = null,
    val imageId: EntityID? = null,
    val relatedToId: EntityID? = null,
    val thumbnailId: EntityID? = null,
    val preBuiltTemplateId: EntityID? = null,
    /** Pre-resolved SAS URLs from the backend payload. */
    val imageUrl: String? = null,
    val thumbnailUrl: String? = null,
    val videoUrl: String? = null,
    /** Mux ingest state for the form video. READY = `videoUrl` is a playable signed manifest. */
    val videoStatus: MuxStatus? = null,
    /** Mux aspect-ratio string (e.g. "16:9"). */
    val videoAspectRatio: String? = null,
    val videoDurationMs: Int? = null,
    /** Optional 9:16 portrait variant; resolve through the `effective*` accessors. */
    val phoneVideoId: EntityID? = null,
    val phoneVideoUrl: String? = null,
    val phoneVideoStatus: MuxStatus? = null,
    val phoneVideoAspectRatio: String? = null,
    val phoneVideoDurationMs: Int? = null,
    /** Many-to-many anatomy tags (union of primary + secondary). */
    val anatomyIds: List<EntityID> = emptyList(),
    /** Many-to-many equipment tags, server-ordered. */
    val equipmentIds: List<EntityID> = emptyList(),
    val isFavorite: Boolean? = null,
    val audit: AuditFields? = null,
    /** Sharing flags from /movements/list-view + /movements/{id}. */
    val isSharedWithMe: Boolean? = false,
    val sharedByUserId: EntityID? = null,
    /** Plain-text overview shown above the structured sections. */
    val summary: String? = null,
    /** Structured, ordered instructional content (Setup / Execution / Breathe / Tips). */
    val sections: List<MovementSection> = emptyList()
) {
    val createdAt get() = audit?.timestamp

    val isVideoReady: Boolean get() = (videoStatus ?: MuxStatus.READY) == MuxStatus.READY
    val isPhoneVideoReady: Boolean get() = phoneVideoId != null && (phoneVideoStatus ?: MuxStatus.READY) == MuxStatus.READY

    /** FULL-SCREEN playback: the 9:16 variant when present and ready, else the landscape video. */
    val effectiveVideoId: EntityID? get() = if (isPhoneVideoReady) phoneVideoId else videoId
    val effectiveVideoUrl: String? get() = if (isPhoneVideoReady) phoneVideoUrl else videoUrl
    val effectiveVideoStatus: MuxStatus? get() = if (isPhoneVideoReady) phoneVideoStatus else videoStatus
    val effectiveVideoAspectRatio: String? get() = if (isPhoneVideoReady) phoneVideoAspectRatio else videoAspectRatio

    private val hasLandscapeVideo: Boolean get() = videoId != null && isVideoReady

    /** INLINE / horizontal surfaces: landscape first, phone variant only as a fallback. */
    val inlineVideoId: EntityID? get() = if (hasLandscapeVideo) videoId else effectiveVideoId
    val inlineVideoUrl: String? get() = if (hasLandscapeVideo) videoUrl else effectiveVideoUrl
    val inlineVideoStatus: MuxStatus? get() = if (hasLandscapeVideo) videoStatus else effectiveVideoStatus
    val inlineVideoAspectRatio: String? get() = if (hasLandscapeVideo) videoAspectRatio else effectiveVideoAspectRatio

    class Adapter : TolerantAdapterFactory<Movement>(Movement::class.java) {
        override fun preprocess(raw: JsonObject) {
            // A malformed sections payload falls back to empty rather than failing the decode.
            raw.get("sections")?.let { if (!it.isJsonArray) raw.remove("sections") }
            if (!raw.has("isSharedWithMe")) raw.addProperty("isSharedWithMe", false)
        }
    }
}

/** One typed, ordered section of a movement's structured description. */
@JsonAdapter(MovementSection.Adapter::class)
data class MovementSection(
    val id: EntityID = UUID(0L, 0L),
    /** The server's section-type Name (e.g. "Setup", "Breathe"). */
    val type: String = "",
    val note: String? = null,
    val ordinal: Int = 0,
    val steps: List<MovementStep> = emptyList()
) {
    class Adapter : TolerantAdapterFactory<MovementSection>(MovementSection::class.java)
}

/** A single ordered step within a [MovementSection]. */
@JsonAdapter(MovementStep.Adapter::class)
data class MovementStep(
    val id: EntityID = UUID(0L, 0L),
    /** Optional heading (e.g. "Inhale" / "Exhale" on the Breathe section). */
    val label: String? = null,
    val text: String = "",
    val ordinal: Int = 0
) {
    class Adapter : TolerantAdapterFactory<MovementStep>(MovementStep::class.java)
}
