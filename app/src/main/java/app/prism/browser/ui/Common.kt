package app.prism.browser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import app.prism.browser.R
import app.prism.browser.data.Site
import app.prism.browser.data.Store
import app.prism.browser.engine.BrowserController
import app.prism.browser.engine.ContextTarget
import app.prism.browser.engine.DownloadRequest
import app.prism.browser.engine.Sheet
import app.prism.browser.engine.Snack
import app.prism.browser.engine.SitePrompt
import app.prism.browser.engine.Tab
import app.prism.browser.engine.UrlUtil

fun Context.copyText(s: String) {
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", s))
}

fun Context.shareText(s: String, title: String? = null) {
    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s).apply {
        title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }, null))
}

fun ago(t: Long): String =
    if (t <= 0) "" else DateUtils.getRelativeTimeSpanString(t, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

fun glyphFor(title: String, url: String): String =
    (title.trim().firstOrNull { it.isLetterOrDigit() } ?: UrlUtil.host(url)?.removePrefix("www.")?.firstOrNull() ?: '•')
        .uppercaseChar().toString()

fun Context.field(hint: String, value: String = "", pad: Int = 12): EditText = EditText(this).apply {
    setText(value)
    setHint(hint)
    setHintTextColor(Prism.Mute.a(.9f))
    setTextColor(Prism.Ink)
    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
    typeface = sora(400)
    includeFontPadding = false
    isSingleLine = true
    highlightColor = Prism.Accent.a(.25f)
    background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.WHITE.a(.6f)) }
    setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
}

fun Context.tabCount(count: Int, color: Int = Prism.Ink): TextView =
    text(if (count > 99) ":D" else count.toString(), 12f, 700, color).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { setStroke(dp(2), color); cornerRadius = dp(7).toFloat() }
        setPadding(dp(6), dp(1), dp(6), dp(1))
    }

class ShieldDot(ctx: Context, onClick: () -> Unit) : FrameLayout(ctx) {
    private val dot = View(ctx)

    init {
        layoutParams = ViewGroup.LayoutParams(dp(28), dp(28))
        isClickable = true
        setOnClickListener { onClick() }
        addView(dot, LayoutParams(dp(8), dp(8), Gravity.CENTER))
        refresh()
    }

    fun refresh() {
        val color = if (Store.adblockEnabled) Prism.Accent else Prism.Mute
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(3), color.a(.25f))
        }
    }
}

/** History and bookmark matches under the address bar while typing. */
class Suggestions(ctx: Context, private val onPick: (String) -> Unit) : LinearLayout(ctx) {
    private var query = ""

    init {
        orientation = VERTICAL
        glass(24f, 18)
        setPadding(0, dp(6), 0, dp(6))
        observe { build() }
    }

    fun setQuery(q: String) {
        query = q
        build()
    }

    private fun build() {
        removeAllViews()
        val q = query.trim().lowercase()
        visibility = if (q.isEmpty()) GONE else VISIBLE
        if (q.isEmpty()) return
        addView(row(R.drawable.ic_search, "Search ${Store.searchEngine.label} for \"$query\"", null) { onPick(query) })
        (Store.bookmarks + Store.history).asSequence()
            .filter { it.url.lowercase().contains(q) || it.title.lowercase().contains(q) }
            .distinctBy { it.url }
            .take(6)
            .forEach { s ->
                addView(row(if (Store.isBookmarked(s.url)) R.drawable.ic_star else R.drawable.ic_history, s.title, s.url) { onPick(s.url) })
            }
    }

    private fun row(iconRes: Int, title: String, sub: String?, onClick: () -> Unit): View = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(10), dp(16), dp(10))
        isClickable = true
        setOnClickListener { onClick() }
        add(context.icon(iconRes, Prism.Mute, 18))
        add(View(context), w = 12)
        add(LinearLayout(context).apply {
            orientation = VERTICAL
            add(context.text(title, 14f, 500))
            sub?.let { add(context.text(it, 11f, color = Prism.Mute)) }
        }, weight = 1f)
    }
}

/** Find-in-page bar: field, match counter, previous / next / close. */
class FindBar(ctx: Context, private val c: BrowserController) : FrameLayout(ctx) {
    private val count = ctx.text("", 12f, color = Prism.Mute)
    private val edit = ctx.addressEdit("Find on page")

    init {
        setPadding(dp(12), dp(12), dp(12), dp(12))
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            glass(22f, 18)
            setPadding(dp(16), dp(6), dp(6), dp(6))
        }
        bar.add(ctx.icon(R.drawable.ic_search, Prism.Mute, 18), r = 10)
        edit.doAfterTextChanged { c.find(it.toString()) }
        edit.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO) c.findNext(true)
            true
        }
        bar.add(edit, weight = 1f)
        bar.add(count, l = 6)
        bar.add(ctx.press(44, 36, 14f, onClick = { c.findNext(false) }, content = ctx.icon(R.drawable.ic_chevron_up)))
        bar.add(ctx.press(44, 36, 14f, onClick = { c.findNext(true) }, content = ctx.icon(R.drawable.ic_chevron_down)))
        bar.add(ctx.press(44, 36, 14f, onClick = { c.closeFind() }, content = ctx.icon(R.drawable.ic_close)))
        addView(bar, LayoutParams(MATCH, WRAP))
        observe {
            val t = c.active
            count.text = if (edit.text.isNotEmpty() && t != null) "${t.findIndex}/${t.findMatches}" else ""
        }
        edit.focusAndShowKeyboard()
    }
}

/** Bottom toast with an optional accent action; hides itself after a few seconds. */
class SnackHost(ctx: Context, private val c: BrowserController) : FrameLayout(ctx) {
    private val holder = ClampFrame(ctx, ctx.dp(460))
    private var shown: Snack? = null
    private val dismiss = Runnable { if (c.snack === shown) c.snack = null }

    init {
        visibility = GONE
        addView(holder, LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        observe { render() }
    }

    private fun render() {
        val s = c.snack
        if (s === shown) return
        removeCallbacks(dismiss)
        shown = s
        if (s == null) {
            visibility = GONE
            return
        }
        val large = c.largeScreen
        holder.layoutParams = LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            setMargins(dp(16), 0, dp(16), dp(if (large) 24 else 150) + ScreenInsets.bottom)
        }
        holder.removeAllViews()
        holder.glass(PILL, 10, dark = true)
        holder.background = LayerDrawable(arrayOf(context.glassDrawable(4096f, dark = true), ColorDrawable(Prism.Ink.a(.55f))))
        holder.setPadding(dp(18), dp(6), dp(6), dp(6))
        holder.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            add(context.text(s.message, 13f, 500, Color.WHITE, lines = 2).apply { setPadding(0, dp(8), 0, dp(8)) })
            if (s.action != null) {
                add(View(context), w = 8)
                add(context.labelPill(s.action) {
                    s.onAction?.invoke()
                    c.snack = null
                })
            } else add(View(context), w = 12)
        })
        visibility = VISIBLE
        holder.alpha = 0f
        holder.translationY = dp(20).toFloat()
        holder.animate().alpha(1f).translationY(0f).setDuration(200).start()
        postDelayed(dismiss, if (s.action != null) 5000L else 2600L)
    }
}

private fun Context.labelPill(label: String, onClick: () -> Unit): FrameLayout = FrameLayout(this).apply {
    background = GradientDrawable().apply { cornerRadius = dp(PILL).toFloat(); setColor(Prism.Accent) }
    isClickable = true
    setOnClickListener { onClick() }
    setPadding(dp(14), dp(8), dp(14), dp(8))
    addView(text(label, 13f, 600, Color.WHITE), FrameLayout.LayoutParams(WRAP, WRAP))
}

/** Centered glass card over a dimmed scrim; tapping outside calls [onDismiss]. */
fun Context.centerCard(onDismiss: () -> Unit, build: LinearLayout.() -> Unit): FrameLayout {
    val root = FrameLayout(this)
    root.addView(View(this).apply {
        setBackgroundColor(Prism.Ink.a(.28f))
        setOnClickListener { onDismiss() }
    }, FrameLayout.LayoutParams(MATCH, MATCH))
    val card = ClampFrame(this, dp(420)).apply {
        glass(30f, 18, strength = 1.2f)
        isClickable = true
    }
    card.addView(LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(22), dp(22), dp(22))
        build()
    }, FrameLayout.LayoutParams(MATCH, WRAP))
    root.addView(card, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER).apply {
        setMargins(dp(24), dp(24), dp(24), dp(24) + ScreenInsets.ime)
    })
    return root
}

private fun Context.downloadCard(c: BrowserController, req: DownloadRequest): View = centerCard({ c.pendingDownload = null }) {
    add(LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        add(FrameLayout(context).apply {
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Prism.Accent.a(.14f)) }
            addView(context.icon(R.drawable.ic_download, Prism.Accent, 26), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }, w = 48, h = 48)
        add(View(context), w = 14)
        add(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            add(context.text("Download file?", 17f, 700))
            add(context.text(
                listOfNotNull(
                    req.length.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) },
                    UrlUtil.host(req.url),
                ).joinToString(" · "), 12f, color = Prism.Mute,
            ))
        }, weight = 1f)
    })
    add(View(context), h = 16)
    add(context.field("File name", req.fileName, 14).apply {
        doAfterTextChanged { req.fileName = it.toString() }
    }, weight = 0f)
    add(View(context), h = 8)
    add(context.text("Saved to Downloads/Prism · ${Store.downloadThreads} parallel connections", 11f, color = Prism.Mute))
    add(View(context), h = 18)
    add(LinearLayout(context).apply {
        add(context.labelButton("Cancel") { c.pendingDownload = null }, weight = 1f)
        add(context.labelButton("Download", accent = true) { c.startDownload(req) }, weight = 1f, l = 10)
    })
}

private fun Context.sitePromptCard(c: BrowserController, p: SitePrompt): View {
    val done = { allow: Boolean ->
        c.sitePrompt = null
        p.onResult(allow)
    }
    return centerCard({ done(false) }) {
        if (p.what == "SSL") {
            add(context.icon(R.drawable.ic_warning, Prism.Accent, 34))
            add(View(context), h = 10)
            add(context.text("Connection not private", 18f, 700))
            add(View(context), h = 6)
            add(context.text("${p.host} has an invalid security certificate. Someone could be impersonating the site to steal your information.", 13f, color = Prism.Mute, lines = 5))
            add(View(context), h = 18)
            add(LinearLayout(context).apply {
                add(context.labelButton("Visit anyway") { done(true) }, weight = 1f)
                add(context.labelButton("Go back", accent = true) { done(false) }, weight = 1f, l = 10)
            })
        } else {
            add(context.text("Allow ${p.host} to use your ${p.what}?", 17f, 700, lines = 3))
            add(View(context), h = 6)
            add(context.text("You can change this later by restarting the browser.", 12f, color = Prism.Mute, lines = 2))
            add(View(context), h = 18)
            add(LinearLayout(context).apply {
                add(context.labelButton("Don't allow") { done(false) }, weight = 1f)
                add(context.labelButton("Allow", accent = true) { done(true) }, weight = 1f, l = 10)
            })
        }
    }
}

private fun Context.contextCard(c: BrowserController, t: ContextTarget): View {
    val close = { c.contextTarget = null }
    return centerCard(close) {
        add(context.text(t.title ?: t.linkUrl ?: t.imageUrl ?: "", 12f, color = Prism.Mute, lines = 2))
        add(View(context), h = 10)
        fun item(iconRes: Int, label: String, action: () -> Unit) {
            add(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(12), dp(6), dp(12))
                isClickable = true
                setOnClickListener { action(); close() }
                add(context.icon(iconRes, Prism.Ink, 20))
                add(View(context), w = 14)
                add(context.text(label, 15f))
            })
        }
        t.linkUrl?.let { link ->
            item(R.drawable.ic_open_external, "Open in new tab") { c.newTab(link, parent = c.active) }
            item(R.drawable.ic_tab, "Open in background") { c.newTab(link, background = true, parent = c.active) }
            item(R.drawable.ic_grid, "Open in other app") { c.openInOtherApp(link) }
            item(R.drawable.ic_copy, "Copy link") { context.copyText(link) }
            item(R.drawable.ic_share, "Share link") { context.shareText(link) }
            item(R.drawable.ic_download, "Download link") { c.pendingDownload = DownloadRequest(link, null, null, null, -1, c.active?.url) }
        }
        t.imageUrl?.let { img ->
            item(R.drawable.ic_image, "Open image in new tab") { c.newTab(img, parent = c.active) }
            item(R.drawable.ic_download, "Save image") { c.pendingDownload = DownloadRequest(img, null, null, null, -1, c.active?.url) }
            item(R.drawable.ic_copy, "Copy image address") { context.copyText(img) }
        }
    }
}

private class Slot<T : Any>(ctx: Context) : FrameLayout(ctx) {
    private var key: T? = null

    fun show(target: T?, build: (T) -> View) {
        if (target === key) return
        key = target
        removeAllViews()
        visibility = if (target == null) GONE else VISIBLE
        if (target != null) addView(build(target), LayoutParams(MATCH, MATCH))
    }
}

/** Hosts the download confirmation, site permission prompt and long-press menu. */
class DialogLayer(ctx: Context, private val c: BrowserController) : FrameLayout(ctx) {
    private val download = Slot<DownloadRequest>(ctx)
    private val site = Slot<SitePrompt>(ctx)
    private val menu = Slot<ContextTarget>(ctx)

    init {
        visibility = GONE
        addView(download, LayoutParams(MATCH, MATCH))
        addView(site, LayoutParams(MATCH, MATCH))
        addView(menu, LayoutParams(MATCH, MATCH))
        observe {
            download.show(c.pendingDownload) { context.downloadCard(c, it) }
            site.show(c.sitePrompt) { context.sitePromptCard(c, it) }
            menu.show(c.contextTarget) { context.contextCard(c, it) }
            visibility = if (c.pendingDownload == null && c.sitePrompt == null && c.contextTarget == null) GONE else VISIBLE
        }
    }
}

/** Favourite tile: glass rounded square with the glyph and a label underneath. */
fun Context.favTile(site: Site, tile: Int, radius: Int, labelSp: Float, onClick: () -> Unit, onLong: (() -> Unit)?): View =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val box = FrameLayout(context).apply {
            glass(radius.toFloat(), 12)
            isClickable = true
            setOnClickListener { onClick() }
            if (onLong != null) setOnLongClickListener { onLong(); true }
            addView(text(site.glyph.ifEmpty { glyphFor(site.title, site.url) }, 22f, 700), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }
        add(box, w = MATCH, h = tile)
        add(text(site.title, labelSp), t = 6)
    }

fun Context.favoritesGrid(c: BrowserController, cols: Int, gap: Int, tile: Int, radius: Int, labelSp: Float = 11f): LinearLayout {
    val items = Store.favorites.toList() + Site("Add", "", "+")
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        items.chunked(cols).forEachIndexed { ri, row ->
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEachIndexed { ci, s ->
                val isAdd = ri * cols + ci == items.lastIndex
                line.add(
                    context.favTile(
                        s, tile, radius, labelSp,
                        onClick = { if (isAdd) c.sheet = Sheet.ADD_FAVORITE else c.load(s.url) },
                        onLong = if (isAdd) null else ({
                            Store.removeFavorite(s)
                            c.snack = Snack("Removed ${s.title}", "Undo") { Store.addFavorite(s) }
                        }),
                    ),
                    weight = 1f, l = if (ci > 0) gap else 0,
                )
            }
            repeat(cols - row.size) { line.add(View(context), weight = 1f, l = gap) }
            add(line, t = if (ri > 0) gap else 0)
        }
    }
}

val STARTER = listOf(
    Site("Today's featured article", "https://en.m.wikipedia.org/wiki/Main_Page", time = 0),
    Site("Astronomy Picture of the Day", "https://apod.nasa.gov/apod/", time = 0),
    Site("Top stories in tech", "https://news.ycombinator.com", time = 0),
)
val STARTER_TAGS = listOf("READ · Wikipedia", "SPACE · NASA", "TECH · Hacker News")

fun Context.card(onClick: () -> Unit, build: LinearLayout.() -> Unit): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    glass(24f, 14)
    setPadding(dp(16), dp(16), dp(16), dp(16))
    isClickable = true
    setOnClickListener { onClick() }
    build()
}

fun Context.recentCard(s: Site, onClick: () -> Unit): LinearLayout {
    val host = UrlUtil.host(s.url)?.removePrefix("www.") ?: ""
    return card(onClick) {
        add(context.text(listOf(host.uppercase(), ago(s.time)).filter { it.isNotBlank() }.joinToString(" · "), 11f, color = Prism.Mute))
        add(context.text(s.title, 15f, 700, lines = 2).apply { setPadding(0, dp(4), 0, dp(4)) })
        add(context.text(s.url, 11f, color = Prism.Mute))
    }
}

/** "For you": recent history, or the starter cards on a fresh install. */
fun Context.forYou(c: BrowserController, max: Int = 3): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    val recent = Store.history.take(max)
    if (recent.isEmpty()) {
        STARTER.forEachIndexed { i, s ->
            add(card({ c.load(s.url) }) {
                add(context.text(STARTER_TAGS[i], 11f, color = Prism.Mute))
                add(context.text(s.title, 15f, 700).apply { setPadding(0, dp(4), 0, dp(4)) })
                add(context.text(UrlUtil.host(s.url) ?: "", 11f, color = Prism.Mute))
            }, t = if (i > 0) 12 else 0)
        }
    } else recent.forEachIndexed { i, s ->
        add(context.recentCard(s) { c.load(s.url) }, t = if (i > 0) 12 else 0)
    }
}

fun Context.tabPreview(t: Tab, color: Int): View {
    val thumb = t.thumbnail
    return when {
        t.isStart -> LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Wallpaper()
            setPadding(dp(16), dp(22), dp(16), dp(22))
            add(text("Home", 20f, 700), b = 10)
            add(text("Wander the web, gently.", 12f, lines = 2))
        }
        thumb != null -> ImageView(this).apply {
            setImageBitmap(thumb)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.WHITE)
        }
        else -> LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color)
            setPadding(dp(16), dp(22), dp(16), dp(22))
            add(text(t.title, 20f, 700, lines = 3), b = 10)
            add(text(t.displayHost, 12f))
        }
    }
}

val CARD_COLORS = listOf(0xFFE9F1FF, 0xFFE6F7EC, 0xFFFFF1E6, 0xFFF3EAFF, 0xFFFFFAF2).map { it.toInt() }

/** Shared by the phone and tablet start pages: the bold hero heading. */
fun Context.hero(sizeSp: Float): TextView {
    val label = android.text.SpannableStringBuilder("Wander the\nweb, ")
    val start = label.length
    label.append("gently.")
    label.setSpan(android.text.style.ForegroundColorSpan(Prism.Accent), start, label.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return text(label, sizeSp, 700, Prism.Ink, lines = Int.MAX_VALUE, spacing = -1f / sizeSp).apply {
        setLineSpacing(0f, .9f)
    }
}
