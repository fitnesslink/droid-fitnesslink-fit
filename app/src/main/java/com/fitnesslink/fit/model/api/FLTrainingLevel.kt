package com.fitnesslink.fit.model.api

data class FLTrainingLevel(
    val id: EntityID,
    val name: String,
    val audit: AuditFields = AuditFields()
)
