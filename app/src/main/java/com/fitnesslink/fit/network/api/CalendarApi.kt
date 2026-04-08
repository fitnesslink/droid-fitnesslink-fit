package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.FitnessContent
import retrofit2.http.*

interface CalendarApi {
    @GET("calendar/me")
    suspend fun getEntries(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): List<FitnessContent>

    @POST("calendar")
    suspend fun create(@Body entry: FitnessContent): FitnessContent

    @PUT("calendar/{id}")
    suspend fun update(@Path("id") id: String, @Body entry: FitnessContent): FitnessContent

    @DELETE("calendar/{id}")
    suspend fun delete(@Path("id") id: String)
}
