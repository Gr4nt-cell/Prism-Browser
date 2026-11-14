package app.prism.browser.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import androidx.core.widget.doAfterTextChanged
import app.prism.browser.R
import app.prism.browser.adblock.AdBlocker
import app.prism.browser.data.SearchEngine
import app.prism.browser.data.Site
import app.prism.browser.data.Store
import app.prism.browser.download.DKind
import app.prism.browser.download.DStatus
import app.prism.browser.download.DownloadItem
import app.prism.browser.download.Downloader
import app.prism.browser.download.Storage
import app.prism.browser.engine.BrowserController
import app.prism.browser.engine.Sheet
import app.prism.browser.engine.Snack
import app.prism.browser.engine.UrlUtil
import app.prism.browser.video.MediaItem
import app.prism.browser.video.MediaKind
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Bottom sheet on phones, Safari-style popover on tablets. */
class SheetHost(ctx: Context, private val c: BrowserController) : FrameLayout(ctx) {
    private val scrim = View(ctx).apply { setOnClickListener { c.sheet = Sheet.NONE } }
    private val panel = ClampFrame(ctx, ctx.dp(640), ctx.dp(720))
    private var shown: Sheet = Sheet.NONE
    private var hiding = false

    init {
        visibility = GONE
        addView(scrim, LayoutParams(MATCH, MATCH))
        addView(panel, LayoutParams(MATCH, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        observe { render() }
    }

    private fun render() {
        val s = c.sheet
        val large = c.largeScreen
        scrim.setBackgroundColor(Prism.Ink.a(if (large) .12f else .28f))
        if (s == Sheet.NONE) {
            if (visibility == VISIBLE && !hiding) hide(large)
            return
        }
        place(large)
        if (s != shown) {
            shown = s
            panel.removeAllViews()
            panel.addView(panelContent(s, large), FrameLayout.LayoutParams(MATCH, WRAP))
        }
        if (visibility != VISIBLE || hiding) show(large)
    }

    private fun place(large: Boolean) {
        panel.maxWidthPx = context.dp(if (large) 400 else 640)
        panel.maxHeightPx = context.dp(if (large) 640 else 720)
        panel.layoutParams = if (large) {
            LayoutParams(context.dp(400), WRAP, Gravity.TOP or Gravity.END).apply {
                setMargins(0, ScreenInsets.top + context.dp(56), context.dp(12), 0)
            }
        } else {
            LayoutParams(MATCH, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        }
        panel.glass(if (large) 22f else 32f, 18, strength = 1.25f, radii = if (large) null else floatArrayOf(32f, 32f, 32f, 32f, 0f, 0f, 0f, 0f))
        panel.isClickable = true
    }

    private fun show(large: Boolean) {
        hiding = false
        animate().cancel()
        visibility = VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(180).start()
        if (!large) panel.post {
            panel.translationY = panel.height.toFloat()
            panel.animate().translationY(0f).setDuration(260).start()
        }
    }

    private fun hide(large: Boolean) {
        hiding = true
        animate().alpha(0f).setDuration(160).withEndAction {
            if (hiding) {
                visibility = GONE
                panel.removeAllViews()
                shown = Sheet.NONE
                hiding = false
            }
        }.start()
        if (!large) panel.animate().translationY(panel.height.toFloat()).setDuration(220).start()
    }

    private fun panelContent(s: Sheet, large: Boolean): View {
        val ctx = context
        val body = when (s) {
            Sheet.MENU -> ctx.menuBody(c)
            Sheet.DOWNLOADS -> ctx.downloadsBody(c)
            Sheet.VIDEOS -> ctx.videosBody(c)
            Sheet.HISTORY -> ctx.historyBody(c)
            Sheet.BOOKMARKS -> ctx.bookmarksBody(c)
            Sheet.SETTINGS -> ctx.settingsBody(c)
            Sheet.SHIELD -> ctx.shieldBody(c)
            Sheet.ADD_FAVORITE -> ctx.addFavoriteBody(c)
            Sheet.NONE -> LinearLayout(ctx)
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(18), ctx.dp(10), ctx.dp(18), ctx.dp(16) + if (large) 0 else ScreenInsets.bottom)
            if (!large) add(FrameLayout(ctx).apply { background = GradientDrawable().apply { cornerRadius = ctx.dp(PILL).toFloat(); setColor(Prism.Ink.a(.18f)) } }, w = 40, h = 5, g = Gravity.CENTER_HORIZONTAL, b = 8)
            add(ScrollView(ctx).apply {
                isVerticalScrollBarEnabled = false
                addView(body, FrameLayout.LayoutParams(MATCH, WRAP))
            }, w = MATCH)
        }
    }
}

/** Body whose children are rebuilt whenever observable state changes. */
private fun Context.liveBody(build: LinearLayout.() -> Unit): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    observe {
        removeAllViews()
        build()
    }
}

private fun Context.sheetTitle(title: String, c: BrowserController, trailing: View? = null): LinearLayout = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL
    setPadding(0, 0, 0, dp(10))
    add(text(title, 20f, 700), weight = 1f)
    trailing?.let { add(it) }
    add(press(36, 36, onClick = { c.sheet = Sheet.NONE }, content = icon(R.drawable.ic_close, Prism.Mute, 20)))
}

private fun Context.pillChip(label: String, bg: Int, fg: Int, onClick: () -> Unit): FrameLayout = FrameLayout(this).apply {
    background = GradientDrawable().apply { cornerRadius = dp(PILL).toFloat(); setColor(bg) }
    isClickable = true
    setOnClickListener { onClick() }
    setPadding(dp(14), dp(8), dp(14), dp(8))
    addView(text(label, 13f, 600, fg), FrameLayout.LayoutParams(WRAP, WRAP))
}

private fun Context.textAction(label: String, onClick: () -> Unit): View = text(label, 13f, 600, Prism.Accent).apply {
    setPadding(dp(12), dp(8), dp(12), dp(8))
    isClickable = true
    setOnClickListener { onClick() }
}

private fun Context.emptyState(iconRes: Int, title: String, hint: String, lines: Int = 1): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER_HORIZONTAL
    setPadding(0, dp(30), 0, dp(30))
    add(icon(iconRes, Prism.Mute, 40), g = Gravity.CENTER_HORIZONTAL, b = 10)
    add(text(title, 15f, 600), g = Gravity.CENTER_HORIZONTAL)
    add(text(hint, 12f, color = Prism.Mute, lines = lines).apply { gravity = Gravity.CENTER_HORIZONTAL }, t = 4)
}

// ------------------------------------------------------------------ menu

private class MenuAction(
    val iconRes: Int, val label: String, val enabled: Boolean = true,
    val tint: Int = Prism.Ink, val go: () -> Unit,
)

private fun Context.menuBody(c: BrowserController): LinearLayout = liveBody {
    val tab = c.active
    val onPage = tab != null && !tab.isStart
    val bookmarked = tab != null && Store.isBookmarked(tab.url)
    if (tab != null && onPage) add(LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        add(context.favicon(tab, 20))
        add(View(context), w = 10)
        add(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            add(context.text(tab.title, 15f, 600))
            add(context.text(tab.displayHost, 12f, color = Prism.Mute))
        }, weight = 1f)
    }, b = 14)
    val actions = listOf(
        MenuAction(R.drawable.ic_back, "Back", onPage || tab?.webView?.canGoBack() == true) { c.sheet = Sheet.NONE; c.back() },
        MenuAction(R.drawable.ic_forward, "Forward", tab?.canGoForward == true) { c.sheet = Sheet.NONE; c.forward() },
        MenuAction(R.drawable.ic_refresh, "Reload", onPage) { c.sheet = Sheet.NONE; c.reload() },
        MenuAction(R.drawable.ic_add, "New tab") { c.sheet = Sheet.NONE; c.newTab() },
        MenuAction(
            if (bookmarked) R.drawable.ic_star else R.drawable.ic_star_border,
            if (bookmarked) "Saved" else "Bookmark", onPage, if (bookmarked) Prism.Accent else Prism.Ink,
        ) { tab?.let { Store.toggleBookmark(it.title, it.url) } },
        MenuAction(R.drawable.ic_share, "Share", onPage) { tab?.let { context.shareText(it.url, it.title) } },
        MenuAction(R.drawable.ic_search, "Find", onPage) { c.sheet = Sheet.NONE; c.findVisible = true },
        MenuAction(R.drawable.ic_download, "Downloads") { c.sheet = Sheet.DOWNLOADS },
        MenuAction(R.drawable.ic_videocam, "Videos" + if (tab?.media?.isNotEmpty() == true) " (${tab.media.size})" else "", onPage) { c.sheet = Sheet.VIDEOS },
        MenuAction(R.drawable.ic_bookmark, "Bookmarks") { c.sheet = Sheet.BOOKMARKS },
        MenuAction(R.drawable.ic_history, "History") { c.sheet = Sheet.HISTORY },
        MenuAction(
            if (tab?.desktop == true) R.drawable.ic_mobile else R.drawable.ic_desktop,
            if (tab?.desktop == true) "Mobile site" else "Desktop site", onPage,
        ) { c.sheet = Sheet.NONE; c.toggleDesktop() },
        MenuAction(R.drawable.ic_shield, "Shield", true, Prism.Accent) { c.sheet = Sheet.SHIELD },
        MenuAction(R.drawable.ic_open_external, "Open in app", onPage) { c.sheet = Sheet.NONE; c.openInOtherApp() },
        MenuAction(R.drawable.ic_tab, "Add to Home", onPage) { c.sheet = Sheet.ADD_FAVORITE },
        MenuAction(R.drawable.ic_settings, "Settings") { c.sheet = Sheet.SETTINGS },
    )
    actions.chunked(4).forEachIndexed { ri, row ->
        add(LinearLayout(context).apply {
            row.forEachIndexed { ci, a -> add(context.menuTile(a), weight = 1f, l = if (ci > 0) 10 else 0) }
            repeat(4 - row.size) { add(View(context), weight = 1f, l = 10) }
        }, t = if (ri > 0) 12 else 0)
    }
}

private fun Context.menuTile(a: MenuAction): View = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER_HORIZONTAL
    setPadding(0, dp(4), 0, dp(4))
    if (a.enabled) {
        isClickable = true
        setOnClickListener { a.go() }
    }
    add(FrameLayout(context).apply {
        glass(20f, 4)
        addView(icon(a.iconRes, if (a.enabled) a.tint else Prism.Mute.a(.4f), 24), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
    }, h = 56)
    add(text(a.label, 11f, color = if (a.enabled) Prism.Ink else Prism.Mute.a(.5f)), t = 5)
}

// ------------------------------------------------------------------ downloads

private fun Context.downloadsBody(c: BrowserController): LinearLayout = liveBody {
    val items = Downloader.items.toList()
    val running = items.any { it.status == DStatus.RUNNING || it.status == DStatus.QUEUED }
    val finished = items.filter { it.status == DStatus.DONE }
    val trailing = when {
        running -> context.textAction("Pause all") { Downloader.pauseAll() }
        finished.isNotEmpty() -> context.textAction("Clear") { finished.forEach { Downloader.remove(it, false) } }
        else -> null
    }
    add(sheetTitle("Downloads", c, trailing))
    if (items.isEmpty()) {
        add(emptyState(R.drawable.ic_folder, "No downloads yet", "Files and videos you save appear here"))
        return@liveBody
    }
    items.forEachIndexed { i, d -> add(context.downloadRow(d), t = if (i > 0) 10 else 0) }
}

private fun Context.downloadRow(d: DownloadItem): View {
    val fmt = { b: Long -> Formatter.formatShortFileSize(this, b) }
    val mime = Storage.mimeFor(d.fileName, d.mime)
    val iconRes = when {
        d.kind == DKind.HLS || mime.startsWith("video") -> R.drawable.ic_videocam
        mime.startsWith("audio") -> R.drawable.ic_audio
        else -> R.drawable.ic_download
    }
    val active = d.status == DStatus.RUNNING || d.status == DStatus.PAUSED || d.status == DStatus.QUEUED
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        glass(20f, 3)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        if (d.status == DStatus.DONE) {
            isClickable = true
            setOnClickListener { Storage.open(context, d) }
        }
        add(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            add(FrameLayout(context).apply {
                background = GradientDrawable().apply { cornerRadius = dp(13).toFloat(); setColor(Prism.Accent.a(.12f)) }
                addView(icon(iconRes, Prism.Accent, 22), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
            }, w = 40, h = 40)
            add(View(context), w = 12)
            add(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                add(context.text(d.fileName, 14f, 600))
                add(context.text(statusLine(d, fmt), 11f, color = if (d.status == DStatus.FAILED) Prism.Accent else Prism.Mute, lines = 2))
            }, weight = 1f)
            when (d.status) {
                DStatus.RUNNING, DStatus.QUEUED -> add(context.press(38, 36, onClick = { Downloader.pause(d) }, content = context.icon(R.drawable.ic_pause, Prism.Ink, 20)))
                DStatus.PAUSED -> add(context.press(38, 36, onClick = { Downloader.resume(d) }, content = context.icon(R.drawable.ic_play, Prism.Accent, 22)))
                DStatus.FAILED -> add(context.press(38, 36, onClick = { Downloader.retry(d) }, content = context.icon(R.drawable.ic_refresh, Prism.Accent, 20)))
                DStatus.DONE -> add(context.press(38, 36, onClick = { Storage.share(context, d) }, content = context.icon(R.drawable.ic_share, Prism.Ink, 19)))
                else -> Unit
            }
            add(context.press(
                38, 36,
                onClick = { if (d.status == DStatus.DONE) Downloader.remove(d, deleteFile = true) else Downloader.cancel(d) },
                content = context.icon(if (d.status == DStatus.DONE) R.drawable.ic_delete else R.drawable.ic_close, Prism.Mute, 19),
            ))
        })
        if (active) {
            val p = d.progress
            add(LineProgress(context).apply {
                set(if (p >= 0) p else .3f, if (d.status == DStatus.PAUSED) Prism.Mute else Prism.Accent)
            }, w = MATCH, h = 5, t = 10)
        }
    }
}

private fun statusLine(d: DownloadItem, fmt: (Long) -> String): String = when (d.status) {
    DStatus.DONE -> "${fmt(d.total)} · ${ago(d.finishedAt)}"
    DStatus.FAILED -> "Failed · ${d.error ?: "unknown error"}"
    DStatus.PAUSED -> "Paused · " + progressText(d, fmt) + (if (!d.resumable) " · restarts on resume" else "")
    DStatus.QUEUED -> "Waiting…"
    DStatus.RUNNING -> buildString {
        append(progressText(d, fmt))
        if (d.speed > 0) append(" · ").append(fmt(d.speed)).append("/s")
        if (d.activeThreads > 1) append(" · ").append(d.activeThreads).append(" threads")
        val eta = d.etaSeconds
        if (eta in 1..359_999) append(" · ").append(etaText(eta))
    }
    DStatus.CANCELED -> "Canceled"
}

private fun progressText(d: DownloadItem, fmt: (Long) -> String): String = when {
    d.kind == DKind.HLS && d.partsTotal > 0 -> "${fmt(d.downloaded)} · ${d.partsDone}/${d.partsTotal} parts"
    d.total > 0 -> "${fmt(d.downloaded)} of ${fmt(d.total)}"
    else -> fmt(d.downloaded)
}

private fun etaText(s: Long): String = when {
    s < 60 -> "${s}s left"
    s < 3600 -> "${s / 60}m ${s % 60}s left"
    else -> "${s / 3600}h ${(s % 3600) / 60}m left"
}

// ------------------------------------------------------------------ videos

private fun Context.videosBody(c: BrowserController): LinearLayout {
    val scope = MainScope()
    val probing = HashSet<String>()
    val body = liveBody {
        add(sheetTitle("Videos on this page", c))
        val media = c.active?.media?.toList().orEmpty()
        when {
            !Store.videoDetection -> add(text("Video detection is off. Turn it on in Settings.", 13f, color = Prism.Mute, lines = 3).apply {
                setPadding(0, dp(20), 0, dp(20))
            })
            media.isEmpty() -> add(emptyState(R.drawable.ic_videocam, "No videos found yet", "Start playing the video, then open this again", 2))
            else -> {
                media.forEachIndexed { i, m ->
                    if (!m.probed && probing.add(m.url)) scope.launch { m.probe() }
                    add(context.mediaRow(c, m), t = if (i > 0) 10 else 0)
                }
                add(View(context), h = 8)
                add(text("Only download videos you have the right to save. DRM-protected streams (Netflix, Disney+ etc.) can't be downloaded.", 10f, color = Prism.Mute, lines = 3))
            }
        }
    }
    body.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = scope.cancel()
    })
    return body
}

private fun Context.mediaRow(c: BrowserController, m: MediaItem): View = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    glass(20f, 3)
    setPadding(dp(14), dp(14), dp(14), dp(14))
    val info = buildList {
        add(
            when (m.kind) {
                MediaKind.HLS -> "Stream (HLS)"
                MediaKind.AUDIO -> m.ext.uppercase() + " audio"
                else -> m.ext.uppercase() + " video"
            },
        )
        if (m.size > 0) add(Formatter.formatShortFileSize(context, m.size))
        if (m.duration > 0) add("%d:%02d".format((m.duration / 60).toInt(), (m.duration % 60).toInt()))
        if (m.variants.isNotEmpty()) add("${m.variants.size} qualities")
        if (!m.probed) add("checking…")
    }.joinToString(" · ")
    add(LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        add(FrameLayout(context).apply {
            background = GradientDrawable().apply { cornerRadius = dp(13).toFloat(); setColor(Prism.Ink) }
            addView(icon(if (m.kind == MediaKind.AUDIO) R.drawable.ic_audio else R.drawable.ic_play, Color.WHITE, 22), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }, w = 40, h = 40)
        add(View(context), w = 12)
        add(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            add(context.text(m.title, 14f, 600, lines = 2))
            add(context.text(info, 11f, color = Prism.Mute))
        }, weight = 1f)
        if (m.variants.isEmpty()) add(FrameLayout(context).apply {
            background = GradientDrawable().apply { cornerRadius = dp(PILL).toFloat(); setColor(Prism.Accent) }
            isClickable = true
            setOnClickListener {
                c.downloadMedia(m)
                c.sheet = Sheet.NONE
            }
            setPadding(dp(14), dp(9), dp(14), dp(9))
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                add(context.icon(R.drawable.ic_download, Color.WHITE, 16))
                add(View(context), w = 4)
                add(context.text("Save", 13f, 600, Color.WHITE))
            })
        })
    })
    if (m.variants.isNotEmpty()) {
        add(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                m.variants.forEachIndexed { i, v ->
                    add(
                        context.pillChip("↓ " + v.label, if (i == 0) Prism.Accent else Color.WHITE.a(.7f), if (i == 0) Color.WHITE else Prism.Ink) {
                            c.downloadMedia(m, v.url, v.label)
                            c.sheet = Sheet.NONE
                        },
                        l = if (i > 0) 8 else 0,
                    )
                }
            })
        }, t = 10)
    }
}

// ------------------------------------------------------------------ shield

private fun Context.shieldBody(c: BrowserController): LinearLayout = liveBody {
    val tab = c.active
    val host = tab?.host?.removePrefix("www.")
    add(sheetTitle("Prism Shield", c))
    add(LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        glass(22f, 3)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        add(FrameLayout(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (Store.adblockEnabled) Prism.Accent else Prism.Mute) }
            addView(context.icon(R.drawable.ic_shield, Color.WHITE, 30), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }, w = 54, h = 54)
        add(View(context), w = 14)
        add(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            add(context.text("${AdBlocker.blockedToday}", 26f, 700))
            add(context.text("ads, trackers & pop-ups blocked today", 12f, color = Prism.Mute, lines = 2))
        }, weight = 1f)
        if (tab != null && !tab.isStart) add(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            add(context.text("${tab.blocked}", 20f, 700, Prism.Accent))
            add(context.text("this page", 11f, color = Prism.Mute))
        })
    }, w = MATCH)
    add(View(context), h = 12)
    if (host != null && tab != null && !tab.isStart) {
        val allowed = AdBlocker.isSiteAllowlisted(host)
        add(context.toggleRow("Shield on $host", if (allowed) "Turned off for this site" else "Blocking ads on this site", !allowed) {
            Store.setAllowlisted(host, !it)
            AdBlocker.reload()
            tab.webView?.reload()
        })
    }
    add(context.toggleRow("Block ads & trackers", "${"%,d".format(AdBlocker.ruleCount)} filter rules active", Store.adblockEnabled) {
        Store.setAdblock(it)
        tab?.webView?.reload()
    })
    add(context.toggleRow("Block pop-ups & redirects", "Stops pop-unders, click-jacking overlays and forced redirects", Store.popupBlocking) {
        Store.setPopups(it)
        c.tabs.forEach { t -> t.webView?.settings?.javaScriptCanOpenWindowsAutomatically = !it }
    })
    add(LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        add(context.sectionLabel("Filter lists"), weight = 1f)
        add(context.text(if (AdBlocker.updating) "Updating…" else "Update now", 12f, 600, Prism.Accent).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            isClickable = !AdBlocker.updating
            setOnClickListener { AdBlocker.update() }
        })
    }, t = 10)
    add(context.text(
        if (AdBlocker.lastUpdated > 0) "Updated ${ago(AdBlocker.lastUpdated)} · auto-updates weekly" else "Built-in lists · auto-updates weekly",
        11f, color = Prism.Mute,
    ), t = 2)
    AdBlocker.LISTS.forEach { l ->
        add(context.toggleRow(l.name, null, AdBlocker.isListOn(l.id)) { AdBlocker.setListOn(l.id, it) }, t = 4)
    }
}

private fun Context.toggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
    val sw = Switch(this).apply {
        isClickable = false
        isFocusable = false
        isChecked = checked
        thumbTintList = ColorStateList.valueOf(Color.WHITE)
        trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Prism.Accent, Color.WHITE.a(.6f)),
        )
    }
    return LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(8), dp(4), dp(8))
        isClickable = true
        setOnClickListener {
            val v = !sw.isChecked
            sw.isChecked = v
            onChange(v)
        }
        add(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            add(context.text(title, 14f, 500))
            sub?.let { add(context.text(it, 11f, color = Prism.Mute, lines = 2)) }
        }, weight = 1f)
        add(sw)
    }
}

// ------------------------------------------------------------------ history & bookmarks

private fun Context.historyBody(c: BrowserController): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    val head = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        observe {
            removeAllViews()
            val clear = if (Store.history.isNotEmpty()) context.textAction("Clear") { Store.clearHistory() } else null
            addView(context.sheetTitle("History", c, clear), MATCH, WRAP)
        }
    }
    add(head)
    val query = context.field("Search history", "", 12)
    add(query, b = 8)
    val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val rebuild = {
        list.removeAllViews()
        val q = query.text.toString().trim()
        val sites = if (q.isEmpty()) Store.history.toList() else Store.history.filter { it.title.contains(q, true) || it.url.contains(q, true) }
        list.addView(context.siteList(sites, { c.sheet = Sheet.NONE; c.load(it.url) }, { Store.history.remove(it) }, "Pages you visit show up here"))
    }
    list.observe(rebuild)
    query.doAfterTextChanged { rebuild() }
    add(list)
}

private fun Context.bookmarksBody(c: BrowserController): LinearLayout = liveBody {
    add(sheetTitle("Bookmarks", c))
    add(siteList(Store.bookmarks.toList(), { c.sheet = Sheet.NONE; c.load(it.url) }, { Store.removeBookmark(it) }, "Tap ☆ Bookmark in the menu to save a page"))
}

private fun Context.siteList(sites: List<Site>, onOpen: (Site) -> Unit, onDelete: ((Site) -> Unit)?, empty: String): View {
    if (sites.isEmpty()) return text(empty, 13f, color = Prism.Mute).apply { setPadding(dp(24), dp(24), dp(24), dp(24)) }
    return ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(420))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            sites.forEach { s -> add(context.siteRow(s, onOpen, onDelete)) }
        }, FrameLayout.LayoutParams(MATCH, WRAP))
    }
}

private fun Context.siteRow(s: Site, onOpen: (Site) -> Unit, onDelete: ((Site) -> Unit)?): View = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL
    setPadding(dp(8), dp(10), dp(8), dp(10))
    isClickable = true
    setOnClickListener { onOpen(s) }
    add(FrameLayout(context).apply {
        glass(11f, 2)
        addView(context.text(glyphFor(s.title, s.url), 14f, 700).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
    }, w = 34, h = 34)
    add(View(context), w = 12)
    add(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        add(context.text(s.title, 14f, 500))
        add(context.text(listOf(UrlUtil.host(s.url) ?: s.url, ago(s.time)).filter { it.isNotBlank() }.joinToString(" · "), 11f, color = Prism.Mute))
    }, weight = 1f)
    if (onDelete != null) add(context.press(36, 36, onClick = { onDelete(s) }, content = context.icon(R.drawable.ic_close, Prism.Mute, 18)))
}

// ------------------------------------------------------------------ add favourite

private fun Context.addFavoriteBody(c: BrowserController): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    val page = c.active?.takeIf { !it.isStart }
    val name = context.field("Name", page?.title?.take(24).orEmpty(), 12)
    val address = context.field("Address", page?.url.orEmpty(), 12)
    add(sheetTitle("Add to Favorites", c))
    add(name, b = 8)
    add(address, b = 8)
    add(View(context), h = 8)
    add(context.labelButton("Add", accent = true) {
        val raw = address.text.toString()
        if (raw.isNotBlank()) {
            val u = UrlUtil.toUrl(raw)
            val title = name.text.toString()
            Store.addFavorite(Site(title.ifBlank { UrlUtil.host(u) ?: u }, u, glyphFor(title, u)))
            c.sheet = Sheet.NONE
        }
    })
}

// ------------------------------------------------------------------ settings

private fun SeekBar.style() {
    progressTintList = ColorStateList.valueOf(Prism.Accent)
    progressBackgroundTintList = ColorStateList.valueOf(Prism.Ink.a(.1f))
    thumbTintList = ColorStateList.valueOf(Prism.Accent)
}

private fun Context.settingsBody(c: BrowserController): LinearLayout = liveBody {
    add(sheetTitle("Settings", c))
    add(sectionLabel("Search engine"), t = 8)
    add(FlowLayout(context, dp(8)).apply {
        SearchEngine.entries.forEach { e ->
            val sel = Store.searchEngine == e
            addView(context.pillChip(e.label, if (sel) Prism.Ink else Color.WHITE.a(.6f), if (sel) Color.WHITE else Prism.Ink) { Store.setEngine(e) })
        }
    }, w = MATCH)
    add(sectionLabel("Website view"), t = 18)
    add(FlowLayout(context, dp(8)).apply {
        listOf<Pair<String, Boolean?>>("Automatic" to null, "Mobile" to false, "Desktop" to true).forEach { (label, v) ->
            val sel = Store.desktopModePref == v
            addView(context.pillChip(label, if (sel) Prism.Ink else Color.WHITE.a(.6f), if (sel) Color.WHITE else Prism.Ink) {
                Store.setDesktopMode(v)
                c.tabs.forEach { t -> if (t.desktop != c.defaultDesktop()) c.toggleDesktop(t) }
            })
        }
    }, w = MATCH)
    add(sectionLabel("Page zoom"), t = 18)
    val zoomValue = context.text("${Store.zoom}%", 14f, 700, Prism.Accent)
    add(LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        add(context.text("Text & content size", 14f, 500), weight = 1f)
        add(zoomValue)
    }, t = 6)
    add(SeekBar(context).apply {
        max = 30
        progress = (Store.zoom - 50) / 5
        style()
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                zoomValue.text = "${50 + p * 5}%"
            }

            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = c.applyZoom(50 + sb.progress * 5)
        })
    }, w = MATCH)
    add(sectionLabel("Downloads"), t = 18)
    val threadsValue = context.text("${Store.downloadThreads}", 14f, 700, Prism.Accent)
    add(LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        add(context.text("Connections per file", 14f, 500), weight = 1f)
        add(threadsValue)
    }, t = 6)
    add(SeekBar(context).apply {
        max = 31
        progress = Store.downloadThreads - 1
        style()
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                threadsValue.text = "${p + 1}"
            }

            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Store.setThreads(sb.progress + 1)
        })
    }, w = MATCH)
    add(context.text("More connections = faster on servers that allow it. 8–16 is a good balance.", 11f, color = Prism.Mute, lines = 2), t = 2)
    add(context.toggleRow("Detect videos on pages", "Shows a download button when a page plays video", Store.videoDetection) { Store.setVideoDetect(it) }, t = 6)
    add(sectionLabel("Privacy"), t = 18)
    listOf<Pair<String, () -> Unit>>(
        "Clear history" to { Store.clearHistory() },
        "Clear cookies & site data" to {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        },
        "Clear cache" to {
            c.tabs.forEach { it.webView?.clearCache(true) }
            c.active?.webView?.clearCache(true) ?: WebView(context).clearCache(true)
        },
    ).forEach { (label, action) ->
        add(context.textAction(label) {
            action()
            c.snack = Snack("$label — done")
        }.apply { setPadding(dp(4), dp(12), dp(4), dp(12)) }, t = 4)
    }
    add(context.text("Prism 1.0 · ${"%,d".format(Store.blockedTotal())} things blocked so far", 11f, color = Prism.Mute), t = 18)
}
