package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.sync.SyncManager
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var isAuthenticated by mutableStateOf(false)
    var needsPersonalization by mutableStateOf(true)
    var isInvalidCredentials by mutableStateOf(false)

    val isFormValid: Boolean
        get() = email.trim().isNotEmpty() && password.isNotEmpty()

    fun login() {
        if (!isFormValid) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            isInvalidCredentials = false
            try {
                AuthManager.login(email, password)
                needsPersonalization = AuthManager.currentUser.value?.requirePersonalization ?: true
                isLoading = false
                isAuthenticated = true
                // Trigger initial sync
                SyncManager.performInitialSync()
            } catch (e: Exception) {
                isLoading = false
                isInvalidCredentials = true
                errorMessage = AuthManager.mapFirebaseError(e)
            }
        }
    }

    /**
     * Sign in via Google. The Firebase plumbing lives in AuthManager; what
     * we still owe is the token acquisition step (Credential Manager +
     * Google Identity Services) which requires a Web Client ID configured
     * in google-services.json. Until that's wired, surface a clear inline
     * error instead of silently succeeding.
     */
    fun loginWithGoogle() {
        val idToken = acquireGoogleIdToken()
        if (idToken == null) {
            errorMessage = "Google sign-in is not yet configured for this build."
            return
        }
        signInWithCredential { AuthManager.loginWithGoogle(idToken) }
    }

    fun loginWithFacebook() {
        val accessToken = acquireFacebookAccessToken()
        if (accessToken == null) {
            errorMessage = "Facebook sign-in is not yet configured for this build."
            return
        }
        signInWithCredential { AuthManager.loginWithFacebook(accessToken) }
    }

    private fun signInWithCredential(block: suspend () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            isInvalidCredentials = false
            try {
                block()
                needsPersonalization = AuthManager.currentUser.value?.requirePersonalization ?: true
                isAuthenticated = true
                SyncManager.performInitialSync()
            } catch (e: Exception) {
                isInvalidCredentials = true
                errorMessage = AuthManager.mapFirebaseError(e)
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * TODO: integrate Credential Manager + Google Identity Services.
     * Until then this returns null so the UI surfaces a configuration
     * error rather than logging the user in with no auth.
     */
    private fun acquireGoogleIdToken(): String? = null

    /** TODO: integrate Facebook Login SDK and return its access token. */
    private fun acquireFacebookAccessToken(): String? = null
}
