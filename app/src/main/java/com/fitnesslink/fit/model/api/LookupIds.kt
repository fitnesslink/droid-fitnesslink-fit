package com.fitnesslink.fit.model.api

// Hard-coded fallback GUIDs for the backend's seeded lookup rows. The live
// values come from the classification endpoints (ClassificationStore, wave
// 0.10); these constants cover the cold-cache / offline path. Ported 1:1
// from iOS `Models/API/WorkoutTaskInput.swift` + `FavoritedContent.swift`.

object WorkoutTaskTypeId {
    const val movement = "168d85cd-f0e9-4a75-b3ad-8f4d0583d4fa"
    const val rest = "99d9354b-c3ff-4d15-9578-1d6813c16116"
    const val interval = movement

    fun fallbackByName(name: String): String = when (name.lowercase()) {
        "movement" -> movement
        "rest" -> rest
        "interval" -> interval
        else -> movement
    }
}

object RestTypeId {
    const val passive = "b6d5e0fa-1a22-4a2b-bb59-a2ce3d7f03ca"
    const val activeRecovery = "803a3f01-f4cb-42c6-a690-d076d7d1cb8b"

    fun fallbackByName(name: String): String = when (name.lowercase()) {
        "passive" -> passive
        "active recovery", "activerecovery" -> activeRecovery
        else -> passive
    }

    /** Reverse map for the cold-cache path; null for genuinely unknown ids. */
    fun fallbackName(id: String): String? = when (id.lowercase()) {
        passive -> "Passive"
        activeRecovery -> "Active Recovery"
        else -> null
    }
}

object WorkoutSessionStatusId {
    const val inProgress = "63ac4e6d-2703-4e9a-a3f9-618d68cee762"
    const val completed = "01ba16fe-8df1-49a2-b37b-eb47c6bb5774"
    const val abandoned = "b5844d1a-8970-4ced-8e21-cb87b0ba2dd8"

    fun fallbackByName(name: String): String = when (name.lowercase()) {
        "inprogress" -> inProgress
        "completed" -> completed
        "abandoned" -> abandoned
        else -> inProgress
    }
}

/** `ContentTypeIds` GUIDs of the polymorphic favorites table. */
object ContentTypeIds {
    const val movement = "a1f3c2d0-4e5b-4a6f-9c7d-3b2a1f0e9d8c"
    const val workout = "b2e4d3c1-5f6a-4b7e-8d9c-2a1f0e9d8c7b"
    const val program = "c3d5e4b2-6a7f-4c8d-9e8a-1f0e9d8c7b6a"
}
