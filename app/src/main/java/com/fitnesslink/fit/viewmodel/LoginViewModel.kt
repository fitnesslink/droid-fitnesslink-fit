package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.delay
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
            delay(1500)
            DatabaseManager.saveUser(id = "user1", name = "Fitness User", email = email)
            DatabaseManager.user()?.let { needsPersonalization = !it.isPersonalized }
            isLoading = false
            isAuthenticated = true
        }
    }

    fun loginWithGoogle() {
        viewModelScope.launch {
            DatabaseManager.saveUser(id = "user1", name = "Fitness User", email = "user@fitnesslink.com")
            DatabaseManager.user()?.let { needsPersonalization = !it.isPersonalized }
        }
        isAuthenticated = true
    }

    fun loginWithFacebook() {
        viewModelScope.launch {
            DatabaseManager.saveUser(id = "user1", name = "Fitness User", email = "user@fitnesslink.com")
            DatabaseManager.user()?.let { needsPersonalization = !it.isPersonalized }
        }
        isAuthenticated = true
    }
}
