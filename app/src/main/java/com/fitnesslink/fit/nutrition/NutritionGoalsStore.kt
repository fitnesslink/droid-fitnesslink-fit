package com.fitnesslink.fit.nutrition

import android.util.Log
import com.fitnesslink.fit.model.NutritionGoal
import com.fitnesslink.fit.model.api.toNutritionGoal
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.CreateNutritionProfileRequest
import com.fitnesslink.fit.network.dto.NutritionProfile
import com.fitnesslink.fit.network.dto.NutritionProfileWithTargets
import com.fitnesslink.fit.network.dto.NutritionTargets
import com.fitnesslink.fit.network.dto.OverrideNutritionTargetsRequest
import com.fitnesslink.fit.network.dto.UpdateNutritionProfileRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.nutritionCacheDelete
import com.fitnesslink.fit.persistence.nutritionCacheGet
import com.fitnesslink.fit.persistence.nutritionCacheObject
import com.fitnesslink.fit.persistence.nutritionCachePut
import com.fitnesslink.fit.persistence.nutritionCachePutObject
import com.fitnesslink.fit.sync.SyncDomain
import com.fitnesslink.fit.sync.SyncManager
import com.fitnesslink.fit.sync.TransientSyncException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * App-wide source of truth for the nutrition profile and its calculated /
 * overridden targets (port of iOS `NutritionGoalsStore`, FTNSSLNK-190/191).
 *
 *   onboarding create  -> apply(profileWithTargets)
 *   profile edit (PUT) -> apply(profileWithTargets)   (server recalculated)
 *   manual override    -> apply(targets)              (Source = "Manual")
 *
 * Applied targets are mirrored into the legacy local `NutritionGoal` row
 * (`DatabaseManager.nutritionGoal()`), which the offline dashboard, the
 * reports and `/nutrition-goals/me` all agree on. Profile + targets are also
 * cached as JSON in `nutrition_cache` so the Goals hub renders on a cold
 * offline launch.
 */
object NutritionGoalsStore {
    private const val TAG = "NutritionGoalsStore"
    private const val KEY_PROFILE = "profile"
    private const val KEY_TARGETS = "targets"
    private const val KEY_STATE = "profileState"

    enum class State {
        /** Not fetched yet this session. */
        UNKNOWN,
        LOADING,
        /** Profile exists; `profile` + `targets` are populated. */
        LOADED,
        /** Server said 404 — onboarding hasn't happened. */
        NO_PROFILE,
        /** Fetch failed (offline / server error); cached values may be shown. */
        FAILED
    }

    private val _state = MutableStateFlow(State.UNKNOWN)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _goal = MutableStateFlow(NutritionGoal())
    /** The legacy flat goal (local row) — always available, offline or not. */
    val goal: StateFlow<NutritionGoal> = _goal.asStateFlow()

    private val _targets = MutableStateFlow<NutritionTargets?>(null)
    val targets: StateFlow<NutritionTargets?> = _targets.asStateFlow()

    private val _profile = MutableStateFlow<NutritionProfile?>(null)
    val profile: StateFlow<NutritionProfile?> = _profile.asStateFlow()

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Fires after every applied change (refresh, override, revert, profile save, manual goal). */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private val hydrated = AtomicBoolean(false)
    private val registered = AtomicBoolean(false)
    private val refreshMutex = Mutex()

    val hasProfile: Boolean get() = _profile.value != null
    val isOverridden: Boolean get() = _targets.value?.isManualOverride ?: false

    /** True when we KNOW onboarding hasn't happened (vs. just not loaded yet). */
    val needsOnboarding: Boolean get() = _state.value == State.NO_PROFILE

    /** The current calorie goal: the server targets when known, else the local row. */
    fun calorieGoal(): Int = _targets.value?.let { Math.round(it.dailyCalorieTarget).toInt() } ?: _goal.value.calorieGoal

    // MARK: - Lifecycle

    /** Load the local goal row + cached profile/targets so screens render before any network call. */
    fun hydrateFromCache() {
        if (!hydrated.compareAndSet(false, true)) return
        runCatching {
            _goal.value = DatabaseManager.nutritionGoal()
            _profile.value = DatabaseManager.nutritionCacheObject<NutritionProfile>(KEY_PROFILE)
            _targets.value = DatabaseManager.nutritionCacheObject<NutritionTargets>(KEY_TARGETS)
            if (_state.value == State.UNKNOWN) {
                when (DatabaseManager.nutritionCacheGet(KEY_STATE)) {
                    State.NO_PROFILE.name -> if (_profile.value == null) _state.value = State.NO_PROFILE
                    State.LOADED.name -> if (_profile.value != null) _state.value = State.LOADED
                }
            }
        }.onFailure { Log.w(TAG, "hydrateFromCache failed", it) }
    }

    /**
     * Registers the NUTRITION_GOALS drain: a legacy manual goal saved while
     * offline (`saveManualGoal`) is queued and POSTed to `/nutrition-goals`
     * once connectivity returns.
     */
    fun ensureRegistered() {
        if (!registered.compareAndSet(false, true)) return
        hydrateFromCache()
        if (SyncManager.hasDrain(SyncDomain.NUTRITION_GOALS)) return
        SyncManager.registerDrain(SyncDomain.NUTRITION_GOALS) {
            SyncManager.drainQueue(SyncDomain.NUTRITION_GOALS) { entry ->
                val goal = SyncManager.decodePayload<NutritionGoal>(entry.payload) ?: return@drainQueue
                try {
                    ApiClient.nutritionApi.upsertGoal(goal)
                } catch (e: ApiError.Offline) {
                    throw TransientSyncException("offline", e)
                } catch (e: ApiError.Timeout) {
                    throw TransientSyncException("timeout", e)
                }
            }
        }
    }

    /**
     * Fetch the profile + current targets (`GET /profile/me`). Concurrent
     * calls collapse into one in-flight fetch. 404 = no profile: still ask
     * `/profile/targets` because a manual override set WITHOUT onboarding
     * produces a current goal.
     */
    suspend fun refresh() {
        ensureRegistered()
        if (!NetworkMonitor.isConnected.value) {
            if (_state.value == State.UNKNOWN) _state.value = if (hasProfile) State.LOADED else State.FAILED
            return
        }
        if (!refreshMutex.tryLock()) return
        try {
            val previous = _state.value
            _state.value = State.LOADING
            try {
                val response = ApiClient.nutritionApi.getNutritionProfile()
                apply(response)
            } catch (e: ApiError.NotFound) {
                _profile.value = null
                DatabaseManager.nutritionCacheDelete(KEY_PROFILE)
                val targets = try { ApiClient.nutritionApi.getCurrentNutritionTargets() } catch (_: Exception) { null }
                _targets.value = targets
                if (targets != null) {
                    mirrorToLocalGoal(targets)
                    DatabaseManager.nutritionCachePutObject(KEY_TARGETS, targets)
                } else {
                    DatabaseManager.nutritionCacheDelete(KEY_TARGETS)
                }
                _state.value = State.NO_PROFILE
                DatabaseManager.nutritionCachePut(KEY_STATE, State.NO_PROFILE.name)
                emitChange()
            } catch (e: CancellationException) {
                _state.value = previous
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "refresh failed", e)
                // Keep whatever we had; screens fall back to the local goal row.
                _state.value = if (hasProfile) State.LOADED else if (previous == State.NO_PROFILE) State.NO_PROFILE else State.FAILED
            }
        } finally {
            refreshMutex.unlock()
        }
    }

    // MARK: - Mutations

    /** Onboarding: `POST /profile` (409 = a profile already exists). */
    suspend fun createProfile(request: CreateNutritionProfileRequest): NutritionProfileWithTargets {
        ensureRegistered()
        val response = ApiClient.nutritionApi.createNutritionProfile(request)
        apply(response)
        return response
    }

    /** Profile edit: rowVersion-guarded `PUT /profile`; the server recalculates targets. */
    suspend fun updateProfile(request: UpdateNutritionProfileRequest): NutritionProfileWithTargets {
        ensureRegistered()
        val response = ApiClient.nutritionApi.updateNutritionProfile(request)
        apply(response)
        return response
    }

    /** Manual override (`POST /profile/targets/override`) — provenance becomes "Manual". */
    suspend fun overrideTargets(calories: Int, protein: Double, fat: Double, carbs: Double) {
        ensureRegistered()
        val request = OverrideNutritionTargetsRequest(
            calorieGoal = calories.toDouble(), proteinTarget = protein, fatTarget = fat, carbsTarget = carbs
        )
        val targets = ApiClient.nutritionApi.overrideNutritionTargets(request)
        apply(targets)
    }

    /**
     * Dropping an override: a rowVersion-only PUT is the server's
     * "recalculate from profile" — it writes a fresh Calculated goal.
     */
    suspend fun revertToCalculated() {
        ensureRegistered()
        val profile = _profile.value ?: throw IllegalStateException("No nutrition profile to recalculate from")
        val response = ApiClient.nutritionApi.updateNutritionProfile(UpdateNutritionProfileRequest(rowVersion = profile.rowVersion))
        apply(response)
    }

    /**
     * Offline / legacy fallback for the manual path: persist the flat goal
     * locally, queue `POST /nutrition-goals`, and flush inline when online.
     * Used when the override endpoint can't be reached.
     */
    suspend fun saveManualGoal(goal: NutritionGoal) {
        ensureRegistered()
        DatabaseManager.insertNutritionGoal(goal)
        _goal.value = DatabaseManager.nutritionGoal()
        val queueId = DatabaseManager.enqueueSyncEntry(
            entityType = SyncManager.NUTRITION_GOALS_TYPE,
            entityId = goal.id,
            action = "update",
            payload = ApiClient.gson.toJson(goal)
        )
        emitChange()
        if (!NetworkMonitor.isConnected.value) return
        try {
            ApiClient.nutritionApi.upsertGoal(goal)
            DatabaseManager.markSyncEntrySynced(queueId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "upsertGoal failed; stays queued", e)
        }
    }

    // MARK: - Apply

    /** Adopt a profile+targets response (onboarding create / profile edit / refresh). */
    fun apply(response: NutritionProfileWithTargets) {
        _profile.value = response.profile
        _targets.value = response.targets
        _state.value = State.LOADED
        mirrorToLocalGoal(response.targets)
        runCatching {
            DatabaseManager.nutritionCachePutObject(KEY_PROFILE, response.profile)
            DatabaseManager.nutritionCachePutObject(KEY_TARGETS, response.targets)
            DatabaseManager.nutritionCachePut(KEY_STATE, State.LOADED.name)
        }
        emitChange()
    }

    /** Adopt standalone targets (manual override / GET targets). */
    fun apply(targets: NutritionTargets) {
        _targets.value = targets
        if (hasProfile) _state.value = State.LOADED
        mirrorToLocalGoal(targets)
        runCatching { DatabaseManager.nutritionCachePutObject(KEY_TARGETS, targets) }
        emitChange()
    }

    /** Drop local knowledge (sign-out). */
    fun reset() {
        _profile.value = null
        _targets.value = null
        _state.value = State.UNKNOWN
        _goal.value = NutritionGoal()
        hydrated.set(false)
        runCatching {
            DatabaseManager.nutritionCacheDelete(KEY_PROFILE)
            DatabaseManager.nutritionCacheDelete(KEY_TARGETS)
            DatabaseManager.nutritionCacheDelete(KEY_STATE)
        }
    }

    private fun mirrorToLocalGoal(t: NutritionTargets) {
        runCatching {
            val existing = DatabaseManager.nutritionGoal()
            val mirrored = t.toNutritionGoal(existing.id)
            DatabaseManager.insertNutritionGoal(mirrored)
            _goal.value = mirrored
        }.onFailure { Log.w(TAG, "mirrorToLocalGoal failed", it) }
    }

    private fun emitChange() {
        _changes.tryEmit(Unit)
    }
}
