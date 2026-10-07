package com.fitnesslink.fit.nutrition

import com.fitnesslink.fit.model.GroceryCategory
import com.fitnesslink.fit.model.GroceryItem
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.MealSlot
import com.fitnesslink.fit.model.MealSlotFood
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.dto.DraftMealIngredient
import com.fitnesslink.fit.network.dto.FoodSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * One food staged in the meal builder (CONTRACTS). Macros are PER SERVING;
 * contribution = macro × [servings]. `foodId` is null for manual / AI /
 * template foods.
 */
data class MealBuilderItem(
    val foodId: String?,
    val name: String,
    val servings: Double,
    val servingSize: Double?,
    val servingUnit: String?,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double
) {
    val totalCalories: Double get() = calories * servings
    val totalProtein: Double get() = protein * servings
    val totalFat: Double get() = fat * servings
    val totalCarbs: Double get() = carbs * servings

    fun toMealSlotFood(id: String = UUID.randomUUID().toString().lowercase(Locale.US)): MealSlotFood = MealSlotFood(
        id = id, foodId = foodId, name = name, servings = servings, servingSize = servingSize, servingUnit = servingUnit,
        calories = calories, protein = protein, fat = fat, carbs = carbs
    )

    companion object {
        fun from(food: MealSlotFood): MealBuilderItem = MealBuilderItem(
            foodId = food.foodId, name = food.name, servings = food.servings, servingSize = food.servingSize,
            servingUnit = food.servingUnit, calories = food.calories, protein = food.protein, fat = food.fat, carbs = food.carbs
        )

        /** A catalog search hit as a one-serving staged food (iOS `MealSlotFood.from(searchResult:)`). */
        fun from(result: FoodSearchResult, servings: Double = 1.0): MealBuilderItem = MealBuilderItem(
            foodId = if (result.isTemplate == true) null else result.id,
            name = result.displayName, servings = servings,
            servingSize = result.servingSizeGrams, servingUnit = result.servingUnit ?: "serving",
            calories = result.calories, protein = result.protein, fat = result.fat, carbs = result.carbs
        )
    }
}

/**
 * FTNSSLNK-205 — meal-builder staging state (port of iOS `MealBuilderStore`).
 *
 * The builder is a staging surface: the user assembles several itemized
 * foods for one `(date, mealType)` slot BEFORE anything is persisted, then
 * commits the whole meal at once through [MealPlanStore.upsert]. Because the
 * food pickers (agent B's search / barcode / recents / templates screens)
 * reach `FoodDetailScreen` through navigation, a picked food is captured
 * back into this singleton ([add]) rather than handed through a callback —
 * `FoodDetailScreen` in mode `addToMeal` calls [add] instead of logging.
 *
 * Pure state + math; no persistence.
 */
object MealBuilderStore {
    private val _items = MutableStateFlow<List<MealBuilderItem>>(emptyList())
    /** The staged foods, in add order (index becomes the server `sortOrder`). */
    val items: StateFlow<List<MealBuilderItem>> = _items.asStateFlow()

    /** Bumped on every [add] — the builder screen watches it to close its picker and reveal the total. */
    private val _captureCount = MutableStateFlow(0)
    val captureCount: StateFlow<Int> = _captureCount.asStateFlow()

    /**
     * The slot being edited, or null when building a brand-new meal. The
     * planner sets it before navigating so the legacy `mealSlotDetail`
     * route (which carries no slot id) still opens the right meal.
     */
    @Volatile var activeSlotId: String? = null

    /**
     * True while the builder is the destination for picked foods. Agent B's
     * pickers may consult this to route their detail screen into
     * `mode = "addToMeal"` when the user came from the builder.
     */
    @Volatile var isCapturing: Boolean = false

    /** `yyyy-MM-dd` the meal is planned for. */
    var date: String = MealPlanDay.string(Date())
        private set
    var mealType: MealType = MealType.BREAKFAST
        private set
    /** rowVersion of the slot under edit (foods-replace PUT). */
    var editingRowVersion: String = ""
        private set
    /** Whether the edited slot came in flagged AI-suggested — preserved on save. */
    var wasAISuggestion: Boolean = false
        private set
    /** Optional meal name; empty falls back to [derivedName]. */
    private val _mealName = MutableStateFlow("")
    val mealName: StateFlow<String> = _mealName.asStateFlow()
    /** FTNSSLNK-369: shoppable ingredient rows carried onto the saved slot. */
    private val _ingredients = MutableStateFlow<List<GroceryItem>>(emptyList())
    val ingredients: StateFlow<List<GroceryItem>> = _ingredients.asStateFlow()

    /** The id the existing foods were loaded with, kept so re-saving preserves them. */
    private var stagedFoodIds: MutableList<String> = mutableListOf()

    // MARK: - Session lifecycle

    /**
     * Begin (or resume) a session for a slot. Editing seeds the staged foods
     * from the slot's itemized `foods`, or synthesizes one line from a flat
     * (legacy / AI / copy) slot so its macros survive the re-save.
     */
    fun begin(date: String, mealType: MealType, existing: MealSlot?) {
        this.date = MealPlanDay.normalize(date)
        this.mealType = mealType
        activeSlotId = existing?.id
        editingRowVersion = existing?.rowVersion ?: ""
        wasAISuggestion = existing?.isAISuggestion ?: false
        _mealName.value = existing?.recipeName ?: ""
        _ingredients.value = existing?.ingredients ?: emptyList()
        stagedFoodIds = mutableListOf()
        _items.value = when {
            existing == null -> emptyList()
            existing.foods.isNotEmpty() -> existing.foods.map { stagedFoodIds += it.id; MealBuilderItem.from(it) }
            existing.calories > 0 || existing.recipeName.isNotEmpty() -> listOf(
                MealBuilderItem(
                    foodId = null,
                    name = existing.recipeName.ifEmpty { mealType.wireValue },
                    servings = 1.0, servingSize = null, servingUnit = null,
                    calories = existing.calories.toDouble(), protein = existing.protein, fat = existing.fat, carbs = existing.carbs
                )
            )
            else -> emptyList()
        }
        _captureCount.value = 0
    }

    /**
     * FTNSSLNK-209: a brand-new session pre-populated with [seed] (e.g. foods
     * matched from grocery items). The destination — a planner slot or a
     * direct diary log — is chosen at save time.
     */
    fun beginSeeded(date: String, mealType: MealType, name: String, seed: List<MealBuilderItem>, markAI: Boolean = false) {
        this.date = MealPlanDay.normalize(date)
        this.mealType = mealType
        activeSlotId = null
        editingRowVersion = ""
        wasAISuggestion = markAI
        _mealName.value = name
        _ingredients.value = emptyList()
        stagedFoodIds = mutableListOf()
        _items.value = seed
        _captureCount.value = 0
    }

    /** Re-point the session at another `(date, mealType)` (destination sheet). */
    fun retarget(date: String, mealType: MealType) {
        this.date = MealPlanDay.normalize(date)
        this.mealType = mealType
    }

    fun clear() {
        _items.value = emptyList()
        _ingredients.value = emptyList()
        _mealName.value = ""
        activeSlotId = null
        editingRowVersion = ""
        wasAISuggestion = false
        isCapturing = false
        stagedFoodIds = mutableListOf()
        _captureCount.value = 0
    }

    // MARK: - Mutations (totals are derived)

    fun add(item: MealBuilderItem) {
        _items.value = _items.value + item
        stagedFoodIds += UUID.randomUUID().toString().lowercase(Locale.US)
        _captureCount.value += 1
    }

    fun remove(index: Int) {
        val current = _items.value
        if (index !in current.indices) return
        _items.value = current.toMutableList().apply { removeAt(index) }
        if (index in stagedFoodIds.indices) stagedFoodIds.removeAt(index)
    }

    fun setServings(index: Int, servings: Double) {
        if (servings <= 0) return
        val current = _items.value
        if (index !in current.indices) return
        _items.value = current.toMutableList().apply { set(index, current[index].copy(servings = servings)) }
    }

    fun setMealName(name: String) { _mealName.value = name }

    fun markAISuggested() { wasAISuggestion = true }

    /** Append an AI suggestion's ingredient rows (deduped by name, case-insensitive). */
    fun addIngredients(draft: List<DraftMealIngredient>) {
        val seen = _ingredients.value.map { it.name.lowercase(Locale.US) }.toMutableSet()
        val added = mutableListOf<GroceryItem>()
        for (ing in draft) {
            val name = ing.name.trim()
            if (name.isEmpty() || !seen.add(name.lowercase(Locale.US))) continue
            added += GroceryItem(name = name, quantity = ing.quantity ?: "", unit = ing.unit ?: "", category = GroceryCategory.fromWire(ing.category))
        }
        if (added.isNotEmpty()) _ingredients.value = _ingredients.value + added
    }

    // MARK: - Derived

    val totalCalories: Int get() = _items.value.sumOf { it.totalCalories }.roundToInt()
    val totalProtein: Double get() = _items.value.sumOf { it.totalProtein }
    val totalFat: Double get() = _items.value.sumOf { it.totalFat }
    val totalCarbs: Double get() = _items.value.sumOf { it.totalCarbs }
    val isEditing: Boolean get() = activeSlotId != null
    val canSave: Boolean get() = _items.value.isNotEmpty()

    val derivedName: String
        get() {
            val trimmed = _mealName.value.trim()
            if (trimmed.isNotEmpty()) return trimmed
            val foods = _items.value
            if (foods.size == 1) return foods[0].name
            if (foods.size > 1) return "${mealType.wireValue} · ${foods.size} items"
            return mealType.wireValue
        }

    /** The staged foods as persisted [MealSlotFood]s (stable ids across re-saves). */
    fun stagedFoods(): List<MealSlotFood> {
        val current = _items.value
        while (stagedFoodIds.size < current.size) stagedFoodIds += UUID.randomUUID().toString().lowercase(Locale.US)
        return current.mapIndexed { index, item ->
            item.copy(servings = item.servings.coerceAtLeast(0.0001)).toMealSlotFood(stagedFoodIds[index])
        }
    }

    /** The slot to commit through [MealPlanStore.upsert]. */
    fun buildSlot(): MealSlot {
        val foods = stagedFoods()
        return MealSlot(
            id = activeSlotId ?: UUID.randomUUID().toString().lowercase(Locale.US),
            date = date,
            mealType = mealType,
            recipeName = derivedName,
            calories = foods.sumOf { it.totalCalories }.roundToInt(),
            protein = foods.sumOf { it.totalProtein },
            fat = foods.sumOf { it.totalFat },
            carbs = foods.sumOf { it.totalCarbs },
            isAISuggestion = wasAISuggestion,
            ingredients = _ingredients.value,
            foods = foods,
            rowVersion = editingRowVersion
        )
    }
}
