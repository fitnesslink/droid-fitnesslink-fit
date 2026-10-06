package com.fitnesslink.fit.media

/**
 * Stable, ID-based reference to a media asset. Persisted in entity models
 * and passed to FLImageView/MediaURLProvider. Never holds a SAS URL itself.
 */
sealed class MediaRef {
    data class MovementThumbnail(val id: String) : MediaRef()
    data class MovementVideo(val id: String) : MediaRef()
    data class WorkoutThumbnail(val id: String) : MediaRef()
    data class ProgramThumbnail(val id: String) : MediaRef()
    data class UserPhoto(val id: String) : MediaRef()
    data class ProgressPhoto(val id: String) : MediaRef()

    val entityId: String
        get() = when (this) {
            is MovementThumbnail -> id
            is MovementVideo -> id
            is WorkoutThumbnail -> id
            is ProgramThumbnail -> id
            is UserPhoto -> id
            is ProgressPhoto -> id
        }

    /** Wire-format type string shared with the backend. */
    val typeWireValue: String
        get() = when (this) {
            is MovementThumbnail -> "MovementThumbnail"
            is MovementVideo -> "MovementVideo"
            is WorkoutThumbnail -> "WorkoutThumbnail"
            is ProgramThumbnail -> "ProgramThumbnail"
            is UserPhoto -> "UserPhoto"
            is ProgressPhoto -> "ProgressPhoto"
        }

    /**
     * File extension for the single-file prefetch cache (thumbnails / images).
     * Movement videos are NOT cached through this path — `MediaCacheStore`
     * keeps them under their own playback-id-stamped names
     * (`movement-video-mp4/{entityId}.{playbackId}.mp4`) — so a
     * `MovementVideo` ref never reaches it.
     */
    val fileExtensionHint: String
        get() = "img"

    val isVideo: Boolean get() = this is MovementVideo

    companion object {
        fun fromWire(type: String, id: String): MediaRef? = when (type) {
            "MovementThumbnail" -> MovementThumbnail(id)
            "MovementVideo" -> MovementVideo(id)
            "WorkoutThumbnail" -> WorkoutThumbnail(id)
            "ProgramThumbnail" -> ProgramThumbnail(id)
            "UserPhoto" -> UserPhoto(id)
            "ProgressPhoto" -> ProgressPhoto(id)
            else -> null
        }
    }
}
