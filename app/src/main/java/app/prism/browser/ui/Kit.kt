package app.prism.browser.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import app.prism.browser.R
import app.prism.browser.core.Changes
import app.prism.browser.engine.Tab
import kotlin.math.max
import kotlin.math.min

object Prism {
    val Ink = 0xFF17242A.toInt()
    val Mute = 0xFF64747A.toInt()
    val Accent = 0xFFFF6A3D.toInt()
    val Paper = 0xFFE6EEE9.toInt()
    val Peach = 0xFFF3D9DF.toInt()
    val Sky = 0xFFB8DAE8.toInt()
    val Lilac = 0xFFD8CBE9.toInt()
    val Night = 0xFF000000.toInt()
    val Green = 0xFF2FB36B.toInt()
}

const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
/** Corner radius (dp) meaning "pill": clamped to half the height. */
const val PILL = 999f
const val SHADOW = 0x301A394C

object ScreenInsets {
    var top = 0
    var bottom = 0
    var ime = 0
}

fun Int.a(f: Float): Int = (this and 0xFFFFFF) or ((f * 255f + .5f).toInt() shl 24)

fun Context.dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density + .5f).toInt()

fun View.dp(v: Number): Int = context.dp(v)

fun Context.len(v: Int): Int = if (v < 0) v else dp(v)

private val faces = HashMap<Int, Typeface>()

fun Context.sora(weight: Int = 400): Typeface = faces.getOrPut(weight) {
    val base = ResourcesCompat.getFont(applicationContext, R.font.sora) ?: return Typeface.DEFAULT
    if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false)
    else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
}

fun Context.text(s: CharSequence, size: Float = 14f, weight: Int = 400, color: Int = Prism.Ink, lines: Int = 1, spacing: Float = 0f): TextView =
    TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        typeface = sora(weight)
        setTextColor(color)
        letterSpacing = spacing
        includeFontPadding = false
        ellipsize = TextUtils.TruncateAt.END
        maxLines = lines
    }

fun Context.sectionLabel(s: String): TextView = text(s.uppercase(), 12f, 600, Prism.Mute, spacing = 2f / 12f)

fun Context.icon(res: Int, tint: Int = Prism.Ink, size: Int = 22): ImageView = ImageView(this).apply {
    setImageResource(res)
    imageTintList = ColorStateList.valueOf(tint)
    scaleType = ImageView.ScaleType.FIT_CENTER
    layoutParams = ViewGroup.LayoutParams(dp(size), dp(size))
}

fun Context.favicon(t: Tab, size: Int = 16): View =
    if (t.favicon != null) ImageView(this).apply {
        setImageBitmap(t.favicon)
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, dp(4).toFloat())
            }
        }
        layoutParams = ViewGroup.LayoutParams(dp(size), dp(size))
    } else icon(R.drawable.ic_public, Prism.Mute, size)

fun Context.glassDrawable(radiusPx: Float, dark: Boolean = false, strength: Float = 1f, radiiPx: FloatArray? = null): Drawable {
    fun corners(d: GradientDrawable): GradientDrawable = d.apply {
        if (radiiPx != null) cornerRadii = radiiPx else cornerRadius = radiusPx
    }
    // Soft glass: frosted translucent body, a warm base glow, a top sheen,
    // a bright outer rim and a fainter inner bevel. No live blur (see glass()).
    val body = corners(GradientDrawable().apply { setColor(if (dark) 0xC4000000.toInt() else 0xA8FFFFFF.toInt()) })
    val sheen = corners(GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.WHITE.a((if (dark) .13f else .76f) * strength), Color.WHITE.a(0f)),
    ))
    val glow = corners(GradientDrawable(
        GradientDrawable.Orientation.BOTTOM_TOP,
        intArrayOf(Prism.Peach.a((if (dark) .03f else .16f) * strength), Color.TRANSPARENT),
    ))
    val rim = corners(GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        setStroke(dp(1), Color.WHITE.a((if (dark) .24f else .94f) * strength.coerceAtMost(1.2f)))
    })
    val bevel = InsetDrawable(corners(GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        setStroke(dp(1), Color.WHITE.a(if (dark) .10f else .42f))
    }), dp(1))
    return LayerDrawable(arrayOf(body, sheen, glow, bevel, rim))
}

/** Liquid-glass look without live blur: translucent gradient, 1dp rim, inner highlight, soft shadow. */
fun View.glass(radius: Float = PILL, elev: Int = 14, dark: Boolean = false, strength: Float = 1f, radii: FloatArray? = null) {
    val pill = radius >= PILL
    val px = if (pill) 4096f else context.dp(radius).toFloat()
    val radiiPx = radii?.map { context.dp(it).toFloat() }?.toFloatArray()
    background = context.glassDrawable(px, dark, strength, radiiPx)
    elevation = context.dp(elev).toFloat()
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(v: View, o: Outline) {
            o.setRoundRect(0, 0, v.width, v.height, if (pill) min(v.width, v.height) / 2f else px)
        }
    }
    if (Build.VERSION.SDK_INT >= 28) {
        outlineAmbientShadowColor = SHADOW
        outlineSpotShadowColor = SHADOW
    }
}

/** Soft three-blob wallpaper from the design. */
class Wallpaper : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        paint.shader = null
        paint.color = Prism.Paper
        canvas.drawRect(b, paint)
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val r = max(w, h)
        blob(canvas, b, Prism.Peach.a(.80f), b.left + w * .04f, b.top + h * .05f, r * .65f)
        blob(canvas, b, Prism.Sky.a(.82f), b.left + w * .94f, b.top + h * .32f, r * .68f)
        blob(canvas, b, Prism.Lilac.a(.70f), b.left + w * .28f, b.top + h * .90f, r * .65f)
    }

    private fun blob(canvas: Canvas, b: Rect, color: Int, cx: Float, cy: Float, radius: Float) {
        paint.shader = RadialGradient(cx, cy, radius, intArrayOf(color, color.a(0f)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(b, paint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

fun LinearLayout.add(
    v: View, w: Int? = null, h: Int? = null, weight: Float = 0f, g: Int = -1,
    l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0,
): View {
    val defaultW = if (orientation == LinearLayout.VERTICAL) MATCH else WRAP
    val pw = w?.let { context.len(it) } ?: if (weight > 0f) 0 else v.layoutParams?.width ?: defaultW
    val ph = h?.let { context.len(it) } ?: v.layoutParams?.height ?: WRAP
    addView(v, LinearLayout.LayoutParams(pw, ph, weight).apply {
        if (g >= 0) gravity = g
        setMargins(context.dp(l), context.dp(t), context.dp(r), context.dp(b))
    })
    return v
}

fun FrameLayout.add(
    v: View, w: Int? = null, h: Int? = null, g: Int = Gravity.TOP or Gravity.START,
    l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0,
): View {
    val pw = w?.let { context.len(it) } ?: v.layoutParams?.width ?: WRAP
    val ph = h?.let { context.len(it) } ?: v.layoutParams?.height ?: WRAP
    addView(v, FrameLayout.LayoutParams(pw, ph, g).apply {
        setMargins(context.dp(l), context.dp(t), context.dp(r), context.dp(b))
    })
    return v
}

/** Runs [refresh] now and every time the observable state changes while this view is attached. */
fun View.observe(refresh: () -> Unit) {
    val listener: () -> Unit = refresh
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            Changes.add(listener)
            listener()
        }

        override fun onViewDetachedFromWindow(v: View) {
            Changes.remove(listener)
        }
    })
}

/** Icon-style button with the design's press feedback (white .6 fill and scale .9). */
fun Context.press(
    w: Int, h: Int, radius: Float = 14f, enabled: Boolean = true,
    onClick: () -> Unit, onLong: (() -> Unit)? = null, content: View,
): FrameLayout {
    val bg = GradientDrawable().apply { cornerRadius = dp(radius).toFloat(); setColor(Color.TRANSPARENT) }
    return FrameLayout(this).apply {
        layoutParams = ViewGroup.LayoutParams(dp(w), dp(h))
        background = bg
        isEnabled = enabled
        setOnClickListener { onClick() }
        if (onLong != null) setOnLongClickListener { onLong(); true }
        setOnTouchListener { v, e ->
            if (v.isEnabled) when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    bg.setColor(Color.WHITE.a(.6f))
                    v.animate().scaleX(.9f).scaleY(.9f).setDuration(100).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    bg.setColor(Color.TRANSPARENT)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }
            }
            false
        }
        addView(content, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
    }
}

/** Thin progress line: accent fill on a translucent track, rounded ends. */
class LineProgress(ctx: Context) : LinearLayout(ctx) {
    private val fill = View(ctx)
    private val rest = View(ctx)

    init {
        orientation = HORIZONTAL
        weightSum = 1f
        background = GradientDrawable().apply { cornerRadius = ctx.dp(PILL).toFloat(); setColor(Prism.Ink.a(.08f)) }
        addView(fill, LayoutParams(0, MATCH, 0f))
        addView(rest, LayoutParams(0, MATCH, 1f))
    }

    fun set(p: Float, color: Int = Prism.Accent) {
        val f = p.coerceIn(0f, 1f)
        fill.background = GradientDrawable().apply { cornerRadius = context.dp(PILL).toFloat(); setColor(color) }
        fill.layoutParams = LayoutParams(0, MATCH, f)
        rest.layoutParams = LayoutParams(0, MATCH, 1f - f)
    }
}

fun Context.addressEdit(hint: String, size: Float = 14f): EditText = EditText(this).apply {
    background = null
    setPadding(0, 0, 0, 0)
    setHint(hint)
    setHintTextColor(Prism.Mute.a(.9f))
    setTextColor(Prism.Ink)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    typeface = sora(400)
    includeFontPadding = false
    isSingleLine = true
    inputType = android.text.InputType.TYPE_CLASS_TEXT or
        android.text.InputType.TYPE_TEXT_VARIATION_URI or
        android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
    highlightColor = Prism.Accent.a(.25f)
    if (Build.VERSION.SDK_INT >= 29) {
        textCursorDrawable = GradientDrawable().apply { setColor(Prism.Accent); setSize(dp(2), dp(20)) }
    }
}

fun EditText.focusAndShowKeyboard() {
    post {
        requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }
}

fun View.hideKeyboard() {
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(windowToken, 0)
}

/** Glass pill button with label; [accent] fills it with the brand colour. */
fun Context.labelButton(label: String, accent: Boolean = false, dark: Boolean = false, onClick: () -> Unit): FrameLayout =
    FrameLayout(this).apply {
        if (accent) background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Prism.Accent) }
        else glass(20f, 6, dark)
        isClickable = true
        setOnClickListener { onClick() }
        setPadding(dp(22), dp(12), dp(22), dp(12))
        addView(text(label, 14f, 600, if (accent || dark) Color.WHITE else Prism.Ink), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
    }

/** Flowing row of children that wraps to the next line like a web flexbox. */
class FlowLayout(ctx: Context, private val gapPx: Int) : ViewGroup(ctx) {
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val maxW = MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            measureChild(c, MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (x > 0 && x + c.measuredWidth > maxW) {
                x = 0
                y += rowH + gapPx
                rowH = 0
            }
            x += c.measuredWidth + gapPx
            rowH = max(rowH, c.measuredHeight)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), y + rowH + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = (r - l) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            if (x > 0 && x + c.measuredWidth > maxW) {
                x = 0
                y += rowH + gapPx
                rowH = 0
            }
            c.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + c.measuredWidth, paddingTop + y + c.measuredHeight)
            x += c.measuredWidth + gapPx
            rowH = max(rowH, c.measuredHeight)
        }
    }
}

/** Caps its width (and height) so a child can be centred on wide screens. */
class ClampFrame(ctx: Context, var maxWidthPx: Int, var maxHeightPx: Int = Int.MAX_VALUE) : FrameLayout(ctx) {
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = min(MeasureSpec.getSize(widthSpec), maxWidthPx)
        val h = min(MeasureSpec.getSize(heightSpec), maxHeightPx)
        val wMode = if (MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY) MeasureSpec.EXACTLY else MeasureSpec.AT_MOST
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, wMode), MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST))
    }
}
