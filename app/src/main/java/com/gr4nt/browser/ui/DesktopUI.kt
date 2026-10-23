package com.gr4nt.browser.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.gr4nt.browser.download.DownloadManager

/** Height of the desktop toolbar row and of the tab strip under it. */
val DESKTOP_BAR_H: Dp = 56.dp
val DESKTOP_TABS_H: Dp = 40.dp

/**
 * True on tablets, foldables (unfolded), Chromebooks and Android desktop windows: the smallest
 * side is at least 600dp and the window is wide enough for a desktop toolbar. A phone, or a
 * narrow split-screen window, keeps the phone layout.
 */
@Composable
fun isLargeScreen(): Boolean {
    val cfg = LocalConfiguration.current
    return Store.settings.desktopLayout && cfg.smallestScreenWidthDp >= 600 && cfg.screenWidthDp >= 600
}

/** Tabs shown in the strip: the ones in the same mode (normal / private) as the current tab. */
fun stripTabs(): List<BrowserTab> {
    val private = Browser.current?.isPrivate ?: false
    return Browser.tabs.filter { it.isPrivate == private }
}

/**
 * Safari for Mac / iPad style chrome: a toolbar of floating Liquid-Glass capsules
 * (sidebar · back/forward · centred address field · downloads/share/new tab/tab overview)
 * and, when more than one tab is open, a tab strip underneath.
 */
@Composable
fun DesktopToolbar(tab: BrowserTab) {
    val tabs = stripTabs()
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(DESKTOP_BAR_H),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Capsule {
                InlineIconButton(Icons.Outlined.ViewSidebar, "Sidebar", iconSize = 21.dp) {
                    Browser.startSection = 1; Browser.showLibrary = true
                }
            }
            Capsule {
                InlineIconButton(
                    Icons.Outlined.ArrowBackIosNew, "Back",
                    tint = if (tab.canGoBack || !tab.isStartPage) Ios.c.label else Ios.c.tertiary
                ) { Browser.back() }
                InlineIconButton(
                    Icons.Outlined.ArrowForwardIos, "Forward",
                    tint = if (tab.canGoForward) Ios.c.label else Ios.c.tertiary
                ) { if (tab.canGoForward) Browser.forward() }
            }
            Spacer(Modifier.weight(0.6f))
            AddressPill(tab, Modifier.weight(3f).widthIn(max = 720.dp), height = 44)
            Spacer(Modifier.weight(0.6f))
            Capsule {
                if (DownloadManager.items.isNotEmpty()) {
                    InlineIconButton(Icons.Outlined.Download, "Downloads") { Browser.showDownloads = true }
                }
                InlineIconButton(
                    Icons.Outlined.IosShare, "Share",
                    tint = if (tab.isStartPage) Ios.c.tertiary else Ios.c.label
                ) { if (!tab.isStartPage) Browser.share(tab.url, tab.title) }
                InlineIconButton(Icons.Outlined.Add, "New Tab", iconSize = 23.dp) { Browser.newTab() }
                InlineIconButton(Icons.Outlined.FilterNone, "Tab Overview") {
                    Browser.captureThumbnail(); Browser.showTabs = true
                }
            }
        }
        if (tabs.size > 1) {
            TabStrip(tabs, tab.id)
        }
    }
}

@Composable
private fun Capsule(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.height(44.dp).glass(RoundedCornerShape(22.dp), elevation = 12.dp).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun TabStrip(tabs: List<BrowserTab>, currentId: String) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(DESKTOP_TABS_H).padding(bottom = 4.dp)
            .glass(RoundedCornerShape(18.dp), elevation = 8.dp).padding(3.dp)
    ) {
        // equal widths like Safari; scroll sideways once tabs would get narrower than 150dp
        val each = (maxWidth / tabs.size).coerceAtLeast(150.dp)
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { t -> StripTab(t, t.id == currentId, each) }
        }
    }
}

@Composable
private fun StripTab(t: BrowserTab, selected: Boolean, width: Dp) {
    val c = Ios.c
    val bg by animateColorAsState(
        if (selected) (if (c.dark) Color.White.copy(alpha = 0.16f) else Color.White) else Color.Transparent,
        tween(200), label = "stripTab"
    )
    Box(
        Modifier.width(width).height(DESKTOP_TABS_H - 10.dp).padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .tapHighlight { Browser.select(t.id) }
    ) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val fav = t.favicon
            if (fav != null) {
                Image(fav.asImageBitmap(), null, Modifier.size(15.dp).clip(RoundedCornerShape(3.dp)))
            } else {
                Icon(Icons.Outlined.Public, null, tint = c.secondary, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                t.title.ifBlank { if (t.isStartPage) "Start Page" else t.host },
                style = Ios.footnote.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                color = if (selected) c.label else c.secondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier.align(Alignment.CenterStart).padding(start = 4.dp).size(24.dp).clip(CircleShape)
                .tapHighlight { Browser.close(t.id) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Close, "Close Tab", tint = if (selected) c.secondary else c.tertiary, modifier = Modifier.size(13.dp))
        }
    }
}
