package com.gr4nt.browser.download

import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import java.net.URLDecoder
import java.util.Locale

/**
 * Works out what is behind any download link before it starts: the real file name
 * (Content-Disposition incl. RFC 5987 `filename*`), size, MIME type, whether the server can
 * resume, and — by sniffing the first bytes — whether it is really an HLS/DASH stream or an
 * HTML page (e.g. a "confirm download" or login page) rather than a file.
 */
object LinkResolver {

    enum class Scheme { HTTP, FTP, DATA, BLOB, LOCAL, UNSUPPORTED }

    data class Plan(
        val url: String,
        val finalUrl: String,
        val fileName: String,
        val mime: String?,
        val size: Long,
        val kind: DlKind,
        val resumable: Boolean,
        val isWebPage: Boolean,
        val error: String? = null
    )

    fun schemeOf(url: String): Scheme {
        val s = url.substringBefore(':').lowercase(Locale.ROOT)
        return when (s) {
            "http", "https" -> Scheme.HTTP
            "ftp" -> Scheme.FTP
            "data" -> Scheme.DATA
            "blob" -> Scheme.BLOB
            "content", "file" -> Scheme.LOCAL
            else -> Scheme.UNSUPPORTED
        }
    }

    /** Pull a usable link out of pasted text ("Check this out: https://…"). */
    fun extractLink(text: String): String? {
        val t = text.trim()
        if (schemeOf(t) != Scheme.UNSUPPORTED && !t.contains(' ')) return t
        return Regex("""(https?|ftp)://[^\s"'<>]+""", RegexOption.IGNORE_CASE).find(t)?.value
            ?: if (Regex("""^[\w-]+(\.[\w-]+)+(/\S*)?$""").matches(t)) "https://$t" else null
    }

    /** filename from Content-Disposition, preferring the UTF-8 `filename*` form. */
    fun nameFromDisposition(cd: String?): String? {
        if (cd.isNullOrBlank()) return null
        Regex("""filename\*\s*=\s*([^']*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE).find(cd)?.let { m ->
            val charset = m.groupValues[1].ifBlank { "UTF-8" }
            return runCatching { URLDecoder.decode(m.groupValues[2].trim().trim('"'), charset) }.getOrNull()?.takeIf { it.isNotBlank() }
        }
        Regex("""filename\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE).find(cd)?.let { return it.groupValues[1] }
        Regex("""filename\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE).find(cd)?.let { return it.groupValues[1].trim() }
        return null
    }

    fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().trim('.').take(150).ifBlank { "download" }

    /** Best file name from all the hints we have. */
    fun bestName(url: String, cd: String?, mime: String?): String {
        var n = nameFromDisposition(cd)
        if (n == null) {
            val seg = runCatching { Uri.parse(url).lastPathSegment }.getOrNull()
            if (!seg.isNullOrBlank() && seg.contains('.')) n = seg
        }
        if (n == null) n = URLUtil.guessFileName(url, cd, mime)
        n = sanitize(n!!)
        val ext = n.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mimeExt = mime?.substringBefore(';')?.trim()?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        if ((ext.isEmpty() || ext == "bin") && mimeExt != null) n = n.substringBeforeLast('.', n) + "." + mimeExt
        return n
    }

    fun mimeFromName(name: String): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase(Locale.ROOT))

    /** Probe an http(s) link. Blocking — call from a background thread. */
    fun resolve(url: String, userAgent: String?, referer: String?, mimeHint: String? = null, cdHint: String? = null): Plan {
        when (schemeOf(url)) {
            Scheme.HTTP -> {}
            Scheme.FTP -> {
                val name = bestName(url, cdHint, mimeHint)
                return Plan(url, url, name, mimeHint ?: mimeFromName(name), -1, DlKind.FILE, false, false)
            }
            Scheme.DATA -> {
                val mime = url.removePrefix("data:").substringBefore(',').substringBefore(';').ifBlank { "application/octet-stream" }
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
                return Plan(url, url, "download.$ext", mime, -1, DlKind.BLOB, false, false)
            }
            Scheme.LOCAL -> {
                val name = sanitize(Uri.parse(url).lastPathSegment ?: "file")
                return Plan(url, url, name, mimeHint ?: mimeFromName(name), -1, DlKind.LOCAL, true, false)
            }
            Scheme.BLOB -> return Plan(url, url, "download", mimeHint, -1, DlKind.BLOB, false, false)
            Scheme.UNSUPPORTED -> return Plan(url, url, "download", null, -1, DlKind.FILE, false, false,
                error = if (url.startsWith("magnet:")) "Torrent / magnet links aren't supported" else "This kind of link can't be downloaded")
        }

        val probe = DownloadItem("probe", url, "x", DlKind.FILE, mimeHint, userAgent, referer, 1)
        return try {
            val req = DownloadManager.request(probe, url).header("Range", "bytes=0-").build()
            DownloadManager.http.newCall(req).execute().use { r ->
                if (!r.isSuccessful && r.code != 416) {
                    return Plan(url, url, bestName(url, cdHint, mimeHint), mimeHint, -1, DownloadManager.kindFor(url, mimeHint), false, false,
                        error = when (r.code) {
                            401, 403 -> "The server refused access (HTTP ${r.code}) — the link may have expired or need you to sign in"
                            404, 410 -> "File not found on the server (HTTP ${r.code})"
                            else -> "Server error (HTTP ${r.code})"
                        })
                }
                val finalUrl = r.request.url.toString()
                val ct = r.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
                val cd = r.header("Content-Disposition") ?: cdHint
                val cr = r.header("Content-Range")
                val size = when {
                    cr != null && cr.contains('/') -> cr.substringAfter('/').trim().toLongOrNull() ?: -1
                    else -> r.body?.contentLength() ?: -1
                }
                val ranges = r.code == 206 || r.header("Accept-Ranges")?.contains("bytes", true) == true
                // sniff the first bytes (does not download the whole body)
                val head = runCatching {
                    val src = r.body!!.source()
                    src.request(512)
                    val n = minOf(512L, src.buffer.size)
                    src.buffer.snapshot(n.toInt()).utf8()
                }.getOrDefault("")
                val lead = head.trimStart('﻿', ' ', '\n', '\r', '\t')
                val kind = when {
                    lead.startsWith("#EXTM3U") || ct?.contains("mpegurl") == true -> DlKind.HLS
                    lead.startsWith("<?xml") && head.contains("<MPD", true) || lead.startsWith("<MPD", true) || ct == "application/dash+xml" -> DlKind.DASH
                    else -> DownloadManager.kindFor(finalUrl, ct)
                }
                val isHtml = kind == DlKind.FILE && cd == null &&
                    (ct == "text/html" || ct == "application/xhtml+xml" ||
                        lead.startsWith("<!doctype html", true) || lead.startsWith("<html", true))
                var name = bestName(finalUrl.takeIf { cd == null && Uri.parse(url).lastPathSegment?.contains('.') != true } ?: url, cd, ct ?: mimeHint)
                if (kind != DlKind.FILE) name = name.substringBeforeLast('.', name) + ".mp4"
                Plan(url, finalUrl, name, ct ?: mimeHint ?: mimeFromName(name), if (kind == DlKind.FILE) size else -1, kind, ranges && size > 0, isHtml)
            }
        } catch (e: Exception) {
            Plan(url, url, bestName(url, cdHint, mimeHint), mimeHint, -1, DownloadManager.kindFor(url, mimeHint), false, false,
                error = "Couldn't reach the server: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
