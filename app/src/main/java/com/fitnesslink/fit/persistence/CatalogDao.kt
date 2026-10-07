package com.fitnesslink.fit.persistence

import android.database.sqlite.SQLiteDatabase
import com.fitnesslink.fit.model.CatalogItem
import com.fitnesslink.fit.model.ProgramList
import com.fitnesslink.fit.model.WorkoutList
import java.util.Locale

// Catalog-hub DB helpers `DatabaseManager` lacks (CONTRACTS: domain DAOs are
// extension functions over `rawDatabase()`). No new tables or columns — the
// `workouts` / `programs` / `program_schedules` / `sync_queue` tables already
// carry everything the catalog refresh reads and writes.

private fun catalogDb(): SQLiteDatabase = DatabaseManager.rawDatabase()

private fun lcId(s: String) = s.lowercase(Locale.US)

/**
 * Entity ids with a sync-queue row that is still pending, or was enqueued
 * within the last [windowMs] (iOS `recentSyncActivityIds`). Lowercased; the
 * id space is UUIDs so no cross-entityType collision is possible. These rows
 * are protected from snapshot reconciliation: an offline create isn't on
 * the server yet, and a just-committed save can race an in-flight snapshot.
 */
fun DatabaseManager.recentSyncActivityIds(windowMs: Long = 5 * 60 * 1000L): Set<String> {
    val cutoff = (System.currentTimeMillis() - windowMs).toString()
    return catalogDb().rawQuery("SELECT entityId FROM sync_queue WHERE synced=0 OR createdAt > ?", arrayOf(cutoff)).use { c ->
        buildSet { while (c.moveToNext()) c.getString(0)?.let { add(lcId(it)) } }
    }
}

/** Lowercased ids of cached contributor (template) rows in [table] absent from [serverIds]. */
private fun staleContributorIds(table: String, serverIds: Collection<String>): List<String> {
    val keep = serverIds.mapTo(HashSet()) { lcId(it) }
    return catalogDb().rawQuery("SELECT id FROM $table WHERE contributorId IS NOT NULL AND contributorId<>''", null).use { c ->
        buildList { while (c.moveToNext()) { val id = lcId(c.getString(0)); if (id !in keep) add(id) } }
    }
}

/**
 * Lowercased ids of cached user-authored (`contributorId` null) or shared-in
 * rows in [table] absent from [serverIds] and not protected by recent sync
 * activity (iOS `staleUserContentIds`). Keeping them would resurrect
 * content deleted on another device or un-shared.
 */
private fun staleUserContentIds(table: String, serverIds: Collection<String>, protected: Set<String>): List<String> {
    val keep = serverIds.mapTo(HashSet()) { lcId(it) }
    return catalogDb().rawQuery(
        "SELECT id FROM $table WHERE contributorId IS NULL OR contributorId='' OR (sharedByUserId IS NOT NULL AND sharedByUserId<>'')", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val id = lcId(c.getString(0))
                if (id !in keep && id !in protected) add(id)
            }
        }
    }
}

private fun deleteRows(db: SQLiteDatabase, table: String, column: String, lowercasedIds: Collection<String>) {
    for (id in lowercasedIds) db.delete(table, "LOWER($column)=?", arrayOf(id))
}

/**
 * Drop cached workouts absent from a successful full `/workouts/list-view`
 * snapshot — contributor rows the catalog removed (FTNSSLNK-333) plus
 * user-authored / shared rows the server soft-deleted or un-shared.
 * Calendar entries pointing at a removed workout are deliberately KEPT:
 * they render from `calendar_content`'s own columns and every workout
 * lookup on the Home / calendar paths guards null (iOS
 * `reconcileContributorWorkouts`).
 */
fun DatabaseManager.reconcileContributorWorkouts(serverIds: Collection<String>) {
    val stale = staleContributorIds("workouts", serverIds) + staleUserContentIds("workouts", serverIds, recentSyncActivityIds())
    if (stale.isEmpty()) return
    val db = catalogDb()
    db.beginTransaction()
    try {
        deleteRows(db, "workouts", "id", stale)
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

/**
 * The user-content half of the programs snapshot reconcile (the contributor
 * half is the programs agent's `reconcileContributorPrograms`): drop cached
 * user-authored / shared programs the server soft-deleted or un-shared, plus
 * their `program_schedules` rows (iOS `reconcileContributorPrograms` +
 * `staleUserContentIds`).
 */
fun DatabaseManager.reconcileStaleUserPrograms(serverIds: Collection<String>) {
    val stale = staleUserContentIds("programs", serverIds, recentSyncActivityIds())
    if (stale.isEmpty()) return
    val db = catalogDb()
    db.beginTransaction()
    try {
        deleteRows(db, "programs", "id", stale)
        deleteRows(db, "program_schedules", "programId", stale)
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

/** Lowercased ids of contributor programs with at least one cached schedule row. */
fun DatabaseManager.contributorProgramIdsWithSchedules(): Set<String> = catalogDb().rawQuery(
    "SELECT DISTINCT LOWER(s.programId) FROM program_schedules s JOIN programs p ON LOWER(p.id)=LOWER(s.programId) WHERE p.contributorId IS NOT NULL AND p.contributorId<>''",
    null
).use { c -> buildSet { while (c.moveToNext()) c.getString(0)?.let { add(it) } } }

// MARK: - Projections

/** Template predicate shared by the catalog rails: exactly the rows carrying a contributor (iOS `templateWorkouts`). */
val WorkoutList.isTemplate: Boolean get() = !contributorId.isNullOrEmpty()
val ProgramList.isTemplate: Boolean get() = !contributorId.isNullOrEmpty()

/** Catalog-rail item for a cached workout row (caption = display time). */
fun WorkoutList.toCatalogItem(anatomyIds: Set<String> = emptySet(), equipmentIds: Set<String> = emptySet()): CatalogItem = CatalogItem(
    id = id, title = name, kind = CatalogItem.Kind.WORKOUT, caption = time,
    imageId = imageId, thumbnailId = thumbnailId, imageUrl = imageUrl, thumbnailUrl = thumbnailUrl,
    sharedByUserId = sharedByUserId, createdAt = createdAt, isFavorite = isFavorite,
    anatomyIds = anatomyIds, equipmentIds = equipmentIds
)

/** Catalog-rail item for a cached program row (caption = display time). */
fun ProgramList.toCatalogItem(anatomyIds: Set<String> = emptySet(), equipmentIds: Set<String> = emptySet()): CatalogItem = CatalogItem(
    id = id, title = name, kind = CatalogItem.Kind.PROGRAM, caption = time,
    imageId = imageId, thumbnailId = thumbnailId, imageUrl = imageUrl, thumbnailUrl = thumbnailUrl,
    sharedByUserId = sharedByUserId, createdAt = createdAt, isFavorite = isFavorite,
    anatomyIds = anatomyIds, equipmentIds = equipmentIds
)
