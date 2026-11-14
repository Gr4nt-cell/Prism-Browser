package app.prism.browser.video

import app.prism.browser.core.Obs
import app.prism.browser.core.ObsList
import android.net.Uri
import app.prism.browser.download.Downloader
import app.prism.browser.download.Downloader.await
import okhttp3.Request
import java.util.Locale

enum class MediaKind { VIDEO, AUDIO, HLS }

class MediaItem(
    val url: String,
    val kind: MediaKind,
    title: String,
    val pageUrl: String?,
    val headers: Map<String, String>,
) {
    var title by Obs(title)
    var size by Obs(-1L)
    var mime by Obs<String?>(null)
    var variants by Obs<List<Hls.Variant>>(emptyList())
    var duration by Obs(0.0)
    var probed by Obs(false)
    var probeError by Obs<String?>(null)

    val ext: String
        get() = when (kind) {
            MediaKind.HLS -> "mp4"
            else -> Uri.parse(url).lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
                ?.takeIf { it.length in 2..4 } ?: if (kind == MediaKind.AUDIO) "mp3" else "mp4"
        }

    /** Fetch size / variants so the sheet can show quality and size. */
    suspend fun probe() {
        if (probed) return
        try {
            val b = Request.Builder().url(url)
            headers.forEach { (k, v) -> b.header(k, v) }
            if (kind == MediaKind.HLS) {
                Downloader.client.newCall(b.build()).await().use { r ->
                    val text = r.body?.string().orEmpty()
                    if (Hls.isMaster(text)) {
                        variants = Hls.parseMaster(r.request.url.toString(), text)
                    } else {
                        duration = Hls.parseMedia(r.request.url.toString(), text).duration
                    }
                }
            } else {
                Downloader.client.newCall(b.header("Range", "bytes=0-0").build()).await().use { r ->
                    mime = r.header("Content-Type")
                    size = r.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                        ?: r.body?.contentLength() ?: -1
                }
            }
        } catch (e: Exception) {
            probeError = e.message
        }
        probed = true
    }
}

object MediaSniffer {
    private val VIDEO_EXT = setOf("mp4", "webm", "m4v", "mov", "mkv", "3gp", "flv", "avi", "ogv")
    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "ogg", "oga", "wav", "flac", "opus")

    /** Hosts that serve split audio/video chunks which can't be saved as a single file. */
    private val SKIP_HOSTS = listOf("googlevideo.com", "doubleclick.net", "googlesyndication.com")

    fun classify(uri: Uri, accept: String? = null): MediaKind? {
        val scheme = uri.scheme ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        if (SKIP_HOSTS.any { host == it || host.endsWith(".$it") }) return null
        val path = (uri.path ?: "").lowercase(Locale.ROOT)
        val ext = path.substringAfterLast('/').substringAfterLast('.', "")
        val query = (uri.query ?: "").lowercase(Locale.ROOT)
        return when {
            ext == "m3u8" || query.contains(".m3u8") || query.contains("format=m3u8") -> MediaKind.HLS
            ext == "m4s" || ext == "ts" || path.contains("/segment") || path.contains("/frag") -> null
            query.contains("range=") || query.contains("bytestart=") -> null // chunked progressive
            ext in VIDEO_EXT -> MediaKind.VIDEO
            ext in AUDIO_EXT -> MediaKind.AUDIO
            accept?.startsWith("video/") == true -> MediaKind.VIDEO
            else -> null
        }
    }

    /** Variant playlists of an already-listed master are hidden to keep the list tidy. */
    fun looksLikeVariantOf(existing: List<MediaItem>, url: String): Boolean {
        val dir = url.substringBeforeLast('/')
        return existing.any { it.kind == MediaKind.HLS && it.url != url && it.url.substringBeforeLast('/') == dir }
    }
}
