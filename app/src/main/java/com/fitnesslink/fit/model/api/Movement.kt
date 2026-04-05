package com.fitnesslink.fit.model.api

data class Movement(
    val id: EntityID,
    val name: String,
    val description: String? = null,
    val videoId: EntityID? = null,
    val statusId: EntityID,
    val contributorId: EntityID? = null,
    val imageId: EntityID? = null,
    val relatedToId: EntityID? = null,
    val thumbnailId: EntityID? = null,
    val preBuiltTemplateId: EntityID? = null,
    val audit: AuditFields = AuditFields()
)
