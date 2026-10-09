# Quick Lens

An Android Quick Settings tile that opens a screenshot of the current app in Google Lens. Quick Lens closes the notification shade, restores the app, captures its window, and tries to select Translate. If that last step fails, choose Translate manually in Lens.

There is no overlay, built-in translator, analytics SDK, or account system. The app is written in Java with Android platform APIs and no runtime dependencies.

## Requirements and installation

- Android 14 or later (API 34+).
- The Google app with a working Google Lens image handler. Lens may need an internet connection.
- Permission to enable Quick Lens as an Accessibility service.

Download the signed APK from [Releases](https://github.com/abocha/quick-lens/releases), compare its SHA-256 checksum with the accompanying file, and install it. Allow installation from your chosen browser or file manager when Android asks.

**Migration from experimental v0.2:** uninstall that test build first, then install the release APK. Its old test signature differs from the permanent release signature. Re-enable Accessibility and add the tile afterwards. Future official releases use the same release key and can update in place. Debug builds are not release builds and may also require uninstalling before switching signatures.

## Setup and use

1. Open Quick Lens and tap **Accessibility settings**. Enable **Quick Lens** and accept Android's Accessibility consent prompt. A floating accessibility shortcut is unnecessary. Sideloaded apps may require **Allow restricted settings** in Android's app settings first.
2. Tap **Add Quick Settings tile**, or add it manually using Quick Settings' Edit control.
3. Open the app you want to translate and tap the Quick Lens tile. Leave the target app in place until Lens opens.
4. Select Translate manually if necessary and choose your source and target languages in Lens.

If the service is disconnected, the app waits briefly for Android to bind it, then asks you to enable it or retry. After a force stop, reopen Quick Lens; some device manufacturers may also require re-enabling Accessibility. Normal process restart and reboot do not require Quick Lens to run a separate background service.

## Privacy and limitations

Quick Lens requests no INTERNET, storage, overlay, microphone, or notification permissions. Android binds its Accessibility and tile services using system-only permissions. Accessibility access is broad: Quick Lens uses active-window metadata to validate a capture and Lens controls to select Translate. It does not collect, transmit, or log screen text.

Each capture is a separate JPEG in the app's private cache. A non-exported, read-only provider grants the Google app temporary access to that image through an intent and ClipData. Files are not overwritten by later captures. Images expire after one hour; cleanup runs periodically while the Accessibility service is connected, and on the next app/service/provider startup if the process was interrupted. Expired files cannot be opened through the provider. Recent files remain available so Lens can read them asynchronously. Android may clear cache earlier. There is no permanent gallery copy or backup of screenshots.

**Google Lens may upload the screenshot to Google's servers.** Quick Lens cannot control Google's retention or processing. Do not use it for screens you are uncomfortable sharing with Google.

- Protected (`FLAG_SECURE`) screens are unsupported. Quick Lens does not bypass protection.
- Missing active-window information, the app's own setup screen, System UI, an already-open Lens window, or a detected app/window change causes cancellation rather than sharing the wrong image. These checks are best effort; Android provides no atomic “user intended screen” API.
- Quick Lens uses Android 14's [window screenshot API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#takeScreenshotOfWindow(int,java.util.concurrent.Executor,android.accessibilityservice.AccessibilityService.TakeScreenshotCallback)) to capture the validated app window, excluding accessibility overlays. Android 12–13 are not supported. Multi-window, secondary displays, and unusual OEM window transitions are not validated.
- The Google image-share activity and Translate UI are not stable public APIs. Quick Lens tries the known Lens entry point, then Google's image-share handler if available. If Google changes its UI, select Translate yourself. Automatic selection recognizes a known resource ID and a few control labels; it never waits indefinitely.
- Capture has an eight-second deadline, limited foreground retries, and one screenshot-interval retry. Translate selection stops after twelve seconds. Rapid overlapping requests are ignored. There is no continuous capture or OCR monitoring.

## Build and checks

Install JDK 17 or 21 and the Android SDK with platform **34** and Build Tools **34.0.0**. Set `JAVA_HOME` and `ANDROID_HOME`, or configure the SDK in an untracked `local.properties`. Android Studio is optional.

```sh
./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease
```

On Windows use `gradlew.bat`. The Wrapper pins Gradle 8.9 and checks its distribution SHA-256. Android Gradle Plugin is pinned to 8.7.3. The debug APK is in `app/build/outputs/apk/debug/`. Without local signing configuration, the release APK is **unsigned**, not installable. GitHub Actions runs the same build and JVM tests; it never holds the release signing key.

The small JVM tests cover request overlap, stale callbacks, deadlines, window changes, URI path validation, immutable filenames, and interrupted-file cleanup.

For the existing disposable `QuickLens_Pixel_API_34` emulator, PowerShell helpers build a separate synthetic test app and exercise failure/recovery cases:

```powershell
./tools/start-emulator.ps1
./tools/install-emulator-apk.ps1
./tools/build-fixture.ps1
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 install -r .private/fixture/fixture.apk
./tools/smoke-emulator.ps1
```

Enable Accessibility and add the tile before the smoke check. The script only targets `emulator-5554`, checks the AVD name, temporarily toggles Accessibility, puts the test tile on the first Quick Settings page, kills the app process, and uses a separate protected test window. It requires a debug APK for private-cache inspection. No fixture is included in the user APK.

## Release signing

Keep one permanent release keystore, and back it up with its passwords in encrypted offline storage. Losing it prevents compatible updates for existing installations. Never commit the keystore or passwords, and never publish a debug-signed APK as an official release.

For a locally signed release, create the ignored `.private/signing.properties`:

```properties
storeFile=.private/release.jks
storePassword=YOUR_PRIVATE_PASSWORD
keyAlias=quicklens
keyPassword=YOUR_PRIVATE_PASSWORD
```

Run `gradlew assembleRelease`, verify the certificate with Android Build Tools' `apksigner verify --print-certs`, and distribute the release APK with its SHA-256 checksum. Release publishing is manual.

## Device validation

| Device | Evidence |
| --- | --- |
| Pixel 7 AVD, Android 14 / API 34, x86_64, Google Play | v0.3.0 external-app capture and automatic Translate verified; synthetic protected-window, lifecycle and cache checks performed with ADB. |
| POCO X5 5G, Android 14 / HyperOS | v0.3.0 main scenario and operation after reboot confirmed by the owner on 2026-10-10. The emulator does not reproduce HyperOS. |

## License and origin

Quick Lens was implemented independently of Screen Translator. The application source uses Android platform APIs; no third-party application code or bundled runtime library was found in the source audit. Application code is licensed under [MIT](LICENSE). The unmodified Gradle Wrapper is from Gradle and retains its [Apache 2.0 license](https://github.com/gradle/gradle/blob/v8.9.0/LICENSE).
