package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.*
import com.fitnesslink.fit.network.dto.GoalProgressRequest
import retrofit2.http.*

interface GoalApi {
    @GET("goals")
    suspend fun list(): List<Goal>

    @GET("goals/{id}")
    suspend fun get(@Path("id") id: String): Goal

    @POST("goals")
    suspend fun create(@Body goal: Goal): Goal

    @PUT("goals/{id}")
    suspend fun update(@Path("id") id: String, @Body goal: Goal): Goal

    @DELETE("goals/{id}")
    suspend fun delete(@Path("id") id: String)

    @PUT("goals/{id}/progress")
    suspend fun updateProgress(@Path("id") id: String, @Body request: GoalProgressRequest)

    // Habits
    @GET("habits/goal/{goalId}")
    suspend fun getHabitsForGoal(@Path("goalId") goalId: String): List<Habit>

    @GET("habits/me")
    suspend fun getMyHabits(): List<Habit>

    @POST("habits")
    suspend fun createHabit(@Body habit: Habit): Habit

    @PUT("habits/{id}")
    suspend fun updateHabit(@Path("id") id: String, @Body habit: Habit): Habit

    @DELETE("habits/{id}")
    suspend fun deleteHabit(@Path("id") id: String)

    @POST("habits/{id}/log")
    suspend fun logHabit(@Path("id") id: String, @Body log: HabitLog)

    @GET("habits/{id}/streak")
    suspend fun getStreak(@Path("id") id: String): Streak

    // Milestones
    @GET("milestones/goal/{goalId}")
    suspend fun getMilestones(@Path("goalId") goalId: String): List<Milestone>

    @POST("milestones")
    suspend fun createMilestone(@Body milestone: Milestone): Milestone

    @PUT("milestones/{id}/achieve")
    suspend fun achieveMilestone(@Path("id") id: String)

    // Achievements
    @GET("achievements/me")
    suspend fun getMyAchievements(): List<Achievement>

    @POST("achievements")
    suspend fun unlockAchievement(@Body achievement: Achievement)
}
