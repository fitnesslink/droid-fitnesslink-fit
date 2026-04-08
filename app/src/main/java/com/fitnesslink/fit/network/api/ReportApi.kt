package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.NutritionReportData
import com.fitnesslink.fit.model.WorkoutReportData
import retrofit2.http.GET
import retrofit2.http.Query

interface ReportApi {
    @GET("reports/workouts")
    suspend fun getWorkoutReport(
        @Query("since") since: String,
        @Query("to") to: String? = null
    ): WorkoutReportData

    @GET("reports/nutrition")
    suspend fun getNutritionReport(
        @Query("since") since: String,
        @Query("to") to: String? = null
    ): NutritionReportData
}
