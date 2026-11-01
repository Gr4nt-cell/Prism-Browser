package com.gr4nt.browser.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*

/** Compatibility facade: browser tools now use the shared Material outline icon system. */
object SafariSymbols {
    val Back = Icons.AutoMirrored.Outlined.ArrowBack
    val Forward = Icons.AutoMirrored.Outlined.ArrowForward
    val Plus = Icons.Outlined.Add
    val Sidebar = Icons.Outlined.ViewSidebar
    val Tabs = Icons.Outlined.Dashboard
    val Share = Icons.Outlined.Share
    val Download = Icons.Outlined.FileDownload
    val PageMenu = Icons.Outlined.Tune
}
