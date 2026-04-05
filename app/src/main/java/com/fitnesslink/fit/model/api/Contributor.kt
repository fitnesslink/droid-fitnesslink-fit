package com.fitnesslink.fit.model.api

data class Contributor(
    val id: EntityID,
    val userId: EntityID? = null,
    val companyId: EntityID? = null,
    val isApproved: Boolean = false,
    val audit: AuditFields = AuditFields()
)
