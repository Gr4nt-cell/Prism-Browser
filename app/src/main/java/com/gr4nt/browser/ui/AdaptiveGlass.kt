package com.gr4nt.browser.ui

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.gr4nt.browser.browser.Browser
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private val mainHandler = Handler(Looper.getMainLooper())

/**
 * Reads the average colour of a thin strip of the screen with PixelCopy (GPU, async, cheap).
 * Used to find out what colour is sitting right behind/next to the floating glass bars.
 */
suspend fun sampleWindowColor(window: Window, rect: android.graphics.Rect): Color? {
    if (rect.width() <= 0 || rect.height() <= 0 || rect.top < 0) return null
    return suspendCancellableCoroutine { cont ->
        val bmp = Bitmap.createBitmap(24, 3, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(window, rect, bmp, { result ->
                val color = if (result == PixelCopy.SUCCESS) averageColor(bmp) else null
                bmp.recycle()
                if (cont.isActive) cont.resume(color)
            }, mainHandler)
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }
}

private fun averageColor(b: Bitmap): Color {
    val px = IntArray(b.width * b.height)
    b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
    var r = 0L; var g = 0L; var bl = 0L
    for (p in px) { r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF }
    val n = px.size
    return Color((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
}

/** Where to sample relative to a glass bar: just above it (bottom bars) or just below it (top bars). */
enum class SampleSide { ABOVE, BELOW }

/**
 * Continuously samples the page colour next to a glass bar. Samples quickly while the page is
 * scrolling (so the tint follows the content) and slowly when static (videos, carousels).
 * Returns null when sampling is paused, which means "use the normal theme glass".
 */
@Composable
fun rememberBackdropColor(bounds: () -> Rect?, side: SampleSide, enabled: Boolean): State<Color?> {
    val state = remember { mutableStateOf<Color?>(null) }
    val activity = LocalContext.current as? Activity
    val density = LocalDensity.current
    val gap = with(density) { 9.dp.toPx() }
    val strip = with(density) { 8.dp.toPx() }
    val inset = with(density) { 20.dp.toPx() }
    val en by rememberUpdatedState(enabled)
    val boundsNow by rememberUpdatedState(bounds)
    LaunchedEffect(activity) {
        val window = activity?.window ?: return@LaunchedEffect
        while (isActive) {
            val b = boundsNow()
            if (en && b != null && b.width > 0f) {
                val top = if (side == SampleSide.ABOVE) b.top - gap - strip else b.bottom + gap
                val rect = android.graphics.Rect(
                    (b.left + inset).toInt(), top.toInt(), (b.right - inset).toInt(), (top + strip).toInt()
                )
                state.value = sampleWindowColor(window, rect) ?: state.value
            } else state.value = null
            val scrolling = System.currentTimeMillis() - Browser.lastScrollAt < 400
            delay(if (scrolling) 60 else 320)
        }
    }
    return state
}

/**
 * Provides a glass palette tinted with [backdrop]. Over dark content the glass switches to its
 * dark appearance (white glyphs); over light content it uses the light appearance — with
 * hysteresis so it doesn't flicker on mid-tones. Colour changes cross-fade smoothly.
 */
@Composable
fun AdaptiveGlass(backdrop: Color?, content: @Composable () -> Unit) {
    val base = Ios.c
    var darkGlass by remember { mutableStateOf(base.dark) }
    val target = backdrop ?: base.wall
    val lum = target.luminance()
    if (backdrop == null) darkGlass = base.dark
    else if (darkGlass && lum > 0.52f) darkGlass = false
    else if (!darkGlass && lum < 0.36f) darkGlass = true

    // Over white / near-white pages translucent white glass disappears, so the bar switches to a
    // soft neutral-grey frost with a firmer outline and shadow (0 = normal, 1 = full boost).
    // The app's own background is plain white too, so this also applies with no page behind.
    val onWhite by animateFloatAsState(
        if (!darkGlass && lum > 0.80f) ((lum - 0.80f) / 0.12f).coerceIn(0f, 1f) else 0f,
        tween(320), label = "onWhite"
    )
    val st = com.gr4nt.browser.data.Store.settings
    // user's transparency: 0 = almost solid, 1 = almost clear (default 0.65 ≈ the original look)
    val baseAlpha = (0.92f - st.barTransparency * 0.80f).coerceIn(0.10f, 0.95f)
    val tint by animateColorAsState(target, tween(320), label = "glassTint")
    val neutral by animateColorAsState(if (darkGlass) Color(0xFF18312D) else Color(0xFFF0F8F3), tween(320), label = "glassNeutral")
    val palette = (if (darkGlass) DarkIos else LightIos).let { p ->
        // keep the tint subtle: a hint of the colour behind, not a solid paint
        val mixed = lerp(neutral, tint, if (backdrop == null || !st.barAdaptiveTint) 0f else 0.42f)
        val frosted = lerp(mixed, Color(0xFFE6F1EB), onWhite)       // cool neutral frost on white
        p.copy(
            glass = frosted.copy(alpha = (baseAlpha + (if (darkGlass) 0.02f else 0f) + onWhite * 0.28f * (1f - baseAlpha)).coerceAtMost(0.93f)),
            liquid = st.barLiquidGlass,
            glassStrong = frosted.copy(alpha = 0.84f + onWhite * 0.06f),
            glassBorder = if (darkGlass) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.72f - onWhite * 0.18f),
            glassShadow = 0.92f + onWhite * 0.42f,
            // keep the theme's own tint colour for links/buttons
            tint = if (darkGlass) DarkIos.tint else LightIos.tint
        )
    }
    CompositionLocalProvider(LocalIos provides palette) { content() }
}
