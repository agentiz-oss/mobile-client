import SwiftUI
import UIKit
import UserNotifications
import ComposeApp
import FirebaseCore
import FirebaseMessaging

/**
 * Push on iOS, through Firebase — the same route Android uses.
 *
 * The app registers an *FCM* token and Google talks to Apple on our behalf.
 * That is what makes a TestFlight build and a build run from Xcode both work with no configuration:
 * Firebase knows which APNs environment a registration came from and picks the matching host
 * itself. Talking to Apple directly used to be this file's job, and it meant tracking that
 * environment ourselves; that route is gone.
 *
 * Two build-time requirements come with it, and neither has a runtime fallback — the imports above
 * fail the build until both are met: the **FirebaseMessaging** product must be linked into the
 * target (SPM: https://github.com/firebase/firebase-ios-sdk), and `GoogleService-Info.plist` must be
 * in the bundle, or `FirebaseApp.configure()` traps at launch.
 *
 * The delegate's own job is unchanged: hand the token and the payload of a tapped notification to
 * the shared Kotlin `Push` object; everything after that is common code.
 *
 * The target needs the "Push Notifications" capability enabled in Xcode — see BUILD-IOS.md.
 */
class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Before anything asks Messaging for a token. Reads GoogleService-Info.plist from the
        // bundle; without that file this call traps, which is why the plist is a build requirement
        // rather than something to discover at runtime.
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
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
        // Handed to Firebase, not to our server: the token we register is the FCM one, which
        // arrives through the MessagingDelegate below. Firebase derives the APNs environment from
        // this token itself, so nothing here has to know which build it is running in.
        Messaging.messaging().apnsToken = deviceToken
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

extension AppDelegate: MessagingDelegate {
    /// Called on first registration and again whenever Firebase rotates the token, which is exactly
    /// when the server has to be told — registration is an upsert keyed by the token itself.
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken else { return }
        Push.shared.deliverToken(token: fcmToken, platform: "ios")
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
