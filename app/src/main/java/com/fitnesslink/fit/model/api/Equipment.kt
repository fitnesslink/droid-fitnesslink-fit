package com.fitnesslink.fit.model.api

data class Equipment(
    val id: EntityID,
    val name: String,
    val audit: AuditFields = AuditFields()
)
