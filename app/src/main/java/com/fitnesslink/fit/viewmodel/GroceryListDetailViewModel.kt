package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.GroceryCategory
import com.fitnesslink.fit.model.GroceryItem
import com.fitnesslink.fit.model.GroceryList
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.FoodSearchResult
import com.fitnesslink.fit.network.dto.GroceryDuplicateMatch
import com.fitnesslink.fit.network.dto.GroceryStaple
import com.fitnesslink.fit.nutrition.GroceryStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Drives one grocery list (port of iOS `GroceryListDetailViewModel`):
 * category groups of unchecked items, an "In Cart" section, progress,
 * adds (manual / food database / staples), tap-to-edit, delete, drag
 * reorder and the smart analyze → merge flow. All persistence is in
 * [GroceryStore]; the screen only holds presentation state.
 */
class GroceryListDetailViewModel : ViewModel() {
    var listId by mutableStateOf<String?>(null)
        private set
    var list by mutableStateOf<GroceryList?>(null)
        private set
    var items by mutableStateOf<List<GroceryItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
    /** Offline with only the cached rows — the list is read-only. */
    var isShowingOfflineCache by mutableStateOf(false)
        private set
    var staples by mutableStateOf<List<GroceryStaple>>(emptyList())
        private set
    private var dismissedStaples by mutableStateOf<Set<String>>(emptySet())

    private var hasLoadedOnce = false
    private var observing = false

    // MARK: - Derived

    val uncheckedItems: List<GroceryItem> get() = items.filter { !it.isChecked }
    val checkedItems: List<GroceryItem> get() = items.filter { it.isChecked }

    /** Unchecked items grouped by category in enum order; empty groups dropped. */
    val groupedUnchecked: List<Pair<GroceryCategory, List<GroceryItem>>>
        get() {
            val grouped = uncheckedItems.groupBy { it.category }
            return GroceryCategory.entries.mapNotNull { cat -> grouped[cat]?.takeIf { it.isNotEmpty() }?.let { cat to it } }
        }

    val visibleStaples: List<GroceryStaple>
        get() {
            if (isShowingOfflineCache) return emptyList()
            val existing = items.map { it.name.lowercase(Locale.US) }.toSet()
            return staples.filter { it.name.lowercase(Locale.US) !in dismissedStaples && it.name.lowercase(Locale.US) !in existing }
        }

    // MARK: - Load

    /** Bind to [id] and load (cache first, then server). */
    fun bind(id: String) {
        if (listId == id) return
        listId = id
        hasLoadedOnce = false
        list = GroceryStore.cachedList(id)
        items = GroceryStore.items(id)
        observe()
    }

    private fun observe() {
        if (observing) return
        observing = true
        viewModelScope.launch {
            GroceryStore.changes.collectLatest {
                val id = listId ?: return@collectLatest
                list = GroceryStore.cachedList(id) ?: list
                items = GroceryStore.items(id)
            }
        }
    }

    suspend fun load() {
        val id = listId ?: return
        if (!hasLoadedOnce) isLoading = true
        try {
            if (!NetworkMonitor.isConnected.value) throw ApiError.Offline
            list = GroceryStore.loadList(id)
            items = GroceryStore.items(id)
            hasLoadedOnce = true
            isShowingOfflineCache = false
            errorMessage = null
        } catch (e: Exception) {
            if (!hasLoadedOnce) {
                val cached = GroceryStore.items(id)
                if (e is ApiError.Offline || e is ApiError.NetworkError) {
                    items = cached
                    isShowingOfflineCache = true
                } else if (cached.isEmpty()) {
                    errorMessage = GroceryListsViewModel.describe(e)
                }
            }
        } finally {
            isLoading = false
        }
    }

    suspend fun loadStaples() {
        val id = listId ?: return
        staples = runCatching { GroceryStore.staples(id) }.getOrDefault(emptyList())
    }

    // MARK: - Items

    suspend fun addItem(item: GroceryItem) = mutate { GroceryStore.addItem(listId ?: return@mutate, item) }

    suspend fun addFromSearch(result: FoodSearchResult) = mutate { GroceryStore.addFromSearch(listId ?: return@mutate, result) }

    suspend fun addStaple(staple: GroceryStaple) {
        val item = GroceryItem(
            name = staple.name,
            quantity = staple.lastQuantity?.let { GroceryStore.formatQuantity(it) } ?: "",
            unit = staple.lastUnit ?: "",
            category = staple.suggestedCategory?.let { GroceryCategory.fromBackendName(it) } ?: GroceryCategory.OTHER
        )
        addItem(item)
    }

    fun dismissStaple(staple: GroceryStaple) { dismissedStaples = dismissedStaples + staple.name.lowercase(Locale.US) }

    suspend fun update(item: GroceryItem) = mutate { GroceryStore.updateItem(item) }

    suspend fun toggle(item: GroceryItem) = mutate { GroceryStore.toggleItem(item) }

    suspend fun delete(item: GroceryItem) = mutate { GroceryStore.deleteItem(item) }

    suspend fun merge(duplicate: GroceryDuplicateMatch, quantity: Double?, unit: String?) =
        mutate { GroceryStore.merge(listId ?: return@mutate, duplicate, quantity, unit) }

    /**
     * Move an unchecked item inside its category group. Rebuilds the flat
     * ordering (every unchecked group in display order, then checked items)
     * and persists it via `/reorder`.
     */
    suspend fun moveUnchecked(category: GroceryCategory, from: Int, to: Int) {
        val groups = groupedUnchecked
        val group = groups.firstOrNull { it.first == category }?.second?.toMutableList() ?: return
        if (from !in group.indices || to !in group.indices || from == to) return
        group.add(to, group.removeAt(from))
        val ordered = groups.flatMap { (cat, rows) -> if (cat == category) group else rows } + checkedItems
        items = ordered.mapIndexed { index, item -> item.copy(sortOrder = index) }
        try {
            GroceryStore.reorder(listId ?: return, ordered.map { it.id })
        } catch (e: Exception) {
            errorMessage = GroceryListsViewModel.describe(e)
        }
    }

    // MARK: - List

    suspend fun renameList(newName: String) {
        val current = list ?: return
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == current.name) return
        mutateList { GroceryStore.renameList(current, trimmed) }
    }

    suspend fun setArchived(archived: Boolean) {
        val current = list ?: return
        mutateList { GroceryStore.setArchived(current, archived) }
    }

    /** True when the list is gone (caller pops). */
    suspend fun deleteList(): Boolean {
        val current = list ?: return false
        if (current.isDefault) return false
        return try {
            GroceryStore.deleteList(current); true
        } catch (e: Exception) {
            errorMessage = GroceryListsViewModel.describe(e); false
        }
    }

    // MARK: - Helpers

    private suspend fun mutate(op: suspend () -> Unit) {
        try {
            op()
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = GroceryListsViewModel.describe(e)
        }
        listId?.let { items = GroceryStore.items(it) }
    }

    private suspend fun mutateList(op: suspend () -> Unit) {
        try {
            op()
            listId?.let { list = GroceryStore.cachedList(it) ?: list }
        } catch (e: Exception) {
            errorMessage = GroceryListsViewModel.describe(e)
        }
    }

    /** Plain-text export for the share sheet (kept from the pre-Wave-3 screen). */
    fun shareText(): String = buildString {
        appendLine(list?.name ?: "Grocery List")
        appendLine()
        groupedUnchecked.forEach { (cat, rows) ->
            appendLine(cat.displayName)
            rows.forEach { appendLine("  [ ] ${it.name}${amountSuffix(it)}") }
            appendLine()
        }
        if (checkedItems.isNotEmpty()) {
            appendLine("In Cart")
            checkedItems.forEach { appendLine("  [x] ${it.name}${amountSuffix(it)}") }
        }
    }.trimEnd()

    private fun amountSuffix(item: GroceryItem): String {
        val label = "${item.quantity} ${item.unit}".trim()
        return if (label.isEmpty()) "" else " — $label"
    }
}
