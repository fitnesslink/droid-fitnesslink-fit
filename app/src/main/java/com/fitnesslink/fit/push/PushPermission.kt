package com.fitnesslink.fit.push

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Runtime `POST_NOTIFICATIONS` request helper (Android 13+). Returns a
 * function that asks the system once; the result is recorded by
 * [PushNotificationManager.onPermissionResult], which registers the FCM
 * token on a grant. On older Android (or when already granted) it just
 * confirms the grant so a stashed token gets synced.
 */
@Composable
fun rememberPushPermissionRequester(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        PushNotificationManager.onPermissionResult(granted)
    }
    return remember(context) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !PushNotificationManager.isPermissionGranted(context)
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                PushNotificationManager.onPermissionResult(PushNotificationManager.isPermissionGranted(context))
            }
        }
    }
}
