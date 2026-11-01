# Prism Browser

An Android browser with an original Liquid Glass interface, built natively with Kotlin and Jetpack Compose.

**Download:** grab `iBrowser.apk` from the [latest release](../../releases/tag/latest). Every push to `main` rebuilds it with GitHub Actions.

## Interface

- Original teal, ink and ivory Liquid Glass design, with light/dark themes and adaptive glass tint.
- Phones: compact command dock, expanded dock with labels or top command bar; counted tab overview, back/forward, Home, New Tab and page tools.
- Tablets and desktop-sized windows: navigation rail, top command bar and vertical tab workspace. The workspace overlays narrower tablet windows and docks beside the page on wider ones.
- Start page with web search, library cards, favorites, browsing history, privacy report and reading list.
- Thumbnail tab grid on phones/tablets with search, private mode, swipe-to-close, archive and tab actions.
- Existing Reader, page summary, find, sharing, bookmarks, permissions, downloads, media detection and player features remain attached to the same browser actions.

## Ad blocker
- Adblock Plus / uBlock-compatible engine: hosts files, `||domain^` rules, wildcards, separators, anchors, regex rules, exceptions, `$third-party`, `$domain=`, resource-type options and `$important`.
- Cosmetic filtering (generic and per-site `##` rules) injected at document start, plus overlay removal and automatic YouTube ad skipping.
- Ships with a built-in list and downloads EasyList, EasyPrivacy, uBlock filters/privacy/badware, AdGuard Base & Mobile, Peter Lowe's list and URLhaus (optional: annoyances, StevenBlack hosts). Lists refresh every 4 days.
- Pop-ups opened without a tap are blocked.

## Download manager
- Up to 32 connections per file with byte-range segmenting and **work stealing** (idle connections split the biggest remaining segment).
- Pause / resume — segment progress is saved to disk, so downloads resume even after the app is closed. Automatic retries with back-off.
- Several simultaneous downloads, foreground-service notification with progress and "Pause all".
- Files are saved to `Download/iBrowser`.

## Media sniffer & stream converter
- Detects video/audio files and HLS (`.m3u8`) / DASH (`.mpd`) streams from network requests, XHR/fetch responses and `<video>`/`<audio>` elements. A blue download badge appears in the address bar.
- HLS: master playlists with quality picker, separate audio renditions, AES-128 encrypted segments, byte ranges and fMP4. DASH: SegmentTemplate (number/time/timeline), SegmentList and single-file representations.
- Segments download in parallel and are **losslessly remuxed to MP4** (no re-encoding). If a stream can't be remuxed, the original `.ts` is kept.
- DRM-protected streams (Widevine, SAMPLE-AES) are detected and not downloaded.

## Build locally
```
./gradlew assembleRelease
```

## Privacy
Prism Browser collects no personal data — no accounts, analytics or ads. See the [Privacy Policy](PRIVACY.md) (also in the app under Settings › About › Privacy Policy).

Design notes, previews and editable Figma import: [Prism redesign](design/PRISM_REDESIGN.md).
