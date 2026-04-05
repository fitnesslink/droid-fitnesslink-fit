package com.fitnesslink.fit.model.api

enum class AdvancedMovementType(val value: Int, val displayName: String) {
    CIRCUIT(1, "Circuit"),
    SUPERSET(2, "Superset"),
    INTERVAL(3, "Interval");

    companion object {
        fun fromValue(value: Int) = entries.firstOrNull { it.value == value }
    }
}

data class AdvancedMovement(
    val id: EntityID,
    val name: String,
    val audit: AuditFields = AuditFields()
)
