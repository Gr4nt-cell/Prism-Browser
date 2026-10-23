package com.gr4nt.browser.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class IosColors(
    val dark: Boolean,
    val background: Color,        // systemBackground
    val grouped: Color,           // systemGroupedBackground
    val card: Color,              // secondarySystemGroupedBackground
    val fill: Color,              // tertiarySystemFill
    val label: Color,
    val secondary: Color,
    val tertiary: Color,
    val separator: Color,
    val tint: Color,
    val red: Color,
    val green: Color,
    val orange: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val scrim: Color,
    // App background: plain white (light) / plain black (dark)
    val glassShadow: Float = 1f,                 // shadow strength multiplier for glass
    val liquid: Boolean = true,                  // false = plain frosted bar (no sheen, rim or glow)
    val wall: Color = Color(0xFFFFFFFF),
    val cardBorder: Color = Color(0x24000000)     // hairline that keeps white cards visible on white
)

val LightIos = IosColors(
    dark = false,
    background = Color(0xFFFFFFFF),
    grouped = Color(0xFFFFFFFF),
    card = Color(0xF7FFFFFF),
    fill = Color(0x1F767680),
    label = Color(0xFF000000),
    secondary = Color(0x993C3C43),
    tertiary = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    tint = Color(0xFF007AFF),
    red = Color(0xFFFF3B30),
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    glass = Color(0x8CFFFFFF),
    glassStrong = Color(0xE6FFFFFF),
    glassBorder = Color(0x33000000),
    glassHighlight = Color(0xCCFFFFFF),
    scrim = Color(0x26000000)
)

val DarkIos = IosColors(
    dark = true,
    background = Color(0xFF000000),
    grouped = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    fill = Color(0x3D767680),
    label = Color(0xFFFFFFFF),
    secondary = Color(0x99EBEBF5),
    tertiary = Color(0x4DEBEBF5),
    separator = Color(0xA6545458),
    tint = Color(0xFF0A84FF),
    red = Color(0xFFFF453A),
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9F0A),
    glass = Color(0x8C2C2C2E),
    glassStrong = Color(0xE62C2C2E),
    glassBorder = Color(0x52FFFFFF),
    glassHighlight = Color(0x33FFFFFF),
    scrim = Color(0x66000000),
    wall = Color(0xFF0F0F11),
    cardBorder = Color(0x33FFFFFF)
)

val LocalIos = staticCompositionLocalOf { LightIos }

object Ios {
    val c: IosColors @Composable get() = LocalIos.current
    val title = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
    val title3 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
    val body = TextStyle(fontSize = 17.sp, letterSpacing = (-0.4).sp)
    val callout = TextStyle(fontSize = 16.sp, letterSpacing = (-0.3).sp)
    val subhead = TextStyle(fontSize = 15.sp, letterSpacing = (-0.2).sp)
    val footnote = TextStyle(fontSize = 13.sp, letterSpacing = (-0.1).sp)
    val caption = TextStyle(fontSize = 12.sp)
    val caption2 = TextStyle(fontSize = 11.sp)
}

@Composable
fun IBrowserTheme(content: @Composable () -> Unit) {
    // Settings › Appearance: 0 = follow the system, 1 = always light, 2 = always dark
    val dark = when (com.gr4nt.browser.data.Store.settings.appearance) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val ios = if (dark) DarkIos else LightIos
    val scheme = if (dark) darkColorScheme(primary = ios.tint, background = ios.background, surface = ios.card)
    else lightColorScheme(primary = ios.tint, background = ios.background, surface = ios.card)
    CompositionLocalProvider(LocalIos provides ios) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

fun formatBytes(b: Long): String {
    if (b < 0) return "—"
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = b / 1024.0
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return if (v >= 100) "%.0f %s".format(v, units[i]) else "%.1f %s".format(v, units[i])
}

fun formatDuration(sec: Long): String {
    if (sec < 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
