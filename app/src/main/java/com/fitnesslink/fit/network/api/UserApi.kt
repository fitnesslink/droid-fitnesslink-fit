package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.api.FLUser
import com.fitnesslink.fit.model.api.UserPreference
import com.fitnesslink.fit.network.dto.CreateUserBody
import com.fitnesslink.fit.network.dto.PasswordResetRequest
import com.fitnesslink.fit.network.dto.UsernameAvailability
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.*

interface UserApi {
    @GET("users/me")
    suspend fun getMe(): FLUser

    @GET("users/{id}")
    suspend fun getById(@Path("id") id: String): FLUser

    /**
     * Creates (or adopts the middleware-provisioned shell row for) the platform
     * user. Answers 201 with the new user's Guid as a BARE JSON string — not a
     * user object — so the body is read as text and the caller follows up with
     * [getMe]. See `AuthManager.createPlatformUser`.
     */
    @POST("users")
    suspend fun create(@Body request: CreateUserBody): ResponseBody

    @PUT("users/{id}")
    suspend fun update(@Path("id") id: String, @Body user: FLUser): FLUser

    @PUT("users/{id}/preferences")
    suspend fun updatePreferences(@Path("id") id: String, @Body prefs: UserPreference)

    @Multipart
    @POST("users/me/profile-photo")
    suspend fun uploadProfilePhoto(@Part file: MultipartBody.Part): FLUser

    /** FTNSSLNK-438: branded password-reset email. 202 whether or not the account exists. */
    @POST("auth/password-reset")
    suspend fun requestPasswordReset(@Body request: PasswordResetRequest)

    /** FTNSSLNK-285: anonymous handle check used by the signup form. */
    @GET("users/username-available")
    suspend fun usernameAvailable(@Query("username") username: String): UsernameAvailability
}
