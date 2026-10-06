package com.fitnesslink.fit

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.media.MediaCacheStore
import com.fitnesslink.fit.media.MediaPrefetcher
import com.fitnesslink.fit.network.ApiConfiguration
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.push.NotificationBadge
import com.fitnesslink.fit.push.PushNotificationManager
import com.fitnesslink.fit.sync.SyncManager
import com.fitnesslink.fit.sync.SyncScheduler
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FitnessLinkApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        DatabaseManager.initialize(this)
        ApiConfiguration.initialize(this)
        NetworkMonitor.initialize(this)
        MediaCacheStore.initialize(this)
        MediaPrefetcher.initialize(this)
        SyncManager.initialize(this)
        // Auth state listener must attach after the DB exists (it hydrates
        // the cached user) and before the first frame (splash gating).
        AuthManager.initialize(this)
        // FTNSSLNK-260: after Firebase so Messaging has the app instance;
        // before any UI so a stashed FCM token meets the session restore.
        PushNotificationManager.start(this)
        SyncScheduler.start(this)

        // FTNSSLNK-261: the badge value goes stale as the user reads in-app —
        // recompute from the server's unread count on every foreground.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (AuthManager.isAuthenticated.value) {
                    appScope.launch { NotificationBadge.refreshFromServer() }
                }
            }
        })
    }
}
