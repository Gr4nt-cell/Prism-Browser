package com.gr4nt.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gr4nt.browser.browser.Browser
import com.gr4nt.browser.browser.BrowserTab
import com.gr4nt.browser.data.Store

@Composable
fun BrowserHomeHero(tab: BrowserTab?) {
    val c = Ios.c
    val private = tab?.isPrivate == true
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Explore, null, tint = c.tint, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp)); Text("Prism Browser", style = Ios.title3, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(c.tint.copy(alpha = .1f))
                .tapHighlight { Browser.newTab(private = !private) }.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(if (private) "Private" else "Personal", style = Ios.caption.copy(fontWeight = FontWeight.SemiBold), color = c.tint)
            }
            InlineIconButton(Icons.Outlined.Tune, "Settings", size = 44.dp) { Browser.showSettings = true }
        }
        Spacer(Modifier.height(if (isLargeScreen()) 44.dp else 28.dp))
        Text(if (private) "A space\njust for you." else "A clearer view\nof the web.",
            style = Ios.title.copy(fontSize = if (isLargeScreen()) 48.sp else 36.sp,
                lineHeight = if (isLargeScreen()) 54.sp else 42.sp), color = c.label)
        Spacer(Modifier.height(10.dp))
        Text(if (private) "Keep this session separate. Browse at your own pace." else "Pick up where you left off, or go somewhere new.",
            style = Ios.callout, color = c.secondary)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth().height(60.dp).glass(RoundedCornerShape(20.dp), elevation = 5.dp)
            .tapHighlight { Browser.editing = true }.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, "Search the web", tint = c.tint, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(12.dp)); Text("Search the web", style = Ios.callout, color = c.secondary, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.NorthEast, null, tint = c.secondary, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeDestination(Icons.Outlined.Bookmarks, "Bookmarks", "${Store.bookmarks.items.size} saved", Modifier.weight(1f)) { Browser.startSection = 1 }
            HomeDestination(Icons.AutoMirrored.Outlined.MenuBook, "Reading", "${Store.readingList.items.size} pages", Modifier.weight(1f)) { Browser.startSection = 2 }
            HomeDestination(Icons.Outlined.History, "History", "Recent visits", Modifier.weight(1f)) { Browser.startSection = 3 }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HomeDestination(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, detail: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.glassCard(RoundedCornerShape(18.dp)).tapHighlight(onClick = onClick).padding(horizontal = 10.dp, vertical = 14.dp)) {
        Icon(icon, null, tint = Ios.c.tint, modifier = Modifier.size(22.dp));Spacer(Modifier.height(12.dp))
        Text(label, style = Ios.footnote.copy(fontWeight = FontWeight.SemiBold), color = Ios.c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, style = Ios.caption2, color = Ios.c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
