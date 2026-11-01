package com.gr4nt.browser.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
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

val WORKSPACE_RAIL_WIDTH = 72.dp
val WORKSPACE_SIDEBAR_WIDTH = 244.dp
val WORKSPACE_CHROME_HEIGHT = 72.dp
object DesktopWorkspace { var sidebarOpen by mutableStateOf(false) }

@Composable
fun isLargeScreen(): Boolean {
    val cfg = LocalConfiguration.current
    return Store.settings.desktopLayout && cfg.smallestScreenWidthDp >= 600 && cfg.screenWidthDp >= 600
}
fun stripTabs(): List<BrowserTab> = Browser.tabs.filter { it.isPrivate == (Browser.current?.isPrivate ?: false) }

@Composable
fun WorkspaceChrome(tab: BrowserTab, modifier: Modifier = Modifier) {
    val c = Ios.c
    Row(modifier.fillMaxWidth().height(WORKSPACE_CHROME_HEIGHT).padding(8.dp)
        .glass(RoundedCornerShape(22.dp), strong = true, elevation = 5.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        InlineIconButton(Icons.Outlined.ViewSidebar, "Toggle Sidebar", tint = if (DesktopWorkspace.sidebarOpen) c.tint else c.secondary) { DesktopWorkspace.sidebarOpen = !DesktopWorkspace.sidebarOpen }
        InlineIconButton(Icons.Outlined.ArrowBack, "Back", tint = if (tab.canGoBack || !tab.isStartPage) c.label else c.tertiary) { if (tab.canGoBack || !tab.isStartPage) Browser.back() }
        InlineIconButton(Icons.Outlined.ArrowForward, "Forward", tint = if (tab.canGoForward) c.label else c.tertiary) { if (tab.canGoForward) Browser.forward() }
        AddressPill(tab, Modifier.weight(1f).padding(horizontal = 6.dp), height = 44)
        InlineIconButton(Icons.Outlined.FileDownload, "Downloads", tint = if (DownloadManager.items.any { it.isActive }) c.tint else c.label) { Browser.showDownloads = true }
        InlineIconButton(Icons.Outlined.Dashboard, "Tab Overview") { Browser.captureThumbnail(); Browser.showTabs = true }
        InlineIconButton(Icons.Outlined.MoreHoriz, "Browser Actions") { Browser.showPageMenu = true }
    }
}

@Composable
fun WorkspaceRail(tab: BrowserTab, modifier: Modifier = Modifier) {
    val c = Ios.c
    Column(modifier.width(WORKSPACE_RAIL_WIDTH).fillMaxHeight().padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
        .glass(RoundedCornerShape(24.dp), strong = true, elevation = 4.dp).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.tint), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Explore, null, tint = if (c.dark) Color(0xFF092421) else Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(10.dp))
        RailItem(Icons.Outlined.Home, "Home", tab.isStartPage) { Browser.startSection = 0; Browser.newTab(private = tab.isPrivate) }
        RailItem(Icons.Outlined.Add, "New Tab") { Browser.newTab(private = tab.isPrivate); Browser.editing = true }
        RailItem(Icons.Outlined.Bookmarks, "Bookmarks") { Browser.startSection = 1; Browser.showLibrary = true }
        RailItem(Icons.Outlined.FileDownload, "Downloads") { Browser.showDownloads = true }
        RailItem(Icons.Outlined.Shield, "Privacy Report") { Browser.showPrivacy = true }
        Spacer(Modifier.weight(1f))
        RailItem(Icons.Outlined.VisibilityOff, "Private Browsing", tab.isPrivate) { Browser.newTab(private = !tab.isPrivate) }
        RailItem(Icons.Outlined.Settings, "Settings") { Browser.showSettings = true }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean = false, action: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (selected) Ios.c.tint.copy(alpha = .14f) else Color.Transparent), contentAlignment = Alignment.Center) {
        InlineIconButton(icon, label, tint = if (selected) Ios.c.tint else Ios.c.secondary, onClick = action)
    }
}

@Composable
fun WorkspaceSidebar(tab: BrowserTab, modifier: Modifier = Modifier) {
    val c = Ios.c
    Column(modifier.width(WORKSPACE_SIDEBAR_WIDTH).fillMaxHeight().glass(RoundedCornerShape(24.dp), strong = true, elevation = 3.dp).padding(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Workspace", style = Ios.title3, color = c.label)
                Text(if (tab.isPrivate) "Private session" else "Your open pages", style = Ios.caption, color = c.secondary)
            }
            Pill("${stripTabs().size}", c.tint)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MiniDestination(Icons.Outlined.Bookmarks, "Bookmarks", Modifier.weight(1f)) { Browser.startSection = 1; Browser.showLibrary = true }
            MiniDestination(Icons.AutoMirrored.Outlined.MenuBook, "Reading List", Modifier.weight(1f)) { Browser.startSection = 2; Browser.showLibrary = true }
            MiniDestination(Icons.Outlined.History, "History", Modifier.weight(1f)) { Browser.startSection = 3; Browser.showLibrary = true }
        }
        Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(c.tint.copy(alpha = .12f))
            .tapHighlight { Browser.newTab(private = tab.isPrivate); Browser.editing = true }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Add, null, tint = c.tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp)); Text("New tab", style = Ios.subhead, color = c.tint)
        }
        Spacer(Modifier.height(16.dp))
        Text(if (tab.isPrivate) "PRIVATE TABS" else "OPEN TABS", style = Ios.caption2, color = c.secondary, modifier = Modifier.padding(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(stripTabs(), key = { it.id }) { WorkspaceTab(it, it.id == tab.id) }
        }
        SidebarDestination(Icons.Outlined.Inventory2, "Archive") { Browser.showArchive = true }
        SidebarDestination(Icons.Outlined.Tune, "Customize browser") { Browser.showSettings = true }
    }
}

@Composable
private fun MiniDestination(icon: ImageVector, title: String, modifier: Modifier, action: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).tapHighlight(onClick = action).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, title, tint = Ios.c.secondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp));Text(title, style = Ios.caption2, color = Ios.c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SidebarDestination(icon: ImageVector, title: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).tapHighlight(onClick = action).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Ios.c.secondary, modifier = Modifier.size(19.dp));Spacer(Modifier.width(10.dp));Text(title, style = Ios.footnote, color = Ios.c.label)
    }
}

@Composable
private fun WorkspaceTab(tab: BrowserTab, selected: Boolean) {
    val c = Ios.c
    Row(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(16.dp))
        .background(if (selected) c.tint.copy(alpha = .14f) else c.fill.copy(alpha = .08f))
        .tapHighlight(onLongClick = { Browser.select(tab.id); Browser.showTabMenu = true }) { Browser.select(tab.id) }
        .padding(start = 10.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (tab.favicon != null) Image(tab.favicon!!.asImageBitmap(), null, Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)))
        else Icon(if (tab.isPrivate) Icons.Outlined.VisibilityOff else Icons.Outlined.Public, null, tint = if (selected) c.tint else c.secondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tab.displayTitle, style = Ios.footnote.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (tab.isStartPage) "Start page" else tab.host, style = Ios.caption2, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        InlineIconButton(Icons.Outlined.Close, "Close ${tab.displayTitle}", size = 40.dp, iconSize = 16.dp, tint = c.secondary) { Browser.close(tab.id) }
    }
}
