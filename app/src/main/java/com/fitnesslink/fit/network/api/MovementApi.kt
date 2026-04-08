package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.api.Movement
import com.fitnesslink.fit.network.dto.PaginatedResponse
import retrofit2.http.*

interface MovementApi {
    @GET("movements")
    suspend fun list(@Query("page") page: Int = 1, @Query("pageSize") pageSize: Int = 100): PaginatedResponse<Movement>

    @GET("movements/list-view")
    suspend fun listView(): List<Movement>

    @GET("movements/{id}")
    suspend fun get(@Path("id") id: String): Movement

    @POST("movements")
    suspend fun create(@Body movement: Movement): Movement

    @PUT("movements/{id}")
    suspend fun update(@Path("id") id: String, @Body movement: Movement): Movement

    @DELETE("movements/{id}")
    suspend fun delete(@Path("id") id: String)
}
