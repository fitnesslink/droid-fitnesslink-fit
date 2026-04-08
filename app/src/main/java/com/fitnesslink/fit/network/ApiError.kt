package com.fitnesslink.fit.network

sealed class ApiError : Exception() {
    data object Unauthorized : ApiError()
    data object Forbidden : ApiError()
    data object NotFound : ApiError()
    data class ServerError(val statusCode: Int, override val message: String) : ApiError()
    data class NetworkError(override val cause: Throwable) : ApiError()
    data class DecodingError(override val cause: Throwable) : ApiError()
    data object Offline : ApiError()

    override val message: String
        get() = when (this) {
            is Unauthorized -> "Session expired. Please log in again."
            is Forbidden -> "You don't have permission to perform this action."
            is NotFound -> "The requested resource was not found."
            is ServerError -> "Server error ($statusCode): $message"
            is NetworkError -> "Network error: ${cause.localizedMessage}"
            is DecodingError -> "Data error: ${cause.localizedMessage}"
            is Offline -> "No internet connection."
        }
}
