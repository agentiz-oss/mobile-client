# Android → Google Play (GitHub Actions)

`android-play.yml` builds the Compose Multiplatform Android app on an Ubuntu runner, signs it with
the upload key and publishes the bundle to the **internal testing** track of Google Play.

It runs on every push to `main` and can be started manually
(**Actions → Build and Deploy Android to Google Play → Run workflow**), where two inputs are
offered: `upload` (turn it off for a build that only leaves an artifact behind) and `track`.

There is no Fastlane here — unlike iOS, nothing in the Android path needs a keychain, a
provisioning profile or Xcode, so Gradle plus one upload action is the whole conveyor.

## What the build actually does

1. Installs JDK 21, the Android SDK (`platforms;android-36`, `build-tools;36.0.0`) and Gradle.
2. Writes the two per-deployment secrets into the checkout: `composeApp/google-services.json`
   (Firebase, gitignored) and the upload keystore (into `RUNNER_TEMP`, never into the workspace).
3. Runs `:composeApp:bundleRelease` — the `.aab` Play wants — and `:composeApp:assembleRelease`,
   because a bundle cannot be sideloaded and a plain `.apk` is what you hand to somebody with a
   cable. Both are kept as run artifacts for 30 days.
4. Verifies the APK is actually signed (`apksigner verify --print-certs`). Play's rejection message
   for an unsigned artifact does not mention signing, so this check is cheaper than the round trip.
5. Uploads the bundle with `r0adkll/upload-google-play`.

## Required repository secrets

| Secret | What it is |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 upload.jks` — the upload key |
| `ANDROID_KEYSTORE_PASSWORD` | Its store password |
| `ANDROID_KEY_ALIAS` | Alias inside the keystore (`agentiz-upload`) |
| `ANDROID_KEY_PASSWORD` | Password of that key; equal to the store password for a PKCS12 keystore |
| `GOOGLE_SERVICES_JSON_BASE64` | `base64 -w0 composeApp/google-services.json` — the Firebase Android config, gitignored and therefore absent from the checkout. Without it the Google Services plugin is not applied and the app never receives a push |
| `PLAY_SERVICE_ACCOUNT_JSON` | The whole service-account JSON, pasted as-is, of an account with Play Developer API access |

`printf '%s' "$VALUE" | gh secret set NAME --repo agentiz-oss/mobile-client` — `printf` rather than
`echo`, a trailing newline breaks a base64 blob in the runner.

## Version numbers

* **versionCode** — `1000 + github.run_number`, read by `composeApp/build.gradle.kts` from
  `ANDROID_VERSION_CODE`. Play refuses a code it has already accepted, and a run number only ever
  grows within a repository, so re-running an old commit cannot collide with another CI upload.
  The offset reserves everything below 1000 for bundles built by hand — which is not a hypothetical
  case, because the first release of this app had to be uploaded that way (it went up as `1`).
  A local build with the variable unset keeps `1`.
* **versionName** — `appVersionName` in `composeApp/build.gradle.kts` (`1.0`). Play shows it to
  testers and does not care whether it repeats.

## The upload key

`cx.m42.agentiz` uses **Play App Signing**: Play holds the key that actually signs what users
install, and the key in these secrets is only the *upload* key — the credential that proves a
bundle comes from us. Losing it is recoverable (Play Console resets an upload key on request);
losing an app signing key would not be, which is why Play holds it.

The key in use here was generated as:

```bash
keytool -genkeypair -v \
  -keystore ~/.android-keys/agentiz-upload.jks -storetype PKCS12 \
  -alias agentiz-upload -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Agentiz, OU=Mobile, O=AVTOTREID OOO, C=RU"
```

**Keep that file backed up outside this machine.** It is not in the repository and not in Play;
the only other copy is the `ANDROID_KEYSTORE_BASE64` secret, which GitHub will not show you again.

## Before the first run

Three things have to exist, and none of them can be created from CI:

1. **The app record in Play Console**, package `cx.m42.agentiz`.
2. **One release uploaded by hand.** Google does not let the API create the very first release of
   an app: upload the signed `.aab` through Play Console → Internal testing → Create new release.
   That upload is also what registers the upload key above with Play App Signing, so it must be
   the bundle signed with this keystore, not one built by Android Studio with a debug key.
3. **A service account with Play Developer API access:**
   * Play Console → **Setup → API access** → link (or create) a Google Cloud project;
   * in that project create a service account, then **Keys → Add key → JSON**;
   * back in Play Console → **Users and permissions** → invite the service account's e-mail and
     grant it, for this app, *Release to testing tracks* (production upload needs *Release apps to
     production*);
   * paste the whole JSON into `PLAY_SERVICE_ACCOUNT_JSON`.

   The permission grant takes a few minutes to propagate; a run started immediately after it can
   fail with `The caller does not have permission` and succeed on a re-run with nothing changed.

Also fill in the app's Play Console content forms (privacy policy, data safety, content rating,
target audience) — internal testing tolerates a lot of them being incomplete, but a release to any
other track does not.

## Building a signed binary by hand

The signing values come from the environment, or from a gitignored
`composeApp/keystore.properties` when there is no environment to speak of:

```properties
storeFile=/home/you/.android-keys/agentiz-upload.jks
storePassword=...
keyAlias=agentiz-upload
keyPassword=...
```

```bash
./gradlew :composeApp:bundleRelease     # composeApp/build/outputs/bundle/release/*.aab
./gradlew :composeApp:assembleRelease   # composeApp/build/outputs/apk/release/*.apk
```

With neither the environment variables nor that file, `release` builds **unsigned** — which is what
this project did before signing existed, and is what keeps a fresh clone compiling without any
credentials.
