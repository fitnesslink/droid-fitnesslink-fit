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

    fun loginWithGoogle() {
        // TODO: Implement Google Sign-In via Firebase
    }

    fun loginWithFacebook() {
        // TODO: Implement Facebook Sign-In via Firebase
    }
}
