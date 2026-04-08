package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.api.FLUser
import com.fitnesslink.fit.model.api.UserPreference
import com.fitnesslink.fit.network.dto.CreateUserRequest
import retrofit2.http.*

interface UserApi {
    @GET("users/me")
    suspend fun getMe(): FLUser

    @GET("users/{id}")
    suspend fun getById(@Path("id") id: String): FLUser

    @POST("users")
    suspend fun create(@Body request: CreateUserRequest): FLUser

    @PUT("users/{id}")
    suspend fun update(@Path("id") id: String, @Body user: FLUser): FLUser

    @PUT("users/{id}/preferences")
    suspend fun updatePreferences(@Path("id") id: String, @Body prefs: UserPreference)
}
