package com.ibrowser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ibrowser.browser.Browser
import com.ibrowser.browser.BrowserTab
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun TabsOverview(visible: Boolean) {
    AnimatedVisibility(visible, enter = fadeIn() + scaleIn(initialScale = 1.08f), exit = fadeOut() + scaleOut(targetScale = 1.08f)) {
        val c = Ios.c
        androidx.compose.runtime.LaunchedEffect(Unit) { Browser.tabsPrivate = Browser.current?.isPrivate ?: false }
        val private = Browser.tabsPrivate
        var q by remember { mutableStateOf("") }
        val group = Browser.tabs.filter { it.isPrivate == private }
            .filter { q.isBlank() || it.displayTitle.contains(q, true) || it.url.contains(q, true) }
        Box(Modifier.fillMaxSize().background(if (private) Color(0xFF1C1C1E) else c.grouped)) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                // search tabs
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(38.dp)
                        .clip(RoundedCornerShape(19.dp)).background(c.fill).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Search, null, tint = c.secondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        q, { q = it }, singleLine = true,
                        textStyle = Ios.body.copy(color = if (private) Color.White else c.label), cursorBrush = SolidColor(c.tint),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search Tabs", style = Ios.body, color = c.secondary); inner() } }
                    )
                }
                if (private && group.isEmpty()) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.Lock, null, tint = Color.White, modifier = Modifier.size(54.dp))
                        Spacer(Modifier.height(14.dp))
                        Text("Private Browsing", style = Ios.title3, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "iBrowser won't remember the pages you visit, your search history or AutoFill information after you close a tab.",
                            style = Ios.subhead, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 110.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(group, key = { it.id }) { t -> TabCard(t, private) }
                    }
                }
            }

            // bottom glass bar: + | Private / N Tabs | Done
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassCircleButton(Icons.Outlined.Add, "New tab") {
                    Browser.newTab(private = private)
                    Browser.showTabs = false
                    Browser.editing = true
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.height(44.dp).glass(RoundedCornerShape(22.dp)).padding(3.dp)) {
                    val normalCount = Browser.tabs.count { !it.isPrivate }
                    Segment("Private", private) { Browser.tabsPrivate = true }
                    Segment(if (normalCount == 1) "1 Tab" else "$normalCount Tabs", !private) { Browser.tabsPrivate = false }
                }
                Spacer(Modifier.weight(1f))
                GlassCircleButton(Icons.Outlined.Check, "Done", tint = c.tint) {
                    val cur = Browser.current
                    if (cur == null || cur.isPrivate != private) {
                        val first = Browser.tabs.firstOrNull { it.isPrivate == private } ?: Browser.newTab(private = private)
                        Browser.select(first.id)
                    }
                    Browser.showTabs = false
                }
            }
        }
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Ios.c
    Box(
        Modifier.height(38.dp).clip(RoundedCornerShape(19.dp))
            .background(if (selected) (if (c.dark) Color(0xFF48484A) else Color.White) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label)
    }
}

@Composable
private fun TabCard(t: BrowserTab, private: Boolean) {
    val c = Ios.c
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val isCurrent = t.id == Browser.currentId
    Column(
        Modifier
            .graphicsLayer { translationX = offset.value; alpha = 1f - (abs(offset.value) / 700f).coerceIn(0f, 0.8f) }
            .pointerInput(t.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(offset.value) > 220f) {
                                offset.animateTo(if (offset.value > 0) 1200f else -1200f)
                                Browser.close(t.id)
                            } else offset.animateTo(0f)
                        }
                    }
                ) { ch, dx -> ch.consume(); scope.launch { offset.snapTo(offset.value + dx) } }
            }
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.72f)
                .clip(RoundedCornerShape(20.dp))
                .background(c.card)
                .then(if (isCurrent) Modifier.border(BorderStroke(3.dp, c.tint), RoundedCornerShape(20.dp)) else Modifier)
                .clickable { Browser.select(t.id); Browser.showTabs = false }
        ) {
            val thumb = t.thumbnail
            if (thumb != null && !t.isStartPage) {
                Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
            } else {
                Column(Modifier.fillMaxSize().background(c.grouped), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (t.isStartPage) {
                        Icon(Icons.Outlined.StarOutline, null, tint = c.secondary, modifier = Modifier.size(34.dp))
                        Text("Start Page", style = Ios.footnote, color = c.secondary)
                    } else SiteTile(t.url, t.displayTitle, t.favicon, size = 56.dp)
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)).clickable { Browser.close(t.id) },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Close, "Close", tint = Color.White, modifier = Modifier.size(15.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            t.favicon?.let {
                Image(it.asImageBitmap(), null, Modifier.size(14.dp).clip(RoundedCornerShape(3.dp))); Spacer(Modifier.width(5.dp))
            }
            Text(
                t.displayTitle, style = Ios.footnote.copy(fontWeight = FontWeight.Medium),
                color = if (private) Color.White else c.label, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
