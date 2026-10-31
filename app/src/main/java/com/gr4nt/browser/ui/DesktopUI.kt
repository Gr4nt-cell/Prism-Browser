package com.gr4nt.browser.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gr4nt.browser.browser.Browser
import com.gr4nt.browser.browser.BrowserTab
import com.gr4nt.browser.data.Store
import com.gr4nt.browser.download.DownloadManager

val SAFARI_SIDEBAR_WIDTH = 248.dp
val SAFARI_CHROME_HEIGHT = 100.dp

object SafariDesktop {
    var sidebarOpen by mutableStateOf(false)
}

/** Use the available window, so split-screen tablets fall back to the phone controls. */
@Composable
fun isLargeScreen(): Boolean {
    val cfg = LocalConfiguration.current
    return Store.settings.desktopLayout && cfg.smallestScreenWidthDp >= 600 && cfg.screenWidthDp >= 600
}

fun stripTabs(): List<BrowserTab> {
    val private = Browser.current?.isPrivate ?: false
    return Browser.tabs.filter { it.isPrivate == private }
}

/** Desktop Safari's toolbar and separate horizontal tab strip; every control uses Browser's existing actions. */
@Composable
fun SafariDesktopChrome(tab: BrowserTab, modifier: Modifier = Modifier) {
    val c = Ios.c
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp), strong = true, elevation = 5.dp)) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            InlineIconButton(SafariSymbols.Sidebar, "Toggle Sidebar", tint = if (SafariDesktop.sidebarOpen) c.tint else c.label) { SafariDesktop.sidebarOpen = !SafariDesktop.sidebarOpen }
            InlineIconButton(SafariSymbols.Back, "Back", tint = if (tab.canGoBack || !tab.isStartPage) c.label else c.tertiary) { if (tab.canGoBack || !tab.isStartPage) Browser.back() }
            InlineIconButton(SafariSymbols.Forward, "Forward", tint = if (tab.canGoForward) c.label else c.tertiary) { if (tab.canGoForward) Browser.forward() }
            if (wide) {
                Spacer(Modifier.weight(1f))
                InlineIconButton(Icons.Outlined.Shield, "Privacy Report") { Browser.showPrivacy = true }
            }
            AddressPill(tab, Modifier.weight(if (wide) 5f else 1f), height = 38)
            if (wide) Spacer(Modifier.weight(1f))
            InlineIconButton(SafariSymbols.Download, "Downloads", tint = if (DownloadManager.items.any { it.isActive }) c.tint else c.label) { Browser.showDownloads = true }
            if (wide) InlineIconButton(SafariSymbols.Share, "Share", tint = if (tab.isStartPage) c.tertiary else c.label) { if (!tab.isStartPage) Browser.share(tab.url, tab.title) }
            InlineIconButton(SafariSymbols.Tabs, "Tab Overview") { Browser.captureThumbnail(); Browser.showTabs = true }
            InlineIconButton(Icons.Outlined.MoreHoriz, "Browser Actions") { Browser.showPageMenu = true }
        }
        HorizontalDivider(color = c.separator.copy(alpha = 0.16f), thickness = 0.5.dp)
        Row(Modifier.fillMaxWidth().height(41.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val tabs = stripTabs()
            val state = androidx.compose.foundation.lazy.rememberLazyListState()
            LaunchedEffect(tab.id, tabs.size) {
                val index = tabs.indexOfFirst { it.id == tab.id }
                if (index >= 0) state.animateScrollToItem(index)
            }
            LazyRow(Modifier.weight(1f), state = state, horizontalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(tabs, key = { it.id }) { DesktopTab(it, it.id == tab.id, horizontal = true) }
            }
            InlineIconButton(SafariSymbols.Plus, "New Tab", size = 40.dp) { Browser.newTab(private = tab.isPrivate); Browser.editing = true }
        }
    }
}

@Composable
fun SafariSidebar(tab: BrowserTab, modifier: Modifier = Modifier) {
    val c = Ios.c
    Column(modifier.width(SAFARI_SIDEBAR_WIDTH).fillMaxHeight().glass(RoundedCornerShape(20.dp), strong = true, elevation = 5.dp).padding(12.dp)) {
        Text("iBrowser", style = Ios.title3, color = c.label, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 18.dp))
        SidebarDestination(Icons.Outlined.StarOutline, "Start Page", tab.isStartPage && Browser.startSection == 0) { Browser.startSection = 0; Browser.newTab(private = tab.isPrivate) }
        SidebarDestination(Icons.AutoMirrored.Outlined.MenuBook, "Bookmarks") { Browser.startSection = 1; Browser.showLibrary = true }
        SidebarDestination(Icons.Outlined.ChromeReaderMode, "Reading List") { Browser.startSection = 2; Browser.showLibrary = true }
        SidebarDestination(Icons.Outlined.History, "History") { Browser.startSection = 3; Browser.showLibrary = true }
        SidebarDestination(Icons.Outlined.VisibilityOff, "Private Browsing", tab.isPrivate) { Browser.newTab(private = !tab.isPrivate) }
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = c.separator.copy(alpha = 0.25f))
        Text(if (tab.isPrivate) "PRIVATE TABS" else "TABS", style = Ios.caption2.copy(fontWeight = FontWeight.SemiBold), color = c.secondary, modifier = Modifier.padding(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(stripTabs(), key = { it.id }) { DesktopTab(it, it.id == tab.id) }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = c.separator.copy(alpha = 0.25f))
        SidebarDestination(Icons.Outlined.Inventory2, "Archive") { Browser.showArchive = true }
        SidebarDestination(SafariSymbols.Download, "Downloads") { Browser.showDownloads = true }
        SidebarDestination(Icons.Outlined.Settings, "Settings") { Browser.showSettings = true }
    }
}

@Composable
private fun SidebarDestination(icon: ImageVector, title: String, selected: Boolean = false, onClick: () -> Unit) {
    val c = Ios.c
    Row(
        Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.tint.copy(alpha = 0.12f) else Color.Transparent)
            .tapHighlight(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = c.tint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, style = Ios.subhead, color = c.label)
    }
}

@Composable
private fun DesktopTab(tab: BrowserTab, selected: Boolean, horizontal: Boolean = false) {
    val c = Ios.c
    val fill by animateColorAsState(if (selected) c.card else Color.Transparent, tween(180), label = "desktopTab")
    Row(
        (if (horizontal) Modifier.widthIn(min = 150.dp, max = 220.dp).width(190.dp) else Modifier.fillMaxWidth())
            .height(if (horizontal) 33.dp else 40.dp).clip(RoundedCornerShape(9.dp)).background(fill)
            .tapHighlight(onLongClick = { Browser.showTabMenu = true }) { Browser.select(tab.id) }
            .padding(start = 10.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val favicon = tab.favicon
        if (favicon != null) Image(favicon.asImageBitmap(), null, Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)))
        else Icon(if (tab.isPrivate) Icons.Outlined.VisibilityOff else Icons.Outlined.Public, null, tint = c.secondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(tab.displayTitle, style = Ios.footnote.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal), color = c.label,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        InlineIconButton(Icons.Outlined.Close, "Close ${tab.displayTitle}", size = 28.dp, iconSize = 14.dp, tint = c.secondary) { Browser.close(tab.id) }
    }
}
