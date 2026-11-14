package app.prism.browser.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.prism.browser.engine.BrowserController
import app.prism.browser.engine.Tab
import kotlin.math.max

/** Root of the app: picks the phone or tablet layout by width and hosts sheets, snackbars and dialogs. */
class BrowserRoot(ctx: Context, private val c: BrowserController) : FrameLayout(ctx) {
    private var layoutLarge: Boolean? = null
    private var content: View? = null
    private val sheets = SheetHost(ctx, c)
    private val snack = SnackHost(ctx, c)
    private val dialogs = DialogLayer(ctx, c)

    init {
        setBackgroundColor(Prism.Paper)
        addView(sheets, LayoutParams(MATCH, MATCH))
        addView(snack, LayoutParams(MATCH, MATCH))
        addView(dialogs, LayoutParams(MATCH, MATCH))
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            ScreenInsets.top = bars.top
            ScreenInsets.bottom = bars.bottom
            ScreenInsets.ime = max(0, ime.bottom - bars.bottom)
            relayoutContent()
            insets
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val large = w >= dp(600)
        if (large == layoutLarge) return
        layoutLarge = large
        content?.let { removeView(it) }
        val view: View = if (large) TabletBrowserView(context, c, wide = w >= dp(900)) else PhoneBrowserView(context, c)
        content = view
        addView(view, 0, LayoutParams(MATCH, MATCH))
        c.largeScreen = large
        relayoutContent()
    }

    private fun relayoutContent() {
        (content as? PhoneBrowserView)?.relayout()
        (content as? TabletBrowserView)?.relayout()
    }
}

/** Hosts the active tab's WebView. The same WebView instance moves here when tabs switch. */
class WebSlot(ctx: Context, private val c: BrowserController) : FrameLayout(ctx) {
    fun show(tab: Tab) {
        val wv = c.webViewFor(tab)
        if (wv.parent === this) return
        (wv.parent as? ViewGroup)?.removeView(wv)
        removeAllViews()
        addView(wv, LayoutParams(MATCH, MATCH))
    }
}
