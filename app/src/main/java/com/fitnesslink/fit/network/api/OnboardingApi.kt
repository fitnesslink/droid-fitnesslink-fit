package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.onboarding.CompleteOnboardingRequest
import com.fitnesslink.fit.model.onboarding.DismissRecommendationRequest
import com.fitnesslink.fit.model.onboarding.HomeExperienceRequest
import com.fitnesslink.fit.model.onboarding.OnboardingProfileResponse
import com.fitnesslink.fit.model.onboarding.RecommendationsResponse
import com.fitnesslink.fit.model.onboarding.SaveOnboardingRequest
import retrofit2.http.*

/** Onboarding PRD v1.1 — answers, completion, and catalog recommendations (core service). */
interface OnboardingApi {
    @GET("onboarding/me")
    suspend fun getMine(): OnboardingProfileResponse

    @PUT("onboarding/me")
    suspend fun save(@Body body: SaveOnboardingRequest): OnboardingProfileResponse

    @POST("onboarding/me/complete")
    suspend fun complete(@Body body: CompleteOnboardingRequest): OnboardingProfileResponse

    /** "Dismissed" | "Graduated" — ends the first-run Home section. 204. */
    @POST("onboarding/me/home-experience")
    suspend fun endHomeExperience(@Body body: HomeExperienceRequest)

    @GET("recommendations/me")
    suspend fun recommendations(
        @Query("programLimit") programLimit: Int = 6,
        @Query("workoutLimit") workoutLimit: Int = 10
    ): RecommendationsResponse

    @POST("recommendations/dismissals")
    suspend fun dismiss(@Body body: DismissRecommendationRequest)

    @DELETE("recommendations/dismissals/{contentType}/{contentId}")
    suspend fun undoDismiss(@Path("contentType") contentType: String, @Path("contentId") contentId: String)
}
