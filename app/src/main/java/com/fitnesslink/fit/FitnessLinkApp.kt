package com.fitnesslink.fit

import android.app.Application
import com.fitnesslink.fit.persistence.DatabaseManager

class FitnessLinkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DatabaseManager.initialize(this)
    }
}
