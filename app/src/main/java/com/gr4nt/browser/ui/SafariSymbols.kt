package com.gr4nt.browser.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Original monoline toolbar artwork, sized on a 24-point grid. No bundled Apple assets. */
object SafariSymbols {
    private fun symbol(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.65f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
        }.build()
    val Back = symbol("Back") { moveTo(14.5f, 4.5f); lineTo(7f, 12f); lineTo(14.5f, 19.5f) }
    val Forward = symbol("Forward") { moveTo(9.5f, 4.5f); lineTo(17f, 12f); lineTo(9.5f, 19.5f) }
    val Plus = symbol("New tab") { moveTo(12f, 4f); lineTo(12f, 20f); moveTo(4f, 12f); lineTo(20f, 12f) }
    val Sidebar = symbol("Sidebar") {
        moveTo(5f, 4.5f); lineTo(19f, 4.5f); quadTo(21f, 4.5f, 21f, 6.5f); lineTo(21f, 17.5f); quadTo(21f, 19.5f, 19f, 19.5f)
        lineTo(5f, 19.5f); quadTo(3f, 19.5f, 3f, 17.5f); lineTo(3f, 6.5f); quadTo(3f, 4.5f, 5f, 4.5f); close()
        moveTo(9f, 4.5f); lineTo(9f, 19.5f)
    }
    val Tabs = symbol("Tabs") {
        moveTo(8f, 3f); lineTo(18f, 3f); quadTo(20f, 3f, 20f, 5f); lineTo(20f, 15f)
        moveTo(5f, 7f); lineTo(15f, 7f); quadTo(17f, 7f, 17f, 9f); lineTo(17f, 19f); quadTo(17f, 21f, 15f, 21f)
        lineTo(5f, 21f); quadTo(3f, 21f, 3f, 19f); lineTo(3f, 9f); quadTo(3f, 7f, 5f, 7f); close()
    }
    val Share = symbol("Share") {
        moveTo(8f, 10f); lineTo(5f, 10f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 10f); lineTo(16f, 10f)
        moveTo(12f, 15f); lineTo(12f, 2f); moveTo(7.5f, 6.5f); lineTo(12f, 2f); lineTo(16.5f, 6.5f)
    }
    val Download = symbol("Downloads") {
        moveTo(21f, 12f); arcTo(9f, 9f, 0f, true, true, 3f, 12f); arcTo(9f, 9f, 0f, true, true, 21f, 12f); close()
        moveTo(12f, 6.5f); lineTo(12f, 16.5f); moveTo(8f, 12.5f); lineTo(12f, 16.5f); lineTo(16f, 12.5f)
    }
    val PageMenu = symbol("Page menu") {
        moveTo(4f, 6f); lineTo(20f, 6f); moveTo(4f, 10.5f); lineTo(20f, 10.5f)
        moveTo(4f, 15f); lineTo(12f, 15f); moveTo(4f, 19.5f); lineTo(12f, 19.5f)
        moveTo(16f, 20f); lineTo(18.5f, 14f); lineTo(21f, 20f); moveTo(17f, 18f); lineTo(20f, 18f)
    }
}
