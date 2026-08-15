# Building and installing on iPhone

This Mac cannot build the app by itself. macOS 12.7.4 caps out at Xcode 14.2,
whose iOS 16.2 SDK is missing UIKit symbols that Compose Multiplatform 1.11.1
references. So the build happens on a remote CI Mac and the signing happens here.

The split is deliberate: the CI Mac has a modern SDK but no signing certificate,
and this Mac has the certificate but no modern SDK. The private key never leaves
this machine.

## Remote CI machine

    ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
      -J xziy@ny.m42.cx:2522 ci@10.10.0.115

macOS 15.7.7, Xcode 16.3, iOS 18.4 SDK. Project lives in `~/build/mobile-client`.

One-time setup already done there: JDK 17 unpacked into `~/.jdks` (no admin
rights needed, and Homebrew casks require a sudo password we can't supply), and
the Gradle 9.5 distribution staged at `/tmp/gradle-9.5.0-bin.zip` because the
wrapper's own download kept timing out mid-build.

## Build

Sync the source, excluding the two symlinks that point outside the repo:

    rsync -az --delete --no-links \
      --exclude '.git/' --exclude 'build/' --exclude '.gradle/' --exclude '.kotlin/' \
      --exclude 'agentiz' --exclude 'iphone-buils' --exclude 'local.properties' \
      -e "ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -J xziy@ny.m42.cx:2522" \
      ./ ci@10.10.0.115:~/build/mobile-client/

Then on the remote, with `JAVA_HOME=/Users/ci/.jdks/jdk-17.0.20+8/Contents/Home`:

    xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
      -configuration Release -sdk iphoneos -destination "generic/platform=iOS" \
      -derivedDataPath ./build/DD \
      CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY= \
      OTHER_LDFLAGS="-Wl,-U,_OBJC_CLASS_\$_UIViewLayoutRegion" \
      build

Run it under `nohup` writing to a log file — the Kotlin framework takes about 20
minutes cold, longer than an SSH command will comfortably sit.

`OTHER_LDFLAGS` is load-bearing. `UIViewLayoutRegion` is an iOS 26 class that
does not exist even in the 18.4 SDK; Compose looks it up at runtime behind an
availability check, so `-U` tells the linker to leave it undefined rather than
fail. Drop this flag once the CI machine gets an SDK that includes the class.

## Sign and install

Pull the unsigned bundle back, then run the signing script:

    rsync -az -e "ssh ... -J xziy@ny.m42.cx:2522" \
      ci@10.10.0.115:'~/build/mobile-client/build/DD/Build/Products/Release-iphoneos/Agentiz.app' \
      build/remote/

    ./sign-and-install.sh build/remote/Agentiz.app

The script signs with certificate SHA-1 `60A1A941...` rather than by name,
because two certificates in the keychain share the same common name and
`codesign` refuses an ambiguous match. It signs against the wildcard profile
`iOS Team Provisioning Profile: *` (`5K5GDFV386.*`), which covers any bundle ID
in the team and lists this iPhone's UDID.

Installation uses `ideviceinstaller`, not Xcode: Xcode 14.2 has no device
support files for iOS 18, but `ideviceinstaller` talks to the phone regardless.

## Info.plist

`CADisableMinimumFrameDurationOnPhone` must be present and true. Compose runs a
`PlistSanityCheck` at startup and calls `abort()` if it is missing — the app
installs fine and then dies with SIGABRT the moment you tap it. The key exists
for ProMotion displays; without it iOS caps the app at 60Hz.

## Push notifications

The app registers for plain APNs — no Firebase, no CocoaPods. `AppDelegate` in
`iosApp/iosApp/iOSApp.swift` hands the device token and any tapped notification
to the shared Kotlin `Push` object; the server signs its own APNs requests with
a `.p8` key (`AGENTIZ_APNS_*` in the mobile API layer).

Two things are not in the repository and have to be set in Xcode / the developer
portal:

- the **Push Notifications** capability on the `iosApp` target, which is what
  puts `aps-environment` into the entitlements. Without it
  `registerForRemoteNotifications` fails with "no valid aps-entitlement string".
- an APNs **key** (Keys → new key with APNs enabled) — the `.p8` goes to the
  *server*, not into the app.

The wildcard provisioning profile used above (`5K5GDFV386.*`) does **not** carry
the push entitlement. A build meant to receive notifications needs an explicit
App ID with Push Notifications enabled and a profile for it; the wildcard build
still installs and runs, it simply never registers.

Development builds talk to Apple's sandbox, so the server must run with
`AGENTIZ_APNS_ENV=sandbox` for them — a sandbox token addressed at the
production host comes back as `BadDeviceToken`.

## Local changes this required

`iosApp/Configuration/Config.xcconfig` had an empty `TEAM_ID`; it is now
`5K5GDFV386`.

No JDK is installed system-wide on either machine; both have one unpacked under
`~/.jdks/jdk-17.0.20+8`. The path is set in `~/.gradle/gradle.properties` rather
than the repo's `gradle.properties`, so it stays machine-specific. That file
only steers the Gradle *daemon*, though — the `gradlew` launcher itself still
needs Java on PATH, so export `JAVA_HOME` before building:

    export JAVA_HOME=~/.jdks/jdk-17.0.20+8/Contents/Home
