# Prism Browser

A private Android browser with a liquid-glass interface, a strong ad and pop-up blocker,
a multithreaded downloader and a video downloader.

## Interface

**Phones**:
- The start page has the "Wander the web, gently." hero, favorites and "For you" cards.
- A floating liquid-glass address bar sits at the bottom. It shrinks to a small pill while you scroll.
- The tab switcher is an iOS-style carousel. Swipe a card up to close that tab.

**Tablets, Chromebooks and desktop mode (DeX)**: the layout follows desktop Safari:
- Liquid-glass toolbar groups, with the smart search field in the center.
- Tab strip, sidebar (tabs, bookmarks, history) and a tab overview grid.
- A start page with a privacy report.

Real backdrop blur needs Android 12 or newer. Older versions get a frosted fallback.

## Shield (ad and pop-up blocker)

- **Network filtering:** every request (including service workers) is checked against EasyList,
  EasyPrivacy, the uBlock Origin lists (filters, badware, privacy), Peter Lowe's list,
  AdGuard Mobile and the Fanboy cookie list. Together that is about 177k rules.
  - The lists are bundled and update weekly.
  - Extra lists can be turned on: AdGuard Base, Annoyances and StevenBlack hosts.
- **Cosmetic filtering:**
  - Element-hiding rules for the current site.
  - About 1.5k complex generic rules.
  - The 27k generic class/id rules are injected only when that class or id appears in the page.
- **Pop-ups:**
  - Blocks windows that open without a user tap.
  - Blocks pop-unders, where you click link A but a script opens site B.
  - Removes invisible full-page click-jacking overlays and blocks synthetic `target=_blank` clicks.
  - Checks every new window against the filter lists before showing it.
  - Blocks forced redirects to ad domains (with an "Allow once" option).
  - Blocks app-scheme jumps (`intent:`, `market:`) that happen without a tap.
- **Per-site:** the Shield can be turned off for a single site.

## Downloads

- Multiple connections per file, with work-stealing: an idle connection splits the largest
  remaining chunk. 1–32 connections; the default is 8.
- Pause and resume, saved across app restarts. Automatic retries with backoff.
- Runs in a foreground service with progress notifications.
- Files are saved to `Downloads/Prism`.

## Video downloads

- Detects `<video>`/`<audio>` sources and media requests (MP4, WebM, MOV, MKV, MP3, M4A, and so on)
  plus HLS (`.m3u8`) streams.
- For HLS streams:
  - Lists the available qualities.
  - Downloads segments in parallel and decrypts AES-128.
  - Remuxes the result losslessly to MP4.
- DRM-protected streams can't be downloaded. Neither can YouTube's split audio/video streams.

## Uploads, camera and permissions

- File uploads accept any type and size, and multiple files where the site allows it (Play Console `.aab`/`.apk` included).
- Upload buttons can open the camera to take a photo or video.
- Sites can request camera, microphone and location. You approve each request.
- Google sign-in is supported: the browser identifies as a regular Chromium browser.

## Build

```bash
./gradlew assembleRelease     # signs with keystore.properties if present, otherwise debug key
./gradlew testReleaseUnitTest # filter engine, downloader and HLS tests
```
