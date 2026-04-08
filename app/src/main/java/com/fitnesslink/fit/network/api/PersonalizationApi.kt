package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.Personalization
import com.fitnesslink.fit.model.api.UserPersonalization
import com.fitnesslink.fit.network.dto.UserPersonalizationRequest
import retrofit2.http.*

interface PersonalizationApi {
    @GET("personalization")
    suspend fun getAll(): List<Personalization>

    @GET("personalization/me")
    suspend fun getMySelections(): List<UserPersonalization>

    @POST("personalization/me")
    suspend fun saveSelections(@Body selections: List<UserPersonalizationRequest>)
}
