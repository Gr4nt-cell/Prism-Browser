package com.gr4nt.browser.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gr4nt.browser.browser.Browser
import com.gr4nt.browser.browser.BrowserTab
import com.gr4nt.browser.data.Store
import com.gr4nt.browser.data.hostOf
import com.gr4nt.browser.download.DownloadManager

/** Arc desktop sidebar width, and the gap around the inset page card. */
val ARC_SIDEBAR_W: Dp = 272.dp
val ARC_GAP: Dp = 8.dp

/** Sidebar shown or hidden (Arc: the sidebar button / swipe). */
object ArcDesktop {
    var sidebarOpen by mutableStateOf(true)
}

/**
 * True on tablets, foldables (unfolded), Chromebooks and Android desktop windows: the smallest
 * side is at least 600dp and the window is wide enough for a sidebar. A phone, or a narrow
 * split-screen window, keeps the phone (Arc Search) layout.
 */
@Composable
fun isLargeScreen(): Boolean {
    val cfg = LocalConfiguration.current
    return Store.settings.desktopLayout && cfg.smallestScreenWidthDp >= 600 && cfg.screenWidthDp >= 600
}

/** Tabs shown in the sidebar: the ones in the same mode (normal / private) as the current tab. */
fun stripTabs(): List<BrowserTab> {
    val private = Browser.current?.isPrivate ?: false
    return Browser.tabs.filter { it.isPrivate == private }
}

/**
 * Arc for desktop/tablet, in Liquid Glass: a vertical sidebar with the navigation buttons,
 * the address field, pinned Favorites as a grid of tiles, "+ New Tab" and the open tabs as a
 * vertical list (newest first); Archive, Downloads, Private and Settings at the bottom.
 * The page itself sits next to it in a rounded, shadowed card.
 */
@Composable
fun ArcSidebar(tab: BrowserTab, modifier: Modifier = Modifier) {
    val c = Ios.c
    val tabs = stripTabs().asReversed()
    Column(
        modifier.width(ARC_SIDEBAR_W).fillMaxHeight()
            .glass(RoundedCornerShape(26.dp), elevation = 14.dp)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        // navigation row
        Row(verticalAlignment = Alignment.CenterVertically) {
            InlineIconButton(Icons.Outlined.ViewSidebar, "Hide Sidebar", size = 36.dp, iconSize = 19.dp) { ArcDesktop.sidebarOpen = false }
            Spacer(Modifier.weight(1f))
            InlineIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", size = 36.dp, iconSize = 19.dp,
                tint = if (tab.canGoBack || !tab.isStartPage) c.label else c.tertiary) { Browser.back() }
            InlineIconButton(Icons.AutoMirrored.Outlined.ArrowForward, "Forward", size = 36.dp, iconSize = 19.dp,
                tint = if (tab.canGoForward) c.label else c.tertiary) { if (tab.canGoForward) Browser.forward() }
            InlineIconButton(if (tab.loading) Icons.Outlined.Close else Icons.Outlined.Refresh, "Reload", size = 36.dp, iconSize = 19.dp) {
                if (tab.loading) Browser.stop() else Browser.reload()
            }
        }
        Spacer(Modifier.height(8.dp))
        // address field
        Row(
            Modifier.fillMaxWidth().height(40.dp).pressable(scaleTo = 0.97f) { Browser.editing = true }
                .clip(RoundedCornerShape(12.dp)).background(c.fill).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!tab.isStartPage && tab.secure) { Icon(Icons.Outlined.Lock, null, tint = c.secondary, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(6.dp)) }
            Text(
                if (tab.isStartPage) "Search or Enter URL…" else tab.host.removePrefix("www."),
                style = Ios.subhead, color = if (tab.isStartPage) c.secondary else c.label,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            if (!tab.isStartPage) {
                InlineIconButton(Icons.Outlined.KeyboardArrowUp, "Actions", size = 30.dp, iconSize = 18.dp) { Browser.showActions = true }
            }
        }
        // favorites grid
        val favs = Store.favorites.items.take(8)
        if (favs.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                favs.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { s ->
                            val open = hostOf(tab.url).removePrefix("www.") == hostOf(s.url).removePrefix("www.")
                            Box(
                                Modifier.weight(1f).height(46.dp)
                                    .pressable(scaleTo = 0.9f, onLongClick = { Store.favorites.remove(s.url) }) { Browser.load(s.url) }
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (open) (if (c.dark) Color.White.copy(alpha = 0.18f) else Color.White) else c.fill),
                                contentAlignment = Alignment.Center
                            ) { SiteTile(s.url, s.title, size = 22.dp, corner = 6.dp) }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = c.separator.copy(alpha = 0.4f), thickness = 0.6.dp)
        Spacer(Modifier.height(6.dp))
        // + New Tab, then the tab list
        Row(
            Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(10.dp)).tapHighlight { Browser.newTab(); Browser.editing = true }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Add, null, tint = c.secondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("New Tab", style = Ios.subhead, color = c.secondary)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 2.dp)) {
            items(tabs, key = { it.id }) { t -> SidebarTab(t, t.id == tab.id) }
        }
        // bottom bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            InlineIconButton(Icons.Outlined.Inventory2, "Archive", size = 38.dp, iconSize = 19.dp) { Browser.showArchive = true }
            InlineIconButton(Icons.Outlined.Download, "Downloads", size = 38.dp, iconSize = 19.dp,
                tint = if (DownloadManager.items.any { it.isActive }) c.tint else c.label) { Browser.showDownloads = true }
            Spacer(Modifier.weight(1f))
            InlineIconButton(Icons.Outlined.VisibilityOff, "New Private Tab", size = 38.dp, iconSize = 19.dp,
                tint = if (tab.isPrivate) c.tint else c.label) { Browser.newTab(private = !tab.isPrivate); Browser.editing = true }
            InlineIconButton(Icons.Outlined.Settings, "Settings", size = 38.dp, iconSize = 19.dp) { Browser.showSettings = true }
        }
    }
}

@Composable
private fun SidebarTab(t: BrowserTab, selected: Boolean) {
    val c = Ios.c
    val bg by animateColorAsState(
        if (selected) (if (c.dark) Color.White.copy(alpha = 0.16f) else Color.White) else Color.Transparent,
        tween(180), label = "sideTab"
    )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).height(38.dp)
            .clip(RoundedCornerShape(10.dp)).background(bg)
            .tapHighlight { Browser.select(t.id) }
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fav = t.favicon
        if (fav != null) Image(fav.asImageBitmap(), null, Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)))
        else Icon(Icons.Outlined.Public, null, tint = c.secondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            t.title.ifBlank { if (t.isStartPage) "New Tab" else t.host },
            style = Ios.subhead.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal),
            color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        if (selected) {
            Box(Modifier.size(28.dp).clip(CircleShape).tapHighlight { Browser.close(t.id) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "Close Tab", tint = c.secondary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Small glass button that brings the sidebar back when it's hidden. */
@Composable
fun ArcSidebarToggle(modifier: Modifier = Modifier) {
    GlassCircleButton(Icons.Outlined.ViewSidebar, "Show Sidebar", modifier, size = 44.dp) { ArcDesktop.sidebarOpen = true }
}

/** The page area on large screens: a rounded card with a soft shadow, inset from the edges. */
@Composable
fun Modifier.arcPageCard(): Modifier = this
    .glassCard(RoundedCornerShape(14.dp))
    .clip(RoundedCornerShape(14.dp))
