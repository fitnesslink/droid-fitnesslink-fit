package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.Program
import com.fitnesslink.fit.model.ProgramWriteRequest
import com.fitnesslink.fit.model.api.ProgramSchedule
import com.fitnesslink.fit.network.dto.PagedResponse
import com.fitnesslink.fit.network.dto.ProgramEnrollmentDto
import com.fitnesslink.fit.network.dto.ProgramEnrollmentRecord
import com.fitnesslink.fit.network.dto.ProgramEnrollmentRemovalRequest
import retrofit2.http.*

/** Mirrors iOS `ProgramService` + `ProgramEnrollmentService` + `UserProgramStore.syncFromServer`. */
interface ProgramApi {
    @GET("programs")
    suspend fun list(@Query("page") page: Int = 1, @Query("pageSize") pageSize: Int = 50): PagedResponse<Program>

    /** Same filter params as `WorkoutApi.listView` (iOS `ContentFilterQuery`). */
    @GET("programs/list-view")
    suspend fun listView(
        @Query("contributorId") contributorId: String? = null,
        @Query("personalOnly") personalOnly: Boolean? = null,
        @Query("favoritesOnly") favoritesOnly: Boolean? = null
    ): List<Program>

    @GET("programs/{id}")
    suspend fun get(@Path("id") id: String): Program

    @POST("programs")
    suspend fun create(@Body request: ProgramWriteRequest): Program

    @PUT("programs/{id}")
    suspend fun update(@Path("id") id: String, @Body request: ProgramWriteRequest): Program

    @DELETE("programs/{id}")
    suspend fun delete(@Path("id") id: String)

    @GET("programs/{id}/weekly-workouts")
    suspend fun weeklyWorkouts(@Path("id") programId: String): List<ProgramSchedule>

    /** 204. */
    @POST("programs/{id}/weekly-workouts")
    suspend fun addWeeklyWorkout(@Path("id") programId: String, @Body request: AddWeeklyWorkoutRequest)

    // Program enrollment history (contributor-payout critical, FTNSSLNK-164/166).
    // Writes are append-only: queue durably and replay until 2xx.

    /** Record an "add to calendar" enrollment. 204. */
    @POST("program-enrollments")
    suspend fun recordEnrollment(@Body record: ProgramEnrollmentRecord)

    /** Flip one enrollment to removed (never deletes). 204. */
    @POST("program-enrollments/{id}/removal")
    suspend fun recordEnrollmentRemoval(@Path("id") enrollmentId: String, @Body request: ProgramEnrollmentRemovalRequest)

    /** The caller's enrollments (active + removed) for fresh-install / second-device hydration. */
    @GET("program-enrollments/me")
    suspend fun myEnrollments(): List<ProgramEnrollmentDto>
}

data class AddWeeklyWorkoutRequest(
    val workoutId: String,
    val weekNumber: Int,
    val dayNumber: Int
)
