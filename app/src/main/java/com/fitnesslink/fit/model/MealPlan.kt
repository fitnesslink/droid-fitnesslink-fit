package com.fitnesslink.fit.model

import com.fitnesslink.fit.model.TolerantAdapterFactory.Companion.optString
import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Legacy day-of-week. Meal slots are DATE-anchored (`yyyy-MM-dd`); this
 * enum only survives to (a) map legacy rows saved before slots were
 * date-anchored onto the current week, and (b) keep the pre-Wave-3
 * nutrition screens compiling.
 */
enum class DayOfWeek(val short: String, val fullName: String) {
    MONDAY("Mon", "Monday"),
    TUESDAY("Tue", "Tuesday"),
    WEDNESDAY("Wed", "Wednesday"),
    THURSDAY("Thu", "Thursday"),
    FRIDAY("Fri", "Friday"),
    SATURDAY("Sat", "Saturday"),
    SUNDAY("Sun", "Sunday");

    /** Calendar weekday index (1 = Sunday … 7 = Saturday). */
    val calendarWeekday: Int
        get() = when (this) {
            SUNDAY -> 1; MONDAY -> 2; TUESDAY -> 3; WEDNESDAY -> 4
            THURSDAY -> 5; FRIDAY -> 6; SATURDAY -> 7
        }

    /** Maps this day onto the concrete date in the (Sunday-start) week containing [reference]. */
    fun dateInWeek(reference: Date = Date()): Date {
        val start = MealPlanDay.weekStart(reference)
        return MealPlanDay.addDays(start, calendarWeekday - 1)
    }

    /** `yyyy-MM-dd` of [dateInWeek]. */
    fun dateStringInWeek(reference: Date = Date()): String = MealPlanDay.string(dateInWeek(reference))

    companion object {
        fun fromShort(raw: String?): DayOfWeek? = raw?.let { r -> entries.firstOrNull { it.short.equals(r, true) || it.name.equals(r, true) } }

        fun of(date: Date): DayOfWeek {
            val weekday = MealPlanDay.calendar().apply { time = date }.get(Calendar.DAY_OF_WEEK)
            return entries.first { it.calendarWeekday == weekday }
        }
    }
}

/**
 * Wire values are the backend `GroceryCategory` enum NAMEs / iOS raw values
 * ("Produce", "Grains & Bread" …). The two enums differ (backend has
 * Meat / Canned / Beverages / Snacks), so unknown names map to the nearest
 * bucket via [fromWire]. The local DB stores the enum `name`.
 */
@JsonAdapter(GroceryCategory.Adapter::class)
enum class GroceryCategory(val displayName: String, val icon: String) {
    PRODUCE("Produce", "leaf"),
    PROTEIN("Protein", "fish"),
    DAIRY("Dairy", "cup"),
    GRAINS("Grains & Bread", "cake"),
    PANTRY("Pantry", "cabinet"),
    FROZEN("Frozen", "snowflake"),
    OTHER("Other", "basket");

    /** iOS `rawValue`. */
    val wireValue: String get() = displayName

    companion object {
        /** Case-insensitive map from any wire / legacy spelling onto the app's set. Unknown → OTHER. */
        fun fromWire(raw: String?): GroceryCategory {
            val key = raw?.trim()?.lowercase(Locale.US) ?: return OTHER
            if (key.isEmpty()) return OTHER
            return when (key) {
                "produce", "vegetables", "vegetable", "fruit", "fruits" -> PRODUCE
                "protein", "proteins", "meat", "meats", "seafood", "fish", "poultry", "eggs" -> PROTEIN
                "dairy" -> DAIRY
                "grains", "grain", "grains & bread", "grains and bread", "bread", "bakery" -> GRAINS
                "pantry", "canned", "snacks", "spices", "condiments", "oils", "baking" -> PANTRY
                "frozen" -> FROZEN
                "other", "beverages", "beverage", "drinks" -> OTHER
                else -> entries.firstOrNull { it.displayName.lowercase(Locale.US) == key || it.name.lowercase(Locale.US) == key } ?: OTHER
            }
        }

        /** Backend enum name → app category, or null for no suggestion. */
        fun fromBackendName(name: String): GroceryCategory? = when (name) {
            "Produce" -> PRODUCE
            "Dairy" -> DAIRY
            "Meat" -> PROTEIN
            "Grains" -> GRAINS
            "Frozen" -> FROZEN
            "Canned", "Snacks" -> PANTRY
            "Beverages" -> OTHER
            else -> entries.firstOrNull { it.displayName == name }
        }
    }

    class Adapter : TypeAdapter<GroceryCategory>() {
        override fun write(out: JsonWriter, value: GroceryCategory?) {
            if (value == null) out.nullValue() else out.value(value.wireValue)
        }

        override fun read(reader: JsonReader): GroceryCategory? {
            if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return null }
            return fromWire(reader.nextString())
        }
    }
}

/**
 * One planned meal. DATE-anchored: [date] is the calendar day as
 * `yyyy-MM-dd` (the server's `MealSlot.Day` column), weeks start Sunday —
 * a multi-week plan is just slots spread across dates. Rows created before
 * slots were date-anchored stored a day-of-week ("Mon"); those map onto the
 * current week on read.
 */
@JsonAdapter(MealSlot.Adapter::class)
data class MealSlot(
    val id: String = UUID.randomUUID().toString().lowercase(Locale.US),
    /** `yyyy-MM-dd`. */
    val date: String = MealPlanDay.string(Date()),
    val mealType: MealType = MealType.BREAKFAST,
    val recipeName: String = "",
    val calories: Int = 0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val isAISuggestion: Boolean = false,
    /**
     * FTNSSLNK-369: the structured, shoppable ingredient rows (server
     * `ingredientItems`, local `ingredientsJson`). This is what the grocery
     * list shops — NOT the recipe name.
     */
    val ingredients: List<GroceryItem> = emptyList(),
    /** The server's free-text `ingredients` display string. Display only; not persisted. */
    val ingredientsText: String? = null,
    /**
     * FTNSSLNK-205: the itemized foods the meal was built from (per-serving
     * macro snapshot × servings). Empty for legacy / AI / copy-week slots.
     * In-memory + sync-queue only — not persisted to `meal_slots`.
     */
    val foods: List<MealSlotFood> = emptyList(),
    /** SQL Server rowversion, base64. Empty on locally-created rows. Required on PUT/DELETE (409 if stale). */
    val rowVersion: String = ""
) {
    /** Parsed [date] at local midnight. */
    val dateValue: Date get() = MealPlanDay.parse(date)

    /** Legacy day-of-week view of [date] (pre-Wave-3 screens). */
    @Deprecated("Slots are date-anchored; use date.")
    val day: DayOfWeek get() = DayOfWeek.of(dateValue)

    val ingredientItems: List<MealSlotIngredientWire> get() = ingredients.map { MealSlotIngredientWire(it) }

    /**
     * Server read shape (`MealSlotReadWire`): `day` carries the date,
     * `ingredients` is a STRING, `ingredientItems` the structured rows,
     * macros may be decimals. Our own payloads use the app-native keys.
     */
    class Adapter : TolerantAdapterFactory<MealSlot>(MealSlot::class.java) {
        override fun preprocess(raw: JsonObject) {
            if (!raw.has("date")) {
                raw.optString("day")?.let { raw.addProperty("date", MealPlanDay.normalize(it)) }
            } else {
                raw.optString("date")?.let { raw.addProperty("date", MealPlanDay.normalize(it)) }
            }
            raw.remove("day")
            val ingredients = raw.get("ingredients")
            if (ingredients != null && ingredients.isJsonPrimitive) {
                raw.addProperty("ingredientsText", ingredients.asString)
                raw.remove("ingredients")
            } else if (ingredients != null && !ingredients.isJsonArray) {
                raw.remove("ingredients")
            }
            // Structured server rows win over whatever the app-native key held.
            val items = raw.get("ingredientItems")
            if (items != null && items.isJsonArray && items.asJsonArray.size() > 0) {
                raw.add("ingredients", items)
            }
            raw.remove("ingredientItems")
            raw.get("calories")?.takeIf { it.isJsonPrimitive }?.let {
                runCatching { it.asDouble }.getOrNull()?.let { d -> raw.addProperty("calories", d.roundToInt()) }
            }
        }

        /** Write the server's shape: `day` + `ingredientItems` beside the app-native keys. */
        override fun postwrite(tree: JsonObject) {
            tree.optString("date")?.let { tree.addProperty("day", it) }
            tree.get("ingredients")?.let { tree.add("ingredientItems", it) }
        }
    }
}

/**
 * One structured ingredient row as the server sends / accepts it
 * (`MealSlotIngredientDto`). Write shape is `{ name, quantity?, unit?,
 * category? }` — `id` / `sortOrder` are server-assigned.
 */
data class MealSlotIngredientWire(
    val id: String? = null,
    val name: String? = null,
    val quantity: String? = null,
    val unit: String? = null,
    val category: String? = null,
    val sortOrder: Int? = null
) {
    constructor(item: GroceryItem) : this(
        name = item.name,
        quantity = item.quantity.ifEmpty { null },
        unit = item.unit.ifEmpty { null },
        category = item.category.wireValue
    )

    fun toGroceryItem(): GroceryItem = GroceryItem(
        id = id?.lowercase(Locale.US) ?: UUID.randomUUID().toString().lowercase(Locale.US),
        name = (name ?: "").trim(),
        quantity = quantity ?: "",
        unit = unit ?: "",
        category = GroceryCategory.fromWire(category),
        sortOrder = sortOrder
    )
}

/**
 * FTNSSLNK-205: one itemized food staged on / persisted to a meal slot. The
 * macro fields are a PER-SERVING snapshot; contribution = macro × servings.
 */
@JsonAdapter(MealSlotFood.Adapter::class)
data class MealSlotFood(
    val id: String = UUID.randomUUID().toString().lowercase(Locale.US),
    /** Source catalog food id; null for manual / AI / template foods. */
    val foodId: String? = null,
    val name: String = "",
    /** Number of servings of this food in the meal (> 0). */
    val servings: Double = 1.0,
    /** Grams-per-serving snapshot (optional); display only. */
    val servingSize: Double? = null,
    val servingUnit: String? = null,
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0
) {
    val totalCalories: Double get() = calories * servings
    val totalProtein: Double get() = protein * servings
    val totalFat: Double get() = fat * servings
    val totalCarbs: Double get() = carbs * servings

    /** "2 × slice" or "150 g". */
    val portionLabel: String
        get() {
            val unit = servingUnit?.takeIf { it.isNotEmpty() } ?: "serving"
            if (servings == 1.0) return "1 $unit"
            val qty = if (servings % 1.0 == 0.0) servings.toInt().toString() else String.format(Locale.US, "%.2g", servings)
            return "$qty × $unit"
        }

    class Adapter : TolerantAdapterFactory<MealSlotFood>(MealSlotFood::class.java)

    companion object {
        fun from(entry: FoodEntry, servings: Double = 1.0): MealSlotFood = MealSlotFood(
            foodId = entry.foodId, name = entry.name, servings = servings,
            servingSize = entry.servingSize, servingUnit = entry.servingUnit,
            calories = entry.calories.toDouble(), protein = entry.protein, fat = entry.fat, carbs = entry.carbs
        )
    }
}

val List<MealSlotFood>.mealTotalCalories: Int get() = sumOf { it.totalCalories }.roundToInt()
val List<MealSlotFood>.mealTotalProtein: Double get() = sumOf { it.totalProtein }
val List<MealSlotFood>.mealTotalFat: Double get() = sumOf { it.totalFat }
val List<MealSlotFood>.mealTotalCarbs: Double get() = sumOf { it.totalCarbs }

// MARK: - Weekly planner read envelope (FTNSSLNK-204)

/** A Sunday-anchored week of planner slots with per-day rollups (`GET /meal-slots/me?from=&to=`). */
data class MealSlotWeek(
    /** `yyyy-MM-dd` */
    val from: String = "",
    val to: String = "",
    val days: List<MealSlotDayRollup> = emptyList()
) {
    val allSlots: List<MealSlot> get() = days.flatMap { it.slots }
}

/** One calendar day's slots plus the server's planned-nutrition rollup. */
data class MealSlotDayRollup(
    /** `yyyy-MM-dd` */
    val date: String = "",
    val totalCalories: Int = 0,
    val totalProtein: Double = 0.0,
    val totalFat: Double = 0.0,
    val totalCarbs: Double = 0.0,
    val slots: List<MealSlot> = emptyList()
)

// MARK: - Copy-week / copy-day requests (FTNSSLNK-204 / 218)

/** `POST /meal-slots/me/copy-week`. Both anchors MUST be Sundays (server-validated). */
data class CopyMealSlotWeekRequest(
    val sourceWeekStart: String,
    val targetWeekStart: String,
    val overwrite: Boolean
) {
    companion object {
        /** "Copy last week into [date]'s week": source is the Sunday one week before the target's Sunday. */
        fun copyingPreviousWeek(into: Date, overwrite: Boolean): CopyMealSlotWeekRequest {
            val target = MealPlanDay.weekStart(into)
            val source = MealPlanDay.addDays(target, -7)
            return CopyMealSlotWeekRequest(MealPlanDay.string(source), MealPlanDay.string(target), overwrite)
        }
    }
}

data class CopyMealSlotWeekResult(
    val sourceWeekStart: String = "",
    val targetWeekStart: String = "",
    val copiedCount: Int = 0,
    val skippedCount: Int = 0,
    val overwrittenCount: Int = 0
)

/** Occupied-target semantics for `POST /meal-slots/me/copy-day`. */
enum class CopyMealSlotDayMode(val rawValue: String) {
    /** Append the source day's meals alongside whatever is already planned. */
    MERGE("merge"),
    /** Clear each target day first, then copy. */
    REPLACE("replace")
}

/** `POST /meal-slots/me/copy-day`. Targets are 1–31 DISTINCT days, none equal to the source. */
data class CopyMealSlotDayRequest(
    val sourceDay: String,
    val targetDays: List<String>,
    val mode: String
) {
    companion object {
        fun copying(sourceDay: Date, to: List<Date>, mode: CopyMealSlotDayMode): CopyMealSlotDayRequest {
            val source = MealPlanDay.string(sourceDay)
            val days = to.map { MealPlanDay.string(it) }.toSet().minus(source).sorted()
            return CopyMealSlotDayRequest(source, days, mode.rawValue)
        }
    }
}

data class CopyMealSlotDayResult(
    val sourceDay: String = "",
    val mode: String = "",
    val totalCopiedCount: Int = 0,
    val totalReplacedCount: Int = 0,
    val targets: List<Target> = emptyList()
) {
    data class Target(val day: String = "", val copiedCount: Int = 0, val replacedCount: Int = 0)
}

// MARK: - Per-day over/under-target state

/** Whether a day's PLANNED calories sit under or over the target. Over is a SIGNAL, never an error. */
enum class PlannedDayTargetState {
    NO_TARGET, UNDER, OVER;

    val isOver: Boolean get() = this == OVER

    companion object {
        fun from(plannedCalories: Int, target: Int?): PlannedDayTargetState {
            if (target == null || target <= 0) return NO_TARGET
            return if (plannedCalories > target) OVER else UNDER
        }
    }
}

@JsonAdapter(GroceryItem.Adapter::class)
data class GroceryItem(
    val id: String = UUID.randomUUID().toString().lowercase(Locale.US),
    val name: String = "",
    val quantity: String = "",
    val unit: String = "",
    val category: GroceryCategory = GroceryCategory.OTHER,
    val isChecked: Boolean = false,
    /** SQL Server rowversion, base64. Required on toggle / delete. */
    val rowVersion: String = "",
    /** Owning list (FTNSSLNK-208). Null on legacy rows — the server resolves to the default list. */
    val groceryListId: String? = null,
    /** Position within the owning list (server-assigned). */
    val sortOrder: Int? = null
) {
    class Adapter : TolerantAdapterFactory<GroceryItem>(GroceryItem::class.java) {
        /** The server sends `quantity` as a number on some endpoints; keep the app's string form. */
        override fun preprocess(raw: JsonObject) {
            raw.get("quantity")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.let {
                val d = it.asDouble
                raw.addProperty("quantity", if (d % 1.0 == 0.0) d.toLong().toString() else d.toString())
            }
        }
    }
}

/**
 * A named grocery list container (FTNSSLNK-208). Every user has one
 * server-created default list; archived lists form the shopping history.
 */
@JsonAdapter(GroceryList.Adapter::class)
data class GroceryList(
    val id: String = UUID.randomUUID().toString().lowercase(Locale.US),
    val name: String = "",
    val isArchived: Boolean = false,
    val isDefault: Boolean = false,
    /** Item count from the list index endpoint. */
    val itemCount: Int = 0,
    val createdAt: Date = Date(),
    val updatedAt: Date? = null,
    val rowVersion: String = ""
) {
    class Adapter : TolerantAdapterFactory<GroceryList>(GroceryList::class.java)
}

data class DailyMealSummary(
    val id: String = UUID.randomUUID().toString(),
    /** Legacy day-of-week (pre-Wave-3 screens). */
    val day: DayOfWeek = DayOfWeek.MONDAY,
    /** `yyyy-MM-dd` */
    val date: String = "",
    val totalCalories: Int = 0,
    val totalProtein: Double = 0.0,
    val totalFat: Double = 0.0,
    val totalCarbs: Double = 0.0,
    val goal: Int = 2000
)

// MARK: - Week math (Sunday-anchored)

/**
 * Day-granular conversion for the server's `MealSlot.Day` column
 * (`yyyy-MM-dd`). A fixed POSIX locale / Gregorian calendar keeps the wire
 * format stable; parsing lands on local midnight. Weeks start Sunday.
 */
object MealPlanDay {
    const val PATTERN = "yyyy-MM-dd"

    fun calendar(): Calendar = Calendar.getInstance(Locale.US).apply { firstDayOfWeek = Calendar.SUNDAY }

    private fun formatter() = SimpleDateFormat(PATTERN, Locale.US).apply { isLenient = false }

    fun string(date: Date): String = formatter().format(date)

    fun date(string: String): Date? = try { formatter().parse(string) } catch (_: Exception) { null }

    /** Local midnight of [date]. */
    fun dayStart(date: Date): Date = calendar().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time

    /** The Sunday that starts the week containing [date] (local midnight). */
    fun weekStart(date: Date): Date {
        val cal = calendar().apply { time = dayStart(date) }
        val offset = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        cal.add(Calendar.DAY_OF_MONTH, -offset)
        return cal.time
    }

    /** The seven dates (Sun…Sat) of the week containing [date]. */
    fun weekDates(date: Date): List<Date> {
        val start = weekStart(date)
        return (0 until 7).map { addDays(start, it) }
    }

    fun addDays(date: Date, days: Int): Date = calendar().apply { time = date; add(Calendar.DAY_OF_MONTH, days) }.time

    /**
     * Parse a persisted or wire `day` value. Normally ISO `yyyy-MM-dd`;
     * tolerates a stray legacy day-of-week ("Mon"…"Sun") by mapping it onto
     * the current week so old rows aren't dropped.
     */
    fun parse(raw: String): Date {
        date(raw)?.let { return it }
        DayOfWeek.fromShort(raw)?.let { return it.dateInWeek() }
        if (raw.length >= 10) date(raw.substring(0, 10))?.let { return it }
        return dayStart(Date())
    }

    /** Canonical `yyyy-MM-dd` for any accepted spelling. */
    fun normalize(raw: String): String = string(parse(raw))
}

// MARK: - Copy-week / apply-week collision rules (client-side replay)

/**
 * The occupied-slot semantics the server applies on `copy-week` and
 * `apply-week`, replayed client-side for the offline fallback. Pure so the
 * rules are unit-testable (iOS `MealPlanViewModel.copyPreviousWeekLocally`
 * / `applyMealPlanIdeasLocally`).
 */
object MealPlanCopyRules {
    /** `(weekday, mealType)` collision key — a slot's position inside its week. */
    fun weekdayKey(slot: MealSlot): String = weekdayKey(slot.dateValue, slot.mealType)

    fun weekdayKey(date: Date, mealType: MealType): String {
        val weekday = MealPlanDay.calendar().apply { time = date }.get(Calendar.DAY_OF_WEEK)
        return "$weekday-${mealType.wireValue}"
    }

    /** `(date, mealType)` collision key — absolute, for apply-week merge. */
    fun dayKey(date: String, mealType: MealType): String = "${MealPlanDay.normalize(date)}-${mealType.wireValue}"

    /** Outcome of a client-side copy-week replay. */
    data class WeekCopyPlan(
        /** Target-week slots that get removed first (overwrite mode only). */
        val remove: List<MealSlot>,
        /** New slots to create (fresh ids, shifted +7 days, foods carried along). */
        val create: List<MealSlot>,
        val skippedCount: Int
    ) {
        val copiedCount: Int get() = create.size
        val overwrittenCount: Int get() = remove.size
    }

    /**
     * Copy [source] (last week's slots) onto the week holding [target].
     * `overwrite = true` replaces colliding `(weekday, meal)` targets and
     * keeps the rest; `false` skips collisions and adds the rest. Mirrors
     * the server's `copy-week` contract.
     */
    fun planWeekCopy(source: List<MealSlot>, target: List<MealSlot>, overwrite: Boolean): WeekCopyPlan {
        if (source.isEmpty()) return WeekCopyPlan(emptyList(), emptyList(), 0)
        val sourceKeys = source.map { weekdayKey(it) }.toSet()
        val remove = if (overwrite) target.filter { weekdayKey(it) in sourceKeys } else emptyList()
        val occupied = if (overwrite) emptySet() else target.map { weekdayKey(it) }.toSet()
        val create = mutableListOf<MealSlot>()
        var skipped = 0
        for (slot in source) {
            val targetDate = MealPlanDay.addDays(slot.dateValue, 7)
            if (!overwrite && weekdayKey(targetDate, slot.mealType) in occupied) { skipped++; continue }
            create += slot.copy(
                id = UUID.randomUUID().toString().lowercase(Locale.US),
                date = MealPlanDay.string(targetDate),
                rowVersion = ""
            )
        }
        return WeekCopyPlan(remove, create, skipped)
    }

    /** One approved AI idea destined for `(day, mealType)`. */
    data class PlannedIdea(val day: String, val mealType: MealType?, val slot: MealSlot)

    data class IdeasApplyPlan(val remove: List<MealSlot>, val create: List<MealSlot>, val skippedCount: Int)

    /**
     * Apply-week collision rules: `replace` clears the week first; `merge`
     * skips any `(day, mealType)` that already holds a meal. Ideas whose
     * day / meal type can't be resolved are skipped.
     */
    fun planIdeasApply(existing: List<MealSlot>, ideas: List<PlannedIdea>, replace: Boolean): IdeasApplyPlan {
        val remove = if (replace) existing else emptyList()
        val occupied = if (replace) mutableSetOf() else existing.map { dayKey(it.date, it.mealType) }.toMutableSet()
        val create = mutableListOf<MealSlot>()
        var skipped = 0
        for (idea in ideas) {
            val mealType = idea.mealType
            if (mealType == null || MealPlanDay.date(idea.day) == null) { skipped++; continue }
            val key = dayKey(idea.day, mealType)
            if (!occupied.add(key)) { skipped++; continue }
            create += idea.slot
        }
        return IdeasApplyPlan(remove, create, skipped)
    }
}
