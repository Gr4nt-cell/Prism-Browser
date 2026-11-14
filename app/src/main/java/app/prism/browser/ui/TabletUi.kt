package app.prism.browser.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import androidx.core.widget.doAfterTextChanged
import app.prism.browser.MainActivity
import app.prism.browser.R
import app.prism.browser.adblock.AdBlocker
import app.prism.browser.data.Store
import app.prism.browser.download.DStatus
import app.prism.browser.download.Downloader
import app.prism.browser.engine.BrowserController
import app.prism.browser.engine.Sheet
import app.prism.browser.engine.Tab
import app.prism.browser.engine.UrlUtil
import kotlin.math.min

private const val TOOLBAR_DP = 56
private const val STRIP_DP = 36
private const val SIDEBAR_DP = 280

/** Tablet and desktop layout: Safari-style toolbar, tab strip, optional sidebar and tab overview. */
class TabletBrowserView(ctx: Context, private val c: BrowserController, wide: Boolean) : FrameLayout(ctx) {
    private var sidebarOn = wide
    private var editing = false
    private val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    private val sidebar = TabletSidebar(ctx, c)
    private val area = FrameLayout(ctx)
    private val start = TabletStart(ctx, c)
    private val webSlot = WebSlot(ctx, c)
    private val chrome = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val toolbar = TabletToolbar(
        ctx, c,
        onSidebar = { sidebarOn = !sidebarOn; render() },
        onEdit = { setEditing(it) },
        onSubmit = { go(it) },
    )
    private val strip = TabletTabStrip(ctx, c)
    private val rule = View(ctx)
    private val scrim = View(ctx).apply { setOnClickListener { setEditing(false) } }
    private val suggestions = Suggestions(ctx) { go(it) }
    private val findBar = FindBar(ctx, c)
    private val overview = TabOverview(ctx, c)

    init {
        setBackgroundColor(Prism.Paper)
        row.add(area, weight = 1f, h = 0)
        area.addView(start, FrameLayout.LayoutParams(MATCH, MATCH))
        area.addView(webSlot, FrameLayout.LayoutParams(MATCH, MATCH))
        row.add(sidebar, w = SIDEBAR_DP, h = MATCH, l = 10, t = 6, r = 4, b = 10)
        addView(row, FrameLayout.LayoutParams(MATCH, MATCH))

        chrome.add(toolbar, w = MATCH, h = TOOLBAR_DP)
        chrome.add(strip, w = MATCH, h = STRIP_DP)
        chrome.add(rule, w = MATCH, h = 1)
        addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))
        addView(chrome, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))
        addView(suggestions, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        addView(findBar, FrameLayout.LayoutParams(dp(460), WRAP, Gravity.TOP or Gravity.END))
        addView(overview, FrameLayout.LayoutParams(MATCH, MATCH))

        suggestions.visibility = GONE
        scrim.visibility = GONE
        overview.visibility = GONE
        toolbar.field.doAfterTextChanged { suggestions.setQuery(it?.toString().orEmpty()) }
        observe { render() }
    }

    fun relayout() {
        start.relayout()
        render()
    }

    private fun setEditing(e: Boolean) {
        val tab = c.active ?: return
        editing = e
        if (e) {
            toolbar.field.setText(if (tab.isStart) "" else tab.url)
            toolbar.field.focusAndShowKeyboard()
        } else {
            toolbar.field.clearFocus()
            toolbar.field.hideKeyboard()
        }
        render()
    }

    private fun go(q: String) {
        setEditing(false)
        if (q.isNotBlank()) c.load(q)
    }

    private fun render() {
        val tab = c.active ?: return
        val isStart = tab.isStart
        val showStrip = c.tabs.size > 1
        val chromeTop = ScreenInsets.top
        chrome.setPadding(0, chromeTop, 0, 0)
        chrome.background = if (isStart) null else ColorDrawable(0xF2F6F4F0.toInt())
        row.setPadding(0, chromeTop + dp(TOOLBAR_DP) + (if (showStrip) dp(STRIP_DP) else 0), 0, 0)

        strip.visibility = if (showStrip) VISIBLE else GONE
        rule.visibility = if (isStart) GONE else VISIBLE
        rule.setBackgroundColor(Prism.Ink.a(.08f))
        sidebar.visibility = if (sidebarOn) VISIBLE else GONE
        start.visibility = if (isStart) VISIBLE else GONE
        webSlot.visibility = if (isStart) GONE else VISIBLE
        if (!isStart) webSlot.show(tab)

        toolbar.render(tab, editing, sidebarOn)
        strip.refresh()

        scrim.visibility = if (editing) VISIBLE else GONE
        (scrim.layoutParams as FrameLayout.LayoutParams).topMargin = chromeTop + dp(100)

        suggestions.visibility = if (editing) VISIBLE else GONE
        (suggestions.layoutParams as FrameLayout.LayoutParams).apply {
            width = toolbar.fieldWidth()
            topMargin = chromeTop + dp(TOOLBAR_DP - 4)
        }

        findBar.visibility = if (c.findVisible && !c.showTabs) VISIBLE else GONE
        (findBar.layoutParams as FrameLayout.LayoutParams).topMargin = chromeTop + dp(TOOLBAR_DP + 12)

        overview.visibility = if (c.showTabs) VISIBLE else GONE
        if (c.showTabs) overview.refresh()
    }
}

/** Safari-style top toolbar: sidebar, back/forward on the left, smart address field centred, actions on the right. */
class TabletToolbar(
    ctx: Context,
    private val c: BrowserController,
    private val onSidebar: () -> Unit,
    private val onEdit: (Boolean) -> Unit,
    private val onSubmit: (String) -> Unit,
) : FrameLayout(ctx) {
    val field = ctx.addressEdit("Search or enter website name", 13f).apply {
        visibility = GONE
        imeOptions = EditorInfo.IME_ACTION_GO
        setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE) {
                onSubmit(text.toString()); true
            } else false
        }
    }
    private val left = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
    private val right = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
    private val center = FrameLayout(ctx)
    private val pill = LinearLayout(ctx).apply {
        gravity = Gravity.CENTER_VERTICAL
        glass(20f, 8)
        setPadding(dp(6), 0, dp(6), 0)
        isClickable = true
        setOnClickListener { onEdit(true) }
    }
    private val shield = ShieldDot(ctx) { c.sheet = Sheet.SHIELD }
    private val labelHolder = FrameLayout(ctx)
    private val labelRow = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
    private val lock = ctx.icon(R.drawable.ic_lock, Prism.Mute, 12)
    private val label = ctx.text("", 13f, 500)
    private val trail = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
    private val progress = LineProgress(ctx)
    private var leftKey = ""
    private var rightKey = ""
    private var trailKey = ""

    init {
        setPadding(dp(12), 0, dp(12), 0)
        addView(left, LayoutParams(WRAP, WRAP, Gravity.START or Gravity.CENTER_VERTICAL))
        addView(center, LayoutParams(fieldWidth(), dp(40), Gravity.CENTER))
        addView(right, LayoutParams(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL))

        center.addView(pill, FrameLayout.LayoutParams(MATCH, MATCH))
        center.addView(progress, FrameLayout.LayoutParams(MATCH, dp(3), Gravity.BOTTOM).apply {
            leftMargin = dp(20); rightMargin = dp(20)
        })
        pill.add(shield, w = 28, h = 28)
        pill.add(labelHolder, weight = 1f, h = MATCH)
        pill.add(trail, w = WRAP, h = WRAP)
        labelHolder.addView(labelRow, FrameLayout.LayoutParams(MATCH, MATCH))
        labelHolder.addView(field, FrameLayout.LayoutParams(MATCH, MATCH))
        labelRow.add(lock, w = 12, h = 12, g = Gravity.CENTER_VERTICAL, r = 5)
        labelRow.add(label, w = WRAP, h = WRAP, g = Gravity.CENTER_VERTICAL)
    }

    /** Width of the centred address field: 46% of the screen, between 280 and 680 dp. */
    fun fieldWidth(): Int {
        val screen = context.resources.displayMetrics.widthPixels
        return (screen * .46f).toInt().coerceIn(dp(280), dp(680))
    }

    fun render(tab: Tab, editing: Boolean, sidebarOn: Boolean) {
        val isStart = tab.isStart
        val canBack = !isStart || tab.webView?.canGoBack() == true
        val downloading = Downloader.items.any { it.status == DStatus.RUNNING || it.status == DStatus.QUEUED }
        val videos = if (!isStart && Store.videoDetection) tab.media.size else 0

        val lk = "$canBack|${tab.canGoForward}|$sidebarOn"
        if (lk != leftKey) {
            leftKey = lk
            left.removeAllViews()
            left.add(group(
                btn(R.drawable.ic_sidebar, if (sidebarOn) Prism.Accent else Prism.Ink, 20, onClick = onSidebar),
            ))
            left.add(View(context), w = 8)
            left.add(group(
                btn(R.drawable.ic_back, Prism.Ink, 17, enabled = canBack) { c.back() },
                btn(R.drawable.ic_forward, Prism.Ink, 17, enabled = tab.canGoForward) { c.forward() },
            ))
        }

        val rk = "$isStart|$videos|$downloading|${tab.isStart}|${c.tabs.size}"
        if (rk != rightKey) {
            rightKey = rk
            right.removeAllViews()
            val groupList = mutableListOf<View>()
            if (videos > 0) groupList += videoBtn(videos)
            right.add(group(
                *(groupList.toTypedArray()),
                btn(R.drawable.ic_download, if (downloading) Prism.Accent else Prism.Ink, 20) { c.sheet = Sheet.DOWNLOADS },
                btn(R.drawable.ic_share, if (isStart) Prism.Mute else Prism.Ink, 19, enabled = !isStart) {
                    if (!isStart) context.shareText(tab.url, tab.title)
                },
                btn(R.drawable.ic_add, Prism.Ink, 22) { c.newTab() },
                btn(R.drawable.ic_grid, Prism.Ink, 19) { c.active?.let { c.captureThumbnail(it) }; c.showTabs = true },
                btn(R.drawable.ic_more_horiz, Prism.Ink, 22) { c.sheet = Sheet.MENU },
            ))
        }

        val tk = "$isStart|$editing|${if (!isStart) Store.isBookmarked(tab.url) else false}|${tab.loading}"
        if (tk != trailKey) {
            trailKey = tk
            trail.removeAllViews()
            if (isStart || editing) {
                trail.add(small(R.drawable.ic_mic, Prism.Mute, 17) { (context as? MainActivity)?.voiceSearch() }, w = 30, h = 30)
            } else {
                val bm = Store.isBookmarked(tab.url)
                trail.add(small(if (bm) R.drawable.ic_star else R.drawable.ic_star_border, if (bm) Prism.Accent else Prism.Mute, 17) {
                    Store.toggleBookmark(tab.title, tab.url)
                }, w = 30, h = 30)
                trail.add(small(if (tab.loading) R.drawable.ic_close else R.drawable.ic_refresh, Prism.Ink, 17) { c.reload() }, w = 30, h = 30)
            }
        }

        shield.refresh()
        labelRow.visibility = if (editing) GONE else VISIBLE
        field.visibility = if (editing) VISIBLE else GONE
        lock.visibility = if (!isStart && tab.secure) VISIBLE else GONE
        label.text = if (isStart) "Search or enter website name" else UrlUtil.pretty(tab.url)
        label.setTextColor(if (isStart) Prism.Mute else Prism.Ink)
        label.setTypeface(label.typeface, if (isStart) android.graphics.Typeface.NORMAL else android.graphics.Typeface.BOLD)

        progress.visibility = if (!isStart && tab.progress < 100) VISIBLE else GONE
        progress.set(tab.progress / 100f, Prism.Accent)
        center.layoutParams = FrameLayout.LayoutParams(fieldWidth(), dp(40), Gravity.CENTER)
    }

    private fun group(vararg buttons: View): LinearLayout = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        glass(PILL, 8)
        setPadding(dp(4), 0, dp(4), 0)
        layoutParams = LinearLayout.LayoutParams(WRAP, dp(40)).apply { leftMargin = 0 }
        buttons.forEach { add(it, w = 40, h = 34) }
    }

    private fun btn(icon: Int, tint: Int, size: Int = 19, enabled: Boolean = true, onClick: () -> Unit): View =
        context.press(40, 34, 17f, enabled = enabled, onClick = onClick,
            content = context.icon(icon, if (enabled) tint else Prism.Mute.a(.35f), size))

    private fun small(icon: Int, tint: Int, size: Int = 17, onClick: () -> Unit): View =
        context.press(30, 30, 15f, onClick = onClick, content = context.icon(icon, tint, size))

    private fun videoBtn(count: Int): View = context.press(40, 34, 17f, onClick = { c.sheet = Sheet.VIDEOS }, content = FrameLayout(context).apply {
        addView(context.icon(R.drawable.ic_videocam, Prism.Accent, 20), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        addView(context.text("$count", 8f, 700, Color.WHITE).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Prism.Accent) }
        }, FrameLayout.LayoutParams(dp(14), dp(14), Gravity.TOP or Gravity.END))
    })
}

/** Tab strip under the toolbar: one segment per tab, the active one lifted white. */
class TabletTabStrip(ctx: Context, private val c: BrowserController) : LinearLayout(ctx) {
    private val bar = LinearLayout(ctx).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        glass(14f, 4, strength = .8f)
        setPadding(dp(3), dp(3), dp(3), dp(3))
    }
    private var key = ""

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(3), dp(12), dp(3))
        add(bar, weight = 1f, h = MATCH)
        add(context.press(30, 30, 15f, onClick = { c.newTab() }, content = context.icon(R.drawable.ic_add, Prism.Mute, 18)), w = 30, h = 30, l = 6)
    }

    fun refresh() {
        val k = c.tabs.joinToString("|") { "${it.id}:${it.title}:${it.loading}:${it.isStart}:${it.favicon != null}" } + "#${c.activeIndex}"
        if (k == key) return
        key = k
        bar.removeAllViews()
        val count = c.tabs.size
        c.tabs.forEachIndexed { i, t ->
            val active = i == c.activeIndex
            val cell = FrameLayout(context).apply {
                isClickable = true
                setOnClickListener { c.select(i) }
                background = if (active) GradientDrawable().apply { cornerRadius = dp(11).toFloat(); setColor(Color.WHITE) } else null
            }
            if (active || count < 7) {
                cell.addView(context.press(20, 20, 10f, onClick = { c.close(t) }, content = context.icon(R.drawable.ic_close, Prism.Mute, 13)),
                    FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(4) })
            }
            val line = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(22), 0, dp(22), 0)
            }
            when {
                t.loading -> line.add(ProgressBar(context).apply {
                    isIndeterminate = true
                    indeterminateTintList = ColorStateList.valueOf(Prism.Accent)
                }, w = 12, h = 12)
                t.isStart -> line.add(context.icon(R.drawable.ic_home, Prism.Mute, 13))
                else -> line.add(context.favicon(t, 13))
            }
            line.add(View(context), w = 6)
            line.add(context.text(if (t.isStart) "Start Page" else t.title, 12f, if (active) 600 else 400,
                if (active) Prism.Ink else Prism.Mute), h = WRAP)
            cell.addView(line, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
            bar.add(cell, weight = 1f, h = MATCH)
            if (i < count - 1 && i != c.activeIndex && i + 1 != c.activeIndex) {
                bar.add(View(context).apply { setBackgroundColor(Prism.Ink.a(.1f)) }, w = 1, h = 16, g = Gravity.CENTER_VERTICAL)
            }
        }
    }
}

/** Left sidebar: open tabs, bookmarks and recent history, with quick buttons at the bottom. */
class TabletSidebar(ctx: Context, private val c: BrowserController) : LinearLayout(ctx) {
    private val list = LinearLayout(ctx).apply { orientation = VERTICAL }
    private var key = ""

    init {
        orientation = VERTICAL
        glass(20f, 10)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        val scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false }
        scroll.addView(list, FrameLayout.LayoutParams(MATCH, WRAP))
        add(scroll, weight = 1f, h = 0)
        val bottom = LinearLayout(ctx).apply { gravity = Gravity.CENTER }
        bottom.add(button(R.drawable.ic_download, Prism.Ink) { c.sheet = Sheet.DOWNLOADS }, w = 40, h = 40, l = 6, r = 6)
        bottom.add(button(R.drawable.ic_bookmark, Prism.Ink) { c.sheet = Sheet.BOOKMARKS }, w = 40, h = 40, l = 6, r = 6)
        bottom.add(button(R.drawable.ic_history, Prism.Ink) { c.sheet = Sheet.HISTORY }, w = 40, h = 40, l = 6, r = 6)
        bottom.add(button(R.drawable.ic_shield, Prism.Accent) { c.sheet = Sheet.SHIELD }, w = 40, h = 40, l = 6, r = 6)
        add(bottom, w = MATCH, h = WRAP, t = 8)
        observe { refresh() }
    }

    private fun button(icon: Int, tint: Int, onClick: () -> Unit): View =
        context.press(40, 40, 20f, onClick = onClick, content = context.icon(icon, tint, 20))

    private fun refresh() {
        if (visibility != VISIBLE) return
        val k = c.tabs.joinToString("|") { "${it.id}:${it.title}" } + "#${c.activeIndex}" +
            "#${Store.bookmarks.size}#${Store.history.size}"
        if (k == key) return
        key = k
        list.removeAllViews()
        list.add(context.sectionLabel("Tabs · ${c.tabs.size}").apply { setPadding(dp(8), dp(6), 0, dp(6)) })
        c.tabs.forEachIndexed { i, t ->
            list.add(sideRow(
                if (t.isStart) context.icon(R.drawable.ic_home, Prism.Mute, 16) else context.favicon(t, 16),
                if (t.isStart) "Start Page" else t.title,
                i == c.activeIndex,
                { c.select(i) },
                { c.close(t) },
            ), w = MATCH, h = WRAP)
        }
        list.add(context.sectionLabel("Bookmarks").apply { setPadding(dp(8), dp(16), 0, dp(6)) })
        if (Store.bookmarks.isEmpty()) list.add(context.text("No bookmarks yet", 12f, color = Prism.Mute).apply { setPadding(dp(8), dp(8), dp(8), dp(8)) })
        Store.bookmarks.take(30).forEach { b ->
            list.add(sideRow(context.icon(R.drawable.ic_star, Prism.Accent, 16), b.title, false, { c.load(b.url) }, null), w = MATCH, h = WRAP)
        }
        list.add(context.sectionLabel("Recently visited").apply { setPadding(dp(8), dp(16), 0, dp(6)) })
        Store.history.take(15).forEach { h ->
            list.add(sideRow(context.icon(R.drawable.ic_history, Prism.Mute, 16), h.title, false, { c.load(h.url) }, null), w = MATCH, h = WRAP)
        }
    }

    private fun sideRow(icon: View, title: String, active: Boolean, onClick: () -> Unit, onClose: (() -> Unit)?): LinearLayout =
        LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (active) Color.WHITE.a(.75f) else Color.TRANSPARENT)
            }
            isClickable = true
            setOnClickListener { onClick() }
            add(icon)
            add(context.text(title, 13f, if (active) 600 else 400), weight = 1f, h = WRAP, l = 10)
            if (onClose != null) add(context.press(22, 22, 11f, onClick = onClose, content = context.icon(R.drawable.ic_close, Prism.Mute, 14)), w = 22, h = 22)
        }
}

/** Start page on wide screens: favourites, privacy report, downloads and recents on the wallpaper. */
class TabletStart(ctx: Context, private val c: BrowserController) : ScrollView(ctx) {
    private val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val clamp = ClampFrame(ctx, dp(940))

    init {
        isVerticalScrollBarEnabled = false
        background = Wallpaper()
        clamp.addView(column, FrameLayout.LayoutParams(MATCH, WRAP))
        addView(clamp, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER_HORIZONTAL))
        observe { rebuild() }
        relayout()
    }

    fun relayout() {
        column.setPadding(dp(40), dp(36), dp(40), ScreenInsets.bottom + dp(40))
    }

    private fun rebuild() {
        column.removeAllViews()
        val cols = ((min(context.resources.displayMetrics.widthPixels, dp(940)) - dp(80)) / dp(104)).coerceIn(4, 8)

        column.add(context.hero(46f))
        column.add(context.text("Private by default · ${"%,d".format(AdBlocker.blockedToday)} trackers blocked today", 14f, color = Prism.Mute)
            .apply { setPadding(0, dp(10), 0, 0) })

        column.add(context.sectionLabel("Favorites").apply { setPadding(0, dp(36), 0, dp(14)) })
        column.add(context.favoritesGrid(c, cols, 18, 76, 24))

        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row.add(privacyCard(), weight = 1f, h = WRAP)
        row.add(downloadsCard(), weight = 1f, h = WRAP, l = 18)
        column.add(row, w = MATCH, h = WRAP, t = 36)

        column.add(context.sectionLabel("For you").apply { setPadding(0, dp(36), 0, dp(14)) })
        val recent = Store.history.take(6)
        if (recent.isEmpty()) column.add(context.forYou(c))
        else recent.chunked(3).forEach { chunk ->
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEachIndexed { i, s ->
                line.add(context.recentCard(s) { c.load(s.url) }, weight = 1f, h = WRAP, l = if (i > 0) 14 else 0)
            }
            repeat(3 - chunk.size) { line.add(View(context), weight = 1f, h = WRAP) }
            column.add(line, w = MATCH, h = WRAP, t = 0, b = 14)
        }
    }

    private fun privacyCard(): LinearLayout = context.card({ c.sheet = Sheet.SHIELD }) {
        add(context.sectionLabel("Privacy report"), b = 12)
        val top = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        top.add(FrameLayout(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Prism.Accent) }
            addView(context.icon(R.drawable.ic_shield, Color.WHITE, 26), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }, w = 46, h = 46)
        top.add(View(context), w = 14)
        top.add(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            add(context.text("${"%,d".format(AdBlocker.blockedToday)}", 26f, 700), w = WRAP, h = WRAP)
            add(context.text("ads, trackers and pop-ups blocked today", 12f, color = Prism.Mute, lines = 2), w = WRAP, h = WRAP)
        }, w = WRAP, h = WRAP)
        add(top, w = MATCH, h = WRAP)
        add(context.text("${"%,d".format(AdBlocker.ruleCount)} filter rules · ${"%,d".format(Store.blockedTotal())} blocked all-time", 11f, color = Prism.Mute), t = 10)
    }

    private fun downloadsCard(): LinearLayout = context.card({ c.sheet = Sheet.DOWNLOADS }) {
        add(context.sectionLabel("Downloads"), b = 12)
        val recent = Downloader.items.take(3)
        if (recent.isEmpty()) {
            add(context.text("Nothing downloaded yet. Videos and files you save land here.", 13f, color = Prism.Mute, lines = 3))
        }
        recent.forEach { d ->
            add(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
                add(context.icon(R.drawable.ic_download, Prism.Accent, 16))
                add(context.text(d.fileName, 13f, 500), weight = 1f, h = WRAP, l = 8)
                add(context.text(
                    when (d.status) {
                        DStatus.DONE -> Formatter.formatShortFileSize(context, d.total)
                        DStatus.RUNNING -> "${(d.progress.coerceAtLeast(0f) * 100).toInt()}%"
                        else -> d.status.name.lowercase()
                    }, 11f, color = Prism.Mute), h = WRAP)
            }, w = MATCH, h = WRAP)
        }
    }
}

/** Tab overview (Safari's grid): a wallpaper sheet with a card per tab. */
class TabOverview(ctx: Context, private val c: BrowserController) : FrameLayout(ctx) {
    private val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val header = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
    private val title = ctx.text("", 22f, 700)
    private val grid = FlowLayout(ctx, ctx.dp(20))
    private val scroll = ScrollView(ctx).apply {
        isVerticalScrollBarEnabled = false
        addView(grid, FrameLayout.LayoutParams(MATCH, WRAP))
    }

    init {
        background = Wallpaper()
        isClickable = true
        header.add(title, weight = 1f, h = WRAP)
        header.add(LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            glass(PILL, 8)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            add(ctx.press(40, 34, 17f, onClick = { c.newTab(); c.showTabs = false },
                content = ctx.icon(R.drawable.ic_add, Prism.Ink, 22)), w = 40, h = 34)
            add(ctx.labelButton("Done") { c.showTabs = false }, w = WRAP, h = WRAP, l = 4)
        }, w = WRAP, h = WRAP)
        column.add(header, w = MATCH, h = WRAP, l = 20, r = 20, t = 10, b = 10)
        column.add(scroll, weight = 1f, h = 0, l = 20, r = 20)
        addView(column, FrameLayout.LayoutParams(MATCH, MATCH))
        observe { if (visibility == VISIBLE) refresh() }
    }

    fun refresh() {
        column.setPadding(0, ScreenInsets.top, 0, ScreenInsets.bottom)
        title.text = "${c.tabs.size} Tabs"
        grid.removeAllViews()
        c.tabs.forEachIndexed { i, t -> grid.addView(tile(i, t)) }
    }

    private fun tile(i: Int, t: Tab): View {
        val active = i == c.activeIndex
        val tileW = context.dp(230)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(tileW, WRAP)
            isClickable = true
            setOnClickListener { c.select(i); c.showTabs = false }

            val head = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(8)) }
            head.add(context.press(22, 22, 11f, onClick = { c.close(t) }, content = context.icon(R.drawable.ic_close, Prism.Mute, 15)), w = 22, h = 22)
            head.add(View(context), w = 6)
            if (!t.isStart) {
                head.add(context.favicon(t, 14))
                head.add(View(context), w = 6)
            }
            head.add(context.text(if (t.isStart) "Start Page" else t.title, 13f, 600), weight = 1f, h = WRAP)
            add(head, w = MATCH, h = WRAP)

            val card = FrameLayout(context).apply {
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) {
                        o.setRoundRect(0, 0, v.width, v.height, context.dp(16).toFloat())
                    }
                }
                elevation = dp(if (active) 18 else 10).toFloat()
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(Color.WHITE)
                    if (active) setStroke(dp(3), Prism.Accent)
                }
            }
            card.addView(context.tabPreview(t, CARD_COLORS[i % CARD_COLORS.size]), FrameLayout.LayoutParams(MATCH, MATCH))
            add(card, w = MATCH, h = (tileW / 1.35f).toInt())
        }
    }
}
