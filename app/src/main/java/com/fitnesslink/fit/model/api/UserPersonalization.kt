package com.fitnesslink.fit.model.api

data class UserPersonalization(
    val id: EntityID,
    val personalizationId: EntityID,
    val personalizationOptionId: EntityID,
    val userId: EntityID,
    val audit: AuditFields = AuditFields()
)
