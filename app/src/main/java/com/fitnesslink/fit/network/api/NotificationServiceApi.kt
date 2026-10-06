package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.GoalNotificationPreference
import com.fitnesslink.fit.model.NotificationItem
import com.fitnesslink.fit.network.dto.*
import okhttp3.ResponseBody
import retrofit2.http.*

interface NotificationServiceApi {
    @GET("notifications/me")
    suspend fun getMyNotifications(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50
    ): PaginatedResponse<NotificationItem>

    @GET("notifications/me/unread-count")
    suspend fun getUnreadCount(): UnreadCountResponse

    @PUT("notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String)

    @PUT("notifications/read-all")
    suspend fun markAllRead()

    // Device tokens (FTNSSLNK-260)

    /**
     * Registers this install's FCM token. Answers with the Guid of the new
     * DeviceToken row as a BARE JSON string — required later for
     * [refreshDeviceToken] / [unregisterDeviceToken].
     */
    @POST("device-tokens")
    suspend fun registerDeviceToken(@Body request: RegisterDeviceTokenRequest): ResponseBody

    /**
     * Rotates the FCM token stored under an existing server row. The endpoint
     * binds `[FromBody] string`, so the body is the raw token JSON-encoded as a
     * bare string (Gson serialises a Kotlin `String` body exactly that way).
     */
    @POST("device-tokens/{id}/refresh")
    suspend fun refreshDeviceToken(@Path("id") id: String, @Body token: String)

    @DELETE("device-tokens/{id}")
    suspend fun unregisterDeviceToken(@Path("id") id: String)

    // Preferences
    @GET("notification-preferences/me")
    suspend fun getPreferences(): GoalNotificationPreference

    @PUT("notification-preferences/me")
    suspend fun updatePreferences(@Body prefs: GoalNotificationPreference)
}
