package com.fitnesslink.fit.model

import java.util.Date

/** Lightweight list row for the Programs screen / pickers. */
data class ProgramList(
    val id: String,
    val name: String,
    val time: String,
    val isFavorite: Boolean,
    val weeks: Int? = null,
    val trainingLevel: String = "",
    val imageId: String? = null,
    val thumbnailId: String? = null,
    /** Pre-resolved SAS URLs from the backend payload. */
    val imageUrl: String? = null,
    val thumbnailUrl: String? = null,
    /** Null when the user authored it; non-null for system / partner templates. */
    val contributorId: String? = null,
    /** Set after refresh when shared with the current user. Live-only. */
    val sharedByUserId: String? = null,
    /** Server-side creation date (`audit.timestamp`), persisted locally. */
    val createdAt: Date? = null,
    /** Server-order rank from the last list-view snapshot. */
    val sortIndex: Int? = null
) {
    val isSharedWithMe: Boolean get() = sharedByUserId != null
}
