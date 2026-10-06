package com.fitnesslink.fit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fitnesslink.fit.push.PushNotificationManager
import com.fitnesslink.fit.ui.navigation.AppNavigation
import com.fitnesslink.fit.ui.theme.FitnessLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // FTNSSLNK-261: cold-start notification tap. The route is parked on
        // AppRouter and consumed once MainTabNavigation appears (after the
        // splash / session restore / gating).
        PushNotificationManager.handleIntent(intent)
        setContent {
            FitnessLinkTheme {
                AppNavigation()
            }
        }
    }

    /** Warm tap (activity already alive — `singleTask` routes it here). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PushNotificationManager.handleIntent(intent)
    }
}
