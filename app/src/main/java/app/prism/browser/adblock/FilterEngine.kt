package app.prism.browser.adblock

import java.util.Locale

/**
 * Compiled Adblock-Plus / uBlock-Origin style filter engine.
 *
 * Network rules:
 *  - "||domain^" (no options)        -> hash set lookup on every host suffix (fast path)
 *  - everything else                -> token-indexed rules verified by lazily compiled regex
 * Cosmetic rules:
 *  - "##sel"                         -> generic hide stylesheet
 *  - "domain##sel"                   -> per-site hide stylesheet
 *  - "domain#@#sel"                  -> per-site exceptions
 */
class FilterEngine {

    // ---- network ----
    private val blockDomains = HashSet<String>(200_000)
    private val allowDomains = HashSet<String>()
    private val popupDomains = HashSet<String>()
    private val disabledSites = HashSet<String>()      // @@||site^$document
    private val noGenericHideSites = HashSet<String>() // @@||site^$generichide
    private val noCosmeticSites = HashSet<String>()    // @@||site^$elemhide

    private val blockIndex = HashMap<String, MutableList<UrlRule>>()
    private val allowIndex = HashMap<String, MutableList<UrlRule>>()
    private val blockLoose = ArrayList<UrlRule>()
    private val allowLoose = ArrayList<UrlRule>()
    private val popupRules = ArrayList<UrlRule>()

    // ---- cosmetic ----
    private val genericSelectors = LinkedHashSet<String>()      // complex generic selectors, always injected
    private val genericSimple = HashSet<String>(40_000)          // ".cls" / "#id", injected only when present in the DOM
    private val siteSelectors = HashMap<String, MutableList<String>>()
    private val siteExceptions = HashMap<String, MutableSet<String>>()

    var ruleCount = 0
        private set

    @Volatile private var genericCss: String? = null
    private val cssCache = object : LinkedHashMap<String, String>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 48
    }

    // ------------------------------------------------------------------ parsing

    fun addList(text: String) {
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line[0] == '!' || line[0] == '[') return@forEach
            try {
                parseLine(line)
            } catch (_: Throwable) {
            }
        }
    }

    /** Hosts-file style list (0.0.0.0 domain). */
    fun addHosts(text: String) {
        text.lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            val parts = line.split(Regex("\\s+"))
            val host = if (parts.size >= 2) parts[1] else parts[0]
            if (host.contains('.') && host != "localhost") {
                blockDomains += host.lowercase(Locale.ROOT); ruleCount++
            }
        }
    }

    private fun parseLine(line: String) {
        // cosmetic
        val cosIdx = line.indexOf('#')
        if (cosIdx >= 0) {
            when {
                line.startsWith("##", cosIdx) -> { addCosmetic(line.substring(0, cosIdx), line.substring(cosIdx + 2), false); return }
                line.startsWith("#@#", cosIdx) -> { addCosmetic(line.substring(0, cosIdx), line.substring(cosIdx + 3), true); return }
                line.startsWith("#?#", cosIdx) || line.startsWith("#$#", cosIdx) ||
                    line.startsWith("#%#", cosIdx) || line.startsWith("#@?#", cosIdx) ||
                    line.startsWith("#@$#", cosIdx) || line.startsWith("#@%#", cosIdx) -> return
            }
        }
        if (line.contains("$$") || line.contains("##^")) return // html filtering

        var body = line
        var exception = false
        if (body.startsWith("@@")) { exception = true; body = body.substring(2) }

        // regex rules are skipped (expensive, rare)
        if (body.length > 1 && body.startsWith("/") && body.lastIndexOf('/') > 0 &&
            (body.endsWith("/") || body.substring(body.lastIndexOf('/')).let { it.startsWith("/$") })
        ) return

        var options: List<String> = emptyList()
        val dollar = findOptionsDollar(body)
        if (dollar >= 0) {
            options = body.substring(dollar + 1).split(',').map { it.trim() }.filter { it.isNotEmpty() }
            body = body.substring(0, dollar)
        }

        val rule = UrlRule(body.lowercase(Locale.ROOT), exception)
        var popup = false
        var document = false
        for (opt in options) {
            val o = opt.lowercase(Locale.ROOT)
            val neg = o.startsWith("~")
            val name = (if (neg) o.substring(1) else o).substringBefore('=')
            val value = o.substringAfter('=', "")
            when (name) {
                "third-party", "3p" -> rule.thirdParty = !neg
                "first-party", "1p" -> rule.thirdParty = neg
                "domain", "from" -> value.split('|').forEach { d ->
                    if (d.startsWith("~")) rule.excludeDomains += d.substring(1) else if (d.isNotEmpty()) rule.includeDomains += d
                }
                "popup" -> popup = true
                "document", "doc" -> document = true
                "all" -> { popup = true; document = true }
                "generichide", "ghide" -> { if (exception) hostOf(body)?.let { noGenericHideSites += it }; return }
                "elemhide", "ehide" -> { if (exception) hostOf(body)?.let { noCosmeticSites += it }; return }
                "important", "match-case", "strict3p", "strict1p", "specifichide", "shide" -> {}
                else -> {
                    val type = TYPE_MAP[name] ?: return // unsupported option -> skip whole rule safely
                    if (neg) rule.excludeTypes = rule.excludeTypes or type else rule.includeTypes = rule.includeTypes or type
                }
            }
        }

        if (exception && document) {
            hostOf(body)?.let { disabledSites += it }
            return
        }

        if (popup && rule.includeTypes == 0) {
            if (exception) return
            val host = pureHost(body)
            if (host != null && rule.includeDomains.isEmpty()) popupDomains += host else popupRules += rule
            ruleCount++
            return
        }

        val pure = pureHost(body)
        if (pure != null && rule.isSimple()) {
            if (exception) allowDomains += pure else blockDomains += pure
            ruleCount++
            return
        }
        if (body.isEmpty() || body == "*" || body == "|" || body == "||") {
            if (rule.includeDomains.isEmpty()) return // would match everything
        }
        val token = bestToken(rule.pattern)
        if (token == null) {
            if (exception) allowLoose += rule else blockLoose += rule
        } else {
            (if (exception) allowIndex else blockIndex).getOrPut(token) { ArrayList(2) } += rule
        }
        ruleCount++
    }

    private fun findOptionsDollar(s: String): Int {
        val i = s.lastIndexOf('$')
        if (i <= 0 || i == s.length - 1) return -1
        // a '$' inside a regex/selector-ish pattern like "/foo$/" is not options
        val after = s.substring(i + 1)
        return if (after.first().isLetter() || after.first() == '~' || after.first() == '_') i else -1
    }

    private fun addCosmetic(domains: String, selector: String, exception: Boolean) {
        val sel = selector.trim()
        if (sel.isEmpty() || sel.startsWith("+js(") || PROCEDURAL.any { sel.contains(it) }) return
        val positives = domains.split(',').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() && !it.startsWith("~") }
        if (exception) {
            positives.forEach { siteExceptions.getOrPut(it) { HashSet() } += sel }
            return
        }
        if (positives.isEmpty()) {
            if (domains.isEmpty()) {
                if (SIMPLE_SEL.matches(sel)) genericSimple += sel else genericSelectors += sel
            }
        } else {
            positives.forEach { siteSelectors.getOrPut(it.removeSuffix(".*")) { ArrayList(2) } += sel }
        }
        ruleCount++
    }

    // ------------------------------------------------------------------ matching

    fun isSiteDisabled(pageHost: String?): Boolean =
        pageHost != null && suffixes(pageHost).any { it in disabledSites }

    /**
     * @return true if the request should be blocked.
     */
    fun shouldBlock(url: String, host: String, pageHost: String?, type: Int): Boolean {
        val pHost = pageHost ?: host
        if (isSiteDisabled(pHost)) return false
        val third = !sameSite(host, pHost)
        val lurl = url.lowercase(Locale.ROOT)

        var blocked = suffixes(host).any { it in blockDomains }
        if (!blocked) {
            blocked = matchIndexed(blockIndex, blockLoose, lurl, host, pHost, third, type)
        }
        if (!blocked) return false
        if (suffixes(host).any { it in allowDomains }) return false
        if (matchIndexed(allowIndex, allowLoose, lurl, host, pHost, third, type)) return false
        return true
    }

    /** Should a popup / new window / non-gesture top-level navigation to this url be blocked? */
    fun isPopupBlocked(url: String, host: String, openerHost: String?): Boolean {
        if (suffixes(host).any { it in popupDomains || it in blockDomains }) {
            return !suffixes(host).any { it in allowDomains }
        }
        val lurl = url.lowercase(Locale.ROOT)
        val third = !sameSite(host, openerHost ?: host)
        if (popupRules.any { it.matches(lurl, host, openerHost ?: host, third, TYPE_DOCUMENT) }) return true
        return matchIndexed(blockIndex, blockLoose, lurl, host, openerHost ?: host, third, TYPE_DOCUMENT)
    }

    private fun matchIndexed(
        index: Map<String, List<UrlRule>>, loose: List<UrlRule>,
        lurl: String, host: String, pageHost: String, third: Boolean, type: Int,
    ): Boolean {
        var i = 0
        val n = lurl.length
        while (i < n) {
            while (i < n && !isTokenChar(lurl[i])) i++
            val start = i
            while (i < n && isTokenChar(lurl[i])) i++
            if (i - start >= 2) {
                val list = index[lurl.substring(start, i)]
                if (list != null) for (r in list) if (r.matches(lurl, host, pageHost, third, type)) return true
            }
        }
        for (r in loose) if (r.matches(lurl, host, pageHost, third, type)) return true
        return false
    }

    // ------------------------------------------------------------------ cosmetic output

    fun cssFor(pageHost: String): String {
        synchronized(cssCache) { cssCache[pageHost]?.let { return it } }
        val hostSuffixes = suffixes(pageHost)
        if (isSiteDisabled(pageHost) || hostSuffixes.any { it in noCosmeticSites }) return ""
        val exceptions = HashSet<String>()
        hostSuffixes.forEach { siteExceptions[it]?.let(exceptions::addAll) }
        val sb = StringBuilder()
        val noGeneric = hostSuffixes.any { it in noGenericHideSites }
        if (!noGeneric) {
            if (exceptions.isEmpty()) sb.append(genericCss())
            else genericSelectors.forEach { if (it !in exceptions) sb.append(it).append(HIDE) }
        }
        // per-site: match host suffixes and "entity" (google.*)
        val keys = hostSuffixes + hostSuffixes.map { entityOf(it) }
        for (k in keys) siteSelectors[k]?.forEach { if (it !in exceptions) sb.append(it).append(HIDE) }
        val css = sb.toString()
        synchronized(cssCache) { cssCache[pageHost] = css }
        return css
    }

    /**
     * Generic ".class"/"#id" hiding rules for the tokens that actually exist in the page.
     * [tokens] is a space separated list like ".banner #top-ad .story".
     */
    fun genericFor(pageHost: String, tokens: String): String {
        val hostSuffixes = suffixes(pageHost)
        if (isSiteDisabled(pageHost) || hostSuffixes.any { it in noCosmeticSites || it in noGenericHideSites }) return ""
        var exceptions: Set<String>? = null
        val sb = StringBuilder()
        for (t in tokens.split(' ')) {
            if (t.length < 2 || t !in genericSimple) continue
            if (exceptions == null) exceptions = HashSet<String>().also { ex -> hostSuffixes.forEach { siteExceptions[it]?.let(ex::addAll) } }
            if (t in exceptions) continue
            sb.append(t).append(HIDE)
        }
        return sb.toString()
    }

    private fun genericCss(): String = genericCss ?: StringBuilder(genericSelectors.size * 32).also { sb ->
        // One rule per selector so an unsupported selector only drops itself.
        genericSelectors.forEach { sb.append(it).append(HIDE) }
    }.toString().also { genericCss = it }

    // ------------------------------------------------------------------ helpers

    companion object {
        const val TYPE_SCRIPT = 1
        const val TYPE_IMAGE = 2
        const val TYPE_STYLESHEET = 4
        const val TYPE_XHR = 8
        const val TYPE_SUBDOCUMENT = 16
        const val TYPE_MEDIA = 32
        const val TYPE_FONT = 64
        const val TYPE_OBJECT = 128
        const val TYPE_OTHER = 256
        const val TYPE_PING = 512
        const val TYPE_WEBSOCKET = 1024
        const val TYPE_DOCUMENT = 2048

        private const val HIDE = "{display:none!important}\n"
        private val SIMPLE_SEL = Regex("[.#][A-Za-z0-9_-]+")

        private val TYPE_MAP = mapOf(
            "script" to TYPE_SCRIPT, "image" to TYPE_IMAGE, "stylesheet" to TYPE_STYLESHEET, "css" to TYPE_STYLESHEET,
            "xmlhttprequest" to TYPE_XHR, "xhr" to TYPE_XHR, "subdocument" to TYPE_SUBDOCUMENT, "frame" to TYPE_SUBDOCUMENT,
            "media" to TYPE_MEDIA, "font" to TYPE_FONT, "object" to TYPE_OBJECT, "object-subrequest" to TYPE_OBJECT,
            "other" to TYPE_OTHER, "ping" to TYPE_PING, "beacon" to TYPE_PING, "websocket" to TYPE_WEBSOCKET,
        )

        private val PROCEDURAL = listOf(
            ":-abp-", ":has-text(", ":xpath(", ":upward(", ":matches-css", ":matches-attr(", ":matches-path(",
            ":min-text-length(", ":remove(", ":style(", ":watch-attr(", ":others(", ":contains(", ":nth-ancestor(",
            ":remove-attr(", ":remove-class(", ":matches-media(", ":matches-prop(", ":if(", ":if-not(", "+js",
        )

        fun isTokenChar(c: Char) = (c in 'a'..'z') || (c in '0'..'9') || c == '%'

        /** "a.b.example.com" -> [a.b.example.com, b.example.com, example.com, com] */
        fun suffixes(host: String): List<String> {
            val out = ArrayList<String>(5)
            var h = host
            while (true) {
                out += h
                val dot = h.indexOf('.')
                if (dot < 0) break
                h = h.substring(dot + 1)
            }
            return out
        }

        private fun entityOf(host: String): String {
            val parts = host.split('.')
            return if (parts.size >= 2) parts.dropLast(1).joinToString(".") + ".*" else host
        }

        fun registrable(host: String): String {
            val parts = host.split('.')
            if (parts.size <= 2) return host
            val last2 = parts.takeLast(2).joinToString(".")
            // crude public-suffix handling for 2-level ccTLDs (co.uk, com.gh, com.au...)
            return if (parts[parts.size - 2].length <= 3 && parts.last().length == 2 && parts.size >= 3)
                parts.takeLast(3).joinToString(".") else last2
        }

        fun sameSite(a: String, b: String) = registrable(a) == registrable(b)

        /** "||example.com^" -> "example.com" */
        fun pureHost(body: String): String? {
            if (!body.startsWith("||")) return null
            var h = body.substring(2)
            if (h.endsWith("^")) h = h.dropLast(1) else if (h.endsWith("^|")) h = h.dropLast(2)
            if (h.isEmpty() || h.any { !(it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') }) return null
            if (!h.contains('.')) return null
            return h.lowercase(Locale.ROOT)
        }

        fun hostOf(body: String): String? {
            if (!body.startsWith("||")) return null
            val h = body.substring(2).takeWhile { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
            return h.ifEmpty { null }?.lowercase(Locale.ROOT)
        }

        /** Choose the longest token that is not adjacent to a wildcard. */
        fun bestToken(pattern: String): String? {
            var best: String? = null
            var i = 0
            val n = pattern.length
            while (i < n) {
                while (i < n && !isTokenChar(pattern[i])) i++
                val start = i
                while (i < n && isTokenChar(pattern[i])) i++
                if (i - start < 2) continue
                val before = if (start > 0) pattern[start - 1] else ' '
                val after = if (i < n) pattern[i] else ' '
                if (before == '*' || after == '*') continue
                // a token at the very start of an unanchored pattern may be a partial match
                if (start == 0) continue
                if (i == n && !pattern.endsWith("|")) continue
                val tok = pattern.substring(start, i)
                if (tok in BAD_TOKENS) continue
                if (best == null || tok.length > best.length) best = tok
            }
            return best
        }

        private val BAD_TOKENS = setOf("http", "https", "www", "com", "js", "html", "net", "org")
    }
}

class UrlRule(val pattern: String, val exception: Boolean) {
    var thirdParty: Boolean? = null
    var includeTypes = 0
    var excludeTypes = 0
    val includeDomains = HashSet<String>(0)
    val excludeDomains = HashSet<String>(0)

    fun isSimple() = thirdParty == null && includeTypes == 0 && excludeTypes == 0 &&
        includeDomains.isEmpty() && excludeDomains.isEmpty()

    private val regex: Regex by lazy { Regex(toRegex(pattern)) }

    fun matches(lurl: String, host: String, pageHost: String, third: Boolean, type: Int): Boolean {
        thirdParty?.let { if (it != third) return false }
        if (includeTypes != 0 && (includeTypes and type) == 0) return false
        if (excludeTypes != 0 && (excludeTypes and type) != 0) return false
        if (includeDomains.isNotEmpty() || excludeDomains.isNotEmpty()) {
            val sfx = FilterEngine.suffixes(pageHost)
            if (sfx.any { it in excludeDomains }) return false
            if (includeDomains.isNotEmpty() && sfx.none { it in includeDomains }) return false
        }
        return regex.containsMatchIn(lurl)
    }

    companion object {
        fun toRegex(p: String): String {
            val sb = StringBuilder()
            var s = p
            var startAnchor = false
            var endAnchor = false
            if (s.startsWith("||")) {
                sb.append("^[a-z][a-z0-9+.-]*://(?:[^/?#]*\\.)?"); s = s.substring(2)
            } else if (s.startsWith("|")) {
                startAnchor = true; s = s.substring(1)
            }
            if (s.endsWith("|")) { endAnchor = true; s = s.dropLast(1) }
            if (startAnchor) sb.append('^')
            for (c in s) {
                when (c) {
                    '*' -> sb.append(".*")
                    '^' -> sb.append("(?:[^a-z0-9_.%-]|$)")
                    else -> if ("\\.[]{}()+?|\$".indexOf(c) >= 0) sb.append('\\').append(c) else sb.append(c)
                }
            }
            if (endAnchor) sb.append('$')
            return sb.toString()
        }
    }
}
