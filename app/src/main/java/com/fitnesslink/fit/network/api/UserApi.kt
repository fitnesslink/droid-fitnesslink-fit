package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.api.FLUser
import com.fitnesslink.fit.model.api.UserPreference
import com.fitnesslink.fit.model.api.UserSummary
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

    /** Full-body update. The server answers 204 No Content — callers re-read [getMe] afterwards. */
    @PUT("users/{id}")
    suspend fun update(@Path("id") id: String, @Body user: FLUser)

    @PUT("users/{id}/preferences")
    suspend fun updatePreferences(@Path("id") id: String, @Body prefs: UserPreference)

    /**
     * Legacy. iOS never calls this; the profile photo flow is `POST media`
     * (container `photos`) followed by a full-body [update] carrying
     * `profileImageId`. Kept only so nothing else breaks at compile time.
     */
    @Deprecated("Use MediaApi.upload(container = \"photos\") + UserApi.update with profileImageId")
    @Multipart
    @POST("users/me/profile-photo")
    suspend fun uploadProfilePhoto(@Part file: MultipartBody.Part): FLUser

    /** FTNSSLNK-438: branded password-reset email. 202 whether or not the account exists. */
    @POST("auth/password-reset")
    suspend fun requestPasswordReset(@Body request: PasswordResetRequest)

    /** Mirrors iOS `UserService.search` — live user lookup for the share sheet. */
    @GET("users/search")
    suspend fun searchUsers(@Query("q") q: String, @Query("limit") limit: Int = 20): List<UserSummary>

    /** FTNSSLNK-285: anonymous handle check used by the signup form. */
    @GET("users/username-available")
    suspend fun usernameAvailable(@Query("username") username: String): UsernameAvailability
}
