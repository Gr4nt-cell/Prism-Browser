package app.prism.browser.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import app.prism.browser.MainActivity
import app.prism.browser.R
import app.prism.browser.adblock.AdBlocker
import app.prism.browser.data.Store
import app.prism.browser.engine.BrowserController
import app.prism.browser.engine.Sheet
import app.prism.browser.engine.Tab
import app.prism.browser.engine.UrlUtil
import kotlin.math.abs
import kotlin.math.min

class PhoneBrowserView(ctx: Context, private val c: BrowserController) : FrameLayout(ctx) {
    private val handler = Handler(Looper.getMainLooper())
    private val hideBar = Runnable { barVisible = false; render() }
    private var barVisible = true
    private var editing = false
    private var seenTouch = -1L
    private var seenTab = -1L
    private var seenAuto = false
    private var seenShow = false
    private var capsuleKey = ""
    private var trailKey = ""

    private val start = PhoneStartView(ctx, c)
    private val scrimStart = View(ctx).apply {
        setBackgroundColor(Prism.Paper.a(.35f)); visibility = GONE; setOnClickListener { setEditing(false) }
    }
    private val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val webHolder = FrameLayout(ctx)
    private val webSlot = WebSlot(ctx, c)
    private val scrimWeb = View(ctx).apply {
        setBackgroundColor(Prism.Paper.a(.75f)); visibility = GONE; setOnClickListener { setEditing(false) }
    }
    private val barHolder = FrameLayout(ctx)
    private val floatBar = FrameLayout(ctx)
    private val bar = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val suggestions = Suggestions(ctx) { go(it) }
    private val row = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
    private val pillHolder = FrameLayout(ctx)
    private val pill = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
    private val shield = ShieldDot(ctx) { c.sheet = Sheet.SHIELD }
    private val labelHolder = FrameLayout(ctx)
    private val labelRow = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
    private val lock = ctx.icon(R.drawable.ic_lock, Prism.Mute, 12)
    private val label = ctx.text("", 14f, 500)
    private val field = ctx.addressEdit("Search or type URL", 14f).apply { visibility = GONE }
    private val trail = FrameLayout(ctx)
    private val capsule = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
    private val progress = LineProgress(ctx)
    private val findHolder = FrameLayout(ctx).apply { visibility = GONE }
    private val findBar = FindBar(ctx, c)
    private val switcher = PhoneTabSwitcher(ctx, c)

    init {
        addView(start, FrameLayout.LayoutParams(MATCH, MATCH))
        addView(scrimStart, FrameLayout.LayoutParams(MATCH, MATCH))
        addView(column, FrameLayout.LayoutParams(MATCH, MATCH))
        addView(floatBar, FrameLayout.LayoutParams(MATCH, MATCH))
        addView(findHolder, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))
        addView(switcher, FrameLayout.LayoutParams(MATCH, MATCH))

        column.add(webHolder, w = MATCH, h = 0, weight = 1f)
        column.add(barHolder, w = MATCH)
        webHolder.addView(webSlot, FrameLayout.LayoutParams(MATCH, MATCH))
        webHolder.addView(scrimWeb, FrameLayout.LayoutParams(MATCH, MATCH))
        barHolder.addView(bar, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        bar.addView(suggestions, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) })
        bar.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))

        row.add(pillHolder, weight = 1f, h = 50)
        row.add(capsule, w = WRAP, h = 50, l = 10)
        pillHolder.addView(pill, FrameLayout.LayoutParams(MATCH, MATCH))
        pillHolder.addView(progress, FrameLayout.LayoutParams(MATCH, dp(3), Gravity.BOTTOM).apply {
            leftMargin = dp(26); rightMargin = dp(26); bottomMargin = dp(2)
        })
        pill.glass(PILL, 10)
        pill.setPadding(dp(6), 0, dp(8), 0)
        pill.isClickable = true
        pill.setOnClickListener { if (!editing) setEditing(true) }
        pill.add(shield, w = 28, h = 28)
        pill.add(labelHolder, weight = 1f, h = MATCH)
        pill.add(trail, w = 32, h = 32)
        labelHolder.addView(labelRow, FrameLayout.LayoutParams(MATCH, MATCH))
        labelHolder.addView(field, FrameLayout.LayoutParams(MATCH, MATCH))
        labelRow.add(lock, w = 12, h = 12, g = Gravity.CENTER_VERTICAL, r = 5)
        labelRow.add(label, w = WRAP, h = WRAP, g = Gravity.CENTER_VERTICAL)

        capsule.glass(PILL, 10)
        capsule.setPadding(dp(4), 0, dp(4), 0)

        bar.setPadding(dp(12), dp(8), dp(12), dp(8))
        suggestions.visibility = GONE
        field.doAfterTextChanged { suggestions.setQuery(it?.toString().orEmpty()); render() }
        field.imeOptions = EditorInfo.IME_ACTION_GO
        field.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE) {
                go(field.text.toString()); true
            } else false
        }

        findHolder.addView(findBar, FrameLayout.LayoutParams(MATCH, WRAP))
        switcher.visibility = GONE
        relayout()
        observe { render() }
    }

    fun relayout() {
        column.setPadding(0, ScreenInsets.top, 0, ScreenInsets.ime)
        barHolder.setPadding(0, 0, 0, if (ScreenInsets.ime > 0) 0 else ScreenInsets.bottom)
        floatBar.setPadding(0, 0, 0, if (ScreenInsets.ime > 0) ScreenInsets.ime else ScreenInsets.bottom + dp(6))
        findHolder.setPadding(dp(12), ScreenInsets.top + dp(12), dp(12), 0)
        start.relayout()
        switcher.relayout()
    }

    private fun setEditing(e: Boolean) {
        val tab = c.active ?: return
        editing = e
        if (e) {
            field.setText(if (tab.isStart) "" else tab.url)
            field.focusAndShowKeyboard()
        } else {
            field.clearFocus()
            field.hideKeyboard()
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
        val auto = !isStart && !editing && c.sheet == Sheet.NONE && !c.showTabs && !c.findVisible &&
            c.pendingDownload == null && c.contextTarget == null
        if (c.lastTouch != seenTouch || tab.id != seenTab || auto != seenAuto) {
            seenTouch = c.lastTouch; seenTab = tab.id; seenAuto = auto
            barVisible = true
            handler.removeCallbacks(hideBar)
            if (auto) handler.postDelayed(hideBar, 5_000)
        }

        start.visibility = if (isStart) VISIBLE else GONE
        column.visibility = if (isStart) GONE else VISIBLE
        if (!isStart) webSlot.show(tab)
        scrimStart.visibility = if (isStart && editing) VISIBLE else GONE
        scrimWeb.visibility = if (!isStart && editing) VISIBLE else GONE

        val wantParent: ViewGroup = if (isStart) floatBar else barHolder
        if (bar.parent !== wantParent) {
            (bar.parent as? ViewGroup)?.removeView(bar)
            wantParent.addView(bar, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        }
        bar.visibility = if (c.showTabs) GONE else if (isStart || barVisible) VISIBLE else GONE
        if (!editing) suggestions.visibility = GONE

        shield.refresh()
        labelRow.visibility = if (editing) GONE else VISIBLE
        field.visibility = if (editing) VISIBLE else GONE
        lock.visibility = if (!isStart && tab.secure) VISIBLE else GONE
        label.text = if (isStart) "Search or type URL" else UrlUtil.pretty(tab.url)
        label.setTextColor(if (isStart) Prism.Mute else Prism.Ink)
        label.setTypeface(label.typeface, if (isStart) android.graphics.Typeface.NORMAL else android.graphics.Typeface.BOLD)

        val trailNow = when {
            editing && field.text.isNotEmpty() -> "clear"
            isStart || editing -> "mic"
            tab.loading -> "stop"
            else -> "reload"
        }
        if (trailNow != trailKey) {
            trailKey = trailNow
            trail.removeAllViews()
            val button = when (trailNow) {
                "clear" -> context.press(32, 32, 16f, onClick = { field.setText("") }, content = context.icon(R.drawable.ic_close, Prism.Mute, 18))
                "mic" -> context.press(32, 32, 16f, onClick = { (context as? MainActivity)?.voiceSearch() }, content = context.icon(R.drawable.ic_mic, Prism.Ink, 19))
                "stop" -> context.press(32, 32, 16f, onClick = { c.reload() }, content = context.icon(R.drawable.ic_close, Prism.Ink, 18))
                else -> context.press(32, 32, 16f, onClick = { c.reload() }, content = context.icon(R.drawable.ic_refresh, Prism.Ink, 18))
            }
            trail.addView(button, FrameLayout.LayoutParams(MATCH, MATCH))
        }

        val videos = !isStart && tab.media.isNotEmpty() && Store.videoDetection
        val capKey = "$isStart|$videos|${c.tabs.size}"
        if (capKey != capsuleKey) {
            capsuleKey = capKey
            capsule.removeAllViews()
            if (videos) capsule.add(videoButton(), w = 40, h = 40)
            capsule.add(context.press(40, 40, 20f, onClick = { c.goHome() }, content = context.icon(R.drawable.ic_home, Prism.Ink, 22)), w = 40, h = 40)
            capsule.add(context.press(40, 40, 20f, onClick = { c.active?.let { c.captureThumbnail(it) }; c.showTabs = true },
                onLong = { c.newTab() }, content = context.tabCount(c.tabs.size)), w = 40, h = 40)
            capsule.add(context.press(40, 40, 20f, onClick = { c.sheet = Sheet.MENU }, content = context.icon(R.drawable.ic_more_horiz, Prism.Ink, 22)), w = 40, h = 40)
        }
        progress.visibility = if (!isStart && tab.progress < 100) VISIBLE else GONE
        progress.set(tab.progress / 100f, Prism.Accent)

        findHolder.visibility = if (c.findVisible && !c.showTabs) VISIBLE else GONE

        val showing = c.showTabs
        if (showing != seenShow) {
            seenShow = showing
            if (showing) switcher.show() else switcher.hide()
        }
    }

    private fun videoButton(): View = context.press(40, 40, 20f, onClick = { c.sheet = Sheet.VIDEOS }, content = FrameLayout(context).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Prism.Accent) }
        setPadding(dp(4), dp(4), dp(4), dp(4))
        addView(context.icon(R.drawable.ic_play, Color.WHITE, 16), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
    })
}

/** Start page on phones: hero, favourites and recents over the wallpaper. */
class PhoneStartView(ctx: Context, private val c: BrowserController) : ScrollView(ctx) {
    private val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    init {
        isVerticalScrollBarEnabled = false
        background = Wallpaper()
        addView(content, FrameLayout.LayoutParams(MATCH, WRAP))

        content.add(ctx.hero(34f), t = 0)
        val subtitle = ctx.text("", 13f, color = Prism.Mute).apply { setPadding(0, dp(8), 0, 0) }
        content.add(subtitle)
        subtitle.observe { subtitle.text = "Private by default · ${"%,d".format(AdBlocker.blockedToday)} trackers blocked today" }

        content.add(ctx.sectionLabel("Favorites").apply { setPadding(0, dp(22), 0, dp(12)) })
        val favs = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        content.add(favs)
        favs.observe { favs.removeAllViews(); favs.add(ctx.favoritesGrid(c, 4, 14, 64, 22)) }

        content.add(ctx.sectionLabel("For you").apply { setPadding(0, dp(22), 0, dp(12)) })
        val forYou = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        content.add(forYou)
        forYou.observe { forYou.removeAllViews(); forYou.add(ctx.forYou(c)) }
        relayout()
    }

    fun relayout() {
        content.setPadding(dp(22), ScreenInsets.top + dp(28), dp(22), ScreenInsets.bottom + dp(190))
    }
}

/** iOS-style recents switcher with overlapping, scaled cards. */
class PhoneTabSwitcher(ctx: Context, private val c: BrowserController) : FrameLayout(ctx) {
    private val handler = Handler(Looper.getMainLooper())
    private val screenW = ctx.resources.displayMetrics.widthPixels
    private val screenH = ctx.resources.displayMetrics.heightPixels
    private val cardW = (screenW * .64f).toInt()
    private var cardH = ctx.dp(480)
    private var stripH = cardH + ctx.dp(40)
    private val sidePad = (screenW * .18f).toInt()
    private val overlap = (screenW * .26f).toInt()
    private val strip = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; overScrollMode = OVER_SCROLL_NEVER }
    private val cards = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
    private val count = ctx.text("", 15f, 700, Color.WHITE).apply { gravity = Gravity.CENTER }
    private val controls: LinearLayout by lazy { buildControls() }
    private val snap = Runnable { snapNearest() }

    init {
        setBackgroundColor(Prism.Ink.a(.72f))
        isClickable = true
        strip.addView(cards, FrameLayout.LayoutParams(WRAP, WRAP))
        strip.setOnScrollChangeListener { _, _, _, _, _ -> updateCards() }
        strip.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) handler.postDelayed(snap, 120)
            false
        }
        addView(strip, FrameLayout.LayoutParams(MATCH, stripH, Gravity.TOP))
        addView(controls, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        visibility = GONE
        relayout()
        observe { if (visibility == VISIBLE) rebuild() }
    }

    /** Places the card strip in the middle of the space between the status bar and the controls. */
    fun relayout() {
        val controlsH = context.dp(72)
        val topReserve = ScreenInsets.top + context.dp(12)
        val bottomReserve = ScreenInsets.bottom + context.dp(28) + controlsH
        val avail = screenH - topReserve - bottomReserve
        cardH = min(avail - context.dp(40), context.dp(520)).coerceAtLeast(context.dp(240))
        stripH = cardH + context.dp(40)
        (strip.layoutParams as FrameLayout.LayoutParams).apply {
            height = stripH
            topMargin = topReserve + ((avail - stripH) / 2).coerceAtLeast(0)
        }
        strip.requestLayout()
        (controls.layoutParams as FrameLayout.LayoutParams).bottomMargin = ScreenInsets.bottom + context.dp(28)
        if (visibility == VISIBLE) rebuild()
    }

    private var hiding = false

    fun show() {
        hiding = false
        rebuild()
        animate().cancel()
        visibility = VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(180).start()
    }

    /** Fades out once. Repeated calls while fading are ignored, so frequent UI updates can't stall the fade. */
    fun hide() {
        if (hiding || visibility != VISIBLE) return
        hiding = true
        animate().cancel()
        animate().alpha(0f).setDuration(160).withEndAction {
            visibility = GONE
            alpha = 1f
            hiding = false
        }.start()
    }

    private fun buildControls(): LinearLayout = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(24), 0, dp(24), 0)
        add(FrameLayout(context).apply {
            glass(PILL, 6, dark = true)
            isClickable = true
            setOnClickListener { c.newTab(); c.showTabs = false }
            addView(context.icon(R.drawable.ic_add, Color.WHITE, 26), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }, w = 48, h = 48)
        add(View(context), weight = 1f)
        add(context.labelButton("Done", dark = true) { c.showTabs = false }, w = 140, h = 44)
        add(View(context), weight = 1f)
        add(count, w = 48, h = 48)
    }

    private fun rebuild() {
        cards.removeAllViews()
        c.tabs.forEachIndexed { i, t ->
            cards.addView(card(i, t), LinearLayout.LayoutParams(cardW, stripH).apply {
                if (i < c.tabs.lastIndex) marginEnd = -overlap
            })
        }
        cards.setPadding(sidePad, 0, sidePad, 0)
        count.text = "${c.tabs.size}"
        strip.post {
            val target = cards.getChildAt(c.activeIndex)?.let { it.left + it.width / 2 - strip.width / 2 } ?: 0
            strip.scrollTo(target.coerceAtLeast(0), 0)
            updateCards()
        }
    }

    private fun card(i: Int, t: Tab): View {
        val color = CARD_COLORS[i % CARD_COLORS.size]
        return FrameLayout(context).apply {
            val body = FrameLayout(context).apply {
                background = GradientDrawable().apply { cornerRadius = dp(30).toFloat(); setColor(color) }
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
                elevation = dp(14).toFloat()
                addView(context.tabPreview(t, color), FrameLayout.LayoutParams(MATCH, MATCH))
                addView(FrameLayout(context).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Prism.Ink.a(.35f)) }
                    isClickable = true
                    setOnClickListener { c.close(t) }
                    addView(context.icon(R.drawable.ic_close, Color.WHITE, 16), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
                }, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(10), dp(10), 0) })
                var downY = 0f
                var dy = 0f
                var moved = false
                setOnTouchListener { v, e ->
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { downY = e.rawY; moved = false }
                        MotionEvent.ACTION_MOVE -> {
                            dy = (e.rawY - downY).coerceAtMost(0f)
                            if (abs(dy) > dp(8)) moved = true
                            v.translationY = dy
                        }
                        MotionEvent.ACTION_UP -> {
                            if (dy < -dp(140)) c.close(t)
                            else { v.animate().translationY(0f).setDuration(120).start(); if (!moved) v.performClick() }
                            dy = 0f
                        }
                        MotionEvent.ACTION_CANCEL -> { v.animate().translationY(0f).setDuration(120).start(); dy = 0f }
                    }
                    true
                }
                setOnClickListener { c.select(i); c.showTabs = false }
            }
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), 0, 0, 0)
                add(context.favicon(t, 14), w = 14, h = 14)
                add(View(context), w = 8)
                add(context.text(if (t.isStart) "Home" else t.title, 13f, 600, Color.WHITE, lines = 1), weight = 1f)
            }, FrameLayout.LayoutParams((cardW * .56f).toInt(), dp(30)))
            addView(body, FrameLayout.LayoutParams(MATCH, MATCH).apply { topMargin = dp(40) })
        }
    }

    private fun updateCards() {
        val mid = strip.scrollX + strip.width / 2f
        for (i in 0 until cards.childCount) {
            val v = cards.getChildAt(i)
            val center = v.left + v.width / 2f
            val d = ((center - mid) / v.width).coerceIn(-1.5f, 1.5f)
            v.translationX = d * -0.08f * v.width
            val s = 1f - abs(d) * .06f
            v.scaleX = s
            v.scaleY = s
        }
    }

    private fun snapNearest() {
        val mid = strip.scrollX + strip.width / 2
        var best: View? = null
        for (i in 0 until cards.childCount) {
            val v = cards.getChildAt(i)
            if (best == null || abs(v.left + v.width / 2 - mid) < abs(best.left + best.width / 2 - mid)) best = v
        }
        best?.let { strip.smoothScrollTo((it.left + it.width / 2 - strip.width / 2).coerceAtLeast(0), 0) }
    }
}
