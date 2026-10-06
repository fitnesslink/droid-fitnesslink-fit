package com.fitnesslink.fit.auth

import com.fitnesslink.fit.network.ApiError
import retrofit2.HttpException

/**
 * Thrown by the social flows when the user backs out of the provider UI.
 * Callers treat it as "nothing happened", not as an error to show.
 * Mirrors iOS `AuthManager.SignInCancelled`.
 */
class SignInCancelledException : Exception("Sign-in cancelled")

/** Mirrors iOS `AuthManager.AuthError`. */
sealed class AuthException(message: String) : Exception(message) {
    class Misconfigured(message: String = "Sign-in isn't configured.") : AuthException(message)
    class AccountCreationDisabled(message: String) : AuthException(message)
    /** Firebase succeeded but `/users/me` could not be loaded. */
    class ProfileUnavailable(message: String) : AuthException(message)
}

/**
 * HTTP status of a failed Retrofit call, whichever shape the API layer threw
 * it in. Retrofit's suspend functions throw [HttpException]; the API client
 * may instead surface its own [ApiError]. `null` for network / decoding
 * failures with no status.
 */
fun Throwable.httpStatusCode(): Int? = when (this) {
    is HttpException -> code()
    is ApiError.ServerError -> statusCode
    is ApiError.Unauthorized -> 401
    is ApiError.Forbidden -> 403
    is ApiError.NotFound -> 404
    else -> null
}
