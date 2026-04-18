package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.Program
import com.fitnesslink.fit.model.api.ProgramSchedule
import com.fitnesslink.fit.network.dto.PaginatedResponse
import retrofit2.http.*

interface ProgramApi {
    @GET("programs")
    suspend fun list(@Query("page") page: Int = 1, @Query("pageSize") pageSize: Int = 50): PaginatedResponse<Program>

    @GET("programs/list-view")
    suspend fun listView(): List<Program>

    @GET("programs/{id}")
    suspend fun get(@Path("id") id: String): Program

    @POST("programs")
    suspend fun create(@Body program: Program): Program

    @PUT("programs/{id}")
    suspend fun update(@Path("id") id: String, @Body program: Program): Program

    @DELETE("programs/{id}")
    suspend fun delete(@Path("id") id: String)

    @GET("programs/{id}/weekly-workouts")
    suspend fun weeklyWorkouts(@Path("id") programId: String): List<ProgramSchedule>

    @POST("programs/{id}/weekly-workouts")
    suspend fun addWeeklyWorkout(@Path("id") programId: String, @Body request: AddWeeklyWorkoutRequest)
}

data class AddWeeklyWorkoutRequest(
    val workoutId: String,
    val weekNumber: Int,
    val dayNumber: Int
)
