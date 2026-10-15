package com.ibrowser.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import com.ibrowser.MainActivity
import com.ibrowser.browser.Browser
import com.ibrowser.browser.BrowserTab
import com.ibrowser.data.Store
import com.ibrowser.data.ToolbarLayout
import kotlin.math.abs

@Composable
fun BrowserApp() {
    val tab = Browser.current
    val layout = Store.settings.layout
    val c = Ios.c
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topBarH = if (layout == ToolbarLayout.TOP && !Browser.toolbarHidden) (if (Browser.toolbarCollapsed) 34.dp else 64.dp) else 0.dp
    val bottomInset = when (layout) {
        ToolbarLayout.COMPACT -> navBottom
        ToolbarLayout.BOTTOM -> navBottom
        ToolbarLayout.TOP -> navBottom + if (Browser.toolbarCollapsed || Browser.toolbarHidden) 0.dp else 56.dp
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        // status-bar tint (uses the site's theme-color like Safari)
        val statusColor = tab?.themeColor?.let { Color(it) } ?: c.background
        Box(Modifier.fillMaxWidth().height(statusTop).background(if (tab?.isStartPage == true) c.grouped else statusColor))

        if (tab != null) {
            Box(Modifier.fillMaxSize().padding(top = statusTop + topBarH, bottom = bottomInset)) {
                WebContent(tab)
            }
            if (tab.isStartPage) {
                Box(Modifier.fillMaxSize()) { StartPage(tab, inSheet = false) }
            }
        }

        // ---- Toolbars ----
        if (tab != null && !Browser.findMode) {
            when (layout) {
                ToolbarLayout.COMPACT -> Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp)) {
                    CompactToolbar(tab)
                }
                ToolbarLayout.BOTTOM -> Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp)) {
                    BottomToolbar(tab)
                }
                ToolbarLayout.TOP -> {
                    Box(Modifier.align(Alignment.TopCenter).statusBarsPadding()) { TopAddressBar(tab) }
                    Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { TopLayoutBottomBar(tab) }
                }
            }
        }

        if (Browser.findMode) {
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 6.dp)) { FindBar() }
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
        ReaderOverlay(Browser.reader)
        ContextMenuOverlay(Browser.contextTarget)
        Browser.pendingDownload?.let { DownloadDialog(it) }
        FullscreenOverlay()
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
    val shift by animateFloatAsState(dragX * 0.4f, spring(), label = "pillShift")
    Box(
        modifier
            .height(height.dp)
            .graphicsLayer { translationX = shift }
            .glass(RoundedCornerShape((height / 2).dp))
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
            .clickable(interactionSource = null, indication = null) { Browser.editing = true }
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // Page menu
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { Browser.showPageMenu = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Outlined.Article, "Page menu", tint = c.label, modifier = Modifier.size(21.dp))
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
                    if (tab.isStartPage) "Search or enter website name" else tab.host.ifBlank { tab.url },
                    style = Ios.callout.copy(fontWeight = if (tab.isStartPage) FontWeight.Normal else FontWeight.Medium),
                    color = if (tab.isStartPage) c.secondary else c.label,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (tab.media.isNotEmpty()) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable { Browser.showMedia = true },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(c.tint), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ArrowDownward, "Media found", tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                }
            }
            if (!tab.isStartPage) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { if (tab.loading) Browser.stop() else Browser.reload() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (tab.loading) Icons.Outlined.Close else Icons.Outlined.Refresh, "Reload", tint = c.label, modifier = Modifier.size(20.dp))
                }
            } else Spacer(Modifier.width(40.dp))
        }
        if (tab.loading && tab.progress in 1..99) {
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(tab.progress / 100f).height(2.5.dp)
                    .padding(horizontal = 0.dp).background(c.tint)
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
            .glass(RoundedCornerShape(15.dp), elevation = 8.dp)
            .clickable { Browser.toolbarCollapsed = false; Browser.toolbarHidden = false }
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
private fun CompactToolbar(tab: BrowserTab) {
    val collapsed = (Browser.toolbarCollapsed || Browser.toolbarHidden) && !tab.isStartPage
    AnimatedContent(
        collapsed,
        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.9f)) },
        label = "compact"
    ) { col ->
        if (col) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CollapsedPill(tab) }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassCircleButton(
                    Icons.Outlined.ArrowBackIosNew, "Back",
                    enabled = tab.canGoBack || !tab.isStartPage,
                    onLongClick = { if (tab.canGoForward) Browser.forward() }
                ) { Browser.back() }
                if (tab.canGoForward) {
                    GlassCircleButton(Icons.Outlined.ArrowForwardIos, "Forward", size = 40.dp) { Browser.forward() }
                }
                AddressPill(tab, Modifier.weight(1f))
                TabsButton()
            }
        }
    }
}

@Composable
private fun TabsButton(size: Int = 48) {
    GlassCircleButton(
        Icons.Outlined.FilterNone, "Tabs", size = size.dp,
        onLongClick = { Browser.showTabMenu = true }
    ) {
        Browser.captureThumbnail(); Browser.showTabs = true
    }
}

// ------------------------------------------------------------------ Bottom layout

@Composable
private fun BottomToolbar(tab: BrowserTab) {
    val collapsed = (Browser.toolbarCollapsed || Browser.toolbarHidden) && !tab.isStartPage
    AnimatedContent(collapsed, label = "bottom") { col ->
        if (col) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CollapsedPill(tab) }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                    .glass(RoundedCornerShape(30.dp))
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                AddressPill(tab, Modifier.fillMaxWidth(), height = 46)
                Spacer(Modifier.height(2.dp))
                ToolbarRow(tab)
            }
        }
    }
}

@Composable
private fun ToolbarRow(tab: BrowserTab) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        ToolbarIcon(Icons.Outlined.ArrowBackIosNew, "Back", enabled = tab.canGoBack || !tab.isStartPage) { Browser.back() }
        ToolbarIcon(Icons.Outlined.ArrowForwardIos, "Forward", enabled = tab.canGoForward) { Browser.forward() }
        ToolbarIcon(Icons.Outlined.IosShare, "Share", enabled = !tab.isStartPage) { Browser.share(tab.url, tab.title) }
        ToolbarIcon(Icons.AutoMirrored.Outlined.MenuBook, "Bookmarks") { Browser.startSection = 1; Browser.showLibrary = true }
        ToolbarIcon(Icons.Outlined.FilterNone, "Tabs", onLongClick = { Browser.showTabMenu = true }) {
            Browser.captureThumbnail(); Browser.showTabs = true
        }
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
    AnimatedVisibility(
        !(Browser.toolbarCollapsed || Browser.toolbarHidden) || tab.isStartPage,
        enter = slideInVertically { it }, exit = slideOutVertically { it }
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).glass(RoundedCornerShape(28.dp)).padding(4.dp)) {
            ToolbarRow(tab)
        }
    }
}

// ------------------------------------------------------------------ Tab button menu (long press)

@Composable
private fun TabButtonMenu(visible: Boolean) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Ios.c.scrim).clickable(interactionSource = null, indication = null) { Browser.showTabMenu = false }) {
            Column(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp, bottom = 70.dp).width(250.dp)
                    .glass(RoundedCornerShape(22.dp), strong = true).padding(vertical = 6.dp)
            ) {
                MenuItem(Icons.Outlined.Add, "New Tab") { Browser.showTabMenu = false; Browser.newTab(private = false); Browser.editing = true }
                MenuItem(Icons.Outlined.VisibilityOff, "New Private Tab") { Browser.showTabMenu = false; Browser.newTab(private = true); Browser.editing = true }
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
        Box(Modifier.size(36.dp).clip(CircleShape).clickable { Browser.findNext(false) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.KeyboardArrowUp, "Previous", tint = c.tint)
        }
        Box(Modifier.size(36.dp).clip(CircleShape).clickable { Browser.findNext(true) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.KeyboardArrowDown, "Next", tint = c.tint)
        }
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
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
