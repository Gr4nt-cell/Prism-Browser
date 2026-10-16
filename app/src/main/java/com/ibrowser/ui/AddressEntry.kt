package com.ibrowser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ibrowser.browser.Browser
import com.ibrowser.data.Site
import com.ibrowser.data.Store
import com.ibrowser.data.hostOf
import com.ibrowser.download.DownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

@Composable
fun AddressEntry(visible: Boolean) {
    AnimatedVisibility(
        visible,
        enter = fadeIn(androidx.compose.animation.core.tween(180)) + androidx.compose.animation.scaleIn(Motion.smooth, initialScale = 1.04f),
        exit = fadeOut(androidx.compose.animation.core.tween(160)) + androidx.compose.animation.scaleOut(Motion.smooth, targetScale = 1.04f)
    ) {
        val c = Ios.c
        val tab = Browser.current
        val initial = if (tab == null || tab.isStartPage) "" else tab.url
        var tf by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
        val focus = remember { FocusRequester() }
        var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
        val clipboard = LocalClipboardManager.current
        LaunchedEffect(Unit) { delay(60); runCatching { focus.requestFocus() } }

        val q = tf.text.trim()
        LaunchedEffect(q) {
            if (q.isEmpty() || q == initial || tab?.isPrivate == true) { suggestions = emptyList(); return@LaunchedEffect }
            delay(180)
            val api = Store.settings.engine.suggest ?: return@LaunchedEffect
            suggestions = withContext(Dispatchers.IO) {
                runCatching {
                    val url = api.replace("%s", URLEncoder.encode(q, "UTF-8"))
                    DownloadManager.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                        val body = r.body!!.string()
                        val arr = JSONArray(body)
                        val list = arr.getJSONArray(1)
                        (0 until minOf(list.length(), 6)).map { list.getString(it) }
                    }
                }.getOrDefault(emptyList())
            }
        }

        val local: List<Site> = remember(q) {
            if (q.isEmpty()) emptyList()
            else (Store.bookmarks.items + Store.favorites.items + Store.history.items.sortedByDescending { it.visits })
                .filter { it.title.contains(q, true) || it.url.contains(q, true) }
                .distinctBy { it.url }.take(6)
        }
        val topHit = local.firstOrNull { hostOf(it.url).startsWith(q.lowercase().removePrefix("www.")) }

        Box(Modifier.fillMaxSize().wallpaper()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) {
                    if (q.isEmpty()) {
                        item {
                            SectionHeader("Favorites")
                        }
                        item {
                            FavoritesMini()
                        }
                        clipboard.getText()?.text?.takeIf { it.isNotBlank() && it.length < 2000 }?.let { clip ->
                            item {
                                SuggestionRow(Icons.Outlined.ContentPaste, "Paste and Go", clip.take(80)) { Browser.load(clip) }
                            }
                        }
                    } else {
                        if (topHit != null) {
                            item { GroupLabel("Top Hit") }
                            item { SiteSuggestion(topHit) }
                        }
                        item { GroupLabel("${Store.settings.engine.label} Search") }
                        item {
                            SuggestionRow(Icons.Outlined.Search, q, null, onFill = null) { Browser.load(q) }
                        }
                        items(suggestions.filter { it != q }) { s ->
                            SuggestionRow(Icons.Outlined.Search, s, null, onFill = { tf = TextFieldValue(s, TextRange(s.length)) }) { Browser.load(s) }
                        }
                        val rest = local.filter { it != topHit }
                        if (rest.isNotEmpty()) {
                            item { GroupLabel("Bookmarks and History") }
                            items(rest, key = { "l" + it.url }) { s -> SiteSuggestion(s) }
                        }
                        item { GroupLabel("On This Page") }
                        item {
                            SuggestionRow(Icons.Outlined.Search, "Find “$q”", null) {
                                Browser.editing = false; Browser.startFind(); Browser.find(q)
                            }
                        }
                    }
                }
                // Bottom field
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier.weight(1f).height(48.dp).glass(RoundedCornerShape(24.dp)).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Search, null, tint = c.secondary, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            tf, { tf = it },
                            singleLine = true,
                            textStyle = Ios.body.copy(color = c.label),
                            cursorBrush = SolidColor(c.tint),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                            keyboardActions = KeyboardActions(onGo = { if (tf.text.isNotBlank()) Browser.load(tf.text) }),
                            modifier = Modifier.weight(1f).focusRequester(focus),
                            decorationBox = { inner ->
                                Box { if (tf.text.isEmpty()) Text("Search or enter website name", style = Ios.body, color = c.secondary); inner() }
                            }
                        )
                        if (tf.text.isNotEmpty()) {
                            Box(Modifier.size(30.dp).pressable(scaleTo = 0.8f) { tf = TextFieldValue("") }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Cancel, "Clear", tint = c.secondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    TextAction("Cancel", color = c.label) { Browser.editing = false }
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(t: String) {
    Text(t, style = Ios.footnote.copy(fontWeight = FontWeight.SemiBold), color = Ios.c.secondary, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun SuggestionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    sub: String?,
    onFill: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val c = Ios.c
    Row(
        Modifier.fillMaxWidth().tapHighlight(onClick = onClick).padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = c.secondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = Ios.body, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = Ios.footnote, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onFill != null) {
            Box(Modifier.size(32.dp).pressable(scaleTo = 0.8f, onClick = onFill), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.NorthWest, "Fill", tint = c.secondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SiteSuggestion(s: Site) {
    val c = Ios.c
    Row(
        Modifier.fillMaxWidth().tapHighlight { Browser.load(s.url) }.padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SiteTile(s.url, s.title, size = 30.dp, corner = 7.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title.ifBlank { hostOf(s.url) }, style = Ios.body, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hostOf(s.url), style = Ios.footnote, color = c.secondary, maxLines = 1)
        }
    }
}

@Composable
private fun FavoritesMini() {
    val c = Ios.c
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Store.favorites.items.take(8).chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { s ->
                    Column(
                        Modifier.weight(1f).pressable(scaleTo = 0.88f) { Browser.load(s.url) }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SiteTile(s.url, s.title, size = 54.dp, corner = 13.dp)
                        Spacer(Modifier.height(5.dp))
                        Text(s.title.ifBlank { hostOf(s.url) }, style = Ios.caption, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
