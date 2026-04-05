package com.fitnesslink.fit.model.api

data class Rpe(
    val id: EntityID,
    val name: String,
    val fromValue: Int,
    val toValue: Int? = null,
    val audit: AuditFields = AuditFields()
)
