package com.fitnesslink.fit.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM entry point (FTNSSLNK-260/261). Registered in the manifest. All real
 * work is delegated to [PushNotificationManager] so it stays testable.
 *
 * `onMessageReceived` fires for every data-only message and for notification
 * messages while the app is foregrounded; backgrounded notification messages
 * are rendered by FCM itself (on the channel named by the manifest
 * `default_notification_channel_id` meta-data) and their data keys land in
 * `MainActivity`'s launch intent, which [PushNotificationManager.handleIntent]
 * reads.
 */
class FitnessLinkMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushNotificationManager.onToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        PushNotificationManager.onMessageReceived(title, body, message.data)
    }
}
