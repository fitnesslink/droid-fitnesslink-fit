package com.fitnesslink.fit.model.api

data class Equipment(
    val id: EntityID,
    val name: String,
    val sortOrder: Int? = null,
    /**
     * Flagged on the server as one of the curated chip-strip taxa shown
     * directly in the filter bars. Anything else lives in the "More filters"
     * drawer, grouped by [category]. (iOS `Equipment.isPrimary`.)
     */
    val isPrimary: Boolean? = null,
    /** Grouping label for the drawer (e.g. "free_weights", "machines"). Server-controlled. */
    val category: String? = null,
    val audit: AuditFields = AuditFields()
)
