package com.ibrowser.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibrowser.browser.Browser
import com.ibrowser.browser.BrowserTab
import com.ibrowser.data.Site
import com.ibrowser.data.SiteList
import com.ibrowser.data.Store
import com.ibrowser.data.hostOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val SECTIONS = listOf(
    "Start" to Icons.Outlined.StarOutline,
    "Bookmarks" to Icons.AutoMirrored.Outlined.MenuBook,
    "Reading List" to Icons.Outlined.ChromeReaderMode,
    "History" to Icons.Outlined.AccessTime
)

@Composable
fun StartPage(tab: BrowserTab?, inSheet: Boolean) {
    val c = Ios.c
    Column(Modifier.fillMaxSize().wallpaper().then(if (!inSheet) Modifier.statusBarsPadding() else Modifier)) {
        Spacer(Modifier.height(8.dp))
        SectionPicker()
        Box(Modifier.weight(1f)) {
            when (Browser.startSection) {
                0 -> StartHome(tab)
                1 -> SiteListView(Store.bookmarks, "No Bookmarks", "Pages you bookmark appear here.")
                2 -> ReadingListView()
                else -> HistoryView()
            }
        }
    }
}

@Composable
private fun SectionPicker() {
    val c = Ios.c
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(40.dp).glass(RoundedCornerShape(20.dp), elevation = 4.dp).padding(3.dp)
    ) {
        val segW = maxWidth / SECTIONS.size
        val x by animateDpAsState(segW * Browser.startSection, androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 420f), label = "seg")
        Box(Modifier.offset(x = x).width(segW).fillMaxSize().clip(RoundedCornerShape(17.dp)).background(if (c.dark) Color(0xFF3A3A3C) else Color.White))
        Row(Modifier.fillMaxSize()) {
            SECTIONS.forEachIndexed { i, (label, icon) ->
                Row(
                    Modifier.weight(1f).fillMaxSize().pressable(scaleTo = 0.9f) { Browser.startSection = i }.clip(RoundedCornerShape(17.dp)),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, tint = if (Browser.startSection == i) c.tint else c.secondary, modifier = Modifier.size(17.dp))
                    if (Browser.startSection == i) {
                        Spacer(Modifier.width(4.dp))
                        Text(label, style = Ios.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun StartHome(tab: BrowserTab?) {
    val s = Store.settings
    val c = Ios.c
    var editing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 140.dp)) {
        if (tab?.isPrivate == true) {
            Column(Modifier.fillMaxWidth().padding(20.dp).glassCard(RoundedCornerShape(22.dp)).padding(18.dp)) {
                Text("Private Browsing", style = Ios.headline, color = c.label)
                Spacer(Modifier.height(4.dp))
                Text(
                    "iBrowser won't remember the pages you visit, your search history or AutoFill information. Cookies and site data are wiped when you close your private tabs.",
                    style = Ios.subhead, color = c.secondary
                )
            }
        }
        if (s.showFavorites) {
            SectionHeader("Favorites", Modifier.padding(top = 14.dp))
            TileGrid(Store.favorites.items, onRemove = { Store.favorites.remove(it.url) })
        }
        if (s.showFrequent) {
            val freq = Store.frequentlyVisited()
            if (freq.isNotEmpty()) {
                SectionHeader("Frequently Visited", Modifier.padding(top = 10.dp))
                TileGrid(freq, onRemove = { site -> Store.history.items.filter { hostOf(it.url) == hostOf(site.url) }.forEach { Store.history.remove(it.url) } })
            }
        }
        if (s.showPrivacy) {
            SectionHeader("Privacy Report", Modifier.padding(top = 10.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).pressable(scaleTo = 0.97f) { Browser.showPrivacy = true }
                    .glassCard(RoundedCornerShape(22.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Shield, null, tint = c.label, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("${s.totalBlocked}", style = Ios.title3.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold), color = c.label)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (s.adBlock) "ads and trackers have been blocked from loading and profiling you."
                    else "Ad blocking is off. Turn it on in Settings.",
                    style = Ios.footnote, color = c.secondary
                )
            }
        }
        if (s.showReading && Store.readingList.items.isNotEmpty()) {
            SectionHeader("Reading List", Modifier.padding(top = 10.dp)) {
                TextAction("Show All") { Browser.startSection = 2 }
            }
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Store.readingList.items.take(10), key = { it.url }) { site ->
                    Row(
                        Modifier.width(260.dp).pressable(scaleTo = 0.96f) { Browser.load(site.url) }.glassCard(RoundedCornerShape(18.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SiteTile(site.url, site.title, size = 44.dp, corner = 10.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(site.title, style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(hostOf(site.url), style = Ios.footnote, color = c.secondary, maxLines = 1)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Edit", style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label,
                modifier = Modifier.pressable(scaleTo = 0.9f) { editing = !editing }.clip(CircleShape).background(c.fill).padding(horizontal = 22.dp, vertical = 9.dp)
            )
        }
        if (editing) {
            IosGroup("Start Page") {
                IosRow("Favorites", trailing = { IosSwitch(s.showFavorites) { s.showFavorites = it; s.save() } })
                IosRow("Frequently Visited", trailing = { IosSwitch(s.showFrequent) { s.showFrequent = it; s.save() } })
                IosRow("Privacy Report", trailing = { IosSwitch(s.showPrivacy) { s.showPrivacy = it; s.save() } })
                IosRow("Reading List", divider = false, trailing = { IosSwitch(s.showReading) { s.showReading = it; s.save() } })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileGrid(sites: List<Site>, onRemove: (Site) -> Unit) {
    val c = Ios.c
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        sites.take(16).chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { site ->
                    var menu by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .pressable(scaleTo = 0.88f, onLongClick = { menu = true }) { Browser.load(site.url) }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SiteTile(site.url, site.title)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                site.title.ifBlank { hostOf(site.url) }, style = Ios.caption, color = c.label,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Open in New Tab") }, { menu = false; Browser.load(site.url, Browser.newTab()) })
                            DropdownMenuItem({ Text("Open in Private Tab") }, { menu = false; Browser.load(site.url, Browser.newTab(private = true)) })
                            DropdownMenuItem({ Text("Copy Link") }, { menu = false; Browser.copy(site.url) })
                            DropdownMenuItem({ Text("Remove", color = c.red) }, { menu = false; onRemove(site) })
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SiteRow(site: Site, subtitle: String = hostOf(site.url), onDelete: () -> Unit, extra: (@Composable () -> Unit)? = null) {
    val c = Ios.c
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().tapHighlight(onLongClick = { menu = true }) { Browser.load(site.url); Browser.showLibrary = false }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SiteTile(site.url, site.title, size = 36.dp, corner = 9.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(site.title.ifBlank { site.url }, style = Ios.body, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = Ios.footnote, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            extra?.invoke()
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text("Open in New Tab") }, { menu = false; Browser.load(site.url, Browser.newTab()); Browser.showLibrary = false })
            DropdownMenuItem({ Text("Open in Private Tab") }, { menu = false; Browser.load(site.url, Browser.newTab(private = true)); Browser.showLibrary = false })
            DropdownMenuItem({ Text("Copy Link") }, { menu = false; Browser.copy(site.url) })
            DropdownMenuItem({ Text("Share") }, { menu = false; Browser.share(site.url, site.title) })
            DropdownMenuItem({ Text("Delete", color = c.red) }, { menu = false; onDelete() })
        }
    }
}

@Composable
private fun SearchField(q: String, placeholder: String, onChange: (String) -> Unit) {
    val c = Ios.c
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(38.dp).clip(RoundedCornerShape(19.dp)).background(c.fill).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, null, tint = c.secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            q, onChange, singleLine = true, textStyle = Ios.body.copy(color = c.label), cursorBrush = SolidColor(c.tint),
            modifier = Modifier.weight(1f),
            decorationBox = { inner -> Box { if (q.isEmpty()) Text(placeholder, style = Ios.body, color = c.secondary); inner() } }
        )
    }
}

@Composable
private fun EmptyState(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(top = 80.dp, start = 40.dp, end = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = Ios.title3, color = Ios.c.label)
        Spacer(Modifier.height(6.dp))
        Text(sub, style = Ios.subhead, color = Ios.c.secondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SiteListView(list: SiteList, emptyTitle: String, emptySub: String) {
    var q by remember { mutableStateOf("") }
    val items = list.items.filter { q.isBlank() || it.title.contains(q, true) || it.url.contains(q, true) }
    Column(Modifier.fillMaxSize()) {
        SearchField(q, "Search Bookmarks") { q = it }
        if (list.items.isEmpty()) EmptyState(emptyTitle, emptySub)
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 140.dp)) {
            item {
                if (Store.favorites.items.isNotEmpty() && q.isBlank()) {
                    Text("FAVORITES", style = Ios.footnote, color = Ios.c.secondary, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp))
                }
            }
            if (q.isBlank()) items(Store.favorites.items, key = { "f" + it.url }) { s -> SiteRow(s, onDelete = { Store.favorites.remove(s.url) }) }
            item {
                if (items.isNotEmpty()) Text("BOOKMARKS", style = Ios.footnote, color = Ios.c.secondary, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
            }
            items(items, key = { "b" + it.url }) { s -> SiteRow(s, onDelete = { list.remove(s.url) }) }
        }
    }
}

@Composable
private fun ReadingListView() {
    val c = Ios.c
    var unreadOnly by remember { mutableStateOf(false) }
    val items = Store.readingList.items.filter { !unreadOnly || !it.read }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All" to false, "Unread" to true).forEach { (l, v) ->
                Text(
                    l, style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = if (unreadOnly == v) Color.White else c.label,
                    modifier = Modifier.pressable(scaleTo = 0.9f) { unreadOnly = v }.clip(CircleShape).background(if (unreadOnly == v) c.tint else c.fill)
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }
        if (items.isEmpty()) EmptyState("No Items", "Add pages to your Reading List from the page menu or by long-pressing a link.")
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 140.dp)) {
            items(items, key = { it.url }) { s ->
                SiteRow(s, onDelete = { Store.readingList.remove(s.url) }) {
                    Text(
                        if (s.read) "Mark Unread" else "Mark Read", style = Ios.footnote, color = c.tint,
                        modifier = Modifier.clickable { Store.readingList.update(s.url) { it.copy(read = !it.read) } }.padding(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryView() {
    val c = Ios.c
    var q by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val items = Store.history.items.filter { q.isBlank() || it.title.contains(q, true) || it.url.contains(q, true) }.take(1500)
    val fmtDay = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }
    val fmtTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    fun dayLabel(t: Long): String {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_YEAR) to cal.get(Calendar.YEAR)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yday = cal.get(Calendar.DAY_OF_YEAR) to cal.get(Calendar.YEAR)
        cal.timeInMillis = t
        val d = cal.get(Calendar.DAY_OF_YEAR) to cal.get(Calendar.YEAR)
        return when (d) { today -> "Today"; yday -> "Yesterday"; else -> fmtDay.format(Date(t)) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SearchField(q, "Search History") { q = it } }
            Box(Modifier.padding(end = 12.dp).size(36.dp).pressable(scaleTo = 0.8f) { confirm = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Delete, "Clear", tint = c.red, modifier = Modifier.size(20.dp))
            }
        }
        if (confirm) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).glassCard(RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Clear all history?", style = Ios.subhead, color = c.label, modifier = Modifier.weight(1f))
                TextAction("Cancel") { confirm = false }
                TextAction("Clear", color = c.red, bold = true) { Store.history.clear(); confirm = false }
            }
        }
        if (items.isEmpty()) EmptyState("No History", "Websites you visit appear here.")
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 140.dp)) {
            var last = ""
            items.forEach { s ->
                val label = dayLabel(s.time)
                if (label != last) {
                    last = label
                    item(key = "h$label") {
                        Text(label, style = Ios.headline, color = c.label, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
                    }
                }
                item(key = s.url) {
                    SiteRow(s, subtitle = "${fmtTime.format(Date(s.time))} · ${hostOf(s.url)}", onDelete = { Store.history.remove(s.url) })
                }
            }
        }
    }
}
