package com.fitnesslink.fit.persistence

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.fitnesslink.fit.model.ModelGson
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.api.Movement
import java.util.Locale

/**
 * Movement helpers `DatabaseManager` lacks (Wave 1.9 / 1.10). Extension
 * functions over `DatabaseManager.rawDatabase()`; the two extra tables are
 * created lazily by [ensureMovementSchema] (guarded, idempotent) so the
 * integrator can fold them into the versioned migration later.
 *
 * - `movement_detail_cache`: the full `GET /movements/{id}` payload (summary,
 *   sections, Mux status, aspect ratios) as JSON so Movement Detail and the
 *   preview sheet render structured content offline. The `movements` table
 *   only carries the list-view columns.
 * - `movement_pending_creates`: best-effort retry ledger for `POST /movements`
 *   calls that failed offline. iOS does not queue movement creates (it falls
 *   back to a local-only id); this ledger lets Android re-attempt the create
 *   and re-key the local row to the server id when it succeeds.
 */

private val movementGson by lazy { ModelGson.instance }

@Volatile
private var movementSchemaReady = false

private fun db(): SQLiteDatabase = DatabaseManager.rawDatabase()

private fun lc(s: String) = s.lowercase(Locale.US)

private fun hasTable(db: SQLiteDatabase, name: String): Boolean =
    db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name)).use { it.moveToFirst() }

private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
    db.rawQuery("PRAGMA table_info($table)", null).use { c ->
        val nameIdx = c.getColumnIndex("name")
        while (c.moveToNext()) if (c.getString(nameIdx).equals(column, ignoreCase = true)) return true
        false
    }

/** Create the movement side tables if missing. Safe to call on every entry point. */
fun DatabaseManager.ensureMovementSchema() {
    if (movementSchemaReady) return
    synchronized(DatabaseManager) {
        if (movementSchemaReady) return
        val db = db()
        if (!hasTable(db, "movement_detail_cache")) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS movement_detail_cache (
                    movementId TEXT PRIMARY KEY,
                    json TEXT NOT NULL,
                    cachedAt INTEGER NOT NULL)"""
            )
        }
        if (!hasTable(db, "movement_pending_creates")) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS movement_pending_creates (
                    localId TEXT PRIMARY KEY,
                    payload TEXT NOT NULL,
                    uploadId TEXT,
                    createdAt INTEGER NOT NULL)"""
            )
        }
        // The versioned schema already carries these; double-check so a stale
        // test DB built before V11 still works with the store's upserts.
        if (hasTable(db, "movements")) {
            if (!hasColumn(db, "movements", "sharedByUserId")) db.execSQL("ALTER TABLE movements ADD COLUMN sharedByUserId TEXT")
            if (!hasColumn(db, "movements", "phoneVideoId")) db.execSQL("ALTER TABLE movements ADD COLUMN phoneVideoId TEXT")
            if (!hasColumn(db, "movements", "sortIndex")) db.execSQL("ALTER TABLE movements ADD COLUMN sortIndex INTEGER")
            if (!hasColumn(db, "movements", "createdAt")) db.execSQL("ALTER TABLE movements ADD COLUMN createdAt INTEGER")
            if (!hasColumn(db, "movements", "summary")) db.execSQL("ALTER TABLE movements ADD COLUMN summary TEXT")
        }
        movementSchemaReady = true
    }
}

// MARK: - Detail cache

/** Persist the full movement payload so detail / preview render offline. */
fun DatabaseManager.cacheMovementDetail(movement: Movement) {
    ensureMovementSchema()
    db().insertWithOnConflict(
        "movement_detail_cache", null,
        ContentValues().apply {
            put("movementId", lc(movement.id.toString()))
            put("json", movementGson.toJson(movement))
            put("cachedAt", System.currentTimeMillis())
        },
        SQLiteDatabase.CONFLICT_REPLACE
    )
}

/** The last cached `GET /movements/{id}` payload, or null when never fetched. */
fun DatabaseManager.cachedMovementDetail(movementId: String): Movement? {
    ensureMovementSchema()
    return db().rawQuery("SELECT json FROM movement_detail_cache WHERE movementId=?", arrayOf(lc(movementId))).use { c ->
        if (!c.moveToFirst()) return null
        runCatching { movementGson.fromJson(c.getString(0), Movement::class.java) }.getOrNull()
    }
}

fun DatabaseManager.deleteMovementDetail(movementId: String) {
    ensureMovementSchema()
    db().delete("movement_detail_cache", "movementId=?", arrayOf(lc(movementId)))
}

// MARK: - Catalog reconciliation

/**
 * After a successful full `list-view` snapshot, drop cached contributor
 * (template) movements the server no longer returns. User-authored rows
 * (`contributorId` null) and shared-in rows are left alone — they may be
 * offline-created or scoped out of the snapshot by a filter. (iOS
 * `reconcileContributorMovements`, FTNSSLNK-333.)
 */
fun DatabaseManager.reconcileContributorMovements(serverIds: Collection<String>) {
    ensureMovementSchema()
    val keep = serverIds.mapTo(HashSet()) { lc(it) }
    val stale = db().rawQuery(
        "SELECT id FROM movements WHERE contributorId IS NOT NULL AND contributorId<>'' AND isDeleted=0", null
    ).use { c -> buildList { while (c.moveToNext()) { val id = c.getString(0); if (lc(id) !in keep) add(id) } } }
    if (stale.isEmpty()) return
    val d = db()
    d.beginTransaction()
    try {
        stale.forEach { id ->
            d.delete("movement_anatomy", "movementId=?", arrayOf(lc(id)))
            d.delete("movement_equipment", "movementId=?", arrayOf(lc(id)))
            d.delete("movements", "LOWER(id)=?", arrayOf(lc(id)))
            d.delete("movement_detail_cache", "movementId=?", arrayOf(lc(id)))
        }
        d.setTransactionSuccessful()
    } finally {
        d.endTransaction()
    }
}

/** Template movements only (`contributorId != null`) — the catalog's visibility rule (iOS `templateMovements`). */
fun DatabaseManager.templateMovements(): List<MovementLibraryItem> = allMovements().filter { !it.contributorId.isNullOrEmpty() }

/** User-authored movements (`contributorId == null`, not shared in). */
fun DatabaseManager.ownedMovements(): List<MovementLibraryItem> =
    allMovements().filter { it.contributorId.isNullOrEmpty() && it.sharedByUserId.isNullOrEmpty() }

/**
 * Move a locally-created movement onto its server id once a deferred create
 * succeeds: rewrites the row key, tag joins, recents and the detail cache.
 */
fun DatabaseManager.rekeyMovement(oldId: String, newId: String) {
    ensureMovementSchema()
    if (lc(oldId) == lc(newId)) return
    val d = db()
    d.beginTransaction()
    try {
        d.execSQL("UPDATE OR REPLACE movements SET id=? WHERE LOWER(id)=?", arrayOf(lc(newId), lc(oldId)))
        d.execSQL("UPDATE OR IGNORE movement_anatomy SET movementId=? WHERE movementId=?", arrayOf(lc(newId), lc(oldId)))
        d.execSQL("UPDATE OR IGNORE movement_equipment SET movementId=? WHERE movementId=?", arrayOf(lc(newId), lc(oldId)))
        d.delete("movement_anatomy", "movementId=?", arrayOf(lc(oldId)))
        d.delete("movement_equipment", "movementId=?", arrayOf(lc(oldId)))
        runCatching { d.execSQL("UPDATE OR REPLACE recent_movements SET movementId=? WHERE movementId=?", arrayOf(lc(newId), lc(oldId))) }
        d.execSQL("UPDATE OR REPLACE movement_detail_cache SET movementId=? WHERE movementId=?", arrayOf(lc(newId), lc(oldId)))
        d.setTransactionSuccessful()
    } finally {
        d.endTransaction()
    }
}

// MARK: - Pending creates (best-effort retry ledger)

data class PendingMovementCreate(
    val localId: String,
    val payloadJson: String,
    val uploadId: String?,
    val createdAt: Long
)

fun DatabaseManager.enqueuePendingMovementCreate(localId: String, payloadJson: String, uploadId: String?) {
    ensureMovementSchema()
    db().insertWithOnConflict(
        "movement_pending_creates", null,
        ContentValues().apply {
            put("localId", lc(localId)); put("payload", payloadJson)
            if (uploadId != null) put("uploadId", uploadId) else putNull("uploadId")
            put("createdAt", System.currentTimeMillis())
        },
        SQLiteDatabase.CONFLICT_REPLACE
    )
}

fun DatabaseManager.pendingMovementCreates(): List<PendingMovementCreate> {
    ensureMovementSchema()
    return db().rawQuery("SELECT localId, payload, uploadId, createdAt FROM movement_pending_creates ORDER BY createdAt", null).use { c ->
        buildList {
            while (c.moveToNext()) add(PendingMovementCreate(c.getString(0), c.getString(1), if (c.isNull(2)) null else c.getString(2), c.getLong(3)))
        }
    }
}

fun DatabaseManager.removePendingMovementCreate(localId: String) {
    ensureMovementSchema()
    db().delete("movement_pending_creates", "localId=?", arrayOf(lc(localId)))
}
