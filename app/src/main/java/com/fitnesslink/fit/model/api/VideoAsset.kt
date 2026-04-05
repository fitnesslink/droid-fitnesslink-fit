package com.fitnesslink.fit.model.api

data class VideoAsset(
    val id: EntityID,
    val name: String,
    val mimeType: String,
    val friendlyName: String,
    val contributorId: EntityID? = null,
    val audit: AuditFields = AuditFields()
)
