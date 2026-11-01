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

// Compatibility names keep existing feature screens bound to one original browser palette.
val LightIos = IosColors(
    dark = false, background = Color(0xFFF5F8F7), grouped = Color(0xFFEDF3F1),
    card = Color(0xCCFFFFFF), fill = Color(0x18204741), label = Color(0xFF152E2A),
    secondary = Color(0xFF53685F), tertiary = Color(0xFF849B93), separator = Color(0x24204741),
    tint = Color(0xFF076F61), red = Color(0xFFC84647), green = Color(0xFF138572), orange = Color(0xFFB97624),
    glass = Color(0xB8F8FFFC), glassStrong = Color(0xE8F5FCF9), glassBorder = Color(0xCDFFFFFF),
    glassHighlight = Color(0xE0FFFFFF), scrim = Color(0x55122C27), wall = Color(0xFFEDF3F0),
    cardBorder = Color(0x1F126957)
)
val DarkIos = IosColors(
    dark = true, background = Color(0xFF0D1919), grouped = Color(0xFF112222),
    card = Color(0xCF1B302F), fill = Color(0x386EAAA0), label = Color(0xFFE5F4EF),
    secondary = Color(0xFFA2BDB4), tertiary = Color(0xFF718F85), separator = Color(0x306C998B),
    tint = Color(0xFF76E3C6), red = Color(0xFFFF8C89), green = Color(0xFF76E3C6), orange = Color(0xFFEAC28B),
    glass = Color(0xB31B3330), glassStrong = Color(0xEA19312E), glassBorder = Color(0x4576BAA3),
    glassHighlight = Color(0x3DDCFFF3), scrim = Color(0xA304100E), wall = Color(0xFF0C1D1A),
    cardBorder = Color(0x3A76BAA3)
)

val LocalIos = staticCompositionLocalOf { LightIos }

object Ios {
    val c: IosColors @Composable get() = LocalIos.current
    val title = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
    val title3 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
    val body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
    val callout = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
    val subhead = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    val footnote = TextStyle(fontSize = 13.sp, letterSpacing = (-0.1).sp)
    val caption = TextStyle(fontSize = 12.sp)
    val caption2 = TextStyle(fontSize = 11.sp)
}

@Composable
fun IBrowserTheme(content: @Composable () -> Unit) {
    // Settings › Appearance: 0 = follow the system, 1 = always light, 2 = always dark
    val dark = (when (com.gr4nt.browser.data.Store.settings.appearance) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }) || com.gr4nt.browser.browser.Browser.current?.isPrivate == true
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
    val scheme = if (dark) darkColorScheme(primary = ios.tint, background = ios.background, surface = ios.card, onPrimary = Color(0xFF072A21), onSurface = ios.label)
    else lightColorScheme(primary = ios.tint, background = ios.background, surface = ios.card, onPrimary = Color.White, onSurface = ios.label)
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
