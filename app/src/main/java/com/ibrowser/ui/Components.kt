package com.ibrowser.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibrowser.data.hostOf
import kotlin.math.abs

/** Liquid-glass surface: translucent fill, specular top highlight and hairline border. */
@Composable
fun Modifier.glass(shape: Shape, strong: Boolean = false, elevation: Dp = 14.dp): Modifier {
    val c = Ios.c
    return this
        .shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
        .clip(shape)
        .background(if (strong) c.glassStrong else c.glass)
        .background(
            Brush.verticalGradient(
                listOf(c.glassHighlight.copy(alpha = c.glassHighlight.alpha * 0.55f), Color.Transparent, Color.Transparent)
            )
        )
        .border(
            0.6.dp,
            Brush.verticalGradient(listOf(c.glassHighlight, c.glassBorder.copy(alpha = 0.10f), c.glassBorder.copy(alpha = 0.18f))),
            shape
        )
}

/** Press-to-shrink clickable like iOS controls. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val m = this.scale(if (pressed) 0.92f else 1f)
    return if (onLongClick != null) {
        m.then(
            Modifier.combinedClickableCompat(src, enabled, onClick, onLongClick)
        )
    } else m.clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(
    src: MutableInteractionSource, enabled: Boolean, onClick: () -> Unit, onLongClick: () -> Unit
): Modifier = this.then(
    Modifier.combinedClickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick, onLongClick = onLongClick)
)

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
    Box(
        modifier
            .size(size)
            .glass(CircleShape)
            .pressable(enabled, onLongClick, onClick),
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
    Box(modifier.size(44.dp).pressable(enabled, onLongClick, onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = if (enabled) Ios.c.tint else Ios.c.tertiary, modifier = Modifier.size(24.dp))
    }
}

/** Favicon or coloured letter tile (Safari Favorites style). */
@Composable
fun SiteTile(url: String, title: String, icon: android.graphics.Bitmap? = null, size: Dp = 60.dp, corner: Dp = 14.dp) {
    val host = hostOf(url)
    val letter = (title.ifBlank { host }).firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
    val palette = listOf(
        Color(0xFF5E5CE6), Color(0xFF0A84FF), Color(0xFF30B0C7), Color(0xFF34C759), Color(0xFFFF9F0A),
        Color(0xFFFF375F), Color(0xFFBF5AF2), Color(0xFFAC8E68), Color(0xFF64D2FF), Color(0xFFFF453A)
    )
    val col = palette[abs(host.hashCode()) % palette.size]
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
        Text(text, style = Ios.title3.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = Ios.c.label, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Grouped inset list section (Settings style). */
@Composable
fun IosGroup(header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) {
            Text(header.uppercase(), style = Ios.footnote, color = Ios.c.secondary, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp, top = 22.dp))
        } else Spacer(Modifier.height(22.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Ios.c.card), content = content)
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
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = if (subtitle != null) 9.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                if (iconBg != null) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
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
            checkedThumbColor = Color.White, checkedTrackColor = Ios.c.green, checkedBorderColor = Color.Transparent,
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
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible,
        enter = slideInVertically(tween(320)) { it } + fadeIn(),
        exit = slideOutVertically(tween(260)) { it } + fadeOut()
    ) {
        Box(Modifier.fillMaxSize().background(Ios.c.scrim)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Ios.c.grouped)
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(5.dp).clip(CircleShape).background(Ios.c.tertiary))
                }
                Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
                    Box(Modifier.align(Alignment.CenterStart)) { leading?.invoke() }
                    Text(title, style = Ios.headline, color = Ios.c.label, modifier = Modifier.align(Alignment.Center), textAlign = TextAlign.Center)
                    Box(
                        Modifier.align(Alignment.CenterEnd).clip(CircleShape).background(Ios.c.fill)
                            .clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(trailingText, style = Ios.headline.copy(fontSize = 16.sp), color = Ios.c.label)
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
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp)
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
