package com.example.app.push

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import com.example.app.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM's entry point into the app.
 *
 * Only two things reach us here. A refreshed token, which the server has to be told about or every
 * later push goes nowhere; and a message that arrived while the app is in the foreground — when the
 * app is backgrounded the system draws the notification itself from the payload's `notification`
 * block and this is never called.
 */
class AgentizMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Parked, not sent: there may be no session yet. App() registers it as soon as there is one.
        Push.deliverToken(token, "android")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (pushRouteFrom(data) == null) return
        val title = message.notification?.title ?: "Агент ждёт ответа"
        val body = message.notification?.body.orEmpty()
        show(title, body, data)
    }

    private fun show(title: String, body: String, data: Map<String, String>) {
        createInteractionsChannel(this)
        val intent = Intent(this, MainActivity::class.java).apply {
            // The activity is already running in this case; reusing its task is what makes the tap
            // land on the existing screen instead of a second copy of the app.
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            for ((key, value) in data) putExtra(key, value)
        }
        val pending = PendingIntent.getActivity(
            this,
            // One pending intent per question, so a second notification cannot silently reuse the
            // first one's extras.
            data[Push.KEY_INTERACTION].hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, INTERACTIONS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(data[Push.KEY_INTERACTION].hashCode(), notification)
    }
}
