package com.ibrowser.download

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil

object DashParser {
    data class Rep(
        val id: String,
        val type: String,           // "video" | "audio" | "text"
        val bandwidth: Long,
        val width: Int,
        val height: Int,
        val codecs: String?,
        val lang: String?,
        val init: SegSpec?,
        val segments: List<SegSpec>,
        val protected: Boolean
    ) {
        val label: String
            get() = if (type == "video") "${height}p · ${String.format("%.1f Mbps", bandwidth / 1_000_000.0)}"
            else "${lang ?: "Audio"} · ${bandwidth / 1000} kbps"
    }

    private fun children(e: Element, name: String): List<Element> {
        val out = ArrayList<Element>()
        val nl = e.childNodes
        for (i in 0 until nl.length) {
            val n = nl.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && (n.localName ?: n.nodeName).substringAfter(':') == name) out.add(n as Element)
        }
        return out
    }

    private fun child(e: Element?, name: String) = e?.let { children(it, name).firstOrNull() }
    private fun Element.attr(n: String): String? = getAttribute(n).ifEmpty { null }

    private fun baseOf(parent: String, e: Element): String {
        val b = child(e, "BaseURL")?.textContent?.trim()
        return if (b.isNullOrEmpty()) parent else HlsParser.resolve(parent, b)
    }

    fun parseDuration(s: String?): Double {
        if (s == null) return 0.0
        val m = Regex("P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?(?:([\\d.]+)S)?").find(s) ?: return 0.0
        val (dd, h, mi, se) = m.destructured
        return (dd.toDoubleOrNull() ?: 0.0) * 86400 + (h.toDoubleOrNull() ?: 0.0) * 3600 +
            (mi.toDoubleOrNull() ?: 0.0) * 60 + (se.toDoubleOrNull() ?: 0.0)
    }

    private fun fill(t: String, repId: String, bw: Long, number: Long?, time: Long?): String {
        return Regex("\\$(RepresentationID|Bandwidth|Number|Time)(%0(\\d+)d)?\\$|\\$\\$").replace(t) { m ->
            if (m.value == "$$") return@replace "$"
            val v = when (m.groupValues[1]) {
                "RepresentationID" -> return@replace repId
                "Bandwidth" -> bw
                "Number" -> number ?: 0
                "Time" -> time ?: 0
                else -> 0
            }
            val w = m.groupValues[3].toIntOrNull()
            if (w != null) v.toString().padStart(w, '0') else v.toString()
        }
    }

    fun parse(xml: String, mpdUrl: String): List<Rep> {
        val f = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = f.newDocumentBuilder().parse(xml.byteInputStream())
        val mpd = doc.documentElement
        val total = parseDuration(mpd.attr("mediaPresentationDuration"))
        val mpdBase = baseOf(mpdUrl, mpd)
        val period = children(mpd, "Period").firstOrNull() ?: return emptyList()
        val periodDur = parseDuration(period.attr("duration")).takeIf { it > 0 } ?: total
        val periodBase = baseOf(mpdBase, period)
        val reps = ArrayList<Rep>()

        for (aset in children(period, "AdaptationSet")) {
            val asBase = baseOf(periodBase, aset)
            val asProtected = children(aset, "ContentProtection").isNotEmpty()
            val asTemplate = child(aset, "SegmentTemplate")
            val asList = child(aset, "SegmentList")
            val asMime = aset.attr("mimeType") ?: ""
            val asType = aset.attr("contentType") ?: asMime.substringBefore('/')
            for (rep in children(aset, "Representation")) {
                val id = rep.attr("id") ?: continue
                val bw = rep.attr("bandwidth")?.toLongOrNull() ?: 0
                val mime = rep.attr("mimeType") ?: asMime
                val type = (asType.ifEmpty { mime.substringBefore('/') }).ifEmpty {
                    if ((rep.attr("width") ?: aset.attr("width")) != null) "video" else "audio"
                }
                if (type != "video" && type != "audio") continue
                val base = baseOf(asBase, rep)
                val prot = asProtected || children(rep, "ContentProtection").isNotEmpty()
                val tpl = child(rep, "SegmentTemplate") ?: asTemplate
                val sl = child(rep, "SegmentList") ?: asList
                var init: SegSpec? = null
                val segs = ArrayList<SegSpec>()

                if (tpl != null) {
                    fun ta(n: String) = child(rep, "SegmentTemplate")?.attr(n) ?: asTemplate?.attr(n)
                    val media = ta("media") ?: continue
                    val timescale = ta("timescale")?.toLongOrNull() ?: 1L
                    val startNumber = ta("startNumber")?.toLongOrNull() ?: 1L
                    ta("initialization")?.let { init = SegSpec(HlsParser.resolve(base, fill(it, id, bw, null, null))) }
                    val timeline = child(child(rep, "SegmentTemplate"), "SegmentTimeline") ?: child(asTemplate, "SegmentTimeline")
                    if (timeline != null) {
                        var t = 0L
                        var num = startNumber
                        for (s in children(timeline, "S")) {
                            s.attr("t")?.toLongOrNull()?.let { t = it }
                            val dd = s.attr("d")?.toLongOrNull() ?: continue
                            var r = s.attr("r")?.toLongOrNull() ?: 0L
                            if (r < 0) r = if (periodDur > 0) (ceil((periodDur * timescale - t) / dd).toLong() - 1).coerceAtLeast(0) else 0
                            for (k in 0..r) {
                                segs.add(SegSpec(HlsParser.resolve(base, fill(media, id, bw, num, t))))
                                t += dd; num++
                            }
                        }
                    } else {
                        val dur = ta("duration")?.toLongOrNull() ?: continue
                        val count = if (periodDur > 0) ceil(periodDur * timescale / dur).toLong() else 0L
                        for (k in 0 until count) {
                            segs.add(SegSpec(HlsParser.resolve(base, fill(media, id, bw, startNumber + k, k * dur))))
                        }
                    }
                } else if (sl != null) {
                    child(sl, "Initialization")?.let { ie ->
                        init = SegSpec(HlsParser.resolve(base, ie.attr("sourceURL") ?: ""), ie.attr("range"))
                    }
                    for (su in children(sl, "SegmentURL")) {
                        segs.add(SegSpec(HlsParser.resolve(base, su.attr("media") ?: ""), su.attr("mediaRange")))
                    }
                } else {
                    // single file (SegmentBase / plain BaseURL)
                    segs.add(SegSpec(base))
                }

                reps.add(
                    Rep(
                        id, type, bw,
                        rep.attr("width")?.toIntOrNull() ?: aset.attr("width")?.toIntOrNull() ?: 0,
                        rep.attr("height")?.toIntOrNull() ?: aset.attr("height")?.toIntOrNull() ?: 0,
                        rep.attr("codecs") ?: aset.attr("codecs"), aset.attr("lang"),
                        init, segs, prot
                    )
                )
            }
        }
        return reps
    }
}

class DashDownloader(private val d: DownloadItem) {
    suspend fun run() {
        val work = DownloadManager.workDir(d.id)
        d.detail = "Reading manifest"
        val reps = DashParser.parse(DownloadManager.fetchText(d, d.url), d.url)
        val videos = reps.filter { it.type == "video" }.sortedByDescending { it.bandwidth }
        val audios = reps.filter { it.type == "audio" }.sortedByDescending { it.bandwidth }
        val v = videos.firstOrNull { it.id == d.variant } ?: videos.firstOrNull()
        val a = audios.firstOrNull { it.id == d.audio } ?: audios.firstOrNull()
        if (v == null && a == null) throw IOException("No downloadable streams in manifest")
        if (v?.protected == true || a?.protected == true) throw DrmException("This stream is DRM protected and can't be downloaded")

        val fetcher = SegmentFetcher(d, work)
        fetcher.totalSegments = (v?.segments?.size ?: 0) + (a?.segments?.size ?: 0)
        val files = ArrayList<File>()
        v?.let { files.add(track(fetcher, it, "v", work)) }
        a?.let { files.add(track(fetcher, it, "a", work)) }

        d.status = DlStatus.CONVERTING
        d.detail = "Converting to MP4"
        val target = d.path?.let { File(it) } ?: DownloadManager.uniqueFile(d.fileName)
        if (!Muxer.remux(files, target)) {
            if (files.size == 1) files[0].copyTo(target, overwrite = true)
            else {
                files[0].copyTo(target, overwrite = true)
                files[1].copyTo(DownloadManager.uniqueFile(target.nameWithoutExtension + " (audio).m4a"), overwrite = true)
            }
        }
        d.path = target.absolutePath
        d.downloaded = target.length(); d.total = d.downloaded
        d.detail = ""
    }

    private suspend fun track(f: SegmentFetcher, r: DashParser.Rep, prefix: String, work: File): File {
        var init: File? = null
        r.init?.let { init = File(work, "${prefix}_init.mp4"); f.fetchFile(it, init!!) }
        f.fetchAll(prefix, r.segments, d.threads)
        val out = File(work, "$prefix.mp4")
        f.concat(prefix, r.segments.size, out, init)
        return out
    }
}
