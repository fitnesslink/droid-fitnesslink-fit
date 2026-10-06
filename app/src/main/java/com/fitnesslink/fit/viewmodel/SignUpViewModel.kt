package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.auth.httpStatusCode
import com.fitnesslink.fit.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// MARK: - Username rules (FTNSSLNK-285)

/** Shared username validation + debounced availability check. */
object UsernameRules {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 30
    const val PASSWORD_MIN_LENGTH = 8

    /** Long enough that a fast typist doesn't fire a request per character. */
    const val DEBOUNCE_MS = 400L

    fun normalize(raw: String): String {
        var value = raw.trim()
        if (value.startsWith("@")) value = value.drop(1)
        return value.lowercase()
    }

    /** Format-only. Availability is the server's call, tracked separately. */
    fun isWellFormed(raw: String): Boolean {
        val value = normalize(raw)
        if (value.length !in MIN_LENGTH..MAX_LENGTH) return false
        if (!value.all { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '.' }) return false
        val first = value.first()
        return first in 'a'..'z' || first in '0'..'9'
    }

    /**
     * Deliberately loose — the authoritative check is Firebase rejecting it.
     * The form only needs to catch obvious typos before a round trip.
     */
    fun isEmailValid(raw: String): Boolean {
        val trimmed = raw.trim()
        val at = trimmed.indexOf('@')
        if (at <= 0) return false
        val domain = trimmed.substring(at + 1)
        return domain.contains(".") && !domain.endsWith(".") && !domain.startsWith(".")
    }
}

sealed class UsernameState {
    data object Idle : UsernameState()
    data object Checking : UsernameState()
    data object Available : UsernameState()
    data class Unavailable(val reason: String, val suggestions: List<String>) : UsernameState()

    val isAvailable: Boolean get() = this is Available
}

/**
 * Debounced, cancellable username availability check shared by the sign-up
 * form and the social CompleteProfile sheet. Typing restarts it, so only the
 * handle the user has actually settled on costs a request.
 */
class UsernameAvailabilityChecker(private val scope: CoroutineScope) {
    var state by mutableStateOf<UsernameState>(UsernameState.Idle)
        private set
    private var job: Job? = null

    fun reset() {
        job?.cancel()
        state = UsernameState.Idle
    }

    /** The server just told us this one is free — skip the re-confirming round trip. */
    fun markAvailable() {
        job?.cancel()
        state = UsernameState.Available
    }

    fun markUnavailable(reason: String, suggestions: List<String> = emptyList()) {
        job?.cancel()
        state = UsernameState.Unavailable(reason, suggestions)
    }

    /** `current()` re-reads the field so a stale response for an older value is dropped. */
    fun schedule(raw: String, current: () -> String) {
        job?.cancel()
        state = UsernameState.Idle
        val candidate = UsernameRules.normalize(raw)
        if (!UsernameRules.isWellFormed(raw)) return

        job = scope.launch {
            delay(UsernameRules.DEBOUNCE_MS)
            if (!isActive) return@launch
            state = UsernameState.Checking
            try {
                val result = ApiClient.userApi.usernameAvailable(candidate)
                // The field may have moved on while this was in flight.
                if (!isActive || UsernameRules.normalize(current()) != candidate) return@launch
                state = if (result.available) UsernameState.Available
                else UsernameState.Unavailable(
                    reason = result.reason ?: "That username is taken.",
                    suggestions = result.suggestions
                )
            } catch (_: Exception) {
                if (!isActive) return@launch
                // Don't block signup on a flaky check — the server enforces
                // uniqueness on create either way, and a 409 is handled there.
                state = UsernameState.Available
            }
        }
    }
}

// MARK: - Sign-up

/**
 * Port of iOS `ViewModels/SignUpViewModel.swift` (FTNSSLNK-285). Submit stays
 * disabled until a username check has come back available — better a
 * disabled button than a 409 after the Firebase account already exists.
 */
class SignUpViewModel : ViewModel() {
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var email by mutableStateOf("")
    /** Optional by product decision — the only field that may be left blank. */
    var phone by mutableStateOf("")
    var password by mutableStateOf("")

    /** Stored bare (no '@'); the field renders the '@' as a prefix. */
    var username by mutableStateOf("")
        private set

    private val checker = UsernameAvailabilityChecker(viewModelScope)
    val usernameState: UsernameState get() = checker.state

    var isSubmitting by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
    /** True once the account was created and the user is signed in. */
    var didSignUp by mutableStateOf(false)
        private set

    private val auth = AuthManager

    fun onUsernameChanged(value: String) {
        if (value == username) return
        username = value
        checker.schedule(value) { username }
    }

    fun applySuggestion(suggestion: String) {
        username = suggestion
        checker.markAvailable()
    }

    val isEmailValid: Boolean get() = UsernameRules.isEmailValid(email)
    val isPasswordValid: Boolean get() = password.length >= UsernameRules.PASSWORD_MIN_LENGTH
    val isUsernameWellFormed: Boolean get() = UsernameRules.isWellFormed(username)

    val isFormValid: Boolean
        get() = firstName.isNotBlank() &&
            lastName.isNotBlank() &&
            isEmailValid &&
            isPasswordValid &&
            isUsernameWellFormed &&
            usernameState.isAvailable

    fun submit() {
        if (!isFormValid || isSubmitting) return
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            try {
                auth.signUp(
                    email = email.trim(),
                    password = password,
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    phone = phone.trim().ifEmpty { null },
                    username = UsernameRules.normalize(username)
                )
                didSignUp = true
            } catch (e: Exception) {
                errorMessage = auth.errorMessage ?: e.localizedMessage
                // A 409 means the handle went in the gap between check and
                // submit — put the field back into a state the user can act on.
                if (e.httpStatusCode() == 409) {
                    checker.markUnavailable("That username was just taken.")
                    checker.schedule(username) { username }
                }
            } finally {
                isSubmitting = false
            }
        }
    }
}

// MARK: - Complete profile (social shell user)

/**
 * Port of iOS `CompleteProfileViewModel` (in `CompleteProfileSheet.swift`).
 * Pre-filled from the provider hints; the user confirms names + handle.
 */
class CompleteProfileViewModel : ViewModel() {
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var email by mutableStateOf("")
    var username by mutableStateOf("")
        private set
    /** Facebook can withhold the email when the user declines that permission. */
    var needsEmail by mutableStateOf(false)
        private set

    private val checker = UsernameAvailabilityChecker(viewModelScope)
    val usernameState: UsernameState get() = checker.state

    var isSubmitting by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
    var didComplete by mutableStateOf(false)
        private set

    private var seeded = false
    private val auth = AuthManager

    /** Idempotent; the sheet calls this on first composition. */
    fun seed(hints: AuthManager.SocialProfileHints?) {
        if (seeded) return
        seeded = true
        firstName = hints?.firstName.orEmpty()
        lastName = hints?.lastName.orEmpty()
        val providedEmail = hints?.email.orEmpty()
        email = providedEmail
        needsEmail = providedEmail.isEmpty()
        username = hints?.username.orEmpty()
        // The server-generated placeholder is guaranteed unique.
        if (username.isNotEmpty()) checker.markAvailable()
    }

    fun onUsernameChanged(value: String) {
        if (value == username) return
        username = value
        checker.schedule(value) { username }
    }

    fun applySuggestion(suggestion: String) {
        username = suggestion
        checker.markAvailable()
    }

    val isFormValid: Boolean
        get() = firstName.isNotBlank() &&
            lastName.isNotBlank() &&
            (!needsEmail || UsernameRules.isEmailValid(email)) &&
            UsernameRules.isWellFormed(username) &&
            usernameState.isAvailable

    fun submit() {
        if (!isFormValid || isSubmitting) return
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            try {
                auth.completeSocialProfile(
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    email = email.trim(),
                    username = UsernameRules.normalize(username)
                )
                didComplete = true
            } catch (e: Exception) {
                errorMessage = auth.errorMessage ?: e.localizedMessage
                if (e.httpStatusCode() == 409) {
                    checker.markUnavailable("That username was just taken.")
                    checker.schedule(username) { username }
                }
            } finally {
                isSubmitting = false
            }
        }
    }
}
