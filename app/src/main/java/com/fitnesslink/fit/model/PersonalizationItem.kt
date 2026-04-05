package com.fitnesslink.fit.model

data class PersonalizationItem(
    val id: String = "",
    val text: String = "",
    val selected: Boolean = false,

    // API-aligned fields
    val personalizationId: String? = null,
    val order: Int? = null
)

typealias PersonalizationOption = PersonalizationItem
