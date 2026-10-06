package com.fitnesslink.fit.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.fitnesslink.fit.media.MediaPrefetcher
import com.fitnesslink.fit.model.api.FLUser
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.dto.CreateUserBody
import com.fitnesslink.fit.network.dto.PasswordResetRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.push.NotificationBadge
import com.fitnesslink.fit.push.PushNotificationManager
import com.fitnesslink.fit.sync.SyncManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseAuthWebException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Port of iOS `Services/Auth/AuthManager.swift`.
 *
 * Owns the Firebase session, the platform user (`/users/me`), session restore
 * on cold start, sign-up / social sign-in policy, password reset and logout.
 * Everything the UI needs is exposed as [StateFlow]s so `AppNavigation` can
 * derive its root gating from auth state instead of local flags.
 */
object AuthManager {
    private const val TAG = "AuthManager"

    private val auth = FirebaseAuth.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var appContext: Context

    // MARK: - State

    private val _currentUser = MutableStateFlow<FLUser?>(null)
    val currentUser: StateFlow<FLUser?> = _currentUser.asStateFlow()

    private val _firebaseUser = MutableStateFlow<FirebaseUser?>(null)
    val firebaseUser: FirebaseUser? get() = _firebaseUser.value

    /** Signed in to Firebase AND the platform profile is loaded. */
    val isAuthenticated: StateFlow<Boolean> =
        combine(_firebaseUser, _currentUser) { fb, user -> fb != null && user != null }
            .stateIn(scope, SharingStarted.Eagerly, false)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * True until the first auth-state callback fires (and any follow-up
     * platform-user fetch settles). The app shows a splash while this is true
     * so a relaunching user can't tap "Sign In" before we know whether the
     * previous session is still valid.
     */
    private val _isRestoringSession = MutableStateFlow(true)
    val isRestoringSession: StateFlow<Boolean> = _isRestoringSession.asStateFlow()

    /** Last human-readable failure, set by every flow that can fail. */
    @Volatile
    var errorMessage: String? = null
        private set

    /**
     * Set when a social sign-in lands on an account the platform has never
     * seen a profile for. The middleware auto-provisions a shell User row
     * (empty names, placeholder handle) on the first authenticated request,
     * so `/users/me` never 404s — "new" is detected by the empty names.
     */
    private val _needsProfileCompletion = MutableStateFlow(false)
    val needsProfileCompletion: StateFlow<Boolean> = _needsProfileCompletion.asStateFlow()

    /** Hints for the completion sheet, pulled from the provider profile. */
    data class SocialProfileHints(
        val firstName: String,
        val lastName: String,
        val email: String,
        /** Server-generated placeholder handle. Unique, so a valid default. */
        val username: String
    )

    @Volatile
    var socialProfileHints: SocialProfileHints? = null
        private set

    /**
     * True while `signUp` / a social flow owns the session: the auth-state
     * listener must not race it with its own `/users/me` fetch (that fetch is
     * what makes the middleware provision a shell row).
     */
    @Volatile private var signUpInProgress = false
    @Volatile private var socialSignInInProgress = false

    // MARK: - Wire-up

    /** Call once from `FitnessLinkApp.onCreate` after `FirebaseApp.initializeApp`. */
    fun initialize(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        listenToAuthState()
    }

    // MARK: - Auth State Listener (session restore)

    private fun listenToAuthState() {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _firebaseUser.value = user
            if (user == null) {
                _currentUser.value = null
                _isRestoringSession.value = false
                return@addAuthStateListener
            }
            // On relaunch Firebase restores `user` from disk but `currentUser`
            // is in-memory only. Hydrate synchronously from the local DB so
            // `isAuthenticated` flips true immediately and we don't flash the
            // welcome page; then refresh from the server in the background.
            if (_currentUser.value == null) {
                hydrateFromCache(user)
            }
            // Hold the splash up only when we have *no* local user to work
            // with — otherwise the cached hydration above is enough to render
            // the main UI immediately while the network refresh runs.
            if (_currentUser.value != null) {
                _isRestoringSession.value = false
            }
            // Sign-up / social flows drive their own POST /users → /users/me
            // sequence; a parallel fetch here would only race it.
            if (signUpInProgress || socialSignInInProgress) {
                _isRestoringSession.value = false
                return@addAuthStateListener
            }
            scope.launch {
                try { fetchPlatformUser() } catch (e: Exception) {
                    Log.w(TAG, "background /users/me refresh failed: ${e.message}")
                }
                _isRestoringSession.value = false
                onSessionLive()
            }
        }
    }

    private fun hydrateFromCache(user: FirebaseUser) {
        val cached = try { DatabaseManager.user() } catch (_: Exception) { null } ?: return
        _currentUser.value = FLUser(
            id = runCatching { UUID.fromString(cached.id) }.getOrDefault(UUID.randomUUID()),
            firstName = cached.firstName,
            lastName = cached.lastName,
            email = cached.email,
            phone = cached.phone.ifEmpty { null },
            firebaseId = user.uid,
            username = cached.username,
            country = cached.country.ifEmpty { null },
            profileImageId = runCatching { UUID.fromString(cached.profileImageId) }.getOrNull(),
            isActive = cached.isActive,
            requirePersonalization = !cached.isPersonalized
        )
    }

    /**
     * Single post-auth hook (the listener fires for restore, login and sign-up
     * alike): sync a stashed FCM token and the unread badge.
     */
    private fun onSessionLive() {
        if (!isAuthenticated.value) return
        PushNotificationManager.userDidAuthenticate()
        scope.launch { NotificationBadge.refreshFromServer() }
    }

    // MARK: - Token provider for ApiClient

    /**
     * Firebase ID token, optionally bypassing the cache so the SDK pulls a
     * fresh one. `forceRefresh = true` is used by the API client after a 401
     * to recover from expired-but-still-cached tokens.
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): String? {
        val user = auth.currentUser ?: return null
        return user.getIdToken(forceRefresh).await().token
    }

    // MARK: - Email / password login

    suspend fun login(email: String, password: String) {
        _isLoading.value = true
        errorMessage = null
        try {
            val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
            _firebaseUser.value = result.user
            fetchPlatformUser()
            interactiveSignInCompleted()
        } catch (e: Exception) {
            Log.w(TAG, "signIn failed: ${e.javaClass.simpleName} ${e.message}")
            errorMessage = when {
                e is FirebaseAuthException || e is FirebaseNetworkException || e is FirebaseTooManyRequestsException ->
                    mapFirebaseError(e)
                e.httpStatusCode() != null ->
                    "Signed in, but we couldn't load your profile. Please try again."
                else -> mapFirebaseError(e)
            }
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    // MARK: - Password reset

    /**
     * Asks the platform to send the branded password-reset email
     * (FTNSSLNK-438). The platform answers 202 whether or not the account
     * exists. If the platform can't take the request (unreachable, 5xx, older
     * deploy), fall back to Firebase's own email so a reset is never
     * impossible. 429 / 400 are surfaced, not retried.
     */
    suspend fun sendPasswordReset(email: String) {
        errorMessage = null
        try {
            ApiClient.userApi.requestPasswordReset(PasswordResetRequest(email))
            return
        } catch (e: Exception) {
            when (e.httpStatusCode()) {
                429 -> { errorMessage = "Too many attempts. Try again later."; throw e }
                400 -> { errorMessage = "Please enter a valid email address."; throw e }
                else -> Log.w(TAG, "platform password reset failed, falling back to Firebase — ${e.message}")
            }
        }
        try {
            auth.sendPasswordResetEmail(email).await()
        } catch (e: Exception) {
            errorMessage = mapFirebaseError(e)
            throw e
        }
    }

    // MARK: - Social sign-in

    /**
     * Google via Credential Manager → Firebase credential. `context` must be
     * an Activity context (Credential Manager shows UI). Cancellation throws
     * [SignInCancelledException]; callers treat it as silent.
     */
    suspend fun signInWithGoogle(context: Context) {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(AuthConfig.GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val idToken: String
        try {
            val result = CredentialManager.create(context).getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                errorMessage = "Google didn't return a sign-in token."
                throw AuthException.Misconfigured(errorMessage!!)
            }
        } catch (e: GetCredentialCancellationException) {
            throw SignInCancelledException()
        } catch (e: NoCredentialException) {
            // No Google account on the device, or the user dismissed the
            // account picker — nothing to show.
            throw SignInCancelledException()
        } catch (e: AuthException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Google credential failed: ${e.javaClass.simpleName} ${e.message}")
            errorMessage = "Google Sign-In failed. Please try again."
            throw e
        }

        signInWithCredential(GoogleAuthProvider.getCredential(idToken, null), providerLabel = "Google")
    }

    /**
     * Facebook through Firebase's generic OAuth web flow (Custom Tab →
     * `<project>.firebaseapp.com/__/auth/handler` → back to us). Avoids
     * linking the Facebook SDK. Hidden behind [AuthConfig.isFacebookSignInEnabled]
     * until the provider is enabled in the Firebase console.
     */
    suspend fun signInWithFacebook(activity: Activity) {
        val provider = OAuthProvider.newBuilder("facebook.com")
            .setScopes(listOf("email", "public_profile"))
            .build()

        _isLoading.value = true
        errorMessage = null
        _needsProfileCompletion.value = false
        socialProfileHints = null
        socialSignInInProgress = true
        try {
            val pending = auth.pendingAuthResult
            val result = try {
                (pending ?: auth.startActivityForSignInWithProvider(activity, provider)).await()
            } catch (e: FirebaseAuthWebException) {
                if (e.errorCode == "ERROR_WEB_CONTEXT_CANCELED") throw SignInCancelledException()
                errorMessage = mapFirebaseError(e)
                throw e
            } catch (e: Exception) {
                errorMessage = mapFirebaseError(e)
                throw e
            }
            finishSocialSignIn(result.user, result.additionalUserInfo?.isNewUser == true, "Facebook")
        } finally {
            socialSignInInProgress = false
            _isLoading.value = false
        }
    }

    /** Shared tail of every credential-based social flow. */
    private suspend fun signInWithCredential(credential: AuthCredential, providerLabel: String) {
        _isLoading.value = true
        errorMessage = null
        _needsProfileCompletion.value = false
        socialProfileHints = null
        socialSignInInProgress = true
        try {
            val result = try {
                auth.signInWithCredential(credential).await()
            } catch (e: Exception) {
                Log.w(TAG, "social signIn failed: ${e.javaClass.simpleName} ${e.message}")
                errorMessage = mapFirebaseError(e)
                throw e
            }
            finishSocialSignIn(result.user, result.additionalUserInfo?.isNewUser == true, providerLabel)
        } finally {
            socialSignInInProgress = false
            _isLoading.value = false
        }
    }

    private suspend fun finishSocialSignIn(user: FirebaseUser?, isNewUser: Boolean, providerLabel: String) {
        if (user == null) {
            errorMessage = "Sign-in failed. Please try again."
            throw AuthException.Misconfigured(errorMessage!!)
        }
        if (!AuthConfig.allowsSocialAccountCreation && isNewUser) {
            // Undo the account Firebase just minted. Deleting requires a
            // recent sign-in, which this is; fall back to sign-out if it
            // fails so we never leave a half-made session behind.
            try { user.delete().await() } catch (_: Exception) { runCatching { auth.signOut() } }
            _firebaseUser.value = null
            _currentUser.value = null
            errorMessage = "No FitnessLink account is linked to that $providerLabel account yet. Please sign up with your email first."
            throw AuthException.AccountCreationDisabled(errorMessage!!)
        }
        _firebaseUser.value = user

        try {
            fetchPlatformUser()
        } catch (e: Exception) {
            errorMessage = "Signed in, but we couldn't load your profile. Please try again."
            throw AuthException.ProfileUnavailable(errorMessage!!)
        }

        // A shell row from the middleware has no names. Ask the user to
        // finish the profile before letting them into the app; the sheet
        // POSTs /users, which adopts and fills in that same row.
        val platformUser = _currentUser.value
        if (platformUser != null &&
            platformUser.firstName.isBlank() && platformUser.lastName.isBlank()
        ) {
            val parts = (user.displayName ?: "").trim().split(" ", limit = 2)
            socialProfileHints = SocialProfileHints(
                firstName = parts.getOrNull(0).orEmpty(),
                lastName = parts.getOrNull(1).orEmpty(),
                email = user.email ?: platformUser.email,
                username = platformUser.username
            )
            _needsProfileCompletion.value = true
        } else {
            interactiveSignInCompleted()
        }
    }

    /**
     * Second half of a first-time social sign-in: the profile the user
     * confirmed on `CompleteProfileSheet`. Reuses the signup path, which the
     * backend treats as "adopt the auto-provisioned row and fill it in".
     */
    suspend fun completeSocialProfile(firstName: String, lastName: String, email: String, username: String) {
        val uid = auth.currentUser?.uid ?: throw AuthException.Misconfigured()
        _isLoading.value = true
        errorMessage = null
        try {
            createPlatformUser(uid, email, firstName, lastName, phone = null, username = username)
            _needsProfileCompletion.value = false
            socialProfileHints = null
            interactiveSignInCompleted()
        } catch (e: Exception) {
            errorMessage = mapPlatformError(e)
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * The user dismissed the completion sheet. They are signed in to Firebase
     * but have no usable profile, so drop the session rather than leave them
     * stuck behind a first `/users/me` with empty names.
     */
    fun abandonProfileCompletion() {
        _needsProfileCompletion.value = false
        socialProfileHints = null
        runCatching { auth.signOut() }
        _firebaseUser.value = null
        _currentUser.value = null
    }

    // MARK: - Registration (FTNSSLNK-285)

    /**
     * Creates the Firebase account, then the platform profile with everything
     * signup collected. A previous attempt may have created the Firebase
     * account and then failed on the platform step (username taken, network):
     * that signed-in account is reused instead of asking Firebase for a second
     * one. On a platform failure the Firebase account is kept (the middleware
     * already provisioned a row for this uid; the next submit adopts it).
     */
    suspend fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        phone: String?,
        username: String
    ) {
        _isLoading.value = true
        errorMessage = null

        val existing = auth.currentUser
        val firebaseAccount: FirebaseUser = if (existing != null && existing.email.equals(email, ignoreCase = true)) {
            existing
        } else {
            try {
                if (existing != null) runCatching { auth.signOut() }
                signUpInProgress = true
                auth.createUserWithEmailAndPassword(email, password).await().user
                    ?: throw IllegalStateException("Firebase returned no user")
            } catch (e: Exception) {
                signUpInProgress = false
                _isLoading.value = false
                errorMessage = mapFirebaseError(e)
                throw e
            }
        }
        _firebaseUser.value = firebaseAccount
        signUpInProgress = true
        try {
            createPlatformUser(firebaseAccount.uid, email, firstName, lastName, phone, username)
            interactiveSignInCompleted()
        } catch (e: Exception) {
            _currentUser.value = null
            errorMessage = mapPlatformError(e)
            throw e
        } finally {
            signUpInProgress = false
            _isLoading.value = false
        }
    }

    // MARK: - Platform API

    private suspend fun fetchPlatformUser() {
        val user = ApiClient.userApi.getMe()
        _currentUser.value = user
        withContext(Dispatchers.IO) { runCatching { DatabaseManager.saveUserFromApi(user) } }
    }

    private suspend fun createPlatformUser(
        firebaseId: String,
        email: String,
        firstName: String,
        lastName: String,
        phone: String?,
        username: String
    ) {
        val body = CreateUserBody(
            firebaseId = firebaseId,
            email = email,
            firstName = firstName,
            lastName = lastName,
            phone = phone?.takeIf { it.isNotBlank() },
            username = username
        )
        // POST /users answers 201 with the new user's Guid as a bare JSON
        // string, not a user object. Create, then read back.
        val raw = ApiClient.userApi.create(body).string()
        val id = raw.trim().trim('"')
        Log.d(TAG, "created platform user $id")
        fetchPlatformUser()
    }

    /** Turns a platform failure during signup into something the form can show. */
    fun mapPlatformError(error: Throwable): String = when (error.httpStatusCode()) {
        409 -> "That username was just taken. Please pick another."
        400 -> "Some of those details weren't accepted. Please check and try again."
        else -> "We couldn't finish creating your account. Please try again."
    }

    // MARK: - Personalization

    /** Flip the in-memory flag now so the root gate releases immediately. */
    fun markPersonalizationComplete() {
        _currentUser.value = _currentUser.value?.copy(requirePersonalization = false)
    }

    // MARK: - Post-login hooks (iOS `FitnessLinkApp.handleLogin`)

    /**
     * Every *interactive* sign-in (login, sign-up, social, profile completion)
     * ends here — not session restore. Lives in the manager rather than the
     * login screen's callback because `isAuthenticated` flips (and the login
     * screen leaves composition) before that callback can run.
     */
    private fun interactiveSignInCompleted() {
        onSessionLive()
        runPostLoginHooks()
    }

    /**
     * Initial sync + media prefetch. Runs on a process-wide scope so leaving
     * the login screen (which clears its ViewModel) can't cancel the initial
     * sync half-way.
     */
    fun runPostLoginHooks() {
        scope.launch(Dispatchers.IO) {
            runCatching { SyncManager.performInitialSync() }
                .onFailure { Log.w(TAG, "initial sync failed: ${it.message}") }
            runCatching { MediaPrefetcher.runNow() }
                .onFailure { Log.w(TAG, "media prefetch failed: ${it.message}") }
        }
    }

    // MARK: - Logout

    /**
     * Fire-and-forget; `AppNavigation` reacts to the auth state flipping.
     * Order matters: the push unregister needs the bearer token that sign-out
     * invalidates, and the DB wipe must come after the session is gone.
     */
    fun logout() {
        scope.launch { logoutSuspending() }
    }

    suspend fun logoutSuspending() {
        // Best-effort push unregister first (bounded to ~3 s internally).
        PushNotificationManager.unregisterForLogout()
        runCatching { auth.signOut() }
        _firebaseUser.value = null
        _currentUser.value = null
        _needsProfileCompletion.value = false
        socialProfileHints = null
        errorMessage = null
        withContext(Dispatchers.IO) {
            runCatching { DatabaseManager.clearUserData() }
            runCatching { SyncManager.reset() }
        }
        NotificationBadge.clear()
        // Entitlement / StoreKit-equivalent resets land here in Wave 4
        // (EntitlementStore.reset(), BillingManager.reset()).
    }

    // MARK: - Error mapping (iOS `mapFirebaseError`)

    fun mapFirebaseError(error: Throwable): String = when (error) {
        is FirebaseAuthWeakPasswordException -> "Password must be at least 6 characters."
        is FirebaseAuthInvalidCredentialsException -> when (error.errorCode) {
            "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
            else -> "Invalid email or password."
        }
        is FirebaseAuthInvalidUserException -> when (error.errorCode) {
            "ERROR_USER_NOT_FOUND" -> "No account found with this email."
            else -> error.localizedMessage ?: "No account found with this email."
        }
        is FirebaseAuthUserCollisionException -> "An account with this email already exists."
        is FirebaseNetworkException -> "Network error. Check your connection."
        is FirebaseTooManyRequestsException -> "Too many attempts. Try again later."
        is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_INVALID_LOGIN_CREDENTIALS", "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD" ->
                "Invalid email or password."
            "ERROR_USER_NOT_FOUND" -> "No account found with this email."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists."
            "ERROR_WEAK_PASSWORD" -> "Password must be at least 6 characters."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Try again later."
            "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
            else -> error.localizedMessage ?: "An unknown error occurred."
        }
        else -> error.localizedMessage ?: "An unknown error occurred."
    }
}
