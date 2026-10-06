package com.fitnesslink.fit.network.dto

/** `POST core/auth/password-reset` body (FTNSSLNK-438). Platform answers 202 either way. */
data class PasswordResetRequest(
    val email: String
)

/**
 * `POST core/users` body. Mirrors the iOS `CreateUserRequest` in
 * `AuthManager.createPlatformUser` — `phone` is optional and sent as null
 * when blank. The response is a BARE GUID string, not a user object.
 */
data class CreateUserBody(
    val firebaseId: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val phone: String?,
    val username: String
)

/**
 * `GET core/users/username-available?username=` (FTNSSLNK-285). Never a 4xx
 * for "taken" — that's a 200 with `available == false` plus alternatives.
 */
data class UsernameAvailability(
    /** Normalized form the server evaluated (lowercased, no leading '@'). */
    val username: String = "",
    val available: Boolean = false,
    /** Why not, when unavailable — "taken", too short, reserved, and so on. */
    val reason: String? = null,
    /** Free alternatives to offer as tappable chips. */
    val suggestions: List<String> = emptyList()
)
