package com.fitnesslink.fit.sync

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.*
import com.fitnesslink.fit.network.NetworkMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private var scope: CoroutineScope? = null
    private var wasOffline = false

    fun start(context: Context) {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
    }

    fun triggerSync() {
        scope?.launch {
            SyncManager.performFullSync()
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

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            SyncManager.performFullSync()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
