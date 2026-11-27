package com.fitnesslink.fit.network.api

import retrofit2.http.Body
import retrofit2.http.POST

/** The phone's side of TV pairing. The TV inits and polls; the signed-in phone approves its code. */
interface TvPairingApi {
    @POST("tv/pair/confirm")
    suspend fun confirm(@Body request: ConfirmPairingRequest): ConfirmPairingResponse
}

data class ConfirmPairingRequest(val code: String)

data class ConfirmPairingResponse(val success: Boolean? = null)
