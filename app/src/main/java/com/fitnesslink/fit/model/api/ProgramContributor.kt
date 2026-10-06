package com.fitnesslink.fit.model.api

import com.fitnesslink.fit.model.TolerantAdapterFactory
import com.google.gson.annotations.JsonAdapter
import java.util.Date
import java.util.UUID

/**
 * Public contributor profile embedded in the `GET /programs/{id}` and
 * `GET /workouts/{id}` detail payloads ("About the Coach"). Live-only — not
 * persisted to SQLite.
 */
@JsonAdapter(ProgramContributor.Adapter::class)
data class ProgramContributor(
    val id: EntityID = UUID(0L, 0L),
    val type: String = "",
    val displayName: String? = null,
    val headline: String? = null,
    val bio: String? = null,
    /** Avatar asset id (explicit avatar or the fallback profile image). */
    val avatarMediaAssetId: EntityID? = null,
    /** Pre-resolved SAS URL for the avatar. */
    val avatarUrl: String? = null,
    val socialLinks: List<ContributorSocialLink> = emptyList(),
    val credentials: List<ContributorCredential> = emptyList(),
    val achievements: List<ContributorAchievement> = emptyList()
) {
    class Adapter : TolerantAdapterFactory<ProgramContributor>(ProgramContributor::class.java)
}

@JsonAdapter(ContributorSocialLink.Adapter::class)
data class ContributorSocialLink(
    val id: EntityID = UUID(0L, 0L),
    val platform: String = "",
    val url: String = "",
    val displayHandle: String? = null,
    val sortOrder: Int = 0
) {
    /** User-facing button label: the handle if present, else the capitalized platform. */
    val label: String
        get() {
            if (!displayHandle.isNullOrEmpty()) return displayHandle
            return if (platform.isEmpty()) "Link" else platform.replaceFirstChar { it.uppercase() }
        }

    class Adapter : TolerantAdapterFactory<ContributorSocialLink>(ContributorSocialLink::class.java)
}

@JsonAdapter(ContributorCredential.Adapter::class)
data class ContributorCredential(
    val id: EntityID = UUID(0L, 0L),
    val kind: String = "",
    val name: String = "",
    val issuingOrganization: String = "",
    val issuedOn: Date? = null,
    val expiresOn: Date? = null,
    val verificationUrl: String? = null,
    val sortOrder: Int = 0
) {
    /** e.g. "CSCS — NSCA". */
    val displayText: String get() = if (issuingOrganization.isEmpty()) name else "$name — $issuingOrganization"

    class Adapter : TolerantAdapterFactory<ContributorCredential>(ContributorCredential::class.java)
}

@JsonAdapter(ContributorAchievement.Adapter::class)
data class ContributorAchievement(
    val id: EntityID = UUID(0L, 0L),
    val title: String = "",
    val year: Int? = null,
    val description: String? = null,
    val mediaAssetId: EntityID? = null,
    val externalUrl: String? = null,
    val sortOrder: Int = 0
) {
    /** e.g. "National Champion (2021)". */
    val displayTitle: String get() = if (year != null) "$title ($year)" else title

    class Adapter : TolerantAdapterFactory<ContributorAchievement>(ContributorAchievement::class.java)
}
