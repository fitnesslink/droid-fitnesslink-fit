package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.api.FavoritedContent
import com.fitnesslink.fit.model.api.ImageAsset
import com.fitnesslink.fit.network.dto.ShareContentRequest
import okhttp3.MultipartBody
import retrofit2.http.*

interface ContentApi {
    @POST("content/favorites/{contentId}")
    suspend fun toggleFavorite(@Path("contentId") contentId: String)

    @GET("content/favorites")
    suspend fun getFavorites(): List<FavoritedContent>

    @POST("content/share")
    suspend fun share(@Body request: ShareContentRequest)

    @GET("content/shared")
    suspend fun getSharedContent(): List<FavoritedContent>

    @Multipart
    @POST("content/upload")
    suspend fun uploadMedia(@Part file: MultipartBody.Part): ImageAsset
}
