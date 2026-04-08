package com.fitnesslink.fit.sync

import java.util.Date

enum class ConflictResolution { KEEP_LOCAL, KEEP_SERVER }

object ConflictResolver {
    private val serverAuthoritativeDomains = setOf(
        "movements", "workouts", "programs", "classification",
        "anatomy", "equipment", "training_levels", "rpe_scales",
        "personalizations"
    )

    fun resolve(
        entityType: String,
        localTimestamp: Date?,
        serverTimestamp: Date?
    ): ConflictResolution {
        if (entityType in serverAuthoritativeDomains) return ConflictResolution.KEEP_SERVER

        val local = localTimestamp ?: return ConflictResolution.KEEP_SERVER
        val server = serverTimestamp ?: return ConflictResolution.KEEP_LOCAL

        return if (server.after(local)) ConflictResolution.KEEP_SERVER else ConflictResolution.KEEP_LOCAL
    }
}
