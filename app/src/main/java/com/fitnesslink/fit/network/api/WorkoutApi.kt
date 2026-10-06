package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.network.dto.CreateWorkoutRequest
import com.fitnesslink.fit.network.dto.PagedResponse
import com.fitnesslink.fit.network.dto.ReplaceWorkoutTasksRequest
import com.fitnesslink.fit.network.dto.UpdateWorkoutRequest
import retrofit2.http.*

/** Mirrors iOS `WorkoutService`. */
interface WorkoutApi {
    @GET("workouts")
    suspend fun list(@Query("page") page: Int = 1, @Query("pageSize") pageSize: Int = 50): PagedResponse<Workout>

    /**
     * Server-filtered catalog (iOS `ContentFilterQuery`): `personalOnly ==
     * true` narrows to user-authored content and takes precedence over
     * `contributorId`; `favoritesOnly` is only sent when true.
     */
    @GET("workouts/list-view")
    suspend fun listView(
        @Query("contributorId") contributorId: String? = null,
        @Query("personalOnly") personalOnly: Boolean? = null,
        @Query("favoritesOnly") favoritesOnly: Boolean? = null
    ): List<Workout>

    @GET("workouts/{id}")
    suspend fun get(@Path("id") id: String): Workout

    /** Create (optionally with tasks). Returns the projected workout with phases. */
    @POST("workouts")
    suspend fun create(@Body request: CreateWorkoutRequest): Workout

    /** Shell-only update — 204. Use [replaceTasks] for the task list. */
    @PUT("workouts/{id}")
    suspend fun update(@Path("id") id: String, @Body request: UpdateWorkoutRequest)

    @DELETE("workouts/{id}")
    suspend fun delete(@Path("id") id: String)

    /**
     * FTNSSLNK-250: fork a library / shared-in workout into a caller-owned
     * copy (201 = the new workout, `preBuiltTemplateId` = source). 400 =
     * caller already owns the source, 404 = not visible, 409 = in-progress
     * session on the source.
     */
    @POST("workouts/{id}/fork")
    suspend fun fork(@Path("id") id: String): Workout

    /** Atomically replace the full task list; returns the projected workout. */
    @POST("workouts/{id}/tasks/replace")
    suspend fun replaceTasks(@Path("id") id: String, @Body request: ReplaceWorkoutTasksRequest): Workout
}
