package com.ibrowser.download

import android.net.Uri
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Single-stream copies for sources that aren't plain HTTP:
 *  - `content://` and `file://` (save a device file / shared file into the downloads folder)
 *  - `ftp://` (Android's built-in FTP client; resumes with REST via the "Range"-less offset skip)
 */
class LocalCopier(private val d: DownloadItem) {
    suspend fun run() {
        val target = d.path?.let { File(it) } ?: DownloadManager.uniqueFile(d.fileName).also { d.path = it.absolutePath; d.fileName = it.name }
        val part = File(target.absolutePath + ".part")
        val already = if (part.exists()) part.length() else 0L
        d.activeThreads = 1
        val input: InputStream = when (LinkResolver.schemeOf(d.url)) {
            LinkResolver.Scheme.LOCAL -> {
                val ctx = com.ibrowser.browser.Browser.activity?.applicationContext ?: throw IOException("App not ready")
                val uri = Uri.parse(d.url)
                if (d.total <= 0) runCatching {
                    ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { d.total = it.length }
                }
                ctx.contentResolver.openInputStream(uri) ?: throw IOException("Can't open the file")
            }
            LinkResolver.Scheme.FTP -> {
                val conn = URL(d.url).openConnection()
                conn.connectTimeout = 20_000; conn.readTimeout = 30_000
                if (d.total <= 0) d.total = conn.contentLengthLong
                conn.getInputStream()
            }
            else -> throw IOException("Unsupported source")
        }
        input.use { inp ->
            // no server-side resume for these sources: skip what we already have
            var skipped = 0L
            while (skipped < already) { val n = inp.skip(already - skipped); if (n <= 0) break; skipped += n }
            java.io.FileOutputStream(part, skipped > 0).use { out ->
                if (skipped == 0L) d.downloaded = 0 else d.downloaded = skipped
                val buf = ByteArray(256 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    d.downloaded += n
                }
            }
        }
        coroutineContext.ensureActive()
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) { part.copyTo(target, overwrite = true); part.delete() }
        d.path = target.absolutePath
        d.total = target.length(); d.downloaded = d.total
    }
}
