package app.prism.browser.adblock

import app.prism.browser.core.Obs
import app.prism.browser.core.ObsList
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import app.prism.browser.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

object AdBlocker {

    data class FilterList(val id: String, val name: String, val url: String, val defaultOn: Boolean, val bundled: Boolean)

    val LISTS = listOf(
        FilterList("easylist", "EasyList", "https://easylist.to/easylist/easylist.txt", true, true),
        FilterList("easyprivacy", "EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt", true, true),
        FilterList("ubo_filters", "uBlock filters", "https://ublockorigin.github.io/uAssets/filters/filters.txt", true, true),
        FilterList("ubo_badware", "uBlock badware risks", "https://ublockorigin.github.io/uAssets/filters/badware.txt", true, true),
        FilterList("ubo_privacy", "uBlock privacy", "https://ublockorigin.github.io/uAssets/filters/privacy.txt", true, true),
        FilterList("peterlowe", "Peter Lowe's ad servers", "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=adblockplus&showintro=0&mimetype=plaintext", true, true),
        FilterList("adguard_mobile", "AdGuard Mobile Ads", "https://filters.adtidy.org/extension/ublock/filters/11.txt", true, true),
        FilterList("cookies", "Cookie banners (Fanboy)", "https://secure.fanboy.co.nz/fanboy-cookiemonster.txt", true, true),
        FilterList("adguard_base", "AdGuard Base (extra strength)", "https://filters.adtidy.org/extension/ublock/filters/2_without_easylist.txt", false, false),
        FilterList("ubo_annoyances", "Annoyances (pop-overs, newsletters)", "https://ublockorigin.github.io/uAssets/filters/annoyances-others.txt", false, false),
        FilterList("stevenblack", "StevenBlack hosts (malware + ads)", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", false, false),
    )

    private const val TAG = "AdBlocker"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var appContext: Context

    @Volatile private var engine: FilterEngine = FilterEngine()

    var ready by Obs(false); private set
    var ruleCount by Obs(0); private set
    var blockedToday by Obs(0L); private set
    var lastUpdated by Obs(0L); private set
    var updating by Obs(false); private set

    val enabled get() = Store.adblockEnabled

    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        blockedToday = Store.blockedToday()
        lastUpdated = Store.filtersUpdatedAt
        scope.launch { rebuild() }
        // weekly auto-update
        if (System.currentTimeMillis() - lastUpdated > 7L * 24 * 3600 * 1000) {
            scope.launch { kotlinx.coroutines.delay(15_000); update() }
        }
    }

    private fun listDir() = File(appContext.filesDir, "filters").apply { mkdirs() }

    fun isListOn(id: String) = Store.isFilterOn(id, LISTS.first { it.id == id }.defaultOn)

    fun setListOn(id: String, on: Boolean) {
        Store.setFilterOn(id, on)
        scope.launch {
            val list = LISTS.first { it.id == id }
            if (on && !File(listDir(), "$id.txt").exists() && !list.bundled) download(list)
            rebuild()
        }
    }

    private fun readList(list: FilterList): String? {
        val f = File(listDir(), "${list.id}.txt")
        if (f.exists() && f.length() > 0) return f.readText()
        if (list.bundled) return try {
            appContext.assets.open("filters/${list.id}.txt").bufferedReader().readText()
        } catch (e: Exception) { null }
        return null
    }

    private fun rebuild() {
        val t0 = System.currentTimeMillis()
        val e = FilterEngine()
        try {
            e.addList(appContext.assets.open("builtin.txt").bufferedReader().readText())
        } catch (_: Exception) {}
        for (list in LISTS) {
            if (!isListOn(list.id)) continue
            val text = readList(list) ?: continue
            if (list.id == "stevenblack") e.addHosts(text) else e.addList(text)
        }
        Store.userBlockedHosts().forEach { e.addList("||$it^") }
        Store.allowlistedSites().forEach { e.addList("@@||$it^\$document") }
        engine = e
        ruleCount = e.ruleCount
        ready = true
        Log.i(TAG, "filters compiled: ${e.ruleCount} rules in ${System.currentTimeMillis() - t0}ms")
    }

    fun reload() = scope.launch { rebuild() }

    fun update() {
        if (updating) return
        updating = true
        scope.launch(Dispatchers.IO) {
            try {
                LISTS.filter { isListOn(it.id) }.forEach { download(it) }
                Store.filtersUpdatedAt = System.currentTimeMillis()
                lastUpdated = Store.filtersUpdatedAt
                rebuild()
            } finally {
                updating = false
            }
        }
    }

    private fun download(list: FilterList) {
        try {
            http.newCall(Request.Builder().url(list.url).build()).execute().use { r ->
                if (!r.isSuccessful) return
                val body = r.body?.string() ?: return
                if (body.length < 200) return
                val tmp = File(listDir(), "${list.id}.tmp")
                tmp.writeText(body)
                tmp.renameTo(File(listDir(), "${list.id}.txt"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "update ${list.id} failed: $e")
        }
    }

    // ------------------------------------------------------------------ runtime

    private fun empty(mime: String = "text/plain") =
        WebResourceResponse(mime, "utf-8", ByteArrayInputStream(ByteArray(0)))

    fun isSiteAllowlisted(host: String?) = host != null && Store.allowlistedSites().any { host == it || host.endsWith(".$it") }

    /** Called from shouldInterceptRequest. Returns an empty response if blocked. */
    fun intercept(request: WebResourceRequest, pageHost: String?, onBlocked: () -> Unit): WebResourceResponse? {
        if (!enabled || !ready) return null
        val uri = request.url
        val scheme = uri.scheme ?: return null
        if (scheme != "http" && scheme != "https" && scheme != "ws" && scheme != "wss") return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (request.isForMainFrame) return null
        if (isSiteAllowlisted(pageHost)) return null
        val type = guessType(request, uri)
        val url = uri.toString()
        if (engine.shouldBlock(url, host, pageHost, type)) {
            countBlocked()
            onBlocked()
            return when (type) {
                FilterEngine.TYPE_SCRIPT -> empty("application/javascript")
                FilterEngine.TYPE_STYLESHEET -> empty("text/css")
                FilterEngine.TYPE_SUBDOCUMENT -> empty("text/html")
                else -> empty()
            }
        }
        return null
    }

    fun shouldBlockNavigation(url: Uri, openerHost: String?): Boolean {
        if (!enabled || !ready) return false
        val host = url.host?.lowercase(Locale.ROOT) ?: return false
        if (isSiteAllowlisted(openerHost)) return false
        return engine.isPopupBlocked(url.toString(), host, openerHost)
    }

    fun cssFor(host: String): String {
        if (!enabled || !ready || isSiteAllowlisted(host)) return ""
        return engine.cssFor(host.lowercase(Locale.ROOT))
    }

    fun genericCss(host: String, tokens: String): String {
        if (!enabled || !ready || isSiteAllowlisted(host)) return ""
        return engine.genericFor(host.lowercase(Locale.ROOT), tokens)
    }

    fun countBlocked() {
        blockedToday = Store.incBlocked()
    }

    private fun guessType(request: WebResourceRequest, uri: Uri): Int {
        val accept = request.requestHeaders["Accept"] ?: request.requestHeaders["accept"] ?: ""
        val path = (uri.path ?: "").lowercase(Locale.ROOT)
        return when {
            uri.scheme == "ws" || uri.scheme == "wss" -> FilterEngine.TYPE_WEBSOCKET
            accept.startsWith("text/html") -> FilterEngine.TYPE_SUBDOCUMENT
            accept.startsWith("text/css") || path.endsWith(".css") -> FilterEngine.TYPE_STYLESHEET
            accept.startsWith("image/") || IMG.any { path.endsWith(it) } -> FilterEngine.TYPE_IMAGE
            path.endsWith(".js") || path.endsWith(".mjs") -> FilterEngine.TYPE_SCRIPT
            MEDIA.any { path.endsWith(it) } -> FilterEngine.TYPE_MEDIA
            FONT.any { path.endsWith(it) } -> FilterEngine.TYPE_FONT
            accept.contains("json") || request.requestHeaders.containsKey("X-Requested-With") -> FilterEngine.TYPE_XHR
            accept == "*/*" -> FilterEngine.TYPE_SCRIPT or FilterEngine.TYPE_XHR or FilterEngine.TYPE_OTHER
            else -> FilterEngine.TYPE_OTHER or FilterEngine.TYPE_XHR
        }
    }

    private val IMG = listOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico", ".avif", ".bmp")
    private val MEDIA = listOf(".mp4", ".webm", ".m3u8", ".mp3", ".m4a", ".ts", ".mpd", ".ogg")
    private val FONT = listOf(".woff", ".woff2", ".ttf", ".otf", ".eot")
}
