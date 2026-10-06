package com.fitnesslink.fit.model

import java.util.Date

/** Lightweight list row for the Workouts screen / pickers. */
data class WorkoutList(
    val id: String,
    val name: String,
    val time: String,
    val isFavorite: Boolean,
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
    /** Server flag: scheduled inside a contributor program. null = unknown. */
    val partOfContributorProgram: Boolean? = null,
    /** Server-order rank from the last list-view snapshot. */
    val sortIndex: Int? = null
) {
    val isSharedWithMe: Boolean get() = sharedByUserId != null
}
