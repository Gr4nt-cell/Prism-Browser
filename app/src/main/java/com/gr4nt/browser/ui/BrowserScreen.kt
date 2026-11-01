package com.gr4nt.browser.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.gr4nt.browser.MainActivity
import com.gr4nt.browser.browser.Browser
import com.gr4nt.browser.browser.BrowserTab
import com.gr4nt.browser.data.Store
import com.gr4nt.browser.data.ToolbarLayout
import kotlin.math.abs

@Composable
fun BrowserApp() {
    val base = Ios.c
    val current = Browser.current
    val palette = if (current?.isPrivate == true) DarkIos else base
    val desktop = isLargeScreen()
    val statusColor = if (!desktop && current?.isStartPage == false) current.themeColor?.let { Color(it) } ?: palette.background else palette.background
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = statusColor.luminance() > .5f
                isAppearanceLightNavigationBars = !palette.dark
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalIos provides palette) { BrowserAppContent() }
}

@Composable
private fun BrowserAppContent() {
    val tab = Browser.current
    val layout = Store.settings.layout
    val c = Ios.c
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // tablets / desktops get the Safari-for-Mac chrome at the top instead of the phone layouts
    val desktop = isLargeScreen()
    val wideWorkspace = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 1000
    LaunchedEffect(desktop, wideWorkspace) { if (desktop) DesktopWorkspace.sidebarOpen = wideWorkspace }
    val topBarH = when {
        desktop -> 0.dp
        layout == ToolbarLayout.TOP && !Browser.toolbarHidden -> if (Browser.toolbarCollapsed) 34.dp else 64.dp
        else -> 0.dp
    }
    val bottomInset = if (desktop) navBottom else when (layout) {
        ToolbarLayout.ARC, ToolbarLayout.COMPACT -> navBottom
        ToolbarLayout.BOTTOM -> navBottom
        ToolbarLayout.TOP -> navBottom + if (Browser.toolbarCollapsed || Browser.toolbarHidden) 0.dp else 56.dp
    }

    Box(
        Modifier.fillMaxSize().wallpaper()
            // watch every touch (also those going into the web page) without consuming it
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        when (e.type) {
                            androidx.compose.ui.input.pointer.PointerEventType.Press -> Browser.onTouch(true)
                            androidx.compose.ui.input.pointer.PointerEventType.Release -> Browser.onTouch(false)
                            else -> {}
                        }
                    }
                }
            }
    ) {
        // status-bar tint (uses the site's theme-color like Safari)
        val statusColor = tab?.themeColor?.let { Color(it) } ?: c.background
        if (tab?.isStartPage != true && !desktop) Box(Modifier.fillMaxWidth().height(statusTop).background(statusColor))

        if (tab != null && desktop) {
            val startPad by androidx.compose.animation.core.animateDpAsState(
                WORKSPACE_RAIL_WIDTH + if (DesktopWorkspace.sidebarOpen && wideWorkspace) WORKSPACE_SIDEBAR_WIDTH + 16.dp else 0.dp,
                spring(dampingRatio = 0.9f, stiffness = 380f), label = "safariSidebar"
            )
            Box(Modifier.fillMaxSize().padding(start = startPad, top = statusTop + WORKSPACE_CHROME_HEIGHT + 8.dp, bottom = navBottom + 8.dp, end = 8.dp).clip(RoundedCornerShape(22.dp))) {
                WebContent(tab)
                if (tab.isStartPage) StartPage(tab, inSheet = false)
            }
        } else if (tab != null) {
            Box(Modifier.fillMaxSize().padding(top = statusTop + topBarH, bottom = bottomInset)) {
                WebContent(tab)
            }
            if (tab.isStartPage) {
                Box(Modifier.fillMaxSize()) { StartPage(tab, inSheet = false) }
            }
        }

        if (tab != null && !desktop && layout == ToolbarLayout.BOTTOM && !tab.isStartPage && !Browser.editing && !Browser.showTabs) {
            Box(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 2.dp).fillMaxWidth().height(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tab.host, style = Ios.caption.copy(fontSize = 12.sp), color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // ---- Toolbars ----
        // ---- Adaptive glass: sample the page colour next to each bar ----
        var bottomBounds by remember { mutableStateOf<Rect?>(null) }
        var topBounds by remember { mutableStateOf<Rect?>(null) }
        val sampling = tab != null && !tab.isStartPage && !Browser.editing && !Browser.showTabs && Browser.customView == null &&
            Browser.reader == null && !Browser.showPageMenu && !Browser.showTabMenu && Browser.contextTarget == null &&
            !Browser.showDownloads && !Browser.showSettings && !Browser.showMedia && !Browser.showLibrary &&
            !Browser.showPrivacy && !Browser.showSiteSettings && Browser.pendingDownload == null &&
            !Browser.showActions && !Browser.showArchive && Browser.summary == null && !Browser.summarizing
        val bottomBackdrop by rememberBackdropColor({ bottomBounds }, SampleSide.ABOVE, sampling)

        // ---- Auto-hide: the toolbar slides away after a few seconds without any touch ----
        val canAutoHide = !desktop && sampling && Store.settings.autoHideToolbar && !Browser.findMode && Browser.contextTarget == null
        LaunchedEffect(Browser.lastTouchAt, canAutoHide, Store.settings.autoHideSeconds) {
            if (!canAutoHide) { Browser.idleHidden = false; return@LaunchedEffect }
            kotlinx.coroutines.delay(Store.settings.autoHideSeconds * 1000L)
            if (Browser.fingersDown == 0) Browser.idleHidden = true
        }
        val idle by animateFloatAsState(
            if (Browser.idleHidden) 1f else 0f,
            if (Browser.idleHidden) androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            else spring(dampingRatio = 0.82f, stiffness = 420f),
            label = "idleHide"
        )
        val hideBottom = Modifier.graphicsLayer { translationY = idle * 120.dp.toPx(); alpha = 1f - idle; scaleX = 1f - idle * 0.06f; scaleY = 1f - idle * 0.06f }
        val hideTop = Modifier.graphicsLayer { translationY = -idle * 100.dp.toPx(); alpha = 1f - idle }
        val topBackdrop by rememberBackdropColor({ topBounds }, SampleSide.BELOW, sampling && (desktop || layout == ToolbarLayout.TOP))

        if (tab != null && desktop) {
            Box(Modifier.align(Alignment.TopCenter).padding(start = WORKSPACE_RAIL_WIDTH).statusBarsPadding().onGloballyPositioned { topBounds = it.boundsInWindow() }) {
                AdaptiveGlass(topBackdrop) { WorkspaceChrome(tab) }
            }
            WorkspaceRail(tab, Modifier.align(Alignment.TopStart).statusBarsPadding().navigationBarsPadding())
            androidx.compose.animation.AnimatedVisibility(
                DesktopWorkspace.sidebarOpen,
                Modifier.align(Alignment.TopStart).padding(top = statusTop + WORKSPACE_CHROME_HEIGHT + 8.dp, start = WORKSPACE_RAIL_WIDTH + 8.dp, bottom = navBottom + 8.dp),
                enter = androidx.compose.animation.slideInHorizontally { -it } + fadeIn(),
                exit = androidx.compose.animation.slideOutHorizontally { -it } + fadeOut()
            ) { WorkspaceSidebar(tab) }
        } else if (tab != null && !Browser.findMode) {
            when (layout) {
                ToolbarLayout.ARC -> Box(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp)
                        .onGloballyPositioned { bottomBounds = it.boundsInWindow() }.then(hideBottom)
                ) {
                    AdaptiveGlass(bottomBackdrop) { CompactToolbar(tab) }
                }
                ToolbarLayout.COMPACT -> Box(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp)
                        .onGloballyPositioned { bottomBounds = it.boundsInWindow() }.then(hideBottom)
                ) {
                    AdaptiveGlass(bottomBackdrop) { CompactToolbar(tab) }
                }
                ToolbarLayout.BOTTOM -> Box(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp)
                        .onGloballyPositioned { bottomBounds = it.boundsInWindow() }.then(hideBottom)
                ) {
                    AdaptiveGlass(bottomBackdrop) { BottomToolbar(tab) }
                }
                ToolbarLayout.TOP -> {
                    Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().onGloballyPositioned { topBounds = it.boundsInWindow() }.then(hideTop)) {
                        AdaptiveGlass(topBackdrop) { TopAddressBar(tab) }
                    }
                    Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().onGloballyPositioned { bottomBounds = it.boundsInWindow() }.then(hideBottom)) {
                        AdaptiveGlass(bottomBackdrop) { TopLayoutBottomBar(tab) }
                    }
                }
            }
        }

        if (Browser.findMode) {
            Box(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 6.dp)
                    .onGloballyPositioned { bottomBounds = it.boundsInWindow() }
            ) { AdaptiveGlass(bottomBackdrop) { FindBar() } }
        }

        // ---- Overlays ----
        AddressEntry(Browser.editing && tab != null)
        TabsOverview(Browser.showTabs)
        PageMenu(Browser.showPageMenu && tab != null)
        TabButtonMenu(Browser.showTabMenu)
        DownloadsSheet(Browser.showDownloads)
        MediaSheet(Browser.showMedia)
        SettingsSheet(Browser.showSettings)
        PrivacySheet(Browser.showPrivacy)
        SiteSettingsSheet(Browser.showSiteSettings)
        LibrarySheet(Browser.showLibrary)
        ArcActionsSheet(Browser.showActions && tab != null)
        ArchiveSheet(Browser.showArchive)
        SummaryOverlay(Browser.summary, Browser.summarizing)
        ReaderOverlay(Browser.reader)
        ContextMenuOverlay(Browser.contextTarget)
        // ---- Media: mini player above the toolbar, full glass player on top ----
        if (!Browser.showTabs && !Browser.editing) {
            val lift = when {
                Browser.findMode -> 64.dp
                desktop -> 16.dp
                Browser.idleHidden -> 12.dp
                Browser.toolbarCollapsed || Browser.toolbarHidden -> 46.dp
                layout == ToolbarLayout.TOP -> 70.dp
                layout == ToolbarLayout.BOTTOM -> 142.dp
                else -> 126.dp
            }
            val liftAnim by androidx.compose.animation.core.animateDpAsState(lift, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f), label = "miniLift")
            MiniPlayer(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = liftAnim))
        }
        AppPromptBanner(com.gr4nt.browser.browser.AppLinks.prompt, Modifier.align(Alignment.TopCenter))
        Browser.pendingDownload?.let { DownloadDialog(it) }
        PermissionPromptOverlay(com.gr4nt.browser.browser.SitePermissions.prompt)
        FullscreenOverlay()
        MediaPlayerOverlay()
    }
}

@Composable
private fun WebContent(tab: BrowserTab) {
    AndroidView(
        factory = { ctx -> FrameLayout(ctx) },
        update = { fl ->
            val wv = Browser.webViewFor(tab, fl.context)
            if (wv.parent !== fl) {
                (wv.parent as? ViewGroup)?.removeView(wv)
                fl.removeAllViews()
                fl.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            wv.onResume()
        },
        modifier = Modifier.fillMaxSize()
    )
}

// ------------------------------------------------------------------ address pill

@Composable
fun AddressPill(tab: BrowserTab, modifier: Modifier = Modifier, height: Int = 48) {
    val c = Ios.c
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val shift by animateFloatAsState(dragX * 0.45f, Motion.bouncy, label = "pillShift")
    val lift by animateFloatAsState(if (dragY < 0) (dragY * 0.25f).coerceAtLeast(-40f) else 0f, Motion.bouncy, label = "pillLift")
    var pressed by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (pressed) 1f else 0f, Motion.smooth, label = "pillGlow")
    Box(
        modifier
            .height(height.dp)
            .graphicsLayer { translationX = shift; translationY = lift }
            .pressable(scaleTo = 0.97f, haptic = false, onPressedChange = { pressed = it }) { Browser.editing = true }
            .glass(RoundedCornerShape(16.dp), glow = glow)
            .pointerInput(tab.id) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            dragY < -90 && abs(dragY) > abs(dragX) -> { Browser.captureThumbnail(); Browser.showTabs = true }
                            dragX < -120 -> Browser.switchRelative(1)
                            dragX > 120 -> Browser.switchRelative(-1)
                        }
                        dragX = 0f; dragY = 0f
                    },
                    onDragCancel = { dragX = 0f; dragY = 0f }
                ) { change, amount ->
                    change.consume(); dragX += amount.x; dragY += amount.y
                }
            }
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // Page menu
            Box(contentAlignment = Alignment.Center) {
                InlineIconButton(androidx.compose.material.icons.Icons.Outlined.Tune, "Page menu", iconSize = 20.dp) { Browser.showPageMenu = true }
                if (tab.blocked > 0 && Store.settings.adBlock) {
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 6.dp).size(7.dp).clip(CircleShape).background(c.green))
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (tab.isPrivate) {
                    Icon(Icons.Outlined.VisibilityOff, null, tint = c.secondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                if (!tab.isStartPage && tab.secure) {
                    Icon(Icons.Filled.Lock, null, tint = c.secondary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    if (tab.isStartPage) "Search or enter address" else tab.host.ifBlank { tab.url },
                    style = Ios.callout.copy(fontSize = (if (height < 44) 13 else 15).sp, fontWeight = if (tab.isStartPage) FontWeight.Normal else FontWeight.Medium),
                    color = if (tab.isStartPage) c.secondary else c.label,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (tab.media.isNotEmpty()) {
                Box(
                    Modifier.size(34.dp).pressable(scaleTo = 0.8f) { Browser.showMedia = true },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(c.tint), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ArrowDownward, "Media found", tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                }
            }
            if (!tab.isStartPage) {
                InlineIconButton(if (tab.loading) Icons.Outlined.Close else Icons.Outlined.Refresh, "Reload") {
                    if (tab.loading) Browser.stop() else Browser.reload()
                }
            } else Spacer(Modifier.width(40.dp))
        }
        val prog by animateFloatAsState(if (tab.loading) tab.progress / 100f else 1f, Motion.smooth, label = "progress")
        val progAlpha by animateFloatAsState(if (tab.loading && tab.progress in 1..99) 1f else 0f, tween(350), label = "progressAlpha")
        if (progAlpha > 0f) {
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(prog.coerceIn(0f, 1f)).height(2.5.dp)
                    .graphicsLayer { alpha = progAlpha }.background(c.tint)
            )
        }
    }
}

@Composable
private fun CollapsedPill(tab: BrowserTab) {
    val c = Ios.c
    Row(
        Modifier
            .height(30.dp)
            .pressable(scaleTo = 0.92f) { Browser.toolbarCollapsed = false; Browser.toolbarHidden = false }
            .glass(RoundedCornerShape(15.dp), elevation = 10.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tab.secure) {
            Icon(Icons.Filled.Lock, null, tint = c.secondary, modifier = Modifier.size(10.dp)); Spacer(Modifier.width(4.dp))
        }
        Text(tab.host.ifBlank { "Start Page" }, style = Ios.footnote.copy(fontWeight = FontWeight.Medium), color = c.label, maxLines = 1)
    }
}

// ------------------------------------------------------------------ Compact (iOS 27 default)

@Composable
private fun CompactToolbar(tab: BrowserTab) = CommandDock(tab)

@Composable
private fun TabsButton(size: Int = 48) {
    GlassCircleButton(
        SafariSymbols.Tabs, "Tabs", size = size.dp,
        onLongClick = { Browser.showTabMenu = true }
    ) {
        Browser.captureThumbnail(); Browser.showTabs = true
    }
}

// ------------------------------------------------------------------ Bottom layout

@Composable
private fun BottomToolbar(tab: BrowserTab) = CommandDock(tab, expanded = true)

@Composable
private fun TabsCountToolbarButton(tab: BrowserTab) {
    val c = Ios.c
    val count = Browser.tabs.count { it.isPrivate == tab.isPrivate }
    Box(
        Modifier.size(44.dp).pressable(scaleTo = 0.8f, onLongClick = { Browser.showTabMenu = true }) {
            Browser.captureThumbnail(); Browser.showTabs = true
        },
        contentAlignment = Alignment.Center
    ) {
        Icon(SafariSymbols.Tabs, "Tabs", tint = c.tint, modifier = Modifier.size(24.dp))
        Text(
            count.coerceAtMost(99).toString(),
            style = Ios.caption2.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = c.tint,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp).background(c.glassStrong, CircleShape).padding(horizontal = 3.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun ToolbarRow(tab: BrowserTab) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        ToolbarIcon(SafariSymbols.Back, "Back", enabled = tab.canGoBack || !tab.isStartPage) { Browser.back() }
        ToolbarIcon(SafariSymbols.Forward, "Forward", enabled = tab.canGoForward) { Browser.forward() }
        ToolbarIcon(SafariSymbols.Share, "Share", enabled = !tab.isStartPage) { Browser.share(tab.url, tab.title) }
        ToolbarIcon(Icons.AutoMirrored.Outlined.MenuBook, "Bookmarks") { Browser.startSection = 1; Browser.showLibrary = true }
        TabsCountToolbarButton(tab)
    }
}

// ------------------------------------------------------------------ Top layout

@Composable
private fun TopAddressBar(tab: BrowserTab) {
    val collapsed = (Browser.toolbarCollapsed || Browser.toolbarHidden) && !tab.isStartPage
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        if (collapsed) CollapsedPill(tab) else AddressPill(tab, Modifier.fillMaxWidth(), height = 46)
    }
}

@Composable
private fun TopLayoutBottomBar(tab: BrowserTab) {
    if (!Browser.toolbarHidden && !Browser.toolbarCollapsed) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
        .glass(RoundedCornerShape(22.dp), elevation = 6.dp).padding(horizontal = 8.dp, vertical = 4.dp)) { CommandNavigation(tab) }
}

@Composable
private fun TabButtonMenu(visible: Boolean) {
    AnimatedVisibility(visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(Ios.c.scrim).clickable(interactionSource = null, indication = null) { Browser.showTabMenu = false }) {
            Column(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp, bottom = 136.dp).width(250.dp)
                    .animateEnterExit(
                        enter = scaleIn(Motion.bouncy, initialScale = 0.4f, transformOrigin = TransformOrigin(0.92f, 1f)) + fadeIn(tween(120)),
                        exit = scaleOut(Motion.smooth, targetScale = 0.5f, transformOrigin = TransformOrigin(0.92f, 1f)) + fadeOut(tween(120))
                    )
                    .glass(RoundedCornerShape(24.dp), strong = true).padding(vertical = 6.dp)
            ) {
                MenuItem(Icons.Outlined.Add, "New Tab") { Browser.showTabMenu = false; Browser.newTab(private = false); Browser.editing = true }
                MenuItem(Icons.Outlined.VisibilityOff, "New Private Tab") { Browser.showTabMenu = false; Browser.newTab(private = true); Browser.editing = true }
                MenuItem(Icons.Outlined.Inventory2, "Archive", trailing = Store.archive.items.size.takeIf { it > 0 }?.toString()) { Browser.showTabMenu = false; Browser.showArchive = true }
                MenuDivider()
                MenuItem(Icons.Outlined.Close, "Close This Tab", destructive = true) {
                    Browser.showTabMenu = false; Browser.currentId?.let { Browser.close(it) }
                }
                MenuItem(Icons.Outlined.Close, "Close All ${Browser.tabs.count { it.isPrivate == (Browser.current?.isPrivate ?: false) }} Tabs", destructive = true) {
                    Browser.showTabMenu = false; Browser.closeAll(Browser.current?.isPrivate ?: false)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Find on page

@Composable
private fun FindBar() {
    val c = Ios.c
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(50.dp).glass(RoundedCornerShape(25.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            q, { q = it; Browser.find(it) },
            singleLine = true,
            textStyle = Ios.body.copy(color = c.label),
            cursorBrush = SolidColor(c.tint),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { Browser.findNext(true) }),
            modifier = Modifier.weight(1f).focusRequester(focus),
            decorationBox = { inner ->
                Box { if (q.isEmpty()) Text("Find on Page", style = Ios.body, color = c.secondary); inner() }
            }
        )
        if (q.isNotEmpty()) Text(if (Browser.findCount == 0) "No matches" else "${Browser.findIndex} of ${Browser.findCount}", style = Ios.footnote, color = c.secondary)
        InlineIconButton(Icons.Outlined.KeyboardArrowUp, "Previous", size = 36.dp, iconSize = 24.dp, tint = c.tint) { Browser.findNext(false) }
        InlineIconButton(Icons.Outlined.KeyboardArrowDown, "Next", size = 36.dp, iconSize = 24.dp, tint = c.tint) { Browser.findNext(true) }
        TextAction("Done", bold = true) { Browser.closeFind() }
    }
}

// ------------------------------------------------------------------ Fullscreen video

@Composable
private fun FullscreenOverlay() {
    val v = Browser.customView ?: return
    val act = LocalContext.current as? MainActivity
    DisposableEffect(v) {
        act?.setFullscreen(true)
        onDispose { act?.setFullscreen(false) }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    (v.parent as? ViewGroup)?.removeView(v)
                    addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun MenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    destructive: Boolean = false,
    trailing: String? = null,
    onClick: () -> Unit
) {
    val c = Ios.c
    Row(
        Modifier.fillMaxWidth().tapHighlight(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = Ios.body, color = if (destructive) c.red else c.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) {
            Text(trailing, style = Ios.subhead, color = c.secondary); Spacer(Modifier.width(10.dp))
        }
        Icon(icon, null, tint = if (destructive) c.red else c.label, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun MenuDivider(thick: Boolean = false) {
    Box(Modifier.fillMaxWidth().height(if (thick) 8.dp else 0.5.dp).background(if (thick) Ios.c.fill else Ios.c.separator))
}

@Suppress("unused")
@Composable
private fun Placeholder() = Box(Modifier.fillMaxHeight())

@Suppress("unused")
private val unusedSp = 1.sp
