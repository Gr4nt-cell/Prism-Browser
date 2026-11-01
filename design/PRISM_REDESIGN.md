# Prism Browser

This update replaces the Safari-inspired presentation with an original teal,
ink and warm ivory visual system. System sans-serif typography, quieter outline
icons, compact glass surfaces and consistent rounded rectangles connect every
screen. Light/dark appearance and the existing glass/transparency controls remain. Private sessions use a matching dark material palette, including sheets and navigation.

## Interface

Phones use a command dock: a search/address surface above Back, Forward, Home,
New Tab, a counted tab overview button and browser actions. Long-press Tabs keeps
its existing quick menu. Horizontal address gestures still switch tabs; swipe up
opens the overview. Compact, expanded-with-labels and top-command-bar layouts
remain selectable. Auto-hide/collapse, page loading, media detection, find-on-page
and the mini player keep their original browser actions.

Tablets and desktop-sized Android windows use a 72dp navigation rail and top
command bar. The optional 244dp workspace lists vertical tabs with favicons,
titles, close buttons and long-press actions. At widths of at least 1000dp the
workspace opens beside the page; narrower tablet windows use an overlay so the
page keeps its width. Turning off desktop layout still selects the phone UI.

The new start page has a search hero, library cards, favorites, recent browsing,
privacy report and reading list. Every count and stored site comes from the
existing Store. The references use illustrative local data and example.test;
none of that example data is written into the user's profile.

Tab overview now uses a thumbnail grid on phones and tablets, with search,
normal/private mode selection, swipe-to-close, archive, settings and new-tab
controls. The page-tools panel, address entry, library, settings, downloads and
other shared sheets use the same glass palette. Reader/media/player controls
retain their existing behavior; the browser engine and storage code are unchanged.

Legacy internal names such as `Ios` and `SafariSymbols` are compatibility facades
for existing feature screens. They now resolve to the new palette and Material
outline icons. They do not imply Safari UI, Apple assets or a different web engine.

## Figma

Target: https://www.figma.com/design/BPzlo4VIkIiLgn7RT7RtzJ

The account's Collab-seat MCP limit prevented canvas reads/writes. No new screens
have been written to that file. Run the Figma Scripter plugin and paste
`figma-prism-browser.js` to create the editable `iBrowser / Prism` page. The import
requires Roboto Regular, Medium and Bold to match the stock Android reference
font. It refuses to duplicate an existing page with the same name.

The import contains nine composed screen references, 41 shared components and
12 variables with Light/Dark modes. UI remains editable text, vector icons,
frames and instances. There are no flattened UI screenshots or raster fills.
The supplied SVG and PNGs are visual references, not screenshots of a running app.

Regenerate from the repository root:

```
node design/build-prism-reference.cjs
node design/check-prism-import.cjs
```

The latter uses a local scene-graph mock. It validates script construction,
componentization, variable modes, Roboto text and absence of raster UI. It does
not validate an actual Figma import. Install `design/fonts/Roboto.ttf` for faithful
local SVG rendering; its OFL license is included. Material outline icon sources
and Apache 2.0 notice are in `design/icons`.

## Verification and remaining device checks

Build both the application and its interaction-test APK:

```
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

Run the connected tests on both phone and tablet devices/emulators. The tests
exercise private tabs and address entry, tab long-press actions, workspace library
controls, and downloads/settings access. No Android device/emulator was available
in the build environment, so connected tests have not been executed.

Manual device checks remain for 320/390dp phones, tablet portrait, 600dp split
windows, 1000/1200dp windows, light/dark/private appearance, enlarged fonts, web
navigation, tab selection/close, address gestures, keyboard/focus, Reader, page
summary, downloads, media sniffing/player, website permissions and private data.
This redesign does not replace those browser, download, permission or data engines.

The debug APK is for testing and uses the local debug signing key. A production
update must be built with the existing release signing credentials; changing keys
will not install over a production APK. Do not remove an installed browser or its
profile just to install a debug build.

Source guard verification passed: 35 non-UI application files are byte-identical
to the recorded main revision; all 30 Page-menu Browser calls and 20 feature
overlays remain present. The nine-screen Figma scene mock contains 41 components,
12 variables and 177 instances, with Roboto text and no raster UI fills. Small
text palette checks pass 4.5:1 against all reference background hues. These
source/design checks do not establish device interaction or WebView correctness.
