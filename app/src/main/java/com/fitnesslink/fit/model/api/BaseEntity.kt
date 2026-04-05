package com.fitnesslink.fit.model.api

import java.util.Date
import java.util.UUID

typealias EntityID = UUID

data class AuditFields(
    val timestamp: Date? = null,
    val lastUpdateDate: Date? = null,
    val isDeleted: Boolean = false,
    val dateDeleted: Date? = null,
    val createdBy: EntityID? = null
)

enum class WorkoutSessionType(val value: Int, val displayName: String) {
    STANDARD(0, "Standard"),
    GUIDED(1, "Guided"),
    FREE_STYLE(2, "Free Style");

    companion object {
        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: STANDARD
    }
}
