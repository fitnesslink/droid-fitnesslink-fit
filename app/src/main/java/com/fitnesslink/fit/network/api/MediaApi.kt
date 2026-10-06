package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.media.MediaAssetDetail
import com.fitnesslink.fit.media.MediaManifest
import com.fitnesslink.fit.media.MediaUploadResponse
import com.fitnesslink.fit.media.ResolveMediaRequest
import com.fitnesslink.fit.media.ResolvedMediaItem
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Media endpoints. Contract is the iOS `MediaService` / `APIEndpoint`:
 * `POST media/resolve` returns a BARE ARRAY of items (no envelope),
 * `POST media` is multipart with a `container` field + `file` part.
 */
interface MediaApi {
    @POST("media/resolve")
    suspend fun resolve(@Body request: ResolveMediaRequest): List<ResolvedMediaItem>

    @GET("media/me/today")
    suspend fun todayManifest(@Query("utcOffsetMinutes") utcOffsetMinutes: Int): MediaManifest

    /**
     * Upload an image/video into the unified MediaAsset store. `container`
     * is `photos`, `videoassets`, etc. Suitable for small images through the
     * shared Retrofit client; large videos go through `MediaUploadWorker`,
     * which streams from disk with progress and survives relaunch.
     */
    @Multipart
    @POST("media")
    suspend fun upload(
        @Part("container") container: RequestBody,
        @Part file: MultipartBody.Part
    ): MediaUploadResponse

    /** Mux ingest status + playback id for an uploaded asset (FB-89 poller). */
    @GET("media/{id}")
    suspend fun get(@Path("id") id: String): MediaAssetDetail
}
