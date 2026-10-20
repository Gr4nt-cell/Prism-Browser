package com.gr4nt.browser.download

import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer

object HlsParser {
    data class Variant(
        val url: String, val bandwidth: Long, val resolution: String?, val audioGroup: String?,
        val codecs: String?, val frameRate: String?
    ) {
        val height: Int get() = resolution?.substringAfter('x')?.toIntOrNull() ?: 0
        val label: String
            get() = buildString {
                if (height > 0) append("${height}p") else append("Auto")
                if (frameRate != null && (frameRate.toDoubleOrNull() ?: 0.0) > 31) append(frameRate.substringBefore('.'))
                append(" · ").append(String.format("%.1f Mbps", bandwidth / 1_000_000.0))
            }
    }

    data class Rendition(val group: String, val url: String?, val name: String, val lang: String?, val isDefault: Boolean)
    data class Master(val variants: List<Variant>, val audio: List<Rendition>)

    data class Segment(
        val uri: String, val duration: Double, val seq: Long,
        val keyMethod: String?, val keyUri: String?, val keyIv: ByteArray?, val keyFormat: String?,
        val range: String?
    )

    data class Media(
        val segments: List<Segment>, val initUri: String?, val initRange: String?,
        val endList: Boolean, val duration: Double
    )

    private val ATTR = Regex("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)")

    fun attrs(line: String): Map<String, String> =
        ATTR.findAll(line.substringAfter(':')).associate { it.groupValues[1] to it.groupValues[2].trim('"') }

    fun resolve(base: String, ref: String): String =
        runCatching { URI(base).resolve(ref.trim().replace(" ", "%20")).toString() }.getOrDefault(ref)

    fun isMaster(text: String) = text.contains("#EXT-X-STREAM-INF")

    fun parseMaster(text: String, base: String): Master {
        val vs = ArrayList<Variant>()
        val aud = ArrayList<Rendition>()
        val lines = text.lines().map { it.trim() }
        var pending: Map<String, String>? = null
        for (l in lines) {
            when {
                l.startsWith("#EXT-X-STREAM-INF") -> pending = attrs(l)
                l.startsWith("#EXT-X-MEDIA:") -> {
                    val a = attrs(l)
                    if (a["TYPE"] == "AUDIO") aud.add(
                        Rendition(a["GROUP-ID"] ?: "", a["URI"]?.let { resolve(base, it) }, a["NAME"] ?: "Audio",
                            a["LANGUAGE"], a["DEFAULT"] == "YES")
                    )
                }
                l.isNotEmpty() && !l.startsWith("#") && pending != null -> {
                    val a = pending!!
                    vs.add(
                        Variant(resolve(base, l), a["BANDWIDTH"]?.toLongOrNull() ?: a["AVERAGE-BANDWIDTH"]?.toLongOrNull() ?: 0,
                            a["RESOLUTION"], a["AUDIO"], a["CODECS"], a["FRAME-RATE"])
                    )
                    pending = null
                }
            }
        }
        return Master(vs.sortedByDescending { it.bandwidth }, aud)
    }

    fun audioFor(m: Master, v: Variant): String? {
        val g = v.audioGroup ?: return null
        val inGroup = m.audio.filter { it.group == g && it.url != null }
        return (inGroup.firstOrNull { it.isDefault } ?: inGroup.firstOrNull())?.url
    }

    fun parseMedia(text: String, base: String): Media {
        val segs = ArrayList<Segment>()
        var seq = 0L
        var dur = 0.0
        var keyMethod: String? = null
        var keyUri: String? = null
        var keyIv: ByteArray? = null
        var keyFormat: String? = null
        var range: String? = null
        var initUri: String? = null
        var initRange: String? = null
        var endList = false
        var total = 0.0
        val lastEnd = HashMap<String, Long>()
        for (raw in text.lines()) {
            val l = raw.trim()
            when {
                l.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> seq = l.substringAfter(':').trim().toLongOrNull() ?: 0
                l.startsWith("#EXTINF:") -> dur = l.substringAfter(':').substringBefore(',').toDoubleOrNull() ?: 0.0
                l.startsWith("#EXT-X-KEY:") -> {
                    val a = attrs(l)
                    keyMethod = a["METHOD"]
                    keyUri = a["URI"]?.let { resolve(base, it) }
                    keyFormat = a["KEYFORMAT"]
                    keyIv = a["IV"]?.let { hex(it) }
                    if (keyMethod == "NONE") { keyUri = null; keyIv = null }
                }
                l.startsWith("#EXT-X-MAP:") -> {
                    val a = attrs(l)
                    initUri = a["URI"]?.let { resolve(base, it) }
                    initRange = a["BYTERANGE"]?.let { br ->
                        val len = br.substringBefore('@').toLong()
                        val off = br.substringAfter('@', "0").toLong()
                        "$off-${off + len - 1}"
                    }
                }
                l.startsWith("#EXT-X-BYTERANGE:") -> range = l.substringAfter(':')
                l.startsWith("#EXT-X-ENDLIST") -> endList = true
                l.isNotEmpty() && !l.startsWith("#") -> {
                    val uri = resolve(base, l)
                    val r = range?.let { br ->
                        val len = br.substringBefore('@').toLong()
                        val off = if (br.contains('@')) br.substringAfter('@').toLong() else lastEnd[uri] ?: 0L
                        lastEnd[uri] = off + len
                        "$off-${off + len - 1}"
                    }
                    segs.add(Segment(uri, dur, seq, keyMethod, keyUri, keyIv, keyFormat, r))
                    total += dur
                    seq++; range = null
                }
            }
        }
        return Media(segs, initUri, initRange, endList, total)
    }

    private fun hex(s: String): ByteArray {
        val h = s.removePrefix("0x").removePrefix("0X").padStart(32, '0')
        return ByteArray(16) { i -> h.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    fun ivFromSeq(seq: Long): ByteArray = ByteBuffer.allocate(16).putLong(0).putLong(seq).array()
}

class HlsDownloader(private val d: DownloadItem) {

    suspend fun run() {
        val work = DownloadManager.workDir(d.id)
        val fetcher = SegmentFetcher(d, work)
        d.detail = "Reading playlist"

        var playlistUrl = d.variant ?: d.url
        var audioUrl = d.audio
        var text = DownloadManager.fetchText(d, playlistUrl)
        if (HlsParser.isMaster(text)) {
            val m = HlsParser.parseMaster(text, playlistUrl)
            val v = m.variants.firstOrNull() ?: throw IOException("No playable variants")
            playlistUrl = v.url
            if (audioUrl == null) audioUrl = HlsParser.audioFor(m, v)
            text = DownloadManager.fetchText(d, playlistUrl)
        }
        val video = HlsParser.parseMedia(text, playlistUrl)
        val audio = audioUrl?.let { HlsParser.parseMedia(DownloadManager.fetchText(d, it), it) }

        for (media in listOfNotNull(video, audio)) {
            if (media.segments.any {
                    (it.keyMethod != null && it.keyMethod != "AES-128") ||
                        (it.keyFormat != null && it.keyFormat != "identity")
                }) throw DrmException("This stream is DRM protected and can't be downloaded")
        }
        if (video.segments.isEmpty()) throw IOException("Empty playlist")

        fetcher.totalSegments = video.segments.size + (audio?.segments?.size ?: 0)

        val vFile = downloadTrack(fetcher, video, "v", work)
        val aFile = audio?.let { downloadTrack(fetcher, it, "a", work) }

        d.status = DlStatus.CONVERTING
        d.detail = "Converting to MP4"
        val target = d.path?.let { File(it) } ?: DownloadManager.uniqueFile(d.fileName)
        val ok = Muxer.remux(listOfNotNull(vFile, aFile), target)
        if (ok) {
            d.path = target.absolutePath
        } else {
            // Fallback: keep the stream container (plays in VLC / most players)
            val isFmp4 = video.initUri != null
            val ext = if (isFmp4) "mp4" else "ts"
            val fb = DownloadManager.uniqueFile(target.nameWithoutExtension + "." + ext)
            vFile.copyTo(fb, overwrite = true)
            d.path = fb.absolutePath
            d.fileName = fb.name
            if (aFile != null) {
                val af = DownloadManager.uniqueFile(target.nameWithoutExtension + " (audio)." + if (isFmp4) "m4a" else "ts")
                aFile.copyTo(af, overwrite = true)
            }
        }
        d.downloaded = File(d.path!!).length()
        d.total = d.downloaded
        d.detail = if (video.endList) "" else "Live stream: saved the available part"
    }

    private suspend fun downloadTrack(f: SegmentFetcher, m: HlsParser.Media, prefix: String, work: File): File {
        val specs = m.segments.map { s ->
            SegSpec(
                s.uri, s.range,
                if (s.keyMethod == "AES-128") s.keyUri else null,
                if (s.keyMethod == "AES-128") (s.keyIv ?: HlsParser.ivFromSeq(s.seq)) else null
            )
        }
        var init: File? = null
        if (m.initUri != null) {
            init = File(work, "${prefix}_init.mp4")
            val firstKey = m.segments.firstOrNull()
            f.fetchFile(
                SegSpec(m.initUri, m.initRange,
                    if (firstKey?.keyMethod == "AES-128" && firstKey.keyIv != null) firstKey.keyUri else null,
                    firstKey?.keyIv), init
            )
        }
        f.fetchAll(prefix, specs, d.threads)
        val out = File(work, if (m.initUri != null) "$prefix.mp4" else "$prefix.ts")
        f.concat(prefix, specs.size, out, init)
        return out
    }
}
