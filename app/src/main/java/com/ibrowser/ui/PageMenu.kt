package com.ibrowser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartScreen
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibrowser.browser.Browser
import com.ibrowser.browser.ContextTarget
import com.ibrowser.data.Store
import com.ibrowser.download.DownloadManager

@Composable
fun PageMenu(visible: Boolean) {
    AnimatedVisibility(visible, enter = fadeIn(tween(140)), exit = fadeOut(tween(200))) {
        val c = Ios.c
        val t = Browser.current ?: return@AnimatedVisibility
        fun act(f: () -> Unit) { Browser.showPageMenu = false; f() }
        Box(Modifier.fillMaxSize().background(c.scrim.copy(alpha = 0.12f)).clickable(interactionSource = null, indication = null) { Browser.showPageMenu = false }) {
            val top = Store.settings.layout == com.ibrowser.data.ToolbarLayout.TOP
            val origin = androidx.compose.ui.graphics.TransformOrigin(0.08f, if (top) 0f else 1f)
            Box(
                Modifier.align(if (top) Alignment.TopStart else Alignment.BottomStart)
                    .animateEnterExit(
                        enter = scaleIn(Motion.bouncy, initialScale = 0.3f, transformOrigin = origin) + fadeIn(tween(120)),
                        exit = scaleOut(Motion.smooth, targetScale = 0.4f, transformOrigin = origin) + fadeOut(tween(140))
                    )
            ) {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .padding(start = 12.dp, bottom = 70.dp, top = 90.dp)
                        .width(300.dp)
                        .heightIn(max = 560.dp)
                        .glass(RoundedCornerShape(26.dp), strong = true)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp)
                ) {
                    // Text size + reader row
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallBox(Modifier.weight(1f), { Browser.setTextZoom(t.textZoom - 10) }) { Text("A", fontSize = 13.sp, color = c.label) }
                        SmallBox(Modifier.weight(1f), { Browser.setTextZoom(Store.settings.textZoom) }) {
                            Text("${t.textZoom}%", style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label)
                        }
                        SmallBox(Modifier.weight(1f), { Browser.setTextZoom(t.textZoom + 10) }) { Text("A", fontSize = 21.sp, color = c.label) }
                    }
                    if (!t.isStartPage) {
                        MenuItem(Icons.Outlined.ChromeReaderMode, "Show Reader") { act { Browser.openReader() } }
                        MenuItem(Icons.Outlined.Search, "Find on Page") { act { Browser.startFind() } }
                        MenuDivider(true)
                        MenuItem(Icons.Outlined.IosShare, "Share") { act { Browser.share(t.url, t.title) } }
                        MenuItem(Icons.Outlined.ContentCopy, "Copy Link") { act { Browser.copy(t.url) } }
                        MenuItem(Icons.Outlined.BookmarkAdd, "Add Bookmark") { act { Browser.addBookmark() } }
                        MenuItem(Icons.Outlined.StarOutline, "Add to Favorites") { act { Browser.addFavorite() } }
                        MenuItem(Icons.Outlined.AddBox, "Add to Reading List") { act { Browser.addReadingList() } }
                        MenuDivider(true)
                        MenuItem(
                            Icons.Outlined.Movie, "Media on This Page",
                            trailing = if (t.media.isEmpty()) "None" else "${t.media.size}"
                        ) { act { Browser.showMedia = true } }
                    }
                    MenuItem(Icons.Outlined.FolderOpen, "Open File…") { act { (Browser.activity as? com.ibrowser.MainActivity)?.openFilePicker() } }
                    MenuItem(Icons.Outlined.Download, "Downloads",
                        trailing = DownloadManager.items.count { it.isActive }.takeIf { it > 0 }?.toString()
                    ) { act { Browser.showDownloads = true } }
                    if (!t.isStartPage) {
                        MenuItem(
                            if (t.desktop) Icons.Outlined.SmartScreen else Icons.Outlined.DesktopWindows,
                            if (t.desktop) "Request Mobile Website" else "Request Desktop Website"
                        ) { act { Browser.toggleDesktop() } }
                        MenuItem(Icons.Outlined.Translate, "Translate Page") { act { Browser.translate() } }
                        MenuItem(Icons.Outlined.Print, "Print or Save as PDF") { act { Browser.print() } }
                        MenuItem(Icons.Outlined.UnfoldLess, "Hide Toolbar") { act { Browser.toolbarHidden = true } }
                        MenuDivider(true)
                        val blockedHere = t.host in Store.settings.disabledAdSites
                        MenuItem(
                            Icons.Outlined.Shield,
                            if (!Store.settings.adBlock) "Ad Blocking Off" else if (blockedHere) "Resume Ad Blocking" else "Pause Ad Blocking",
                            trailing = if (Store.settings.adBlock && !blockedHere) "${t.blocked} blocked" else null
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
private fun SmallBox(modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier.height(40.dp).pressable(scaleTo = 0.9f, onClick = onClick).clip(RoundedCornerShape(14.dp)).background(Ios.c.fill),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun ContextMenuOverlay(target: ContextTarget?) {
    AnimatedVisibility(target != null, enter = fadeIn(), exit = fadeOut()) {
        val tgt = target ?: return@AnimatedVisibility
        val c = Ios.c
        val tab = Browser.tabs.firstOrNull { it.id == tgt.tabId }
        fun act(f: () -> Unit) { Browser.contextTarget = null; f() }
        Box(
            Modifier.fillMaxSize().background(c.scrim.copy(alpha = 0.45f)).clickable(interactionSource = null, indication = null) { Browser.contextTarget = null },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.padding(24.dp).width(310.dp).animateEnterExit(
                    enter = scaleIn(Motion.bouncy, initialScale = 0.85f) + fadeIn(tween(140)),
                    exit = scaleOut(Motion.smooth, targetScale = 0.9f) + fadeOut(tween(140))
                )
            ) {
                // preview card
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), strong = true).padding(16.dp)) {
                    Text(if (tgt.link != null) "Link" else "Image", style = Ios.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.secondary)
                    Spacer(Modifier.height(4.dp))
                    Text(tgt.link ?: tgt.image ?: "", style = Ios.subhead, color = c.label, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), strong = true).padding(vertical = 4.dp)) {
                    tgt.link?.let { link ->
                        if (com.ibrowser.player.Media.isMediaUrl(link)) {
                            MenuItem(Icons.Outlined.Movie, "Play in iBrowser") {
                                act { com.ibrowser.player.Media.play(link, link.substringAfterLast('/').substringBefore('?'), referer = tab?.url, userAgent = tab?.webView?.settings?.userAgentString) }
                            }
                        }
                        MenuItem(Icons.AutoMirrored.Outlined.OpenInNew, "Open in New Tab") {
                            act { Browser.newTab(link, private = tab?.isPrivate ?: false, parent = tgt.tabId, select = false); Browser.toast("Opened in background") }
                        }
                        MenuItem(Icons.Outlined.VisibilityOff, "Open in Private Tab") { act { Browser.load(link, Browser.newTab(private = true)) } }
                        MenuItem(Icons.Outlined.AddBox, "Add to Reading List") { act { Browser.addReadingList(link, link) } }
                        MenuItem(Icons.Outlined.ContentCopy, "Copy Link") { act { Browser.copy(link) } }
                        MenuItem(Icons.Outlined.IosShare, "Share…") { act { Browser.share(link) } }
                        MenuItem(Icons.Outlined.Download, "Download Linked File") {
                            act {
                                Browser.requestDownload(link, DownloadManager.guessName(link, null, null), null, -1,
                                    tab?.webView?.settings?.userAgentString, tab?.url)
                            }
                        }
                    }
                    tgt.image?.let { img ->
                        if (tgt.link != null) MenuDivider(true)
                        if (!img.startsWith("data:")) {
                            MenuItem(Icons.Outlined.Download, "Save Image") {
                                act {
                                    Browser.requestDownload(img, DownloadManager.guessName(img, null, "image/*"), null, -1,
                                        tab?.webView?.settings?.userAgentString, tab?.url)
                                }
                            }
                            MenuItem(Icons.Outlined.Image, "Open Image in New Tab") { act { Browser.newTab(img, private = tab?.isPrivate ?: false, parent = tgt.tabId) } }
                            MenuItem(Icons.Outlined.ContentCopy, "Copy Image Address") { act { Browser.copy(img) } }
                        }
                        MenuItem(Icons.AutoMirrored.Outlined.Article, "Search Image with Google Lens") {
                            act { Browser.load("https://lens.google.com/uploadbyurl?url=" + java.net.URLEncoder.encode(img, "UTF-8"), Browser.newTab(private = tab?.isPrivate ?: false)) }
                        }
                    }
                }
            }
        }
    }
}
