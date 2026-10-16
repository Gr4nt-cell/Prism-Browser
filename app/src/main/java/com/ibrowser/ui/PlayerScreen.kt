@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.ibrowser.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ibrowser.MainActivity
import com.ibrowser.player.Media
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Glass palette used on top of video: dark, very translucent glass. */
private val VideoGlass = DarkIos.copy(
    glass = Color(0x47202024),
    glassStrong = Color(0x8C1C1C1E),
    label = Color.White,
    secondary = Color(0xB3FFFFFF),
    tint = Color.White
)

private val shadowText = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 6f)

private fun fmt(ms: Long): String = formatDuration((ms / 1000).coerceAtLeast(0))

// ============================================================================ entry points

/** Full-screen player overlay (video or audio). */
@Composable
fun MediaPlayerOverlay() {
    AnimatedVisibility(
        Media.visible && Media.active,
        enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 300f, visibilityThreshold = IntOffset(1, 1))) { it } + fadeIn(tween(160)),
        exit = slideOutVertically(spring(dampingRatio = 1f, stiffness = 380f, visibilityThreshold = IntOffset(1, 1))) { it } + fadeOut(tween(200))
    ) {
        CompositionLocalProvider(LocalIos provides VideoGlass) {
            if (Media.hasVideo) VideoPlayer() else AudioPlayer()
        }
    }
}

/** Floating glass mini player shown above the toolbar while something plays in the background. */
@Composable
fun MiniPlayer(modifier: Modifier = Modifier) {
    AnimatedVisibility(
        Media.active && !Media.visible && !Media.inPip,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = 380f)) { it * 2 } + fadeIn(),
        exit = slideOutVertically(spring(dampingRatio = 1f, stiffness = 420f)) { it * 2 } + fadeOut()
    ) {
        val c = Ios.c
        var pressed by remember { mutableStateOf(false) }
        val glow by animateFloatAsState(if (pressed) 1f else 0f, Motion.smooth, label = "miniGlow")
        Box(
            Modifier
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .height(60.dp)
                .pressable(scaleTo = 0.97f, onPressedChange = { pressed = it }) { Media.visible = true }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { ch, dy -> if (dy < -12) { ch.consume(); Media.visible = true } }
                }
                .glass(RoundedCornerShape(30.dp), glow = glow)
        ) {
            Row(Modifier.fillMaxSize().padding(start = 9.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(Media.artwork, Media.title, size = 42, corner = 11, video = Media.hasVideo)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(Media.title, style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            Media.error != null -> Media.error!!
                            Media.buffering -> "Loading…"
                            Media.isLive -> "Live"
                            Media.duration > 0 -> "${fmt(Media.position)} / ${fmt(Media.duration)}"
                            else -> Media.subtitle
                        },
                        style = Ios.caption, color = c.secondary, maxLines = 1
                    )
                }
                InlineIconButton(if (Media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", size = 42.dp, iconSize = 26.dp) { Media.toggle() }
                InlineIconButton(Icons.Rounded.Close, "Close", size = 38.dp, iconSize = 20.dp, tint = c.secondary) { Media.stop() }
            }
            // progress hairline
            if (Media.duration > 0) {
                val p = (Media.position.toFloat() / Media.duration).coerceIn(0f, 1f)
                Box(
                    Modifier.align(Alignment.BottomStart).padding(horizontal = 26.dp).fillMaxWidth().height(2.dp)
                        .drawBehind {
                            drawRoundRect(c.label.copy(alpha = 0.15f), cornerRadius = CornerRadius(2f, 2f))
                            drawRoundRect(c.tint, size = Size(size.width * p, size.height), cornerRadius = CornerRadius(2f, 2f))
                        }
                )
            }
        }
    }
}

// ============================================================================ video

@Composable
private fun VideoPlayer() {
    val act = LocalContext.current as? MainActivity
    val view = LocalView.current
    var controls by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }          // bump to restart the auto-hide timer
    var fill by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf<Boolean?>(null) }
    var seekBubble by remember { mutableIntStateOf(0) }    // -1 left, +1 right
    var seekAmount by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf<Pair<ImageVector, Float>?>(null) }
    var scrubbing by remember { mutableStateOf(false) }

    // immersive while the video player is open
    DisposableEffect(Unit) {
        act?.setFullscreen(true)
        onDispose {
            act?.setFullscreen(false)
            act?.lockOrientation(null)
            act?.setBrightness(null)
        }
    }
    // auto-hide controls
    LaunchedEffect(controls, poke, Media.isPlaying, scrubbing) {
        if (controls && Media.isPlaying && !scrubbing) { delay(3200); controls = false }
    }
    LaunchedEffect(seekBubble, seekAmount) { if (seekBubble != 0) { delay(700); seekBubble = 0; seekAmount = 0 } }
    LaunchedEffect(hud) { if (hud != null) { delay(900); hud = null } }

    val ctx = LocalContext.current
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var brightness by remember { mutableFloatStateOf(act?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.6f) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // ---- video surface ----
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                    keepScreenOn = true
                    player = Media.obtain()
                }
            },
            update = {
                it.player = Media.obtain()
                it.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        if (Media.inPip) return@Box

        // ---- gesture layer: tap, double-tap seek, vertical volume/brightness ----
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(locked) {
                        detectTapGestures(
                            onTap = { controls = !controls; poke++ },
                            onDoubleTap = { o ->
                                if (locked) return@detectTapGestures
                                val dir = if (o.x < w / 2) -1 else 1
                                if (seekBubble == dir) seekAmount += 10 else { seekBubble = dir; seekAmount = 10 }
                                Media.seekBy(dir * 10_000L)
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                        )
                    }
                    .pointerInput(locked) {
                        if (locked) return@pointerInput
                        var right = true
                        detectVerticalDragGestures(
                            onDragStart = { o -> right = o.x > w / 2 }
                        ) { ch, dy ->
                            ch.consume()
                            val delta = -dy / (h * 0.6f)
                            if (right) {
                                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val acc = (hud?.second ?: (cur.toFloat() / max)) + delta
                                val level = acc.coerceIn(0f, 1f)
                                val target = (level * max).roundToInt()
                                if (target != cur) audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                hud = (if (level < 0.5f) Icons.Rounded.VolumeDown else Icons.Rounded.VolumeUp) to level
                            } else {
                                brightness = (brightness + delta).coerceIn(0.02f, 1f)
                                act?.setBrightness(brightness)
                                hud = Icons.Rounded.BrightnessMedium to brightness
                            }
                        }
                    }
            )
        }

        // ---- double-tap seek bubbles ----
        SeekBubble(visible = seekBubble == -1, text = "−${seekAmount}s", modifier = Modifier.align(Alignment.CenterStart).padding(start = 40.dp))
        SeekBubble(visible = seekBubble == 1, text = "+${seekAmount}s", modifier = Modifier.align(Alignment.CenterEnd).padding(end = 40.dp))

        // ---- volume / brightness HUD ----
        AnimatedVisibility(hud != null, modifier = Modifier.align(Alignment.Center), enter = fadeIn() + scaleIn(Motion.bouncy, 0.8f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
            val (icon, level) = hud ?: (Icons.Rounded.VolumeUp to 0f)
            Row(
                Modifier.height(48.dp).width(210.dp).glass(RoundedCornerShape(24.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.weight(1f).height(6.dp).drawBehind {
                        drawRoundRect(Color.White.copy(alpha = 0.25f), cornerRadius = CornerRadius(6f, 6f))
                        drawRoundRect(Color.White, size = Size(size.width * level, size.height), cornerRadius = CornerRadius(6f, 6f))
                    }
                )
            }
        }

        // ---- lock button (only thing visible when locked) ----
        if (locked) {
            AnimatedVisibility(controls, modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp), enter = fadeIn(), exit = fadeOut()) {
                GlassCircleButton(Icons.Rounded.Lock, "Unlock", size = 52.dp) { locked = false; controls = true; poke++ }
            }
            return@Box
        }

        // ---- controls ----
        AnimatedVisibility(controls || !Media.isPlaying || Media.error != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(260))) {
            Box(Modifier.fillMaxSize()) {
                // scrims for legibility
                Box(Modifier.fillMaxWidth().height(160.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(220.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                )

                // top bar
                Row(
                    Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 14.dp, vertical = 8.dp)
                        .animateEnterExit(enter = slideInVertically(Motion.soft()) { -it }, exit = slideOutVertically { -it }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassCircleButton(Icons.Rounded.KeyboardArrowDown, "Minimize", size = 44.dp) { Media.minimize() }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Media.title, style = Ios.headline.copy(shadow = shadowText), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (Media.subtitle.isNotBlank()) Text(Media.subtitle, style = Ios.caption.copy(shadow = shadowText), color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                    }
                    Spacer(Modifier.width(10.dp))
                    GlassCircleButton(Icons.Rounded.PictureInPictureAlt, "Picture in picture", size = 44.dp) { act?.enterPip() }
                    Spacer(Modifier.width(8.dp))
                    GlassCircleButton(Icons.Rounded.Close, "Close", size = 44.dp) { Media.stop() }
                }

                // center transport
                Row(
                    Modifier.align(Alignment.Center)
                        .animateEnterExit(enter = scaleIn(Motion.bouncy, 0.85f), exit = scaleOut(targetScale = 0.9f)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(34.dp)
                ) {
                    GlassCircleButton(Icons.Rounded.Replay10, "Back 10 seconds", size = 56.dp) { Media.seekBy(-10_000); poke++ }
                    PlayPauseButton(80)
                    GlassCircleButton(Icons.Rounded.Forward10, "Forward 10 seconds", size = 56.dp) { Media.seekBy(10_000); poke++ }
                }

                // bottom glass panel
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .widthIn(max = 720.dp)
                        .animateEnterExit(enter = slideInVertically(Motion.soft()) { it }, exit = slideOutVertically { it })
                        .glass(RoundedCornerShape(28.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Media.error?.let { ErrorRow(it) }
                    Scrubber(onScrub = { scrubbing = it; poke++ })
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallGlassAction(if (Media.speed == 1f) "1×" else "${trimSpeed(Media.speed)}×") { Media.cycleSpeed(); poke++ }
                        Spacer(Modifier.width(8.dp))
                        SmallGlassIcon(if (Media.repeat) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, Media.repeat) { Media.toggleRepeat(); poke++ }
                        Spacer(Modifier.weight(1f))
                        SmallGlassIcon(if (fill) Icons.Rounded.AspectRatio else Icons.Rounded.Fullscreen, fill) { fill = !fill; poke++ }
                        Spacer(Modifier.width(8.dp))
                        SmallGlassIcon(Icons.Rounded.ScreenRotation, landscape == true) {
                            landscape = if (landscape == true) false else true
                            act?.lockOrientation(landscape); poke++
                        }
                        Spacer(Modifier.width(8.dp))
                        SmallGlassIcon(Icons.Rounded.LockOpen, false) { locked = true; controls = true; poke++ }
                    }
                }
            }
        }
    }
}

private fun trimSpeed(s: Float): String = if (s % 1f == 0f) s.toInt().toString() else s.toString().trimEnd('0')

@Composable
private fun SeekBubble(visible: Boolean, text: String, modifier: Modifier) {
    AnimatedVisibility(visible, modifier = modifier, enter = scaleIn(Motion.bouncy, 0.6f) + fadeIn(), exit = scaleOut(targetScale = 1.2f) + fadeOut()) {
        Box(Modifier.size(96.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
            Text(text, style = Ios.headline, color = Color.White)
        }
    }
}

@Composable
private fun PlayPauseButton(sizeDp: Int) {
    val c = Ios.c
    var pressed by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (pressed) 1f else 0f, Motion.smooth, label = "ppGlow")
    Box(
        Modifier.size(sizeDp.dp)
            .pressable(scaleTo = 0.84f, onPressedChange = { pressed = it }) { Media.toggle() }
            .glass(CircleShape, glow = glow),
        contentAlignment = Alignment.Center
    ) {
        if (Media.buffering) {
            CircularProgressIndicator(Modifier.size((sizeDp - 10).dp), color = c.label.copy(alpha = 0.8f), strokeWidth = 2.dp)
        }
        androidx.compose.animation.Crossfade(Media.isPlaying, animationSpec = tween(160), label = "pp") { playing ->
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (playing) "Pause" else "Play", tint = c.label,
                modifier = Modifier.size((sizeDp * 0.5f).dp)
            )
        }
    }
}

@Composable
private fun SmallGlassIcon(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val c = Ios.c
    val bg by animateColorAsState(if (active) c.label.copy(alpha = 0.9f) else c.label.copy(alpha = 0.12f), tween(200), label = "sgi")
    Box(
        Modifier.size(38.dp).pressable(scaleTo = 0.82f, onClick = onClick).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (active) Color.Black else c.label, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SmallGlassAction(text: String, onClick: () -> Unit) {
    val c = Ios.c
    Box(
        Modifier.height(38.dp).pressable(scaleTo = 0.86f, onClick = onClick).clip(CircleShape)
            .background(c.label.copy(alpha = 0.12f)).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, style = Ios.subhead.copy(fontWeight = FontWeight.Bold), color = c.label) }
}

@Composable
private fun ErrorRow(msg: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(msg, style = Ios.footnote, color = Color(0xFFFF8A80), modifier = Modifier.weight(1f), maxLines = 2)
        SmallGlassAction("Retry") { Media.retry() }
    }
}

// ============================================================================ scrubber

/**
 * iOS-style scrubber: a slim capsule that swells while you drag it, shows a time bubble,
 * draws the buffered range, and ticks once when you cross back to the start.
 */
@Composable
private fun Scrubber(onScrub: (Boolean) -> Unit = {}) {
    val c = Ios.c
    val view = LocalView.current
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    var lastSeek by remember { mutableLongStateOf(0L) }
    val thickness by animateDpAsState(if (dragging) 12.dp else 6.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "thick")
    val dur = Media.duration
    val frac = if (dragging) dragFrac else if (dur > 0) (Media.position.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val buf = if (dur > 0) (Media.buffered.toFloat() / dur).coerceIn(0f, 1f) else 0f

    Column {
        Box(
            Modifier.fillMaxWidth().height(28.dp)
                .pointerInput(dur) {
                    widthPx = size.width.toFloat()
                    detectTapGestures { o ->
                        if (dur > 0) { Media.seekTo((o.x / size.width * dur).toLong()); view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                    }
                }
                .pointerInput(dur) {
                    widthPx = size.width.toFloat()
                    detectHorizontalDragGestures(
                        onDragStart = { o ->
                            if (dur <= 0) return@detectHorizontalDragGestures
                            dragging = true; onScrub(true)
                            dragFrac = (o.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (dur > 0) Media.seekTo((dragFrac * dur).toLong())
                            dragging = false; onScrub(false)
                        },
                        onDragCancel = { dragging = false; onScrub(false) }
                    ) { ch, dx ->
                        if (dur <= 0) return@detectHorizontalDragGestures
                        ch.consume()
                        val prev = dragFrac
                        dragFrac = (dragFrac + dx / size.width).coerceIn(0f, 1f)
                        if ((prev > 0f && dragFrac == 0f) || (prev < 1f && dragFrac == 1f)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        // live preview seeking (throttled)
                        val now = System.currentTimeMillis()
                        if (now - lastSeek > 120) { lastSeek = now; Media.seekTo((dragFrac * dur).toLong()) }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier.fillMaxWidth().height(thickness).drawBehind {
                    val r = CornerRadius(size.height / 2, size.height / 2)
                    drawRoundRect(c.label.copy(alpha = 0.18f), cornerRadius = r)
                    drawRoundRect(c.label.copy(alpha = 0.28f), size = Size(size.width * buf, size.height), cornerRadius = r)
                    drawRoundRect(c.label, size = Size(size.width * frac, size.height), cornerRadius = r)
                }
            )
            if (dragging && dur > 0) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val x = with(density) { (widthPx * frac).toDp() }
                Box(
                    Modifier.offset(x = (x - 34.dp).coerceAtLeast(0.dp), y = (-34).dp)
                        .glass(RoundedCornerShape(12.dp), strong = true).padding(horizontal = 10.dp, vertical = 4.dp)
                ) { Text(fmt((frac * dur).toLong()), style = Ios.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.label) }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(fmt(if (dragging) (frac * dur).toLong() else Media.position), style = Ios.caption.copy(fontWeight = FontWeight.Medium), color = c.secondary)
            Spacer(Modifier.weight(1f))
            if (Media.isLive) {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFFF3B30)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    Text("LIVE", style = Ios.caption2.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
            } else {
                Text("−" + fmt((dur - if (dragging) (frac * dur).toLong() else Media.position).coerceAtLeast(0)), style = Ios.caption.copy(fontWeight = FontWeight.Medium), color = c.secondary)
            }
        }
    }
}

// ============================================================================ audio

@Composable
private fun AudioPlayer() {
    val c = Ios.c
    val ctx = LocalContext.current
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol) }
    LaunchedEffect(Unit) {
        while (true) { delay(800); vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol }
    }
    val accent = remember(Media.artwork, Media.title) { accentFor(Media.artwork, Media.title) }
    val bgTop by animateColorAsState(lerp(accent, Color.Black, 0.25f), tween(600), label = "bgTop")
    val bgBottom by animateColorAsState(lerp(accent, Color.Black, 0.82f), tween(600), label = "bgBottom")
    val artScale by animateFloatAsState(if (Media.isPlaying) 1f else 0.84f, spring(dampingRatio = 0.62f, stiffness = 260f), label = "artScale")

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bgTop, bgBottom)))) {
        // soft light blobs for depth
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawCircle(Color.White.copy(alpha = 0.10f), radius = size.width * 0.7f, center = Offset(size.width * 0.15f, size.height * 0.1f))
                drawCircle(accent.copy(alpha = 0.35f), radius = size.width * 0.6f, center = Offset(size.width * 0.95f, size.height * 0.55f))
            }
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassCircleButton(Icons.Rounded.KeyboardArrowDown, "Minimize", size = 44.dp) { Media.minimize() }
                Text("Now Playing", style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold), color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                GlassCircleButton(Icons.Rounded.Close, "Close", size = 44.dp) { Media.stop() }
            }
            Spacer(Modifier.weight(1f))
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = maxWidth.coerceAtMost(340.dp)
                Box(
                    Modifier.size(side).graphicsLayer { scaleX = artScale; scaleY = artScale }
                        .shadow(40.dp, RoundedCornerShape(30.dp), ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.6f))
                        .clip(RoundedCornerShape(30.dp))
                ) {
                    Artwork(Media.artwork, Media.title, sizeDp = side, corner = 30)
                }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth()) {
                Text(Media.title, style = Ios.title3.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (Media.subtitle.isNotBlank()) Text(Media.subtitle, style = Ios.body, color = Color.White.copy(alpha = 0.65f), maxLines = 1)
            }
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(32.dp)).padding(horizontal = 20.dp, vertical = 16.dp)) {
                Media.error?.let { ErrorRow(it) }
                Scrubber()
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    InlineIconButton(Icons.Rounded.Replay10, "Back 10 seconds", size = 54.dp, iconSize = 30.dp) { Media.seekBy(-10_000) }
                    PlayPauseButton(74)
                    InlineIconButton(Icons.Rounded.Forward10, "Forward 10 seconds", size = 54.dp, iconSize = 30.dp) { Media.seekBy(10_000) }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VolumeDown, null, tint = c.secondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    VolumeBar(vol) { v ->
                        vol = v
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVol).roundToInt(), 0)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.VolumeUp, null, tint = c.secondary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallGlassAction(if (Media.speed == 1f) "1×" else "${trimSpeed(Media.speed)}×") { Media.cycleSpeed() }
                    Spacer(Modifier.weight(1f))
                    SmallGlassIcon(if (Media.repeat) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, Media.repeat) { Media.toggleRepeat() }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.VolumeBar(value: Float, onChange: (Float) -> Unit) {
    val c = Ios.c
    var dragging by remember { mutableStateOf(false) }
    val thickness by animateDpAsState(if (dragging) 10.dp else 6.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "volThick")
    Box(
        Modifier.weight(1f).height(26.dp)
            .pointerInput(Unit) { detectTapGestures { o -> onChange((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false }
                ) { ch, _ -> ch.consume(); onChange((ch.position.x / size.width).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.fillMaxWidth().height(thickness).drawBehind {
                val r = CornerRadius(size.height / 2, size.height / 2)
                drawRoundRect(c.label.copy(alpha = 0.18f), cornerRadius = r)
                drawRoundRect(c.label, size = Size(size.width * value, size.height), cornerRadius = r)
            }
        )
    }
}

private fun accentFor(art: Bitmap?, title: String): Color {
    if (art != null) {
        runCatching {
            val px = Bitmap.createScaledBitmap(art, 1, 1, true).getPixel(0, 0)
            return Color(px).copy(alpha = 1f)
        }
    }
    val palette = listOf(Color(0xFF5E5CE6), Color(0xFF0A84FF), Color(0xFF30B0C7), Color(0xFFFF375F), Color(0xFFBF5AF2), Color(0xFFFF9F0A), Color(0xFF32D74B))
    return palette[abs(title.hashCode()) % palette.size]
}

@Composable
private fun Artwork(art: Bitmap?, title: String, size: Int = 0, corner: Int = 12, video: Boolean = false, sizeDp: androidx.compose.ui.unit.Dp? = null) {
    val s = sizeDp ?: size.dp
    val accent = remember(art, title) { accentFor(art, title) }
    Box(
        Modifier.size(s).clip(RoundedCornerShape(corner.dp))
            .background(Brush.linearGradient(listOf(lerp(accent, Color.White, 0.25f), lerp(accent, Color.Black, 0.35f)))),
        contentAlignment = Alignment.Center
    ) {
        if (art != null) {
            Image(art.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                if (video) Icons.Rounded.PlayArrow else Icons.Rounded.MusicNote, null,
                tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(s * 0.42f)
            )
        }
    }
}

@Suppress("unused")
private val keep = listOf(Modifier.fillMaxHeight(), Modifier.aspectRatio(1f))
