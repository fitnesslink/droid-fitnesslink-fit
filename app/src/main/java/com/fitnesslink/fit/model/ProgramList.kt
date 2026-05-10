package com.fitnesslink.fit.model

data class ProgramList(
    val id: String,
    val name: String,
    val time: String,
    val isFavorite: Boolean,
    val weeks: Int? = null,
    val trainingLevel: String = ""
)
