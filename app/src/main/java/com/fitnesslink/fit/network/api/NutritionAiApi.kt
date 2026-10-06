package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.network.dto.*
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * The Nutrition module's AI endpoints, built on
 * `ApiClient.longRunningRetrofit` (180 s): each is a single structured
 * model response that can exceed the default 30 s.
 *
 * Every call returns its envelope on success AND on handled failures
 * (400 safety / empty, 422 implausible / allergen, 429 per-user hourly
 * quota, 502 model down, 504 timeout). The failures surface as
 * `ApiError.ServerError`; recover the envelope with
 * `decodeBody(ApiClient.gson, <Response>::class.java)` to read
 * `errorReasonCode` / `errorDetail`. None of these persist anything — the
 * confirmed result goes through the normal create paths.
 */
interface NutritionAiApi {
    /** Lookup cascade (log → library → AI). Known values are locked (FTNSSLNK-447). */
    @POST("foods/estimate-macros")
    suspend fun estimateMacros(@Body request: EstimateMacrosRequest): EstimateMacrosResponse

    /** FTNSSLNK-450 "describe a meal": one line → several estimate-shaped items. */
    @POST("foods/parse-meal")
    suspend fun parseMeal(@Body request: ParseMealRequest): ParseMealResponse

    /** FTNSSLNK-488 "snap a meal": photo → identified foods with portion guesses, no macros. */
    @POST("foods/identify-meal-photo")
    suspend fun identifyMealPhoto(@Body request: IdentifyMealPhotoRequest): IdentifyMealPhotoResponse

    /** AI meal suggestions for one (day, mealType) slot. */
    @POST("meal-slots/generate-suggestion")
    suspend fun generateMealSuggestions(@Body request: GenerateMealSuggestionRequest): GenerateMealSuggestionResponse

    /** "Meal Plan Ideas": an AI-drafted Sunday-anchored week (review-only). */
    @POST("meal-slots/generate-week")
    suspend fun generateMealPlanWeek(@Body request: GenerateMealPlanWeekRequest): GenerateMealPlanWeekResponse
}
