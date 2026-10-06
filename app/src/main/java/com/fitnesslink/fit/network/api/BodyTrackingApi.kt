package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.WeightEntry
import com.fitnesslink.fit.network.dto.BodyMeasurementDto
import com.fitnesslink.fit.network.dto.CreateMeasurementRequest
import com.fitnesslink.fit.network.dto.CreateProgressPhotoRequest
import com.fitnesslink.fit.network.dto.ProgressPhotoDto
import retrofit2.http.*

/** Mirrors iOS `BodyTrackingService`. */
interface BodyTrackingApi {
    // Weight — the model is the wire shape on both platforms.
    @GET("weight/me")
    suspend fun getWeightEntries(
        @Query("since") since: String? = null,
        @Query("to") to: String? = null
    ): List<WeightEntry>

    @POST("weight")
    suspend fun addWeightEntry(@Body entry: WeightEntry): WeightEntry

    @PUT("weight/{id}")
    suspend fun updateWeightEntry(@Path("id") id: String, @Body entry: WeightEntry): WeightEntry

    @DELETE("weight/{id}")
    suspend fun deleteWeightEntry(@Path("id") id: String)

    // Measurements — ONE server row per (anatomy, value). A multi-part
    // entry fans out N POSTs; reads group rows by `logDate` day.
    @GET("measurements/me")
    suspend fun getMeasurements(): List<BodyMeasurementDto>

    /** 204. */
    @POST("measurements")
    suspend fun addMeasurement(@Body request: CreateMeasurementRequest)

    @DELETE("measurements/{id}")
    suspend fun deleteMeasurement(@Path("id") id: String)

    // Progress photos — one image per row; upload bytes to `POST /media`
    // first (MediaApi), then create the row with the returned media id.
    @GET("progress-photos/me")
    suspend fun getProgressPhotos(): List<ProgressPhotoDto>

    /** Returns the new row id as a bare JSON string. */
    @POST("progress-photos")
    suspend fun createProgressPhoto(@Body request: CreateProgressPhotoRequest): String

    @DELETE("progress-photos/{id}")
    suspend fun deleteProgressPhoto(@Path("id") id: String)
}
