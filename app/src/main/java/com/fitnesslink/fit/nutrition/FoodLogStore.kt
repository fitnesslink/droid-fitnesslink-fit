package com.fitnesslink.fit.nutrition

import android.util.Log
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.ensureFoodLogSchema
import com.fitnesslink.fit.persistence.foodEntry
import com.fitnesslink.fit.persistence.foodEntriesForDay
import com.fitnesslink.fit.persistence.pendingFoodEntryIds
import com.fitnesslink.fit.sync.SyncDomain
import com.fitnesslink.fit.sync.SyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

/**
 * Food-diary write pipeline — port of the logging half of iOS
 * `NutritionViewModel` (`logEntry` / `saveEdit` / `deleteEntry`) plus
 * `SyncManager.drainFoodEntries`.
 *
 * Every single-entry mutation is local-first: SQLite row → durable
 * `sync_queue` row under [SyncDomain.FOOD_ENTRIES] → inline attempt
 * (2xx / final 4xx marks the queue row synced; offline / 5xx leaves it for
 * the drain). Batches (`POST /food-entries/batch`) are atomic and online-only,
 * exactly as on iOS: either every row lands or none do, and the returned rows
 * are adopted locally.
 *
 * After any change the store emits [changes] and calls
 * `NutritionSummaryStore.invalidate(date)` (agent A) for the affected day.
 */
object FoodLogStore {
    private const val TAG = "FoodLogStore"

    /** Queue tag — paired producer ↔ drain (iOS `SyncManager.foodEntriesType`). */
    val queueEntityType: String = SyncDomain.FOOD_ENTRIES.wireName

    private const val ACTION_CREATE = "create"
    private const val ACTION_UPDATE = "update"
    private const val ACTION_DELETE = "delete"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    /** Fires after any local diary change (log / update / delete / batch / refresh) — screens re-read. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    @Volatile private var registered = false

    /** Register the queue drain once per process. Safe to call repeatedly. */
    fun ensureRegistered() {
        if (registered) return
        registered = true
        runCatching { DatabaseManager.ensureFoodLogSchema() }
        SyncManager.registerDrain(SyncDomain.FOOD_ENTRIES) { drain() }
    }

    // MARK: - Reads

    /** Diary rows logged to [date] (`yyyy-MM-dd`), template rows excluded, oldest first. */
    fun entries(date: String): List<FoodEntry> = DatabaseManager.foodEntriesForDay(date).filter { !it.isCustomTemplate }

    fun entry(id: String): FoodEntry? = DatabaseManager.foodEntry(id)

    /** The user's saved custom foods (`isCustomTemplate` rows), name-sorted, newest save wins a name tie. */
    fun customTemplates(): List<FoodEntry> =
        dedupedByName(DatabaseManager.allFoodEntries().filter { it.isCustomTemplate })
            .sortedBy { it.name.lowercase(Locale.US) }

    /** Most-recent-first diary history, one row per name (offline / initial Recent Foods state). */
    fun recentFoodsLocal(limit: Int = 50): List<FoodEntry> =
        dedupedByName(DatabaseManager.allFoodEntries().filter { !it.isCustomTemplate }).take(limit)

    private fun dedupedByName(entries: List<FoodEntry>): List<FoodEntry> {
        val seen = HashSet<String>()
        return entries.sortedByDescending { it.loggedAt.time }.filter { seen.add(it.name.lowercase(Locale.US)) }
    }

    // MARK: - Writes

    /** Offline-first create, shared by every add-food flow (quick add, search, recents, barcode, AI review). */
    suspend fun log(entry: FoodEntry) {
        ensureRegistered()
        val normalized = entry.copy(id = entry.id.lowercase(Locale.US))
        withContext(Dispatchers.IO) { DatabaseManager.insertFoodEntry(normalized) }
        notifyChanged(normalized)
        commit(ACTION_CREATE, normalized.id, ApiClient.gson.toJson(normalized)) {
            ApiClient.nutritionApi.addFoodEntry(normalized)
        }
    }

    /**
     * Atomic batch create (FTNSSLNK-214). [source] / [generationId] stamp
     * provenance onto rows that don't already carry it. Requires a
     * connection: throws [ApiError.Offline] (nothing saved) or the server's
     * [ApiError]; on success the created rows are adopted locally.
     */
    suspend fun logBatch(entries: List<FoodEntry>, source: String? = null, generationId: String? = null) {
        ensureRegistered()
        if (entries.isEmpty()) return
        if (!NetworkMonitor.isConnected.value) throw ApiError.Offline
        val stamped = FoodEntryBatch.stamp(entries, source, generationId).map { it.copy(id = it.id.lowercase(Locale.US)) }
        for (chunk in FoodEntryBatch.chunk(stamped)) {
            val created = ApiClient.nutritionApi.batchAddFoodEntries(FoodEntryBatch.request(chunk))
            val rows = if (created.isEmpty()) chunk else created
            withContext(Dispatchers.IO) { rows.forEach { DatabaseManager.insertFoodEntry(it) } }
            rows.firstOrNull()?.let { notifyChanged(it) }
        }
    }

    /** Offline-first update. Without the queue + PUT an edit would be reverted by the next server refresh. */
    suspend fun update(entry: FoodEntry) {
        ensureRegistered()
        val normalized = entry.copy(id = entry.id.lowercase(Locale.US))
        val previous = DatabaseManager.foodEntry(normalized.id)
        withContext(Dispatchers.IO) { DatabaseManager.insertFoodEntry(normalized) }
        notifyChanged(normalized)
        if (previous != null && !sameDay(previous.loggedAt, normalized.loggedAt)) notifyChanged(previous)
        commit(ACTION_UPDATE, normalized.id, ApiClient.gson.toJson(normalized)) {
            ApiClient.nutritionApi.updateFoodEntry(normalized.id, normalized)
        }
    }

    /** Offline-first delete (also used for custom-food template rows — logged entries are separate rows and stay put). */
    suspend fun delete(id: String) {
        ensureRegistered()
        val key = id.lowercase(Locale.US)
        val previous = DatabaseManager.foodEntry(key)
        withContext(Dispatchers.IO) { DatabaseManager.deleteFoodEntry(key) }
        if (previous != null) notifyChanged(previous) else notifyChanged(null)
        commit(ACTION_DELETE, key, "") {
            ApiClient.nutritionApi.deleteFoodEntry(key)
        }
    }

    /** Adopt rows the server created elsewhere (quick-log copy, a summary's meal groups) into the local cache. */
    suspend fun adopt(entries: List<FoodEntry>) {
        if (entries.isEmpty()) return
        withContext(Dispatchers.IO) { entries.forEach { DatabaseManager.insertFoodEntry(it) } }
        notifyChanged(entries.first())
    }

    // MARK: - Refresh

    /**
     * `GET /food-entries/me?date=` for one day. Pending local writes are
     * drained first so a fresh delete can't be re-inserted by the read; rows
     * still queued for update/delete are skipped; local rows the server no
     * longer has (and that have no queued write) are dropped.
     */
    suspend fun refresh(date: String) {
        ensureRegistered()
        if (!NetworkMonitor.isConnected.value) return
        SyncManager.drain(SyncDomain.FOOD_ENTRIES)
        val remote = try {
            ApiClient.nutritionApi.getFoodEntries(date)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "refresh $date failed: ${e.message}")
            return
        }
        val pendingAny = DatabaseManager.pendingFoodEntryIds(null)
        val pendingWrites = DatabaseManager.pendingFoodEntryIds(setOf(ACTION_UPDATE, ACTION_DELETE))
        withContext(Dispatchers.IO) {
            val remoteIds = HashSet<String>()
            for (entry in remote) {
                val key = entry.id.lowercase(Locale.US)
                remoteIds.add(key)
                if (key in pendingWrites) continue
                DatabaseManager.insertFoodEntry(entry)
            }
            for (local in DatabaseManager.foodEntriesForDay(date)) {
                val key = local.id.lowercase(Locale.US)
                if (local.isCustomTemplate || key in remoteIds || key in pendingAny) continue
                DatabaseManager.deleteFoodEntry(key)
            }
        }
        invalidate(date)
        _changes.tryEmit(Unit)
    }

    // MARK: - Queue + drain

    private suspend fun commit(action: String, entityId: String, payload: String, attempt: suspend () -> Unit) {
        val queueId = withContext(Dispatchers.IO) {
            DatabaseManager.enqueueSyncEntry(queueEntityType, entityId, action, payload)
        }
        if (!NetworkMonitor.isConnected.value) return
        scope.launch {
            try {
                attempt()
                DatabaseManager.markSyncEntrySynced(queueId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isFinal(e)) {
                    Log.w(TAG, "$action $entityId rejected (${e.message}); dropping queue row")
                    DatabaseManager.markSyncEntrySynced(queueId)
                } else {
                    Log.i(TAG, "$action $entityId deferred to drain: ${e.message}")
                }
            }
        }
    }

    private fun isFinal(e: Exception): Boolean = when (e) {
        is ApiError.Forbidden, is ApiError.NotFound -> true
        is ApiError.ServerError -> e.statusCode in 400..499 && e.statusCode != 401
        is ApiError.DecodingError -> true
        else -> false
    }

    /** iOS `drainFoodEntries`: replay queued create / update / delete rows through the real endpoints. */
    suspend fun drain() {
        SyncManager.drainQueue(SyncDomain.FOOD_ENTRIES) { entry ->
            when (entry.action) {
                ACTION_CREATE, ACTION_UPDATE -> {
                    val food = decode(entry.payload) ?: return@drainQueue // malformed → drop
                    if (entry.action == ACTION_CREATE) ApiClient.nutritionApi.addFoodEntry(food)
                    else ApiClient.nutritionApi.updateFoodEntry(food.id, food)
                }
                ACTION_DELETE -> ApiClient.nutritionApi.deleteFoodEntry(entry.entityId)
                else -> Unit
            }
        }
    }

    private fun decode(payload: String): FoodEntry? =
        try { if (payload.isBlank()) null else ApiClient.gson.fromJson(payload, FoodEntry::class.java) } catch (_: Exception) { null }

    // MARK: - Change propagation

    private fun notifyChanged(entry: FoodEntry?) {
        if (entry != null && !entry.isCustomTemplate) invalidate(MealPlanDay.string(entry.loggedAt))
        _changes.tryEmit(Unit)
    }

    private fun invalidate(date: String) {
        try {
            NutritionSummaryStore.invalidate(date)
        } catch (e: Throwable) {
            Log.w(TAG, "NutritionSummaryStore.invalidate failed", e)
        }
    }

    private fun sameDay(a: Date, b: Date): Boolean = MealPlanDay.string(a) == MealPlanDay.string(b)
}
