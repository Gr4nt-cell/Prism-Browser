package app.prism.browser.engine

import app.prism.browser.core.Obs
import app.prism.browser.core.ObsList
import android.graphics.Bitmap
import android.webkit.WebView
import app.prism.browser.video.MediaItem

class Tab(val id: Long, initialUrl: String, val parent: Tab? = null) {
    var webView: WebView? = null
    /** URL to load once the WebView is created (restored tabs load lazily). */
    var pendingUrl: String? = initialUrl.takeIf { it != START }

    var url by Obs(initialUrl)
    var title by Obs(if (initialUrl == START) "Start Page" else initialUrl)
    var progress by Obs(100)
    var loading by Obs(false)
    var canGoBack by Obs(false)
    var canGoForward by Obs(false)
    var favicon by Obs<Bitmap?>(null)
    var thumbnail by Obs<Bitmap?>(null)
    var blocked by Obs(0)
    var desktop by Obs(false)
    var findMatches by Obs(0)
    var findIndex by Obs(0)

    /** Popup windows are hidden until their first URL passes the ad filter. */
    var probation by Obs(false)

    val media = ObsList<MediaItem>()

    @Volatile var host: String? = null

    val isStart get() = url == START

    val displayHost: String
        get() = if (isStart) "" else runCatching { android.net.Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url

    val secure get() = url.startsWith("https://")

    companion object {
        const val START = "prism://start"
    }
}
