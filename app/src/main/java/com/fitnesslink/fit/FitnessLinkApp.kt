package com.fitnesslink.fit

import android.app.Application
import com.fitnesslink.fit.network.ApiConfiguration
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.sync.SyncManager
import com.fitnesslink.fit.sync.SyncScheduler
import com.google.firebase.FirebaseApp

class FitnessLinkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        DatabaseManager.initialize(this)
        ApiConfiguration.initialize(this)
        NetworkMonitor.initialize(this)
        SyncManager.initialize(this)
        SyncScheduler.start(this)
    }
}
