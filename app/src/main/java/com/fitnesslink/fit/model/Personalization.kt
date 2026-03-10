package com.fitnesslink.fit.model

data class Personalization(
    val id: String = "",
    val name: String = "",
    val singleSelection: Boolean = false,
    val options: List<PersonalizationItem> = emptyList()
)
