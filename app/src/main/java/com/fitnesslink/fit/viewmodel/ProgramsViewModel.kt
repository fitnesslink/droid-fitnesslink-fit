package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.contributors.ContributorStore
import com.fitnesslink.fit.model.ProgramList
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.programs.ProgramStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Programs "See All" — port of iOS `ProgramsViewModel` + the filtering
 * `ProgramsView` does on top of it. Template content only (has a
 * contributor), in server ranking order; every filter runs client-side
 * against cached rows so it works offline. Equipment / anatomy ids come
 * from the last `/programs/list-view` snapshot and are not persisted.
 */
class ProgramsViewModel : ViewModel() {
    var programs by mutableStateOf<List<ProgramList>>(emptyList())
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    var searchText by mutableStateOf("")
    var showFavoritesOnly by mutableStateOf(false)
    var selectedContributorId by mutableStateOf<String?>(null)
    /** Android extra from `ContributorFilterMenu`: show the user's own programs instead of templates. */
    var mineSelected by mutableStateOf(false)
    var selectedEquipmentIds by mutableStateOf<Set<String>>(emptySet())
    var selectedAnatomyIds by mutableStateOf<Set<String>>(emptySet())

    /** Android-only extras (iOS has neither on Programs). */
    var levelFilter by mutableStateOf<String?>(null)
    var lengthFilter by mutableStateOf<IntRange?>(null)

    var equipmentByProgramId by mutableStateOf<Map<String, Set<String>>>(emptyMap())
        private set
    var anatomyByProgramId by mutableStateOf<Map<String, Set<String>>>(emptyMap())
        private set

    val hasActiveFilters: Boolean
        get() = showFavoritesOnly || selectedContributorId != null || mineSelected ||
            selectedEquipmentIds.isNotEmpty() || selectedAnatomyIds.isNotEmpty() ||
            levelFilter != null || lengthFilter != null

    fun clearFilters() {
        showFavoritesOnly = false
        selectedContributorId = null
        mineSelected = false
        selectedEquipmentIds = emptySet()
        selectedAnatomyIds = emptySet()
        levelFilter = null
        lengthFilter = null
        programs = visibleRows()
    }

    fun toggleEquipment(id: String) {
        val lc = id.lowercase(Locale.US)
        selectedEquipmentIds = if (lc in selectedEquipmentIds) selectedEquipmentIds - lc else selectedEquipmentIds + lc
    }

    fun toggleAnatomy(id: String) {
        val lc = id.lowercase(Locale.US)
        selectedAnatomyIds = if (lc in selectedAnatomyIds) selectedAnatomyIds - lc else selectedAnatomyIds + lc
    }

    fun toggleMine() {
        mineSelected = !mineSelected
        if (mineSelected) selectedContributorId = null
        programs = visibleRows()
    }

    /** Contributor ids present in the current rows (scopes the filter menu). */
    val presentContributorIds: Set<String>
        get() = programs.mapNotNull { it.contributorId?.lowercase(Locale.US) }.toSet()

    val visiblePrograms: List<ProgramList>
        get() {
            var items = programs
            if (showFavoritesOnly) items = items.filter { it.isFavorite }
            selectedContributorId?.let { cid -> items = items.filter { it.contributorId.equals(cid, ignoreCase = true) } }
            if (selectedEquipmentIds.isNotEmpty()) {
                items = items.filter { p -> (equipmentByProgramId[p.id.lowercase(Locale.US)] ?: emptySet()).any { it in selectedEquipmentIds } }
            }
            if (selectedAnatomyIds.isNotEmpty()) {
                items = items.filter { p -> (anatomyByProgramId[p.id.lowercase(Locale.US)] ?: emptySet()).any { it in selectedAnatomyIds } }
            }
            levelFilter?.let { level -> items = items.filter { it.trainingLevel.equals(level, ignoreCase = true) } }
            lengthFilter?.let { range -> items = items.filter { it.weeks != null && it.weeks in range } }
            val q = searchText.trim().lowercase(Locale.US)
            if (q.isNotEmpty()) items = items.filter { it.name.lowercase(Locale.US).contains(q) }
            return items
        }

    fun loadData() {
        programs = visibleRows()
        viewModelScope.launch { refreshFromServer() }
        viewModelScope.launch { runCatching { ContributorStore.refresh() } }
    }

    /** Re-read the cache (cheap) — e.g. when the list reappears after a delete on the detail screen. */
    fun reloadFromCache() {
        programs = visibleRows()
    }

    private suspend fun refreshFromServer() {
        isRefreshing = true
        try {
            val snapshot = ProgramStore.refreshListView()
            if (snapshot != null) {
                equipmentByProgramId = snapshot.equipmentByProgramId
                anatomyByProgramId = snapshot.anatomyByProgramId
            }
            programs = visibleRows()
        } finally {
            isRefreshing = false
        }
    }

    /** Optimistic heart toggle: flip locally, persist, reconcile to the server's state, revert on error. */
    fun toggleFavorite(program: ProgramList) {
        val wasFavorited = program.isFavorite
        val nowFavorited = !wasFavorited
        DatabaseManager.setProgramFavorite(program.id, nowFavorited)
        programs = visibleRows()
        viewModelScope.launch {
            val serverState = ProgramStore.toggleFavoriteOnServer(program.id)
            when {
                serverState == null -> withContext(Dispatchers.IO) { DatabaseManager.setProgramFavorite(program.id, wasFavorited) }
                serverState != nowFavorited -> withContext(Dispatchers.IO) { DatabaseManager.setProgramFavorite(program.id, serverState) }
                else -> return@launch
            }
            programs = visibleRows()
        }
    }

    private fun visibleRows(): List<ProgramList> {
        val rows = DatabaseManager.allPrograms()
        return if (mineSelected) ProgramStore.own(rows) else ProgramStore.templates(rows)
    }
}
