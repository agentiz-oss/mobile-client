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
        // Only what the app can route on gets drawn by hand here; anything else stays silent in
        // the foreground rather than becoming a banner that taps into nothing.
        if (pushRouteFrom(data) == null) return
        val title = message.notification?.title ?: "Agentiz"
        val body = message.notification?.body.orEmpty()
        show(title, body, data, message.notification?.channelId)
    }

    private fun show(title: String, body: String, data: Map<String, String>, serverChannelId: String?) {
        createNotificationChannels(this)
        val intent = Intent(this, MainActivity::class.java).apply {
            // The activity is already running in this case; reusing its task is what makes the tap
            // land on the existing screen instead of a second copy of the app.
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            for ((key, value) in data) putExtra(key, value)
        }
        // One card per subject — the interaction for a question, the run for everything else — so
        // consecutive events about one run replace each other the way the server's collapse key
        // intends, instead of stacking.
        val collapseId = (data[Push.KEY_INTERACTION] ?: data[Push.KEY_RUN] ?: data[Push.KEY_ACTIVITY_ID]).hashCode()
        val pending = PendingIntent.getActivity(
            this,
            collapseId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // The channel the server chose (carried in the message's notification block); absent —
        // as from an older server — falls back to the type mapping, then to interactions, which
        // is the pre-activities behaviour. Unknown ids are refused: a notification against a
        // channel that was never created is silently dropped by the OS.
        val channelId = when (serverChannelId ?: serverChannelFor(data)) {
            ACTIONS_CHANNEL_ID -> ACTIONS_CHANNEL_ID
            FAILURES_CHANNEL_ID -> FAILURES_CHANNEL_ID
            RESULTS_CHANNEL_ID -> RESULTS_CHANNEL_ID
            else -> INTERACTIONS_CHANNEL_ID
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(collapseId, notification)
    }

    /** Mirrors the server's type → channel mapping for foreground display of `type=activity`. */
    private fun serverChannelFor(data: Map<String, String>): String = when (data[Push.KEY_ACTIVITY_TYPE]) {
        "proposal.waiting_review", "proposal.push_failed", "proposal.reset_failed",
        "run.held_for_approval", "pr.opened",
        -> ACTIONS_CHANNEL_ID
        "run.failed" -> FAILURES_CHANNEL_ID
        "run.succeeded", "proposal.pushed", "run.cancelled" -> RESULTS_CHANNEL_ID
        else -> INTERACTIONS_CHANNEL_ID
    }
}
