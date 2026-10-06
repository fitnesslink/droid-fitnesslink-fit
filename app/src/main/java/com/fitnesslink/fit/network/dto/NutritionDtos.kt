package com.fitnesslink.fit.network.dto

import com.fitnesslink.fit.model.FoodEntry
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.util.Date

// Wire shapes for the Nutrition API (nutrition/api/v1). Mirrors iOS
// `NutritionService.swift`, `HydrationService.swift` and the models under
// `Models/API/` (NutritionProfileModels, NutritionAnalyticsModels,
// NutritionFavoriteModels, QuickLogModels, RecentMealModels,
// MealTemplateModels, MicronutrientModels, FoodPortionModels,
// GrocerySmartModels, EstimateMacros, ParseMeal, IdentifyMealPhoto,
// GenerateMealSuggestion, GenerateMealPlanWeek) and `MealPlan.swift`.
//
// Conventions:
// - Calendar dates (`DateOnly` server-side) travel as plain "yyyy-MM-dd"
//   strings and are modelled as String.
// - SQL Server rowversion tokens travel as base64 and are modelled as
//   String (`rowVersion`). Echo them on PUT/DELETE; 409 = stale.
// - Enum-like fields carry the backend's stable member NAMES.

const val MEAL_PLAN_DAY_FORMAT = "yyyy-MM-dd"

// MARK: - Food entries

/** `POST /food-entries/batch` (FTNSSLNK-214): 1–50 entries, all-or-nothing. */
data class BatchCreateFoodEntriesRequest(
    val entries: List<FoodEntry>
)

// MARK: - Foods (catalog search / detail / convert)

/**
 * `GET /foods/search?query=` row and `GET /foods/{id}`. USDA-style wire
 * names (`description` / `brandOwner` / `servingSize` / `servingSizeUnit`)
 * are mapped onto the iOS property names. `isTemplate` marks one of the
 * caller's saved custom foods merged above the catalog results — its `id`
 * is then a FoodEntry id, not resolvable via `/foods/{id}` or `/convert`.
 */
data class FoodSearchResult(
    val id: String,
    @SerializedName("description") val name: String = "",
    @SerializedName("brandOwner") val brand: String? = null,
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    @SerializedName("servingSize") val servingSizeGrams: Double? = null,
    @SerializedName("servingSizeUnit") val servingUnit: String? = null,
    /** Portions this food can be logged in (FTNSSLNK-186); null on older payloads. */
    val portions: List<FoodPortion>? = null,
    val isTemplate: Boolean? = null
) {
    val displayName: String get() = if (brand.isNullOrEmpty()) name else "$name ($brand)"
}

/** One serving portion (`FoodPortionDto`). `category`: Count / Weight / Volume / Household / Container / Portion. */
data class FoodPortion(
    val unit: String,
    val gramWeight: Double,
    val category: String = "",
    val isStandard: Boolean = false,
    val milliliterVolume: Double? = null
) {
    val isVolume: Boolean get() = category == "Volume"
}

/** `GET /foods/{id}/convert?unit=&quantity=` (`FoodPortionConversionDto`). 404 unknown food, 422 unit not resolvable. */
data class FoodPortionConversionResponse(
    val foodId: String,
    val unit: String,
    val category: String = "",
    val quantity: Double = 0.0,
    val grams: Double = 0.0,
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val fiber: Double? = null,
    val sugars: Double? = null,
    val saturatedFat: Double? = null,
    val sodium: Double? = null,
    val cholesterol: Double? = null,
    val usedDensityFallback: Boolean = false,
    val assumedDensityGramsPerMl: Double? = null
)

/**
 * `GET /barcode/{code}` (`BarcodeProductDto`, FTNSSLNK-198): the matched
 * CATALOG food's id + per-serving macros. 404 = not in the catalog (fall
 * back to OpenFoodFacts). Follow up with `GET /foods/{id}` for portions.
 */
data class BarcodeMatchDto(
    val id: String,
    val description: String = "",
    val brandOwner: String? = null,
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val gtinUpc: String? = null
) {
    fun asSearchResult() = FoodSearchResult(
        id = id, name = description, brand = brandOwner,
        calories = calories, protein = protein, fat = fat, carbs = carbs,
        isTemplate = false
    )
}

// MARK: - Quick Log (FTNSSLNK-213)

/** `GET /quick-log/sources?date=&tz=` — always exactly 4 sources (B/L/D/S). */
data class QuickLogSourcesResponse(
    val date: String = "",
    val timeZone: String = "",
    val lookbackDays: Int = 0,
    val sources: List<QuickLogMealSource> = emptyList()
)

data class QuickLogMealSource(
    val mealType: String = "",
    /** `yyyy-MM-dd`; null = nothing to copy for this meal. */
    val sourceDate: String? = null,
    val entryCount: Int = 0,
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val entries: List<QuickLogSourceEntry> = emptyList()
) {
    val hasSource: Boolean get() = sourceDate != null && entryCount > 0
}

data class QuickLogSourceEntry(
    val name: String = "",
    val calories: Double = 0.0,
    val quantity: Double? = null,
    val portionUnit: String? = null,
    val servingSize: Double? = null,
    val servingUnit: String? = null
)

/** `POST /quick-log/copy` (201). `sourceDate` pins the copy to the previewed day. */
data class QuickLogCopyRequest(
    val mealType: String,
    val targetDate: String,
    val sourceDate: String? = null,
    val tz: String? = null
)

data class QuickLogCopyResponse(
    val mealType: String = "",
    val sourceDate: String? = null,
    val targetDate: String = "",
    val timeZone: String = "",
    val entries: List<FoodEntry> = emptyList()
)

// MARK: - Recent meals (FTNSSLNK-214)

/** `GET /meals/recent?mealType=&limit=&tz=`. */
data class RecentMealsResponse(
    val timeZone: String = "",
    val windowDays: Int = 0,
    val mealTypes: List<RecentMealTypeGroup> = emptyList()
)

data class RecentMealTypeGroup(
    val mealType: String = "",
    val meals: List<RecentMeal> = emptyList()
)

data class RecentMeal(
    val mealType: String = "",
    /** `yyyy-MM-dd` */
    val date: String = "",
    val entryCount: Int = 0,
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val items: List<RecentMealItem> = emptyList()
)

/** Macros are the TOTAL as logged for the stated quantity. */
data class RecentMealItem(
    val name: String = "",
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val servingSize: Double? = null,
    val servingUnit: String? = null,
    val foodId: String? = null,
    val quantity: Double? = null,
    val portionUnit: String? = null,
    val portionGramWeight: Double? = null
)

// MARK: - Favorites (FTNSSLNK-215)

object NutritionFavoriteKind {
    const val FOOD = "food"
    const val MEAL = "meal"
}

/** `GET /favorites/me?kind=food|meal` row. `isAvailable == false` = underlying food/template removed. */
data class NutritionFavorite(
    val id: String,
    val kind: String = "",
    val foodId: String? = null,
    val mealTemplateId: String? = null,
    val dateFavorited: Date? = null,
    val isAvailable: Boolean = true,
    val food: FavoriteFoodSummary? = null,
    val meal: FavoriteMealSummary? = null
)

data class FavoriteFoodSummary(
    val id: String,
    val description: String = "",
    val brandName: String? = null,
    val brandOwner: String? = null,
    val servingSize: Double = 0.0,
    val servingSizeUnit: String = "",
    val householdServingText: String? = null,
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0
) {
    fun toFoodSearchResult() = FoodSearchResult(
        id = id, name = description, brand = brandOwner ?: brandName,
        calories = calories, protein = protein, fat = fat, carbs = carbs,
        servingSizeGrams = servingSize, servingUnit = servingSizeUnit, isTemplate = false
    )
}

data class FavoriteMealSummary(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val durationDays: Int = 0,
    val totalCalories: Double? = null,
    val proteinTarget: Double? = null,
    val fatTarget: Double? = null,
    val carbsTarget: Double? = null
)

// MARK: - Nutrition profile + target engine (FTNSSLNK-190/191)

/** Canonical backend names for the profile's enum-like fields. */
object NutritionProfileValues {
    val SEX = listOf("Male", "Female")
    val ACTIVITY_LEVEL = listOf("Sedentary", "LightlyActive", "ModeratelyActive", "VeryActive", "ExtraActive")
    val WORKOUT_FREQUENCY = listOf("None", "OneToTwo", "ThreeToFour", "FiveToSix", "SevenPlus")
    val PRIMARY_GOAL = listOf("Lose", "Maintain", "Gain", "BuildMuscle", "Recomp", "Health")
    val TIMELINE = listOf("ThirtyDays", "SixtyDays", "NinetyDays", "SixMonths", "OneYear", "Custom")
    const val SOURCE_CALCULATED = "Calculated"
    const val SOURCE_MANUAL = "Manual"
}

/**
 * One per-number "why" explanation. `key` is a stable contract key
 * ("why.bmr.mifflinStJeor", …) the UI maps to a template; `inputs` is a
 * heterogeneous bag (numbers, strings, bools) kept as raw JSON.
 */
data class WhyExplanation(
    val metric: String = "",
    val key: String = "",
    val inputs: Map<String, JsonElement> = emptyMap()
)

/** The computed (or manually overridden) plan. */
data class NutritionTargets(
    val bmr: Double = 0.0,
    val maintenanceCalories: Double = 0.0,
    val dailyCalorieTarget: Double = 0.0,
    val proteinGrams: Double = 0.0,
    val carbGrams: Double = 0.0,
    val fatGrams: Double = 0.0,
    val weeklyWeightChangeKg: Double = 0.0,
    /** `yyyy-MM-dd`; null for maintenance-style plans. */
    val estimatedGoalDate: String? = null,
    /** "Calculated" | "Manual". */
    val source: String = "",
    /** `yyyy-MM-dd` — the day this goal takes effect. */
    val effectiveFrom: String = "",
    val explanations: List<WhyExplanation> = emptyList()
) {
    val isManualOverride: Boolean get() = source == NutritionProfileValues.SOURCE_MANUAL
}

/** The stored profile. Units are METRIC on the wire; `age` is server-derived. */
data class NutritionProfile(
    val id: String,
    val userId: String = "",
    /** `yyyy-MM-dd` */
    val dateOfBirth: String = "",
    val age: Int = 0,
    val sex: String = "",
    val heightCm: Double = 0.0,
    val currentWeightKg: Double = 0.0,
    val goalWeightKg: Double = 0.0,
    val activityLevel: String = "",
    val workoutFrequency: String = "",
    val primaryGoal: String = "",
    val timeline: String = "",
    val timelineCustomDate: String? = null,
    val dietaryPreferences: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val createdAtUtc: Date? = null,
    val updatedAtUtc: Date? = null,
    /** Optimistic-concurrency token (base64) — echo on PUT/DELETE. */
    val rowVersion: String = ""
)

/** Profile create/update responses return the profile AND fresh targets. */
data class NutritionProfileWithTargets(
    val profile: NutritionProfile,
    val targets: NutritionTargets
)

/** `POST /profile` (onboarding) and `POST /profile/calculate` (preview, nothing persisted). */
data class CreateNutritionProfileRequest(
    val dateOfBirth: String,
    val sex: String,
    val heightCm: Double,
    val currentWeightKg: Double,
    val goalWeightKg: Double,
    val activityLevel: String,
    val workoutFrequency: String,
    val primaryGoal: String,
    val timeline: String,
    val timelineCustomDate: String? = null,
    val dietaryPreferences: List<String>? = null,
    val allergies: List<String>? = null
)

/**
 * `PUT /profile` — partial update; only non-null fields change and the
 * server recalculates. A `rowVersion`-only body is the sanctioned
 * "recalculate now" (drops a manual override).
 */
data class UpdateNutritionProfileRequest(
    val rowVersion: String,
    val dateOfBirth: String? = null,
    val sex: String? = null,
    val heightCm: Double? = null,
    val currentWeightKg: Double? = null,
    val goalWeightKg: Double? = null,
    val activityLevel: String? = null,
    val workoutFrequency: String? = null,
    val primaryGoal: String? = null,
    val timeline: String? = null,
    val timelineCustomDate: String? = null,
    val dietaryPreferences: List<String>? = null,
    val allergies: List<String>? = null
)

/** `POST /profile/targets/override` — provenance becomes "Manual". */
data class OverrideNutritionTargetsRequest(
    val calorieGoal: Double,
    val proteinTarget: Double,
    val fatTarget: Double,
    val carbsTarget: Double
)

/** `GET /profile/goals` row (newest first). */
data class NutritionGoalHistoryEntry(
    val id: String,
    val userId: String = "",
    val calorieGoal: Double = 0.0,
    val proteinTarget: Double = 0.0,
    val fatTarget: Double = 0.0,
    val carbsTarget: Double = 0.0,
    val source: String = "",
    val effectiveFrom: String = "",
    val isCurrent: Boolean = false,
    val supersededAtUtc: Date? = null,
    val maintenanceCalories: Double? = null,
    val weeklyWeightChangeKg: Double? = null,
    val estimatedGoalDate: String? = null,
    val createdAtUtc: Date? = null
)

// MARK: - Daily summary (FTNSSLNK-187/191/216) + range summary (193/196)

/** `target` / `remaining` are null when no goal was in effect on that date. */
data class MacroSummaryLine(
    val target: Double? = null,
    val consumed: Double = 0.0,
    val remaining: Double? = null
)

/** Effective-dated: describes the goal in effect ON the requested date. */
data class NutritionDailyTargets(
    val calories: MacroSummaryLine = MacroSummaryLine(),
    val protein: MacroSummaryLine = MacroSummaryLine(),
    val fat: MacroSummaryLine = MacroSummaryLine(),
    val carbs: MacroSummaryLine = MacroSummaryLine(),
    val source: String? = null,
    val effectiveFrom: String? = null
) {
    val hasTargets: Boolean get() = calories.target != null
}

/** Water logged vs. the hydration goal, in `unit` ("fl oz" when no goal exists). */
data class WaterDaySummary(
    val total: Double = 0.0,
    val goal: Double? = null,
    val remaining: Double? = null,
    val unit: String = ""
)

data class MealGroupSummary(
    val mealType: String = "",
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val entries: List<FoodEntry> = emptyList()
)

/**
 * One micro's line. `consumed == null` means NO data was recorded that day
 * — render "—", never 0. `target` is null for micros with no target (Sugars).
 */
data class MicroSummaryLine(
    val consumed: Double? = null,
    val target: Double? = null,
    val remaining: Double? = null,
    val unit: String? = null
)

data class DailyMicrosSummary(
    val fiber: MicroSummaryLine? = null,
    val sugars: MicroSummaryLine? = null,
    val sodium: MicroSummaryLine? = null,
    val saturatedFat: MicroSummaryLine? = null,
    val cholesterol: MicroSummaryLine? = null
)

/** `GET /summary?date=&tz=`. */
data class NutritionDailySummary(
    val date: String = "",
    val timeZone: String = "",
    val targets: NutritionDailyTargets = NutritionDailyTargets(),
    val water: WaterDaySummary? = null,
    val meals: List<MealGroupSummary>? = null,
    val micros: DailyMicrosSummary? = null
)

/** `GET /summary?from=&to=&tz=` row — one per local day (max 92 days). */
data class NutritionDayRollup(
    val date: String = "",
    val calorieTarget: Double? = null,
    val calorieConsumed: Double = 0.0,
    val calorieRemaining: Double? = null,
    val proteinConsumed: Double = 0.0,
    val fatConsumed: Double = 0.0,
    val carbsConsumed: Double = 0.0,
    val waterTotal: Double = 0.0,
    val waterGoal: Double? = null,
    val entryCount: Int = 0,
    /** Any food or water logged that day — the completeness dot. */
    val hasData: Boolean = false
)

data class NutritionSummaryRange(
    val from: String = "",
    val to: String = "",
    val timeZone: String = "",
    /** Unit every day's water figures arrive in (user's preferred display unit). */
    val waterUnit: String? = null,
    val days: List<NutritionDayRollup> = emptyList()
)

// MARK: - Analytics (FTNSSLNK-212)

object NutritionTrendPeriod {
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
}

/** `GET /analytics/trends?from=&to=&period=&tz=&page=&pageSize=`. */
data class NutritionTrendsResponse(
    val from: String = "",
    val to: String = "",
    val period: String = "",
    val timeZone: String = "",
    val waterUnit: String = "",
    val waterDailyGoal: Double? = null,
    val buckets: NutritionTrendBucketPage = NutritionTrendBucketPage()
)

/** The trends page envelope (`data` / `hasNextPage`). */
data class NutritionTrendBucketPage(
    val data: List<NutritionTrendBucket> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 0,
    val totalCount: Int = 0,
    val totalPages: Int = 0,
    val hasNextPage: Boolean = false,
    val hasPreviousPage: Boolean = false
)

/** One time bucket, oldest-first. Weekly buckets are Sunday-start. */
data class NutritionTrendBucket(
    val periodStart: String = "",
    val periodEnd: String = "",
    val daysInPeriod: Int = 0,
    val daysLogged: Int = 0,
    val entryCount: Int = 0,
    val caloriesConsumed: Double = 0.0,
    val proteinConsumed: Double = 0.0,
    val fatConsumed: Double = 0.0,
    val carbsConsumed: Double = 0.0,
    val avgCaloriesPerLoggedDay: Double? = null,
    val avgProteinPerLoggedDay: Double? = null,
    val avgFatPerLoggedDay: Double? = null,
    val avgCarbsPerLoggedDay: Double? = null,
    val avgDailyCalorieTarget: Double? = null,
    val avgDailyProteinTarget: Double? = null,
    val avgDailyFatTarget: Double? = null,
    val avgDailyCarbsTarget: Double? = null,
    val daysWithTarget: Int = 0,
    val waterTotal: Double = 0.0,
    val waterDaysLogged: Int = 0,
    val avgWaterPerLoggedDay: Double? = null
)

/** `GET /analytics/consistency?from=&to=&tz=`. */
data class NutritionConsistencyResponse(
    val from: String = "",
    val to: String = "",
    val timeZone: String = "",
    val daysInRange: Int = 0,
    val daysWithFood: Int = 0,
    val daysWithWater: Int = 0,
    val daysWithAny: Int = 0,
    /** 0–1 fraction of range days with at least one food entry. */
    val foodLoggingRate: Double = 0.0,
    val firstLoggedDate: String? = null,
    val lastLoggedDate: String? = null,
    val currentStreak: NutritionLoggingStreak = NutritionLoggingStreak(),
    val longestStreak: NutritionLoggingStreak = NutritionLoggingStreak()
)

data class NutritionLoggingStreak(
    val lengthDays: Int = 0,
    val startDate: String? = null,
    val endDate: String? = null
)

/** `GET /analytics/weight-progress?from=&to=&tz=`. Weights are in `unit` ("lbs" today). */
data class WeightProgressResponse(
    val from: String = "",
    val to: String = "",
    val timeZone: String = "",
    val unit: String = "lbs",
    val points: List<WeightProgressPoint> = emptyList(),
    val startWeight: Double? = null,
    val endWeight: Double? = null,
    val totalChange: Double? = null,
    val plan: WeightProgressPlan? = null
)

data class WeightProgressPoint(
    val date: String = "",
    val weight: Double = 0.0,
    val movingAvg7Day: Double = 0.0
)

data class WeightProgressPlan(
    val source: String = "",
    val effectiveFrom: String = "",
    val anchorDate: String = "",
    val anchorWeight: Double = 0.0,
    /** Per week in `unit`; negative = losing. */
    val weeklyChange: Double = 0.0,
    val goalWeight: Double? = null,
    val estimatedGoalDate: String? = null,
    val projectedWeightAtRangeEnd: Double = 0.0,
    val latestVarianceFromPlan: Double = 0.0
)

// MARK: - Displayed nutrients preference (FTNSSLNK-217)

/** Canonical micro names (`Fiber`, `Sugars`, `Sodium`, `SaturatedFat`, `Cholesterol`). */
object Micronutrients {
    const val FIBER = "Fiber"
    const val SUGARS = "Sugars"
    const val SODIUM = "Sodium"
    const val SATURATED_FAT = "SaturatedFat"
    const val CHOLESTEROL = "Cholesterol"
    val ALL = listOf(FIBER, SUGARS, SODIUM, SATURATED_FAT, CHOLESTEROL)
    val DEFAULT_DISPLAYED = listOf(FIBER, SUGARS, SODIUM)
}

/** `GET` / `PUT /preferences/displayed-nutrients` DTO. */
data class DisplayedNutrientsPreference(
    val displayedMicros: List<String> = emptyList(),
    /** True while the user has never saved a preference. */
    val isDefault: Boolean = true
)

/** `PUT /preferences/displayed-nutrients` — FULL replacement (empty allowed); unknown names → 400. */
data class UpdateDisplayedNutrientsRequest(
    val displayedMicros: List<String>
)

// MARK: - Meal slots (date-anchored, Sunday weeks — FTNSSLNK-204/205/218/369)

/**
 * `GET /meal-slots/me?startDate=&endDate=` row (flat totals, no itemized
 * foods). `day` is `yyyy-MM-dd`; legacy rows may hold a day-of-week like
 * "Mon" — map onto the current week on read, never write that shape back.
 * `ingredients` is the server's free-text display string.
 */
data class MealSlotDto(
    val id: String,
    val day: String? = null,
    val mealType: String? = null,
    val recipeName: String? = null,
    val calories: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    val carbs: Double? = null,
    val isAISuggestion: Boolean? = null,
    val ingredients: String? = null,
    /** FTNSSLNK-369 structured ingredient rows (null on older servers). */
    val ingredientItems: List<MealSlotIngredientDto>? = null,
    val rowVersion: String? = null
)

/**
 * One structured ingredient row (`MealSlotIngredientDto`). On write the
 * shape is `{ name, quantity?, unit?, category? }` — `id` / `sortOrder` are
 * server-assigned (by array index) and must be left null.
 */
data class MealSlotIngredientDto(
    val id: String? = null,
    val name: String? = null,
    val quantity: String? = null,
    val unit: String? = null,
    /** Backend `GroceryCategory` enum NAME. */
    val category: String? = null,
    val sortOrder: Int? = null
)

/** One itemized food on a slot (`MealSlotFoodDto`). Macros are PER-SERVING. */
data class MealSlotFoodDto(
    val id: String? = null,
    val foodId: String? = null,
    val name: String? = null,
    val servings: Double? = null,
    val servingSize: Double? = null,
    val servingUnit: String? = null,
    val calories: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    val carbs: Double? = null
)

/** `GET /meal-slots/{id}` — a slot with its itemized foods + current rowVersion. */
data class MealSlotDetailDto(
    val id: String,
    val day: String? = null,
    val mealType: String? = null,
    val recipeName: String? = null,
    val calories: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    val carbs: Double? = null,
    val isAISuggestion: Boolean? = null,
    val ingredients: String? = null,
    val ingredientItems: List<MealSlotIngredientDto>? = null,
    val rowVersion: String? = null,
    val foods: List<MealSlotFoodDto>? = null
)

/** One calendar day of the week envelope with the server's planned-nutrition rollup. */
data class MealSlotDayDto(
    val day: String? = null,
    val totalCalories: Double? = null,
    val totalProtein: Double? = null,
    val totalFat: Double? = null,
    val totalCarbs: Double? = null,
    val slots: List<MealSlotDetailDto>? = null
)

/** `GET /meal-slots/me?from=&to=` (`MealSlotWeekDto`) — Sunday-anchored week WITH itemized foods. */
data class MealSlotWeekDto(
    val from: String? = null,
    val to: String? = null,
    val days: List<MealSlotDayDto>? = null
)

/** One food on a create / replace-foods write (`CreateMealSlotFoodRequest`); server assigns sortOrder by index. */
data class CreateMealSlotFoodRequest(
    val name: String,
    val servings: Double,
    val servingSize: Double? = null,
    val servingUnit: String? = null,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val foodId: String? = null
)

/**
 * `POST /meal-slots` (201 = bare new slot id; the client-supplied `id` is
 * kept). `day` is `yyyy-MM-dd`. When `foods` ride along the server recomputes
 * the flat totals from them. `ingredientItems`: null = leave server rows
 * unchanged, [] = clear, list = replace.
 */
data class CreateMealSlotRequest(
    val id: String,
    val day: String,
    val mealType: String,
    val recipeName: String? = null,
    val calories: Int = 0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val isAISuggestion: Boolean = false,
    val ingredients: String? = null,
    val foods: List<CreateMealSlotFoodRequest>? = null,
    val ingredientItems: List<MealSlotIngredientDto>? = null
)

/** `PUT /meal-slots/{id}` (204, rowVersion-guarded). Null fields unchanged. */
data class UpdateMealSlotRequest(
    val rowVersion: String,
    val recipeName: String? = null,
    val calories: Int? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    val carbs: Double? = null,
    val ingredients: String? = null,
    val ingredientItems: List<MealSlotIngredientDto>? = null
)

/** `PUT /meal-slots/{id}/foods` (204) — replace-set the itemized foods; needs the CURRENT rowVersion. */
data class ReplaceMealSlotFoodsRequest(
    val rowVersion: String,
    val foods: List<CreateMealSlotFoodRequest>
)

/** `POST /meal-slots/me/copy-week`. Both anchors MUST be Sundays (`yyyy-MM-dd`). */
data class CopyMealSlotWeekRequest(
    val sourceWeekStart: String,
    val targetWeekStart: String,
    val overwrite: Boolean
)

data class CopyMealSlotWeekResult(
    val sourceWeekStart: String = "",
    val targetWeekStart: String = "",
    val copiedCount: Int = 0,
    val skippedCount: Int = 0,
    val overwrittenCount: Int = 0
)

object CopyMealSlotDayMode {
    const val MERGE = "merge"
    const val REPLACE = "replace"
}

/** `POST /meal-slots/me/copy-day`. 1–31 distinct target days, none equal to the source (400 otherwise). Atomic. */
data class CopyMealSlotDayRequest(
    val sourceDay: String,
    val targetDays: List<String>,
    /** "merge" | "replace" */
    val mode: String
)

data class CopyMealSlotDayResult(
    val sourceDay: String = "",
    val mode: String = "",
    val totalCopiedCount: Int = 0,
    val totalReplacedCount: Int = 0,
    val targets: List<Target> = emptyList()
) {
    data class Target(
        val day: String = "",
        val copiedCount: Int = 0,
        val replacedCount: Int = 0
    )
}

// MARK: - Meal templates (FTNSSLNK-219)

/** `GET /meal-templates/me` row. `userId == null` = system template (read-only). */
data class MealTemplate(
    val id: String,
    val userId: String? = null,
    val name: String = "",
    val mealType: String? = null,
    val totalCalories: Double = 0.0,
    val totalProtein: Double = 0.0,
    val totalFat: Double = 0.0,
    val totalCarbs: Double = 0.0,
    val createdAtUtc: Date? = null,
    val rowVersion: String = "",
    val foods: List<MealTemplateFood> = emptyList()
) {
    val isSystem: Boolean get() = userId == null
}

/** One food on a template (`MealTemplateFoodDto`). Macros are per-serving. */
data class MealTemplateFood(
    val id: String,
    val mealTemplateId: String = "",
    val foodId: String? = null,
    val name: String = "",
    val servings: Double = 1.0,
    val servingSize: Double? = null,
    val servingUnit: String? = null,
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val sortOrder: Int = 0,
    val createdAtUtc: Date? = null,
    val rowVersion: String = ""
)

/** `POST /meal-templates` (201 = bare new template id). `foods` ≥ 1. */
data class CreateMealTemplateRequest(
    val name: String,
    val mealType: String? = null,
    val foods: List<CreateMealSlotFoodRequest>
)

/** `PUT /meal-templates/{id}` (204, rowVersion-guarded). `foods` non-null = FULL replace-set. */
data class UpdateMealTemplateRequest(
    val rowVersion: String,
    val name: String? = null,
    val mealType: String? = null,
    val foods: List<CreateMealSlotFoodRequest>? = null
)

// MARK: - Grocery (FTNSSLNK-208/210/220/369)

/** Server grocery row. `quantity` is `decimal?`; `category` is a free string. */
data class GroceryItemDto(
    val id: String,
    val name: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val category: String? = null,
    val isChecked: Boolean? = null,
    val rowVersion: String? = null,
    val groceryListId: String? = null,
    val sortOrder: Int? = null
)

/**
 * `POST /grocery` (201 = bare item id, the client-supplied one). A
 * non-numeric free-form quantity is sent as null. `groceryListId == null`
 * = the caller's default list.
 */
data class CreateGroceryItemRequest(
    val id: String,
    val groceryListId: String? = null,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val category: String? = null
)

/** `PUT /grocery/{id}` (204, rowVersion-guarded). Null fields unchanged. */
data class UpdateGroceryItemRequest(
    val rowVersion: String,
    val name: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val category: String? = null
)

/**
 * `GET /grocery/lists` row and `GET /grocery/lists/{id}` detail. The index
 * carries `itemCount`; the detail carries `items` (ordered by sortOrder).
 */
data class GroceryListDto(
    val id: String,
    val name: String? = null,
    val isArchived: Boolean? = null,
    val isDefault: Boolean? = null,
    val itemCount: Int? = null,
    val createdAtUtc: Date? = null,
    val updatedAtUtc: Date? = null,
    val rowVersion: String? = null,
    val items: List<GroceryItemDto>? = null
)

/** `POST /grocery/lists` (201 = the list). */
data class CreateGroceryListRequest(
    val name: String,
    val id: String? = null
)

/** `PUT /grocery/lists/{id}` (204). Null fields unchanged. */
data class UpdateGroceryListRequest(
    val rowVersion: String,
    val name: String? = null,
    val isArchived: Boolean? = null
)

/** `POST /grocery/lists/{id}/duplicate`. `name == null` → "<source> (copy)". */
data class DuplicateGroceryListRequest(
    val name: String? = null
)

/** `PUT /grocery/lists/{id}/reorder` (204) — each item's sortOrder becomes its index. */
data class ReorderGroceryItemsRequest(
    val itemIds: List<String>
)

/**
 * `POST /grocery/generate` (FTNSSLNK-210): consolidate every itemized
 * planned food in the inclusive `[from, to]` day range into a NEW list and
 * return it in full. 400 when the range holds no itemized planned foods.
 */
data class GenerateGroceryListRequest(
    val from: String,
    val to: String,
    val listName: String? = null
)

/** `POST /grocery/analyze` — READ-ONLY dry run before an add. */
data class AnalyzeGroceryItemRequest(
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    /** null = the caller's default list. */
    val groceryListId: String? = null
)

data class GroceryAnalyzeResponse(
    /** Backend `GroceryCategory` NAME (Produce | Dairy | Meat | Grains | Frozen | Canned | Beverages | Snacks). */
    val suggestedCategory: String? = null,
    val duplicate: GroceryDuplicateMatch? = null
)

data class GroceryDuplicateMatch(
    val itemId: String,
    val name: String = "",
    val quantity: Double? = null,
    val unit: String? = null,
    val category: String? = null,
    val isChecked: Boolean = false,
    val proposedQuantity: Double? = null,
    val quantitySummable: Boolean = false,
    val rowVersion: String = ""
)

/** `POST /grocery/{id}/merge` — fold the INCOMING amount into an existing item (also unchecks). */
data class MergeGroceryItemRequest(
    val rowVersion: String,
    val quantity: Double? = null,
    val unit: String? = null
)

/** `GET /grocery/staples?listId=` row (max 20, server-ranked). */
data class GroceryStaple(
    val name: String,
    val lastQuantity: Double? = null,
    val lastUnit: String? = null,
    val suggestedCategory: String? = null,
    val listCount: Int = 0,
    val lastAddedAtUtc: Date? = null
)

// MARK: - Hydration (FTNSSLNK-221)

/** `GET /water-entries/recent-amounts?limit=` row. Units canonical ("fl oz" / "ml" / "cups"). */
data class RecentWaterAmount(
    val amount: Double,
    val unit: String,
    val lastLoggedAt: Date? = null
)

/**
 * `PUT /hydration-goals/me/preferred-unit` (204). `unit` null = clear the
 * preference — the key is written as an explicit JSON null (ApiClient
 * registers a serializer). 404 when the user has no hydration goal yet.
 */
data class PreferredWaterUnitRequest(
    val unit: String?
)

// MARK: - AI: estimate macros (FTNSSLNK-447)

data class EstimateMacrosRequest(
    val name: String,
    val brand: String? = null,
    val servingDescription: String? = null,
    /** Values the user already typed; the server treats them as locked. */
    val knownCalories: Double? = null,
    val knownProtein: Double? = null,
    val knownFat: Double? = null,
    val knownCarbs: Double? = null
)

/**
 * Either-shape envelope (also the 400 / 422 / 502 / 504 body — recover it
 * with `ApiError.ServerError.decodeBody`). `source` is "logged" | "library"
 * | "ai_estimate"; `confidence` "high" | "medium" | "low" (AI only).
 */
data class EstimateMacrosResponse(
    val success: Boolean = false,
    val httpStatus: Int = 0,
    val source: String = "",
    val generationId: String? = null,
    val food: FoodMacros? = null,
    val confidence: String? = null,
    val errorReasonCode: String? = null,
    val errorDetail: String? = null
)

data class FoodMacros(
    val name: String = "",
    val brand: String? = null,
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val servingSize: Double = 1.0,
    val servingUnit: String = "serving",
    val caloriesPer100g: Double? = null,
    val proteinPer100g: Double? = null,
    val fatPer100g: Double? = null,
    val carbsPer100g: Double? = null
) {
    val displayName: String get() = if (brand.isNullOrEmpty()) name else "$name ($brand)"
}

// MARK: - AI: parse meal (FTNSSLNK-450) + identify meal photo (FTNSSLNK-488)

data class ParseMealRequest(
    val description: String,
    val mealType: String? = null,
    val knownTotalCalories: Double? = null,
    /** The identify-meal-photo generation this description was confirmed from (FTNSSLNK-489). */
    val sourceGenerationId: String? = null
)

data class ParseMealResponse(
    val success: Boolean = false,
    val httpStatus: Int = 0,
    val generationId: String? = null,
    val items: List<ParsedMealItem>? = null,
    val notes: String? = null,
    val errorReasonCode: String? = null,
    val errorDetail: String? = null
)

data class ParsedMealItem(
    val source: String = "ai_estimate",
    val confidence: String = "",
    val food: FoodMacros = FoodMacros()
)

/** `imageBase64` is a downscaled, EXIF-free JPEG. */
data class IdentifyMealPhotoRequest(
    val imageBase64: String,
    val mealType: String? = null,
    val hint: String? = null
)

data class IdentifyMealPhotoResponse(
    val success: Boolean = false,
    val httpStatus: Int = 0,
    val generationId: String? = null,
    val items: List<IdentifiedFood>? = null,
    val notes: String? = null,
    val errorReasonCode: String? = null,
    val errorDetail: String? = null
)

data class IdentifiedFood(
    val name: String = "",
    val portionDescription: String = "",
    val confidence: String = ""
)

// MARK: - AI: meal suggestions + week ideas

data class GenerateMealSuggestionRequest(
    val prompt: String? = null,
    val context: GenerateMealContext? = null,
    val count: Int? = 3,
    val temperature: Double? = null
)

data class GenerateMealContext(
    val mealType: String? = null,
    val calorieTarget: Int? = null,
    val proteinTarget: Double? = null,
    val fatTarget: Double? = null,
    val carbsTarget: Double? = null,
    val dietaryPreferences: List<String>? = null,
    val allergies: List<String>? = null,
    val avoidIngredients: List<String>? = null
)

data class GenerateMealSuggestionResponse(
    val success: Boolean = false,
    val httpStatus: Int = 0,
    val generationId: String = "",
    val suggestions: List<DraftMeal>? = null,
    val usage: GenerationUsage? = null,
    val errorReasonCode: String? = null,
    val errorDetail: String? = null
)

/** Shared by suggestions, the week draft and the apply-week payload. */
data class DraftMeal(
    val recipeName: String = "",
    val description: String? = null,
    val calories: Int = 0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val ingredients: List<DraftMealIngredient> = emptyList(),
    val prepMinutes: Int? = null
)

data class DraftMealIngredient(
    val name: String = "",
    val quantity: String? = null,
    val unit: String? = null,
    val category: String? = null
)

/** `POST /meal-slots/generate-week` — review-only, nothing saved. `weekStart` is a Sunday. */
data class GenerateMealPlanWeekRequest(
    val weekStart: String,
    val prompt: String? = null,
    val context: GenerateMealPlanWeekContext? = null,
    val temperature: Double? = null
)

/** Targets are PER DAY. The server unions allergies / preferences with the saved profile. */
data class GenerateMealPlanWeekContext(
    val dailyCalorieTarget: Int? = null,
    val dailyProteinTarget: Double? = null,
    val dailyFatTarget: Double? = null,
    val dailyCarbsTarget: Double? = null,
    /** MealType names to fill each day; null = all four. */
    val mealTypes: List<String>? = null,
    val dietaryPreferences: List<String>? = null,
    val allergies: List<String>? = null,
    val avoidIngredients: List<String>? = null,
    val cuisinePreferences: List<String>? = null,
    val maxPrepMinutes: Int? = null,
    val cookingSkill: String? = null,
    val budget: String? = null
)

data class GenerateMealPlanWeekResponse(
    val success: Boolean = false,
    val httpStatus: Int = 0,
    val generationId: String = "",
    val draft: DraftMealPlanWeek? = null,
    val usage: GenerationUsage? = null,
    val errorReasonCode: String? = null,
    val errorDetail: String? = null
)

data class DraftMealPlanWeek(
    val weekStart: String = "",
    val notes: String? = null,
    val days: List<DraftMealPlanDay> = emptyList()
)

data class DraftMealPlanDay(
    /** `yyyy-MM-dd` */
    val day: String = "",
    val meals: List<DraftPlannedMeal> = emptyList()
)

data class DraftPlannedMeal(
    val mealType: String = "",
    val meal: DraftMeal = DraftMeal()
)

object ApplyMealPlanWeekMode {
    const val MERGE = "merge"
    const val REPLACE = "replace"
}

/** `POST /meal-slots/me/apply-week` — the reviewed subset of a draft. Atomic. */
data class ApplyMealPlanWeekRequest(
    val generationId: String? = null,
    val weekStart: String,
    /** "merge" keeps existing meals and skips clashes; "replace" clears the week first. */
    val mode: String,
    val meals: List<ApplyMealPlanWeekMeal>
)

data class ApplyMealPlanWeekMeal(
    val day: String,
    val mealType: String,
    val meal: DraftMeal
)

data class ApplyMealPlanWeekResult(
    val weekStart: String = "",
    val mode: String = "merge",
    val createdCount: Int = 0,
    val replacedCount: Int = 0,
    val skippedCount: Int = 0,
    val createdSlotIds: List<String> = emptyList()
)
