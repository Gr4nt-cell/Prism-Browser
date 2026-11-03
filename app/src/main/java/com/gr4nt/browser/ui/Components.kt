package com.gr4nt.browser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.IntOffset
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick as semanticClick
import androidx.compose.ui.semantics.onLongClick as semanticLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gr4nt.browser.data.hostOf
import kotlin.math.abs

/** The app background: a plain, flat colour (white in light mode, black in dark mode). */
@Composable
fun Modifier.wallpaper(): Modifier {
    val c = Ios.c
    return this.background(c.wall)
}
/** Pastel refraction backdrop used by the Safari Glass start page and tab grid. */
@Composable
fun Modifier.safariGlassWallpaper(privateMode: Boolean = false): Modifier {
    val c = Ios.c
    val k = if (c.dark) 0.42f else 0.30f
    return drawBehind {
        drawRect(if (privateMode) c.background else c.grouped)
        if (!privateMode) {
            drawRect(Brush.radialGradient(
                listOf(c.tint.copy(alpha = k), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height * 0.08f),
                radius = size.width * 1.1f
            ))
            drawRect(Brush.radialGradient(
                listOf(c.green.copy(alpha = k * 0.75f), Color.Transparent),
                center = Offset(size.width, size.height * 0.55f),
                radius = size.width * 1.1f
            ))
            drawRect(Brush.radialGradient(
                listOf(c.orange.copy(alpha = k * 0.7f), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.98f),
                radius = size.width
            ))
        }
    }
}

/**
 * Translucent frosted card with a fine highlight rim and a restrained layered shadow.
 */
@Composable
fun Modifier.glassCard(shape: Shape): Modifier {
    val c = Ios.c
    return this
        .shadow(14.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = if (c.dark) 0.18f else 0.10f), spotColor = Color.Black.copy(alpha = if (c.dark) 0.22f else 0.14f))
        .clip(shape)
        .background(c.card)
        .background(
            Brush.verticalGradient(
                0f to c.glassHighlight.copy(alpha = c.glassHighlight.alpha * 0.24f),
                0.48f to Color.Transparent,
                1f to Color.Transparent
            )
        )
        .border(0.6.dp, c.glassBorder, shape)
}

/** Shared spring specs so motion feels consistent everywhere. */
object Motion {
    val press = spring<Float>(dampingRatio = 0.82f, stiffness = 700f)
    val release = spring<Float>(dampingRatio = 0.78f, stiffness = 380f)
    val smooth = spring<Float>(dampingRatio = 0.82f, stiffness = 320f)
    val bouncy = spring<Float>(dampingRatio = 0.68f, stiffness = 380f)
    fun <T> soft() = spring<T>(dampingRatio = 0.86f, stiffness = 300f)
}

/**
 * Liquid-glass surface: very translucent fill, soft specular sheen at the top, a faint
 * refraction rim along the bottom edge, a light gradient hairline and a wide soft shadow.
 * [glow] (0..1) brightens the glass while it is pressed, like iOS 26/27 Liquid Glass.
 */
@Composable
fun Modifier.glass(shape: Shape, strong: Boolean = false, elevation: Dp = 18.dp, glow: Float = 0f): Modifier {
    val c = Ios.c
    if (!c.liquid) {
        // plain frosted bar: flat translucent fill, even hairline, softer shadow — no sheen, rim or refraction tint
        return this
            .shadow(elevation * 0.7f, shape, clip = false,
                ambientColor = Color.Black.copy(alpha = ((if (c.dark) 0.44f else 0.14f) * c.glassShadow).coerceAtMost(1f)),
                spotColor = Color.Black.copy(alpha = ((if (c.dark) 0.54f else 0.24f) * c.glassShadow).coerceAtMost(1f)))
            .shadow(2.dp, shape, clip = false,
                ambientColor = Color.Black.copy(alpha = if (c.dark) 0.42f else 0.08f), spotColor = Color.Black.copy(alpha = if (c.dark) 0.52f else 0.16f))
            .clip(shape)
            .background(if (strong) c.glassStrong else c.glass)
            .background(Color.White.copy(alpha = glow * if (c.dark) 0.10f else 0.22f))
            .border(0.6.dp, if (c.dark) c.glassHighlight else c.glassBorder, shape)
    }
    return this
        .shadow(
            elevation.coerceAtMost(14.dp), shape, clip = false,
            ambientColor = Color.Black.copy(alpha = ((if (c.dark) 0.18f else 0.10f) * c.glassShadow).coerceAtMost(1f)),
            spotColor = Color.Black.copy(alpha = ((if (c.dark) 0.22f else 0.14f) * c.glassShadow).coerceAtMost(1f))
        )
        .clip(shape)
        .background(if (strong) c.glassStrong else c.glass)
        .background(
            Brush.verticalGradient(
                listOf(
                    c.glassHighlight.copy(alpha = c.glassHighlight.alpha * 0.55f),
                    Color.Transparent,
                    Color.Transparent
                )
            )
        )
        .background(Color.White.copy(alpha = glow * if (c.dark) 0.14f else 0.30f))
        .border(
            0.6.dp,
            Brush.verticalGradient(
                listOf(c.glassHighlight, c.glassBorder.copy(alpha = 0.10f), c.glassBorder.copy(alpha = 0.18f))
            ),
            shape
        )
}

/** Light tick on touch-down, like iOS controls. */
fun View.tick() {
    performHapticFeedback(if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.KEYBOARD_PRESS else HapticFeedbackConstants.VIRTUAL_KEY)
}

/**
 * Instant-response press: scales down with a spring the moment a finger lands (no tap delay),
 * springs back with a little overshoot on release, gives a haptic tick, supports long-press.
 */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    scaleTo: Float = 0.96f,
    haptic: Boolean = true,
    onPressedChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) scaleTo else 1f, if (pressed) Motion.press else Motion.release, label = "press")
    val view = LocalView.current
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    val change by rememberUpdatedState(onPressedChange)
    return this
        .semantics(mergeDescendants = true) {
            role = Role.Button
            if (enabled) {
                semanticClick { click(); true }
                if (onLongClick != null) semanticLongClick { long?.invoke(); true }
            } else disabled()
        }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(enabled, onLongClick != null) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    pressed = true; change?.invoke(true)
                    if (haptic) view.tick()
                    tryAwaitRelease()
                    pressed = false; change?.invoke(false)
                },
                onLongPress = if (onLongClick != null) {
                    { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); long?.invoke() }
                } else null,
                onTap = { click() }
            )
        }
}

/**
 * iOS table-cell style feedback: the row tints the moment it is pressed (after a tiny delay so
 * scrolling doesn't flash rows) and fades back out on release.
 */
@Composable
fun Modifier.tapHighlight(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val fill = Ios.c.fill
    val bg by animateColorAsState(
        if (pressed) fill else fill.copy(alpha = 0f),
        if (pressed) tween(60) else tween(260),
        label = "hl"
    )
    val view = LocalView.current
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    return this
        .semantics(mergeDescendants = true) {
            role = Role.Button
            if (enabled) {
                semanticClick { click(); true }
                if (onLongClick != null) semanticLongClick { long?.invoke(); true }
            } else disabled()
        }
        .drawBehind { drawRect(bg) }
        .pointerInput(enabled, onLongClick != null) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    coroutineScope {
                        val delayed = launch { delay(45); pressed = true }
                        val released = tryAwaitRelease()
                        delayed.cancel()
                        if (released && !pressed) { pressed = true; delay(90) }
                        pressed = false
                    }
                },
                onLongPress = if (onLongClick != null) {
                    { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); long?.invoke() }
                } else null,
                onTap = { click() }
            )
        }
}

@Composable
fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    enabled: Boolean = true,
    tint: Color = Ios.c.label,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (pressed) 1f else 0f, Motion.smooth, label = "glow")
    Box(
        modifier
            .size(size)
            .pressable(enabled, onLongClick, scaleTo = 0.84f, onPressedChange = { pressed = it }, onClick = onClick)
            .glass(CircleShape, glow = glow),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
fun ToolbarIcon(
    icon: ImageVector,
    desc: String?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (pressed) 1f else 0f, Motion.smooth, label = "glow")
    Box(
        modifier.size(44.dp)
            .pressable(enabled, onLongClick, scaleTo = 0.8f, onPressedChange = { pressed = it }, onClick = onClick)
            .drawBehind { if (glow > 0f) drawCircle(Color.White.copy(alpha = 0.22f * glow), radius = size.minDimension / 2f) },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = if (enabled) Ios.c.tint else Ios.c.tertiary, modifier = Modifier.size(24.dp))
    }
}

/** Small circular icon button used inside glass bars (no glass of its own). */
@Composable
fun InlineIconButton(
    icon: ImageVector,
    desc: String?,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    tint: Color = Ios.c.label,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (pressed) 1f else 0f, Motion.smooth, label = "glow")
    val fill = Ios.c.fill
    Box(
        Modifier.size(size)
            .pressable(scaleTo = 0.8f, onPressedChange = { pressed = it }, onClick = onClick)
            .drawBehind { if (glow > 0f) drawCircle(fill.copy(alpha = fill.alpha * 2.2f * glow)) },
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(iconSize)) }
}

/** Favicon or coloured letter tile (Safari Favorites style). */
@Composable
fun SiteTile(url: String, title: String, icon: android.graphics.Bitmap? = null, size: Dp = 60.dp, corner: Dp = 14.dp) {
    val host = hostOf(url)
    val letter = (title.ifBlank { host }).firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
    val palette = listOf(
        Color(0xFF237F70), Color(0xFF567A98), Color(0xFF488F94), Color(0xFF5B8864), Color(0xFFB57C38),
        Color(0xFFB67166), Color(0xFF86789D), Color(0xFF9A8861), Color(0xFF58989F), Color(0xFFB26F4E)
    )
    val col = palette[(host.hashCode() and Int.MAX_VALUE) % palette.size]
    Box(
        Modifier
            .size(size)
            .shadow(2.dp, RoundedCornerShape(corner))
            .clip(RoundedCornerShape(corner))
            .background(Ios.c.card),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(icon.asImageBitmap(), null, Modifier.size(size * 0.55f))
        } else {
            Box(Modifier.fillMaxSize().background(col.copy(alpha = if (Ios.c.dark) 0.30f else 0.14f)), contentAlignment = Alignment.Center) {
                Text(letter, color = col, fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Ios.title3.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = Ios.c.label, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Grouped inset list section (Settings style). */
@Composable
fun IosGroup(header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) {
            Text(header, style = Ios.headline, color = Ios.c.secondary, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp, top = 22.dp))
        } else Spacer(Modifier.height(22.dp))
        Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(20.dp)), content = content)
        if (footer != null) {
            Text(footer, style = Ios.footnote, color = Ios.c.secondary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
        }
    }
}

@Composable
fun IosRow(
    title: String,
    icon: ImageVector? = null,
    iconBg: Color? = null,
    value: String? = null,
    titleColor: Color = Ios.c.label,
    showChevron: Boolean = false,
    divider: Boolean = true,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.tapHighlight(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = if (subtitle != null) 9.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                if (iconBg != null) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(iconBg.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = iconBg, modifier = Modifier.size(20.dp))
                    }
                } else Icon(icon, null, tint = Ios.c.tint, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = Ios.body, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = Ios.footnote, color = Ios.c.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (value != null) Text(value, style = Ios.body, color = Ios.c.secondary, maxLines = 1)
            trailing?.invoke()
            if (showChevron) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ios.c.tertiary, modifier = Modifier.size(22.dp))
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().padding(start = if (icon != null) 60.dp else 16.dp).height(0.5.dp).background(Ios.c.separator))
    }
}

@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = Ios.c.tint, checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = Ios.c.fill, uncheckedBorderColor = Color.Transparent
        )
    )
}

/** Full-screen modal sheet with iOS nav-bar (title + Done). */
@Composable
fun IosSheet(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailingText: String = "Done",
    centeredOnMobile: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(220))) {
        val desktop = isLargeScreen()
        val floating = desktop || centeredOnMobile
        val compactFloating = centeredOnMobile && !desktop
        val drag = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val dismiss by rememberUpdatedState(onDismiss)
        LaunchedEffect(Unit) { drag.snapTo(0f) }
        Box(Modifier.fillMaxSize().background(Ios.c.scrim), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .then(
                        if (compactFloating) {
                            Modifier.fillMaxWidth(0.84f).fillMaxHeight(0.7f)
                                .widthIn(max = 520.dp).heightIn(max = 760.dp)
                        } else {
                            Modifier.widthIn(max = if (desktop) 720.dp else 10000.dp)
                                .heightIn(max = if (desktop) 820.dp else 10000.dp)
                                .fillMaxSize()
                        }
                    )
                    .animateEnterExit(
                        enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 340f, visibilityThreshold = IntOffset.VisibilityThreshold)) { it },
                        exit = slideOutVertically(spring(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = IntOffset.VisibilityThreshold)) { it }
                    )
                    .graphicsLayer { translationY = drag.value.coerceAtLeast(0f) }
                    .statusBarsPadding()
                    .padding(
                        top = if (desktop) 24.dp else if (compactFloating) 0.dp else 10.dp,
                        bottom = if (desktop) 24.dp else if (compactFloating) 0.dp else 0.dp
                    )
                    .clip(if (floating) RoundedCornerShape(26.dp) else RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .safariGlassWallpaper()
            ) {
                // grabber + nav bar: drag down to dismiss
                Column(
                    Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (drag.value > 180f) { dismiss() } else drag.animateTo(0f, Motion.bouncy)
                                }
                            },
                            onDragCancel = { scope.launch { drag.animateTo(0f, Motion.bouncy) } }
                        ) { ch, dy -> ch.consume(); scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(-20f)) } }
                    }
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(36.dp).height(5.dp).clip(CircleShape).background(Ios.c.tertiary))
                    }
                    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
                        Box(Modifier.align(Alignment.CenterStart)) { leading?.invoke() }
                        Text(title, style = Ios.title3, color = Ios.c.label, modifier = Modifier.align(Alignment.CenterStart).padding(start = if (leading != null) 96.dp else 4.dp, end = 100.dp))
                        Box(
                            Modifier.align(Alignment.CenterEnd)
                                .pressable(scaleTo = 0.88f) { dismiss() }
                                .clip(CircleShape).background(Ios.c.fill)
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(trailingText, style = Ios.headline.copy(fontSize = 16.sp), color = Ios.c.label)
                        }
                    }
                }
                content()
            }
        }
    }
}

@Composable
fun TextAction(text: String, color: Color = Ios.c.tint, bold: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        style = Ios.body.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
        color = color,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable(scaleTo = 0.9f, onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, style = Ios.caption2.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

@Composable
fun VerticalSpacer(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun RowSpaced(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}
