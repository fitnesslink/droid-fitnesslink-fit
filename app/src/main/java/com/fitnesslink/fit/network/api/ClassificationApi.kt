package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.api.*
import com.fitnesslink.fit.network.dto.RestTypeDto
import com.fitnesslink.fit.network.dto.WorkoutTaskTypeDto
import retrofit2.http.GET

/** Mirrors iOS `ClassificationService`. Lookup tables are enums server-side — match by name. */
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

    /** Movement / Rest / Interval — resolved by name at workout-save time. */
    @GET("classification/workout-task-types")
    suspend fun getWorkoutTaskTypes(): List<WorkoutTaskTypeDto>

    /** "Passive" / "Active Recovery" — the `restTypeId` on rest tasks. */
    @GET("classification/rest-types")
    suspend fun getRestTypes(): List<RestTypeDto>
}
