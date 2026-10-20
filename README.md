# iBrowser

An Android browser with a Safari (iOS 27) style interface, built natively with Kotlin and Jetpack Compose.

**Download:** grab `iBrowser.apk` from the [latest release](../../releases/tag/latest). Every push to `main` rebuilds it with GitHub Actions.

## Interface
- iOS 27 **compact** layout: floating Liquid-Glass back button, address pill with the Page menu on the left, and a Tabs button on the right. Bottom and Top layouts are available in Settings.
- Toolbar shrinks to a small domain pill while scrolling; swipe the address bar left/right to switch tabs, up to open the tab overview.
- New **Start page** with Start / Bookmarks / Reading List / History sections, Favorites, Frequently Visited and a Privacy Report.
- Dense, scrollable **Page menu** (text size, Reader, Find on Page, Share, bookmarks, reading list, desktop site, translate, print/PDF, hide toolbar, per-site ad blocking, website settings).
- Tab overview grid with thumbnails, swipe-to-close, search, and Private Browsing (separate cookie profile on supported WebView versions).
- Reader view with font size, serif/sans and four themes; long-press link and image menus; full-screen video.

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
iBrowser collects no personal data — no accounts, analytics or ads. See the [Privacy Policy](PRIVACY.md) (also in the app under Settings › About › Privacy Policy).
