package com.gr4nt.browser.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartScreen
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gr4nt.browser.browser.Browser
import com.gr4nt.browser.browser.BrowserTab
import com.gr4nt.browser.browser.SummaryContent
import com.gr4nt.browser.data.Store
import com.gr4nt.browser.data.hostOf
import com.gr4nt.browser.download.DownloadManager
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

// ============================================================================ Arc bottom bar

/**
 * Arc Search style bottom bar, in Liquid Glass:
 *   [ tabs (count) ]  [ address capsule ]  [ ^ actions ]
 * Swipe the capsule sideways to switch tabs, up to open the tab gallery; tap it to search.
 * Long-press tabs → new tab, long-press ^ → share sheet (as in Arc).
 */
@Composable
fun ArcToolbar(tab: BrowserTab) {
    val collapsed = (Browser.toolbarCollapsed || Browser.toolbarHidden) && !tab.isStartPage
    AnimatedContent(
        collapsed,
        transitionSpec = {
            (fadeIn(tween(180, delayMillis = 60)) + scaleIn(Motion.bouncy, initialScale = if (targetState) 1.5f else 0.6f)) togetherWith
                (fadeOut(tween(120)) + scaleOut(Motion.smooth, targetScale = if (targetState) 0.6f else 1.5f)) using
                SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.8f, stiffness = 380f) }
        },
        contentAlignment = Alignment.BottomCenter,
        label = "arcBar"
    ) { col ->
        if (col) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ArcMiniPill(tab)
            }
        } else {
            // Arc Search: one bar, tinted by the page — [tabs]  site name  [^]
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(56.dp)
                    .glass(RoundedCornerShape(28.dp))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabCountButton()
                ArcCapsule(tab, Modifier.weight(1f))
                ArcChevron(tab)
            }
        }
    }
}

@Composable
private fun TabCountButton() {
    val c = Ios.c
    val count = Browser.tabs.count { it.isPrivate == (Browser.current?.isPrivate ?: false) }
    var pressed by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (pressed) 1f else 0f, Motion.smooth, label = "tabsGlow")
    Box(
        Modifier.size(48.dp)
            .pressable(scaleTo = 0.84f, onLongClick = { Browser.newTab(); Browser.editing = true }, onPressedChange = { pressed = it }) {
                Browser.captureThumbnail(); Browser.showTabs = true
            }
            .clip(CircleShape).background(c.fill.copy(alpha = c.fill.alpha * 2.2f * glow)),
        contentAlignment = Alignment.Center
    ) {
        // Arc's tab icon: a rounded square with the tab count inside
        // Arc's tab icon: two stacked rounded squares, the front one holding the tab count
        Box(Modifier.offset(x = 3.dp, y = (-3).dp).size(22.dp).border(1.8.dp, c.label.copy(alpha = 0.45f), RoundedCornerShape(6.dp)))
        Box(
            Modifier.offset(x = (-1).dp, y = 1.dp).size(22.dp).clip(RoundedCornerShape(6.dp)).background(c.glassStrong)
                .border(1.8.dp, c.label, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (count > 99) "∞" else "$count",
                style = Ios.caption2.copy(fontWeight = FontWeight.Bold, fontSize = if (count > 9) 10.sp else 12.sp),
                color = c.label
            )
        }
    }
}

@Composable
private fun ArcCapsule(tab: BrowserTab, modifier: Modifier) {
    val c = Ios.c
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val shift by animateFloatAsState(dragX * 0.45f, Motion.bouncy, label = "capShift")
    val lift by animateFloatAsState(if (dragY < 0) (dragY * 0.25f).coerceAtLeast(-40f) else 0f, Motion.bouncy, label = "capLift")
    var pressed by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (pressed) 1f else 0f, Motion.smooth, label = "capGlow")
    Box(
        modifier
            .height(46.dp)
            .graphicsLayer { translationX = shift; translationY = lift }
            .pressable(scaleTo = 0.97f, haptic = false, onPressedChange = { pressed = it }) { Browser.editing = true }
            .clip(RoundedCornerShape(23.dp)).background(c.fill.copy(alpha = c.fill.alpha * 1.6f * glow))
            .pointerInput(tab.id) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            dragY < -80 && abs(dragY) > abs(dragX) -> { Browser.captureThumbnail(); Browser.showTabs = true }
                            dragX < -110 -> Browser.switchRelative(1)
                            dragX > 110 -> Browser.switchRelative(-1)
                        }
                        dragX = 0f; dragY = 0f
                    },
                    onDragCancel = { dragX = 0f; dragY = 0f }
                ) { ch, amount -> ch.consume(); dragX += amount.x; dragY += amount.y }
            }
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (tab.isStartPage) {
                Icon(Icons.Outlined.Search, null, tint = c.secondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Search or enter a URL", style = Ios.callout, color = c.secondary, maxLines = 1, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            } else {
                if (tab.isPrivate) { Icon(Icons.Outlined.VisibilityOff, null, tint = c.secondary, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(5.dp)) }
                if (tab.secure) { Icon(Icons.Outlined.Lock, null, tint = c.secondary, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(5.dp)) }
                Text(
                    tab.host.ifBlank { tab.url }, style = Ios.callout.copy(fontWeight = FontWeight.SemiBold),
                    color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                if (tab.media.isNotEmpty()) {
                    Box(
                        Modifier.size(26.dp).pressable(scaleTo = 0.8f) { Browser.showMedia = true }.clip(CircleShape).background(c.tint),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.ArrowDownward, "Media found", tint = Color.White, modifier = Modifier.size(15.dp)) }
                }
                if (tab.blocked > 0 && Store.settings.adBlock && tab.media.isEmpty()) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(c.green))
                }
            }
        }
        val prog by animateFloatAsState(if (tab.loading) tab.progress / 100f else 1f, Motion.smooth, label = "capProg")
        val progAlpha by animateFloatAsState(if (tab.loading && tab.progress in 1..99) 1f else 0f, tween(350), label = "capProgA")
        if (progAlpha > 0f) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp).fillMaxWidth(prog.coerceIn(0f, 1f)).height(2.5.dp)
                    .graphicsLayer { alpha = progAlpha }.clip(CircleShape).background(c.tint)
            )
        }
    }
}

/** Arc's "^": opens the actions sheet; long-press shares the page. */
@Composable
private fun ArcChevron(tab: BrowserTab) {
    val c = Ios.c
    var pressed by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (pressed) 1f else 0f, Motion.smooth, label = "chevGlow")
    Box(
        Modifier.size(48.dp)
            .pressable(scaleTo = 0.84f, onLongClick = { if (!tab.isStartPage) Browser.share(tab.url, tab.title) }, onPressedChange = { pressed = it }) {
                Browser.showActions = true
            }
            .clip(CircleShape).background(c.fill.copy(alpha = c.fill.alpha * 2.2f * glow)),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Outlined.KeyboardArrowUp, "Actions", tint = c.label, modifier = Modifier.size(28.dp)) }
}

@Composable
private fun ArcMiniPill(tab: BrowserTab) {
    val c = Ios.c
    Row(
        Modifier.height(30.dp)
            .pressable(scaleTo = 0.92f) { Browser.toolbarCollapsed = false; Browser.toolbarHidden = false }
            .glass(RoundedCornerShape(15.dp), elevation = 10.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tab.secure) { Icon(Icons.Outlined.Lock, null, tint = c.secondary, modifier = Modifier.size(10.dp)); Spacer(Modifier.width(4.dp)) }
        Text(tab.host.ifBlank { "Search" }, style = Ios.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1)
    }
}

// ============================================================================ "^" actions sheet

@Composable
fun ArcActionsSheet(visible: Boolean) {
    AnimatedVisibility(visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(200))) {
        val c = Ios.c
        val t = Browser.current ?: return@AnimatedVisibility
        fun act(f: () -> Unit) { Browser.showActions = false; f() }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (c.dark) 0.4f else 0.18f))
                .clickable(interactionSource = null, indication = null) { Browser.showActions = false },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .animateEnterExit(
                        enter = slideInVertically(spring(dampingRatio = 0.84f, stiffness = 380f)) { it } +
                            scaleIn(Motion.bouncy, initialScale = 0.92f, transformOrigin = TransformOrigin(0.9f, 1f)),
                        exit = slideOutVertically(spring(dampingRatio = 1f, stiffness = 500f)) { it }
                    )
                    .clickable(interactionSource = null, indication = null) { }
                    .glass(RoundedCornerShape(34.dp), strong = true)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(5.dp).clip(CircleShape).background(c.tertiary))
                }
                // address row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).height(46.dp).pressable(scaleTo = 0.97f) { act { Browser.editing = true } }
                            .clip(RoundedCornerShape(23.dp)).background(c.fill).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!t.isStartPage && t.secure) { Icon(Icons.Outlined.Lock, null, tint = c.secondary, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(6.dp)) }
                        Text(
                            if (t.isStartPage) "Search or enter a URL" else t.url.removePrefix("https://").removePrefix("http://"),
                            style = Ios.subhead, color = if (t.isStartPage) c.secondary else c.label, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!t.isStartPage) {
                        Spacer(Modifier.width(8.dp))
                        InlineIconButton(Icons.Outlined.PushPin, "Pin to Favorites", size = 46.dp, iconSize = 21.dp) { act { Browser.addFavorite() } }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (!t.isStartPage) {
                    // Arc's big buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigAction(Icons.Outlined.ChromeReaderMode, "Reader", Modifier.weight(1f)) { act { Browser.openReader() } }
                        BigAction(Icons.Outlined.Search, "Find", Modifier.weight(1f)) { act { Browser.startFind() } }
                        BigAction(Icons.Outlined.ContentCopy, "Copy Link", Modifier.weight(1f)) { act { Browser.copy(t.url) } }
                        BigAction(Icons.Outlined.Share, "Share", Modifier.weight(1f)) { act { Browser.share(t.url, t.title) } }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigAction(Icons.AutoMirrored.Outlined.ArrowBack, "Back", Modifier.weight(1f), enabled = t.canGoBack || !t.isStartPage) { act { Browser.back() } }
                        BigAction(Icons.AutoMirrored.Outlined.ArrowForward, "Forward", Modifier.weight(1f), enabled = t.canGoForward) { act { Browser.forward() } }
                        BigAction(Icons.Outlined.Refresh, "Reload", Modifier.weight(1f)) { act { Browser.reload() } }
                        BigAction(Icons.Outlined.AutoAwesome, "Summarize", Modifier.weight(1f)) { act { Browser.summarize() } }
                    }
                    Spacer(Modifier.height(12.dp))
                    // text size
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((label, size, delta) in listOf(Triple("A", 13, -10), Triple("${t.textZoom}%", 15, 0), Triple("A", 21, 10))) {
                            Box(
                                Modifier.weight(1f).height(40.dp).pressable(scaleTo = 0.9f) {
                                    Browser.setTextZoom(if (delta == 0) Store.settings.textZoom else t.textZoom + delta)
                                }.clip(RoundedCornerShape(14.dp)).background(c.fill),
                                contentAlignment = Alignment.Center
                            ) { Text(label, fontSize = size.sp, fontWeight = if (delta == 0) FontWeight.SemiBold else FontWeight.Normal, color = c.label) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(22.dp))) {
                    if (!t.isStartPage) {
                        MenuItem(Icons.Outlined.BookmarkAdd, "Add Bookmark") { act { Browser.addBookmark() } }
                        MenuItem(Icons.Outlined.AddBox, "Add to Reading List") { act { Browser.addReadingList() } }
                        MenuItem(Icons.AutoMirrored.Outlined.OpenInNew, "Open in App…") { act { Browser.activity?.let { com.gr4nt.browser.browser.AppLinks.openWith(it, t.url) } } }
                        MenuItem(Icons.Outlined.Movie, "Media on This Page", trailing = if (t.media.isEmpty()) "None" else "${t.media.size}") { act { Browser.showMedia = true } }
                    }
                    MenuItem(Icons.Outlined.Download, "Downloads",
                        trailing = DownloadManager.items.count { it.isActive }.takeIf { it > 0 }?.toString()) { act { Browser.showDownloads = true } }
                    MenuItem(Icons.Outlined.FolderOpen, "Open File…") { act { (Browser.activity as? com.gr4nt.browser.MainActivity)?.openFilePicker() } }
                    MenuItem(Icons.AutoMirrored.Outlined.MenuBook, "Bookmarks & History") { act { Browser.startSection = 1; Browser.showLibrary = true } }
                    MenuItem(Icons.Outlined.Inventory2, "Archive", trailing = Store.archive.items.size.takeIf { it > 0 }?.toString()) { act { Browser.showArchive = true } }
                    if (!t.isStartPage) {
                        MenuItem(if (t.desktop) Icons.Outlined.SmartScreen else Icons.Outlined.DesktopWindows,
                            if (t.desktop) "Request Mobile Website" else "Request Desktop Website") { act { Browser.toggleDesktop() } }
                        MenuItem(Icons.Outlined.Translate, "Translate Page") { act { Browser.translate() } }
                        MenuItem(Icons.Outlined.Print, "Print or Save as PDF") { act { Browser.print() } }
                        val off = t.host in Store.settings.disabledAdSites
                        MenuItem(Icons.Outlined.Shield,
                            if (!Store.settings.adBlock) "Ad Blocking Off" else if (off) "Resume Ad Blocking" else "Pause Ad Blocking",
                            trailing = if (Store.settings.adBlock && !off) "${t.blocked} blocked" else null
                        ) { act { if (Store.settings.adBlock) Browser.toggleSiteAdBlock() else Browser.showSettings = true } }
                        MenuItem(Icons.Outlined.Tune, "Website Settings") { act { Browser.showSiteSettings = true } }
                    }
                    MenuItem(Icons.Outlined.Shield, "Privacy Report") { act { Browser.showPrivacy = true } }
                    MenuItem(Icons.Outlined.VisibilityOff, "New Private Tab") { act { Browser.newTab(private = true); Browser.editing = true } }
                    MenuItem(Icons.Outlined.Settings, "Settings") { act { Browser.showSettings = true } }
                }
            }
        }
    }
}

@Composable
private fun BigAction(icon: ImageVector, label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Ios.c
    Column(
        modifier.height(72.dp).pressable(enabled = enabled, scaleTo = 0.9f, onClick = onClick)
            .glassCard(RoundedCornerShape(20.dp)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = if (enabled) c.label else c.tertiary, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = Ios.caption.copy(fontWeight = FontWeight.SemiBold), color = if (enabled) c.label else c.tertiary)
    }
}

// ============================================================================ Archive

@Composable
fun ArchiveSheet(visible: Boolean) {
    val c = Ios.c
    IosSheet(visible, "Archive", { Browser.showArchive = false }, leading = {
        TextAction("Clear", color = c.red) { Store.archive.clear() }
    }) {
        var q by remember { mutableStateOf("") }
        val hours = Store.settings.archiveAfterHours
        Text(
            if (hours > 0) "Tabs you close, and tabs you haven't opened for ${archiveLabel(hours).lowercase()}, are kept here."
            else "Tabs you close are kept here. Automatic archiving is off.",
            style = Ios.footnote, color = c.secondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(40.dp).glass(RoundedCornerShape(20.dp), elevation = 4.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Search, null, tint = c.secondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                q, { q = it }, singleLine = true, textStyle = Ios.body.copy(color = c.label), cursorBrush = SolidColor(c.tint),
                modifier = Modifier.weight(1f),
                decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search Archive", style = Ios.body, color = c.secondary); inner() } }
            )
        }
        val items = Store.archive.items.filter { q.isBlank() || it.title.contains(q, true) || it.url.contains(q, true) }
        if (items.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.History, null, tint = c.tertiary, modifier = Modifier.size(52.dp))
                Spacer(Modifier.height(8.dp))
                Text("Nothing archived yet", style = Ios.title3, color = c.label)
            }
        }
        val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp)) {
            items(items, key = { it.url }) { s ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).pressable(scaleTo = 0.97f) { Browser.reopenArchived(s) }
                        .glassCard(RoundedCornerShape(18.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SiteTile(s.url, s.title, size = 38.dp, corner = 10.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title.ifBlank { s.url }, style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${hostOf(s.url)} · ${fmt.format(Date(s.time))}", style = Ios.footnote, color = c.secondary, maxLines = 1)
                    }
                    InlineIconButton(Icons.Outlined.Refresh, "Reopen", size = 36.dp, iconSize = 18.dp, tint = c.tint) { Browser.reopenArchived(s) }
                }
            }
        }
    }
}

fun archiveLabel(hours: Int): String = when (hours) {
    0 -> "Never"
    12 -> "12 Hours"
    24 -> "1 Day"
    168 -> "1 Week"
    720 -> "30 Days"
    else -> "$hours Hours"
}

// ============================================================================ Pinch to Summarize

@Composable
fun SummaryOverlay(content: SummaryContent?, busy: Boolean) {
    AnimatedVisibility(content != null || busy, enter = fadeIn(tween(160)), exit = fadeOut(tween(200))) {
        val c = Ios.c
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (c.dark) 0.45f else 0.22f))
                .clickable(interactionSource = null, indication = null) { Browser.summary = null; Browser.summarizing = false },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(10.dp).fillMaxWidth().heightIn(max = 640.dp)
                    .animateEnterExit(enter = slideInVertically(spring(dampingRatio = 0.84f, stiffness = 360f)) { it }, exit = slideOutVertically { it })
                    .clickable(interactionSource = null, indication = null) { }
                    .glass(RoundedCornerShape(34.dp), strong = true)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(c.tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = c.tint, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Summary", style = Ios.headline, color = c.label, modifier = Modifier.weight(1f))
                    if (content != null) Text("${content.minutes} min read", style = Ios.footnote, color = c.secondary)
                }
                Spacer(Modifier.height(14.dp))
                if (content == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = c.tint, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Reading the page…", style = Ios.subhead, color = c.secondary)
                    }
                } else {
                    Text(content.title, style = Ios.title3.copy(fontWeight = FontWeight.Bold), color = c.label)
                    if (content.site.isNotBlank()) Text(content.site, style = Ios.footnote, color = c.secondary)
                    Spacer(Modifier.height(14.dp))
                    content.points.forEach { p ->
                        Row(Modifier.padding(bottom = 12.dp)) {
                            Box(Modifier.padding(top = 7.dp).size(7.dp).clip(CircleShape).background(c.tint))
                            Spacer(Modifier.width(12.dp))
                            Text(p, style = Ios.body.copy(lineHeight = 23.sp), color = c.label)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Key sentences picked on your phone from the article — no AI service involved.", style = Ios.caption, color = c.secondary)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.weight(1f).height(44.dp).pressable(scaleTo = 0.94f) { Browser.summary = null; Browser.openReader() }
                                .clip(RoundedCornerShape(22.dp)).background(c.fill),
                            contentAlignment = Alignment.Center
                        ) { Text("Open Reader", style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label) }
                        Box(
                            Modifier.weight(1f).height(44.dp).pressable(scaleTo = 0.94f) {
                                Browser.copy(content.title + "\n\n" + content.points.joinToString("\n") { "• $it" })
                            }.clip(RoundedCornerShape(22.dp)).background(c.tint),
                            contentAlignment = Alignment.Center
                        ) { Text("Copy Summary", style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold), color = Color.White) }
                    }
                }
            }
        }
    }
}
