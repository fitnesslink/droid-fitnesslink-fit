package com.fitnesslink.fit.model

import com.fitnesslink.fit.model.api.AuditFields

data class Personalization(
    val id: String = "",
    val name: String = "",
    val singleSelection: Boolean = false,
    val options: List<PersonalizationItem> = emptyList(),

    // API-aligned fields
    val alias: String? = null,
    val order: Int? = null,
    val audit: AuditFields? = null
)
