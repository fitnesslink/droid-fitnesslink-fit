package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.GoalNotificationPreference
import com.fitnesslink.fit.model.NotificationItem
import com.fitnesslink.fit.network.dto.*
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

    // Device tokens
    @POST("device-tokens")
    suspend fun registerDeviceToken(@Body request: RegisterDeviceTokenRequest)

    @DELETE("device-tokens/{id}")
    suspend fun unregisterDeviceToken(@Path("id") id: String)

    // Preferences
    @GET("notification-preferences/me")
    suspend fun getPreferences(): GoalNotificationPreference

    @PUT("notification-preferences/me")
    suspend fun updatePreferences(@Body prefs: GoalNotificationPreference)
}
