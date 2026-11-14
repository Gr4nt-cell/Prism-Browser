package app.prism.browser.data

import app.prism.browser.core.Changes
import app.prism.browser.core.Obs
import app.prism.browser.core.ObsList
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class Site(val title: String, val url: String, val glyph: String = "", val time: Long = 0L)

enum class SearchEngine(val label: String, val template: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    BRAVE("Brave", "https://search.brave.com/search?q=%s"),
    STARTPAGE("Startpage", "https://www.startpage.com/do/search?q=%s"),
}

object Store {
    private lateinit var p: SharedPreferences

    val favorites = ObsList<Site>()
    val bookmarks = ObsList<Site>()
    val history = ObsList<Site>()

    var adblockEnabled by Obs(true); private set
    var popupBlocking by Obs(true); private set
    var videoDetection by Obs(true); private set
    var searchEngine by Obs(SearchEngine.GOOGLE); private set
    var downloadThreads by Obs(8); private set
    /** page zoom in percent */
    var zoom by Obs(85); private set
    /** null = automatic (desktop on tablets, mobile on phones) */
    var desktopModePref by Obs<Boolean?>(null); private set

    private val DEFAULT_FAVORITES = listOf(
        Site("Wiki", "https://en.m.wikipedia.org", "W"),
        Site("Video", "https://m.youtube.com", "▶"),
        Site("Mail", "https://mail.google.com", "✉"),
        Site("Maps", "https://www.google.com/maps", "◎"),
        Site("News", "https://news.google.com", "N"),
        Site("Music", "https://music.youtube.com", "♫"),
        Site("Weather", "https://weather.com", "☀"),
    )

    fun init(context: Context) {
        p = context.getSharedPreferences("prism", Context.MODE_PRIVATE)
        adblockEnabled = p.getBoolean("adblock", true)
        popupBlocking = p.getBoolean("popups", true)
        videoDetection = p.getBoolean("videoDetect", true)
        searchEngine = runCatching { SearchEngine.valueOf(p.getString("engine", "GOOGLE")!!) }.getOrDefault(SearchEngine.GOOGLE)
        downloadThreads = p.getInt("threads", 8)
        zoom = p.getInt("zoom", 85)
        desktopModePref = if (p.contains("desktop")) p.getBoolean("desktop", false) else null
        favorites.addAll(readSites("favorites") ?: DEFAULT_FAVORITES)
        bookmarks.addAll(readSites("bookmarks") ?: emptyList())
        history.addAll(readSites("history") ?: emptyList())
    }

    // ---------------------------------------------------------------- settings
    fun setAdblock(on: Boolean) { adblockEnabled = on; p.edit().putBoolean("adblock", on).apply() }
    fun setPopups(on: Boolean) { popupBlocking = on; p.edit().putBoolean("popups", on).apply() }
    fun setVideoDetect(on: Boolean) { videoDetection = on; p.edit().putBoolean("videoDetect", on).apply() }
    fun setEngine(e: SearchEngine) { searchEngine = e; p.edit().putString("engine", e.name).apply() }
    fun setThreads(n: Int) { downloadThreads = n.coerceIn(1, 32); p.edit().putInt("threads", downloadThreads).apply() }
    fun updateZoom(z: Int) { zoom = z.coerceIn(50, 200); p.edit().putInt("zoom", zoom).apply() }
    fun setDesktopMode(v: Boolean?) {
        desktopModePref = v
        p.edit().apply { if (v == null) remove("desktop") else putBoolean("desktop", v) }.apply()
    }

    var filtersUpdatedAt: Long
        get() = p.getLong("filtersUpdated", 0L)
        set(v) { p.edit().putLong("filtersUpdated", v).apply() }

    fun isFilterOn(id: String, default: Boolean) = p.getBoolean("filter_$id", default)
    fun setFilterOn(id: String, on: Boolean) = p.edit().putBoolean("filter_$id", on).apply()

    fun userBlockedHosts(): Set<String> = p.getStringSet("userBlocked", emptySet())!!
    fun addUserBlockedHost(h: String) = p.edit().putStringSet("userBlocked", userBlockedHosts() + h).apply()

    fun allowlistedSites(): Set<String> = p.getStringSet("allowlist", emptySet())!!
    fun setAllowlisted(host: String, allowed: Boolean) {
        val s = allowlistedSites().toMutableSet()
        if (allowed) s += host else s -= host
        p.edit().putStringSet("allowlist", s).apply()
    }

    // ---------------------------------------------------------------- stats
    private var statDay = ""
    private var dayCount = -1L
    private var total = -1L

    @Synchronized
    fun incBlocked(): Long {
        val today = LocalDate.now().toString()
        if (dayCount < 0 || statDay != today) {
            statDay = today
            dayCount = if (p.getString("blockedDay", "") == today) p.getLong("blockedToday", 0) else 0
        }
        if (total < 0) total = p.getLong("blockedTotal", 0)
        dayCount++; total++
        if (dayCount % 25 == 0L) flushStats()
        return dayCount
    }

    @Synchronized
    fun flushStats() {
        if (dayCount < 0) return
        p.edit().putString("blockedDay", statDay).putLong("blockedToday", dayCount).putLong("blockedTotal", total).apply()
    }

    fun blockedToday(): Long {
        val today = LocalDate.now().toString()
        if (dayCount >= 0 && statDay == today) return dayCount
        return if (p.getString("blockedDay", "") == today) p.getLong("blockedToday", 0) else 0
    }
    fun blockedTotal() = if (total >= 0) total else p.getLong("blockedTotal", 0)

    // ---------------------------------------------------------------- tabs
    fun saveTabs(urls: List<String>, active: Int) {
        p.edit().putString("tabs", JSONArray(urls).toString()).putInt("activeTab", active).apply()
    }
    fun savedTabs(): Pair<List<String>, Int> {
        val arr = runCatching { JSONArray(p.getString("tabs", "[]")) }.getOrDefault(JSONArray())
        return List(arr.length()) { arr.getString(it) } to p.getInt("activeTab", 0)
    }

    // ---------------------------------------------------------------- sites
    fun addFavorite(s: Site) { favorites.add(s); writeSites("favorites", favorites) }
    fun removeFavorite(s: Site) { favorites.remove(s); writeSites("favorites", favorites) }

    fun isBookmarked(url: String) = bookmarks.any { it.url == url }
    fun toggleBookmark(title: String, url: String) {
        val existing = bookmarks.firstOrNull { it.url == url }
        if (existing != null) bookmarks.remove(existing) else bookmarks.add(0, Site(title, url, time = System.currentTimeMillis()))
        writeSites("bookmarks", bookmarks)
    }
    fun removeBookmark(s: Site) { bookmarks.remove(s); writeSites("bookmarks", bookmarks) }

    fun addHistory(title: String, url: String) {
        if (url.startsWith("about:") || url.startsWith("data:") || url.isBlank()) return
        history.removeAll { it.url == url }
        history.add(0, Site(title.ifBlank { url }, url, time = System.currentTimeMillis()))
        while (history.size > 1000) history.removeAt(history.lastIndex)
        writeSites("history", history)
    }
    fun clearHistory() { history.clear(); writeSites("history", history) }

    private fun readSites(key: String): List<Site>? {
        val s = p.getString(key, null) ?: return null
        return runCatching {
            val arr = JSONArray(s)
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                Site(o.optString("t"), o.optString("u"), o.optString("g"), o.optLong("ts"))
            }
        }.getOrNull()
    }

    private fun writeSites(key: String, list: List<Site>) {
        Changes.notifyChanged()
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.title).put("u", it.url).put("g", it.glyph).put("ts", it.time)) }
        p.edit().putString(key, arr.toString()).apply()
    }
}
