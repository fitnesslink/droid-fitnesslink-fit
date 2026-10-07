package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.model.BrowserTab
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.Personalization
import com.fitnesslink.fit.movements.BrowserFilterState
import com.fitnesslink.fit.movements.MovementListQuery
import com.fitnesslink.fit.movements.MovementStore
import com.fitnesslink.fit.movements.TagFilterOptions
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Port of iOS `ExerciseBrowserViewModel`. The list is always hydrated from
 * SQLite first (instant, offline), then `GET /movements/list-view` with the
 * full param set upserts the cache and re-hydrates. Search + chip changes
 * are debounced ~300 ms with cancel-and-replace so only the latest keystroke
 * reaches the server.
 */
class ExerciseBrowserViewModel : ViewModel() {
    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    var filters by mutableStateOf(BrowserFilterState())
        private set
    var selectedTab by mutableStateOf(BrowserTab.All)
        private set
    var movements by mutableStateOf<List<MovementLibraryItem>>(emptyList())
        private set
    var tagOptions by mutableStateOf(TagFilterOptions())
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    // Convenience accessors the sheet binds to.
    val searchText: String get() = filters.searchText
    val selectedAnatomyIds: Set<String> get() = filters.anatomyIds
    val selectedEquipmentIds: Set<String> get() = filters.equipmentIds

    /** Count badge on the "More filters" chip — drawer-only selections. */
    val nonPrimaryFilterCount: Int
        get() = filters.nonPrimaryFilterCount(
            ClassificationStore.primaryAnatomy.map { it.id },
            ClassificationStore.primaryEquipment.map { it.id }
        )

    /**
     * Lowercased movement ids from the most recent list-view response while a
     * tag filter is active. The local table doesn't store difficulty / goal /
     * pattern / location tags, so those dimensions can only be applied by
     * narrowing to what the server matched. Null until a response lands.
     */
    private var serverTagMatchIds: Set<String>? = null
    private var searchJob: Job? = null
    private var loaded = false

    init {
        MovementStore.ensureRegistered()
        viewModelScope.launch { MovementStore.changes.collectLatest { hydrateFromLocal() } }
    }

    fun loadData() {
        if (!loaded) {
            loaded = true
            loadTagFilterOptions()
            viewModelScope.launch { runCatching { ClassificationStore.refresh() } }
            viewModelScope.launch { MovementStore.refreshFavoritesAndRecents() }
        }
        hydrateFromLocal()
        scheduleSearch()
    }

    // MARK: - Filter mutations

    fun setSearchText(text: String) {
        if (text == filters.searchText) return
        filters = filters.copy(searchText = text)
        scheduleSearch()
    }

    fun toggleAnatomy(id: String) {
        filters = filters.copy(anatomyIds = filters.anatomyIds.toggled(id))
        scheduleSearch()
    }

    fun toggleEquipment(id: String) {
        filters = filters.copy(equipmentIds = filters.equipmentIds.toggled(id))
        scheduleSearch()
    }

    /** Apply the "More filters" drawer's drafts in one shot. */
    fun applyFilters(
        anatomyIds: Set<String>,
        equipmentIds: Set<String>,
        difficultyIds: Set<String>,
        goalIds: Set<String>,
        movementPatternIds: Set<String>,
        locationIds: Set<String>
    ) {
        filters = filters.copy(
            anatomyIds = anatomyIds, equipmentIds = equipmentIds,
            difficultyIds = difficultyIds, goalIds = goalIds,
            movementPatternIds = movementPatternIds, locationIds = locationIds
        )
        scheduleSearch()
    }

    fun selectTab(tab: BrowserTab) {
        if (tab == selectedTab) return
        selectedTab = tab
        hydrateFromLocal()
        scheduleSearch()
    }

    fun clearFilters() {
        filters = filters.cleared()
        serverTagMatchIds = null
        scheduleSearch()
    }

    /** Debounced entry point kept for callers that mutate state through the setters above. */
    fun search() = scheduleSearch()

    // MARK: - Favorites

    fun toggleFavorite(movementId: String) {
        val was = movements.firstOrNull { it.id.equals(movementId, ignoreCase = true) }?.isFavorite
            ?: DatabaseManager.movement(movementId)?.isFavorite ?: false
        MovementStore.toggleFavorite(movementId, was)
        hydrateFromLocal()
    }

    /** Record "used" when the user adds a movement from the browser. */
    fun recordRecent(movementId: String) = MovementStore.recordRecent(movementId)

    // MARK: - Tag taxonomy

    private fun loadTagFilterOptions() {
        runCatching { DatabaseManager.personalizations() }.getOrNull()?.let { applyTaxonomy(it) }
        viewModelScope.launch {
            if (!NetworkMonitor.isConnected.value) return@launch
            val remote = runCatching { ApiClient.personalizationApi.getAll() }.getOrNull() ?: return@launch
            if (remote.isNotEmpty()) applyTaxonomy(remote)
        }
    }

    private fun applyTaxonomy(groups: List<Personalization>) {
        tagOptions = TagFilterOptions.from(groups)
    }

    // MARK: - Search pipeline

    private fun scheduleSearch() {
        // Any change invalidates the previous server-matched id set — a stale
        // set would narrow the new search to the OLD filter combination.
        serverTagMatchIds = null
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            hydrateFromLocal()
            refreshMovementsFromServer()
        }
    }

    private fun hydrateFromLocal() {
        val f = filters
        val list = runCatching {
            when (selectedTab) {
                BrowserTab.All -> DatabaseManager.searchMovements(f.searchText, anatomyIds = f.anatomyIds, equipmentIds = f.equipmentIds)
                BrowserTab.Mine -> DatabaseManager.searchMovements(f.searchText, anatomyIds = f.anatomyIds, equipmentIds = f.equipmentIds, mineOnly = true)
                BrowserTab.Favorites -> DatabaseManager.favoriteMovements()
                BrowserTab.Recent -> DatabaseManager.recentMovements()
            }
        }.getOrDefault(emptyList())
        val matchIds = serverTagMatchIds
        movements = if (f.isTagFilterActive && (selectedTab == BrowserTab.All || selectedTab == BrowserTab.Mine) && matchIds != null) {
            list.filter { it.id.lowercase(Locale.US) in matchIds }
        } else {
            list
        }
    }

    private suspend fun refreshMovementsFromServer() {
        val f = filters
        val tab = selectedTab
        isRefreshing = true
        try {
            val remote = MovementStore.refreshListView(MovementListQuery.forBrowser(f, tab)) ?: return
            // Only honour the response if the filters haven't moved on.
            if (f != filters || tab != selectedTab) return
            serverTagMatchIds = if (f.isTagFilterActive) remote.mapTo(HashSet()) { it.id.toString().lowercase(Locale.US) } else null
            withContext(Dispatchers.Main) { hydrateFromLocal() }
        } finally {
            isRefreshing = false
        }
    }

    private fun Set<String>.toggled(id: String): Set<String> =
        if (any { it.equals(id, ignoreCase = true) }) filterNot { it.equals(id, ignoreCase = true) }.toSet() else this + id
}
