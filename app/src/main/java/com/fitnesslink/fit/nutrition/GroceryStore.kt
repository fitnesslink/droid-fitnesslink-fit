package com.fitnesslink.fit.nutrition

import android.util.Log
import com.fitnesslink.fit.model.GroceryCategory
import com.fitnesslink.fit.model.GroceryItem
import com.fitnesslink.fit.model.GroceryList
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.AnalyzeGroceryItemRequest
import com.fitnesslink.fit.network.dto.CreateGroceryItemRequest
import com.fitnesslink.fit.network.dto.CreateGroceryListRequest
import com.fitnesslink.fit.network.dto.DuplicateGroceryListRequest
import com.fitnesslink.fit.network.dto.FoodSearchResult
import com.fitnesslink.fit.network.dto.GenerateGroceryListRequest
import com.fitnesslink.fit.network.dto.GroceryAnalyzeResponse
import com.fitnesslink.fit.network.dto.GroceryDuplicateMatch
import com.fitnesslink.fit.network.dto.GroceryListDto
import com.fitnesslink.fit.network.dto.GroceryStaple
import com.fitnesslink.fit.network.dto.MergeGroceryItemRequest
import com.fitnesslink.fit.network.dto.ReorderGroceryItemsRequest
import com.fitnesslink.fit.network.dto.RowVersionBody
import com.fitnesslink.fit.network.dto.UpdateGroceryItemRequest
import com.fitnesslink.fit.network.dto.UpdateGroceryListRequest
import com.fitnesslink.fit.network.dto.toGroceryItem
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.defaultGroceryList
import com.fitnesslink.fit.persistence.groceryListById
import com.fitnesslink.fit.persistence.reorderGroceryItemsLocal
import com.fitnesslink.fit.persistence.replaceGroceryItemsForList
import com.fitnesslink.fit.persistence.replaceGroceryLists
import com.fitnesslink.fit.persistence.setGroceryItemChecked
import com.fitnesslink.fit.sync.SyncDomain
import com.fitnesslink.fit.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.Date
import java.util.Locale

/**
 * Grocery lists + items — port of iOS `GroceryListsViewModel` /
 * `GroceryListDetailViewModel` persistence and `SyncManager.drainGroceryItems`.
 *
 * Lists (containers) are online-first management metadata: every mutation
 * refetches the index so `rowVersion` / `itemCount` stay fresh, and a 409
 * refetches and replays once with the fresh token before surfacing
 * [ConflictException]. Items are offline-first: local row → durable
 * `sync_queue` row under [SyncDomain.GROCERY_ITEMS] → inline attempt; a
 * successful inline write re-pulls the list so server-assigned
 * `rowVersion` / `sortOrder` land locally. A 409 inline reloads the list
 * (server truth wins) and throws [ConflictException] so the screen can say
 * so.
 */
object GroceryStore {
    private const val TAG = "GroceryStore"

    val queueEntityType: String = SyncManager.GROCERY_ITEMS_TYPE

    private const val ACTION_CREATE = "create"
    private const val ACTION_UPDATE = "update"
    private const val ACTION_TOGGLE = "toggle"
    private const val ACTION_DELETE = "delete"

    /** "Changed on another device" — the list has already been refreshed when this is thrown. */
    class ConflictException(message: String) : Exception(message)

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    /** Fires after any local grocery change — screens re-read the cache. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    @Volatile private var registered = false

    fun ensureRegistered() {
        if (registered) return
        registered = true
        SyncManager.registerDrain(SyncDomain.GROCERY_ITEMS) { drain() }
    }

    // MARK: - Lists

    fun cachedLists(): List<GroceryList> = DatabaseManager.groceryLists()

    fun cachedList(id: String): GroceryList? = DatabaseManager.groceryListById(id)

    /** `GET /grocery/lists?includeArchived=true` → replace the cached index. */
    suspend fun refreshLists(): List<GroceryList> {
        val remote = ApiClient.nutritionApi.getGroceryLists(includeArchived = true).map { it.toGroceryList() }
        withContext(Dispatchers.IO) { DatabaseManager.replaceGroceryLists(remote) }
        notifyChanged()
        return remote
    }

    suspend fun createList(name: String): GroceryList {
        val created = ApiClient.nutritionApi.createGroceryList(CreateGroceryListRequest(name = name.trim())).toGroceryList()
        runCatching { refreshLists() }
        return created
    }

    suspend fun renameList(list: GroceryList, newName: String) = mutateList(list) { fresh ->
        ApiClient.nutritionApi.updateGroceryList(fresh.id, UpdateGroceryListRequest(rowVersion = fresh.rowVersion, name = newName.trim()))
    }

    suspend fun setArchived(list: GroceryList, archived: Boolean) = mutateList(list) { fresh ->
        ApiClient.nutritionApi.updateGroceryList(fresh.id, UpdateGroceryListRequest(rowVersion = fresh.rowVersion, isArchived = archived))
    }

    /** Server 400s on the default list — callers never offer delete for it. */
    suspend fun deleteList(list: GroceryList) {
        if (list.isDefault) return
        mutateList(list) { fresh -> ApiClient.nutritionApi.deleteGroceryList(fresh.id, RowVersionBody(fresh.rowVersion)) }
        withContext(Dispatchers.IO) { DatabaseManager.deleteGroceryList(list.id) }
        notifyChanged()
    }

    /** Items come back copied unchecked. */
    suspend fun duplicateList(list: GroceryList, name: String? = null): GroceryList {
        val copy = ApiClient.nutritionApi.duplicateGroceryList(list.id, DuplicateGroceryListRequest(name)).toGroceryList()
        runCatching { refreshLists() }
        return copy
    }

    /** Run a rowVersion-carrying list mutation; on 409 refetch the index and replay once. */
    private suspend fun mutateList(list: GroceryList, op: suspend (GroceryList) -> Unit) {
        try {
            op(list)
            runCatching { refreshLists() }
        } catch (e: ApiError.ServerError) {
            if (!e.isConflict) throw e
            val fresh = runCatching { refreshLists() }.getOrDefault(cachedLists()).firstOrNull { it.id.equals(list.id, true) }
                ?: throw ConflictException("That list was removed on another device.")
            try {
                op(fresh)
                runCatching { refreshLists() }
            } catch (_: Exception) {
                throw ConflictException("This list changed on another device. It's been refreshed — try again.")
            }
        }
    }

    // MARK: - Items

    /** Cached items of [listId], server order (sortOrder, then name). */
    fun items(listId: String): List<GroceryItem> = DatabaseManager.groceryItems(listId)

    /** `GET /grocery/lists/{id}` → cache list + items; returns the list. */
    suspend fun loadList(listId: String): GroceryList {
        val dto = ApiClient.nutritionApi.getGroceryList(listId)
        val list = dto.toGroceryList()
        val items = (dto.items ?: emptyList()).map { it.toGroceryItem().copy(groceryListId = it.groceryListId ?: list.id) }
        withContext(Dispatchers.IO) {
            DatabaseManager.upsertGroceryList(list.copy(itemCount = items.size))
            DatabaseManager.replaceGroceryItemsForList(list.id, items)
        }
        notifyChanged()
        return list
    }

    /**
     * Re-pull the default list after a meal-slot write (its generated items
     * change). `GET /grocery/me` returns the default list's rows; the list
     * id comes from the rows (or the cached index).
     */
    suspend fun refreshDefaultList() {
        if (!NetworkMonitor.isConnected.value) return
        val remote = ApiClient.nutritionApi.getGroceryItems().map { it.toGroceryItem() }
        val listId = remote.firstNotNullOfOrNull { it.groceryListId }
            ?: withContext(Dispatchers.IO) { DatabaseManager.defaultGroceryList()?.id }
            ?: run { runCatching { refreshLists() }.getOrNull()?.firstOrNull { it.isDefault }?.id }
            ?: return
        withContext(Dispatchers.IO) { DatabaseManager.replaceGroceryItemsForList(listId, remote) }
        notifyChanged()
    }

    /** Resolve the default list (cached, else from the server). */
    suspend fun defaultList(): GroceryList? =
        withContext(Dispatchers.IO) { DatabaseManager.defaultGroceryList() }
            ?: runCatching { refreshLists() }.getOrNull()?.firstOrNull { it.isDefault }

    /** Add an item (offline-first). Returns the locally-stored item. */
    suspend fun addItem(listId: String, item: GroceryItem): GroceryItem {
        ensureRegistered()
        val toCreate = item.copy(id = item.id.lowercase(Locale.US), name = item.name.trim(), groceryListId = listId, isChecked = false)
        withContext(Dispatchers.IO) { DatabaseManager.insertGroceryItem(toCreate) }
        notifyChanged()
        commitItem(ACTION_CREATE, toCreate) { ApiClient.nutritionApi.addGroceryItem(toCreate.toCreateRequest()) }
        return toCreate
    }

    /** Add a food-database hit as a grocery line (serving size → quantity/unit when known). */
    suspend fun addFromSearch(listId: String, result: FoodSearchResult): GroceryItem {
        val grams = result.servingSizeGrams
        return addItem(
            listId,
            GroceryItem(
                name = result.name,
                quantity = grams?.let { formatQuantity(it) } ?: "",
                unit = if (grams != null) (result.servingUnit ?: "g") else "",
                category = GroceryCategory.OTHER
            )
        )
    }

    suspend fun updateItem(item: GroceryItem) {
        ensureRegistered()
        val updated = item.copy(name = item.name.trim())
        withContext(Dispatchers.IO) { DatabaseManager.insertGroceryItem(updated) }
        notifyChanged()
        commitItem(ACTION_UPDATE, updated) { ApiClient.nutritionApi.updateGroceryItem(updated.id, updated.toUpdateRequest(freshRowVersion(updated))) }
    }

    suspend fun toggleItem(item: GroceryItem) {
        ensureRegistered()
        val flipped = item.copy(isChecked = !item.isChecked)
        withContext(Dispatchers.IO) { DatabaseManager.setGroceryItemChecked(item.id, flipped.isChecked) }
        notifyChanged()
        commitItem(ACTION_TOGGLE, flipped) { ApiClient.nutritionApi.toggleGroceryItem(flipped.id, RowVersionBody(freshRowVersion(flipped))) }
    }

    suspend fun deleteItem(item: GroceryItem) {
        ensureRegistered()
        withContext(Dispatchers.IO) { DatabaseManager.deleteGroceryItem(item.id) }
        notifyChanged()
        commitItem(ACTION_DELETE, item) { ApiClient.nutritionApi.deleteGroceryItem(item.id, RowVersionBody(freshRowVersion(item))) }
    }

    /** Persist a drag reorder: local sortOrder rewrite, then `PUT /reorder` (online; on failure re-pull). */
    suspend fun reorder(listId: String, orderedIds: List<String>) {
        withContext(Dispatchers.IO) { DatabaseManager.reorderGroceryItemsLocal(orderedIds) }
        notifyChanged()
        if (!NetworkMonitor.isConnected.value) return
        try {
            ApiClient.nutritionApi.reorderGroceryItems(listId, ReorderGroceryItemsRequest(orderedIds))
        } catch (e: Exception) {
            runCatching { loadList(listId) }
            throw e
        }
    }

    // MARK: - Smart grocery (FTNSSLNK-220)

    /** READ-ONLY dry run: category suggestion + duplicate match. */
    suspend fun analyze(listId: String?, name: String, quantity: Double?, unit: String?): GroceryAnalyzeResponse =
        ApiClient.nutritionApi.analyzeGroceryItem(AnalyzeGroceryItemRequest(name = name.trim(), quantity = quantity, unit = unit?.trim()?.ifEmpty { null }, groceryListId = listId))

    /** Fold the incoming amount into [duplicate] (also unchecks it). */
    suspend fun merge(listId: String, duplicate: GroceryDuplicateMatch, quantity: Double?, unit: String?): GroceryItem {
        try {
            val merged = ApiClient.nutritionApi.mergeGroceryItem(duplicate.itemId, MergeGroceryItemRequest(duplicate.rowVersion, quantity, unit?.trim()?.ifEmpty { null })).toGroceryItem()
            runCatching { loadList(listId) }
            return merged
        } catch (e: ApiError.ServerError) {
            if (e.isConflict) {
                runCatching { loadList(listId) }
                throw ConflictException("That item changed on another device. The list has been refreshed — try adding again.")
            }
            throw e
        }
    }

    suspend fun staples(listId: String?): List<GroceryStaple> = ApiClient.nutritionApi.getGroceryStaples(listId)

    /** `POST /grocery/generate`: every itemized planned food in `[from, to]` → a NEW list (400 = nothing planned). */
    suspend fun generate(from: String, to: String, listName: String?): GroceryList {
        val dto = ApiClient.nutritionApi.generateGroceryList(GenerateGroceryListRequest(from, to, listName?.trim()?.ifEmpty { null }))
        val list = dto.toGroceryList()
        val items = (dto.items ?: emptyList()).map { it.toGroceryItem().copy(groceryListId = it.groceryListId ?: list.id) }
        withContext(Dispatchers.IO) {
            DatabaseManager.upsertGroceryList(list.copy(itemCount = items.size))
            DatabaseManager.replaceGroceryItemsForList(list.id, items)
        }
        runCatching { refreshLists() }
        notifyChanged()
        return list
    }

    // MARK: - Queue

    /**
     * Enqueue + awaited inline attempt. Success → re-pull the list (canonical
     * rows). 409 → re-pull, throw [ConflictException]. Other final 4xx → drop
     * the row, re-pull, rethrow. Transient / offline → leave queued, return.
     */
    private suspend fun commitItem(action: String, item: GroceryItem, call: suspend () -> Unit) {
        val queueId = DatabaseManager.enqueueSyncEntry(queueEntityType, item.id, action, ApiClient.gson.toJson(item))
        if (!NetworkMonitor.isConnected.value) return
        val listId = item.groceryListId
        try {
            call()
            DatabaseManager.markSyncEntrySynced(queueId)
            if (listId != null) runCatching { loadList(listId) }
        } catch (e: Exception) {
            if (!isFinalFailure(e)) {
                Log.i(TAG, "$action ${item.id}: transient ${e.javaClass.simpleName} — left queued")
                return
            }
            DatabaseManager.markSyncEntrySynced(queueId)
            if (listId != null) runCatching { loadList(listId) }
            if (e is ApiError.ServerError && e.isConflict) {
                throw ConflictException("That item changed on another device. The list has been refreshed — try again.")
            }
            throw e
        }
    }

    internal fun isFinalFailure(e: Throwable): Boolean = when (e) {
        is HttpException -> e.code() in 400..499 && e.code() != 401
        is ApiError.Forbidden, is ApiError.NotFound -> true
        is ApiError.ServerError -> e.statusCode in 400..499 && e.statusCode != 401
        else -> false
    }

    /**
     * The token to send with a mutating call. An item created offline has
     * none yet — read it back from its list (404 → `ApiError.NotFound`,
     * final, row dropped).
     */
    private suspend fun freshRowVersion(item: GroceryItem): String {
        if (item.rowVersion.isNotBlank()) return item.rowVersion
        val rows = if (item.groceryListId != null) ApiClient.nutritionApi.getGroceryList(item.groceryListId).items ?: emptyList()
        else ApiClient.nutritionApi.getGroceryItems()
        return rows.firstOrNull { it.id.equals(item.id, true) }?.rowVersion ?: throw ApiError.NotFound
    }

    /** Replay queued item writes (iOS `SyncManager.drainGroceryItems`). */
    suspend fun drain() {
        SyncManager.drainQueue(SyncDomain.GROCERY_ITEMS) { entry ->
            val item = try { ApiClient.gson.fromJson(entry.payload, GroceryItem::class.java) } catch (_: Exception) { null }
                ?: return@drainQueue
            when (entry.action) {
                ACTION_CREATE -> ApiClient.nutritionApi.addGroceryItem(item.toCreateRequest())
                ACTION_UPDATE -> ApiClient.nutritionApi.updateGroceryItem(item.id, item.toUpdateRequest(freshRowVersion(item)))
                ACTION_TOGGLE -> ApiClient.nutritionApi.toggleGroceryItem(item.id, RowVersionBody(freshRowVersion(item)))
                ACTION_DELETE -> ApiClient.nutritionApi.deleteGroceryItem(item.id, RowVersionBody(freshRowVersion(item)))
                else -> Log.w(TAG, "unknown grocery queue action '${entry.action}' — dropping")
            }
        }
    }

    private fun notifyChanged() { _changes.tryEmit(Unit) }

    // MARK: - Wire mapping

    private fun GroceryItem.toCreateRequest() = CreateGroceryItemRequest(
        id = id, groceryListId = groceryListId, name = name,
        quantity = quantity.trim().toDoubleOrNull(), unit = unit.trim().ifEmpty { null }, category = category.wireValue
    )

    private fun GroceryItem.toUpdateRequest(rowVersion: String) = UpdateGroceryItemRequest(
        rowVersion = rowVersion, name = name, quantity = quantity.trim().toDoubleOrNull(),
        unit = unit.trim().ifEmpty { null }, category = category.wireValue
    )

    internal fun GroceryListDto.toGroceryList(): GroceryList = GroceryList(
        id = id.lowercase(Locale.US), name = name ?: "", isArchived = isArchived ?: false, isDefault = isDefault ?: false,
        itemCount = itemCount ?: items?.size ?: 0, createdAt = createdAtUtc ?: Date(), updatedAt = updatedAtUtc, rowVersion = rowVersion ?: ""
    )

    fun formatQuantity(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}

// MARK: - Pure analyze / merge decision rules (unit-tested)

/**
 * What the add form does with a `POST /grocery/analyze` response (iOS
 * `GroceryItemFormSheet.attemptSave` / `visibleSuggestion`).
 */
object GroceryAnalyzeRules {
    sealed class AddDecision {
        /** Plain create. */
        data object AddSeparately : AddDecision()
        /** Ask: combine into [duplicate] or add separately. */
        data class PromptMerge(val duplicate: GroceryDuplicateMatch) : AddDecision()
    }

    /** Only a NEW item with a duplicate (and a merge handler) prompts; edits and misses just save. */
    fun decide(isNew: Boolean, analysis: GroceryAnalyzeResponse?, canMerge: Boolean): AddDecision {
        if (!isNew || !canMerge) return AddDecision.AddSeparately
        val duplicate = analysis?.duplicate ?: return AddDecision.AddSeparately
        return AddDecision.PromptMerge(duplicate)
    }

    /**
     * The category chip to show: the server's suggestion mapped onto the
     * app's set, when the analysis is for the name being typed, hasn't been
     * dismissed for that name, and differs from the current pick.
     */
    fun visibleSuggestion(
        isNew: Boolean,
        analysis: GroceryAnalyzeResponse?,
        analyzedName: String,
        currentName: String,
        dismissedForName: String?,
        currentCategory: GroceryCategory
    ): GroceryCategory? {
        if (!isNew) return null
        val suggested = analysis?.suggestedCategory?.let { GroceryCategory.fromBackendName(it) } ?: return null
        if (analyzedName != currentName || dismissedForName == currentName || suggested == currentCategory) return null
        return suggested
    }

    /** "Combine into 3 lb" when the server could sum the quantities, else plain "Combine". */
    fun combineLabel(duplicate: GroceryDuplicateMatch, typedUnit: String): String {
        val proposed = duplicate.proposedQuantity
        if (!duplicate.quantitySummable || proposed == null) return "Combine"
        val qty = GroceryStore.formatQuantity(proposed)
        val unit = (duplicate.unit ?: typedUnit).trim()
        return if (unit.isEmpty()) "Combine into $qty" else "Combine into $qty $unit"
    }

    /** "usually 2 lb · on 4 lists" subtitle for a staple row. */
    fun stapleSubtitle(staple: GroceryStaple): String {
        val parts = mutableListOf<String>()
        staple.lastQuantity?.let { qty ->
            val label = GroceryStore.formatQuantity(qty)
            parts += "usually " + (staple.lastUnit?.takeIf { it.isNotEmpty() }?.let { "$label $it" } ?: label)
        }
        parts += "on ${staple.listCount} list${if (staple.listCount == 1) "" else "s"}"
        return parts.joinToString(" · ")
    }
}
