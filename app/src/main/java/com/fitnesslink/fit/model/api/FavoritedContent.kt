package com.fitnesslink.fit.model.api

import java.util.Date

data class FavoritedContent(
    val id: EntityID,
    val contributorId: EntityID? = null,
    val userId: EntityID,
    val contentId: EntityID,
    val contentTypeId: EntityID,
    val dateFavorited: Date,
    val audit: AuditFields = AuditFields()
)
