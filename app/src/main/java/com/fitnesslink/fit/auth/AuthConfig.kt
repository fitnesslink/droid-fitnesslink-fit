package com.fitnesslink.fit.auth

/**
 * Static auth configuration. Mirrors the iOS constants that live on
 * `AuthManager` / `LoginFormView`.
 */
object AuthConfig {
    /**
     * OAuth 2.0 *web* client id (client_type 3 in app/google-services.json).
     * Credential Manager's `GetGoogleIdOption.setServerClientId` needs the web
     * client, not the Android client, so Firebase can verify the ID token.
     */
    const val GOOGLE_WEB_CLIENT_ID =
        "864850452172-16brbu630npi088vse4uj9udb652dr8h.apps.googleusercontent.com"

    /**
     * Facebook sign-in is fully wired (Firebase `OAuthProvider("facebook.com")`
     * web flow) but the provider isn't enabled in the Firebase console yet, so
     * the button is hidden. Flip once the Facebook app + Firebase provider are
     * configured. Matches iOS `LoginFormView.isFacebookSignInEnabled`.
     */
    const val isFacebookSignInEnabled = false

    /**
     * Reversed 2026-10-27 (was false since 2026-09-09): Google sign-in may now
     * create accounts, matching the web app and iOS. A first-time social user
     * lands on `CompleteProfileSheet`, which POSTs /users to adopt the
     * auto-provisioned shell row and fill in the name. When false, a brand-new
     * Firebase user is rolled back before any platform call. Matches iOS
     * `AuthManager.allowsSocialAccountCreation`.
     */
    const val allowsSocialAccountCreation = true
}
