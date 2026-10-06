package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.HydrationGoal
import com.fitnesslink.fit.model.NutritionGoal
import com.fitnesslink.fit.model.WaterIntakeEntry
import com.fitnesslink.fit.network.dto.*
import retrofit2.http.*

/**
 * Mirrors iOS `NutritionService` + `HydrationService` (base URL
 * `ApiService.NUTRITION`). The AI generators live in [NutritionAiApi] on
 * the long-running client.
 *
 * Conventions: calendar-day params are `yyyy-MM-dd`; `tz` is the IANA
 * zone id (`TimeZone.getDefault().id`) pinning the local day boundary;
 * mutating DELETE / toggle calls carry a `rowVersion` body (FB-102) and
 * 409 on a stale token.
 */
interface NutritionApi {
    // MARK: Food entries

    @GET("food-entries/me")
    suspend fun getFoodEntries(@Query("date") date: String? = null): List<FoodEntry>

    @GET("food-entries/me/all")
    suspend fun getAllFoodEntries(@Query("page") page: Int = 1, @Query("pageSize") pageSize: Int = 50): PagedResponse<FoodEntry>

    /** The user's own previously-logged foods, deduped by name (most recent macros). Empty query = most recent. */
    @GET("food-entries/me/foods")
    suspend fun searchLoggedFoods(@Query("query") query: String? = null, @Query("limit") limit: Int = 50): List<FoodEntry>

    @POST("food-entries")
    suspend fun addFoodEntry(@Body entry: FoodEntry): FoodEntry

    /** Atomically create 1–50 entries (FTNSSLNK-214). Returns the created rows. */
    @POST("food-entries/batch")
    suspend fun batchAddFoodEntries(@Body request: BatchCreateFoodEntriesRequest): List<FoodEntry>

    @PUT("food-entries/{id}")
    suspend fun updateFoodEntry(@Path("id") id: String, @Body entry: FoodEntry): FoodEntry

    @DELETE("food-entries/{id}")
    suspend fun deleteFoodEntry(@Path("id") id: String)

    // MARK: Quick log (FTNSSLNK-213)

    /** Always exactly 4 sources (Breakfast, Lunch, Dinner, Snack). */
    @GET("quick-log/sources")
    suspend fun getQuickLogSources(@Query("date") date: String, @Query("tz") tz: String): QuickLogSourcesResponse

    /** 201 = the created FoodEntry rows. */
    @POST("quick-log/copy")
    suspend fun quickLogCopy(@Body request: QuickLogCopyRequest): QuickLogCopyResponse

    // MARK: Recent meals (FTNSSLNK-214)

    @GET("meals/recent")
    suspend fun getRecentMeals(
        @Query("tz") tz: String,
        @Query("mealType") mealType: String? = null,
        @Query("limit") limit: Int? = null
    ): RecentMealsResponse

    // MARK: Favorites (FTNSSLNK-215) — star/unstar are idempotent 204s

    /** `kind` = "food" | "meal" | null (both). Newest first. */
    @GET("favorites/me")
    suspend fun getNutritionFavorites(@Query("kind") kind: String? = null): List<NutritionFavorite>

    @POST("favorites/foods/{foodId}")
    suspend fun favoriteFood(@Path("foodId") foodId: String, @Body body: EmptyBody = EmptyBody)

    @DELETE("favorites/foods/{foodId}")
    suspend fun unfavoriteFood(@Path("foodId") foodId: String)

    /** "Meals" are meal-plan templates. */
    @POST("favorites/meals/{mealTemplateId}")
    suspend fun favoriteMeal(@Path("mealTemplateId") mealTemplateId: String, @Body body: EmptyBody = EmptyBody)

    @DELETE("favorites/meals/{mealTemplateId}")
    suspend fun unfavoriteMeal(@Path("mealTemplateId") mealTemplateId: String)

    // MARK: Nutrition goals (legacy flat goal)

    @GET("nutrition-goals/me")
    suspend fun getGoal(): NutritionGoal

    /** 204. */
    @POST("nutrition-goals")
    suspend fun upsertGoal(@Body goal: NutritionGoal)

    // MARK: Nutrition profile + target engine (FTNSSLNK-190/191)

    /** Profile + currently-effective targets. 404 = onboarding not done. */
    @GET("profile/me")
    suspend fun getNutritionProfile(): NutritionProfileWithTargets

    /** Create (onboarding). 409 = a profile already exists. */
    @POST("profile")
    suspend fun createNutritionProfile(@Body request: CreateNutritionProfileRequest): NutritionProfileWithTargets

    /** Partial update + recalculate (rowVersion-guarded). */
    @PUT("profile")
    suspend fun updateNutritionProfile(@Body request: UpdateNutritionProfileRequest): NutritionProfileWithTargets

    /** Remove the profile (rowVersion body). */
    @HTTP(method = "DELETE", path = "profile", hasBody = true)
    suspend fun deleteNutritionProfile(@Body body: RowVersionBody)

    @GET("profile/targets")
    suspend fun getCurrentNutritionTargets(): NutritionTargets

    /** Preview a calculation WITHOUT persisting (onboarding summary). */
    @POST("profile/calculate")
    suspend fun calculateNutritionTargets(@Body request: CreateNutritionProfileRequest): NutritionTargets

    /** Manual override — provenance becomes "Manual". */
    @POST("profile/targets/override")
    suspend fun overrideNutritionTargets(@Body request: OverrideNutritionTargetsRequest): NutritionTargets

    /** Goal history, newest first. */
    @GET("profile/goals")
    suspend fun getNutritionGoalHistory(): List<NutritionGoalHistoryEntry>

    // MARK: Summary (FTNSSLNK-187/191/193/196) — same route, shape switches on date vs from+to

    @GET("summary")
    suspend fun getDailySummary(@Query("date") date: String, @Query("tz") tz: String): NutritionDailySummary

    /** One rollup per local day in the inclusive range (max 92 days). */
    @GET("summary")
    suspend fun getSummaryRange(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("tz") tz: String
    ): NutritionSummaryRange

    // MARK: Analytics (FTNSSLNK-212)

    /** `period` = daily | weekly | monthly; max 366 days; `data`/`hasNextPage` page (max 200). */
    @GET("analytics/trends")
    suspend fun getNutritionTrends(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("period") period: String,
        @Query("tz") tz: String,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 100
    ): NutritionTrendsResponse

    @GET("analytics/consistency")
    suspend fun getNutritionConsistency(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("tz") tz: String
    ): NutritionConsistencyResponse

    @GET("analytics/weight-progress")
    suspend fun getWeightProgress(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("tz") tz: String
    ): WeightProgressResponse

    // MARK: Displayed nutrients preference (FTNSSLNK-217)

    @GET("preferences/displayed-nutrients")
    suspend fun getDisplayedNutrients(): DisplayedNutrientsPreference

    /** FULL replacement; unknown names → 400. */
    @PUT("preferences/displayed-nutrients")
    suspend fun updateDisplayedNutrients(@Body request: UpdateDisplayedNutrientsRequest): DisplayedNutrientsPreference

    // MARK: Meal slots (date-anchored, Sunday weeks)

    /** Flat slots for an inclusive `yyyy-MM-dd` range (no itemized foods). No range = every slot. */
    @GET("meal-slots/me")
    suspend fun getMealSlots(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): List<MealSlotDto>

    /** A Sunday-anchored week WITH itemized foods + per-day rollups (`from` Sunday, `to` = from + 6). */
    @GET("meal-slots/me")
    suspend fun getMealSlotWeek(@Query("from") from: String, @Query("to") to: String): MealSlotWeekDto

    /** One slot with foods + current rowVersion — re-read before a foods-replace PUT. */
    @GET("meal-slots/{id}")
    suspend fun getMealSlot(@Path("id") id: String): MealSlotDetailDto

    /** 201 = bare new slot id (the client-supplied id is kept). */
    @POST("meal-slots")
    suspend fun createMealSlot(@Body request: CreateMealSlotRequest): String

    /** 204. */
    @PUT("meal-slots/{id}")
    suspend fun updateMealSlot(@Path("id") id: String, @Body request: UpdateMealSlotRequest)

    @HTTP(method = "DELETE", path = "meal-slots/{id}", hasBody = true)
    suspend fun deleteMealSlot(@Path("id") id: String, @Body body: RowVersionBody)

    /** Replace-set the itemized foods on an existing slot (FTNSSLNK-205). 204. */
    @PUT("meal-slots/{id}/foods")
    suspend fun replaceMealSlotFoods(@Path("id") id: String, @Body request: ReplaceMealSlotFoodsRequest)

    /** Copy-last-week (FTNSSLNK-204). Both anchors must be Sundays. */
    @POST("meal-slots/me/copy-week")
    suspend fun copyMealSlotWeek(@Body request: CopyMealSlotWeekRequest): CopyMealSlotWeekResult

    /** Copy one day onto 1–31 target days, atomically (FTNSSLNK-218). 400 = validation. */
    @POST("meal-slots/me/copy-day")
    suspend fun copyMealSlotDay(@Body request: CopyMealSlotDayRequest): CopyMealSlotDayResult

    /** Approve a reviewed AI week draft onto the planner. 400 = validation, 404 = not the caller's generation. */
    @POST("meal-slots/me/apply-week")
    suspend fun applyMealPlanWeek(@Body request: ApplyMealPlanWeekRequest): ApplyMealPlanWeekResult

    // MARK: Meal templates (FTNSSLNK-219)

    /** The caller's templates + system templates, newest first. */
    @GET("meal-templates/me")
    suspend fun getMealTemplates(): List<MealTemplate>

    @GET("meal-templates/{id}")
    suspend fun getMealTemplate(@Path("id") id: String): MealTemplate

    /** 201 = bare new template id. */
    @POST("meal-templates")
    suspend fun createMealTemplate(@Body request: CreateMealTemplateRequest): String

    /** 204; 409 = stale rowVersion. */
    @PUT("meal-templates/{id}")
    suspend fun updateMealTemplate(@Path("id") id: String, @Body request: UpdateMealTemplateRequest)

    @HTTP(method = "DELETE", path = "meal-templates/{id}", hasBody = true)
    suspend fun deleteMealTemplate(@Path("id") id: String, @Body body: RowVersionBody)

    // MARK: Grocery items

    /** The default list's items (legacy single-list read). */
    @GET("grocery/me")
    suspend fun getGroceryItems(): List<GroceryItemDto>

    /** 201 = bare item id (the client-supplied one). */
    @POST("grocery")
    suspend fun addGroceryItem(@Body request: CreateGroceryItemRequest): String

    /** 204. */
    @PUT("grocery/{id}")
    suspend fun updateGroceryItem(@Path("id") id: String, @Body request: UpdateGroceryItemRequest)

    /** 204. */
    @PUT("grocery/{id}/toggle")
    suspend fun toggleGroceryItem(@Path("id") id: String, @Body body: RowVersionBody)

    @HTTP(method = "DELETE", path = "grocery/{id}", hasBody = true)
    suspend fun deleteGroceryItem(@Path("id") id: String, @Body body: RowVersionBody)

    /** Range-based generation into a NEW list (FTNSSLNK-210); returns the list detail. 400 = nothing planned. */
    @POST("grocery/generate")
    suspend fun generateGroceryList(@Body request: GenerateGroceryListRequest): GroceryListDto

    // MARK: Grocery lists (FTNSSLNK-208)

    @GET("grocery/lists")
    suspend fun getGroceryLists(@Query("includeArchived") includeArchived: Boolean = false): List<GroceryListDto>

    /** One list plus its items (server-ordered by sortOrder). */
    @GET("grocery/lists/{id}")
    suspend fun getGroceryList(@Path("id") id: String): GroceryListDto

    @POST("grocery/lists")
    suspend fun createGroceryList(@Body request: CreateGroceryListRequest): GroceryListDto

    /** 204; 409 = stale rowVersion. */
    @PUT("grocery/lists/{id}")
    suspend fun updateGroceryList(@Path("id") id: String, @Body request: UpdateGroceryListRequest)

    /** 400 when deleting the default list. */
    @HTTP(method = "DELETE", path = "grocery/lists/{id}", hasBody = true)
    suspend fun deleteGroceryList(@Path("id") id: String, @Body body: RowVersionBody)

    /** Items copied unchecked. */
    @POST("grocery/lists/{id}/duplicate")
    suspend fun duplicateGroceryList(@Path("id") id: String, @Body request: DuplicateGroceryListRequest): GroceryListDto

    /** 204. */
    @PUT("grocery/lists/{id}/reorder")
    suspend fun reorderGroceryItems(@Path("id") id: String, @Body request: ReorderGroceryItemsRequest)

    // MARK: Smart grocery (FTNSSLNK-220)

    /** READ-ONLY dry run: category suggestion + duplicate match. */
    @POST("grocery/analyze")
    suspend fun analyzeGroceryItem(@Body request: AnalyzeGroceryItemRequest): GroceryAnalyzeResponse

    /** Fold an incoming amount into an existing item (also unchecks). 409 = stale. */
    @POST("grocery/{id}/merge")
    suspend fun mergeGroceryItem(@Path("id") id: String, @Body request: MergeGroceryItemRequest): GroceryItemDto

    /** "You usually buy" (max 20). `listId` null = the default list. */
    @GET("grocery/staples")
    suspend fun getGroceryStaples(@Query("listId") listId: String? = null): List<GroceryStaple>

    // MARK: Foods

    @GET("foods/search")
    suspend fun searchFood(@Query("query") query: String): List<FoodSearchResult>

    /** Same DTO as a search result, portions included. */
    @GET("foods/{id}")
    suspend fun getFood(@Path("id") id: String): FoodSearchResult

    /** Authoritative (unit, quantity) → nutrition. 404 unknown food, 422 unit not resolvable. */
    @GET("foods/{id}/convert")
    suspend fun convertPortion(
        @Path("id") foodId: String,
        @Query("unit") unit: String,
        @Query("quantity") quantity: Double
    ): FoodPortionConversionResponse

    // MARK: Barcode (FTNSSLNK-198)

    /** Catalog match for a UPC/EAN; 404 (`ApiError.NotFound`) = fall back to OpenFoodFacts. */
    @GET("barcode/{barcode}")
    suspend fun lookupBarcode(@Path("barcode") barcode: String): BarcodeMatchDto

    // MARK: Water entries + hydration goals

    @GET("water-entries/me")
    suspend fun getWaterEntries(@Query("date") date: String? = null): List<WaterIntakeEntry>

    @POST("water-entries")
    suspend fun addWaterEntry(@Body entry: WaterIntakeEntry): WaterIntakeEntry

    @PUT("water-entries/{id}")
    suspend fun updateWaterEntry(@Path("id") id: String, @Body entry: WaterIntakeEntry): WaterIntakeEntry

    @DELETE("water-entries/{id}")
    suspend fun deleteWaterEntry(@Path("id") id: String)

    /** Distinct recent custom amounts, newest first (FTNSSLNK-221) — the quick chips. */
    @GET("water-entries/recent-amounts")
    suspend fun getRecentWaterAmounts(@Query("limit") limit: Int = 5): List<RecentWaterAmount>

    @GET("hydration-goals/me")
    suspend fun getHydrationGoal(): HydrationGoal

    /** 204. The goal upsert also accepts `preferredUnit`. */
    @POST("hydration-goals")
    suspend fun upsertHydrationGoal(@Body goal: HydrationGoal)

    /** Set (or null-clear) the preferred display unit. 204; 404 when no goal row exists yet. */
    @PUT("hydration-goals/me/preferred-unit")
    suspend fun setPreferredWaterUnit(@Body request: PreferredWaterUnitRequest)
}
