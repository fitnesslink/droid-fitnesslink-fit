package com.fitnesslink.fit.model

data class CatalogItem(
    val id: String,
    val title: String,
    val kind: Kind,
    val caption: String
) {
    enum class Kind { PROGRAM, WORKOUT }
}
