package com.ibrowser.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ibrowser.download.DownloadManager
import okhttp3.Request
import java.util.concurrent.Executors

enum class MediaKind { HLS, DASH, VIDEO, AUDIO }

class MediaFound(
    val url: String,
    val kind: MediaKind,
    val pageTitle: String,
    val pageUrl: String,
    mime: String?
) {
    var mime by mutableStateOf(mime)
    var size by mutableStateOf(-1L)
    var info by mutableStateOf("")   // e.g. "1080p · 4 qualities"

    val extension: String
        get() = when (kind) {
            MediaKind.HLS -> "m3u8"
            MediaKind.DASH -> "mpd"
            else -> url.substringBefore('?').substringAfterLast('.', "").lowercase().take(5).ifEmpty {
                if (kind == MediaKind.AUDIO) "mp3" else "mp4"
            }
        }
}

object MediaSniffer {
    private val probe = Executors.newFixedThreadPool(3)

    private val VIDEO_EXT = Regex("\\.(mp4|m4v|webm|mkv|mov|flv|3gp|avi)(\\?|#|$)", RegexOption.IGNORE_CASE)
    private val AUDIO_EXT = Regex("\\.(mp3|m4a|aac|ogg|oga|opus|wav|flac)(\\?|#|$)", RegexOption.IGNORE_CASE)
    private val HLS_EXT = Regex("\\.m3u8(\\?|#|$)", RegexOption.IGNORE_CASE)
    private val DASH_EXT = Regex("\\.mpd(\\?|#|$)", RegexOption.IGNORE_CASE)
    private val SEGMENT = Regex("\\.(ts|m4s|m4f|cmfv|cmfa)(\\?|#|$)|(segment|chunk|frag)[-_]?\\d+|/range/\\d|[?&]range=\\d", RegexOption.IGNORE_CASE)

    fun classify(url: String, mime: String?): MediaKind? {
        val m = mime?.lowercase() ?: ""
        if (url.startsWith("blob:") || url.startsWith("data:")) return null
        return when {
            m.contains("mpegurl") || HLS_EXT.containsMatchIn(url) -> MediaKind.HLS
            m.contains("dash+xml") || DASH_EXT.containsMatchIn(url) -> MediaKind.DASH
            SEGMENT.containsMatchIn(url) -> null
            m.startsWith("video/") || VIDEO_EXT.containsMatchIn(url) -> MediaKind.VIDEO
            m.startsWith("audio/") || AUDIO_EXT.containsMatchIn(url) -> MediaKind.AUDIO
            else -> null
        }
    }

    /** Resolve size / quality info in the background. */
    fun enrich(item: MediaFound, userAgent: String?) {
        probe.execute {
            runCatching {
                when (item.kind) {
                    MediaKind.HLS -> {
                        val d = tmpItem(item, userAgent)
                        val text = DownloadManager.fetchText(d, item.url)
                        if (com.ibrowser.download.HlsParser.isMaster(text)) {
                            val m = com.ibrowser.download.HlsParser.parseMaster(text, item.url)
                            val best = m.variants.firstOrNull()
                            item.info = buildString {
                                append("HLS stream")
                                best?.let { if (it.height > 0) append(" · up to ${it.height}p") }
                                append(" · ${m.variants.size} qualities")
                            }
                        } else {
                            val media = com.ibrowser.download.HlsParser.parseMedia(text, item.url)
                            val mins = (media.duration / 60).toInt()
                            val secs = (media.duration % 60).toInt()
                            item.info = "HLS stream · ${media.segments.size} segments · %d:%02d".format(mins, secs) +
                                if (media.segments.any { it.keyMethod != null && it.keyMethod != "AES-128" }) " · DRM" else ""
                        }
                    }
                    MediaKind.DASH -> {
                        val d = tmpItem(item, userAgent)
                        val reps = com.ibrowser.download.DashParser.parse(DownloadManager.fetchText(d, item.url), item.url)
                        val v = reps.filter { it.type == "video" }.maxByOrNull { it.height }
                        item.info = "DASH stream" + (v?.let { " · up to ${it.height}p" } ?: "") +
                            if (reps.any { it.protected }) " · DRM" else ""
                    }
                    else -> {
                        val req = Request.Builder().url(item.url).head()
                            .header("User-Agent", userAgent ?: DownloadManager.DEFAULT_UA)
                            .header("Referer", item.pageUrl).build()
                        DownloadManager.http.newCall(req).execute().use { r ->
                            item.size = r.header("Content-Length")?.toLongOrNull() ?: -1
                            r.header("Content-Type")?.let { if (item.mime.isNullOrBlank() || item.mime!!.endsWith("*")) item.mime = it }
                        }
                    }
                }
            }
        }
    }

    private fun tmpItem(item: MediaFound, ua: String?) = com.ibrowser.download.DownloadItem(
        "probe", item.url, "probe", com.ibrowser.download.DlKind.FILE, item.mime, ua, item.pageUrl, 1
    )
}
