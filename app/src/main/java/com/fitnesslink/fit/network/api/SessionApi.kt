package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.model.WorkoutSession
import com.fitnesslink.fit.network.dto.EmptyBody
import com.fitnesslink.fit.network.dto.MovementHistorySummary
import com.fitnesslink.fit.network.dto.SessionBodyDataRequest
import com.fitnesslink.fit.network.dto.SessionCompleteRequest
import com.fitnesslink.fit.network.dto.SessionHistoryEntry
import com.fitnesslink.fit.network.dto.SessionHistoryLogRequest
import com.fitnesslink.fit.network.dto.SessionProgressRequest
import com.fitnesslink.fit.network.dto.SessionShareResultsRequest
import com.fitnesslink.fit.network.dto.SessionStartRequest
import retrofit2.http.*

/** Mirrors iOS `SessionService`. Every mutation returns 204. */
interface SessionApi {
    @GET("sessions/me")
    suspend fun getMySessions(): List<WorkoutSession>

    @GET("sessions/{id}")
    suspend fun get(@Path("id") id: String): WorkoutSession

    /**
     * Idempotent upsert on the client-minted id, deduped by (user, workout,
     * in-progress) — the returned session's id may differ from the request.
     */
    @POST("sessions")
    suspend fun start(@Body request: SessionStartRequest): WorkoutSession

    @POST("sessions/{id}/complete")
    suspend fun complete(@Path("id") id: String, @Body request: SessionCompleteRequest)

    /** Idempotent; completed sessions are not downgraded. */
    @POST("sessions/{id}/abandon")
    suspend fun abandon(@Path("id") id: String, @Body body: EmptyBody = EmptyBody)

    /** Partial playback-state update (phase / index / next movement). */
    @POST("sessions/{id}/progress")
    suspend fun updateProgress(@Path("id") id: String, @Body request: SessionProgressRequest)

    /** One logged set. */
    @POST("sessions/{id}/history")
    suspend fun logHistory(@Path("id") id: String, @Body request: SessionHistoryLogRequest)

    /** Every logged set for the session (`SessionEntryDto`). */
    @GET("sessions/{id}/history")
    suspend fun getHistory(@Path("id") id: String): List<SessionHistoryEntry>

    @POST("sessions/{id}/body-data")
    suspend fun logBodyData(@Path("id") id: String, @Body request: SessionBodyDataRequest)

    @POST("sessions/{id}/share-results")
    suspend fun shareResults(@Path("id") id: String, @Body request: SessionShareResultsRequest)

    /**
     * Per-movement last / best figures (FTNSSLNK-410). `movementIds` is a
     * comma-separated list of at most 50 lowercase ids; only movements with
     * history come back.
     */
    @GET("sessions/me/movement-history")
    suspend fun movementHistory(@Query("movementIds") movementIds: String): List<MovementHistorySummary>

    /** Soft delete — `/sessions/me` stops returning it. 404 = unknown / not ours. */
    @DELETE("sessions/{id}")
    suspend fun delete(@Path("id") id: String)

    companion object {
        const val MOVEMENT_HISTORY_MAX_IDS = 50

        /** Dedupe, lowercase, cap at 50 and join for [movementHistory]. */
        fun movementHistoryQuery(movementIds: Collection<String>): String? {
            val ids = movementIds.map { it.lowercase() }.distinct().take(MOVEMENT_HISTORY_MAX_IDS)
            return if (ids.isEmpty()) null else ids.joinToString(",")
        }
    }
}
