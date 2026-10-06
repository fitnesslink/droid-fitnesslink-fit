package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.network.dto.SyncPullResponse
import com.fitnesslink.fit.network.dto.SyncPushRequest
import com.fitnesslink.fit.network.dto.SyncPushResponse
import retrofit2.http.*

/**
 * Core-service sync endpoints. The CORE base URL already carries the
 * `/api/v1/` prefix, so these paths match iOS `APIEndpoint.syncPull` /
 * `.syncPush` (`core/sync/pull`, `core/sync/push`).
 */
interface SyncApi {
    /**
     * @param cursor opaque server cursor from the previous page/pull; null on first sync.
     * @param entities comma-joined [com.fitnesslink.fit.sync.SyncDomain.wireName]s, or null for all.
     */
    @GET("sync/pull")
    suspend fun pull(
        @Query("cursor") cursor: String? = null,
        @Query("entities") entities: String? = null
    ): SyncPullResponse

    @POST("sync/push")
    suspend fun push(@Body request: SyncPushRequest): SyncPushResponse
}
