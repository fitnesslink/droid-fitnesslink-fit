package com.fitnesslink.fit.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fitnesslink.fit.MainActivity
import com.fitnesslink.fit.R
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.auth.httpStatusCode
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.dto.RegisterDeviceTokenRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.navigation.DeepLinkResolver
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Port of iOS `Services/PushNotificationManager.swift` (FTNSSLNK-260/261).
 *
 * Owns the push lifecycle:
 *  - FCM registration-token sync with the backend device-token endpoints
 *    (register once, refresh on rotation, delete on logout),
 *  - the Android 13+ runtime permission bookkeeping (asked once; denial is
 *    respected silently),
 *  - foreground presentation on the "fitnesslink_default" channel,
 *  - tap handling (mark the row read, resolve `deepLink`, hand the route to
 *    [AppRouter]) including cold-start taps via `MainActivity` intent extras.
 */
object PushNotificationManager {
    private const val TAG = "Push"

    const val CHANNEL_ID = "fitnesslink_default"
    private const val CHANNEL_NAME = "FitnessLink"

    private const val PREFS = "fl_push"
    /** Guid of our row in the backend's DeviceToken table (POST /device-tokens). */
    private const val KEY_SERVER_TOKEN_ID = "push.serverDeviceTokenId"
    /** The FCM token the backend currently has for this install. */
    private const val KEY_REGISTERED_TOKEN = "push.registeredFcmToken"
    /** Whether the runtime permission prompt has been shown once. */
    private const val KEY_HAS_PROMPTED = "push.hasPromptedForPush"

    /** FCM data keys the backend sends alongside the notification payload. */
    const val EXTRA_DEEP_LINK = "deepLink"
    const val EXTRA_NOTIFICATION_ID = "notificationId"
    const val EXTRA_TYPE = "type"

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestCodes = AtomicInteger(1000)

    /**
     * FCM token that arrived before auth was ready (Firebase hands us the
     * token at launch, potentially before the session is restored). Synced as
     * soon as [userDidAuthenticate] fires.
     */
    @Volatile private var pendingFcmToken: String? = null

    private val prefs get() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // MARK: - Wire-up

    /** Call from `FitnessLinkApp.onCreate` after Firebase is initialised. */
    fun start(context: Context) {
        appContext = context.applicationContext
        createChannel()
        // Already granted on a previous run → re-sync the token on every
        // launch (tokens can rotate; registration is idempotent).
        if (isPermissionGranted(appContext)) requestCurrentToken()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Workout reminders, goals and content updates"
        }
        manager.createNotificationChannel(channel)
    }

    // MARK: - Permission (Android 13+)

    fun isPermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun hasPromptedForPush(): Boolean = prefs.getBoolean(KEY_HAS_PROMPTED, false)

    /**
     * Should the app ask the system for the permission now? Only on Android
     * 13+, only while not granted, and only if it has never asked before — a
     * denial is respected silently (no settings deep-link nagging).
     */
    fun shouldRequestPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isPermissionGranted(context) &&
            !hasPromptedForPush()

    /** Result of the runtime prompt; registers the token on a grant. */
    fun onPermissionResult(granted: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_PROMPTED, true).apply()
        if (granted) requestCurrentToken()
    }

    // MARK: - Token lifecycle

    private fun requestCurrentToken() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> if (!token.isNullOrEmpty()) onToken(token) }
            .addOnFailureListener { Log.w(TAG, "FCM token fetch failed: ${it.message}") }
    }

    /** New or rotated FCM token (from the messaging service or the launch fetch). */
    fun onToken(token: String) {
        if (!AuthManager.isAuthenticated.value) {
            // Fired before session restore/login — stash and sync on auth.
            pendingFcmToken = token
            return
        }
        scope.launch { syncTokenWithBackend(token) }
    }

    /** Called by `AuthManager` once the platform user is available. */
    fun userDidAuthenticate() {
        val token = pendingFcmToken ?: return
        pendingFcmToken = null
        scope.launch { syncTokenWithBackend(token) }
    }

    private suspend fun syncTokenWithBackend(token: String) {
        if (!isPermissionGranted(appContext)) {
            // Nothing can be shown on this device yet; keep the token and
            // register once the permission is granted.
            pendingFcmToken = token
            return
        }
        val storedId = prefs.getString(KEY_SERVER_TOKEN_ID, null)
        val storedToken = prefs.getString(KEY_REGISTERED_TOKEN, null)

        // Already in sync — nothing to do.
        if (storedId != null && storedToken == token) return

        if (storedId != null) {
            // Token rotated under a known server row → refresh in place.
            try {
                ApiClient.notificationApi.refreshDeviceToken(storedId, token)
                prefs.edit().putString(KEY_REGISTERED_TOKEN, token).apply()
                return
            } catch (e: Exception) {
                if (e.httpStatusCode() == 404) {
                    // Server no longer knows the row → fresh registration.
                    clearStoredRegistration()
                } else {
                    // Transient failure — keep local state; FCM re-delivers
                    // the token on next launch and we retry then.
                    Log.w(TAG, "device-token refresh failed: ${e.message}")
                    return
                }
            }
        }

        try {
            val raw = ApiClient.notificationApi
                .registerDeviceToken(RegisterDeviceTokenRequest(token = token, platform = "android"))
                .string()
            val id = raw.trim().trim('"')
            if (id.isEmpty()) throw IllegalStateException("empty device-token id")
            prefs.edit()
                .putString(KEY_SERVER_TOKEN_ID, id)
                .putString(KEY_REGISTERED_TOKEN, token)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "device-token registration failed: ${e.message}")
        }
    }

    private fun clearStoredRegistration() {
        prefs.edit().remove(KEY_SERVER_TOKEN_ID).remove(KEY_REGISTERED_TOKEN).apply()
    }

    // MARK: - Logout

    /**
     * Best-effort server unregister. Runs *before* Firebase sign-out (the
     * DELETE needs the auth token), bounded to ~3 s so a dead network can
     * never hang the logout button. Local state is cleared regardless, so the
     * next login re-registers fresh.
     */
    suspend fun unregisterForLogout() {
        pendingFcmToken = null
        if (!::appContext.isInitialized) return
        val storedId = prefs.getString(KEY_SERVER_TOKEN_ID, null)
        clearStoredRegistration()

        if (storedId != null) {
            withTimeoutOrNull(3_000) {
                runCatching { ApiClient.notificationApi.unregisterDeviceToken(storedId) }
                    .onFailure { Log.w(TAG, "device-token DELETE failed: ${it.message}") }
            }
        }

        // Invalidate the FCM token so this install stops being a push target
        // even if the server DELETE didn't land.
        FirebaseMessaging.getInstance().deleteToken()
            .addOnFailureListener { Log.w(TAG, "FCM deleteToken failed: ${it.message}") }
    }

    // MARK: - Incoming message

    /** A push arrived while foregrounded: show it ourselves and refresh the badge. */
    fun onMessageReceived(title: String?, body: String?, data: Map<String, String>) {
        scope.launch { NotificationBadge.refreshFromServer() }
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        showNotification(title ?: CHANNEL_NAME, body.orEmpty(), data)
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        if (!isPermissionGranted(appContext)) return
        val tapIntent = Intent(appContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data[EXTRA_DEEP_LINK]?.let { putExtra(EXTRA_DEEP_LINK, it) }
            data[EXTRA_NOTIFICATION_ID]?.let { putExtra(EXTRA_NOTIFICATION_ID, it) }
            data[EXTRA_TYPE]?.let { putExtra(EXTRA_TYPE, it) }
        }
        val requestCode = requestCodes.incrementAndGet()
        val pending = PendingIntent.getActivity(
            appContext, requestCode, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fitnesslink)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(appContext).notify(requestCode, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "notify refused: ${e.message}")
        }
    }

    // MARK: - Tap handling

    /** The FitnessLink-specific data keys of an FCM push. */
    data class Payload(val deepLink: String?, val notificationId: String?, val type: String?) {
        val isEmpty: Boolean get() = deepLink == null && notificationId == null && type == null

        /** The route for this payload, if any. `null` for absent or unknown deep links. */
        val route: String? get() = deepLink?.let(DeepLinkResolver::resolve)

        companion object {
            fun from(data: Map<String, String?>) = Payload(
                deepLink = data[EXTRA_DEEP_LINK]?.takeIf { it.isNotEmpty() },
                notificationId = data[EXTRA_NOTIFICATION_ID]?.takeIf { it.isNotEmpty() },
                type = data[EXTRA_TYPE]?.takeIf { it.isNotEmpty() }
            )

            fun from(intent: Intent?): Payload? {
                val extras = intent?.extras ?: return null
                val payload = from(
                    mapOf(
                        EXTRA_DEEP_LINK to extras.getString(EXTRA_DEEP_LINK),
                        EXTRA_NOTIFICATION_ID to extras.getString(EXTRA_NOTIFICATION_ID),
                        EXTRA_TYPE to extras.getString(EXTRA_TYPE)
                    )
                )
                return payload.takeUnless { it.isEmpty }
            }
        }
    }

    /**
     * Cold-start or warm tap delivered through `MainActivity`'s intent
     * (our own notifications and FCM-rendered background ones both put the
     * data keys in the launcher intent's extras). Returns true when the
     * intent carried a push payload. The extras are consumed so a
     * configuration change doesn't replay the tap.
     */
    fun handleIntent(intent: Intent?): Boolean {
        val payload = Payload.from(intent) ?: return false
        intent?.removeExtra(EXTRA_DEEP_LINK)
        intent?.removeExtra(EXTRA_NOTIFICATION_ID)
        intent?.removeExtra(EXTRA_TYPE)
        handleTap(payload)
        return true
    }

    /** User tapped a notification: mark it read (local + server) and route. */
    fun handleTap(payload: Payload) {
        payload.notificationId?.let { id ->
            // Mirror locally so the inbox agrees even if the network call
            // loses the race with the inbox fetch.
            scope.launch {
                runCatching { DatabaseManager.markNotificationRead(id) }
                runCatching { ApiClient.notificationApi.markRead(id) }
                    .onFailure { Log.w(TAG, "markRead failed: ${it.message}") }
                NotificationBadge.refreshFromServer()
            }
        }
        // No deepLink, or one with no pushable route → just open the app.
        payload.route?.let { AppRouter.open(it) }
    }
}
