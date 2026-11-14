package app.prism.browser.video

import java.net.URI

object Hls {
    data class Variant(val url: String, val bandwidth: Long, val resolution: String?, val name: String?) {
        val label: String
            get() {
                val h = resolution?.substringAfter('x')?.toIntOrNull()
                return when {
                    h != null -> "${h}p"
                    name != null -> name
                    bandwidth > 0 -> "${bandwidth / 1000} kbps"
                    else -> "Stream"
                }
            }
    }

    data class Key(val method: String, val uri: String?, val iv: ByteArray?)
    data class Seg(val url: String, val duration: Double, val key: Key?, val seq: Long, val byteRange: LongRange?)
    data class Media(val segments: List<Seg>, val initUrl: String?, val initRange: LongRange?, val duration: Double)

    fun isMaster(text: String) = text.contains("#EXT-X-STREAM-INF")

    fun resolve(base: String, ref: String): String = try {
        URI(base).resolve(ref.trim()).toString()
    } catch (e: Exception) {
        if (ref.startsWith("http")) ref else base.substringBeforeLast('/') + "/" + ref
    }

    private fun attrs(line: String): Map<String, String> {
        val out = HashMap<String, String>()
        val body = line.substringAfter(':')
        val re = Regex("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)")
        re.findAll(body).forEach { m -> out[m.groupValues[1]] = m.groupValues[2].trim('"') }
        return out
    }

    fun parseMaster(base: String, text: String): List<Variant> {
        val lines = text.lines().map { it.trim() }
        val out = ArrayList<Variant>()
        var i = 0
        while (i < lines.size) {
            val l = lines[i]
            if (l.startsWith("#EXT-X-STREAM-INF")) {
                val a = attrs(l)
                var j = i + 1
                while (j < lines.size && (lines[j].isEmpty() || lines[j].startsWith("#"))) j++
                if (j < lines.size) {
                    out += Variant(resolve(base, lines[j]), a["BANDWIDTH"]?.toLongOrNull() ?: 0, a["RESOLUTION"], a["NAME"])
                }
                i = j
            }
            i++
        }
        return out.distinctBy { it.url }.sortedByDescending { it.bandwidth }
    }

    private fun parseRange(v: String, lastEnd: Long): LongRange {
        val len = v.substringBefore('@').toLong()
        val start = if (v.contains('@')) v.substringAfter('@').toLong() else lastEnd
        return start until start + len
    }

    fun parseMedia(base: String, text: String): Media {
        val segs = ArrayList<Seg>()
        var key: Key? = null
        var dur = 0.0
        var seq = 0L
        var init: String? = null
        var initRange: LongRange? = null
        var range: LongRange? = null
        var lastEnd = 0L
        var total = 0.0
        for (raw in text.lines()) {
            val l = raw.trim()
            when {
                l.startsWith("#EXT-X-MEDIA-SEQUENCE") -> seq = l.substringAfter(':').trim().toLongOrNull() ?: 0
                l.startsWith("#EXT-X-KEY") -> {
                    val a = attrs(l)
                    val method = a["METHOD"] ?: "NONE"
                    key = if (method == "NONE") null else Key(
                        method, a["URI"]?.let { resolve(base, it) },
                        a["IV"]?.let { hex(it) },
                    )
                }
                l.startsWith("#EXT-X-MAP") -> {
                    val a = attrs(l)
                    init = a["URI"]?.let { resolve(base, it) }
                    initRange = a["BYTERANGE"]?.let { parseRange(it, 0) }
                }
                l.startsWith("#EXTINF") -> dur = l.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() ?: 0.0
                l.startsWith("#EXT-X-BYTERANGE") -> range = parseRange(l.substringAfter(':').trim(), lastEnd)
                l.isNotEmpty() && !l.startsWith("#") -> {
                    segs += Seg(resolve(base, l), dur, key, seq, range)
                    range?.let { lastEnd = it.last + 1 }
                    total += dur
                    seq++
                    dur = 0.0
                    range = null
                }
            }
        }
        return Media(segs, init, initRange, total)
    }

    private fun hex(s: String): ByteArray {
        val h = s.removePrefix("0x").removePrefix("0X").padStart(32, '0')
        return ByteArray(16) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
