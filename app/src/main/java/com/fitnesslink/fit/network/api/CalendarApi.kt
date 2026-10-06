package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.network.dto.CalendarEntryDto
import com.fitnesslink.fit.network.dto.CreateCalendarEntryRequest
import com.fitnesslink.fit.network.dto.UpdateCalendarEntryRequest
import retrofit2.http.*

/** Mirrors iOS `CalendarService`. Dates in the query are ISO-8601 instants. */
interface CalendarApi {
    @GET("calendar/me")
    suspend fun getEntries(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): List<CalendarEntryDto>

    /** 204. The client owns the entry id. */
    @POST("calendar")
    suspend fun create(@Body request: CreateCalendarEntryRequest)

    /** 204. Null fields are left unchanged — a reschedule sends only `scheduledDate`. */
    @PUT("calendar/{id}")
    suspend fun update(@Path("id") id: String, @Body request: UpdateCalendarEntryRequest)

    @DELETE("calendar/{id}")
    suspend fun delete(@Path("id") id: String)
}
