package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.*
import com.fitnesslink.fit.network.dto.PaginatedResponse
import retrofit2.http.*

interface NutritionApi {
    // Food Entries
    @GET("food-entries/me")
    suspend fun getFoodEntries(@Query("date") date: String? = null): List<FoodEntry>

    @GET("food-entries/me/all")
    suspend fun getAllFoodEntries(@Query("page") page: Int = 1, @Query("pageSize") pageSize: Int = 50): PaginatedResponse<FoodEntry>

    @POST("food-entries")
    suspend fun addFoodEntry(@Body entry: FoodEntry): FoodEntry

    @PUT("food-entries/{id}")
    suspend fun updateFoodEntry(@Path("id") id: String, @Body entry: FoodEntry): FoodEntry

    @DELETE("food-entries/{id}")
    suspend fun deleteFoodEntry(@Path("id") id: String)

    // Nutrition Goals
    @GET("nutrition-goals/me")
    suspend fun getGoal(): NutritionGoal

    @POST("nutrition-goals")
    suspend fun upsertGoal(@Body goal: NutritionGoal)

    // Meal Slots
    @GET("meal-slots/me")
    suspend fun getMealSlots(@Query("day") day: String? = null): List<MealSlot>

    @POST("meal-slots")
    suspend fun saveMealSlot(@Body slot: MealSlot): MealSlot

    @PUT("meal-slots/{id}")
    suspend fun updateMealSlot(@Path("id") id: String, @Body slot: MealSlot): MealSlot

    @DELETE("meal-slots/{id}")
    suspend fun deleteMealSlot(@Path("id") id: String)

    // Grocery
    @GET("grocery/me")
    suspend fun getGroceryItems(): List<GroceryItem>

    @POST("grocery")
    suspend fun addGroceryItem(@Body item: GroceryItem): GroceryItem

    @POST("grocery/generate")
    suspend fun generateGroceryList(): List<GroceryItem>

    @PUT("grocery/{id}/toggle")
    suspend fun toggleGroceryItem(@Path("id") id: String)

    @DELETE("grocery/{id}")
    suspend fun deleteGroceryItem(@Path("id") id: String)

    // Barcode
    @GET("barcode/{barcode}")
    suspend fun lookupBarcode(@Path("barcode") barcode: String): BarcodeProduct
}
