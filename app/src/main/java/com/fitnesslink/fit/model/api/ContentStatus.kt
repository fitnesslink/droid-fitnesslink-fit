package com.fitnesslink.fit.model.api

enum class ContentStatusValue(val displayName: String) {
    DRAFT("Draft"),
    APPROVED("Approved"),
    PUBLISHED("Published")
}

data class ContentStatus(
    val id: EntityID,
    val name: String,
    val audit: AuditFields = AuditFields()
)
