package com.fitnesslink.fit.auth

import com.fitnesslink.fit.model.api.FLUser
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.dto.CreateUserRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

object AuthManager {
    private val auth = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow<FLUser?>(null)
    val currentUser: StateFlow<FLUser?> = _currentUser.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val firebaseUser: FirebaseUser?
        get() = auth.currentUser

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _isAuthenticated.value = user != null && _currentUser.value != null
        }
    }

    suspend fun getIdToken(): String? {
        return auth.currentUser?.getIdToken(false)?.await()?.token
    }

    suspend fun login(email: String, password: String) {
        _isLoading.value = true
        try {
            auth.signInWithEmailAndPassword(email, password).await()
            fetchPlatformUser()
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Exchange a Google ID token for a Firebase credential and sign in.
     * Token acquisition is the caller's responsibility — typically via
     * Credential Manager + Google Identity Services. The token is wrapped
     * in [GoogleAuthProvider.getCredential] and handed to Firebase Auth.
     */
    suspend fun loginWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        signInWithCredential(credential)
    }

    /**
     * Exchange a Facebook access token for a Firebase credential and sign
     * in. Acquired via the Facebook Login SDK on the caller side; we stay
     * provider-agnostic past the credential boundary.
     */
    suspend fun loginWithFacebook(accessToken: String) {
        val credential = FacebookAuthProvider.getCredential(accessToken)
        signInWithCredential(credential)
    }

    private suspend fun signInWithCredential(credential: AuthCredential) {
        _isLoading.value = true
        try {
            auth.signInWithCredential(credential).await()
            // Try to fetch a platform user; if the server doesn't have one
            // yet (first OAuth sign-in), provision it from the Firebase
            // user's metadata.
            try {
                fetchPlatformUser()
            } catch (_: Exception) {
                val user = auth.currentUser
                    ?: throw IllegalStateException("Firebase sign-in succeeded but currentUser is null")
                val email = user.email ?: ""
                val displayName = user.displayName.orEmpty().split(" ", limit = 2)
                val first = displayName.getOrNull(0).orEmpty()
                val last = displayName.getOrNull(1).orEmpty()
                createPlatformUser(user.uid, email, first, last)
            }
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun signUp(email: String, password: String, firstName: String, lastName: String) {
        _isLoading.value = true
        try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("Failed to create Firebase user")
            createPlatformUser(uid, email, firstName, lastName)
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun fetchPlatformUser() {
        val user = ApiClient.userApi.getMe()
        _currentUser.value = user
        _isAuthenticated.value = true
        DatabaseManager.saveUserFromApi(user)
    }

    private suspend fun createPlatformUser(
        firebaseId: String, email: String, firstName: String, lastName: String
    ) {
        val request = CreateUserRequest(
            firebaseId = firebaseId,
            email = email,
            firstName = firstName,
            lastName = lastName,
            username = email.substringBefore("@")
        )
        val user = ApiClient.userApi.create(request)
        _currentUser.value = user
        _isAuthenticated.value = true
        DatabaseManager.saveUserFromApi(user)
    }

    fun logout() {
        auth.signOut()
        _currentUser.value = null
        _isAuthenticated.value = false
        DatabaseManager.clearUserData()
    }

    suspend fun restoreSession() {
        val firebaseUser = auth.currentUser ?: return
        _isLoading.value = true
        try {
            fetchPlatformUser()
        } catch (_: Exception) {
            // Use cached local user if offline
            DatabaseManager.user()?.let { cached ->
                _currentUser.value = FLUser(
                    id = try { java.util.UUID.fromString(cached.id) } catch (_: Exception) { java.util.UUID.randomUUID() },
                    firstName = cached.firstName,
                    lastName = cached.lastName,
                    email = cached.email,
                    firebaseId = firebaseUser.uid,
                    username = cached.username,
                    isActive = cached.isActive,
                    requirePersonalization = !cached.isPersonalized
                )
                _isAuthenticated.value = true
            }
        } finally {
            _isLoading.value = false
        }
    }

    fun mapFirebaseError(error: Exception): String {
        val message = error.message ?: return "An unknown error occurred."
        return when {
            "INVALID_LOGIN_CREDENTIALS" in message || "wrong-password" in message ->
                "Invalid email or password."
            "USER_NOT_FOUND" in message || "user-not-found" in message ->
                "No account found with this email."
            "EMAIL_EXISTS" in message || "email-already-in-use" in message ->
                "An account with this email already exists."
            "WEAK_PASSWORD" in message || "weak-password" in message ->
                "Password must be at least 6 characters."
            "NETWORK" in message.uppercase() ->
                "Network error. Check your connection."
            "TOO_MANY_ATTEMPTS" in message.uppercase() ->
                "Too many attempts. Try again later."
            "INVALID_EMAIL" in message ->
                "Please enter a valid email address."
            else -> message
        }
    }
}
