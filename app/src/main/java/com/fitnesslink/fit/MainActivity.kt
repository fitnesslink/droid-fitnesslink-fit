package com.fitnesslink.fit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.fitnesslink.fit.push.PushNotificationManager
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.navigation.DeepLinkResolver
import com.fitnesslink.fit.ui.navigation.AppNavigation
import com.fitnesslink.fit.ui.theme.FitnessLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash (Theme.FitnessLink.Starting) hands off to the Compose
        // SplashScreen, which stays up while the session restores.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // FTNSSLNK-261: cold-start notification tap. The route is parked on
        // AppRouter and consumed once MainTabNavigation appears (after the
        // splash / session restore / gating).
        PushNotificationManager.handleIntent(intent)
        handleDeepLink(intent)
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
        handleDeepLink(intent)
    }

    /** `fitnesslink://` links opened from outside the app, such as a TV's pairing QR. */
    private fun handleDeepLink(intent: Intent?) {
        val link = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        intent.data = null // A configuration change must not replay it.
        DeepLinkResolver.resolve(link)?.let { AppRouter.open(it) }
    }
}
