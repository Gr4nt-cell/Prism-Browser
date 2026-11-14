# Prism Browser implementation notes

The uploaded `mobile-browser_v2.zip` was inspected as a **visual reference only** (its sole implementation file is `mobile-browser/index.html`). The Android app is native Kotlin/Android Views, not a WebView wrapper around that HTML.

- Phone: soft orange-accented Pearl Mist home screen, 4-column favorites, recommendation cards, floating glass address/navigation controls, iOS-style tab switcher.
- Tablet/desktop-sized windows: Safari-like toolbar, tab strip and sidebar.
- The phone web-content viewport is laid out **above** the dock (`webHolder` has weight 1 and the dock lives in a separate `barHolder`), not obscured by it.
- WebView uses a wide viewport with overview fitting, with 85% text zoom as the default (user-adjustable). Website viewport/CSS still controls individual site responsiveness.
- Ad/tracker blocking: bundled filter lists and site controls; pop-up blocking enabled by default. No blocker guarantees 100% of ads.
- Downloads: up to 32 configurable parallel HTTP range segments (default 8), pause/resume where supported, HLS support. Resuming depends on server range support; DRM streams and inaccessible/unsupported streams cannot be downloaded.
- Uploads: Android system file chooser, photo picker/camera capture, multiple selections when supported, for sites such as Google Play Console. Camera and microphone access is subject to runtime permissions and website permission requests.
- Open links in other installed apps via Android intents.

## Build
Run the GitHub Actions `Build Prism APK and AAB` workflow after configuring `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` as repository secrets. The workflow generates `app-release.apk` and `app-release.aab`. An AAB intended to update an existing Play listing must be signed with that listing's existing upload key.

No APK/AAB has been compiled or device-tested in this workspace: the Android SDK and Gradle tooling are unavailable here.
