package com.fitnesslink.fit.model.api

data class FLUser(
    val id: EntityID,
    val firstName: String,
    val lastName: String,
    val email: String,
    val alias: String? = null,
    val phone: String? = null,
    val firebaseId: String,
    val username: String,
    val country: String? = null,
    val profileImageId: EntityID? = null,
    val companyId: EntityID? = null,
    val isActive: Boolean = true,
    val requirePersonalization: Boolean = true,
    val audit: AuditFields = AuditFields()
) {
    val fullName: String get() = "$firstName $lastName"
}
