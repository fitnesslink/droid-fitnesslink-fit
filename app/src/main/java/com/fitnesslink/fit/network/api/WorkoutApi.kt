package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.network.dto.PaginatedResponse
import retrofit2.http.*

interface WorkoutApi {
    @GET("workouts")
    suspend fun list(@Query("page") page: Int = 1, @Query("pageSize") pageSize: Int = 50): PaginatedResponse<Workout>

    @GET("workouts/list-view")
    suspend fun listView(): List<Workout>

    @GET("workouts/{id}")
    suspend fun get(@Path("id") id: String): Workout

    @POST("workouts")
    suspend fun create(@Body workout: Workout): Workout

    @PUT("workouts/{id}")
    suspend fun update(@Path("id") id: String, @Body workout: Workout): Workout

    @DELETE("workouts/{id}")
    suspend fun delete(@Path("id") id: String)
}
