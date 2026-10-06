package com.fitnesslink.fit.network

import com.google.gson.Gson
import com.google.gson.JsonParseException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Structured error body the platform returns on handled failures. The AI
 * endpoints (`generate-from-prompt`, `estimate-macros`, `parse-meal`, …)
 * return their full response envelope with `errorReasonCode` /
 * `errorDetail` on 400/422/502/504; everything else returns `{ error }` or
 * `{ message }`. Every field is optional so any JSON object decodes.
 */
data class ServerErrorEnvelope(
    val errorReasonCode: String? = null,
    val errorDetail: String? = null,
    val message: String? = null,
    val error: String? = null,
    val success: Boolean? = null,
    val httpStatus: Int? = null,
    val generationId: String? = null
) {
    /** The most specific human-readable text the server sent, if any. */
    val displayMessage: String?
        get() = errorDetail ?: message ?: error
}

/**
 * Typed API failures. Mirrors iOS `APIError` one-to-one so view models can
 * branch on 401 / 402 / 403 / 404 and, through [ServerError], on 400 / 409 /
 * 502 / 504 with the server's envelope.
 *
 * Every Retrofit call made through [ApiClient] throws one of these (the
 * client installs a call adapter that converts `HttpException` and
 * `IOException`), so callers can `catch (e: ApiError.NotFound)` directly.
 * [ApiError.from] and [apiCall] exist for code that gets a raw throwable
 * from somewhere else.
 */
sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object Unauthorized : ApiError("Session expired. Please log in again.") {
        private fun readResolve(): Any = Unauthorized
    }

    data object Forbidden : ApiError("You don't have permission to perform this action.") {
        private fun readResolve(): Any = Forbidden
    }

    data object NotFound : ApiError("The requested resource was not found.") {
        private fun readResolve(): Any = NotFound
    }

    /**
     * FTNSSLNK-286: the endpoint is behind the subscription gate and this
     * user isn't entitled. Distinct from [Forbidden] so callers route to the
     * paywall rather than a permissions error. [ApiClient.onPaymentRequired]
     * has already fired by the time this is thrown.
     */
    data object PaymentRequired : ApiError("An active subscription is required.") {
        private fun readResolve(): Any = PaymentRequired
    }

    /**
     * Any other non-2xx. [body] is the raw response text (what iOS puts in
     * `serverError(statusCode, message)`); [envelope] is that body decoded as
     * [ServerErrorEnvelope] when it was a JSON object.
     */
    data class ServerError(
        val statusCode: Int,
        val body: String,
        val envelope: ServerErrorEnvelope? = null
    ) : ApiError("Server error ($statusCode): ${envelope?.displayMessage ?: body}") {
        val errorReasonCode: String? get() = envelope?.errorReasonCode
        val errorDetail: String? get() = envelope?.errorDetail

        val isBadRequest: Boolean get() = statusCode == 400
        val isConflict: Boolean get() = statusCode == 409
        val isUnprocessable: Boolean get() = statusCode == 422
        val isRateLimited: Boolean get() = statusCode == 429
        val isBadGateway: Boolean get() = statusCode == 502
        val isGatewayTimeout: Boolean get() = statusCode == 504
        /** 4xx other than the typed ones — terminal for retry queues. */
        val isClientError: Boolean get() = statusCode in 400..499

        /**
         * Re-decode the body as a typed envelope (the AI endpoints return
         * their success DTO shape on handled failures). Null when the body
         * isn't that shape.
         */
        fun <T> decodeBody(gson: Gson, type: Class<T>): T? = try {
            gson.fromJson(body, type)
        } catch (_: Exception) {
            null
        }
    }

    data class NetworkError(val underlying: Throwable) :
        ApiError("Network error: ${underlying.localizedMessage ?: underlying.javaClass.simpleName}", underlying)

    data class DecodingError(val underlying: Throwable) :
        ApiError("Data error: ${underlying.localizedMessage ?: underlying.javaClass.simpleName}", underlying)

    data object Offline : ApiError("No internet connection.") {
        private fun readResolve(): Any = Offline
    }

    data object Timeout : ApiError("The request timed out.") {
        private fun readResolve(): Any = Timeout
    }

    companion object {
        private val envelopeGson = Gson()

        /** Map an HTTP status + raw body onto the typed error, iOS `validate` rules. */
        fun fromResponse(statusCode: Int, body: String?): ApiError = when (statusCode) {
            401 -> Unauthorized
            402 -> PaymentRequired
            403 -> Forbidden
            404 -> NotFound
            else -> {
                val text = body ?: "Unknown error"
                val envelope = if (text.trimStart().startsWith("{")) {
                    try { envelopeGson.fromJson(text, ServerErrorEnvelope::class.java) } catch (_: Exception) { null }
                } else null
                ServerError(statusCode, text, envelope)
            }
        }

        /** Normalize any throwable from a network call into an [ApiError]. */
        fun from(t: Throwable): ApiError = when (t) {
            is ApiError -> t
            is HttpException -> fromResponse(t.code(), runCatching { t.response()?.errorBody()?.string() }.getOrNull())
            is SocketTimeoutException -> Timeout
            is IOException -> if (!NetworkMonitor.isConnected.value) Offline else NetworkError(t)
            is JsonParseException, is IllegalStateException -> DecodingError(t)
            else -> NetworkError(t)
        }
    }
}

/**
 * Run a network block and surface failures as [ApiError]. Calls made through
 * [ApiClient] already throw [ApiError]; this is for wrapping code that mixes
 * in other I/O (file reads, decode steps) or for callers that want the
 * offline short-circuit iOS performs before every request.
 */
suspend fun <T> apiCall(block: suspend () -> T): T {
    if (!NetworkMonitor.isConnected.value) throw ApiError.Offline
    return try {
        block()
    } catch (e: ApiError) {
        throw e
    } catch (e: Exception) {
        throw ApiError.from(e)
    }
}
