package com.example.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Android's half of push: the notification channel, the runtime permission, the FCM token, and
 * reading a tapped notification back off the intent that launched the activity.
 *
 * Everything here tolerates Firebase being absent. A build without `google-services.json` has no
 * `FirebaseApp` to initialise, and the calls below then throw — which must leave an app that still
 * works, only without notifications, rather than one that cannot start.
 */

/** Must match `android.notification.channelId` in the server's FCM payload, or O+ drops the push. */
const val INTERACTIONS_CHANNEL_ID = "agentiz-interactions"

private var permissionRequest: (() -> Unit)? = null

/**
 * Wires the activity into push. Called from `onCreate`, before the first composition: the
 * permission launcher has to be registered while the activity is still being created, and the
 * channel has to exist before any notification can be shown against it.
 */
fun attachPushHost(activity: ComponentActivity) {
    createInteractionsChannel(activity)
    val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Denied is a normal answer, not an error: the questions screen and its badge still work,
        // they just stop announcing themselves.
        if (granted) fetchFcmToken()
    }
    permissionRequest = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            fetchFcmToken()
        }
    }
}

fun createInteractionsChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    manager.createNotificationChannel(
        NotificationChannel(
            INTERACTIONS_CHANNEL_ID,
            "Вопросы агентов",
            // High: a question holds its whole pipeline run until someone answers it.
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Агент остановился и ждёт ответа"
        },
    )
}

private fun fetchFcmToken() {
    runCatching {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            Push.deliverToken(token, "android")
        }
    }.onFailure {
        println("[push] FCM token unavailable: ${it.message}")
    }
}

actual fun ensurePushRegistration() {
    permissionRequest?.invoke() ?: fetchFcmToken()
}

/**
 * Reads the `data` payload FCM put on the launch intent when a notification was tapped. Extras are
 * `Any?`-typed and full of Android's own keys, so only string values are considered.
 */
fun routeFromIntent(intent: Intent?): Map<String, String> {
    val extras = intent?.extras ?: return emptyMap()
    return buildMap {
        for (key in extras.keySet()) {
            val value = extras.getString(key) ?: continue
            put(key, value)
        }
    }
}
