package com.fitnesslink.fit.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.model.WaterIntakeEntry
import com.fitnesslink.fit.model.api.FLUser
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.SyncIdMapping
import com.fitnesslink.fit.network.dto.SyncPullResponse
import com.fitnesslink.fit.network.dto.SyncPushChange
import com.fitnesslink.fit.network.dto.SyncPushRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/** Coarse sync status; kept as a 4-value enum because Developer Settings switches over it exhaustively. */
enum class SyncStatus { IDLE, SYNCING, ERROR, COMPLETE }

/** Snapshot exposed through [SyncManager.state] for Developer Settings. */
data class SyncState(
    val status: SyncStatus = SyncStatus.IDLE,
    val lastSyncAt: Date? = null,
    val errorMessage: String? = null
)

/**
 * Thrown by a [SyncManager.drainQueue] handler to say "this row cannot be
 * replayed right now, leave it queued and stop walking this domain". Any
 * [IOException] / 5xx / 401 is classified the same way automatically; this
 * exists for handler-level decisions such as iOS's
 * `drainSessions` → `complete` → 404 ("the matching `start` has not landed
 * yet, keep it").
 */
class TransientSyncException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/**
 * Offline sync coordinator — port of iOS `Services/Sync/SyncManager.swift`.
 *
 * Three layers, run in this order by [performFullSync]:
 *
 * 1. **Pre-drain steps** ([registerPreDrainStep]) — cheap local cleanup that
 *    may *enqueue* more work, e.g. iOS purges stale in-progress sessions and
 *    enqueues an `abandon` for each (Wave 1.1 registers this).
 * 2. **Per-domain drains** ([registerDrain]) — replay queued rows through the
 *    real REST endpoints, in [drainOrder]. Each drain is a queue walk
 *    ([drainQueue]) whose rows are marked synced on 2xx or a final 4xx and
 *    left queued (walk stopped) on anything transient.
 * 3. **Generic push + pull** — `POST /sync/push` for any queued row that no
 *    registered drain owns, then `GET /sync/pull` paged until `hasMore` is
 *    false, applied through `DatabaseManager.applySyncPayload` /
 *    `deleteSyncEntity`.
 *
 * ### Drains iOS implements (register here as their waves land)
 *
 * | Domain                | iOS drain                | Android wave |
 * |-----------------------|--------------------------|--------------|
 * | WORKOUT_PHASES        | `drainWorkoutPhases`     | 1.7          |
 * | WORKOUTS              | `drainWorkouts`          | 1.7          |
 * | SESSIONS              | `drainSessions`          | 1.1          |
 * | SESSION_PROGRESS      | `drainSessionProgress`   | 1.1          |
 * | FOOD_ENTRIES          | `drainFoodEntries`       | nutrition    |
 * | WATER_ENTRIES         | `drainWaterEntries`      | **built**    |
 * | NUTRITION_GOALS       | `drainNutritionGoals`    | nutrition    |
 * | MEAL_SLOTS            | `drainMealSlots`         | nutrition    |
 * | GROCERY_ITEMS         | `drainGroceryItems`      | nutrition    |
 * | GOALS                 | `drainGoals`             | goals        |
 * | HABITS                | `drainHabits`            | goals        |
 * | HABIT_LOGS            | `drainHabitLogs`         | goals        |
 * | MEASUREMENTS          | `drainMeasurements`      | body         |
 * | WEIGHT                | `drainWeight`            | body         |
 * | PROGRAM_ENROLLMENTS   | `drainProgramEnrollments`| 2.1          |
 * | CALENDAR              | `drainCalendar`          | 2.x          |
 *
 * Producers must write `sync_queue.entityType` with the domain's
 * [SyncDomain.wireName] (use the `*_TYPE` constants below) so the drain finds
 * its rows.
 *
 * Kotlin `object` singleton, no DI, Gson for payloads.
 */
object SyncManager {
    private const val TAG = "SyncManager"
    private const val PREFS_NAME = "fl_sync"
    private const val KEY_CURSOR = "fl_sync_cursor"
    private const val KEY_LAST_SYNC = "fl_last_sync"

    // MARK: - Queue entity-type tags (producer ↔ drainer agreement, as on iOS)

    val WORKOUT_PHASES_TYPE: String = SyncDomain.WORKOUT_PHASES.wireName
    val WORKOUTS_TYPE: String = SyncDomain.WORKOUTS.wireName
    val SESSIONS_TYPE: String = SyncDomain.SESSIONS.wireName
    val SESSION_PROGRESS_TYPE: String = SyncDomain.SESSION_PROGRESS.wireName
    val FOOD_ENTRIES_TYPE: String = SyncDomain.FOOD_ENTRIES.wireName
    val WATER_ENTRIES_TYPE: String = SyncDomain.WATER_ENTRIES.wireName
    val HYDRATION_GOALS_TYPE: String = SyncDomain.HYDRATION_GOALS.wireName
    val NUTRITION_GOALS_TYPE: String = SyncDomain.NUTRITION_GOALS.wireName
    val MEAL_SLOTS_TYPE: String = SyncDomain.MEAL_SLOTS.wireName
    val GROCERY_ITEMS_TYPE: String = SyncDomain.GROCERY_ITEMS.wireName
    val GOALS_TYPE: String = SyncDomain.GOALS.wireName
    val HABITS_TYPE: String = SyncDomain.HABITS.wireName
    val HABIT_LOGS_TYPE: String = SyncDomain.HABIT_LOGS.wireName
    val MEASUREMENTS_TYPE: String = SyncDomain.MEASUREMENTS.wireName
    val WEIGHT_ENTRIES_TYPE: String = SyncDomain.WEIGHT.wireName
    val PROGRAM_ENROLLMENT_TYPE: String = SyncDomain.PROGRAM_ENROLLMENTS.wireName
    val CALENDAR_TYPE: String = SyncDomain.CALENDAR.wireName

    /**
     * Drain order, verbatim from iOS `performFullSync`. Phases before
     * workouts (queued workouts reference server phase ids); session start
     * before progress (server needs the row); calendar last so its DELETEs
     * are sent before the generic push could ack them away.
     */
    val drainOrder: List<SyncDomain> = listOf(
        SyncDomain.WORKOUT_PHASES,
        SyncDomain.WORKOUTS,
        SyncDomain.SESSIONS,
        SyncDomain.SESSION_PROGRESS,
        SyncDomain.FOOD_ENTRIES,
        SyncDomain.WATER_ENTRIES,
        SyncDomain.NUTRITION_GOALS,
        SyncDomain.MEAL_SLOTS,
        SyncDomain.GROCERY_ITEMS,
        SyncDomain.GOALS,
        SyncDomain.HABITS,
        SyncDomain.HABIT_LOGS,
        SyncDomain.MEASUREMENTS,
        SyncDomain.WEIGHT,
        SyncDomain.PROGRAM_ENROLLMENTS,
        SyncDomain.CALENDAR
    )

    // MARK: - State

    private val _state = MutableStateFlow(SyncState())
    /** Status + last sync time + last error, for Developer Settings. */
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _status = MutableStateFlow(SyncStatus.IDLE)
    /** Convenience projection of [state] kept for existing callers (DeveloperSettingsScreen). */
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _lastSyncDate = MutableStateFlow<Date?>(null)
    /** Convenience projection of [state] kept for existing callers (DeveloperSettingsScreen). */
    val lastSyncDate: StateFlow<Date?> = _lastSyncDate.asStateFlow()

    /**
     * Invoked once after [performInitialSync] finishes. `SyncScheduler`
     * hooks `MediaPrefetcher.runIfNeeded()` here so bulk media downloads run
     * on login (and the periodic worker) but never on the foreground path,
     * where iOS found they starved streaming playback.
     */
    @Volatile var onInitialSyncComplete: (suspend () -> Unit)? = null

    private lateinit var prefs: SharedPreferences

    /** Payload codec shared with producers (`HydrationViewModel` uses the same date format). */
    private val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .create()

    /** Serializes full-sync passes; standalone [pull]/[push] calls also take it. */
    private val syncMutex = Mutex()
    @Volatile private var inFlightJob: Job? = null
    /** Bumped by [reset] so a pass cancelled mid-flight never writes a cursor for the next user. */
    @Volatile private var generation = 0

    private val drains = ConcurrentHashMap<SyncDomain, suspend () -> Unit>()
    private val preDrainSteps = java.util.Collections.synchronizedMap(LinkedHashMap<String, suspend () -> Unit>())
    /** Per-domain re-entrancy guard (iOS `isDraining*` flags). */
    private val draining: MutableSet<SyncDomain> = ConcurrentHashMap.newKeySet()

    init {
        registerDrain(SyncDomain.WATER_ENTRIES) { drainWaterEntries() }
    }

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastMs = prefs.getLong(KEY_LAST_SYNC, 0L)
        val last = if (lastMs > 0) Date(lastMs) else null
        _lastSyncDate.value = last
        _state.value = SyncState(status = SyncStatus.IDLE, lastSyncAt = last)
    }

    private var cursor: String?
        get() = prefs.getString(KEY_CURSOR, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_CURSOR) else putString(KEY_CURSOR, value)
            }.apply()
        }

    // MARK: - Drain registry

    /**
     * Register (or replace) the drain for [domain]. The drain is run by
     * [performFullSync] in [drainOrder] and by [drain]; it should be built on
     * [drainQueue]. Domains without a registered drain fall through to the
     * generic `/sync/push`.
     */
    fun registerDrain(domain: SyncDomain, drain: suspend () -> Unit) {
        drains[domain] = drain
    }

    fun unregisterDrain(domain: SyncDomain) {
        drains.remove(domain)
    }

    fun hasDrain(domain: SyncDomain): Boolean = drains.containsKey(domain)

    /**
     * Register a local step that runs before any drain on every full sync,
     * in registration order. iOS runs
     * `purgeStaleInProgressSessions` + `commitAbandon` here; Wave 1.1
     * registers the Android equivalent under a stable [key].
     */
    fun registerPreDrainStep(key: String, step: suspend () -> Unit) {
        preDrainSteps[key] = step
    }

    fun unregisterPreDrainStep(key: String) {
        preDrainSteps.remove(key)
    }

    /** Run one domain's drain now (outside a full sync), with the per-domain re-entrancy guard. */
    suspend fun drain(domain: SyncDomain) {
        val handler = drains[domain] ?: return
        if (!NetworkMonitor.isConnected.value) return
        if (!draining.add(domain)) return
        try {
            handler()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "drain ${domain.wireName} failed", e)
        } finally {
            draining.remove(domain)
        }
    }

    // MARK: - Generic queue walk

    /**
     * Walk the pending `sync_queue` rows whose `entityType` is one of
     * [domain]'s [SyncDomain.entityTypeAliases], oldest first, and invoke
     * [handler] for each. Mirrors every iOS `drain*` loop:
     *
     * - handler returns normally → row marked synced (so "drop this row",
     *   e.g. an undecodable payload or unknown action, is just `return`);
     * - final failure (403, 404, any other 4xx except 401, Gson decode
     *   error) → logged, row marked synced; walk continues;
     * - transient failure ([IOException], 5xx, 401, [TransientSyncException])
     *   → row left queued, walk stops to preserve order.
     *
     * @return number of rows marked synced in this walk.
     */
    suspend fun drainQueue(
        domain: SyncDomain,
        handler: suspend (DatabaseManager.SyncQueueEntry) -> Unit
    ): Int {
        if (!NetworkMonitor.isConnected.value) return 0
        val aliases = domain.entityTypeAliases
        val pending = DatabaseManager.pendingSyncEntries().filter { it.entityType in aliases }
        if (pending.isEmpty()) return 0

        var drained = 0
        for (entry in pending) {
            currentCoroutineContext().ensureActive()
            try {
                handler(entry)
                DatabaseManager.markSyncEntrySynced(entry.id)
                drained++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isFinalFailure(e)) {
                    Log.w(TAG, "drain ${domain.wireName} ${entry.action} ${entry.entityId}: final ${describe(e)} — dropping")
                    DatabaseManager.markSyncEntrySynced(entry.id)
                    drained++
                } else {
                    Log.i(TAG, "drain ${domain.wireName} ${entry.action} ${entry.entityId}: transient ${describe(e)} — leaving queued")
                    break
                }
            }
        }
        return drained
    }

    /**
     * iOS semantics: `.notFound` / `.forbidden` / `serverError(400..<500)` are
     * final; everything else (network, 5xx, and `.unauthorized`, which iOS
     * never pattern-matched) is transient.
     */
    private fun isFinalFailure(e: Throwable): Boolean = when (e) {
        is TransientSyncException -> false
        is HttpException -> e.code() in 400..499 && e.code() != 401
        is ApiError.Forbidden, is ApiError.NotFound -> true
        is ApiError.ServerError -> e.statusCode in 400..499 && e.statusCode != 401
        is ApiError.DecodingError, is JsonParseException -> true
        is ApiError.Unauthorized, is ApiError.Offline, is ApiError.NetworkError, is IOException -> false
        else -> false
    }

    private fun describe(e: Throwable): String = when (e) {
        is HttpException -> "HTTP ${e.code()}"
        else -> e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")
    }

    /** Decode a queued payload; null (→ caller drops the row) on any failure, as on iOS. */
    inline fun <reified T> decodePayload(payload: String): T? = decodePayload(payload, T::class.java)

    fun <T> decodePayload(payload: String, type: Class<T>): T? =
        try { if (payload.isBlank()) null else gson.fromJson(payload, type) } catch (_: Exception) { null }

    // MARK: - Per-domain drain: Water entries (iOS `drainWaterEntries`)

    /**
     * Replay queued water-intake writes from `HydrationViewModel`
     * (`create` carries the full [WaterIntakeEntry] JSON, `delete` an empty
     * payload). Both legacy `water_entries` and wire `water-entries` rows
     * are picked up via [SyncDomain.entityTypeAliases].
     */
    suspend fun drainWaterEntries() {
        drainQueue(SyncDomain.WATER_ENTRIES) { entry ->
            when (entry.action) {
                "create" -> {
                    val water = decodePayload<WaterIntakeEntry>(entry.payload) ?: return@drainQueue
                    ApiClient.nutritionApi.addWaterEntry(water)
                }
                "delete" -> ApiClient.nutritionApi.deleteWaterEntry(entry.entityId)
                else -> Unit // unknown action: drop
            }
        }
    }

    // MARK: - Full sync

    /**
     * Pre-drain steps → drains in [drainOrder] → generic push → paged pull.
     * No-ops if offline or if another pass holds the lock.
     */
    suspend fun performFullSync() {
        runExclusive(wait = false) { gen ->
            runPreDrainSteps()
            for (domain in drainOrder) {
                currentCoroutineContext().ensureActive()
                drain(domain)
            }
            pushInternal()
            pullInternal(domains = null, gen = gen)
        }
    }

    /**
     * Take [syncMutex] (skipping when [wait] is false and a pass is already
     * running), flip to SYNCING, run [block] on IO, then COMPLETE / ERROR /
     * back to IDLE on cancellation. Owns [inFlightJob] so [reset] can cancel
     * whatever is running.
     */
    private suspend fun runExclusive(wait: Boolean, block: suspend (gen: Int) -> Unit) {
        if (!NetworkMonitor.isConnected.value) return
        if (wait) syncMutex.lock() else if (!syncMutex.tryLock()) return
        val startGen = generation
        try {
            withContext(Dispatchers.IO) {
                inFlightJob = currentCoroutineContext()[Job]
                setStatus(SyncStatus.SYNCING)
                try {
                    block(startGen)
                    if (generation == startGen) markSynced()
                } catch (e: CancellationException) {
                    if (generation == startGen) setStatus(SyncStatus.IDLE)
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "sync pass failed", e)
                    if (generation == startGen) setStatus(SyncStatus.ERROR, e.message)
                }
            }
        } finally {
            inFlightJob = null
            syncMutex.unlock()
        }
    }

    private suspend fun runPreDrainSteps() {
        val steps = synchronized(preDrainSteps) { preDrainSteps.entries.map { it.key to it.value } }
        for ((key, step) in steps) {
            currentCoroutineContext().ensureActive()
            try { step() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w(TAG, "pre-drain step '$key' failed", e)
            }
        }
    }

    // MARK: - Pull

    /**
     * `GET /sync/pull` from the stored cursor, looping while `hasMore`
     * (iOS stops after one page; paging is cheap and correct). Pass
     * [domains] to restrict `entities=`. Standalone entry point — skipped
     * if a full sync is already running.
     */
    suspend fun pull(domains: List<SyncDomain>? = null) {
        runExclusive(wait = false) { gen -> pullInternal(domains, gen) }
    }

    /** Caller holds [syncMutex]. Throws on transport failure so the caller can set ERROR. */
    private suspend fun pullInternal(domains: List<SyncDomain>?, gen: Int) {
        val entities = domains
            ?.filter { it.isPullDomain }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(",") { it.wireName }

        var pages = 0
        do {
            currentCoroutineContext().ensureActive()
            val response = ApiClient.syncApi.pull(cursor = cursor, entities = entities)
            if (generation != gen) return // reset() happened mid-flight: do not persist for the new user
            applyPulledChanges(response)
            response.cursor?.let { cursor = it }
            pages++
        } while (response.hasMore && response.cursor != null && pages < MAX_PULL_PAGES)
        if (pages >= MAX_PULL_PAGES) Log.w(TAG, "pull stopped after $MAX_PULL_PAGES pages with hasMore still true")
    }

    private const val MAX_PULL_PAGES = 200

    /**
     * Apply one pull page. Deletes go through `deleteSyncEntity`, upserts
     * through `applySyncPayload` (which allow-lists entity types — unknown
     * ones are its problem to log and skip). Calendar rows whose id is in
     * `pendingCalendarMutationIds()` are skipped so a stale pull cannot
     * resurrect an entry whose local DELETE / PUT has not landed yet.
     */
    private fun applyPulledChanges(response: SyncPullResponse) {
        val changes = response.changes.orEmpty()
        if (changes.isEmpty()) return
        val pendingCalendar: Set<String> = try {
            DatabaseManager.pendingCalendarMutationIds().map { it.lowercase() }.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "pendingCalendarMutationIds failed; applying calendar rows unguarded", e)
            emptySet()
        }
        for (change in changes) {
            val domain = SyncDomain.fromEntityType(change.entityType)
            if (!change.isDeleted && domain == SyncDomain.CALENDAR &&
                change.entityId.lowercase() in pendingCalendar
            ) continue
            try {
                if (change.isDeleted) {
                    DatabaseManager.deleteSyncEntity(change.entityType, change.entityId)
                } else {
                    val payload = change.payload ?: continue
                    DatabaseManager.applySyncPayload(change.entityType, change.entityId, payload)
                }
            } catch (e: Exception) {
                // One bad row must not abort the page; the cursor advances regardless (as on iOS).
                Log.w(TAG, "apply ${change.entityType}/${change.entityId} failed", e)
            }
        }
    }

    // MARK: - Push

    /**
     * `POST /sync/push` for queued rows that no registered drain owns.
     * Standalone entry point — skipped if a full sync is already running.
     */
    suspend fun push() {
        runExclusive(wait = false) { pushInternal() }
    }

    /**
     * Caller holds [syncMutex]. Rows whose entity type maps to a domain with
     * a registered drain are excluded: that drain owns them and replays them
     * through the real endpoint. `/sync/push` acks everything it is sent, so
     * handing it a row a drain left queued (transient failure) would
     * silently drop that write — this exclusion is the guard iOS lacks.
     */
    private suspend fun pushInternal() {
        val pending = DatabaseManager.pendingSyncEntries().filter { entry ->
            val domain = SyncDomain.fromEntityType(entry.entityType)
            domain == null || !drains.containsKey(domain)
        }
        if (pending.isEmpty()) return

        val request = SyncPushRequest(
            changes = pending.map { entry ->
                SyncPushChange(
                    entityType = entry.entityType,
                    entityId = entry.entityId,
                    action = entry.action,
                    payload = entry.payload
                )
            }
        )
        val response = ApiClient.syncApi.push(request)
        for (entry in pending) DatabaseManager.markSyncEntrySynced(entry.id)
        applyIdMappings(response.idMappings.orEmpty())
    }

    /**
     * iOS forwards each mapping to `DatabaseManager.updateEntityId(entityType:,
     * localId:, serverId:)`. The Android `DatabaseManager` has no such method
     * yet (owned by the persistence agent); until it lands, mappings are
     * logged so they are visible rather than silently ignored.
     */
    private fun applyIdMappings(mappings: List<SyncIdMapping>) {
        for (m in mappings) {
            Log.i(TAG, "idMapping ${m.entityType}: ${m.localId} -> ${m.serverId} (DatabaseManager.updateEntityId not available)")
        }
    }

    // MARK: - Initial sync (after login)

    /**
     * Wipe local user data, drop the cursor, re-save the signed-in user
     * (`clearUserData()` deletes the `users` row login just wrote and no pull
     * domain restores it — without this Profile shows "Fitness User"), then
     * pull everything. [user] defaults to `AuthManager.currentUser`.
     */
    suspend fun performInitialSync(user: FLUser? = AuthManager.currentUser.value) {
        // Invalidate and cancel any pass still running for the previous
        // account so it can neither write rows into the wiped DB nor persist
        // its cursor; runExclusive(wait = true) below waits for it to unwind.
        generation++
        inFlightJob?.cancel(CancellationException("SyncManager.performInitialSync"))
        withContext(Dispatchers.IO) {
            DatabaseManager.clearUserData()
            cursor = null
            user?.let { DatabaseManager.saveUserFromApi(it) }
        }
        // Wait for any running pass (a foreground trigger may have fired) rather
        // than being skipped like a standalone pull() would be.
        runExclusive(wait = true) { gen -> pullInternal(domains = null, gen = gen) }
        // Belt and braces: a pass that was mid-flight could have raced the wipe above.
        withContext(Dispatchers.IO) { user?.let { DatabaseManager.saveUserFromApi(it) } }
        onInitialSyncComplete?.let { hook ->
            try { hook() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w(TAG, "onInitialSyncComplete hook failed", e)
            }
        }
    }

    // MARK: - Reset (logout)

    /**
     * Cancel any in-flight pass, forget the cursor and last-sync time, and
     * return to IDLE. Drain registrations are process-level and survive.
     * Call from logout, before or after `DatabaseManager.clearUserData()`.
     */
    fun reset() {
        generation++
        inFlightJob?.cancel(CancellationException("SyncManager.reset"))
        inFlightJob = null
        draining.clear()
        if (::prefs.isInitialized) prefs.edit().clear().apply()
        _lastSyncDate.value = null
        _status.value = SyncStatus.IDLE
        _state.value = SyncState()
    }

    // MARK: - State helpers

    private fun setStatus(status: SyncStatus, error: String? = null) {
        _status.value = status
        _state.value = _state.value.copy(
            status = status,
            errorMessage = if (status == SyncStatus.ERROR) (error ?: "Sync failed") else null
        )
    }

    private fun markSynced() {
        val now = Date()
        prefs.edit().putLong(KEY_LAST_SYNC, now.time).apply()
        _lastSyncDate.value = now
        _status.value = SyncStatus.COMPLETE
        _state.value = SyncState(status = SyncStatus.COMPLETE, lastSyncAt = now, errorMessage = null)
    }
}
