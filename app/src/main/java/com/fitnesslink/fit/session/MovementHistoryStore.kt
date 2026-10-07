package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.api.SessionApi
import com.fitnesslink.fit.network.dto.MovementHistorySummary
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.maxLoggedWeight
import com.fitnesslink.fit.persistence.movementIdsWithWeightHistory
import com.fitnesslink.fit.persistence.previousLoggedWeight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Last session's logged weight for a movement (FTNSSLNK-408). Stored lbs. */
data class MovementPreviousWeight(
    val weightLbs: Double,
    val reps: Int,
    val isTopSet: Boolean,
    val sessionId: String?,
    val loggedAtMs: Long?
) {
    /** `Previous: 185 lbs × 8` — the active-screen / playlist-row line. */
    val previousLine: String
        get() {
            val weight = WeightUnit.displayString(fromLbs = weightLbs)
            return if (reps > 0) "Previous: $weight × $reps" else "Previous: $weight"
        }

    /** `Last time: 185 lbs × 8 reps` — the log sheet's line. */
    val lastTimeLine: String
        get() {
            val weight = WeightUnit.displayString(fromLbs = weightLbs)
            return if (reps > 0) "Last time: $weight × $reps reps" else "Last time: $weight"
        }
}

/** Heaviest set ever logged for a movement (FTNSSLNK-434). Stored lbs. */
data class MovementWeightMax(val maxWeight: Double, val reps: Int, val setCount: Int)

/** `185 lbs × 8 reps` / top-set lookups shared by the log sheets and the movement-info sheet. */
object MovementPerformanceLines {
    fun topSetLine(top: MovementWeightMax): String {
        val weight = WeightUnit.displayString(fromLbs = top.maxWeight)
        return if (top.reps > 0) "$weight × ${top.reps} reps" else weight
    }

    /** The heaviest set ever logged for [task] — by movement id, or by name for tasks with no catalog movement. */
    fun topSet(task: WorkoutTask, excludingSessionId: String? = null): MovementWeightMax? =
        if (task.movementId != null) MovementHistoryStore.topSet(task.movementId, task.name, excludingSessionId)
        else DatabaseManager.maxLoggedWeight(null, task.name, MovementHistoryStore.userId, excludingSessionId)
}

/**
 * "Previous weight" / "Top set" per movement (FTNSSLNK-408). Local history is the source of
 * truth; on a fresh install with nothing local the store seeds itself once per launch from
 * `GET /sessions/me/movement-history` (≤ 50 ids per call). Every server failure is swallowed.
 */
object MovementHistoryStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Server-seeded figures by lowercased movement id. */
    private val serverSummaries = mutableMapOf<String, MovementHistorySummary>()
    /** Movement ids already asked of the server this launch (hit or miss). */
    private val requested = mutableSetOf<String>()
    private val _version = MutableStateFlow(0)
    /** Bumped whenever the server seed lands so rows re-read their "Previous" line. */
    val version: StateFlow<Int> = _version.asStateFlow()

    /** Injectable fetch for tests. Null = the real API. */
    var fetch: (suspend (List<String>) -> List<MovementHistorySummary>)? = null

    internal val userId: String? get() = DatabaseManager.user()?.id

    fun serverSummary(movementId: String): MovementHistorySummary? = serverSummaries[movementId.lowercase(Locale.US)]

    /** Last session's logged weight: local history first, else the server seed. */
    fun previousWeight(movementId: String?, taskName: String? = null, excludingSessionId: String? = null): MovementPreviousWeight? {
        if (movementId.isNullOrEmpty()) return null
        DatabaseManager.previousLoggedWeight(movementId, userId, excludingSessionId, taskName)?.let { return it }
        return serverSummaries[movementId.lowercase(Locale.US)]?.let(::previousWeight)
    }

    /** Heaviest logged set for a movement (local, else the server's best). */
    fun topSet(movementId: String?, taskName: String? = null, excludingSessionId: String? = null): MovementWeightMax? {
        if (movementId.isNullOrEmpty()) return null
        DatabaseManager.maxLoggedWeight(movementId, taskName, userId, excludingSessionId)?.let { return it }
        val summary = serverSummaries[movementId.lowercase(Locale.US)] ?: return null
        val best = summary.bestWeight ?: return null
        if (best <= 0) return null
        return MovementWeightMax(maxWeight = best, reps = summary.bestReps ?: 0, setCount = 1)
    }

    /** The server's "last" figures as a previous weight (never a "top set"). */
    fun previousWeight(summary: MovementHistorySummary): MovementPreviousWeight? {
        val weight = summary.lastWeight ?: return null
        if (weight <= 0) return null
        return MovementPreviousWeight(weight, summary.lastReps ?: 0, isTopSet = false, sessionId = null, loggedAtMs = summary.lastLoggedAt?.time)
    }

    /** Ask the server about movements with no local weighted history that haven't been asked about yet. Fire-and-forget. */
    fun seedIfNeeded(movementIds: List<String>) {
        val candidates = movementIds.filter { it.isNotEmpty() }.map { it.lowercase(Locale.US) }.distinct().filter { !requested.contains(it) }
        if (candidates.isEmpty()) return
        val local = DatabaseManager.movementIdsWithWeightHistory(candidates, userId)
        val missing = candidates.filter { !local.contains(it) }.sorted()
        requested.addAll(candidates)
        if (missing.isEmpty()) return
        if (fetch == null && !NetworkMonitor.isConnected.value) return
        scope.launch {
            val summaries = try {
                withContext(Dispatchers.IO) {
                    fetch?.invoke(missing) ?: missing.chunked(SessionApi.MOVEMENT_HISTORY_MAX_IDS).flatMap { chunk ->
                        val query = SessionApi.movementHistoryQuery(chunk) ?: return@flatMap emptyList()
                        ApiClient.sessionApi.movementHistory(query)
                    }
                }
            } catch (_: Exception) {
                return@launch
            }
            apply(summaries)
        }
    }

    /** Merge a server response into the seed (also the test entry point). */
    fun apply(summaries: List<MovementHistorySummary>) {
        if (summaries.isEmpty()) return
        for (summary in summaries) serverSummaries[summary.movementId.lowercase(Locale.US)] = summary
        _version.value += 1
    }

    /** Forget every seed (sign-out / tests). */
    fun reset() {
        serverSummaries.clear()
        requested.clear()
        _version.value += 1
    }
}
