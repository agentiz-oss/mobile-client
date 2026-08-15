package com.example.app.push

import platform.UIKit.UIApplication
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS's half of push: ask for permission, then ask the system to register with APNs.
 *
 * No Firebase here — the app registers its raw APNs device token and the server talks to Apple
 * directly (see `app-agentiz-mobile-api/lib/push/ApnsPushProvider.ts`), which keeps an SDK and a CocoaPods
 * setup out of the iOS build entirely.
 *
 * The token itself does not come back through this call: it is handed to the app delegate, which
 * passes it to [Push.deliverToken] (see `iosApp/iosApp/iOSApp.swift`).
 */
actual fun ensurePushRegistration() {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    center.requestAuthorizationWithOptions(
        UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound,
    ) { granted, error ->
        if (!granted) {
            println("[push] notifications not authorised: ${error?.localizedDescription ?: "declined"}")
            return@requestAuthorizationWithOptions
        }
        // UIKit is main-thread only, and this callback arrives on an arbitrary queue.
        dispatch_async(dispatch_get_main_queue()) {
            UIApplication.sharedApplication.registerForRemoteNotifications()
        }
    }
}
