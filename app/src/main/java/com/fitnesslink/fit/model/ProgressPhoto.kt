package com.fitnesslink.fit.model

import java.util.Date
import java.util.UUID

enum class PhotoAngle(val displayName: String) {
    FRONT("Front"),
    SIDE("Side"),
    BACK("Back");

    val sfIcon: String
        get() = when (this) {
            FRONT -> "person"
            SIDE -> "person"
            BACK -> "person"
        }

    val next: PhotoAngle?
        get() = when (this) {
            FRONT -> SIDE
            SIDE -> BACK
            BACK -> null
        }

    companion object {
        /** Wire value ("Front" | "Side" | "Back", any case); null for unknown / absent. */
        fun fromWire(raw: String?): PhotoAngle? {
            val value = raw?.trim() ?: return null
            return entries.firstOrNull { it.displayName.equals(value, true) || it.name.equals(value, true) }
        }
    }
}

data class AnglePhoto(
    val id: String = UUID.randomUUID().toString(),
    val angle: PhotoAngle = PhotoAngle.FRONT,
    val imageData: ByteArray? = null,
    val imageName: String = "",
    /** Server media asset id (`beforeImageId`) — render with `MediaRef.ProgressPhoto`. */
    val imageId: String? = null,
    val notes: String = "",
    val createdAt: Date = Date()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnglePhoto) return false
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}

data class ProgressPhotoEntry(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val date: Date = Date(),
    val photos: List<AnglePhoto> = emptyList(),
    val notes: String = "",
    val createdAt: Date = Date()
) {
    fun photo(angle: PhotoAngle): AnglePhoto? = photos.firstOrNull { it.angle == angle }

    val hasAllAngles: Boolean
        get() = PhotoAngle.entries.all { angle -> photos.any { it.angle == angle } }
}
