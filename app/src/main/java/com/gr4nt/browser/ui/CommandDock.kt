package com.gr4nt.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.gr4nt.browser.browser.Browser
import com.gr4nt.browser.browser.BrowserTab
import com.gr4nt.browser.data.Store

@Composable
fun CommandDock(tab: BrowserTab, expanded: Boolean = false) {
    val c = Ios.c
    if ((Browser.toolbarCollapsed || Browser.toolbarHidden) && !tab.isStartPage) {
        Row(Modifier.padding(horizontal = 30.dp).widthIn(max = 420.dp).glass(RoundedCornerShape(18.dp)).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            InlineIconButton(Icons.Outlined.ExpandLess, "Show toolbar", size = 44.dp) { Browser.toolbarCollapsed = false; Browser.toolbarHidden = false; Browser.idleHidden = false }
            Text(tab.host, style = Ios.footnote, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            InlineIconButton(Icons.Outlined.Dashboard, "Tabs", size = 44.dp) { Browser.captureThumbnail(); Browser.showTabs = true }
        }
    } else Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 12.dp)
        .glass(RoundedCornerShape(28.dp), elevation = 8.dp).padding(8.dp)) {
        AddressPill(tab, Modifier.fillMaxWidth(), height = 48)
        Spacer(Modifier.height(4.dp))
        CommandNavigation(tab, expanded)
    }
}

@Composable
fun CommandNavigation(tab: BrowserTab, labels: Boolean = false) {
    val c = Ios.c
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        DockAction(Icons.Outlined.ArrowBack, "Back", labels, tab.canGoBack || !tab.isStartPage) { Browser.back() }
        DockAction(Icons.Outlined.ArrowForward, "Forward", labels, tab.canGoForward) { Browser.forward() }
        DockAction(Icons.Outlined.Home, "Home", labels) { Browser.startSection = 0; Browser.newTab(private = tab.isPrivate) }
        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { Browser.newTab(private = tab.isPrivate); Browser.editing = true },
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(c.tint)) {
                Icon(Icons.Outlined.Add, "New Tab", tint = if (c.dark) Color(0xFF082B26) else Color.White, modifier = Modifier.size(22.dp))
            }
            if (labels) Text("New", style = Ios.caption2, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(44.dp).semantics { contentDescription = "Tabs" }
                .pressable(onLongClick = { Browser.showTabMenu = true }) { Browser.captureThumbnail(); Browser.showTabs = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Dashboard, null, tint = c.label, modifier = Modifier.size(22.dp))
                Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 1.dp).size(16.dp, 14.dp).clip(RoundedCornerShape(5.dp)).background(c.tint), contentAlignment = Alignment.Center) {
                    Text("${stripTabs().size}", style = Ios.caption2.copy(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Bold), color = if (c.dark) Color(0xFF082B26) else Color.White, maxLines = 1)
                }
                // The count is visual; expose a stable label for accessibility and UI automation.
            }
            if (labels) Text("Tabs", style = Ios.caption2, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DockAction(Icons.Outlined.MoreHoriz, "Browser Actions", labels, shortLabel = "Menu") { Browser.showPageMenu = true }
    }
}

@Composable
private fun DockAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, showLabel: Boolean, enabled: Boolean = true, shortLabel: String = label, onClick: () -> Unit) {
    Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
            Icon(icon, label, tint = if (enabled) Ios.c.label else Ios.c.tertiary, modifier = Modifier.size(21.dp))
        }
        if (showLabel) Text(shortLabel, style = Ios.caption2, color = if (enabled) Ios.c.secondary else Ios.c.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
