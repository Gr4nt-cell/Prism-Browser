# Safari 27 interface redesign

Phone: the Compact layout is the default. A floating back control sits beside the rounded Smart Search field and overlapping-square tab button. Horizontal address gestures switch tabs; swipe up opens the overview. Long-press Back retains Forward access, also available in the page menu. Bottom and Top layouts remain selectable.

Tablet: the chrome spans the top of the window, with back/forward, a centered address field, privacy, downloads, share, tab overview and browser actions. A horizontal tab strip supports selecting and closing each tab. The sidebar is optional and opens the existing Bookmarks, Reading List, History, Private Browsing, Archive, Downloads and Settings. Windows narrower than 600dp use phone controls.

Shared: quiet lavender/blue wallpaper, translucent frost, white highlight rims, soft depth, original monoline vector symbols and restrained spring feedback. Native Android system typography is retained. This is a Safari-inspired native Android implementation; it does not use Safari/WebKit, Apple fonts or an Apple rendering engine, and pixel-for-pixel identity has not been established.

Start Page, tab overview, private appearance, address editing, menus and settings use the same material. The tab grid is two columns on phones and adaptive on tablets. Cards retain thumbnail selection, close controls and horizontal swipe-to-close. Search, normal/private switching, archive and settings remain reachable.

## Figma

Target file: https://www.figma.com/design/BPzlo4VIkIiLgn7RT7RtzJ

The file was created, but Figma's Collab-seat MCP limit prevented canvas editing. It is not a completed design file. Open it, run `design/figma-safari-redesign.js` in the Scripter plugin, and the script creates a new page with themed variables, a reusable glass component, editable vectors/text and phone/tablet reference screens. Sample content is illustrative and is never added to the app's stored browser data. The script refuses to duplicate its own page.

## Design references

- https://support.apple.com/en-euro/guide/iphone/ipha9ffea1a3/27/ios/27 — Compact, Bottom and Top layouts.
- https://support.apple.com/en-gb/guide/safari/ibrw1045/27.0/mac/27 — Separate desktop tab strip and Smart Search field.
- https://developer.apple.com/documentation/safari-release-notes — current Safari release family.

## Device checks

Run `./gradlew assembleDebug assembleDebugAndroidTest`. Run `./gradlew connectedDebugAndroidTest` with both a phone and a tablet emulator. The smoke tests cover tab overview, private mode, address entry and library/download controls. Manually verify 360dp/390dp phones, 600dp split-screen and 840dp/1200dp tablets, landscape, light/dark appearance and enlarged text. Verify forward/back, reload/stop, long-press menus, tab gestures, search, bookmarking, Reader, summarization, media sniffing/player, downloads and private cookies on device before release. Download, browser engine, ad-block, media and storage backend code is unchanged except for the one-time visual layout preference migration.

## Verification for this update

- `:app:assembleDebug` and `:app:assembleDebugAndroidTest` completed with exit code 0 using JDK 17 / Android SDK 35.
- The two interaction smoke tests compiled; no emulator or physical device was available to execute them.
- `git diff --check` and JavaScript module syntax validation pass.
- The Figma script executed against a local scene-model mock: four screen frames, 80 text nodes, 41 reusable material instances, and no raster image nodes. This checks script construction; it is not validation inside Figma. The generated `safari-reference.svg` is a design reference, not an Android screenshot.
- Browser/download/media/player/ad-block sources and AndroidManifest.xml are byte-identical to the base commit.
- GitHub branch creation was rejected with HTTP 403, so this update is not published to the remote repository.
