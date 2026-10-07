package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.contributors.ContributorStore
import com.fitnesslink.fit.model.WorkoutList
import com.fitnesslink.fit.model.api.ContentTypeIds
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.ShareableContentKind
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.toListRow
import com.fitnesslink.fit.workouts.WorkoutStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Workouts "See All" list — port of iOS `WorkoutsViewModel` + the filtering
 * `WorkoutsView` does on top of it. Shows template content only (has a
 * contributor, not inside a contributor program); every filter runs
 * client-side against cached rows so it works offline. Equipment / anatomy
 * ids come from the last `/workouts/list-view` snapshot and are not
 * persisted (same as iOS).
 */
class WorkoutsViewModel : ViewModel() {
    var workouts by mutableStateOf<List<WorkoutList>>(emptyList())
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    var searchText by mutableStateOf("")
    var showFavoritesOnly by mutableStateOf(false)
    var selectedContributorId by mutableStateOf<String?>(null)
    /** Android extra from `ContributorFilterMenu`: show the user's own workouts instead of templates. */
    var mineSelected by mutableStateOf(false)
    var selectedEquipmentIds by mutableStateOf<Set<String>>(emptySet())
    var selectedAnatomyIds by mutableStateOf<Set<String>>(emptySet())
    /** Android-only training-level row (iOS has no level filter). */
    var levelFilter by mutableStateOf<String?>(null)

    var equipmentByWorkoutId by mutableStateOf<Map<String, Set<String>>>(emptyMap())
        private set
    var anatomyByWorkoutId by mutableStateOf<Map<String, Set<String>>>(emptyMap())
        private set

    val hasActiveFilters: Boolean
        get() = showFavoritesOnly || selectedContributorId != null || mineSelected ||
            selectedEquipmentIds.isNotEmpty() || selectedAnatomyIds.isNotEmpty() || levelFilter != null

    fun clearFilters() {
        showFavoritesOnly = false
        selectedContributorId = null
        mineSelected = false
        selectedEquipmentIds = emptySet()
        selectedAnatomyIds = emptySet()
        levelFilter = null
    }

    fun toggleEquipment(id: String) {
        val lc = id.lowercase(Locale.US)
        selectedEquipmentIds = if (selectedEquipmentIds.contains(lc)) selectedEquipmentIds - lc else selectedEquipmentIds + lc
    }

    fun toggleAnatomy(id: String) {
        val lc = id.lowercase(Locale.US)
        selectedAnatomyIds = if (selectedAnatomyIds.contains(lc)) selectedAnatomyIds - lc else selectedAnatomyIds + lc
    }

    /** Contributor ids present in the current rows (scopes the filter menu). */
    val presentContributorIds: Set<String>
        get() = workouts.mapNotNull { it.contributorId?.lowercase(Locale.US) }.toSet()

    val visibleWorkouts: List<WorkoutList>
        get() {
            var items = workouts
            if (showFavoritesOnly) items = items.filter { it.isFavorite }
            selectedContributorId?.let { cid -> items = items.filter { it.contributorId.equals(cid, ignoreCase = true) } }
            if (selectedEquipmentIds.isNotEmpty()) {
                items = items.filter { w -> (equipmentByWorkoutId[w.id.lowercase(Locale.US)] ?: emptySet()).any { selectedEquipmentIds.contains(it) } }
            }
            if (selectedAnatomyIds.isNotEmpty()) {
                items = items.filter { w -> (anatomyByWorkoutId[w.id.lowercase(Locale.US)] ?: emptySet()).any { selectedAnatomyIds.contains(it) } }
            }
            levelFilter?.let { level -> items = items.filter { it.trainingLevel.equals(level, ignoreCase = true) } }
            val q = searchText.trim().lowercase(Locale.US)
            if (q.isNotEmpty()) items = items.filter { it.name.lowercase(Locale.US).contains(q) }
            return items
        }

    fun loadData() {
        WorkoutStore.ensureRegistered()
        workouts = visibleTemplates()
        viewModelScope.launch { refreshFromServer() }
        viewModelScope.launch { runCatching { ContributorStore.refresh() } }
    }

    /** Re-read the cache (cheap) — e.g. when the list reappears after a delete on the detail screen. */
    fun reloadFromCache() {
        workouts = visibleTemplates()
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        isRefreshing = true
        try {
            val remote = withContext(Dispatchers.IO) { runCatching { ApiClient.workoutApi.listView() }.getOrNull() }
            if (remote != null) {
                withContext(Dispatchers.IO) {
                    remote.forEachIndexed { index, w -> DatabaseManager.upsertWorkoutListRow(w.toListRow(sortIndex = index)) }
                    // Stamp the response-array order so cached reads reopen in the server's ranking.
                    DatabaseManager.setWorkoutSortOrder(remote.map { it.id })
                }
                equipmentByWorkoutId = remote.associate { w ->
                    w.id.lowercase(Locale.US) to w.equipmentIds.map { it.toString().lowercase(Locale.US) }.toSet()
                }.filterValues { it.isNotEmpty() }
                anatomyByWorkoutId = remote.associate { w ->
                    w.id.lowercase(Locale.US) to w.anatomyIds.map { it.toString().lowercase(Locale.US) }.toSet()
                }.filterValues { it.isNotEmpty() }
            }
            hydrateFavorites()
            workouts = visibleTemplates()
        } finally {
            isRefreshing = false
        }
    }

    /** Server is authoritative for favorites — replace, don't merge. */
    private suspend fun hydrateFavorites() {
        val favorites = withContext(Dispatchers.IO) { runCatching { ApiClient.contentApi.getFavorites() }.getOrNull() } ?: return
        val ids = favorites
            .filter { it.contentTypeId.toString().equals(ContentTypeIds.workout, ignoreCase = true) }
            .map { it.contentId.toString() }
        withContext(Dispatchers.IO) { DatabaseManager.replaceFavoriteWorkouts(ids) }
    }

    /** Optimistic heart toggle: flip locally, persist, reconcile to the server's state, revert on error. */
    fun toggleFavorite(workout: WorkoutList) {
        val wasFavorited = workout.isFavorite
        val nowFavorited = !wasFavorited
        DatabaseManager.setWorkoutFavorite(workout.id, nowFavorited)
        workouts = visibleTemplates()
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.contentApi.toggleFavorite(workout.id, ShareableContentKind.WORKOUT.serverName)
                }
                if (response.isFavorited != nowFavorited) {
                    DatabaseManager.setWorkoutFavorite(workout.id, response.isFavorited)
                    workouts = visibleTemplates()
                }
            } catch (_: Exception) {
                DatabaseManager.setWorkoutFavorite(workout.id, wasFavorited)
                workouts = visibleTemplates()
            }
        }
    }

    /**
     * Template content only (has a contributor), excluding workouts that
     * belong to a contributor program (surfaced via their program instead).
     * "Mine" flips to the user's own authored workouts.
     */
    private fun visibleTemplates(): List<WorkoutList> {
        val rows = DatabaseManager.catalogVisibleWorkouts()
        return if (mineSelected) rows.filter { it.contributorId == null && it.sharedByUserId == null }
        else rows.filter { it.contributorId != null }
    }

    fun toggleMine() {
        mineSelected = !mineSelected
        if (mineSelected) selectedContributorId = null
        workouts = visibleTemplates()
    }
}
