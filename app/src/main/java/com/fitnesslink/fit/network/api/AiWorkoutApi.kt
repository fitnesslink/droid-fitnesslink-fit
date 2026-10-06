package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.network.dto.GenerateWorkoutRequest
import com.fitnesslink.fit.network.dto.GenerateWorkoutResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Mirrors iOS `AIWorkoutService`. Built on `ApiClient.longRunningRetrofit`
 * (180 s read timeout): Gemini drafts run 20–30 s in prod.
 *
 * The server returns the same [GenerateWorkoutResponse] envelope on
 * handled failures (400 safety reject, 502 model down, 504 timeout); those
 * surface as `ApiError.ServerError` — call
 * `decodeBody(ApiClient.gson, GenerateWorkoutResponse::class.java)` to
 * recover `errorReasonCode` / `errorDetail`. Nothing is persisted; saving
 * goes through `WorkoutApi.create` once the user approves the draft.
 */
interface AiWorkoutApi {
    @POST("workouts/generate-from-prompt")
    suspend fun generate(@Body request: GenerateWorkoutRequest): GenerateWorkoutResponse
}
