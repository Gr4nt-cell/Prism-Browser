package com.ibrowser.adblock

import android.content.Context
import com.ibrowser.data.Store
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object FilterLists {
    data class FilterList(val id: String, val name: String, val url: String, val defaultOn: Boolean)

    val all = listOf(
        FilterList("easylist", "EasyList (ads)", "https://easylist.to/easylist/easylist.txt", true),
        FilterList("easyprivacy", "EasyPrivacy (trackers)", "https://easylist.to/easylist/easyprivacy.txt", true),
        FilterList("ublock", "uBlock filters", "https://ublockorigin.github.io/uAssets/filters/filters.txt", true),
        FilterList("ublock_privacy", "uBlock privacy", "https://ublockorigin.github.io/uAssets/filters/privacy.txt", true),
        FilterList("ublock_badware", "Malware & badware", "https://ublockorigin.github.io/uAssets/filters/badware.txt", true),
        FilterList("adguard_mobile", "AdGuard Mobile Ads", "https://filters.adtidy.org/extension/ublock/filters/11.txt", true),
        FilterList("adguard_base", "AdGuard Base", "https://filters.adtidy.org/extension/ublock/filters/2_without_easylist.txt", true),
        FilterList("peterlowe", "Peter Lowe's ad servers", "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=adblockplus&showintro=0&mimetype=plaintext", true),
        FilterList("urlhaus", "Online malicious URLs", "https://malware-filter.gitlab.io/malware-filter/urlhaus-filter-ag-online.txt", true),
        FilterList("annoyances", "Cookie notices & annoyances", "https://secure.fanboy.co.nz/fanboy-annoyance.txt", false),
        FilterList("stevenblack", "StevenBlack hosts", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", false)
    )

    fun dir(ctx: Context): File = File(ctx.filesDir, "filters").apply { mkdirs() }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("filters", Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context, id: String): Boolean =
        prefs(ctx).getBoolean(id, all.firstOrNull { it.id == id }?.defaultOn ?: true)

    fun setEnabled(ctx: Context, id: String, on: Boolean) {
        prefs(ctx).edit().putBoolean(id, on).apply()
    }

    fun needsUpdate(): Boolean =
        System.currentTimeMillis() - Store.settings.lastFilterUpdate > TimeUnit.DAYS.toMillis(4)

    /** Downloads all enabled lists. Returns number of lists updated. Blocking call. */
    fun update(ctx: Context, onProgress: (String) -> Unit = {}): Int {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        var ok = 0
        for (l in all) {
            if (!isEnabled(ctx, l.id)) continue
            onProgress(l.name)
            runCatching {
                client.newCall(Request.Builder().url(l.url).header("User-Agent", "iBrowser/1.0").build()).execute().use { r ->
                    if (r.isSuccessful) {
                        val tmp = File(dir(ctx), "${l.id}.tmp")
                        r.body!!.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                        tmp.renameTo(File(dir(ctx), "${l.id}.txt"))
                        ok++
                    }
                }
            }
        }
        if (ok > 0) {
            Store.settings.lastFilterUpdate = System.currentTimeMillis()
            Store.settings.save()
        }
        AdBlocker.instance.load(ctx)
        return ok
    }
}
