package app.prism.browser.engine

import android.net.Uri
import android.util.Patterns
import app.prism.browser.data.Store
import java.net.URLEncoder

object UrlUtil {
    fun toUrl(input: String): String {
        val q = input.trim()
        if (q.isEmpty()) return Tab.START
        if (q.startsWith("prism://") || q.startsWith("about:") || q.startsWith("data:") || q.startsWith("javascript:")) return q
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(q)) return q
        if (!q.contains(' ')) {
            if (q.startsWith("localhost") || Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$").matches(q)) return "http://$q"
            if (Patterns.WEB_URL.matcher(q).matches() && q.contains('.')) return "https://$q"
        }
        return Store.searchEngine.template.replace("%s", URLEncoder.encode(q, "UTF-8"))
    }

    fun host(url: String?): String? = runCatching { Uri.parse(url).host }.getOrNull()

    /** Text shown in the address bar while not editing (Safari shows just the domain). */
    fun pretty(url: String): String {
        if (url == Tab.START) return ""
        val u = Uri.parse(url)
        // search results: show the query
        val host = u.host ?: return url
        val q = u.getQueryParameter("q")
        if (q != null && (host.contains("google.") || host.contains("duckduckgo") || host.contains("bing.") || host.contains("brave") || host.contains("startpage"))) return q
        return host.removePrefix("www.").removePrefix("m.")
    }
}
