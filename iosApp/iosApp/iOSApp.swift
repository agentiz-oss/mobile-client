import SwiftUI
import UIKit
import UserNotifications
import ComposeApp

/**
 * Push on iOS is plain APNs — no Firebase SDK, no CocoaPods. The device token this delegate
 * receives is registered with the Agentiz mobile API as-is, and the server signs its own APNs
 * requests with a `.p8` key.
 *
 * The delegate's only job is to hand two things to the shared Kotlin `Push` object: the token, and
 * the payload of a notification the user tapped. Everything after that (registering the token,
 * navigating to the question) is common code.
 *
 * The target needs the "Push Notifications" capability enabled in Xcode — see BUILD-IOS.md.
 */
class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        // Launched *by* a notification: the tap has no delegate callback of its own in this case,
        // the payload only appears here.
        if let userInfo = launchOptions?[.remoteNotification] as? [AnyHashable: Any] {
            deliver(userInfo)
        }
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        // APNs tokens are addressed as lowercase hex, which is also what the server stores.
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        Push.shared.deliverToken(token: token, platform: "ios")
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("[push] APNs registration failed: \(error.localizedDescription)")
    }

    /// In the foreground the system stays silent unless asked otherwise — and the user is not
    /// necessarily looking at the questions screen when a run stops to ask something.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        deliver(response.notification.request.content.userInfo)
        completionHandler()
    }

    private func deliver(_ userInfo: [AnyHashable: Any]) {
        Push.shared.deliverRouteFields(
            type: userInfo["type"] as? String,
            interactionId: userInfo["interactionId"] as? String,
            projectId: userInfo["projectId"] as? String,
            projectName: userInfo["projectName"] as? String,
            taskId: userInfo["taskId"] as? String
        )
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
