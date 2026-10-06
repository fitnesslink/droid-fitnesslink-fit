package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.api.Movement
import com.fitnesslink.fit.network.dto.CreateMovementRequest
import com.fitnesslink.fit.network.dto.EmptyBody
import com.fitnesslink.fit.network.dto.MovementAnatomyRole
import com.fitnesslink.fit.network.dto.MovementFavoriteDto
import com.fitnesslink.fit.network.dto.PagedResponse
import com.fitnesslink.fit.network.dto.RecentMovementDto
import retrofit2.http.*

/** Mirrors iOS `MovementService`. */
interface MovementApi {
    @GET("movements")
    suspend fun list(@Query("page") page: Int = 1, @Query("pageSize") pageSize: Int = 100): PagedResponse<Movement>

    /**
     * Server-filtered movement list. Each id list ORs within itself and
     * ANDs against every other filter; repeated keys without brackets
     * (`anatomyIds=a&anatomyIds=b`), which is how Retrofit encodes a List.
     * `role` defaults to `primary`. The taxonomy-tag lists (difficulty /
     * goal / movementPattern / location) take PersonalizationOption ids.
     */
    @GET("movements/list-view")
    suspend fun listView(
        @Query("q") query: String? = null,
        @Query("anatomyIds") anatomyIds: List<String>? = null,
        @Query("equipmentIds") equipmentIds: List<String>? = null,
        @Query("role") role: String = MovementAnatomyRole.PRIMARY,
        @Query("difficultyIds") difficultyIds: List<String>? = null,
        @Query("goalIds") goalIds: List<String>? = null,
        @Query("movementPatternIds") movementPatternIds: List<String>? = null,
        @Query("locationIds") locationIds: List<String>? = null,
        @Query("contributorId") contributorId: String? = null,
        @Query("personalOnly") personalOnly: Boolean? = null,
        @Query("favoritesOnly") favoritesOnly: Boolean? = null
    ): List<Movement>

    @GET("movements/{id}")
    suspend fun get(@Path("id") id: String): Movement

    /** Returns the new row's id as a bare JSON string, not the entity. */
    @POST("movements")
    suspend fun create(@Body request: CreateMovementRequest): String

    @PUT("movements/{id}")
    suspend fun update(@Path("id") id: String, @Body movement: Movement): Movement

    @DELETE("movements/{id}")
    suspend fun delete(@Path("id") id: String)

    // Favorites + recents (FB-101)

    @GET("movements/favorites")
    suspend fun getFavorites(): List<MovementFavoriteDto>

    @POST("movements/{id}/favorite")
    suspend fun favorite(@Path("id") id: String, @Body body: EmptyBody = EmptyBody)

    @DELETE("movements/{id}/favorite")
    suspend fun unfavorite(@Path("id") id: String)

    @GET("movements/recents")
    suspend fun getRecents(@Query("limit") limit: Int = 50): List<RecentMovementDto>

    /** Fire-and-forget "I just used this" ping; server upserts on (user, movement). */
    @POST("movements/{id}/recent")
    suspend fun recordRecent(@Path("id") id: String, @Body body: EmptyBody = EmptyBody)
}
