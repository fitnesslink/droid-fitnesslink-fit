package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.network.dto.SyncPullResponse
import com.fitnesslink.fit.network.dto.SyncPushRequest
import com.fitnesslink.fit.network.dto.SyncPushResponse
import retrofit2.http.*

interface SyncApi {
    @GET("sync/pull")
    suspend fun pull(
        @Query("since") since: String? = null,
        @Query("domains") domains: String? = null,
        @Query("limit") limit: Int = 500
    ): SyncPullResponse

    @POST("sync/push")
    suspend fun push(@Body request: SyncPushRequest): SyncPushResponse
}
