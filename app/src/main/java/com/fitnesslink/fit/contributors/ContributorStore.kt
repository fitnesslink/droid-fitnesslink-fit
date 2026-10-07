package com.fitnesslink.fit.contributors

import android.util.Log
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.ContributorSummaryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * In-memory cache of the contributor roster (iOS `ContributorStore`) so the
 * list screens can resolve a `contributorId` to a display name and populate
 * the contributor filter menu. Mirrors `ClassificationStore`'s launch-refresh
 * + name-lookup pattern: `GET /contributors?pageSize=200`, fetched once per
 * process (pass `force = true` to bypass), failures leave the cache intact.
 *
 * Rows are the slim [ContributorSummaryDto] (id + type + displayName) — the
 * `Contributor` model has no display name, so it can't back a menu.
 */
object ContributorStore {
    private const val TAG = "ContributorStore"

    /**
     * FitnessLink's own house contributor. Content attributed to it is
     * first-party: the detail screens hide the "coach" card for it (iOS
     * `ProgramContributorView.fitnessLinkContributorId`).
     */
    val houseContributorId: String? = "daf56c04-f9aa-41fb-bfae-690bea5260a2"

    private val _contributors = MutableStateFlow<List<ContributorSummaryDto>>(emptyList())
    /** Alphabetical by label. */
    val contributors: StateFlow<List<ContributorSummaryDto>> = _contributors.asStateFlow()

    @Volatile private var namesById: Map<String, String> = emptyMap()
    private var hasFetched = false

    suspend fun refresh() = refresh(force = false)

    suspend fun refresh(force: Boolean) {
        if (hasFetched && !force) return
        val online = runCatching { NetworkMonitor.isConnected.value }.getOrDefault(true)
        if (!online) return
        val rows = runCatching { ApiClient.contributorApi.list(pageSize = 200).items }
            .onFailure { Log.w(TAG, "contributors fetch failed: ${it.message}") }
            .getOrNull() ?: return
        hasFetched = true
        seed(rows)
    }

    /** Display name for a contributor id (case-insensitive), or null if unknown / null id. */
    fun name(id: String?): String? {
        if (id.isNullOrBlank()) return null
        return namesById[id.lowercase(Locale.US)]
    }

    /** True when [id] is the FitnessLink house contributor (case-insensitive). */
    fun isHouse(id: String?): Boolean =
        id != null && houseContributorId != null && id.equals(houseContributorId, ignoreCase = true)

    /**
     * The cached contributors whose ids appear in [presentIds], preserving the
     * store's alphabetical order — scopes a screen's filter menu to coaches
     * that actually have content on that screen.
     */
    fun present(presentIds: Set<String>): List<ContributorSummaryDto> {
        val lowered = presentIds.mapTo(HashSet()) { it.lowercase(Locale.US) }
        return _contributors.value.filter { lowered.contains(it.id.lowercase(Locale.US)) }
    }

    private fun seed(rows: List<ContributorSummaryDto>) {
        _contributors.value = rows.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        val map = LinkedHashMap<String, String>()
        rows.forEach { row -> map.putIfAbsent(row.id.lowercase(Locale.US), row.label) }
        namesById = map
    }

    /** Test seam: populate without the network. */
    internal fun seedForTesting(rows: List<ContributorSummaryDto>) {
        hasFetched = true
        seed(rows)
    }
}
