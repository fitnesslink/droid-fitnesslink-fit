package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.WorkoutSession
import com.fitnesslink.fit.network.dto.*
import retrofit2.http.*

interface SessionApi {
    @GET("sessions/me")
    suspend fun getMySessions(): List<WorkoutSession>

    @GET("sessions/{id}")
    suspend fun get(@Path("id") id: String): WorkoutSession

    @POST("sessions")
    suspend fun start(@Body request: StartSessionRequest): WorkoutSession

    @POST("sessions/{id}/complete")
    suspend fun complete(@Path("id") id: String, @Body request: CompleteSessionRequest)

    @POST("sessions/{id}/history")
    suspend fun logHistory(@Path("id") id: String, @Body request: LogHistoryRequest)
}
