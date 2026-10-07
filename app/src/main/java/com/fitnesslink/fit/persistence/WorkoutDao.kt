package com.fitnesslink.fit.persistence

import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.WorkoutList
import java.util.Locale

// Workout-domain DB helpers that `DatabaseManager` lacks (CONTRACTS: domain
// DAOs are extension functions over `rawDatabase()`). No new tables or
// columns are needed — `workouts`, `workout_phases` (id, name, sortOrder,
// isSystem, syncState, lastUpdatedAt, deletedLocally) and `sync_queue`
// already carry everything the workout save pipeline writes.

private fun lc(s: String) = s.lowercase(Locale.US)

/** Adopt the server-issued id for a locally-created workout (cascades to schedules / calendar / sessions / queue). */
fun DatabaseManager.renameWorkout(localId: String, serverId: String) = updateEntityId("workouts", localId, serverId)

/** Delete a workout and any calendar rows that pointed at it (iOS `deleteWorkout` cascade). */
fun DatabaseManager.deleteWorkoutCascade(id: String) {
    deleteWorkout(id)
    rawDatabase().delete("calendar_content", "LOWER(workoutId)=?", arrayOf(lc(id)))
}

/** Lower-cased phase id → `syncState` ("synced" / "pending") for every non-deleted catalog row. */
fun DatabaseManager.workoutPhaseSyncStates(): Map<String, String> =
    rawDatabase().rawQuery("SELECT id, syncState FROM workout_phases WHERE deletedLocally=0", null).use { c ->
        buildMap { while (c.moveToNext()) put(lc(c.getString(0)), c.getString(1) ?: "synced") }
    }

/** Phase ids created offline whose server id hasn't landed yet. */
fun DatabaseManager.pendingWorkoutPhaseIds(): Set<String> =
    workoutPhaseSyncStates().filterValues { it != "synced" }.keys

/** Promote a phase row from its local UUID to the server-issued id and mark it synced. */
fun DatabaseManager.renameWorkoutPhaseId(localId: String, serverId: String) {
    val from = lc(localId); val to = lc(serverId)
    if (from == to) {
        rawDatabase().execSQL("UPDATE workout_phases SET syncState='synced' WHERE LOWER(id)=?", arrayOf(to))
        return
    }
    val db = rawDatabase()
    db.beginTransaction()
    try {
        db.delete("workout_phases", "LOWER(id)=?", arrayOf(to))
        db.execSQL("UPDATE workout_phases SET id=?, syncState='synced', lastUpdatedAt=? WHERE LOWER(id)=?",
            arrayOf(to, System.currentTimeMillis(), from))
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

/**
 * Rewrite a phase id inside every cached workout's `phasesJson` (both
 * casings) so drafts that referenced an offline-created phase pick up the
 * server id.
 */
fun DatabaseManager.rewritePhaseIdInWorkouts(from: String, to: String) {
    val lower = lc(from); val upper = from.uppercase(Locale.US); val target = lc(to)
    rawDatabase().execSQL(
        "UPDATE workouts SET phasesJson = REPLACE(REPLACE(phasesJson, ?, ?), ?, ?) WHERE phasesJson LIKE ? OR phasesJson LIKE ?",
        arrayOf(lower, target, upper, target, "%$lower%", "%$upper%")
    )
}

/** Same rewrite over queued workout write payloads so a drained create / replace-tasks carries the server phase id. */
fun DatabaseManager.rewritePhaseIdInSyncQueue(from: String, to: String) {
    val lower = lc(from); val upper = from.uppercase(Locale.US); val target = lc(to)
    rawDatabase().execSQL(
        "UPDATE sync_queue SET payload = REPLACE(REPLACE(payload, ?, ?), ?, ?) WHERE synced=0 AND (payload LIKE ? OR payload LIKE ?)",
        arrayOf(lower, target, upper, target, "%$lower%", "%$upper%")
    )
}

/**
 * Project a list-view `Workout` payload onto the lightweight list row the
 * Workouts screen caches via `upsertWorkoutListRow` (never touches
 * `phasesJson` / `isFavorite`). `time` carries `displayTime` so cached rows
 * render "45 min" / "~38 min" like iOS instead of a blank.
 */
fun Workout.toListRow(sortIndex: Int? = this.sortIndex): WorkoutList = WorkoutList(
    id = lc(id),
    name = name,
    time = displayTime,
    isFavorite = isFavorite,
    trainingLevel = trainingLevels.firstOrNull() ?: trainingLevel,
    imageId = imageId?.toString()?.let(::lc),
    thumbnailId = thumbnailId?.toString()?.let(::lc),
    imageUrl = imageUrl,
    thumbnailUrl = thumbnailUrl,
    contributorId = contributorId?.toString()?.let(::lc),
    sharedByUserId = sharedByUserId?.toString()?.let(::lc),
    createdAt = libraryAddedAt ?: audit?.timestamp,
    partOfContributorProgram = partOfContributorProgram,
    sortIndex = sortIndex
)
