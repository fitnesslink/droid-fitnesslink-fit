package com.fitnesslink.fit.model.api

data class Anatomy(
    val id: EntityID,
    val name: String,
    val sortOrder: Int? = null,
    val audit: AuditFields = AuditFields()
)
