package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.contributors.ContributorStore
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.movements.MovementStore
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.templateMovements
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Port of iOS `MovementsViewModel` + the client-side filtering `MovementsView`
 * applies. The catalog shows template movements only (`contributorId != null`,
 * iOS `CatalogViewModel.templateMovements`, FTNSSLNK-343) in the server's
 * ranking (`sortIndex`, FTNSSLNK-334). Filters run against cached rows so the
 * screen works offline: favorites-only, contributor, equipment / anatomy (OR
 * within a group, AND across), and a name + tag-name search.
 */
class MovementsViewModel : ViewModel() {
    var movements by mutableStateOf<List<MovementLibraryItem>>(emptyList())
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    var searchText by mutableStateOf("")
    var showFavoritesOnly by mutableStateOf(false)
    var selectedContributorId by mutableStateOf<String?>(null)
    var selectedEquipmentIds by mutableStateOf<Set<String>>(emptySet())
    var selectedAnatomyIds by mutableStateOf<Set<String>>(emptySet())

    val hasActiveFilters: Boolean
        get() = showFavoritesOnly || selectedContributorId != null ||
            selectedEquipmentIds.isNotEmpty() || selectedAnatomyIds.isNotEmpty()

    /** Equipment + anatomy picked anywhere (chip strip or drawer) — the "More filters" badge. */
    val moreFiltersCount: Int get() = selectedEquipmentIds.size + selectedAnatomyIds.size

    /** Contributor ids present in the current catalog slice (scopes the contributor menu). */
    val presentContributorIds: Set<String>
        get() = movements.mapNotNullTo(HashSet()) { it.contributorId?.lowercase(Locale.US) }

    val filtered: List<MovementLibraryItem>
        get() {
            var items = movements
            if (showFavoritesOnly) items = items.filter { it.isFavorite }
            selectedContributorId?.let { cid -> items = items.filter { it.contributorId.equals(cid, ignoreCase = true) } }
            if (selectedEquipmentIds.isNotEmpty()) {
                val wanted = selectedEquipmentIds.mapTo(HashSet()) { it.lowercase(Locale.US) }
                items = items.filter { m -> m.equipmentIds.any { it.lowercase(Locale.US) in wanted } }
            }
            if (selectedAnatomyIds.isNotEmpty()) {
                val wanted = selectedAnatomyIds.mapTo(HashSet()) { it.lowercase(Locale.US) }
                items = items.filter { m -> m.anatomyIds.any { it.lowercase(Locale.US) in wanted } }
            }
            val q = searchText.trim().lowercase(Locale.US)
            if (q.isEmpty()) return items
            return items.filter { m ->
                m.name.lowercase(Locale.US).contains(q) ||
                    m.anatomyIds.any { ClassificationStore.anatomyName(it)?.lowercase(Locale.US)?.contains(q) == true } ||
                    m.equipmentIds.any { ClassificationStore.equipmentName(it)?.lowercase(Locale.US)?.contains(q) == true }
            }
        }

    private var loaded = false

    init {
        MovementStore.ensureRegistered()
        viewModelScope.launch { MovementStore.changes.collectLatest { hydrate() } }
    }

    fun loadData() {
        hydrate()
        if (loaded) return
        loaded = true
        viewModelScope.launch { runCatching { ClassificationStore.refresh() } }
        viewModelScope.launch { runCatching { ContributorStore.refresh() } }
        viewModelScope.launch { refreshFromServer() }
    }

    fun refresh() {
        viewModelScope.launch { refreshFromServer() }
    }

    fun toggleEquipment(id: String) {
        selectedEquipmentIds = selectedEquipmentIds.toggled(id)
    }

    fun toggleAnatomy(id: String) {
        selectedAnatomyIds = selectedAnatomyIds.toggled(id)
    }

    fun applyMoreFilters(anatomyIds: Set<String>, equipmentIds: Set<String>) {
        selectedAnatomyIds = anatomyIds
        selectedEquipmentIds = equipmentIds
    }

    fun clearFilters() {
        showFavoritesOnly = false
        selectedContributorId = null
        selectedEquipmentIds = emptySet()
        selectedAnatomyIds = emptySet()
    }

    /** Optimistic heart (movement-specific endpoints, mirrors the browser). */
    fun toggleFavorite(movementId: String) {
        if (!AuthManager.isAuthenticated.value) return
        val was = movements.firstOrNull { it.id.equals(movementId, ignoreCase = true) }?.isFavorite ?: false
        MovementStore.toggleFavorite(movementId, was)
        hydrate()
    }

    private fun hydrate() {
        movements = runCatching { DatabaseManager.templateMovements() }.getOrDefault(emptyList())
    }

    private suspend fun refreshFromServer() {
        isRefreshing = true
        try {
            MovementStore.refreshCatalog()
            hydrate()
        } finally {
            isRefreshing = false
        }
    }

    private fun Set<String>.toggled(id: String): Set<String> =
        if (any { it.equals(id, ignoreCase = true) }) filterNot { it.equals(id, ignoreCase = true) }.toSet() else this + id
}
