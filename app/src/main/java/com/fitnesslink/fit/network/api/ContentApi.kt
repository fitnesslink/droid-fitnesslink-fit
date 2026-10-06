package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.api.FavoritedContent
import com.fitnesslink.fit.model.api.ImageAsset
import com.fitnesslink.fit.network.dto.ContentShareRequest
import com.fitnesslink.fit.network.dto.EmptyBody
import com.fitnesslink.fit.network.dto.SharedContentDto
import com.fitnesslink.fit.network.dto.ToggleFavoriteResponse
import okhttp3.MultipartBody
import retrofit2.http.*

/** Mirrors iOS `ContentService`. */
interface ContentApi {
    /**
     * Server-side toggle; returns the authoritative new state. `kind` is the
     * PascalCase content kind ("Workout" / "Program" / "Movement" — see
     * `ShareableContentKind.serverName`) so the polymorphic favorites table
     * tags the row with the right ContentTypeId.
     */
    @POST("content/favorites/{contentId}")
    suspend fun toggleFavorite(
        @Path("contentId") contentId: String,
        @Query("kind") kind: String,
        @Body body: EmptyBody = EmptyBody
    ): ToggleFavoriteResponse

    @GET("content/favorites")
    suspend fun getFavorites(): List<FavoritedContent>

    /** 204. */
    @POST("content/share")
    suspend fun share(@Body request: ContentShareRequest)

    /** Shares made WITH the caller, every status (pending / accepted / …). */
    @GET("content/shared")
    suspend fun getSharedContent(): List<SharedContentDto>

    /** Sharer-side: revoke a share the caller made. */
    @DELETE("content/share/{shareId}")
    suspend fun revokeShare(@Path("shareId") shareId: String)

    /** Recipient-side: remove a shared-in item from the caller's library (content untouched). */
    @DELETE("content/shared/{shareId}")
    suspend fun removeShare(@Path("shareId") shareId: String)

    /** Recipient accepts: enters their library, sharer notified. Returns the share in its new state. */
    @POST("content/shared/{shareId}/accept")
    suspend fun acceptShare(@Path("shareId") shareId: String, @Body body: EmptyBody = EmptyBody): SharedContentDto

    /** Recipient declines: never enters their library; the sharer can re-share. */
    @POST("content/shared/{shareId}/decline")
    suspend fun declineShare(@Path("shareId") shareId: String, @Body body: EmptyBody = EmptyBody): SharedContentDto

    @Multipart
    @POST("content/upload")
    suspend fun uploadMedia(@Part file: MultipartBody.Part): ImageAsset
}
