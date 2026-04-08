package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.api.*
import retrofit2.http.GET

interface ClassificationApi {
    @GET("classification/anatomy")
    suspend fun getAnatomy(): List<Anatomy>

    @GET("classification/equipment")
    suspend fun getEquipment(): List<Equipment>

    @GET("classification/training-levels")
    suspend fun getTrainingLevels(): List<FLTrainingLevel>

    @GET("classification/content-statuses")
    suspend fun getContentStatuses(): List<ContentStatus>

    @GET("classification/rpe-scales")
    suspend fun getRpeScales(): List<Rpe>
}
