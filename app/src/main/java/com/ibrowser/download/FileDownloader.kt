package com.ibrowser.download

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * Multi-connection HTTP downloader.
 * - Probes range support; splits the file into N segments written in place.
 * - Work stealing: idle connections split the largest remaining segment.
 * - Segment offsets are persisted every second, so pause/resume survives app restarts.
 * - Falls back to a single resumable (or restartable) stream when ranges are unsupported.
 */
class FileDownloader(private val d: DownloadItem) {

    private class Part(var start: Long, @Volatile var end: Long, @Volatile var pos: Long) {
        @Volatile var active = false
        val remaining get() = end - pos + 1
        fun toJson() = JSONObject().put("s", start).put("e", end).put("p", pos)
    }

    private val lock = Any()
    private val parts = ArrayList<Part>()
    private val downloaded = AtomicLong(0)
    private val active = AtomicInteger(0)

    private val minSplit = 512 * 1024L

    suspend fun run() {
        val target = d.path?.let { File(it) } ?: DownloadManager.uniqueFile(d.fileName).also {
            d.path = it.absolutePath; d.fileName = it.name
        }
        val partFile = File(target.absolutePath + ".part")
        val stateFile = File(DownloadManager.workDir(d.id), "parts.json")

        // ----- probe -----
        var total = d.total
        var rangesOk = d.resumable
        if (total <= 0 || !stateFile.exists()) {
            val probe = DownloadManager.request(d, d.url).header("Range", "bytes=0-").build()
            DownloadManager.http.newCall(probe).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                val cr = r.header("Content-Range")
                total = when {
                    cr != null && cr.contains('/') -> cr.substringAfter('/').trim().toLongOrNull() ?: -1
                    else -> r.body?.contentLength() ?: -1
                }
                rangesOk = r.code == 206 || r.header("Accept-Ranges")?.contains("bytes") == true
            }
            d.total = total
            d.resumable = rangesOk && total > 0
        }

        if (!d.resumable || total <= 0) {
            singleStream(partFile, total)
            coroutineContext.ensureActive()
        } else {
            loadOrCreateParts(stateFile, total)
            RandomAccessFile(partFile, "rw").use { raf ->
                if (raf.length() != total) raf.setLength(total)
            }
            downloaded.set(parts.sumOf { it.pos - it.start })
            d.downloaded = downloaded.get()

            coroutineScope {
                // state saver / progress publisher
                val saver = launch {
                    while (isActive) {
                        delay(1000)
                        saveParts(stateFile)
                        d.downloaded = downloaded.get()
                        d.activeThreads = active.get()
                    }
                }
                val workers = (1..d.threads).map { launch { worker(partFile) } }
                workers.forEach { it.join() }
                saver.cancel()
            }
            saveParts(stateFile)
            d.downloaded = downloaded.get()
            coroutineContext.ensureActive()
            if (parts.any { it.pos <= it.end }) throw IOException("Incomplete download")
        }

        if (target.exists()) target.delete()
        if (!partFile.renameTo(target)) {
            partFile.copyTo(target, overwrite = true); partFile.delete()
        }
        d.path = target.absolutePath
        d.downloaded = target.length()
        if (d.total <= 0) d.total = target.length()
    }

    private fun loadOrCreateParts(stateFile: File, total: Long) {
        parts.clear()
        if (stateFile.exists()) {
            runCatching {
                val arr = JSONArray(stateFile.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    parts.add(Part(o.getLong("s"), o.getLong("e"), o.getLong("p")))
                }
            }
        }
        if (parts.isEmpty()) {
            val n = d.threads.coerceAtLeast(1).toLong().coerceAtMost((total / minSplit).coerceAtLeast(1))
            val size = total / n
            var s = 0L
            for (i in 0 until n) {
                val e = if (i == n - 1) total - 1 else s + size - 1
                parts.add(Part(s, e, s)); s = e + 1
            }
        }
    }

    private fun saveParts(f: File) {
        val arr = JSONArray()
        synchronized(lock) { parts.forEach { arr.put(it.toJson()) } }
        runCatching { f.writeText(arr.toString()) }
    }

    /** Pick an unclaimed part, or split the biggest active one. */
    private fun claim(): Part? = synchronized(lock) {
        parts.firstOrNull { !it.active && it.pos <= it.end }?.let { it.active = true; return it }
        val victim = parts.filter { it.active }.maxByOrNull { it.remaining } ?: return null
        if (victim.remaining < minSplit * 2) return null
        val mid = victim.pos + victim.remaining / 2
        val np = Part(mid, victim.end, mid)
        victim.end = mid - 1
        np.active = true
        parts.add(np)
        np
    }

    private suspend fun worker(partFile: File) {
        while (true) {
            coroutineContext.ensureActive()
            val p = claim() ?: return
            active.incrementAndGet()
            try {
                downloadPart(p, partFile)
            } finally {
                active.decrementAndGet()
                synchronized(lock) { p.active = false }
            }
        }
    }

    private suspend fun downloadPart(p: Part, partFile: File) {
        var attempt = 0
        while (p.pos <= p.end) {
            coroutineContext.ensureActive()
            try {
                val req = DownloadManager.request(d, d.url).header("Range", "bytes=${p.pos}-${p.end}").build()
                val call = DownloadManager.http.newCall(req)
                call.execute().use { r ->
                    if (r.code != 206) {
                        if (r.code == 200 && p.pos == 0L && p.end == d.total - 1) {
                            // server ignored range but we wanted everything: fine
                        } else throw IOException("HTTP ${r.code} on range")
                    }
                    RandomAccessFile(partFile, "rw").use { raf ->
                        raf.seek(p.pos)
                        val input = r.body!!.byteStream()
                        val buf = ByteArray(64 * 1024)
                        while (p.pos <= p.end) {
                            if (!coroutineContext.isActive) { call.cancel(); return }
                            val want = minOf(buf.size.toLong(), p.end - p.pos + 1).toInt()
                            val n = input.read(buf, 0, want)
                            if (n < 0) break
                            raf.write(buf, 0, n)
                            p.pos += n
                            downloaded.addAndGet(n.toLong())
                        }
                    }
                }
                attempt = 0
            } catch (e: IOException) {
                coroutineContext.ensureActive()
                attempt++
                if (attempt > 6) throw e
                delay(500L * attempt * attempt)
            }
        }
    }

    private suspend fun singleStream(partFile: File, total: Long) {
        d.activeThreads = 1
        var attempt = 0
        while (true) {
            coroutineContext.ensureActive()
            val existing = if (partFile.exists()) partFile.length() else 0L
            val b = DownloadManager.request(d, d.url)
            if (existing > 0) b.header("Range", "bytes=$existing-")
            try {
                val call = DownloadManager.http.newCall(b.build())
                call.execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    val append = r.code == 206 && existing > 0
                    if (!append) downloaded.set(0) else downloaded.set(existing)
                    java.io.FileOutputStream(partFile, append).use { out ->
                        val input = r.body!!.byteStream()
                        val buf = ByteArray(64 * 1024)
                        var last = System.currentTimeMillis()
                        while (true) {
                            if (!coroutineContext.isActive) { call.cancel(); return }
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            downloaded.addAndGet(n.toLong())
                            val now = System.currentTimeMillis()
                            if (now - last > 300) { d.downloaded = downloaded.get(); last = now }
                        }
                    }
                }
                d.downloaded = downloaded.get()
                if (total > 0 && partFile.length() < total) throw IOException("Connection closed early")
                return
            } catch (e: IOException) {
                coroutineContext.ensureActive()
                attempt++
                if (attempt > 6) throw e
                delay(700L * attempt)
            }
        }
    }
}
