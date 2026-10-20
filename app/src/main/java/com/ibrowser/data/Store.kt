package com.ibrowser.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Site(
    val url: String,
    val title: String,
    val time: Long = System.currentTimeMillis(),
    val visits: Int = 1,
    val read: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("url", url).put("title", title).put("time", time)
        .put("visits", visits).put("read", read)

    companion object {
        fun fromJson(o: JSONObject) = Site(
            o.optString("url"), o.optString("title"), o.optLong("time"),
            o.optInt("visits", 1), o.optBoolean("read", false)
        )
    }
}

/** ARC = Arc Search style (tabs · address capsule · ^ actions); the others are Safari-style. */
enum class ToolbarLayout { ARC, COMPACT, BOTTOM, TOP }

enum class SearchEngine(val label: String, val query: String, val suggest: String?) {
    GOOGLE("Google", "https://www.google.com/search?q=%s", "https://suggestqueries.google.com/complete/search?client=firefox&q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/ac/?type=list&q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s", "https://api.bing.com/osjson.aspx?query=%s"),
    BRAVE("Brave", "https://search.brave.com/search?q=%s", null),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=%s", null),
    YAHOO("Yahoo", "https://search.yahoo.com/search?p=%s", null)
}

/** Observable app settings, persisted in SharedPreferences. */
const val DEFAULT_TEXT_ZOOM = 90

class Settings(private val p: SharedPreferences) {
    // Arc Search interface is the default; existing installs are switched to it once
    var layout by mutableStateOf(
        if (!p.getBoolean("arcMigrated", false)) ToolbarLayout.ARC
        else runCatching { ToolbarLayout.valueOf(p.getString("layout", "ARC")!!) }.getOrDefault(ToolbarLayout.ARC)
    )
    /** Archive tabs not opened for this many hours (0 = never). Arc's default is 24 h. */
    var archiveAfterHours by mutableStateOf(p.getInt("archiveHours", 24))
    var pinchToSummarize by mutableStateOf(p.getBoolean("pinchSummary", true))
    var engine by mutableStateOf(SearchEngine.valueOf(p.getString("engine", "GOOGLE")!!))
    var adBlock by mutableStateOf(p.getBoolean("adBlock", true))
    var cosmetic by mutableStateOf(p.getBoolean("cosmetic", true))
    var blockPopups by mutableStateOf(p.getBoolean("blockPopups", true))
    var javascript by mutableStateOf(p.getBoolean("javascript", true))
    var desktopDefault by mutableStateOf(p.getBoolean("desktop", false))
    var sniffMedia by mutableStateOf(p.getBoolean("sniff", true))
    // 16 connections by default; users still on the old default of 8 are moved up once
    var threads by mutableStateOf(
        if (!p.getBoolean("threadsMigrated", false) && p.getInt("threads", 8) == 8) 16 else p.getInt("threads", 16)
    )
    var downloadDir by mutableStateOf(p.getString("downloadDir", "") ?: "")
    // bottom bar appearance
    var barLiquidGlass by mutableStateOf(p.getBoolean("barLiquid", true))
    var barTransparency by mutableStateOf(p.getFloat("barTransparency", 0.65f))   // 0 = solid, 1 = clear
    var barAdaptiveTint by mutableStateOf(p.getBoolean("barAdaptive", true))
    var autoHideToolbar by mutableStateOf(p.getBoolean("autoHide", true))
    var autoHideSeconds by mutableStateOf(p.getInt("autoHideSec", 3))
    var maxParallel by mutableStateOf(p.getInt("maxParallel", 3))
    var askBeforeDownload by mutableStateOf(p.getBoolean("askDl", true))
    var darkWeb by mutableStateOf(p.getBoolean("darkWeb", false))
    var showFavorites by mutableStateOf(p.getBoolean("showFav", true))
    var showFrequent by mutableStateOf(p.getBoolean("showFreq", true))
    var showPrivacy by mutableStateOf(p.getBoolean("showPriv", true))
    var showReading by mutableStateOf(p.getBoolean("showRead", true))
    var landscapeTabBar by mutableStateOf(p.getBoolean("landTabs", true))
    var lastFilterUpdate by mutableStateOf(p.getLong("filterUpdate", 0L))
    var totalBlocked by mutableStateOf(p.getLong("totalBlocked", 0L))
    // default page text size is 90%; existing installs are moved to 90% once
    var textZoom by mutableStateOf(if (!p.getBoolean("zoom90", false)) DEFAULT_TEXT_ZOOM else p.getInt("textZoom", DEFAULT_TEXT_ZOOM))
    var disabledAdSites by mutableStateOf(p.getStringSet("noAds", emptySet())!!.toSet())

    fun save() {
        p.edit()
            .putString("layout", layout.name)
            .putBoolean("arcMigrated", true)
            .putInt("archiveHours", archiveAfterHours)
            .putBoolean("pinchSummary", pinchToSummarize)
            .putString("engine", engine.name)
            .putBoolean("adBlock", adBlock)
            .putBoolean("cosmetic", cosmetic)
            .putBoolean("blockPopups", blockPopups)
            .putBoolean("javascript", javascript)
            .putBoolean("desktop", desktopDefault)
            .putBoolean("sniff", sniffMedia)
            .putInt("threads", threads)
            .putBoolean("threadsMigrated", true)
            .putString("downloadDir", downloadDir)
            .putBoolean("barLiquid", barLiquidGlass)
            .putFloat("barTransparency", barTransparency)
            .putBoolean("barAdaptive", barAdaptiveTint)
            .putBoolean("autoHide", autoHideToolbar)
            .putInt("autoHideSec", autoHideSeconds)
            .putInt("maxParallel", maxParallel)
            .putBoolean("askDl", askBeforeDownload)
            .putBoolean("darkWeb", darkWeb)
            .putBoolean("showFav", showFavorites)
            .putBoolean("showFreq", showFrequent)
            .putBoolean("showPriv", showPrivacy)
            .putBoolean("showRead", showReading)
            .putBoolean("landTabs", landscapeTabBar)
            .putLong("filterUpdate", lastFilterUpdate)
            .putLong("totalBlocked", totalBlocked)
            .putInt("textZoom", textZoom)
            .putBoolean("zoom90", true)
            .putStringSet("noAds", disabledAdSites)
            .apply()
    }
}

/** A JSON-file backed observable list of sites. */
class SiteList(private val file: File, private val max: Int = 5000) {
    val items = mutableStateListOf<Site>()

    init {
        runCatching {
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) items.add(Site.fromJson(arr.getJSONObject(i)))
            }
        }
    }

    fun persist() {
        val snapshot = items.toList()
        Thread {
            runCatching {
                val arr = JSONArray()
                snapshot.forEach { arr.put(it.toJson()) }
                file.writeText(arr.toString())
            }
        }.start()
    }

    fun contains(url: String) = items.any { it.url == url }

    fun add(site: Site, front: Boolean = true) {
        items.removeAll { it.url == site.url }
        if (front) items.add(0, site) else items.add(site)
        while (items.size > max) items.removeAt(items.size - 1)
        persist()
    }

    fun remove(url: String) {
        items.removeAll { it.url == url }
        persist()
    }

    fun update(url: String, transform: (Site) -> Site) {
        val i = items.indexOfFirst { it.url == url }
        if (i >= 0) {
            items[i] = transform(items[i]); persist()
        }
    }

    fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return
        val s = items.removeAt(from); items.add(to, s); persist()
    }

    fun clear() {
        items.clear(); persist()
    }
}

object Store {
    lateinit var settings: Settings
    lateinit var bookmarks: SiteList
    lateinit var favorites: SiteList
    lateinit var history: SiteList
    lateinit var readingList: SiteList
    /** Arc-style Archive: tabs that were closed or auto-archived, newest first. */
    lateinit var archive: SiteList

    fun init(ctx: Context) {
        settings = Settings(ctx.getSharedPreferences("settings", Context.MODE_PRIVATE))
        val dir = ctx.filesDir
        bookmarks = SiteList(File(dir, "bookmarks.json"))
        favorites = SiteList(File(dir, "favorites.json"))
        history = SiteList(File(dir, "history.json"), 10000)
        readingList = SiteList(File(dir, "reading.json"))
        archive = SiteList(File(dir, "archive.json"), 2000)
        if (favorites.items.isEmpty() && !File(dir, "favorites.json").exists()) {
            listOf(
                Site("https://www.google.com", "Google"),
                Site("https://www.youtube.com", "YouTube"),
                Site("https://www.wikipedia.org", "Wikipedia"),
                Site("https://news.ycombinator.com", "Hacker News"),
                Site("https://www.reddit.com", "Reddit"),
                Site("https://github.com", "GitHub"),
                Site("https://x.com", "X"),
                Site("https://www.bbc.com", "BBC")
            ).forEach { favorites.add(it, front = false) }
        }
    }

    fun recordVisit(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("data:")) return
        val existing = history.items.firstOrNull { it.url == url }
        history.add(
            Site(url, title.ifBlank { existing?.title ?: url }, System.currentTimeMillis(), (existing?.visits ?: 0) + 1)
        )
    }

    fun frequentlyVisited(limit: Int = 8): List<Site> {
        val byHost = LinkedHashMap<String, Site>()
        history.items.sortedByDescending { it.visits }.forEach {
            val h = hostOf(it.url)
            if (h.isNotEmpty() && !byHost.containsKey(h) && favorites.items.none { f -> hostOf(f.url) == h }) byHost[h] = it
        }
        return byHost.values.take(limit)
    }
}

fun hostOf(url: String): String = runCatching {
    android.net.Uri.parse(url).host?.removePrefix("www.") ?: ""
}.getOrDefault("")
