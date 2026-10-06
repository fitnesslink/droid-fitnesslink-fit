package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.network.dto.*
import retrofit2.http.*

/** Mirrors iOS `WorkoutPhaseService` + `WorkoutPhaseTemplateService`. */
interface WorkoutPhaseApi {
    /** System + the caller's custom phases. */
    @GET("workout-phases")
    suspend fun listAvailable(): List<AvailableWorkoutPhaseDto>

    @GET("workout-phases/{id}")
    suspend fun get(@Path("id") id: String): AvailableWorkoutPhaseDto

    /** 201 = the new phase id as a bare string; 409 = duplicate name for this owner. */
    @POST("workout-phases")
    suspend fun create(@Body request: CreateWorkoutPhaseRequest): String

    /** 204. 403 = system phase, 404 = missing, 409 = duplicate name. */
    @PUT("workout-phases/{id}")
    suspend fun update(@Path("id") id: String, @Body request: UpdateWorkoutPhaseRequest)

    /** 409 = still referenced by a workout. */
    @DELETE("workout-phases/{id}")
    suspend fun delete(@Path("id") id: String)

    // Phase templates (FTNSSLNK-247) — online only, no offline queue.

    /** The caller's templates + system templates. */
    @GET("workout-phase-templates")
    suspend fun listTemplates(): List<WorkoutPhaseTemplateSummary>

    /** One template with its ordered task list (404 = missing / others'). */
    @GET("workout-phase-templates/{id}")
    suspend fun getTemplate(@Path("id") id: String): WorkoutPhaseTemplate

    /** 201 = the full template; 409 = duplicate name. */
    @POST("workout-phase-templates")
    suspend fun createTemplate(@Body request: CreateWorkoutPhaseTemplateRequest): WorkoutPhaseTemplate
}
