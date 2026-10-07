package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.*
import com.fitnesslink.fit.network.dto.AchievementDefinition
import com.fitnesslink.fit.network.dto.GoalProgressRequest
import com.fitnesslink.fit.network.dto.UnlockAchievementRequest
import retrofit2.http.*

/**
 * Mirrors iOS `GoalService` / `HabitService` / `MilestoneService` /
 * `AchievementService`. Return shapes follow the controllers: every create
 * answers 201 with the new row id as a bare JSON string, every update /
 * delete answers 204 with no body.
 */
interface GoalApi {
    @GET("goals")
    suspend fun list(): List<Goal>

    @GET("goals/{id}")
    suspend fun get(@Path("id") id: String): Goal

    /** 201 → the new goal id as a bare string (the client-supplied `id` is honoured). */
    @POST("goals")
    suspend fun create(@Body goal: Goal): String

    /** 204. */
    @PUT("goals/{id}")
    suspend fun update(@Path("id") id: String, @Body goal: Goal)

    /** 204. */
    @DELETE("goals/{id}")
    suspend fun delete(@Path("id") id: String)

    /** 204. */
    @PUT("goals/{id}/progress")
    suspend fun updateProgress(@Path("id") id: String, @Body request: GoalProgressRequest)

    // Habits
    @GET("habits/goal/{goalId}")
    suspend fun getHabitsForGoal(@Path("goalId") goalId: String): List<Habit>

    @GET("habits/me")
    suspend fun getMyHabits(): List<Habit>

    /** 201 → the new habit id as a bare string. */
    @POST("habits")
    suspend fun createHabit(@Body habit: Habit): String

    /** 204. */
    @PUT("habits/{id}")
    suspend fun updateHabit(@Path("id") id: String, @Body habit: Habit)

    /** 204. */
    @DELETE("habits/{id}")
    suspend fun deleteHabit(@Path("id") id: String)

    /** 201 (log id) — body ignored. */
    @POST("habits/{id}/log")
    suspend fun logHabit(@Path("id") id: String, @Body log: HabitLog)

    @GET("habits/{id}/streak")
    suspend fun getStreak(@Path("id") id: String): Streak

    // Milestones
    @GET("milestones/goal/{goalId}")
    suspend fun getMilestones(@Path("goalId") goalId: String): List<Milestone>

    /** 201 → the new milestone id as a bare string. */
    @POST("milestones")
    suspend fun createMilestone(@Body milestone: Milestone): String

    /** 204. */
    @PUT("milestones/{id}/achieve")
    suspend fun achieveMilestone(@Path("id") id: String)

    // Achievements
    @GET("achievements/me")
    suspend fun getMyAchievements(): List<Achievement>

    /** Every achievement definition the server knows; `id` is what [unlockAchievement] posts. */
    @GET("achievements")
    suspend fun getAchievementDefinitions(): List<AchievementDefinition>

    /** `{ achievementId }` → 201 with the new user-achievement id as a bare string. Duplicates are the caller's to avoid. */
    @POST("achievements")
    suspend fun unlockAchievement(@Body request: UnlockAchievementRequest): String
}
