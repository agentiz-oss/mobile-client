package com.example.app.push

import platform.UIKit.UIApplication
// Not a member of UIApplication: UIKit declares it in the UIRemoteNotifications category, which
// Kotlin/Native exposes as a package-level extension. Without this import it does not resolve.
import platform.UIKit.registerForRemoteNotifications
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

/**
 * iOS draws the badge from a number the app owns, not from the notifications on screen — clearing
 * the notification centre does not clear it, and neither does answering the question it was about.
 *
 * `applicationIconBadgeNumber` rather than `setBadgeCount` on purpose: the latter arrived in iOS 16
 * and this target still deploys to 15. It is a member property of UIApplication — unlike
 * `registerForRemoteNotifications` above, which is a category and needs its own import. UIKit is
 * main-thread only, and this runs on whatever dispatcher the polling coroutine happens to be on.
 */
actual fun setAppBadge(count: Int) {
    dispatch_async(dispatch_get_main_queue()) {
        UIApplication.sharedApplication.applicationIconBadgeNumber = count.toLong()
    }
}
