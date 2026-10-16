package com.ibrowser.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ibrowser.adblock.AdBlocker
import com.ibrowser.data.Site
import com.ibrowser.data.Store
import com.ibrowser.data.hostOf
import com.ibrowser.download.DlKind
import com.ibrowser.download.DownloadManager
import com.ibrowser.media.MediaFound
import com.ibrowser.media.MediaKind
import com.ibrowser.media.MediaSniffer
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.lang.ref.WeakReference
import java.net.URLEncoder

/** Pending download confirmation (Safari's "Do you want to download…" sheet). */
data class DownloadRequest(
    val url: String,
    val fileName: String,
    val mime: String?,
    val size: Long,
    val userAgent: String?,
    val referer: String?,
    val kind: DlKind
)

/** Long-press context menu target. */
data class ContextTarget(val link: String?, val image: String?, val tabId: String)

data class ReaderContent(val title: String, val byline: String, val site: String, val html: String)

object Browser {
    private lateinit var app: Context
    private var activityRef: WeakReference<Activity>? = null
    val activity: Activity? get() = activityRef?.get()
    private val main = Handler(Looper.getMainLooper())

    val tabs = mutableStateListOf<BrowserTab>()
    var currentId by mutableStateOf<String?>(null)

    // ---- UI state ----
    var showTabs by mutableStateOf(false)
    var tabsPrivate by mutableStateOf(false)
    var editing by mutableStateOf(false)
    var showPageMenu by mutableStateOf(false)
    var showDownloads by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var showMedia by mutableStateOf(false)
    var showPrivacy by mutableStateOf(false)
    var showSiteSettings by mutableStateOf(false)
    var showLibrary by mutableStateOf(false)
    var showTabMenu by mutableStateOf(false)
    var startSection by mutableStateOf(0)      // 0 Start, 1 Bookmarks, 2 Reading List, 3 History
    var findMode by mutableStateOf(false)
    var findCount by mutableStateOf(0)
    var findIndex by mutableStateOf(0)
    var reader by mutableStateOf<ReaderContent?>(null)
    var pendingDownload by mutableStateOf<DownloadRequest?>(null)
    var contextTarget by mutableStateOf<ContextTarget?>(null)
    var customView by mutableStateOf<View?>(null)
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    var toolbarCollapsed by mutableStateOf(false)
    /** Last time the page scrolled; the glass samples its backdrop faster while scrolling. */
    @Volatile var lastScrollAt = 0L
    var toolbarHidden by mutableStateOf(false)
    var fileCallback: ValueCallback<Array<Uri>>? = null
    var fileChooserParams: WebChromeClient.FileChooserParams? = null
    var requestFileChooser: (() -> Unit)? = null

    private val injectJs: String by lazy { app.assets.open("inject.js").bufferedReader().readText() }
    private val readerJs: String by lazy { app.assets.open("reader.js").bufferedReader().readText() }
    private var privateProfileReady = false

    val current: BrowserTab? get() = tabs.firstOrNull { it.id == currentId }

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    fun attach(a: Activity) {
        activityRef = WeakReference(a)
    }

    // ------------------------------------------------------------ Tabs
    fun restore() {
        if (tabs.isNotEmpty()) return
        val p = app.getSharedPreferences("tabs", Context.MODE_PRIVATE)
        val urls = p.getString("urls", "")!!.split('\n').filter { it.isNotBlank() }
        urls.forEach { u -> tabs.add(BrowserTab(false, if (u == "-") "" else u)) }
        if (tabs.isEmpty()) tabs.add(BrowserTab(false))
        currentId = tabs.getOrNull(p.getInt("index", 0).coerceIn(0, tabs.size - 1))?.id ?: tabs.first().id
    }

    fun persistTabs() {
        val normal = tabs.filter { !it.isPrivate }
        val urls = normal.joinToString("\n") { it.url.ifBlank { "-" } }
        val idx = normal.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        app.getSharedPreferences("tabs", Context.MODE_PRIVATE).edit().putString("urls", urls).putInt("index", idx).apply()
    }

    fun newTab(url: String = "", private: Boolean = current?.isPrivate ?: false, parent: String? = null, select: Boolean = true): BrowserTab {
        val t = BrowserTab(private, url, parent)
        val idx = tabs.indexOfFirst { it.id == (parent ?: currentId) }
        if (idx >= 0) tabs.add(idx + 1, t) else tabs.add(t)
        if (select) select(t.id)
        return t
    }

    fun select(id: String) {
        if (id != currentId) captureThumbnail(current)
        currentId = id
        current?.lastAccess = System.currentTimeMillis()
        toolbarCollapsed = false
        findMode = false
    }

    fun close(id: String) {
        val i = tabs.indexOfFirst { it.id == id }
        if (i < 0) return
        val t = tabs[i]
        tabs.removeAt(i)
        t.webView?.let { wv ->
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.stopLoading(); wv.destroy()
        }
        t.webView = null
        if (currentId == id) {
            val sameGroup = tabs.filter { it.isPrivate == t.isPrivate }
            val next = tabs.firstOrNull { it.id == t.parentId } ?: sameGroup.getOrNull((i - 1).coerceAtLeast(0))
                ?: sameGroup.firstOrNull()
            if (next != null) select(next.id)
            else {
                val nt = newTab(private = t.isPrivate)
                select(nt.id)
            }
        }
        if (t.isPrivate && tabs.none { it.isPrivate }) clearPrivateData()
    }

    fun closeAll(private: Boolean) {
        tabs.filter { it.isPrivate == private }.map { it.id }.forEach { close(it) }
    }

    fun switchRelative(delta: Int) {
        val cur = current ?: return
        val group = tabs.filter { it.isPrivate == cur.isPrivate }
        val i = group.indexOf(cur)
        val n = group.getOrNull(i + delta) ?: return
        select(n.id)
    }

    private fun clearPrivateData() {
        if (privateProfileReady && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching {
                val store = ProfileStore.getInstance()
                store.getProfile("private")?.let { prof ->
                    prof.cookieManager.removeAllCookies(null)
                    prof.webStorage.deleteAllData()
                }
            }
        }
    }

    // ------------------------------------------------------------ Navigation
    fun resolveInput(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return ""
        val looksUrl = Regex("^(https?|file|about|data|javascript):", RegexOption.IGNORE_CASE).containsMatchIn(t) ||
            (!t.contains(' ') && Regex("^[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/.*)?$").matches(t)) ||
            t.startsWith("localhost")
        return when {
            Regex("^(https?|file|about|data|javascript):", RegexOption.IGNORE_CASE).containsMatchIn(t) -> t
            looksUrl -> "https://$t"
            else -> Store.settings.engine.query.replace("%s", URLEncoder.encode(t, "UTF-8"))
        }
    }

    fun load(input: String, tab: BrowserTab? = current) {
        val t = tab ?: newTab()
        val url = resolveInput(input)
        if (url.isEmpty()) return
        t.url = url
        t.pageUrl = url
        val wv = t.webView
        if (wv != null) wv.loadUrl(url) else t.pendingUrl = url
        editing = false
    }

    fun goHome() {
        val t = current ?: return
        t.webView?.let { it.stopLoading(); it.loadUrl("about:blank"); it.clearHistory() }
        t.url = ""; t.title = ""; t.pageUrl = ""; t.media.clear(); t.blocked = 0
    }

    fun back() {
        val t = current ?: return
        val wv = t.webView
        if (wv != null && wv.canGoBack()) {
            val list = wv.copyBackForwardList()
            if (list.currentIndex > 0 && list.getItemAtIndex(list.currentIndex - 1).url == "about:blank") {
                goHome()
            } else wv.goBack()
        } else if (!t.isStartPage) goHome()
    }

    fun forward() { current?.webView?.goForward() }
    fun reload() { current?.webView?.reload() }
    fun stop() { current?.webView?.stopLoading() }

    /** Called by the system back button. Returns false if nothing consumed it. */
    fun handleBack(): Boolean {
        when {
            customView != null -> { exitFullscreen(); return true }
            contextTarget != null -> { contextTarget = null; return true }
            pendingDownload != null -> { pendingDownload = null; return true }
            showPageMenu -> { showPageMenu = false; return true }
            showMedia -> { showMedia = false; return true }
            showPrivacy -> { showPrivacy = false; return true }
            showSiteSettings -> { showSiteSettings = false; return true }
            showLibrary -> { showLibrary = false; return true }
            showTabMenu -> { showTabMenu = false; return true }
            showSettings -> { showSettings = false; return true }
            showDownloads -> { showDownloads = false; return true }
            reader != null -> { reader = null; return true }
            findMode -> { closeFind(); return true }
            editing -> { editing = false; return true }
            showTabs -> { showTabs = false; return true }
        }
        val t = current ?: return false
        if (t.webView?.canGoBack() == true) { back(); return true }
        if (!t.isStartPage) { goHome(); return true }
        if (t.parentId != null && tabs.any { it.id == t.parentId }) { close(t.id); return true }
        return false
    }

    // ------------------------------------------------------------ WebView
    @SuppressLint("SetJavaScriptEnabled")
    fun webViewFor(t: BrowserTab, ctx: Context): WebView {
        t.webView?.let { return it }
        val wv = WebView(ctx)
        if (t.isPrivate && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching {
                ProfileStore.getInstance().getOrCreateProfile("private")
                WebViewCompat.setProfile(wv, "private")
                privateProfileReady = true
            }
        }
        configure(wv, t)
        t.webView = wv
        val pend = t.pendingUrl
        t.pendingUrl = null
        if (!pend.isNullOrBlank()) wv.loadUrl(pend)
        return wv
    }

    fun mobileUA(wv: WebView): String =
        WebSettings.getDefaultUserAgent(wv.context).replace("; wv", "").replace(Regex("Version/[\\d.]+ "), "")

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"

    fun applySettings(wv: WebView, t: BrowserTab) {
        val s = wv.settings
        val st = Store.settings
        s.javaScriptEnabled = st.javascript
        s.javaScriptCanOpenWindowsAutomatically = !st.blockPopups
        s.userAgentString = if (t.desktop) DESKTOP_UA else mobileUA(wv)
        s.textZoom = t.textZoom
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, st.darkWeb)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun configure(wv: WebView, t: BrowserTab) {
        val s = wv.settings
        s.domStorageEnabled = true
        @Suppress("DEPRECATION")
        s.databaseEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.setSupportZoom(true)
        s.setSupportMultipleWindows(true)
        s.mediaPlaybackRequiresUserGesture = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.allowFileAccess = false
        if (t.isPrivate) s.cacheMode = WebSettings.LOAD_NO_CACHE
        t.desktop = Store.settings.desktopDefault
        t.textZoom = Store.settings.textZoom
        applySettings(wv, t)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, !t.isPrivate)

        wv.addJavascriptInterface(JsBridge(t), "iBrowserNative")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            runCatching { WebViewCompat.addDocumentStartJavaScript(wv, injectJs, setOf("*")) }
        }

        wv.setOnScrollChangeListener { _, _, y, _, oldY ->
            if (t.id != currentId) return@setOnScrollChangeListener
            lastScrollAt = System.currentTimeMillis()
            val dy = y - oldY
            if (y <= 4) toolbarCollapsed = false
            else if (dy > 12) toolbarCollapsed = true
            else if (dy < -24) toolbarCollapsed = false
        }

        wv.setFindListener { active, count, done ->
            if (done) { findCount = count; findIndex = if (count == 0) 0 else active + 1 }
        }

        wv.setOnLongClickListener { v ->
            val r = (v as WebView).hitTestResult
            when (r.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> { contextTarget = ContextTarget(r.extra, null, t.id); true }
                WebView.HitTestResult.IMAGE_TYPE -> { contextTarget = ContextTarget(null, r.extra, t.id); true }
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    val img = r.extra
                    val msg = Handler(Looper.getMainLooper()) { m ->
                        val href = m.data.getString("url")
                        contextTarget = ContextTarget(href?.ifBlank { null }, img, t.id)
                        true
                    }.obtainMessage()
                    v.requestFocusNodeHref(msg)
                    true
                }
                else -> false
            }
        }

        wv.setDownloadListener { url, ua, cd, mime, len ->
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                toast("This file is generated by the page and can't be downloaded directly")
                return@setDownloadListener
            }
            requestDownload(url, DownloadManager.guessName(url, cd, mime), mime, len, ua, t.url)
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                if (request.isForMainFrame || !url.startsWith("http")) return null
                val accept = request.requestHeaders["Accept"]
                // media sniffing
                if (Store.settings.sniffMedia) {
                    MediaSniffer.classify(url, null)?.let { kind -> main.post { addMedia(t, url, kind, null, t.title, t.url) } }
                }
                // ad / tracker blocking
                val page = t.pageUrl
                if (Store.settings.adBlock && hostOf(page) !in Store.settings.disabledAdSites &&
                    AdBlocker.instance.shouldBlock(url, page, accept)
                ) {
                    main.post {
                        t.blocked++
                        Store.settings.totalBlocked++
                    }
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val scheme = request.url.scheme?.lowercase() ?: return false
                if (scheme == "http" || scheme == "https" || scheme == "about" || scheme == "data" || scheme == "javascript") {
                    // Treat direct stream manifests as downloads
                    return false
                }
                return openExternal(url, view)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (url == "about:blank") return
                if (hostOf(url) != hostOf(t.pageUrl) || url != t.pageUrl) {
                    if (url.substringBefore('#') != t.pageUrl.substringBefore('#')) {
                        t.media.clear(); t.blocked = 0
                    }
                }
                t.url = url; t.pageUrl = url
                t.loading = true
                t.favicon = favicon
                t.themeColor = null
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) view.evaluateJavascript(injectJs, null)
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) view.evaluateJavascript(injectJs, null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                t.loading = false
                t.canGoBack = view.canGoBack()
                t.canGoForward = view.canGoForward()
                if (url == "about:blank") return
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) view.evaluateJavascript(injectJs, null)
                if (!t.isPrivate) Store.recordVisit(url, view.title ?: "")
                view.evaluateJavascript(
                    "(function(){var m=document.querySelector('meta[name=theme-color]');return m?m.content:''})()"
                ) { c -> t.themeColor = parseColor(c.trim('"')) }
                Store.settings.save()
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                if (url == "about:blank") { t.canGoBack = view.canGoBack(); return }
                t.url = url; t.pageUrl = url
                t.canGoBack = view.canGoBack()
                t.canGoForward = view.canGoForward()
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                toast("This connection is not private (certificate error)")
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                t.progress = newProgress
                if (newProgress >= 100) t.loading = false
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                t.title = title ?: ""
                if (!t.isPrivate && !t.url.isBlank()) Store.history.update(t.url) { it.copy(title = t.title) }
            }

            override fun onReceivedIcon(view: WebView, icon: Bitmap?) { t.favicon = icon }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (Store.settings.blockPopups && !isUserGesture) {
                    toast("Pop-up blocked")
                    return false
                }
                val nt = newTab(private = t.isPrivate, parent = t.id)
                val nwv = webViewFor(nt, view.context)
                (resultMsg.obj as WebView.WebViewTransport).webView = nwv
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                tabs.firstOrNull { it.webView === window }?.let { close(it.id) }
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                customView = view
                customCallback = callback
            }

            override fun onHideCustomView() { exitFullscreen() }

            override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                Browser.fileChooserParams = fileChooserParams
                val r = requestFileChooser
                if (r == null) { filePathCallback.onReceiveValue(null); fileCallback = null; return false }
                r()
                return true
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: android.webkit.GeolocationPermissions.Callback) {
                callback.invoke(origin, false, false)
            }
        }
    }

    fun exitFullscreen() {
        customCallback?.onCustomViewHidden()
        customCallback = null
        customView = null
    }

    private fun parseColor(c: String): Int? = runCatching {
        if (c.startsWith("#")) android.graphics.Color.parseColor(if (c.length == 4) "#" + c.drop(1).map { "$it$it" }.joinToString("") else c)
        else null
    }.getOrNull()

    fun openExternal(url: String, view: WebView? = null): Boolean {
        val a = activity ?: return true
        try {
            val intent = if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.component = null
            intent.selector = null
            try {
                a.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                val fb = intent.getStringExtra("browser_fallback_url")
                if (fb != null && view != null) view.loadUrl(fb)
                else if (intent.`package` != null) {
                    a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${intent.`package`}")))
                }
            }
        } catch (e: Exception) {
            toast("Can't open this link")
        }
        return true
    }

    // ------------------------------------------------------------ Media sniffing
    fun addMedia(t: BrowserTab, url: String, kind: MediaKind, mime: String?, title: String, page: String) {
        if (t.media.any { it.url == url }) return
        // prefer master playlists: skip variant playlists when their master is known later (keep all, UI sorts)
        val m = MediaFound(url, kind, title, page, mime)
        t.media.add(m)
        MediaSniffer.enrich(m, t.webView?.settings?.userAgentString)
    }

    class JsBridge(private val t: BrowserTab) {
        @JavascriptInterface fun css(host: String): String =
            if (Store.settings.adBlock && Store.settings.cosmetic && hostOf("https://$host") !in Store.settings.disabledAdSites)
                AdBlocker.instance.cssFor(host) else ""

        @JavascriptInterface fun cosmeticEnabled(): Boolean = Store.settings.adBlock && Store.settings.cosmetic &&
            hostOf(t.pageUrl) !in Store.settings.disabledAdSites

        @JavascriptInterface fun sniffEnabled(): Boolean = Store.settings.sniffMedia

        @JavascriptInterface fun media(url: String, ct: String, from: String, title: String, page: String) {
            val kind = MediaSniffer.classify(url, ct) ?: if (ct.startsWith("video")) MediaKind.VIDEO else if (ct.startsWith("audio")) MediaKind.AUDIO else return
            main.post { addMedia(t, url, kind, ct.ifBlank { null }, title, page) }
        }
    }

    // ------------------------------------------------------------ Downloads
    fun requestDownload(url: String, name: String, mime: String?, size: Long, ua: String?, referer: String?) {
        val kind = DownloadManager.kindFor(url, mime)
        val req = DownloadRequest(url, name, mime, size, ua, referer, kind)
        if (Store.settings.askBeforeDownload || kind != DlKind.FILE) pendingDownload = req
        else startDownload(req)
    }

    fun startDownload(r: DownloadRequest, variant: String? = null, audio: String? = null, threads: Int = Store.settings.threads) {
        DownloadManager.enqueue(r.url, r.fileName, r.mime, r.userAgent, r.referer, r.kind, variant, audio, threads)
        pendingDownload = null
        toast("Downloading ${r.fileName}")
    }

    fun downloadMedia(m: MediaFound, t: BrowserTab?) {
        val base = (t?.title?.ifBlank { null } ?: m.pageTitle.ifBlank { hostOf(m.pageUrl) })
            .replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "video" }
        val kind = when (m.kind) {
            MediaKind.HLS -> DlKind.HLS
            MediaKind.DASH -> DlKind.DASH
            else -> DlKind.FILE
        }
        val name = when (kind) {
            DlKind.FILE -> "$base.${m.extension}"
            else -> "$base.mp4"
        }
        pendingDownload = DownloadRequest(m.url, name, m.mime, m.size, t?.webView?.settings?.userAgentString, m.pageUrl, kind)
    }

    // ------------------------------------------------------------ Page actions
    fun share(url: String, title: String? = null) {
        val a = activity ?: return
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        title?.let { i.putExtra(Intent.EXTRA_SUBJECT, it) }
        a.startActivity(Intent.createChooser(i, null))
    }

    fun copy(text: String) {
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("link", text))
        toast("Copied")
    }

    fun toggleDesktop() {
        val t = current ?: return
        t.desktop = !t.desktop
        t.webView?.let { applySettings(it, t); it.reload() }
    }

    fun setTextZoom(z: Int) {
        val t = current ?: return
        t.textZoom = z.coerceIn(50, 300)
        t.webView?.settings?.textZoom = t.textZoom
    }

    fun startFind() { findMode = true; findCount = 0; findIndex = 0 }
    fun find(q: String) { current?.webView?.findAllAsync(q) }
    fun findNext(forward: Boolean) { current?.webView?.findNext(forward) }
    fun closeFind() { findMode = false; current?.webView?.clearMatches() }

    fun openReader() {
        val wv = current?.webView ?: return
        wv.evaluateJavascript(readerJs) { res ->
            runCatching {
                val json = JSONObject(org.json.JSONTokener(res).nextValue() as String)
                if (json.optBoolean("ok")) {
                    reader = ReaderContent(json.optString("title"), json.optString("byline"), json.optString("site"), json.optString("html"))
                } else toast("Reader isn't available for this page")
            }.onFailure { toast("Reader isn't available for this page") }
        }
    }

    fun print() {
        val a = activity ?: return
        val wv = current?.webView ?: return
        val pm = a.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print(current?.displayTitle ?: "Page", wv.createPrintDocumentAdapter(current?.displayTitle ?: "Page"), PrintAttributes.Builder().build())
    }

    fun translate() {
        val t = current ?: return
        load("https://translate.google.com/translate?sl=auto&tl=en&u=" + URLEncoder.encode(t.url, "UTF-8"))
    }

    fun addBookmark() {
        val t = current ?: return
        if (t.isStartPage) return
        Store.bookmarks.add(Site(t.url, t.displayTitle)); toast("Bookmark added")
    }

    fun addFavorite() {
        val t = current ?: return
        if (t.isStartPage) return
        Store.favorites.add(Site(t.url, t.displayTitle), front = false); toast("Added to Favorites")
    }

    fun addReadingList(url: String? = null, title: String? = null) {
        val t = current
        val u = url ?: t?.url ?: return
        if (u.isBlank()) return
        Store.readingList.add(Site(u, title ?: t?.displayTitle ?: u)); toast("Added to Reading List")
    }

    fun toggleSiteAdBlock() {
        val h = current?.host ?: return
        val s = Store.settings
        s.disabledAdSites = if (h in s.disabledAdSites) s.disabledAdSites - h else s.disabledAdSites + h
        s.save()
        reload()
    }

    fun captureThumbnail(t: BrowserTab? = current) {
        val wv = t?.webView ?: return
        if (wv.width <= 0 || wv.height <= 0) return
        runCatching {
            val scale = 0.4f
            val bmp = Bitmap.createBitmap((wv.width * scale).toInt(), (wv.height * scale).toInt(), Bitmap.Config.RGB_565)
            val c = Canvas(bmp)
            c.scale(scale, scale)
            c.translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat())
            wv.draw(c)
            t.thumbnail = bmp
        }
    }

    fun toast(msg: String) {
        main.post { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() }
    }

    fun clearHistoryAndData() {
        Store.history.clear()
        CookieManager.getInstance().removeAllCookies(null)
        android.webkit.WebStorage.getInstance().deleteAllData()
        tabs.forEach { it.webView?.clearCache(true) }
        toast("History and website data cleared")
    }
}
