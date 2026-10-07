package com.fitnesslink.fit.nutrition

import android.util.Log
import com.fitnesslink.fit.model.CopyMealSlotDayMode
import com.fitnesslink.fit.model.MealPlanCopyRules
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.MealSlot
import com.fitnesslink.fit.model.MealSlotDayRollup
import com.fitnesslink.fit.model.MealSlotFood
import com.fitnesslink.fit.model.MealSlotWeek
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.ApplyMealPlanWeekMeal
import com.fitnesslink.fit.network.dto.ApplyMealPlanWeekMode
import com.fitnesslink.fit.network.dto.ApplyMealPlanWeekRequest
import com.fitnesslink.fit.network.dto.ApplyMealPlanWeekResult
import com.fitnesslink.fit.network.dto.CopyMealSlotDayRequest
import com.fitnesslink.fit.network.dto.CopyMealSlotDayResult
import com.fitnesslink.fit.network.dto.CopyMealSlotWeekRequest
import com.fitnesslink.fit.network.dto.CopyMealSlotWeekResult
import com.fitnesslink.fit.network.dto.CreateMealSlotFoodRequest
import com.fitnesslink.fit.network.dto.CreateMealSlotRequest
import com.fitnesslink.fit.network.dto.MealSlotDetailDto
import com.fitnesslink.fit.network.dto.MealSlotFoodDto
import com.fitnesslink.fit.network.dto.MealSlotIngredientDto
import com.fitnesslink.fit.network.dto.MealSlotWeekDto
import com.fitnesslink.fit.network.dto.ReplaceMealSlotFoodsRequest
import com.fitnesslink.fit.network.dto.RowVersionBody
import com.fitnesslink.fit.network.dto.UpdateMealSlotRequest
import com.fitnesslink.fit.network.dto.toGroceryItem
import com.fitnesslink.fit.network.dto.toMealSlot
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.deleteMealSlotLocal
import com.fitnesslink.fit.persistence.mealSlotWithFoods
import com.fitnesslink.fit.persistence.mealSlotsWithFoods
import com.fitnesslink.fit.persistence.reconcileMealSlots
import com.fitnesslink.fit.persistence.saveMealSlotFoods
import com.fitnesslink.fit.persistence.saveMealSlotWithFoods
import com.fitnesslink.fit.sync.SyncDomain
import com.fitnesslink.fit.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Meal-plan write pipeline + week cache — port of iOS `MealPlanViewModel`'s
 * persistence half (`commitBuiltMeal` / `removeSlot` / `refreshFromServer`
 * / copy-week / copy-day / apply-week) and `SyncManager.drainMealSlots`.
 *
 * Slots are DATE-anchored (`yyyy-MM-dd`, Sunday-start weeks). Every
 * mutation is: local row (+ cached itemized foods) first → durable
 * `sync_queue` row under [SyncDomain.MEAL_SLOTS] → inline attempt. A week
 * read drains the queue first and skips the remote read while a slot write
 * is still queued, so a stale server list can never clobber a pending
 * local change (iOS `refreshFromServer`).
 *
 * Queue actions: `create` (POST with foods — the server recomputes flat
 * totals), `foods` (PUT `{id}/foods`, re-reading the fresh rowVersion each
 * attempt), `update` (PUT `{id}`), `delete` (DELETE with rowVersion body).
 * The payload is always the full [MealSlot] JSON.
 */
object MealPlanStore {
    private const val TAG = "MealPlanStore"

    /** Queue tag (iOS `SyncManager.mealSlotsType`). Paired producer ↔ drain. */
    val queueEntityType: String = SyncManager.MEAL_SLOTS_TYPE

    private const val ACTION_CREATE = "create"
    private const val ACTION_UPDATE = "update"
    private const val ACTION_FOODS = "foods"
    private const val ACTION_DELETE = "delete"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    /** Fires after any local slot change (write / delete / refresh) — screens re-read the cache. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    /** Server per-day planned-nutrition rollups keyed by week-start `yyyy-MM-dd`. */
    private val rollupsByWeek = ConcurrentHashMap<String, List<MealSlotDayRollup>>()

    /**
     * Consume-once handoff: the Ideas review screen stores its apply result
     * here and the planner picks it up as a banner on its next hydrate.
     */
    @Volatile var pendingIdeasResult: ApplyMealPlanWeekResult? = null

    @Volatile private var registered = false

    /** Register the queue drain once per process. Safe to call repeatedly. */
    fun ensureRegistered() {
        if (registered) return
        registered = true
        SyncManager.registerDrain(SyncDomain.MEAL_SLOTS) { drain() }
    }

    // MARK: - Reads (local cache)

    /** Every planned meal on [date] (`yyyy-MM-dd`), itemized foods attached. */
    fun slots(date: String): List<MealSlot> {
        val day = MealPlanDay.normalize(date)
        return DatabaseManager.mealSlotsWithFoods(day, day)
    }

    /** The Sunday-start week beginning at [sundayDate] (any date in the week is accepted). */
    fun week(sundayDate: String): List<MealSlot> {
        val (from, to) = weekBounds(sundayDate)
        return DatabaseManager.mealSlotsWithFoods(from, to)
    }

    fun slot(id: String): MealSlot? = DatabaseManager.mealSlotWithFoods(id)

    /** Whether the week before the one holding [sundayDate] has anything to copy. */
    fun previousWeekHasSlots(sundayDate: String): Boolean {
        val start = MealPlanDay.weekStart(MealPlanDay.parse(sundayDate))
        return week(MealPlanDay.string(MealPlanDay.addDays(start, -7))).isNotEmpty()
    }

    /** The server's rollups for the week holding [sundayDate] from the last successful fetch (empty before). */
    fun rollups(sundayDate: String): List<MealSlotDayRollup> =
        rollupsByWeek[weekBounds(sundayDate).first] ?: emptyList()

    /** `(from, to)` = Sunday … Saturday of the week holding [anyDate]. */
    fun weekBounds(anyDate: String): Pair<String, String> {
        val start = MealPlanDay.weekStart(MealPlanDay.parse(anyDate))
        return MealPlanDay.string(start) to MealPlanDay.string(MealPlanDay.addDays(start, 6))
    }

    // MARK: - Refresh

    /**
     * Pull the viewed week (itemized envelope + per-day rollups), reconcile
     * the local cache for that window, and prefetch the previous week
     * (upsert-only) so "copy last week" works on a first visit.
     */
    suspend fun refreshWeek(sundayDate: String) {
        ensureRegistered()
        if (!NetworkMonitor.isConnected.value) return
        runCatching { drain() }
        val (from, to) = weekBounds(sundayDate)
        try {
            val pending = withContext(Dispatchers.IO) { DatabaseManager.pendingSyncEntries(queueEntityType) }
            if (pending.isEmpty()) {
                val week = ApiClient.nutritionApi.getMealSlotWeek(from, to).toWeek(from, to)
                withContext(Dispatchers.IO) { DatabaseManager.reconcileMealSlots(from, to, week.allSlots) }
                rollupsByWeek[from] = week.days
            } else {
                Log.i(TAG, "refreshWeek $from: ${pending.size} slot write(s) still queued — keeping local cache")
            }
        } catch (e: Exception) {
            Log.i(TAG, "refreshWeek $from failed — using cache (${e.javaClass.simpleName})")
        }
        cachePreviousWeek(from)
        notifyChanged()
    }

    private suspend fun cachePreviousWeek(weekStart: String) {
        if (!NetworkMonitor.isConnected.value) return
        val start = MealPlanDay.addDays(MealPlanDay.parse(weekStart), -7)
        val from = MealPlanDay.string(start)
        val to = MealPlanDay.string(MealPlanDay.addDays(start, 6))
        runCatching {
            val remote = ApiClient.nutritionApi.getMealSlots(startDate = from, endDate = to).map { it.toMealSlot() }
            withContext(Dispatchers.IO) { remote.forEach { DatabaseManager.insertMealSlot(it) } }
        }
    }

    /** Which days in `[from, to]` already hold a planned meal (server truth; used by copy-day). */
    suspend fun occupiedDays(from: String, to: String): Set<String> =
        ApiClient.nutritionApi.getMealSlots(startDate = from, endDate = to).map { MealPlanDay.normalize(it.day ?: "") }.toSet()

    // MARK: - Writes

    /**
     * Commit a meal built in the builder (iOS `commitBuiltMeal`). A new slot
     * goes through the create-with-foods path; an existing one enqueues a
     * `foods` replace (inherently online — needs the server's rowVersion —
     * applied locally and replayed on reconnect).
     */
    suspend fun upsert(slot: MealSlot) {
        ensureRegistered()
        val normalized = slot.copy(id = slot.id.lowercase(Locale.US), date = MealPlanDay.normalize(slot.date))
        val isUpdate = withContext(Dispatchers.IO) {
            val exists = DatabaseManager.mealSlotWithFoods(normalized.id) != null
            DatabaseManager.saveMealSlotWithFoods(normalized)
            if (normalized.foods.isEmpty()) DatabaseManager.saveMealSlotFoods(normalized.id, emptyList())
            exists
        }
        notifyChanged()
        val action = if (isUpdate) ACTION_FOODS else ACTION_CREATE
        commit(action, normalized.id, ApiClient.gson.toJson(normalized)) {
            if (isUpdate) replaceFoodsOnServer(normalized) else ApiClient.nutritionApi.createMealSlot(normalized.toCreateRequest())
            runCatching { GroceryStore.refreshDefaultList() }
        }
    }

    /** Replace-set the itemized foods on [slotId]; totals are recomputed from the foods. */
    suspend fun replaceFoods(slotId: String, foods: List<MealSlotFood>) {
        val existing = withContext(Dispatchers.IO) { DatabaseManager.mealSlotWithFoods(slotId) } ?: return
        upsert(
            existing.copy(
                foods = foods,
                calories = foods.sumOf { it.totalCalories }.roundToInt(),
                protein = foods.sumOf { it.totalProtein },
                fat = foods.sumOf { it.totalFat },
                carbs = foods.sumOf { it.totalCarbs }
            )
        )
    }

    /** Remove a planned meal: local delete → queued DELETE (rowVersion body). */
    suspend fun delete(slot: MealSlot) {
        ensureRegistered()
        val id = slot.id.lowercase(Locale.US)
        withContext(Dispatchers.IO) { DatabaseManager.deleteMealSlotLocal(id) }
        notifyChanged()
        commit(ACTION_DELETE, id, ApiClient.gson.toJson(slot)) {
            deleteOnServer(slot)
            runCatching { GroceryStore.refreshDefaultList() }
        }
    }

    suspend fun delete(slotId: String) {
        val slot = withContext(Dispatchers.IO) { DatabaseManager.mealSlotWithFoods(slotId) } ?: return
        delete(slot)
    }

    // MARK: - Copy last week (FTNSSLNK-204)

    /**
     * Copy the week before the one holding [into] onto it. Online: the
     * first-class `POST /meal-slots/me/copy-week` (Sunday anchors, foods
     * copied server-side). Offline / failed: client-side replay through
     * [upsert] with the same collision rules ([MealPlanCopyRules]).
     */
    suspend fun copyWeek(into: String, overwrite: Boolean): CopyMealSlotWeekResult {
        val (targetStart, _) = weekBounds(into)
        val sourceStart = MealPlanDay.string(MealPlanDay.addDays(MealPlanDay.parse(targetStart), -7))
        if (NetworkMonitor.isConnected.value) {
            try {
                val result = ApiClient.nutritionApi.copyMealSlotWeek(CopyMealSlotWeekRequest(sourceStart, targetStart, overwrite))
                refreshWeek(targetStart)
                runCatching { GroceryStore.refreshDefaultList() }
                return result
            } catch (e: Exception) {
                Log.i(TAG, "copy-week endpoint failed (${e.javaClass.simpleName}) — replaying locally")
            }
        }
        return copyWeekLocally(sourceStart, targetStart, overwrite)
    }

    private suspend fun copyWeekLocally(sourceStart: String, targetStart: String, overwrite: Boolean): CopyMealSlotWeekResult {
        val plan = MealPlanCopyRules.planWeekCopy(week(sourceStart), week(targetStart), overwrite)
        plan.remove.forEach { delete(it) }
        plan.create.forEach { upsert(it) }
        return CopyMealSlotWeekResult(
            sourceWeekStart = sourceStart, targetWeekStart = targetStart,
            copiedCount = plan.copiedCount, skippedCount = plan.skippedCount, overwrittenCount = plan.overwrittenCount
        )
    }

    // MARK: - Copy day (FTNSSLNK-218) — online only (atomic server write)

    suspend fun copyDay(sourceDay: String, targetDays: List<String>, mode: CopyMealSlotDayMode): CopyMealSlotDayResult {
        if (!NetworkMonitor.isConnected.value) throw ApiError.Offline
        val source = MealPlanDay.normalize(sourceDay)
        val targets = targetDays.map { MealPlanDay.normalize(it) }.toSet().minus(source).sorted()
        val result = ApiClient.nutritionApi.copyMealSlotDay(CopyMealSlotDayRequest(source, targets, mode.rawValue))
        // Refresh every week we touched so the cache (and open planner) agree.
        (targets + source).map { weekBounds(it).first }.toSet().forEach { refreshWeek(it) }
        runCatching { GroceryStore.refreshDefaultList() }
        return result
    }

    // MARK: - Meal Plan Ideas approval (apply-week)

    /**
     * Write approved AI ideas onto the week via `POST /meal-slots/me/apply-week`
     * (atomic, AI-flagged slots with one itemized food each). Offline we
     * replay through [upsert] with the server's collision rules.
     */
    suspend fun applyIdeas(
        weekStart: String,
        generationId: String?,
        mode: String,
        meals: List<ApplyMealPlanWeekMeal>
    ): ApplyMealPlanWeekResult {
        val (start, _) = weekBounds(weekStart)
        if (!NetworkMonitor.isConnected.value) return applyIdeasLocally(start, mode, meals)
        val result = ApiClient.nutritionApi.applyMealPlanWeek(ApplyMealPlanWeekRequest(generationId, start, mode, meals))
        refreshWeek(start)
        runCatching { GroceryStore.refreshDefaultList() }
        return result
    }

    private suspend fun applyIdeasLocally(start: String, mode: String, meals: List<ApplyMealPlanWeekMeal>): ApplyMealPlanWeekResult {
        val ideas = meals.map { planned ->
            val mealType = MealType.fromWire(planned.mealType)
            val meal = planned.meal
            val slot = MealSlot(
                id = UUID.randomUUID().toString().lowercase(Locale.US),
                date = MealPlanDay.normalize(planned.day),
                mealType = mealType ?: MealType.BREAKFAST,
                recipeName = meal.recipeName,
                calories = meal.calories, protein = meal.protein, fat = meal.fat, carbs = meal.carbs,
                isAISuggestion = true,
                ingredients = meal.ingredients.mapNotNull { ing ->
                    val name = ing.name.trim()
                    if (name.isEmpty()) null else com.fitnesslink.fit.model.GroceryItem(
                        name = name, quantity = ing.quantity ?: "", unit = ing.unit ?: "",
                        category = com.fitnesslink.fit.model.GroceryCategory.fromWire(ing.category)
                    )
                },
                foods = listOf(
                    MealSlotFood(
                        name = meal.recipeName, servings = 1.0,
                        calories = meal.calories.toDouble(), protein = meal.protein, fat = meal.fat, carbs = meal.carbs
                    )
                )
            )
            MealPlanCopyRules.PlannedIdea(planned.day, mealType, slot)
        }
        val plan = MealPlanCopyRules.planIdeasApply(week(start), ideas, replace = mode == ApplyMealPlanWeekMode.REPLACE)
        plan.remove.forEach { delete(it) }
        plan.create.forEach { upsert(it) }
        return ApplyMealPlanWeekResult(
            weekStart = start, mode = mode,
            createdCount = plan.create.size, replacedCount = plan.remove.size, skippedCount = plan.skippedCount,
            createdSlotIds = plan.create.map { it.id }
        )
    }

    // MARK: - Server calls shared by the inline path and the drain

    private suspend fun replaceFoodsOnServer(slot: MealSlot) {
        val api = ApiClient.nutritionApi
        val current = api.getMealSlot(slot.id)
        var rowVersion = current.rowVersion ?: ""
        val ingredientItems = slot.ingredientItemsPayload()
        if ((current.recipeName ?: "") != slot.recipeName || ingredientItems != null) {
            api.updateMealSlot(slot.id, UpdateMealSlotRequest(rowVersion = rowVersion, recipeName = slot.recipeName, ingredientItems = ingredientItems))
            rowVersion = api.getMealSlot(slot.id).rowVersion ?: rowVersion
        }
        api.replaceMealSlotFoods(slot.id, ReplaceMealSlotFoodsRequest(rowVersion, slot.foods.map { it.toCreateRequest() }))
        // Adopt the fresh token so a follow-up delete / edit doesn't 409.
        runCatching { api.getMealSlot(slot.id).rowVersion }.getOrNull()?.let { fresh ->
            withContext(Dispatchers.IO) {
                DatabaseManager.mealSlotWithFoods(slot.id)?.let { DatabaseManager.insertMealSlot(it.copy(rowVersion = fresh)) }
            }
        }
    }

    private suspend fun deleteOnServer(slot: MealSlot) {
        var rowVersion = slot.rowVersion
        if (rowVersion.isBlank()) {
            // A slot created offline (create still queued ahead of this delete)
            // has no token yet — fetch it so the delete can land.
            rowVersion = ApiClient.nutritionApi.getMealSlot(slot.id).rowVersion ?: ""
        }
        ApiClient.nutritionApi.deleteMealSlot(slot.id, RowVersionBody(rowVersion))
    }

    // MARK: - Queue

    private fun commit(action: String, entityId: String, payload: String, call: suspend () -> Unit) {
        val queueId = DatabaseManager.enqueueSyncEntry(queueEntityType, entityId, action, payload)
        if (!NetworkMonitor.isConnected.value) return
        scope.launch {
            try {
                call()
                DatabaseManager.markSyncEntrySynced(queueId)
                notifyChanged()
            } catch (e: Exception) {
                if (isFinalFailure(e)) {
                    Log.w(TAG, "$action $entityId: final ${e.javaClass.simpleName} — dropping")
                    DatabaseManager.markSyncEntrySynced(queueId)
                } else {
                    Log.i(TAG, "$action $entityId: transient ${e.javaClass.simpleName} — left queued")
                }
            }
        }
    }

    /** iOS semantics: any 4xx but 401 is final (409 stale, 404 gone, 400 bad body); network / 5xx / 401 retry. */
    internal fun isFinalFailure(e: Throwable): Boolean = when (e) {
        is HttpException -> e.code() in 400..499 && e.code() != 401
        is ApiError.Forbidden, is ApiError.NotFound -> true
        is ApiError.ServerError -> e.statusCode in 400..499 && e.statusCode != 401
        else -> false
    }

    /** Replay queued slot writes (iOS `SyncManager.drainMealSlots`). */
    suspend fun drain() {
        SyncManager.drainQueue(SyncDomain.MEAL_SLOTS) { entry ->
            val slot = decodeSlot(entry.payload) ?: return@drainQueue // undecodable → drop
            when (entry.action) {
                ACTION_CREATE -> ApiClient.nutritionApi.createMealSlot(slot.toCreateRequest())
                ACTION_UPDATE -> ApiClient.nutritionApi.updateMealSlot(
                    slot.id,
                    UpdateMealSlotRequest(
                        rowVersion = slot.rowVersion, recipeName = slot.recipeName, calories = slot.calories,
                        protein = slot.protein, fat = slot.fat, carbs = slot.carbs, ingredientItems = slot.ingredientItemsPayload()
                    )
                )
                ACTION_FOODS -> replaceFoodsOnServer(slot)
                ACTION_DELETE -> deleteOnServer(slot)
                else -> Log.w(TAG, "unknown meal-slot queue action '${entry.action}' — dropping")
            }
        }
    }

    private fun decodeSlot(payload: String): MealSlot? =
        try { if (payload.isBlank()) null else ApiClient.gson.fromJson(payload, MealSlot::class.java) } catch (_: Exception) { null }

    private fun notifyChanged() { _changes.tryEmit(Unit) }

    // MARK: - Wire mapping

    /** Null when the slot has no structured ingredients (leave server rows unchanged). */
    private fun MealSlot.ingredientItemsPayload(): List<MealSlotIngredientDto>? =
        if (ingredients.isEmpty()) null
        else ingredients.map { MealSlotIngredientDto(name = it.name, quantity = it.quantity.ifEmpty { null }, unit = it.unit.ifEmpty { null }, category = it.category.wireValue) }

    internal fun MealSlot.toCreateRequest(): CreateMealSlotRequest = CreateMealSlotRequest(
        id = id, day = date, mealType = mealType.wireValue, recipeName = recipeName,
        calories = calories, protein = protein, fat = fat, carbs = carbs, isAISuggestion = isAISuggestion,
        ingredients = null,
        foods = foods.takeIf { it.isNotEmpty() }?.map { it.toCreateRequest() },
        ingredientItems = ingredientItemsPayload()
    )

    internal fun MealSlotFood.toCreateRequest(): CreateMealSlotFoodRequest = CreateMealSlotFoodRequest(
        name = name, servings = servings.coerceAtLeast(0.0001), servingSize = servingSize, servingUnit = servingUnit,
        calories = calories, protein = protein, fat = fat, carbs = carbs, foodId = foodId
    )

    internal fun MealSlotFoodDto.toMealSlotFood(): MealSlotFood = MealSlotFood(
        id = id?.lowercase(Locale.US) ?: UUID.randomUUID().toString().lowercase(Locale.US),
        foodId = foodId, name = name ?: "", servings = servings ?: 1.0,
        servingSize = servingSize, servingUnit = servingUnit,
        calories = calories ?: 0.0, protein = protein ?: 0.0, fat = fat ?: 0.0, carbs = carbs ?: 0.0
    )

    internal fun MealSlotDetailDto.toMealSlot(): MealSlot = MealSlot(
        id = id.lowercase(Locale.US),
        date = day?.let { MealPlanDay.normalize(it) } ?: MealPlanDay.string(Date()),
        mealType = MealType.fromWire(mealType) ?: MealType.BREAKFAST,
        recipeName = recipeName ?: "",
        calories = (calories ?: 0.0).roundToInt(),
        protein = protein ?: 0.0, fat = fat ?: 0.0, carbs = carbs ?: 0.0,
        isAISuggestion = isAISuggestion ?: false,
        ingredients = ingredientItems?.sortedBy { it.sortOrder ?: Int.MAX_VALUE }?.map { it.toGroceryItem() } ?: emptyList(),
        ingredientsText = ingredients,
        foods = foods?.map { it.toMealSlotFood() } ?: emptyList(),
        rowVersion = rowVersion ?: ""
    )

    internal fun MealSlotWeekDto.toWeek(fallbackFrom: String, fallbackTo: String): MealSlotWeek = MealSlotWeek(
        from = from?.let { MealPlanDay.normalize(it) } ?: fallbackFrom,
        to = to?.let { MealPlanDay.normalize(it) } ?: fallbackTo,
        days = (days ?: emptyList()).map { d ->
            MealSlotDayRollup(
                date = d.day?.let { MealPlanDay.normalize(it) } ?: "",
                totalCalories = (d.totalCalories ?: 0.0).roundToInt(),
                totalProtein = d.totalProtein ?: 0.0, totalFat = d.totalFat ?: 0.0, totalCarbs = d.totalCarbs ?: 0.0,
                slots = d.slots?.map { it.toMealSlot() } ?: emptyList()
            )
        }
    )
}
