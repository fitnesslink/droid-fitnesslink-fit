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
    // The server route is POST-only (`SyncController.Pull`, body `{lastSyncedAt}`) and is
    // currently a stub that returns no changes. iOS issues a GET and gets 405; Android
    // sends POST so the pull succeeds and the sync state is not marked as errored. The
    // cursor/entities stay as query params so a future server implementation can read them.
    @POST("sync/pull")
    suspend fun pull(
        @Query("cursor") cursor: String? = null,
        @Query("entities") entities: String? = null,
        @Body body: Map<String, String?> = mapOf("lastSyncedAt" to null)
    ): SyncPullResponse

    @POST("sync/push")
    suspend fun push(@Body request: SyncPushRequest): SyncPushResponse
}
