package com.gr4nt.browser.adblock

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Adblock-Plus / uBlock compatible filter engine.
 *
 * Supports: hosts files, ||domain^ rules, @@ exceptions, wildcards (*), separators (^),
 * anchors (|, ||), /regex/ rules, and options $third-party, $~third-party, $domain=,
 * $script, $image, $stylesheet, $xmlhttprequest, $media, $font, $subdocument, $other,
 * $popup (ignored), $match-case, $important. Cosmetic rules: ##, domain##, #@# exceptions.
 */
class AdBlocker private constructor() {

    // ---- Network rule model ----
    class NetRule(
        val raw: String,
        val pattern: String,
        val isRegex: Boolean,
        val exception: Boolean,
        val important: Boolean,
        val thirdParty: Boolean?,
        val types: Int,           // 0 = any
        val notTypes: Int,
        val includeDomains: Array<String>?,
        val excludeDomains: Array<String>?,
        val matchCase: Boolean
    ) {
        @Volatile private var compiled: Regex? = null

        private fun regex(): Regex {
            compiled?.let { return it }
            val r = if (isRegex) {
                runCatching {
                    if (matchCase) Regex(pattern) else Regex(pattern, RegexOption.IGNORE_CASE)
                }.getOrElse { Regex("(?!)") }
            } else Regex(toRegex(pattern), if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
            compiled = r
            return r
        }

        fun matches(url: String, host: String, pageHost: String, type: Int, isThirdParty: Boolean): Boolean {
            if (thirdParty != null && thirdParty != isThirdParty) return false
            if (types != 0 && (types and type) == 0) return false
            if (notTypes != 0 && (notTypes and type) != 0) return false
            if (includeDomains != null && includeDomains.none { domainMatches(pageHost, it) }) return false
            if (excludeDomains != null && excludeDomains.any { domainMatches(pageHost, it) }) return false
            return regex().containsMatchIn(url)
        }

        companion object {
            fun toRegex(p: String): String {
                val sb = StringBuilder()
                var s = p
                var start = false
                if (s.startsWith("||")) {
                    sb.append("^[a-z][a-z0-9+.-]*://(?:[^/?#]*\\.)?"); s = s.substring(2)
                } else if (s.startsWith("|")) {
                    sb.append("^"); s = s.substring(1); start = true
                }
                var end = false
                if (s.endsWith("|")) { end = true; s = s.dropLast(1) }
                for (c in s) {
                    when (c) {
                        '*' -> sb.append(".*")
                        '^' -> sb.append("(?:[^\\w\\-.%]|$)")
                        '.', '?', '+', '(', ')', '[', ']', '{', '}', '\\', '$', '|' -> sb.append('\\').append(c)
                        else -> sb.append(c)
                    }
                }
                if (end) sb.append("$")
                if (!start && sb.startsWith(".*")) return sb.substring(2)
                return sb.toString()
            }
        }
    }

    companion object {
        const val T_SCRIPT = 1
        const val T_IMAGE = 2
        const val T_CSS = 4
        const val T_XHR = 8
        const val T_MEDIA = 16
        const val T_FONT = 32
        const val T_SUBDOC = 64
        const val T_OTHER = 128
        const val T_DOCUMENT = 256

        private val TYPE_NAMES = mapOf(
            "script" to T_SCRIPT, "image" to T_IMAGE, "stylesheet" to T_CSS,
            "xmlhttprequest" to T_XHR, "xhr" to T_XHR, "media" to T_MEDIA, "font" to T_FONT,
            "subdocument" to T_SUBDOC, "frame" to T_SUBDOC, "other" to T_OTHER,
            "object" to T_OTHER, "ping" to T_OTHER, "websocket" to T_XHR, "document" to T_DOCUMENT,
            "doc" to T_DOCUMENT, "css" to T_CSS, "fetch" to T_XHR
        )

        val instance = AdBlocker()

        fun domainMatches(host: String, domain: String): Boolean =
            host == domain || host.endsWith(".$domain")

        fun guessType(url: String, accept: String?): Int {
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            return when {
                path.endsWith(".js") || path.endsWith(".mjs") -> T_SCRIPT
                path.endsWith(".css") -> T_CSS
                path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                    path.endsWith(".gif") || path.endsWith(".webp") || path.endsWith(".svg") ||
                    path.endsWith(".ico") || path.endsWith(".avif") -> T_IMAGE
                path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") || path.endsWith(".otf") -> T_FONT
                path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m3u8") ||
                    path.endsWith(".mp3") || path.endsWith(".ts") || path.endsWith(".m4s") -> T_MEDIA
                accept != null && accept.contains("text/html") -> T_SUBDOC
                accept != null && accept.startsWith("image/") -> T_IMAGE
                accept != null && accept.contains("text/css") -> T_CSS
                accept != null && (accept.contains("json") || accept == "*/*") -> T_XHR or T_OTHER or T_SCRIPT
                else -> T_OTHER or T_XHR or T_SCRIPT
            }
        }

        private val TOKEN = Regex("[a-z0-9%]{3,}")
        private fun tokens(url: String): Sequence<String> = TOKEN.findAll(url.lowercase()).map { it.value }

        /** Domains used by many sites where blocking whole host would break the web. */
        private val NEVER_BLOCK_HOSTS = setOf(
            "google.com", "www.google.com", "gstatic.com", "googleapis.com", "youtube.com",
            "ytimg.com", "googlevideo.com", "github.com", "githubusercontent.com", "cloudflare.com",
            "jsdelivr.net", "cdnjs.cloudflare.com", "unpkg.com", "wikipedia.org", "wikimedia.org", "apple.com"
        )
    }

    private class Engine {
        val blockedDomains = HashSet<String>(200_000)
        val exceptionDomains = HashSet<String>()
        val importantDomains = HashSet<String>()
        val tokenRules = HashMap<String, MutableList<NetRule>>()
        val genericRules = ArrayList<NetRule>()
        val exceptionTokenRules = HashMap<String, MutableList<NetRule>>()
        val genericExceptions = ArrayList<NetRule>()
        val genericSelectors = LinkedHashSet<String>()
        val domainSelectors = HashMap<String, MutableList<String>>()
        val selectorExceptions = HashMap<String, MutableSet<String>>() // domain -> selectors
        val genericHideDomains = HashSet<String>()                     // @@||x^$generichide / elemhide
        var ruleCount = 0
        @Volatile var genericCss: String? = null

        fun buildGenericCss(): String {
            genericCss?.let { return it }
            val sb = StringBuilder()
            // Chunk selectors so a single bad selector only kills its chunk
            genericSelectors.chunked(400).forEach { chunk ->
                sb.append(chunk.joinToString(",")).append("{display:none!important;visibility:hidden!important}\n")
            }
            val css = sb.toString()
            genericCss = css
            return css
        }
    }

    private val engine = AtomicReference(Engine())
    @Volatile var ready = false
        private set
    val ruleCount get() = engine.get().ruleCount
    private val decisionCache = object : LinkedHashMap<String, Boolean>(2048, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 4000
    }
    private val pageCounts = ConcurrentHashMap<String, Int>()

    // ------------------------------------------------------------------ Loading
    fun load(ctx: Context) {
        val e = Engine()
        runCatching { ctx.assets.open("adblock_default.txt").bufferedReader().useLines { it.forEach { l -> parseLine(e, l) } } }
        // install the bundled set first so blocking is active immediately
        engine.set(e)
        ready = true
        // then the downloaded lists
        val dir = FilterLists.dir(ctx)
        val files = dir.listFiles()?.filter { it.name.endsWith(".txt") } ?: emptyList()
        if (files.isNotEmpty()) {
            val full = Engine()
            runCatching { ctx.assets.open("adblock_default.txt").bufferedReader().useLines { it.forEach { l -> parseLine(full, l) } } }
            for (f in files) {
                if (!FilterLists.isEnabled(ctx, f.nameWithoutExtension)) continue
                runCatching { f.bufferedReader().useLines { it.forEach { l -> parseLine(full, l) } } }
                    .onFailure { Log.w("AdBlocker", "Failed ${f.name}", it) }
            }
            engine.set(full)
            synchronized(decisionCache) { decisionCache.clear() }
        }
        Log.i("AdBlocker", "Loaded ${engine.get().ruleCount} rules")
    }

    private fun parseLine(e: Engine, raw: String) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[") || line.startsWith("#!")) return
        if (line.startsWith("#") && !line.startsWith("##") && !line.startsWith("#@#")) return

        // hosts format
        if (line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ") || line.startsWith(":: ")) {
            val d = line.substringAfter(' ').trim().substringBefore(' ').substringBefore('#').trim().lowercase()
            if (d.isNotEmpty() && d != "localhost" && d != "0.0.0.0" && d.contains('.')) {
                e.blockedDomains.add(d); e.ruleCount++
            }
            return
        }

        // extended / scriptlet syntaxes we don't support
        if (line.contains("#?#") || line.contains("#$#") || line.contains("#%#") ||
            line.contains("#@\$#") || line.contains("#@?#") || line.contains("#@%#")) return
        // cosmetic
        val cosIdx = line.indexOf("##").let { if (it < 0) line.indexOf("#@#") else it }
        if (cosIdx >= 0) {
            val prefix = line.substring(0, cosIdx)
            if (!prefix.contains('/') && !prefix.contains('^') && !prefix.contains('|') && !prefix.contains('$')) {
                parseCosmetic(e, line)
                return
            }
        }

        var rule = line
        val exception = rule.startsWith("@@")
        if (exception) rule = rule.substring(2)

        var opts: String? = null
        val isRegexPattern = rule.startsWith("/") && rule.lastIndexOf('/') > 0 &&
            (rule.endsWith("/") || rule.substring(rule.lastIndexOf('/')).contains('$'))
        val dollar = if (isRegexPattern) {
            val lastSlash = rule.lastIndexOf('/')
            rule.indexOf('$', lastSlash).takeIf { it > lastSlash } ?: -1
        } else rule.lastIndexOf('$')
        if (dollar >= 0 && dollar < rule.length - 1 && !rule.substring(dollar + 1).contains('/')) {
            opts = rule.substring(dollar + 1); rule = rule.substring(0, dollar)
        }

        var thirdParty: Boolean? = null
        var types = 0
        var notTypes = 0
        var include: MutableList<String>? = null
        var exclude: MutableList<String>? = null
        var matchCase = false
        var important = false
        if (opts != null) {
            for (o in opts.split(',')) {
                val opt = o.trim().lowercase()
                when {
                    opt == "third-party" || opt == "3p" -> thirdParty = true
                    opt == "~third-party" || opt == "first-party" || opt == "1p" || opt == "~3p" -> thirdParty = false
                    opt == "match-case" -> matchCase = true
                    opt == "important" -> important = true
                    opt.startsWith("domain=") || opt.startsWith("from=") -> {
                        opt.substringAfter('=').split('|').forEach { d ->
                            if (d.startsWith("~")) (exclude ?: mutableListOf<String>().also { exclude = it }).add(d.substring(1))
                            else if (d.isNotEmpty()) (include ?: mutableListOf<String>().also { include = it }).add(d)
                        }
                    }
                    opt == "generichide" || opt == "ghide" || opt == "elemhide" || opt == "ehide" -> {
                        if (exception) {
                            val d = rule.removePrefix("||").substringBefore('^').substringBefore('/')
                            if (d.isNotEmpty()) e.genericHideDomains.add(d.lowercase())
                        }
                        return
                    }
                    opt.startsWith("~") && TYPE_NAMES.containsKey(opt.substring(1)) -> notTypes = notTypes or TYPE_NAMES[opt.substring(1)]!!
                    TYPE_NAMES.containsKey(opt) -> types = types or TYPE_NAMES[opt]!!
                    opt == "popup" || opt == "all" || opt == "popunder" -> { if (opt != "all") return }
                    // Unsupported modifiers that change semantics: skip the rule entirely
                    opt.startsWith("redirect") || opt.startsWith("removeparam") || opt.startsWith("csp") ||
                        opt.startsWith("replace") || opt.startsWith("header") || opt.startsWith("permissions") ||
                        opt.startsWith("rewrite") || opt == "badfilter" || opt.startsWith("urltransform") ||
                        opt == "content" || opt == "genericblock" || opt == "cname" || opt.startsWith("method") ||
                        opt.startsWith("to=") || opt.startsWith("denyallow") -> return
                    else -> { /* ignore unknown harmless option */ }
                }
            }
        }
        if (rule.isEmpty() || rule == "*") return

        // Fast path: pure domain rule  ||example.com^
        val simpleDomain = SIMPLE_DOMAIN.find(rule)
        if (simpleDomain != null && include == null && exclude == null && types == 0 && notTypes == 0 && thirdParty != false) {
            val d = simpleDomain.groupValues[1].lowercase()
            if (exception) e.exceptionDomains.add(d)
            else {
                e.blockedDomains.add(d); if (important) e.importantDomains.add(d)
            }
            e.ruleCount++
            return
        }

        val isRegex = rule.startsWith("/") && rule.endsWith("/") && rule.length > 2
        val pattern = if (isRegex) rule.substring(1, rule.length - 1) else rule
        val r = NetRule(
            line, pattern, isRegex, exception, important, thirdParty, types, notTypes,
            include?.toTypedArray(), exclude?.toTypedArray(), matchCase
        )
        val token = if (isRegex) null else bestToken(pattern)
        if (exception) {
            if (token != null) e.exceptionTokenRules.getOrPut(token) { ArrayList(2) }.add(r) else e.genericExceptions.add(r)
        } else {
            if (token != null) e.tokenRules.getOrPut(token) { ArrayList(2) }.add(r) else e.genericRules.add(r)
        }
        e.ruleCount++
    }

    /** Pick a literal token from the pattern that is guaranteed to be a whole token in matching URLs. */
    private fun bestToken(p: String): String? {
        val lower = p.lowercase()
        var best: String? = null
        val m = Regex("[a-z0-9%]{3,}").findAll(lower)
        for (t in m) {
            val s = t.range.first
            val en = t.range.last + 1
            val before = if (s == 0) null else lower[s - 1]
            val after = if (en >= lower.length) null else lower[en]
            val okBefore = before != null && before != '*'
            val okAfter = after != null && after != '*'
            if (!okBefore || !okAfter) continue
            if (t.value in COMMON_TOKENS) continue
            if (best == null || t.value.length > best.length) best = t.value
        }
        return best
    }

    private val SIMPLE_DOMAIN = Regex("^\\|\\|([a-z0-9.\\-_]+)\\^?\\|?$", RegexOption.IGNORE_CASE)
    private val COMMON_TOKENS = setOf("http", "https", "www", "com", "net", "org", "html", "js")

    private fun parseCosmetic(e: Engine, line: String) {
        val isException = line.contains("#@#")
        val sep = if (isException) "#@#" else "##"
        val idx = line.indexOf(sep)
        val domains = line.substring(0, idx)
        val selector = line.substring(idx + sep.length).trim()
        if (selector.isEmpty() || selector.startsWith("+js") || selector.contains(":-abp-") ||
            selector.contains(":has-text(") || selector.contains(":upward(") || selector.contains(":xpath(") ||
            selector.contains(":style(") || selector.contains(":remove(") || selector.contains(":matches-") ||
            selector.contains(":min-text-length") || selector.contains(":watch-attr") || selector.contains(":others(") ||
            selector.startsWith("^")
        ) return
        if (domains.isEmpty()) {
            if (!isException) e.genericSelectors.add(selector)
            else e.genericSelectors.remove(selector)
        } else {
            domains.split(',').forEach { d0 ->
                val d = d0.trim().lowercase()
                if (d.isEmpty() || d.startsWith("~")) return@forEach
                if (isException) e.selectorExceptions.getOrPut(d) { HashSet() }.add(selector)
                else e.domainSelectors.getOrPut(d) { ArrayList(2) }.add(selector)
            }
        }
        e.ruleCount++
    }

    // ------------------------------------------------------------------ Matching

    private fun hostChain(host: String): Sequence<String> = sequence {
        var h = host
        while (true) {
            yield(h)
            val i = h.indexOf('.')
            if (i < 0 || h.indexOf('.', i + 1) < 0) break
            h = h.substring(i + 1)
        }
    }

    private fun registrable(host: String): String {
        val parts = host.split('.')
        if (parts.size <= 2) return host
        val sld = parts[parts.size - 2]
        return if (sld.length <= 3 && parts.last().length == 2) parts.takeLast(3).joinToString(".")
        else parts.takeLast(2).joinToString(".")
    }

    /** Returns true if the request should be blocked. */
    fun shouldBlock(url: String, pageUrl: String?, accept: String?): Boolean {
        if (!ready) return false
        val host = runCatching { android.net.Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        val pageHost = pageUrl?.let { runCatching { android.net.Uri.parse(it).host?.lowercase() }.getOrNull() } ?: host
        val key = "$pageHost|$url"
        synchronized(decisionCache) { decisionCache[key]?.let { return it } }
        val result = decide(url, host, pageHost, accept)
        synchronized(decisionCache) { decisionCache[key] = result }
        return result
    }

    private fun decide(url: String, host: String, pageHost: String, accept: String?): Boolean {
        val e = engine.get()
        val thirdParty = registrable(host) != registrable(pageHost)
        val type = guessType(url, accept)

        var blocked = false
        var important = false
        for (h in hostChain(host)) {
            if (h in e.blockedDomains) {
                if (h in NEVER_BLOCK_HOSTS && !thirdParty) break
                blocked = true; important = h in e.importantDomains; break
            }
        }
        if (!blocked) {
            val toks = tokens(url).toList()
            outer@ for (t in toks) {
                val list = e.tokenRules[t] ?: continue
                for (r in list) if (r.matches(url, host, pageHost, type, thirdParty)) {
                    blocked = true; important = r.important; break@outer
                }
            }
            if (!blocked) for (r in e.genericRules) if (r.matches(url, host, pageHost, type, thirdParty)) {
                blocked = true; important = r.important; break
            }
        }
        if (!blocked) return false
        if (important) return true

        // exceptions
        for (h in hostChain(host)) if (h in e.exceptionDomains) return false
        for (t in tokens(url)) {
            val list = e.exceptionTokenRules[t] ?: continue
            for (r in list) if (r.matches(url, host, pageHost, type, thirdParty)) return false
        }
        for (r in e.genericExceptions) if (r.matches(url, host, pageHost, type, thirdParty)) return false
        return true
    }

    /** CSS to hide ad elements on the given host. */
    fun cssFor(host: String): String {
        if (!ready) return ""
        val e = engine.get()
        val h = host.lowercase().removePrefix("www.")
        val sb = StringBuilder()
        val generic = hostChain(h).none { it in e.genericHideDomains }
        if (generic) sb.append(e.buildGenericCss())
        val excluded = HashSet<String>()
        hostChain(h).forEach { d -> e.selectorExceptions[d]?.let { excluded.addAll(it) } }
        val specific = ArrayList<String>()
        hostChain(h).forEach { d -> e.domainSelectors[d]?.let { list -> list.forEach { if (it !in excluded) specific.add(it) } } }
        if (!h.startsWith("www.")) e.domainSelectors["www.$h"]?.let { specific.addAll(it) }
        specific.chunked(200).forEach { sb.append(it.joinToString(",")).append("{display:none!important}\n") }
        return sb.toString()
    }

    fun countBlocked(pageKey: String) {
        pageCounts.merge(pageKey, 1, Int::plus)
    }

    fun blockedOn(pageKey: String): Int = pageCounts[pageKey] ?: 0
    fun resetPage(pageKey: String) { pageCounts.remove(pageKey) }

    fun reload(ctx: Context) {
        Thread { load(ctx) }.start()
    }

    @Suppress("unused")
    fun debugFile(ctx: Context) = File(ctx.filesDir, "adblock")
}
