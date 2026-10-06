package com.fitnesslink.fit.viewmodel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.auth.SignInCancelledException
import kotlinx.coroutines.launch

/**
 * Port of iOS `ViewModels/LoginViewModel.swift`: the Login tab, the Forgot
 * Password sheet and the social sign-in buttons. Sign-up has its own
 * [SignUpViewModel].
 */
class LoginViewModel : ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    /** True once the form should hand off to `onLogin`. */
    var isAuthenticated by mutableStateOf(false)
    var needsPersonalization by mutableStateOf(true)
    var isInvalidCredentials by mutableStateOf(false)

    private val auth = AuthManager

    val isFormValid: Boolean
        get() = email.trim().isNotEmpty() && password.isNotEmpty()

    fun login() {
        if (!isFormValid || isLoading) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            isInvalidCredentials = false
            try {
                auth.login(email.trim(), password)
                needsPersonalization = auth.currentUser.value?.requirePersonalization ?: true
                isLoading = false
                isAuthenticated = true
            } catch (e: Exception) {
                isLoading = false
                isInvalidCredentials = true
                errorMessage = auth.errorMessage ?: auth.mapFirebaseError(e)
            }
        }
    }

    // MARK: - Forgot Password

    var resetEmail by mutableStateOf("")
    var isSendingReset by mutableStateOf(false)
    var resetErrorMessage by mutableStateOf<String?>(null)
    var resetEmailSent by mutableStateOf(false)

    val isResetFormValid: Boolean
        get() {
            val trimmed = resetEmail.trim()
            return trimmed.contains("@") && trimmed.contains(".")
        }

    /** Sheet opened: seed from the login form and clear leftovers from a previous attempt. */
    fun beginForgotPassword() {
        resetEmail = email
        resetErrorMessage = null
        resetEmailSent = false
        isSendingReset = false
    }

    fun sendPasswordReset() {
        if (!isResetFormValid || isSendingReset) return
        viewModelScope.launch {
            isSendingReset = true
            resetErrorMessage = null
            try {
                auth.sendPasswordReset(resetEmail.trim())
                resetEmailSent = true
            } catch (e: Exception) {
                resetErrorMessage = auth.errorMessage ?: "Something went wrong. Please try again."
            }
            isSendingReset = false
        }
    }

    // MARK: - Social Sign-In

    enum class SocialProvider { GOOGLE, FACEBOOK }

    /** Non-null while a social flow is in progress; buttons disable and the matching one spins. */
    var socialProvider by mutableStateOf<SocialProvider?>(null)
    var socialErrorMessage by mutableStateOf<String?>(null)
    /**
     * Set after a successful first-time social sign-in; the form presents
     * `CompleteProfileSheet` and only hands off once that finishes.
     */
    var needsProfileCompletion by mutableStateOf(false)

    /** `context` must come from the Activity (Credential Manager shows UI). */
    fun loginWithGoogle(context: Context) {
        socialLogin(SocialProvider.GOOGLE) { auth.signInWithGoogle(context) }
    }

    fun loginWithFacebook(context: Context) {
        val activity = context.findActivity()
        if (activity == null) {
            socialErrorMessage = "Couldn't open Facebook Sign-In."
            return
        }
        socialLogin(SocialProvider.FACEBOOK) { auth.signInWithFacebook(activity) }
    }

    private fun socialLogin(provider: SocialProvider, run: suspend () -> Unit) {
        if (socialProvider != null) return
        socialProvider = provider
        socialErrorMessage = null
        isInvalidCredentials = false
        viewModelScope.launch {
            try {
                run()
            } catch (_: SignInCancelledException) {
                // User backed out of the provider UI — nothing to show.
                return@launch
            } catch (e: Exception) {
                socialErrorMessage = auth.errorMessage ?: "Sign-in failed. Please try again."
                return@launch
            } finally {
                socialProvider = null
            }

            if (auth.needsProfileCompletion.value) {
                needsProfileCompletion = true
                return@launch
            }
            needsPersonalization = auth.currentUser.value?.requirePersonalization ?: true
            isAuthenticated = true
        }
    }

    /** Called by the completion sheet once the profile is saved. */
    fun profileCompleted() {
        needsProfileCompletion = false
        needsPersonalization = auth.currentUser.value?.requirePersonalization ?: true
        isAuthenticated = true
    }

    /** The sheet was dismissed without saving: drop the half-made session. */
    fun profileCompletionAbandoned() {
        needsProfileCompletion = false
        auth.abandonProfileCompletion()
    }

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
