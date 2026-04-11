package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.media.MediaManifest
import com.fitnesslink.fit.media.ResolveMediaRequest
import com.fitnesslink.fit.media.ResolveMediaResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface MediaApi {
    @POST("media/resolve")
    suspend fun resolve(@Body request: ResolveMediaRequest): ResolveMediaResponse

    @GET("media/me/today")
    suspend fun todayManifest(@Query("utcOffsetMinutes") utcOffsetMinutes: Int): MediaManifest
}
