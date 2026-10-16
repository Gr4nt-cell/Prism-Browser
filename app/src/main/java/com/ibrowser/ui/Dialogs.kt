package com.ibrowser.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ibrowser.browser.Browser
import com.ibrowser.browser.DownloadRequest
import com.ibrowser.browser.ReaderContent
import com.ibrowser.data.Store
import com.ibrowser.download.DashParser
import com.ibrowser.download.DlKind
import com.ibrowser.download.DownloadItem
import com.ibrowser.download.DownloadManager
import com.ibrowser.download.HlsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Quality(val label: String, val variant: String?, val audio: String?)

@Composable
fun DownloadDialog(r: DownloadRequest) {
    val c = Ios.c
    var name by remember(r) { mutableStateOf(r.fileName) }
    var threads by remember(r) { mutableIntStateOf(Store.settings.threads) }
    var qualities by remember(r) { mutableStateOf<List<Quality>?>(if (r.kind == DlKind.FILE) emptyList() else null) }
    var selected by remember(r) { mutableIntStateOf(0) }
    var error by remember(r) { mutableStateOf<String?>(null) }

    LaunchedEffect(r) {
        if (r.kind == DlKind.FILE) return@LaunchedEffect
        val probe = DownloadItem("probe", r.url, "x", r.kind, r.mime, r.userAgent, r.referer, 1)
        qualities = withContext(Dispatchers.IO) {
            runCatching {
                val text = DownloadManager.fetchText(probe, r.url)
                if (r.kind == DlKind.HLS) {
                    if (HlsParser.isMaster(text)) {
                        val m = HlsParser.parseMaster(text, r.url)
                        m.variants.distinctBy { it.label }.map { Quality(it.label, it.url, HlsParser.audioFor(m, it)) }
                    } else {
                        val media = HlsParser.parseMedia(text, r.url)
                        if (media.segments.any { it.keyMethod != null && it.keyMethod != "AES-128" }) error = "This stream is DRM protected."
                        listOf(Quality("Original quality · ${media.segments.size} segments", null, null))
                    }
                } else {
                    val reps = DashParser.parse(text, r.url)
                    if (reps.any { it.protected }) error = "This stream is DRM protected."
                    val audio = reps.filter { it.type == "audio" }.maxByOrNull { it.bandwidth }?.id
                    reps.filter { it.type == "video" }.sortedByDescending { it.bandwidth }.distinctBy { it.label }
                        .map { Quality(it.label, it.id, audio) }.ifEmpty { listOf(Quality("Audio only", null, audio)) }
                }
            }.getOrElse { e -> error = "Couldn't read stream: ${e.message}"; emptyList() }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(interactionSource = null, indication = null) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(28.dp).fillMaxWidth().heightIn(max = 620.dp).clip(RoundedCornerShape(30.dp)).background(c.glassStrong)
                .verticalScroll(rememberScrollState())
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (r.kind == DlKind.FILE) "Do you want to download this file?" else "Download this video?",
                    style = Ios.headline, color = c.label, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                BasicTextField(
                    name, { name = it }, singleLine = true,
                    textStyle = Ios.subhead.copy(color = c.label, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(c.tint),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.fill).padding(10.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        if (r.size > 0) append(formatBytes(r.size)) else if (r.kind == DlKind.FILE) append("Unknown size")
                        if (r.kind != DlKind.FILE) append(if (r.kind == DlKind.HLS) "HLS stream → MP4" else "DASH stream → MP4")
                        append(" · ").append(r.url.substringAfter("://").substringBefore('/'))
                    },
                    style = Ios.footnote, color = c.secondary, textAlign = TextAlign.Center
                )
                error?.let { Spacer(Modifier.height(6.dp)); Text(it, style = Ios.footnote, color = c.red, textAlign = TextAlign.Center) }
            }

            if (r.kind != DlKind.FILE) {
                Text("QUALITY", style = Ios.footnote, color = c.secondary, modifier = Modifier.padding(start = 22.dp, bottom = 4.dp))
                val q = qualities
                if (q == null) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = c.tint, strokeWidth = 2.5.dp)
                    }
                } else {
                    Column(Modifier.padding(horizontal = 14.dp).clip(RoundedCornerShape(18.dp)).background(c.card)) {
                        q.forEachIndexed { i, item ->
                            Row(
                                Modifier.fillMaxWidth().tapHighlight { selected = i }.padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.label, style = Ios.subhead, color = c.label, modifier = Modifier.weight(1f))
                                if (selected == i) Text("✓", style = Ios.headline, color = c.tint)
                            }
                            if (i < q.size - 1) Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(0.5.dp).background(c.separator))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Column(Modifier.padding(horizontal = 22.dp)) {
                Row {
                    Text(if (r.kind == DlKind.FILE) "Connections" else "Parallel segments", style = Ios.subhead, color = c.label, modifier = Modifier.weight(1f))
                    Text("$threads", style = Ios.subhead, color = c.secondary)
                }
                Slider(
                    threads.toFloat(), { threads = it.toInt() }, valueRange = 1f..32f, steps = 30,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = c.tint, inactiveTrackColor = c.fill)
                )
            }

            Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
            Row(Modifier.fillMaxWidth().height(52.dp)) {
                Box(Modifier.weight(1f).fillMaxSize().tapHighlight { Browser.pendingDownload = null }, contentAlignment = Alignment.Center) {
                    Text("Cancel", style = Ios.body, color = c.tint)
                }
                Box(Modifier.width(0.5.dp).fillMaxSize().background(c.separator))
                val canGo = r.kind == DlKind.FILE || (qualities != null)
                Box(
                    Modifier.weight(1f).fillMaxSize().tapHighlight(enabled = canGo) {
                        val q = qualities?.getOrNull(selected)
                        Browser.startDownload(r.copy(fileName = name.ifBlank { r.fileName }), q?.variant, q?.audio, threads)
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Download", style = Ios.headline, color = if (canGo) c.tint else c.tertiary)
                }
            }
        }
    }
}

// ============================================================ Reader

private val READER_THEMES = listOf(
    Triple("White", "#FFFFFF", "#1C1C1E"),
    Triple("Sepia", "#F8F1E3", "#4F321C"),
    Triple("Gray", "#4A4A4D", "#E8E8EA"),
    Triple("Black", "#000000", "#C7C7CC")
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderOverlay(content: ReaderContent?) {
    AnimatedVisibility(content != null, enter = slideInVertically { it / 4 } + fadeIn(), exit = slideOutVertically { it / 4 } + fadeOut()) {
        val rc = content ?: return@AnimatedVisibility
        val c = Ios.c
        var theme by remember { mutableIntStateOf(if (c.dark) 3 else 0) }
        var size by remember { mutableIntStateOf(19) }
        var serif by remember { mutableStateOf(true) }
        var showOpts by remember { mutableStateOf(false) }
        val (_, bg, fg) = READER_THEMES[theme]
        val html = """
            <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
            body{background:$bg;color:$fg;font-family:${if (serif) "Georgia, 'Noto Serif', serif" else "-apple-system, Roboto, sans-serif"};
                 font-size:${size}px;line-height:1.55;margin:0;padding:24px 22px 120px;}
            h1.t{font-family:-apple-system,Roboto,sans-serif;font-size:1.6em;line-height:1.2;margin:8px 0 6px;}
            .m{font-family:-apple-system,Roboto,sans-serif;font-size:.8em;opacity:.6;margin-bottom:22px;}
            img,video,figure{max-width:100%;height:auto;border-radius:10px;margin:12px 0;}
            a{color:${if (theme >= 2) "#64B5F6" else "#007AFF"};text-decoration:none;}
            pre,code{white-space:pre-wrap;font-size:.85em;}
            blockquote{border-left:3px solid rgba(127,127,127,.4);margin:0;padding-left:14px;opacity:.85}
            </style></head><body>
            <div class="m">${rc.site}</div><h1 class="t">${rc.title}</h1>
            ${if (rc.byline.isNotBlank()) "<div class=\"m\">${rc.byline}</div>" else ""}
            ${rc.html}</body></html>
        """.trimIndent()
        Box(Modifier.fillMaxSize().background(Color(android.graphics.Color.parseColor(bg)))) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                                Browser.reader = null
                                Browser.load(request.url.toString())
                                return true
                            }
                        }
                    }
                },
                update = { it.loadDataWithBaseURL(Browser.current?.url, html, "text/html", "utf-8", null) },
                modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 52.dp)
            )
            Row(
                Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(42.dp).pressable(scaleTo = 0.85f) { showOpts = !showOpts }.glass(CircleShape), contentAlignment = Alignment.Center) {
                    Text("aA", style = Ios.headline, color = c.label)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.height(42.dp).pressable(scaleTo = 0.88f) { Browser.reader = null }.glass(RoundedCornerShape(21.dp)).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text("Done", style = Ios.headline, color = c.tint)
                }
            }
            if (showOpts) {
                Column(
                    Modifier.statusBarsPadding().padding(start = 12.dp, top = 56.dp).width(260.dp)
                        .glass(RoundedCornerShape(22.dp), strong = true).padding(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(c.fill).clickable { size = (size - 1).coerceAtLeast(13) }, contentAlignment = Alignment.Center) {
                            Text("A", fontSize = 14.sp, color = c.label)
                        }
                        Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(c.fill).clickable { size = (size + 1).coerceAtMost(32) }, contentAlignment = Alignment.Center) {
                            Text("A", fontSize = 22.sp, color = c.label)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        READER_THEMES.forEachIndexed { i, (_, b, _) ->
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(b)))
                                    .then(if (theme == i) Modifier.background(c.tint.copy(alpha = 0.25f)) else Modifier)
                                    .clickable { theme = i }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Serif" to true, "Sans" to false).forEach { (l, v) ->
                            Box(
                                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (serif == v) c.tint else c.fill).clickable { serif = v },
                                contentAlignment = Alignment.Center
                            ) { Text(l, style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold), color = if (serif == v) Color.White else c.label) }
                        }
                    }
                }
            }
        }
    }
}
