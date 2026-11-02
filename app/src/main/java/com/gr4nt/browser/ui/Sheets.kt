package com.gr4nt.browser.ui

import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.gr4nt.browser.adblock.AdBlocker
import com.gr4nt.browser.adblock.FilterLists
import com.gr4nt.browser.browser.Browser
import com.gr4nt.browser.data.SearchEngine
import com.gr4nt.browser.data.Store
import com.gr4nt.browser.data.ToolbarLayout
import com.gr4nt.browser.download.DlKind
import com.gr4nt.browser.download.DlStatus
import com.gr4nt.browser.download.DownloadItem
import com.gr4nt.browser.download.DownloadManager
import com.gr4nt.browser.media.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

// ============================================================ Downloads

@Composable
fun DownloadsSheet(visible: Boolean) {
    val c = Ios.c
    var adding by remember { mutableStateOf(false) }
    IosSheet(visible, "Downloads", { Browser.showDownloads = false; adding = false }, leading = {
        Row {
            TextAction("Clear") { DownloadManager.clearFinished() }
            TextAction("New") { adding = !adding }
        }
    }, centeredOnMobile = true) {
        if (adding) NewDownloadBox { link -> adding = false; Browser.downloadFromLink(link) }
        val list = DownloadManager.items
        val active = list.count { it.isActive }
        if (list.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val speed = list.sumOf { it.speed }
                Text(
                    if (active > 0) "$active active · ${formatBytes(speed)}/s" else "${list.size} items",
                    style = Ios.footnote, color = c.secondary, modifier = Modifier.weight(1f)
                )
                if (active > 0) TextAction("Pause All") { DownloadManager.pauseAll() }
                else if (list.any { it.status == DlStatus.PAUSED || it.status == DlStatus.FAILED }) TextAction("Resume All") { DownloadManager.resumeAll() }
            }
        }
        if (list.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Download, null, tint = c.tertiary, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(10.dp))
                Text("No Downloads", style = Ios.title3, color = c.label)
                Text("Files and videos you download appear here.", style = Ios.subhead, color = c.secondary)
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 40.dp)) {
            items(list, key = { it.id }) { d ->
                DownloadRow(d)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private fun iconFor(d: DownloadItem): androidx.compose.ui.graphics.vector.ImageVector {
    val ext = d.fileName.substringAfterLast('.', "").lowercase()
    val mime = d.mime ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: ""
    return when {
        d.kind == DlKind.HLS || d.kind == DlKind.DASH || mime.startsWith("video") -> Icons.Outlined.VideoFile
        mime.startsWith("audio") -> Icons.Outlined.AudioFile
        mime.startsWith("image") -> Icons.Outlined.Image
        ext == "pdf" -> Icons.Outlined.PictureAsPdf
        ext == "apk" -> Icons.Outlined.Android
        ext in setOf("zip", "rar", "7z", "tar", "gz", "xz") -> Icons.Outlined.Archive
        mime.startsWith("text") || ext in setOf("doc", "docx", "txt") -> Icons.Outlined.Description
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadRow(d: DownloadItem) {
    val c = Ios.c
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    fun openExternal() {
        val f = File(d.path ?: return)
        runCatching {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Browser.toast("No app can open this file") }
    }
    fun open() {
        val p = d.path ?: return
        val f = File(p)
        if (!f.exists()) { Browser.toast("File was moved or deleted"); return }
        val mimeGuess = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase())
        if (com.gr4nt.browser.player.Media.isPlayableFile(f.name, mimeGuess)) {
            Browser.showDownloads = false
            com.gr4nt.browser.player.Media.playFile(f.absolutePath, f.nameWithoutExtension)
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Browser.toast("No app can open this file") }
    }
    fun shareFile() {
        val f = File(d.path ?: return)
        runCatching {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    Box {
        Row(
            Modifier.fillMaxWidth().pressable(scaleTo = 0.97f, haptic = false, onLongClick = { menu = true }) { if (d.status == DlStatus.COMPLETED) open() }
                .glassCard(RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(c.tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(iconFor(d), null, tint = c.tint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.fileName, style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    if (d.kind == DlKind.HLS || d.kind == DlKind.DASH) { Spacer(Modifier.width(6.dp)); Pill(d.kind.name, c.orange) }
                    if (d.kind == DlKind.BLOB) { Spacer(Modifier.width(6.dp)); Pill("PAGE", c.tint) }
                }
                Spacer(Modifier.height(3.dp))
                Text(statusText(d), style = Ios.footnote, color = if (d.status == DlStatus.FAILED) c.red else c.secondary, maxLines = 2)
                if (d.status == DlStatus.RUNNING || d.status == DlStatus.PAUSED || d.status == DlStatus.QUEUED || d.status == DlStatus.CONVERTING) {
                    Spacer(Modifier.height(7.dp))
                    if (d.total > 0 && d.status != DlStatus.CONVERTING) {
                        LinearProgressIndicator(
                            progress = { d.progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = if (d.status == DlStatus.PAUSED) c.secondary else c.tint, trackColor = c.fill,
                            strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {}
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = c.tint, trackColor = c.fill)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            when (d.status) {
                DlStatus.RUNNING, DlStatus.QUEUED -> if (d.kind != DlKind.BLOB) RoundAction(Icons.Outlined.Pause) { DownloadManager.pause(d.id) }
                DlStatus.PAUSED -> RoundAction(Icons.Outlined.PlayArrow) { DownloadManager.resume(d.id) }
                DlStatus.FAILED, DlStatus.CANCELLED -> RoundAction(Icons.Outlined.Refresh) { DownloadManager.resume(d.id) }
                DlStatus.CONVERTING -> {}
                DlStatus.COMPLETED -> {}
            }
            if (d.status != DlStatus.COMPLETED) {
                Spacer(Modifier.width(4.dp))
                RoundAction(Icons.Outlined.Close) { DownloadManager.remove(d.id, true) }
            }
        }
        DropdownMenu(menu, { menu = false }) {
            if (d.status == DlStatus.COMPLETED) {
                DropdownMenuItem({ Text("Open") }, { menu = false; open() })
                DropdownMenuItem({ Text("Share File") }, { menu = false; shareFile() })
                DropdownMenuItem({ Text("Open With…") }, { menu = false; openExternal() })
            }
            DropdownMenuItem({ Text("Copy Download Link") }, { menu = false; Browser.copy(d.url) })
            DropdownMenuItem({ Text("Go to Page") }, { menu = false; d.referer?.let { Browser.load(it, Browser.newTab()) }; Browser.showDownloads = false })
            DropdownMenuItem({ Text("Remove from List") }, { menu = false; DownloadManager.remove(d.id, false) })
            DropdownMenuItem({ Text("Delete File", color = c.red) }, { menu = false; DownloadManager.remove(d.id, true) })
        }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).pressable(scaleTo = 0.8f, onClick = onClick).clip(CircleShape).background(Ios.c.fill), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Ios.c.tint, modifier = Modifier.size(19.dp))
    }
}

private fun statusText(d: DownloadItem): String = when (d.status) {
    DlStatus.QUEUED -> "Waiting…"
    DlStatus.RUNNING -> buildString {
        append(formatBytes(d.downloaded))
        if (d.total > 0) append(" of ").append(formatBytes(d.total))
        if (d.speed > 0) {
            append(" · ").append(formatBytes(d.speed)).append("/s")
            if (d.total > 0) append(" · ").append(formatDuration((d.total - d.downloaded) / d.speed)).append(" left")
        }
        if (d.activeThreads > 0) append(" · ${d.activeThreads} connections")
        if (d.detail.isNotBlank() && d.kind != DlKind.FILE) append("\n").append(d.detail)
    }
    DlStatus.PAUSED -> "Paused · ${formatBytes(d.downloaded)}" + (if (d.total > 0) " of ${formatBytes(d.total)}" else "") +
        if (!d.resumable && d.kind == DlKind.FILE) " · server can't resume" else ""
    DlStatus.CONVERTING -> "Converting stream to MP4…"
    DlStatus.COMPLETED -> formatBytes(d.total) + " · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(d.createdAt)) +
        if (d.detail.isNotBlank()) "\n${d.detail}" else ""
    DlStatus.FAILED -> "Failed: ${d.error ?: "unknown error"}"
    DlStatus.CANCELLED -> "Cancelled"
}

// ============================================================ Media found

@Composable
fun MediaSheet(visible: Boolean) {
    val c = Ios.c
    IosSheet(visible, "Media on This Page", { Browser.showMedia = false }) {
        val t = Browser.current
        val media = t?.media?.sortedBy {
            when (it.kind) { MediaKind.HLS -> 0; MediaKind.DASH -> 1; MediaKind.VIDEO -> 2; MediaKind.AUDIO -> 3 }
        } ?: emptyList()
        if (media.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No media detected yet", style = Ios.title3, color = c.label)
                Spacer(Modifier.height(6.dp))
                Text("Start playing the video on the page — streams are detected as soon as they load.", style = Ios.subhead, color = c.secondary)
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(media, key = { it.url }) { m ->
                Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).glassCard(RoundedCornerShape(20.dp)).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (label, col) = when (m.kind) {
                            MediaKind.HLS -> "HLS" to c.orange
                            MediaKind.DASH -> "DASH" to c.orange
                            MediaKind.VIDEO -> m.extension.uppercase().ifBlank { "VIDEO" } to c.tint
                            MediaKind.AUDIO -> m.extension.uppercase().ifBlank { "AUDIO" } to c.green
                        }
                        Pill(label, col)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                m.info.isNotBlank() -> m.info
                                m.size > 0 -> formatBytes(m.size)
                                else -> m.mime ?: ""
                            },
                            style = Ios.footnote, color = c.secondary, maxLines = 1, modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(m.url, style = Ios.footnote, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Box(
                            Modifier.weight(1f).height(38.dp).pressable(scaleTo = 0.94f) { Browser.downloadMedia(m, t) }
                                .clip(RoundedCornerShape(19.dp)).background(c.tint),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (m.kind == MediaKind.HLS || m.kind == MediaKind.DASH) "Download as MP4" else "Download",
                                style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold), color = Color.White
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(38.dp).pressable(scaleTo = 0.8f) { Browser.copy(m.url) }.clip(CircleShape).background(c.fill), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.ContentCopy, "Copy link", tint = c.tint, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.size(38.dp).pressable(scaleTo = 0.8f) {
                                val mime = when (m.kind) {
                                    MediaKind.HLS -> androidx.media3.common.MimeTypes.APPLICATION_M3U8
                                    MediaKind.DASH -> androidx.media3.common.MimeTypes.APPLICATION_MPD
                                    else -> null
                                }
                                Browser.showMedia = false
                                com.gr4nt.browser.player.Media.play(
                                    m.url, t?.title?.ifBlank { null } ?: m.pageTitle.ifBlank { "Media" },
                                    referer = m.pageUrl, userAgent = t?.webView?.settings?.userAgentString, mime = mime
                                )
                            }.clip(CircleShape).background(c.tint),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Outlined.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(22.dp)) }
                    }
                }
            }
        }
    }
}

// ============================================================ Settings

@Composable
fun SettingsSheet(visible: Boolean) {
    val s = Store.settings
    val c = Ios.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var updating by remember { mutableStateOf<String?>(null) }
    var engineOpen by remember { mutableStateOf(false) }
    IosSheet(visible, "Settings", { s.save(); Browser.showSettings = false }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 40.dp)) {
            IosGroup("General") {
                Box {
                    IosRow("Search Engine", value = s.engine.label, showChevron = true) { engineOpen = true }
                    DropdownMenu(engineOpen, { engineOpen = false }) {
                        SearchEngine.entries.forEach { e ->
                            DropdownMenuItem({ Text(e.label + if (e == s.engine) "  ✓" else "") }, { s.engine = e; s.save(); engineOpen = false })
                        }
                    }
                }
                var appsOpen by remember { mutableStateOf(false) }
                val appLabels = listOf("Ask", "Always", "Never")
                Box {
                    IosRow("Open Links in Apps", value = appLabels[s.openInApps.coerceIn(0, 2)], showChevron = true) { appsOpen = true }
                    DropdownMenu(appsOpen, { appsOpen = false }) {
                        appLabels.forEachIndexed { i, l ->
                            DropdownMenuItem({ Text(l + if (i == s.openInApps) "  ✓" else "") }, { s.openInApps = i; s.save(); appsOpen = false })
                        }
                    }
                }
                IosRow("Downloads", showChevron = true, divider = false) { Browser.showSettings = false; Browser.showDownloads = true }
            }
            IosGroup("Appearance", footer = "Automatic follows your phone's dark mode setting.") {
                listOf("Automatic", "Light", "Dark").forEachIndexed { i, l ->
                    IosRow(l, divider = i < 2, trailing = { if (s.appearance == i) Text("✓", color = c.tint, style = Ios.headline) }) {
                        s.appearance = i; s.save(); com.gr4nt.browser.MainActivity.applyAppearance(ctx, i)
                    }
                }
            }
            IosGroup("Layout", footer = "Choose a compact command dock, an expanded dock with labels, or a top command bar. Every layout retains tab gestures, navigation and browser actions.") {
                listOf(ToolbarLayout.COMPACT, ToolbarLayout.BOTTOM, ToolbarLayout.TOP).forEachIndexed { i, l ->
                    IosRow(
                        when (l) { ToolbarLayout.BOTTOM -> "Expanded dock"; ToolbarLayout.TOP -> "Top command bar"; else -> "Compact dock" },
                        divider = i < 2,
                        trailing = { if (s.layout == l) Text("✓", color = c.tint, style = Ios.headline) }
                    ) { s.layout = l; s.save() }
                }
            }
            IosGroup(footer = "Larger windows use a navigation rail, a top command bar and an optional vertical tab workspace. Library, downloads and privacy tools stay close to your pages.") {
                IosRow("Desktop Layout on Large Screens", divider = false, trailing = { IosSwitch(s.desktopLayout) { s.desktopLayout = it; s.save() } })
            }
            ToolbarAppearanceGroup()
            IosGroup("Tabs", footer = "Closed tabs and tabs you haven't opened for a while move to the Archive (in the tab switcher). Pinch a page inward to get a quick summary.") {
                var archiveOpen by remember { mutableStateOf(false) }
                Box {
                    IosRow("Archive Tabs After", value = archiveLabel(s.archiveAfterHours), showChevron = true) { archiveOpen = true }
                    DropdownMenu(archiveOpen, { archiveOpen = false }) {
                        listOf(12, 24, 168, 720, 0).forEach { h ->
                            DropdownMenuItem({ Text(archiveLabel(h) + if (h == s.archiveAfterHours) "  ✓" else "") }, {
                                s.archiveAfterHours = h; s.save(); archiveOpen = false
                            })
                        }
                    }
                }
                IosRow("Pinch to Summarize", trailing = { IosSwitch(s.pinchToSummarize) { s.pinchToSummarize = it; s.save() } })
                IosRow("Archive", value = "${Store.archive.items.size}", showChevron = true, divider = false) { Browser.showSettings = false; Browser.showArchive = true }
            }
            IosGroup("Privacy & Security") {
                IosRow("Block Ads & Trackers", trailing = { IosSwitch(s.adBlock) { s.adBlock = it; s.save() } })
                IosRow("Hide Ad Elements", subtitle = "Cosmetic filtering & YouTube ad skipping", trailing = { IosSwitch(s.cosmetic) { s.cosmetic = it; s.save() } })
                IosRow("Block Pop-ups", trailing = { IosSwitch(s.blockPopups) { s.blockPopups = it; s.save() } })
                IosRow("Privacy Report", showChevron = true, divider = false) { Browser.showSettings = false; Browser.showPrivacy = true }
            }
            IosGroup(
                "Filter Lists",
                footer = "${AdBlocker.instance.ruleCount} rules loaded. " +
                    (if (s.lastFilterUpdate > 0) "Updated " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.lastFilterUpdate)) + "." else "Not downloaded yet.")
            ) {
                FilterLists.all.forEach { l ->
                    var on by remember { mutableStateOf(FilterLists.isEnabled(ctx, l.id)) }
                    IosRow(l.name, trailing = { IosSwitch(on) { on = it; FilterLists.setEnabled(ctx, l.id, it); AdBlocker.instance.reload(ctx) } })
                }
                IosRow(updating?.let { "Updating $it…" } ?: "Update Filter Lists Now", titleColor = c.tint, divider = false) {
                    if (updating == null) scope.launch {
                        updating = "lists"
                        val n = withContext(Dispatchers.IO) { FilterLists.update(ctx) { name -> updating = name } }
                        updating = null
                        Browser.toast(if (n > 0) "Updated $n filter lists" else "Couldn't update filter lists")
                    }
                }
            }
            val act = ctx as? com.gr4nt.browser.MainActivity
            var fileAccess by remember(visible) { mutableStateOf(act?.hasAllFilesAccess() == true) }
            fun hasPerm(p: String) = androidx.core.content.ContextCompat.checkSelfPermission(ctx, p) == android.content.pm.PackageManager.PERMISSION_GRANTED
            IosGroup("Device Access", footer = "Websites always ask before using your camera, microphone or location. Full file access lets Prism Browser save downloads to any folder and open any file on your phone.") {
                IosRow("Files & Storage", value = if (fileAccess) "All Files" else "Downloads Only", showChevron = true) {
                    act?.requestAllFilesAccess(); fileAccess = act?.hasAllFilesAccess() == true
                }
                IosRow("Camera", value = if (hasPerm(android.Manifest.permission.CAMERA)) "Allowed" else "Ask", showChevron = true) {
                    com.gr4nt.browser.browser.SitePermissions.requestRuntime?.invoke(arrayOf(android.Manifest.permission.CAMERA)) { }
                }
                IosRow("Microphone", value = if (hasPerm(android.Manifest.permission.RECORD_AUDIO)) "Allowed" else "Ask", showChevron = true) {
                    com.gr4nt.browser.browser.SitePermissions.requestRuntime?.invoke(arrayOf(android.Manifest.permission.RECORD_AUDIO)) { }
                }
                IosRow("Open File…", divider = false, showChevron = true) { Browser.showSettings = false; act?.openFilePicker() }
            }
            WebsitePermissionsGroup()
            IosGroup("Downloads", footer = "More connections download large files faster when the server allows it — each connection is a separate TCP stream. Streams (HLS/DASH) download segments in parallel and are converted to MP4.") {
                IosRow(
                    "Save Downloads To",
                    value = s.downloadDir.ifBlank { "Download/iBrowser" }.removePrefix("/storage/emulated/0/"),
                    showChevron = true
                ) { act?.openFolderPicker() }
                if (s.downloadDir.isNotBlank()) IosRow("Reset to Download/iBrowser", titleColor = c.tint) { s.downloadDir = ""; s.save() }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row { Text("Connections per download", style = Ios.body, color = c.label, modifier = Modifier.weight(1f)); Text("${s.threads}", style = Ios.body, color = c.secondary) }
                    Slider(
                        s.threads.toFloat(), { s.threads = it.toInt(); s.save() }, valueRange = 1f..32f, steps = 30,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = c.tint, inactiveTrackColor = c.fill)
                    )
                    Row { Text("Simultaneous downloads", style = Ios.body, color = c.label, modifier = Modifier.weight(1f)); Text("${s.maxParallel}", style = Ios.body, color = c.secondary) }
                    Slider(
                        s.maxParallel.toFloat(), { s.maxParallel = it.toInt(); s.save() }, valueRange = 1f..8f, steps = 6,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = c.tint, inactiveTrackColor = c.fill)
                    )
                }
                Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(c.separator))
                IosRow("Ask Before Downloading", trailing = { IosSwitch(s.askBeforeDownload) { s.askBeforeDownload = it; s.save() } })
                IosRow("Detect Media on Pages", subtitle = "Find videos, audio and streams to download", divider = false, trailing = { IosSwitch(s.sniffMedia) { s.sniffMedia = it; s.save() } })
            }
            IosGroup("Websites") {
                IosRow("JavaScript", trailing = { IosSwitch(s.javascript) { s.javascript = it; s.save(); applyAll() } })
                IosRow("Request Desktop Website", trailing = { IosSwitch(s.desktopDefault) { s.desktopDefault = it; s.save() } })
                IosRow("Dark Web Pages", subtitle = "Darken pages when the system is in dark mode", trailing = { IosSwitch(s.darkWeb) { s.darkWeb = it; s.save(); applyAll() } })
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row { Text("Page Zoom", style = Ios.body, color = c.label, modifier = Modifier.weight(1f)); Text("${s.textZoom}%", style = Ios.body, color = c.secondary) }
                    Slider(
                        s.textZoom.toFloat(), { s.textZoom = (it / 10).toInt() * 10; s.save(); Browser.tabs.forEach { t -> t.textZoom = s.textZoom; t.webView?.settings?.textZoom = s.textZoom } }, valueRange = 50f..200f,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = c.tint, inactiveTrackColor = c.fill)
                    )
                }
            }
            IosGroup {
                IosRow("Clear History and Website Data", titleColor = c.red, divider = false) { Browser.clearHistoryAndData() }
            }
            IosGroup("About", footer = "Prism Browser — a clearer view of the web. Built for Android.") {
                IosRow("Rules loaded", value = "${AdBlocker.instance.ruleCount}")
                IosRow("Trackers blocked", value = "${s.totalBlocked}")
                IosRow("Privacy Policy", showChevron = true, divider = false) {
                    Browser.showSettings = false
                    Browser.newTab("file:///android_asset/privacy.html")
                }
            }
        }
    }
}

private fun applyAll() {
    Browser.tabs.forEach { t -> t.webView?.let { Browser.applySettings(it, t) } }
}

// ============================================================ Privacy report

@Composable
fun PrivacySheet(visible: Boolean) {
    val c = Ios.c
    val s = Store.settings
    IosSheet(visible, "Privacy Report", { Browser.showPrivacy = false }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(24.dp)).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Shield, null, tint = c.green, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("${s.totalBlocked}", style = Ios.title.copy(fontSize = 30.sp()), color = c.label)
                        Text("ads and trackers blocked", style = Ios.subhead, color = c.secondary)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val t = Browser.current
            if (t != null && !t.isStartPage) {
                Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(24.dp)).padding(20.dp)) {
                    Text("This page", style = Ios.footnote, color = c.secondary)
                    Text(t.host, style = Ios.headline, color = c.label)
                    Spacer(Modifier.height(6.dp))
                    Text("${t.blocked} requests blocked", style = Ios.body, color = c.label)
                }
                Spacer(Modifier.height(12.dp))
            }
            Text(
                "Prism Browser blocks ads, trackers, cryptominers and malware domains using ${AdBlocker.instance.ruleCount} filter rules " +
                    "from EasyList, EasyPrivacy, uBlock, AdGuard and more. Elements left behind by blocked ads are hidden, " +
                    "and pop-ups that open without a tap are stopped.",
                style = Ios.footnote, color = c.secondary, modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

// ============================================================ Website settings

@Composable
fun SiteSettingsSheet(visible: Boolean) {
    val c = Ios.c
    val t = Browser.current
    IosSheet(visible, t?.host ?: "Website", { Browser.showSiteSettings = false }) {
        if (t == null) return@IosSheet
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            IosGroup("Settings for ${t.host}") {
                IosRow("Request Desktop Website", trailing = { IosSwitch(t.desktop) { Browser.toggleDesktop() } })
                val adsOn = t.host !in Store.settings.disabledAdSites
                IosRow("Block Ads on This Site", trailing = { IosSwitch(adsOn) { Browser.toggleSiteAdBlock() } })
                IosRow("Page Zoom", value = "${t.textZoom}%", divider = false)
            }
            IosGroup {
                IosRow("Blocked on this page", value = "${t.blocked}")
                IosRow("Connection", value = if (t.secure) "Secure (HTTPS)" else "Not secure", divider = false)
            }
        }
    }
}

// ============================================================ Library (Bookmarks / Reading list / History)

@Composable
fun LibrarySheet(visible: Boolean) {
    IosSheet(visible, "Library", { Browser.showLibrary = false }) {
        StartPage(null, inSheet = true)
    }
}

@Suppress("unused")
private val keepImports = listOf(Icons.Outlined.Delete)


@Composable
private fun WebsitePermissionsGroup() {
    val c = Ios.c
    var version by remember { mutableStateOf(0) }
    val all = remember(version) { com.gr4nt.browser.browser.SitePermissions.all() }
    if (all.isEmpty()) return
    IosGroup("Websites", footer = "Tap a site to reset its permissions — it will ask again next time.") {
        all.entries.forEachIndexed { i, (host, perms) ->
            IosRow(
                host,
                subtitle = perms.entries.joinToString(" · ") { (a, d) ->
                    a.label.replaceFirstChar { it.uppercase() } + ": " + when (d) {
                        com.gr4nt.browser.browser.SitePermissions.Decision.ALLOW -> "Allow"
                        com.gr4nt.browser.browser.SitePermissions.Decision.DENY -> "Deny"
                        else -> "Ask"
                    }
                },
                divider = i < all.size - 1,
                trailing = { Text("Reset", style = Ios.subhead, color = c.tint) }
            ) { com.gr4nt.browser.browser.SitePermissions.clear(host); version++ }
        }
    }
}


/** Paste-a-link box: http(s), ftp, HLS (.m3u8), DASH (.mpd), data: and content:// links. */
@Composable
private fun NewDownloadBox(onGo: (String) -> Unit) {
    val c = Ios.c
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    var text by remember {
        mutableStateOf(clip.getText()?.text?.let { com.gr4nt.browser.download.LinkResolver.extractLink(it) } ?: "")
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).glassCard(RoundedCornerShape(22.dp)).padding(14.dp)) {
        Text("New Download", style = Ios.headline, color = c.label)
        Spacer(Modifier.height(4.dp))
        Text("Paste any link — files, FTP, video streams (.m3u8 / .mpd) or data: links.", style = Ios.footnote, color = c.secondary)
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.text.BasicTextField(
            text, { text = it }, singleLine = true,
            textStyle = Ios.body.copy(color = c.label),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(c.tint),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri, imeAction = androidx.compose.ui.text.input.ImeAction.Go
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { if (text.isNotBlank()) onGo(text) }),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.fill).padding(12.dp),
            decorationBox = { inner -> Box { if (text.isEmpty()) Text("https://…", style = Ios.body, color = c.secondary); inner() } }
        )
        Spacer(Modifier.height(10.dp))
        Row {
            Box(
                Modifier.weight(1f).height(40.dp).pressable(scaleTo = 0.94f) { clip.getText()?.text?.let { text = it.trim() } }
                    .clip(RoundedCornerShape(20.dp)).background(c.fill),
                contentAlignment = Alignment.Center
            ) { Text("Paste", style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label) }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.weight(1f).height(40.dp).pressable(enabled = text.isNotBlank(), scaleTo = 0.94f) { onGo(text) }
                    .clip(RoundedCornerShape(20.dp)).background(if (text.isNotBlank()) c.tint else c.fill),
                contentAlignment = Alignment.Center
            ) { Text("Download", style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold), color = if (text.isNotBlank()) Color.White else c.secondary) }
        }
    }
}


@Composable
private fun ToolbarAppearanceGroup() {
    val s = Store.settings
    val c = Ios.c
    IosGroup(
        "Toolbar",
        footer = "Auto-hide slides the toolbar away when you haven't touched the screen for a while — any touch brings it back. " +
            "Match Page Colour tints the glass with the colour of the page behind it."
    ) {
        // live preview of the bar over a busy, colourful backdrop
        Box(
            Modifier.fillMaxWidth().padding(12.dp).height(96.dp).clip(RoundedCornerShape(20.dp)).wallpaper()
                .drawBehind {
                    val cols = listOf(Color(0xFFFF8A65), Color(0xFF64B5F6), Color(0xFFFFD54F), Color(0xFF81C784), Color(0xFFBA68C8))
                    val w = size.width / 9f
                    for (k in 0 until 9) drawRect(cols[k % cols.size].copy(alpha = 0.55f), topLeft = androidx.compose.ui.geometry.Offset(k * w, 0f), size = androidx.compose.ui.geometry.Size(w * 0.55f, size.height))
                    drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.62f, 0f), size = androidx.compose.ui.geometry.Size(size.width * 0.38f, size.height))
                },
            contentAlignment = Alignment.Center
        ) {
            AdaptiveGlass(null) {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassCircleButton(Icons.Outlined.ChevronLeft, "Preview back", size = 44.dp) { }
                    Box(Modifier.weight(1f).height(44.dp).glass(RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                        Text("example.com", style = Ios.callout.copy(fontWeight = FontWeight.Medium), color = Ios.c.label)
                    }
                    GlassCircleButton(Icons.Outlined.FilterNone, "Preview tabs", size = 44.dp) { }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(c.separator))
        IosRow("Liquid Glass", subtitle = "Sheen, light rim and refraction glow", trailing = { IosSwitch(s.barLiquidGlass) { s.barLiquidGlass = it; s.save() } })
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row {
                Text("Transparency", style = Ios.body, color = c.label, modifier = Modifier.weight(1f))
                Text("${(s.barTransparency * 100).toInt()}%", style = Ios.body, color = c.secondary)
            }
            Slider(
                s.barTransparency, { s.barTransparency = (it * 20).toInt() / 20f }, onValueChangeFinished = { s.save() },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = c.tint, inactiveTrackColor = c.fill)
            )
            Row {
                Text("Solid", style = Ios.footnote, color = c.secondary, modifier = Modifier.weight(1f))
                Text("Clear", style = Ios.footnote, color = c.secondary)
            }
        }
        Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(c.separator))
        IosRow("Match Page Colour", trailing = { IosSwitch(s.barAdaptiveTint) { s.barAdaptiveTint = it; s.save() } })
        IosRow("Auto-Hide Toolbar", divider = s.autoHideToolbar, trailing = { IosSwitch(s.autoHideToolbar) { s.autoHideToolbar = it; s.save() } })
        if (s.autoHideToolbar) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row {
                    Text("Hide After", style = Ios.body, color = c.label, modifier = Modifier.weight(1f))
                    Text("${s.autoHideSeconds} s", style = Ios.body, color = c.secondary)
                }
                Slider(
                    s.autoHideSeconds.toFloat(), { s.autoHideSeconds = it.toInt() }, onValueChangeFinished = { s.save() },
                    valueRange = 2f..10f, steps = 7,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = c.tint, inactiveTrackColor = c.fill)
                )
            }
        }
    }
}
