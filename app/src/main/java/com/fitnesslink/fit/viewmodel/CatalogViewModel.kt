package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.catalog.CatalogSnapshot
import com.fitnesslink.fit.catalog.CatalogStore
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.model.CatalogItem
import com.fitnesslink.fit.movements.MovementStore
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Port of iOS `CatalogViewModel`: seeds the rails from the local cache so
 * the tab is never blank, then runs [CatalogStore.refresh] and re-reads.
 * [isLoading] only drives the first-load spinner (shown while the cache is
 * empty and the refresh is in flight).
 */
class CatalogViewModel : ViewModel() {
    var snapshot by mutableStateOf(CatalogSnapshot())
        private set
    var isLoading by mutableStateOf(false)
        private set

    val programs: List<CatalogItem> get() = snapshot.programs
    val workouts: List<CatalogItem> get() = snapshot.workouts
    val movements: List<CatalogItem> get() = snapshot.movements
    /** Combined user-authored content (`contributorId == null`), newest first. */
    val myContent: List<CatalogItem> get() = snapshot.myContent

    val isEmpty: Boolean get() = snapshot.isEmpty

    init {
        viewModelScope.launch { runCatching { CatalogStore.changes.collect { reloadFromCache() } } }
        viewModelScope.launch { runCatching { MovementStore.changes.collect { reloadFromCache() } } }
    }

    /** Called on every appear (iOS `.task` + `.onAppear`): cache first, then a server refresh. */
    fun loadData() {
        isLoading = true
        reloadFromCache()
        viewModelScope.launch {
            // Movement captions need the taxonomy names.
            runCatching { ClassificationStore.refresh() }
            CatalogStore.refresh()
            reloadFromCache()
            isLoading = false
        }
    }

    /** Cheap local re-read — e.g. after a delete on a detail screen. */
    fun reloadFromCache() {
        snapshot = CatalogStore.snapshot()
    }
}
