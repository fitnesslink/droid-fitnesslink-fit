package com.fitnesslink.fit.sync

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.*
import com.fitnesslink.fit.calendar.CalendarSyncService
import com.fitnesslink.fit.media.MediaPrefetcher
import com.fitnesslink.fit.network.NetworkMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.TimeUnit

/**
 * Port of iOS `SyncScheduler`: a full sync on app foreground, on
 * offline→online, and every 15 minutes via WorkManager.
 *
 * `MediaPrefetcher.runIfNeeded()` deliberately does NOT run on the
 * foreground/reconnect path — iOS removed it there because bulk video
 * downloads starved streaming playback. It runs from the periodic worker and
 * once after login (`SyncManager.onInitialSyncComplete`).
 */
object SyncScheduler {
    private var scope: CoroutineScope? = null
    private var wasOffline = false
    private var appContext: Context? = null

    fun start(context: Context) {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        appContext = context.applicationContext

        // Login-only media prefetch (see class doc).
        SyncManager.onInitialSyncComplete = { MediaPrefetcher.runIfNeeded() }

        // Sync on app foreground
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                triggerSync()
            }
        })

        // Sync on network reconnect
        scope?.launch {
            NetworkMonitor.isConnected.collectLatest { connected ->
                if (connected && wasOffline) {
                    triggerSync()
                }
                wasOffline = !connected
            }
        }

        // Schedule periodic background sync via WorkManager
        schedulePeriodicSync(context)
    }

    fun stop() {
        scope?.cancel()
        scope = null
        SyncManager.onInitialSyncComplete = null
    }

    /** Foreground / reconnect trigger: data sync + calendar mirror, no media prefetch. */
    fun triggerSync() {
        scope?.launch {
            SyncManager.performFullSync()
            // FA-96: piggy-back the calendar mirror on the data sync. The
            // service no-ops cheaply when sync is disabled or permission
            // hasn't been granted, so this is safe to call every tick.
            appContext?.let { ctx ->
                withContext(Dispatchers.IO) { CalendarSyncService.syncAll(ctx) }
            }
        }
    }

    private fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWork = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "fl_periodic_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncWork
        )
    }
}

/** Periodic (15 min) worker: full sync, then the media prefetch pass. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            SyncManager.performFullSync()
            // Periodic path is the right place for bulk media work: the app
            // is typically backgrounded, so nothing is streaming.
            try { MediaPrefetcher.runIfNeeded() } catch (_: Exception) {}
            if (SyncManager.state.value.status == SyncStatus.ERROR) Result.retry() else Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
