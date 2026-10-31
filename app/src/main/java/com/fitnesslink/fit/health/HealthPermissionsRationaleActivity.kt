package com.fitnesslink.fit.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.billing.AppLinks
import com.fitnesslink.fit.ui.billing.openUrl
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme
import com.fitnesslink.fit.ui.theme.FitnessLinkTheme

/**
 * The privacy rationale Health Connect shows from its permission screen
 * ("why does FitnessLink need this?") and from its app settings. Health
 * Connect requires an app to declare this screen before it will grant any
 * permission (manifest: `ACTION_SHOW_PERMISSIONS_RATIONALE` + the Android 14
 * `VIEW_PERMISSION_USAGE` alias).
 */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitnessLinkTheme {
                RationaleContent(
                    onPrivacyPolicy = { openUrl(AppLinks.PRIVACY_POLICY) },
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
private fun RationaleContent(onPrivacyPolicy: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FLTheme.colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("FitnessLink & Health Connect", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
        Body("FitnessLink reads data from Health Connect to power your Progress stats and goals. It never writes to Health Connect.")
        Heading("What FitnessLink reads")
        listOf(
            "Steps — average and total steps, and step goals",
            "Active calories burned",
            "Exercise sessions — your active minutes",
            "Sleep — time asleep per night, and sleep goals",
            "Resting heart rate and heart-rate variability (HRV) — recovery stats and goals",
            "Body fat — your latest reading"
        ).forEach { Body("•  $it") }
        Heading("How it's used")
        Body("Readings are used on this device to calculate the stats and goal progress you see in the app. FitnessLink doesn't sell your health data or use it for advertising.")
        Heading("Your control")
        Body("You can choose exactly which data FitnessLink reads in Health Connect's settings, or disconnect any time from Profile → Connected Apps & Devices. Disconnecting revokes FitnessLink's access.")
        TextButton(onClick = onPrivacyPolicy) { Text("Privacy Policy", color = FLPrimary, fontWeight = FontWeight.SemiBold) }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close", color = FLTheme.colors.textPrimary) }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Body(text: String) {
    Text(text, fontSize = 15.sp, color = FLTheme.colors.textSecondary)
}
