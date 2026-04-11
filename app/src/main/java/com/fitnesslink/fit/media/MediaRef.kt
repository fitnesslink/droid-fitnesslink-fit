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

    /** File extension hint used by the local prefetch cache. */
    val fileExtensionHint: String
        get() = if (this is MovementVideo) "mp4" else "img"

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
