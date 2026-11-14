package app.prism.browser.engine

import app.prism.browser.core.Obs
import app.prism.browser.core.ObsList
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.util.Base64
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.prism.browser.adblock.AdBlocker
import app.prism.browser.data.Store
import app.prism.browser.download.DKind
import app.prism.browser.download.Downloader
import app.prism.browser.video.MediaItem
import app.prism.browser.video.MediaKind
import app.prism.browser.video.MediaSniffer
import java.io.File
import java.util.concurrent.atomic.AtomicLong

interface BrowserHost {
    val activity: Activity
    fun showFileChooser(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean
    fun requestPermissions(perms: Array<String>, onResult: (Boolean) -> Unit)
    fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)
    fun hideFullscreen()
}

class Snack(val message: String, val action: String? = null, val onAction: (() -> Unit)? = null) {
    val id = System.nanoTime()
}

class DownloadRequest(
    val url: String, val userAgent: String?, val contentDisposition: String?, val mime: String?,
    val length: Long, val referer: String?,
) {
    var fileName by Obs(URLUtil.guessFileName(url, contentDisposition, mime))
}

class SitePrompt(val host: String, val what: String, val onResult: (Boolean) -> Unit)

class ContextTarget(val linkUrl: String?, val imageUrl: String?, val title: String?)

enum class Sheet { NONE, MENU, DOWNLOADS, VIDEOS, HISTORY, BOOKMARKS, SETTINGS, SHIELD, ADD_FAVORITE }

class BrowserController(private val host: BrowserHost) {
    private val act get() = host.activity
    private val ids = AtomicLong(1)

    val tabs = ObsList<Tab>()
    var activeIndex by Obs(0)
    val active: Tab? get() = tabs.getOrNull(activeIndex)

    var showTabs by Obs(false)
    var sheet by Obs(Sheet.NONE)
    var snack by Obs<Snack?>(null)
    var pendingDownload by Obs<DownloadRequest?>(null)
    var sitePrompt by Obs<SitePrompt?>(null)
    var contextTarget by Obs<ContextTarget?>(null)
    var findVisible by Obs(false)
    var barCollapsed by Obs(false)
    var fullscreen by Obs(false)
    /** set by the UI: large screens default to desktop sites */
    var largeScreen by Obs(false)
    /** last touch anywhere in the app (drives the phone bar's auto-hide) */
    var lastTouch by Obs(0L)
    fun touched() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTouch > 300) lastTouch = now
    }

    /** bumps whenever the visible WebView must be re-attached */
    var webViewEpoch by Obs(0)

    private val injectJs: String by lazy { act.assets.open("prism.js").bufferedReader().readText() }
    private val sitePermissionMemory = HashMap<String, Boolean>()

    private val baseUa: String by lazy {
        WebSettings.getDefaultUserAgent(act)
            .replace("; wv)", ")")
            .replace(Regex(" Version/\\d+\\.\\d+"), "")
            .replace(Regex(" Build/[^;)]+"), "")
    }
    private val chromeVersion by lazy { Regex("Chrome/([\\d.]+)").find(baseUa)?.groupValues?.get(1) ?: "131.0.0.0" }
    private val desktopUa by lazy {
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36"
    }

    fun defaultDesktop() = Store.desktopModePref ?: largeScreen

    // ---------------------------------------------------------------- lifecycle

    fun restore(initialUrl: String?) {
        installServiceWorkerFilter()
        val (urls, act) = Store.savedTabs()
        urls.forEach { tabs += Tab(ids.getAndIncrement(), it).also { t -> t.desktop = defaultDesktop() } }
        if (tabs.isEmpty()) tabs += Tab(ids.getAndIncrement(), Tab.START).also { it.desktop = defaultDesktop() }
        activeIndex = act.coerceIn(0, tabs.lastIndex)
        if (initialUrl != null) newTab(initialUrl)
    }

    fun persist() {
        Store.saveTabs(tabs.filter { !it.probation }.map { it.webView?.url?.takeIf { u -> u.startsWith("http") } ?: it.url }, activeIndex)
        Store.flushStats()
        Downloader.save()
    }

    fun webViewFor(tab: Tab): WebView {
        tab.webView?.let { return it }
        val wv = createWebView(tab)
        tab.webView = wv
        tab.pendingUrl?.let { wv.loadUrl(it); tab.pendingUrl = null }
        return wv
    }

    // ---------------------------------------------------------------- tab ops

    fun newTab(url: String = Tab.START, background: Boolean = false, parent: Tab? = null): Tab {
        val t = Tab(ids.getAndIncrement(), url, parent).also { it.desktop = defaultDesktop() }
        val insertAt = if (parent != null) (tabs.indexOf(parent) + 1).coerceAtMost(tabs.size) else tabs.size
        tabs.add(insertAt, t)
        if (!background) select(insertAt)
        if (url != Tab.START && !background) webViewFor(t)
        persist()
        return t
    }

    fun select(index: Int) {
        if (index !in tabs.indices) return
        active?.let { captureThumbnail(it) }
        activeIndex = index
        tabs[index].let { if (!it.isStart) webViewFor(it) }
        barCollapsed = false
        webViewEpoch++
        persist()
    }

    fun select(tab: Tab) = select(tabs.indexOf(tab))

    fun close(tab: Tab) {
        val idx = tabs.indexOf(tab)
        if (idx < 0) return
        val wasActive = idx == activeIndex
        tabs.removeAt(idx)
        destroy(tab)
        if (tabs.isEmpty()) {
            tabs += Tab(ids.getAndIncrement(), Tab.START).also { it.desktop = defaultDesktop() }
            activeIndex = 0
        } else if (wasActive) {
            val parentIdx = tab.parent?.let { tabs.indexOf(it) } ?: -1
            activeIndex = if (parentIdx >= 0) parentIdx else (idx - 1).coerceIn(0, tabs.lastIndex)
        } else if (idx < activeIndex) {
            activeIndex--
        }
        active?.let { if (!it.isStart) webViewFor(it) }
        webViewEpoch++
        persist()
    }

    fun closeAll() {
        tabs.toList().forEach { destroy(it) }
        tabs.clear()
        tabs += Tab(ids.getAndIncrement(), Tab.START).also { it.desktop = defaultDesktop() }
        activeIndex = 0
        webViewEpoch++
        persist()
    }

    private fun destroy(tab: Tab) {
        tab.webView?.let { wv ->
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.stopLoading(); wv.loadUrl("about:blank"); wv.destroy()
        }
        tab.webView = null
    }

    // ---------------------------------------------------------------- navigation

    fun load(input: String, tab: Tab? = active) {
        val t = tab ?: return
        val url = UrlUtil.toUrl(input)
        if (url == Tab.START) { goHome(t); return }
        t.url = url
        val wv = webViewFor(t)
        wv.loadUrl(url)
        webViewEpoch++
    }

    fun goHome(tab: Tab? = active) {
        val t = tab ?: return
        t.url = Tab.START
        t.title = "Start Page"
        t.media.clear()
        t.webView?.let { it.stopLoading(); it.loadUrl("about:blank") }
        t.canGoBack = t.webView != null
        webViewEpoch++
    }

    fun back(): Boolean {
        val t = active ?: return false
        if (t.isStart) {
            val wv = t.webView ?: return false
            // came to the start page from a site: go back to it
            if (wv.canGoBack()) { wv.goBack(); webViewEpoch++; return true }
            if (t.parent != null && tabs.contains(t.parent)) { close(t); return true }
            return false
        }
        val wv = t.webView ?: return false
        if (wv.canGoBack()) {
            val list = wv.copyBackForwardList()
            val prev = list.getItemAtIndex(list.currentIndex - 1)?.url
            if (prev == "about:blank") { goHome(t); return true }
            wv.goBack(); return true
        }
        if (t.parent != null && tabs.contains(t.parent)) { close(t); return true }
        if (!t.isStart) { goHome(t); return true }
        return false
    }

    fun forward() { active?.webView?.let { if (it.canGoForward()) it.goForward() } }
    fun reload() { active?.let { if (it.loading) it.webView?.stopLoading() else it.webView?.reload() } }

    fun toggleDesktop(tab: Tab? = active) {
        val t = tab ?: return
        t.desktop = !t.desktop
        t.webView?.let { applyUa(it, t.desktop); it.reload() }
    }

    // ---------------------------------------------------------------- zoom

    fun applyZoom(percent: Int) {
        Store.updateZoom(percent)
        tabs.forEach { t -> t.webView?.settings?.textZoom = Store.zoom }
    }

    // ---------------------------------------------------------------- open in other apps

    /** Shows the system chooser with every app (except Prism) that can open [url]. */
    fun openInOtherApp(url: String? = active?.url) {
        if (url == null || url == Tab.START) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        val pm = act.packageManager
        @Suppress("DEPRECATION")
        val handlers = pm.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != act.packageName }
        if (handlers.isEmpty()) { snack = Snack("No other app can open this page"); return }
        val chooser = Intent.createChooser(intent, "Open with").apply {
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(android.content.ComponentName(act, act.javaClass)))
        }
        try { act.startActivity(chooser) } catch (e: ActivityNotFoundException) { snack = Snack("No other app can open this page") }
    }

    // ---------------------------------------------------------------- find in page

    fun find(q: String) { active?.webView?.findAllAsync(q) }
    fun findNext(forward: Boolean) { active?.webView?.findNext(forward) }
    fun closeFind() { findVisible = false; active?.webView?.clearMatches() }

    // ---------------------------------------------------------------- thumbnails

    fun captureThumbnail(tab: Tab) {
        val wv = tab.webView ?: return
        if (tab.isStart || wv.width == 0 || wv.height == 0) return
        try {
            val scale = 0.4f
            val bmp = createBitmap((wv.width * scale).toInt().coerceAtLeast(1), (wv.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.RGB_565)
            val c = Canvas(bmp)
            c.scale(scale, scale)
            // View.draw already applies the WebView's scroll offset, so no extra translate here.
            wv.draw(c)
            tab.thumbnail = bmp
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------------------------------- downloads

    fun startDownload(req: DownloadRequest) {
        pendingDownload = null
        if (req.url.startsWith("data:")) { saveDataUrl(req); return }
        if (req.url.startsWith("blob:")) { saveBlob(req); return }
        ensureLegacyStorage {
            Downloader.enqueue(req.url, req.fileName, req.mime, req.userAgent ?: baseUa, req.referer, req.contentDisposition)
            snack = Snack("Downloading ${req.fileName}", "View") { sheet = Sheet.DOWNLOADS }
        }
    }

    fun downloadMedia(m: MediaItem, variantUrl: String? = null, label: String? = null) {
        val tab = active
        val base = (m.title.ifBlank { tab?.title ?: "video" }).take(80)
        val name = Downloader.sanitize(if (label != null) "$base ($label).${m.ext}" else "$base.${m.ext}")
        ensureLegacyStorage {
            Downloader.enqueue(
                variantUrl ?: m.url, name, m.mime, m.headers["User-Agent"] ?: currentUa(tab), m.pageUrl,
                kind = if (m.kind == MediaKind.HLS) DKind.HLS else DKind.HTTP,
                extraHeaders = m.headers.filterKeys { it.equals("Origin", true) },
            )
            snack = Snack("Downloading $name", "View") { sheet = Sheet.DOWNLOADS }
        }
    }

    private fun currentUa(tab: Tab?) = if (tab?.desktop == true) desktopUa else baseUa

    private fun ensureLegacyStorage(then: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 29) { then(); return }
        host.requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { then() }
    }

    private fun saveDataUrl(req: DownloadRequest) {
        try {
            val comma = req.url.indexOf(',')
            val meta = req.url.substring(5, comma)
            val payload = req.url.substring(comma + 1)
            val bytes = if (meta.endsWith(";base64")) Base64.decode(payload, Base64.DEFAULT) else Uri.decode(payload).toByteArray()
            val mime = meta.substringBefore(';').ifBlank { "application/octet-stream" }
            val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
            val name = if (req.fileName.contains('.') && !req.fileName.endsWith(".bin")) req.fileName else "download.$ext"
            val tmp = File(act.cacheDir, "data_${System.nanoTime()}")
            tmp.writeBytes(bytes)
            app.prism.browser.download.Storage.publish(act, tmp, name, mime)
            tmp.delete()
            snack = Snack("Saved $name to Downloads")
        } catch (e: Exception) {
            snack = Snack("Couldn't save file")
        }
    }

    private fun saveBlob(req: DownloadRequest) {
        val wv = active?.webView ?: return
        val js = """
            (async function(){try{var r=await fetch('${req.url}');var b=await r.blob();
            var fr=new FileReader();fr.onload=function(){PrismBridge.blobData(fr.result, ${org.json.JSONObject.quote(req.fileName)});};
            fr.readAsDataURL(b);}catch(e){PrismBridge.blobData('', '');}})();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
        snack = Snack("Preparing ${req.fileName}…")
    }

    // ---------------------------------------------------------------- WebView factory

    private fun applyUa(wv: WebView, desktop: Boolean) {
        wv.settings.userAgentString = if (desktop) desktopUa else baseUa
        wv.settings.useWideViewPort = true
        wv.settings.loadWithOverviewMode = true
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            runCatching {
                val brands = listOf(
                    UserAgentMetadata.BrandVersion.Builder().setBrand("Chromium").setMajorVersion(chromeVersion.substringBefore('.')).setFullVersion(chromeVersion).build(),
                    UserAgentMetadata.BrandVersion.Builder().setBrand("Not_A Brand").setMajorVersion("24").setFullVersion("24.0.0.0").build(),
                )
                val md = UserAgentMetadata.Builder()
                    .setBrandVersionList(brands)
                    .setFullVersion(chromeVersion)
                    .setMobile(!desktop)
                    .setPlatform(if (desktop) "macOS" else "Android")
                    .build()
                WebSettingsCompat.setUserAgentMetadata(wv.settings, md)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createWebView(tab: Tab): WebView {
        val wv = WebView(act)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            setSupportZoom(true)
            // Fit responsive sites to the actual WebView width. Text zoom defaults to 85%.
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = !Store.popupBlocking
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setGeolocationEnabled(true)
            textZoom = Store.zoom
            cacheMode = WebSettings.LOAD_DEFAULT
            offscreenPreRaster = true
        }
        applyUa(wv, tab.desktop)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            runCatching { WebSettingsCompat.setRequestedWithHeaderOriginAllowList(wv.settings, emptySet()) }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            runCatching { WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, false) }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.addJavascriptInterface(Bridge(tab), "PrismBridge")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            runCatching { WebViewCompat.addDocumentStartJavaScript(wv, injectJs, setOf("*")) }
        }
        wv.webViewClient = Client(tab)
        wv.webChromeClient = Chrome(tab)
        wv.setDownloadListener { url, ua, cd, mime, len ->
            pendingDownload = DownloadRequest(url, ua, cd, mime, len, tab.webView?.url)
            if (tab.probation) { tab.probation = false; closeSilently(tab) }
        }
        wv.setOnLongClickListener { v ->
            val r = (v as WebView).hitTestResult
            when (r.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> { contextTarget = ContextTarget(r.extra, null, null); true }
                WebView.HitTestResult.IMAGE_TYPE -> { contextTarget = ContextTarget(null, r.extra, null); true }
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    val msg = android.os.Handler(android.os.Looper.getMainLooper()).obtainMessage()
                    v.requestFocusNodeHref(msg)
                    contextTarget = ContextTarget(msg.data.getString("url"), r.extra, msg.data.getString("title"))
                    true
                }
                else -> false
            }
        }
        var lastY = 0
        wv.setOnScrollChangeListener { _, _, y, _, oldY ->
            val dy = y - oldY
            if (y < 40) barCollapsed = false
            else if (dy > 12 && y - lastY > 60) { barCollapsed = true; lastY = y }
            else if (dy < -12) { barCollapsed = false; lastY = y }
        }
        @SuppressLint("ClickableViewAccessibility")
        wv.setOnTouchListener { _, _ -> touched(); false }
        wv.setFindListener { activeOrdinal, matches, _ ->
            tab.findMatches = matches
            tab.findIndex = if (matches == 0) 0 else activeOrdinal + 1
        }
        wv.setBackgroundColor(android.graphics.Color.WHITE)
        wv.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        if (Build.VERSION.SDK_INT >= 26) wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
        wv.isScrollbarFadingEnabled = true
        wv.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        return wv
    }

    private fun closeSilently(tab: Tab) {
        val idx = tabs.indexOf(tab)
        if (idx >= 0) close(tab) else destroy(tab)
    }

    // ---------------------------------------------------------------- JS bridge

    inner class Bridge(private val tab: Tab) {
        @JavascriptInterface
        fun css(hostName: String): String = runCatching { AdBlocker.cssFor(hostName) }.getOrDefault("")

        @JavascriptInterface
        fun genericCss(hostName: String, tokens: String): String =
            if (tokens.length > 200_000) "" else runCatching { AdBlocker.genericCss(hostName, tokens) }.getOrDefault("")

        @JavascriptInterface
        fun popupBlocking(): Boolean = Store.popupBlocking && AdBlocker.enabled && !AdBlocker.isSiteAllowlisted(tab.host)

        @JavascriptInterface
        fun popupBlocked(url: String) {
            act.runOnUiThread {
                AdBlocker.countBlocked(); tab.blocked++
                if (tab == active) snack = Snack("Pop-up blocked", if (url.startsWith("http")) "Open" else null) {
                    newTab(url, parent = tab)
                }
            }
        }

        @JavascriptInterface
        fun overlayBlocked(url: String) {
            act.runOnUiThread { AdBlocker.countBlocked(); tab.blocked++ }
        }

        @JavascriptInterface
        fun media(src: String, kind: String, title: String, pageUrl: String) {
            if (!Store.videoDetection) return
            val uri = Uri.parse(src)
            val k = MediaSniffer.classify(uri) ?: when (kind) {
                "audio" -> MediaKind.AUDIO
                else -> if (src.startsWith("http")) MediaKind.VIDEO else return
            }
            act.runOnUiThread { addMedia(tab, src, k, title, pageUrl, emptyMap()) }
        }

        @JavascriptInterface
        fun blobData(dataUrl: String, name: String) {
            act.runOnUiThread {
                if (dataUrl.isEmpty()) { snack = Snack("Couldn't save file"); return@runOnUiThread }
                saveDataUrl(DownloadRequest(dataUrl, null, null, null, 0, null).also { it.fileName = name })
            }
        }
    }

    private fun addMedia(tab: Tab, url: String, kind: MediaKind, title: String, pageUrl: String?, reqHeaders: Map<String, String>) {
        if (tab.media.any { it.url == url }) return
        if (kind == MediaKind.HLS && MediaSniffer.looksLikeVariantOf(tab.media, url)) return
        if (tab.media.size >= 40) return
        val headers = LinkedHashMap<String, String>()
        reqHeaders.forEach { (k, v) -> if (k.equals("Referer", true) || k.equals("Origin", true) || k.equals("User-Agent", true)) headers[k] = v }
        if (!headers.keys.any { it.equals("Referer", true) } && pageUrl != null) headers["Referer"] = pageUrl
        if (!headers.keys.any { it.equals("User-Agent", true) }) headers["User-Agent"] = currentUa(tab)
        tab.media += MediaItem(url, kind, title.ifBlank { tab.title }, pageUrl ?: tab.url, headers)
    }

    // ---------------------------------------------------------------- clients

    private fun installServiceWorkerFilter() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) return
        runCatching {
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(object : ServiceWorkerClientCompat() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? =
                    AdBlocker.intercept(request, request.url.host) {}
            })
        }
    }

    private inner class Client(private val tab: Tab) : WebViewClient() {

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (request.isForMainFrame) tab.host = request.url.host
            val blocked = AdBlocker.intercept(request, tab.host) { act.runOnUiThread { tab.blocked++ } }
            if (blocked != null) return blocked
            if (Store.videoDetection) {
                val kind = MediaSniffer.classify(request.url, request.requestHeaders["Accept"])
                if (kind != null) {
                    val page = act.runCatching { tab.url }.getOrNull()
                    act.runOnUiThread { addMedia(tab, request.url.toString(), kind, tab.title, page, request.requestHeaders) }
                }
            }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val scheme = uri.scheme?.lowercase() ?: return false
            val gesture = request.hasGesture()
            if (!request.isForMainFrame) {
                // sub-frame navigations to app schemes are almost always ad tricks
                return scheme != "http" && scheme != "https" && scheme != "about" && scheme != "data" && scheme != "blob"
            }

            if (tab.probation) {
                // first navigation of a new window: decide whether it is a pop-up ad
                if ((scheme == "http" || scheme == "https") && Store.popupBlocking &&
                    AdBlocker.shouldBlockNavigation(uri, tab.parent?.host)
                ) {
                    AdBlocker.countBlocked()
                    tab.parent?.let { it.blocked++ }
                    snack = Snack("Pop-up ad blocked")
                    act.runOnUiThread { closeSilently(tab) }
                    return true
                }
                reveal(tab)
            }

            when (scheme) {
                "http", "https" -> {
                    if (Store.popupBlocking && AdBlocker.shouldBlockNavigation(uri, tab.host) &&
                        !(UrlUtil.host(uri.toString()) == tab.host)
                    ) {
                        AdBlocker.countBlocked(); tab.blocked++
                        snack = Snack("Blocked redirect to ${uri.host}", "Allow once") {
                            tab.webView?.let { wv -> wv.webViewClient = AllowOnce(this, uri.toString()); wv.loadUrl(uri.toString()) }
                        }
                        return true
                    }
                    return false
                }
                "about", "data", "blob", "javascript" -> return false
                "intent" -> {
                    if (!gesture) { tab.blocked++; return true }
                    return try {
                        val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                        intent.addCategory(Intent.CATEGORY_BROWSABLE)
                        intent.component = null
                        intent.selector = null
                        if (intent.resolveActivity(act.packageManager) != null) act.startActivity(intent)
                        else intent.getStringExtra("browser_fallback_url")?.let { view.loadUrl(it) }
                        true
                    } catch (e: Exception) { true }
                }
                else -> {
                    // tel:, mailto:, market:, whatsapp:, etc. – only on a real tap
                    if (!gesture) { tab.blocked++; return true }
                    try {
                        act.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
                    } catch (e: ActivityNotFoundException) {
                        snack = Snack("No app can open this link")
                    }
                    return true
                }
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (url == "about:blank" && tab.isStart) return
            if (tab.probation && url != "about:blank") {
                val uri = Uri.parse(url)
                if (Store.popupBlocking && AdBlocker.shouldBlockNavigation(uri, tab.parent?.host)) {
                    AdBlocker.countBlocked(); snack = Snack("Pop-up ad blocked")
                    view.stopLoading(); closeSilently(tab); return
                }
                reveal(tab)
            }
            val newPage = UrlUtil.host(url) != UrlUtil.host(tab.url) || url.substringBefore('#') != tab.url.substringBefore('#')
            if (url != "about:blank") tab.url = url
            tab.host = UrlUtil.host(url)
            tab.loading = true
            tab.favicon = favicon
            if (newPage) { tab.media.clear(); tab.blocked = 0 }
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) view.evaluateJavascript(injectJs, null)
            if (tab == active) { barCollapsed = false; touched() }
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (url == "about:blank") {
                if (tab.probation) return
                tab.loading = false
                return
            }
            tab.loading = false
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
            view.title?.takeIf { it.isNotBlank() }?.let { tab.title = it }
            view.evaluateJavascript(injectJs, null) // no-op if already injected
            Store.addHistory(tab.title, url)
            if (tab.probation) reveal(tab)
            persist()
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            if (url == "about:blank") return
            tab.url = url
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            val host = Uri.parse(error.url).host ?: "this site"
            sitePrompt = SitePrompt(host, "SSL") { proceed -> if (proceed) handler.proceed() else handler.cancel() }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            val url = tab.url
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            view.destroy()
            tab.webView = null
            tab.pendingUrl = url.takeIf { it != Tab.START }
            if (tab == active) { webViewFor(tab); webViewEpoch++ }
            snack = Snack("Page crashed and was reloaded")
            return true
        }
    }

    /** One-shot client wrapper used by "Allow once" on a blocked redirect. */
    private inner class AllowOnce(private val tabClientOwner: Client, private val allowed: String) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (request.url.toString() == allowed) return false
            view.webViewClient = tabClientOwner
            return tabClientOwner.shouldOverrideUrlLoading(view, request)
        }
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            view.webViewClient = tabClientOwner
            tabClientOwner.onPageStarted(view, url, favicon)
        }
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
            tabClientOwner.shouldInterceptRequest(view, request)
    }

    private fun reveal(tab: Tab) {
        if (!tab.probation) return
        tab.probation = false
        if (!tabs.contains(tab)) {
            val parentIdx = tab.parent?.let { tabs.indexOf(it) } ?: -1
            tabs.add(if (parentIdx >= 0) parentIdx + 1 else tabs.size, tab)
        }
        select(tab)
    }

    private inner class Chrome(private val tab: Tab) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            tab.progress = newProgress
            if (newProgress == 100) tab.loading = false
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (!title.isNullOrBlank() && !tab.isStart && title != "about:blank") tab.title = title
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            tab.favicon = icon?.let { if (it.width > 64) it.scale(64, 64) else it }
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (Store.popupBlocking && !isUserGesture) {
                AdBlocker.countBlocked(); tab.blocked++
                snack = Snack("Pop-up blocked")
                return false
            }
            val popup = Tab(ids.getAndIncrement(), "about:blank", tab).also {
                it.desktop = tab.desktop
                it.probation = Store.popupBlocking
                it.title = "New window"
            }
            val wv = createWebView(popup)
            popup.webView = wv
            if (!popup.probation) {
                tabs.add((tabs.indexOf(tab) + 1).coerceAtMost(tabs.size), popup)
                select(popup)
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = wv
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            tabs.firstOrNull { it.webView == window }?.let { close(it) }
        }

        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean =
            host.showFileChooser(callback, params)

        override fun onPermissionRequest(request: PermissionRequest) {
            val origin = request.origin?.host ?: "This site"
            val wanted = request.resources.toList()
            val runtime = ArrayList<String>()
            val labels = ArrayList<String>()
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in wanted) { runtime += Manifest.permission.CAMERA; labels += "camera" }
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in wanted) { runtime += Manifest.permission.RECORD_AUDIO; labels += "microphone" }
            val drmOnly = runtime.isEmpty()
            if (drmOnly) { // protected media (streaming DRM) - no personal data involved
                act.runOnUiThread { request.grant(request.resources) }
                return
            }
            val key = origin + ":" + labels.joinToString()
            val grant = {
                host.requestPermissions(runtime.toTypedArray()) { ok ->
                    if (ok) request.grant(request.resources) else request.deny()
                }
            }
            sitePermissionMemory[key]?.let { if (it) grant() else request.deny(); return }
            act.runOnUiThread {
                sitePrompt = SitePrompt(origin, labels.joinToString(" and ")) { allow ->
                    sitePermissionMemory[key] = allow
                    if (allow) grant() else request.deny()
                }
            }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            sitePrompt = null
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            val h = Uri.parse(origin).host ?: origin
            val key = "$h:location"
            val go = { allow: Boolean ->
                if (allow) host.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) { ok ->
                    callback.invoke(origin, ok, false)
                } else callback.invoke(origin, false, false)
            }
            sitePermissionMemory[key]?.let { go(it); return }
            sitePrompt = SitePrompt(h, "location") { allow -> sitePermissionMemory[key] = allow; go(allow) }
        }

        private var customCallback: CustomViewCallback? = null

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            customCallback = callback
            fullscreen = true
            host.showFullscreen(view, callback)
        }

        override fun onHideCustomView() {
            fullscreen = false
            host.hideFullscreen()
            customCallback = null
        }

        override fun getDefaultVideoPoster(): Bitmap = createBitmap(1, 1)
    }
}
