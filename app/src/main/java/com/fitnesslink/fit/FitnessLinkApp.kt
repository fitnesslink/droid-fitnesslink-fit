package com.fitnesslink.fit

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.billing.EntitlementStore
import com.fitnesslink.fit.billing.PlayBillingManager
import com.fitnesslink.fit.settings.UserSettings
import com.fitnesslink.fit.calendar.CalendarService
import com.fitnesslink.fit.goals.GoalStore
import com.fitnesslink.fit.progress.BodyTrackingStore
import com.fitnesslink.fit.calendar.CalendarSyncService
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.contributors.ContributorStore
import com.fitnesslink.fit.ui.components.ContentLayoutPrefs
import com.fitnesslink.fit.media.MediaCacheStore
import com.fitnesslink.fit.media.MediaPrefetcher
import com.fitnesslink.fit.network.ApiConfiguration
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.programs.ProgramEnrollmentService
import com.fitnesslink.fit.nutrition.FoodLogStore
import com.fitnesslink.fit.nutrition.GroceryStore
import com.fitnesslink.fit.nutrition.MealPlanStore
import com.fitnesslink.fit.push.NotificationBadge
import com.fitnesslink.fit.session.ActiveWorkoutSession
import com.fitnesslink.fit.session.SessionSettings
import com.fitnesslink.fit.push.PushNotificationManager
import com.fitnesslink.fit.sync.SyncManager
import com.fitnesslink.fit.sync.SyncScheduler
import com.fitnesslink.fit.workouts.WorkoutStore
import com.fitnesslink.fit.workouts.WorkoutPhaseStore
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
        // Units + appearance must be right from the first frame.
        UserSettings.attach(this)
        NetworkMonitor.initialize(this)
        MediaCacheStore.initialize(this)
        MediaPrefetcher.initialize(this)
        SyncManager.initialize(this)
        // Register offline drains before the first sync pass can run.
        WorkoutPhaseStore.ensureRegistered()
        WorkoutStore.ensureRegistered()
        CalendarSyncService.attach(this)
        CalendarService.ensureRegistered()
        ProgramEnrollmentService.ensureRegistered()
        GoalStore.ensureRegistered()
        BodyTrackingStore.ensureRegistered()
        BodyTrackingStore.attach(this)
        FoodLogStore.ensureRegistered()
        MealPlanStore.ensureRegistered()
        GroceryStore.ensureRegistered()
        SessionSettings.attach(this)
        ActiveWorkoutSession.ensureRegistered()
        EntitlementStore.attach(this)
        PlayBillingManager.attach(this)
        ContentLayoutPrefs.initialize(this)
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
                    // iOS reconciles the classification taxonomy on every foreground; the store
                    // itself de-dups to one fetch per process.
                    appScope.launch { runCatching { ClassificationStore.refresh() } }
                    appScope.launch { runCatching { ContributorStore.refresh() } }
                }
            }
        })
    }
}
