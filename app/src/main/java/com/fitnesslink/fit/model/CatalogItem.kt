package com.fitnesslink.fit.model

import java.text.DateFormat
import java.util.Date

data class CatalogItem(
    val id: String,
    val title: String,
    val kind: Kind,
    val caption: String,
    val imageId: String? = null,
    val thumbnailId: String? = null,
    /** Pre-resolved SAS URLs from the backend payload. */
    val imageUrl: String? = null,
    val thumbnailUrl: String? = null,
    /** Non-null when shared *with* the current user (drives the "Shared by …" badge). */
    val sharedByUserId: String? = null,
    /** Server-side creation date from `audit.timestamp`. Never synthesized client-side. */
    val createdAt: Date? = null,
    /** Local favorite flag (persisted per-kind). */
    val isFavorite: Boolean = false,
    /** Lowercased anatomy / equipment tag ids for client-side chip filtering. */
    val anatomyIds: Set<String> = emptySet(),
    val equipmentIds: Set<String> = emptySet()
) {
    enum class Kind { PROGRAM, WORKOUT, MOVEMENT }

    val isSharedWithMe: Boolean get() = sharedByUserId != null

    /** Localized creation date for display, or null so the view can omit the label. */
    val formattedCreatedAt: String?
        get() = createdAt?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(it) }

    /** True when the item has any image / thumbnail to render. */
    val hasMedia: Boolean
        get() = thumbnailUrl != null || imageUrl != null || thumbnailId != null || imageId != null
}

/**
 * Combined My Content ordering: newest-created first; nil `createdAt` sorts
 * to the END; ties break by title, case-insensitive ascending.
 */
fun List<CatalogItem>.sortedByCreatedDescending(): List<CatalogItem> = sortedWith { lhs, rhs ->
    val l = lhs.createdAt
    val r = rhs.createdAt
    when {
        l != null && r != null -> if (l != r) r.compareTo(l) else lhs.title.compareTo(rhs.title, ignoreCase = true)
        l != null -> -1
        r != null -> 1
        else -> lhs.title.compareTo(rhs.title, ignoreCase = true)
    }
}
