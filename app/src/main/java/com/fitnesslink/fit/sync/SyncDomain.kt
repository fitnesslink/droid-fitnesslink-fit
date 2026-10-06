package com.fitnesslink.fit.sync

/**
 * Sync domains — a 1:1 port of iOS `SyncDomain` (Services/Sync/SyncManager.swift).
 *
 * [wireName] is the literal the server uses in `/sync/pull?entities=` and in
 * `SyncChange.entityType`, and the literal producers write into
 * `sync_queue.entityType` so the matching drain can find its rows. Producer
 * and drainer must agree on it, which is why view models reference the
 * constants on [SyncManager] (e.g. `SyncManager.WATER_ENTRIES_TYPE`) rather
 * than spelling the string.
 *
 * The first 19 entries are the pull domains iOS declares. The two trailing
 * entries ([WORKOUT_PHASES], [PROGRAM_ENROLLMENTS]) are queue-only: iOS keeps
 * them as plain string tags (`WorkoutPhaseStore.queueEntityType`,
 * `SyncManager.programEnrollmentType`) because they are never pulled, but
 * they do take part in the drain order, so they live here with
 * [isPullDomain] = false and are excluded from `entities=` lists.
 */
enum class SyncDomain(val wireName: String, val isPullDomain: Boolean = true) {
    WORKOUTS("workouts"),
    PROGRAMS("programs"),
    MOVEMENTS("movements"),
    GOALS("goals"),
    HABITS("habits"),
    SESSIONS("sessions"),
    SESSION_PROGRESS("session-progress"),
    FOOD_ENTRIES("food-entries"),
    NUTRITION_GOALS("nutrition-goals"),
    MEAL_SLOTS("meal-slots"),
    GROCERY_ITEMS("grocery-items"),
    HABIT_LOGS("habit-logs"),
    WATER_ENTRIES("water-entries"),
    HYDRATION_GOALS("hydration-goals"),
    WEIGHT("weight"),
    MEASUREMENTS("measurements"),
    PROGRESS_PHOTOS("progress-photos"),
    CALENDAR("calendar"),
    NOTIFICATIONS("notifications"),

    // Queue-only (drained, never pulled) — see class doc.
    WORKOUT_PHASES("workout-phases", isPullDomain = false),
    PROGRAM_ENROLLMENTS("programEnrollment", isPullDomain = false);

    /**
     * Every `entityType` spelling this domain accepts, both on pull rows and
     * in the local queue. The server has historically mixed the hyphenated
     * wire name with the underscore table name (iOS
     * `DatabaseManager.tableName(forSyncEntityType:)` normalizes the same
     * pairs), and the Android hydration producer wrote `water_entries`
     * before this enum existed, so legacy queue rows still drain.
     */
    val entityTypeAliases: Set<String>
        get() = when (this) {
            SESSIONS -> setOf(wireName, "workout_sessions")
            WEIGHT -> setOf(wireName, "weight_entries")
            MEASUREMENTS -> setOf(wireName, "measurement_entries")
            PROGRESS_PHOTOS -> setOf(wireName, "progress_photo_entries")
            CALENDAR -> setOf(wireName, "calendar_content")
            else -> setOf(wireName, wireName.replace('-', '_'))
        }

    companion object {
        /** The domains eligible for `/sync/pull?entities=`. */
        val pullDomains: List<SyncDomain> = entries.filter { it.isPullDomain }

        /** Resolve any accepted spelling (wire name, table name, legacy tag) to its domain. */
        fun fromEntityType(entityType: String): SyncDomain? =
            entries.firstOrNull { entityType in it.entityTypeAliases }
    }
}
