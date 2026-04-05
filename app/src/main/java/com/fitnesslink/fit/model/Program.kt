package com.fitnesslink.fit.model

import com.fitnesslink.fit.model.api.AuditFields
import com.fitnesslink.fit.model.api.EntityID

data class Program(
    val id: String = "",
    val imageUrl: String = "",
    val name: String = "",
    val time: String = "",
    val location: String = "",
    val trainingLevel: String = "",
    val description: String = "",

    // API-aligned fields
    val statusId: EntityID? = null,
    val contributorId: EntityID? = null,
    val imageId: EntityID? = null,
    val thumbnailId: EntityID? = null,
    val splitId: EntityID? = null,
    val weeks: Int? = null,
    val estimatedTime: Int? = null,
    val showInLibrary: Boolean = true,
    val preBuiltTemplateId: EntityID? = null,
    val audit: AuditFields? = null
)
