package com.fitnesslink.fit.network.dto

import com.google.gson.annotations.SerializedName
import java.util.Date

// Wire shapes for /content/* and /contributors. Mirrors iOS
// `SharedContent.swift`, `FavoritedContent.swift`, `ContributorService.swift`.

/** Polymorphic content kind. Serialized as the C# enum literal (PascalCase). */
enum class ShareableContentKind(val serverName: String) {
    @SerializedName("Program") PROGRAM("Program"),
    @SerializedName("Workout") WORKOUT("Workout"),
    @SerializedName("Movement") MOVEMENT("Movement"),
    @SerializedName("MealPlan") MEAL_PLAN("MealPlan");

    /** `ContentTypeIds` GUID for the favorites table; null for kinds that aren't favoritable. */
    val contentTypeId: String?
        get() = when (this) {
            MOVEMENT -> ContentTypeIds.MOVEMENT
            WORKOUT -> ContentTypeIds.WORKOUT
            PROGRAM -> ContentTypeIds.PROGRAM
            MEAL_PLAN -> null
        }
}

/** Stable ContentType GUIDs — must stay in sync with the platform's ContentTypeIds.cs. */
object ContentTypeIds {
    const val MOVEMENT = "a1f3c2d0-4e5b-4a6f-9c7d-3b2a1f0e9d8c"
    const val WORKOUT = "b2e4d3c1-5f6a-4b7e-8d9c-2a1f0e9d8c7b"
    const val PROGRAM = "c3d5e4b2-6a7f-4c8d-9e8a-1f0e9d8c7b6a"
}

/** Recipient-side lifecycle of a share (server `ShareStatus`, PascalCase). */
enum class ShareStatus {
    @SerializedName("Pending") PENDING,
    @SerializedName("Accepted") ACCEPTED,
    @SerializedName("Rejected") REJECTED,
    @SerializedName("Removed") REMOVED
}

/** Response of `POST /content/favorites/{id}?kind=` — authoritative state after the toggle. */
data class ToggleFavoriteResponse(
    val isFavorited: Boolean
)

/** `POST /content/share` (204). */
data class ContentShareRequest(
    val contentId: String,
    val kind: ShareableContentKind,
    val sharedWithUserId: String
)

/** `GET /content/shared` row; also returned by accept / decline. */
data class SharedContentDto(
    val id: String,
    val sharedByUserId: String,
    val sharedByUserName: String = "",
    val sharedWithUserId: String,
    val kind: ShareableContentKind? = null,
    val contentId: String,
    val contentTitle: String = "",
    val imageId: String? = null,
    val sharedAtUtc: Date? = null,
    /** Null when the server sends a status this build doesn't know. */
    val status: ShareStatus? = null
) {
    val isPending: Boolean get() = status == ShareStatus.PENDING
}

/**
 * Slim `GET /contributors` row for the filter picker (the server's
 * `ContributorDto` carries much more; extra keys are ignored).
 */
data class ContributorSummaryDto(
    val id: String,
    val type: String = "",
    val displayName: String? = null
) {
    val label: String get() = displayName?.trim().takeUnless { it.isNullOrEmpty() } ?: "Unnamed"
}
