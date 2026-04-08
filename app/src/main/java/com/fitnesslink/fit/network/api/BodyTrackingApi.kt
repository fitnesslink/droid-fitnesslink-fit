package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.MeasurementEntry
import com.fitnesslink.fit.model.ProgressPhotoEntry
import com.fitnesslink.fit.model.WeightEntry
import retrofit2.http.*

interface BodyTrackingApi {
    // Weight
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

    // Measurements
    @GET("measurements/me")
    suspend fun getMeasurements(): List<MeasurementEntry>

    @POST("measurements")
    suspend fun addMeasurement(@Body entry: MeasurementEntry): MeasurementEntry

    @DELETE("measurements/{id}")
    suspend fun deleteMeasurement(@Path("id") id: String)

    // Progress Photos
    @GET("progress-photos/me")
    suspend fun getProgressPhotos(): List<ProgressPhotoEntry>

    @DELETE("progress-photos/{id}")
    suspend fun deleteProgressPhoto(@Path("id") id: String)
}
