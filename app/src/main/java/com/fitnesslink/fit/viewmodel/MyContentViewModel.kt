package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.catalog.CatalogStore
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.model.CatalogItem
import com.fitnesslink.fit.movements.MovementStore
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Port of iOS `MyContentViewModel`: the "See All" feed of user-authored
 * workouts, programs and movements (`contributorId == null`) in one
 * newest-first list, with client-side kind / favorites / equipment /
 * anatomy filters. Everything here is the user's own, so there is no
 * contributor filter.
 */
class MyContentViewModel : ViewModel() {
    var items by mutableStateOf<List<CatalogItem>>(emptyList())
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    /** Multi-select OR semantics like the tag strips; empty = every kind. */
    var selectedKinds by mutableStateOf<Set<CatalogItem.Kind>>(emptySet())
    var showFavoritesOnly by mutableStateOf(false)
    var selectedEquipmentIds by mutableStateOf<Set<String>>(emptySet())
    var selectedAnatomyIds by mutableStateOf<Set<String>>(emptySet())

    val hasActiveFilters: Boolean
        get() = selectedKinds.isNotEmpty() || showFavoritesOnly || selectedEquipmentIds.isNotEmpty() || selectedAnatomyIds.isNotEmpty()

    /** Chip strips stay hidden until at least one item carries tags (offline: workout / program tags arrive online only). */
    val anyItemHasEquipment: Boolean get() = items.any { it.equipmentIds.isNotEmpty() }
    val anyItemHasAnatomy: Boolean get() = items.any { it.anatomyIds.isNotEmpty() }

    init {
        viewModelScope.launch { runCatching { CatalogStore.changes.collect { readLocal() } } }
        viewModelScope.launch { runCatching { MovementStore.changes.collect { readLocal() } } }
    }

    fun clearFilters() {
        selectedKinds = emptySet()
        showFavoritesOnly = false
        selectedEquipmentIds = emptySet()
        selectedAnatomyIds = emptySet()
    }

    fun toggleKind(kind: CatalogItem.Kind) {
        selectedKinds = if (kind in selectedKinds) selectedKinds - kind else selectedKinds + kind
    }

    fun toggleEquipment(id: String) { selectedEquipmentIds = selectedEquipmentIds.toggled(id) }
    fun toggleAnatomy(id: String) { selectedAnatomyIds = selectedAnatomyIds.toggled(id) }

    /** Called on every appear so content created in the editors shows up on pop-back. */
    fun loadData() {
        readLocal()
        viewModelScope.launch {
            isRefreshing = true
            try {
                runCatching { ClassificationStore.refresh() }
                CatalogStore.refresh()
                readLocal()
            } finally {
                isRefreshing = false
            }
        }
    }

    private fun readLocal() {
        items = CatalogStore.snapshot().myContent
    }

    /** Apply the chips, favorites and the search text (iOS `MyContentView.filtered`). */
    fun filtered(searchText: String): List<CatalogItem> {
        var result = items
        if (selectedKinds.isNotEmpty()) result = result.filter { it.kind in selectedKinds }
        if (showFavoritesOnly) result = result.filter { it.isFavorite }
        if (selectedEquipmentIds.isNotEmpty()) {
            val wanted = selectedEquipmentIds.map { it.lowercase(Locale.US) }.toSet()
            result = result.filter { item -> item.equipmentIds.any { it in wanted } }
        }
        if (selectedAnatomyIds.isNotEmpty()) {
            val wanted = selectedAnatomyIds.map { it.lowercase(Locale.US) }.toSet()
            result = result.filter { item -> item.anatomyIds.any { it in wanted } }
        }
        val q = searchText.trim().lowercase(Locale.US)
        if (q.isNotEmpty()) result = result.filter { it.title.lowercase(Locale.US).contains(q) }
        return result
    }

    private fun Set<String>.toggled(id: String): Set<String> =
        if (any { it.equals(id, ignoreCase = true) }) filterNot { it.equals(id, ignoreCase = true) }.toSet() else this + id
}
