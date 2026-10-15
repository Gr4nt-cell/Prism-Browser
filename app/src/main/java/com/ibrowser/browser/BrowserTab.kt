package com.ibrowser.browser

import android.graphics.Bitmap
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ibrowser.data.hostOf
import com.ibrowser.media.MediaFound
import java.util.UUID

class BrowserTab(
    val isPrivate: Boolean,
    initialUrl: String = "",
    val parentId: String? = null,
    val id: String = UUID.randomUUID().toString()
) {
    var webView: WebView? = null

    /** URL to load once the WebView is created (lazy tab restore). */
    var pendingUrl: String? = initialUrl.ifBlank { null }

    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var favicon by mutableStateOf<Bitmap?>(null)
    var thumbnail by mutableStateOf<Bitmap?>(null)
    var blocked by mutableIntStateOf(0)
    var desktop by mutableStateOf(false)
    var textZoom by mutableIntStateOf(100)
    var themeColor by mutableStateOf<Int?>(null)
    var lastAccess = System.currentTimeMillis()
    val media = mutableStateListOf<MediaFound>()

    /** Thread-safe copy of the current page URL for the network thread. */
    @Volatile var pageUrl: String = initialUrl

    val isStartPage: Boolean get() = url.isBlank() || url == "about:blank"
    val host: String get() = hostOf(url)
    val secure: Boolean get() = url.startsWith("https://")
    val displayTitle: String get() = title.ifBlank { if (isStartPage) "Start Page" else host.ifBlank { url } }
}
