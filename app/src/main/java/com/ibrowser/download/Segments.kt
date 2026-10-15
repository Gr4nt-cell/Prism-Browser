package com.ibrowser.download

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.coroutineContext

/** One downloadable piece of a stream. */
data class SegSpec(
    val url: String,
    val range: String? = null,      // "start-end"
    val keyUrl: String? = null,     // AES-128 key
    val iv: ByteArray? = null
)

/**
 * Parallel segment fetcher used by the HLS and DASH downloaders.
 * Each finished segment is stored as its own file, so resume just skips finished ones.
 */
class SegmentFetcher(private val d: DownloadItem, private val dir: File) {
    private val keys = ConcurrentHashMap<String, ByteArray>()
    val bytes = AtomicLong(0)
    val done = AtomicInteger(0)
    private val running = AtomicInteger(0)
    var totalSegments = 0

    private fun fileFor(prefix: String, i: Int) = File(dir, "%s_%06d.seg".format(prefix, i))

    suspend fun fetchAll(prefix: String, segs: List<SegSpec>, concurrency: Int) {
        val sem = Semaphore(concurrency.coerceIn(1, 32))
        coroutineScope {
            segs.mapIndexed { i, s ->
                async {
                    val f = fileFor(prefix, i)
                    if (f.exists()) {
                        bytes.addAndGet(f.length()); done.incrementAndGet(); publish()
                        return@async
                    }
                    sem.withPermit {
                        d.activeThreads = running.incrementAndGet()
                        try {
                            fetchOne(s, f)
                        } finally {
                            d.activeThreads = running.decrementAndGet()
                        }
                    }
                    done.incrementAndGet()
                    publish()
                }
            }.awaitAll()
        }
    }

    private fun publish() {
        val n = done.get()
        val b = bytes.get()
        d.downloaded = b
        if (n > 0 && totalSegments > 0) d.total = maxOf(b, b / n * totalSegments)
        d.detail = "$n / $totalSegments segments"
    }

    private suspend fun fetchOne(s: SegSpec, out: File) {
        var attempt = 0
        while (true) {
            coroutineContext.ensureActive()
            try {
                var data = DownloadManager.fetchBytes(d, s.url, s.range)
                if (s.keyUrl != null) data = decrypt(data, s)
                val tmp = File(out.absolutePath + ".tmp")
                tmp.writeBytes(data)
                tmp.renameTo(out)
                bytes.addAndGet(data.size.toLong())
                return
            } catch (e: IOException) {
                coroutineContext.ensureActive()
                attempt++
                if (attempt > 6) throw IOException("Segment failed: ${e.message}")
                delay(400L * attempt * attempt)
            }
        }
    }

    private fun decrypt(data: ByteArray, s: SegSpec): ByteArray {
        val key = keys.getOrPut(s.keyUrl!!) { DownloadManager.fetchBytes(d, s.keyUrl) }
        if (key.size != 16) throw DrmException("Unsupported key (stream is DRM protected)")
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(s.iv ?: ByteArray(16)))
        return c.doFinal(data)
    }

    /** Concatenate finished segment files (optionally after an init segment) into [out]. */
    fun concat(prefix: String, count: Int, out: File, init: File? = null) {
        out.outputStream().buffered(1 shl 20).use { os ->
            init?.inputStream()?.use { it.copyTo(os) }
            for (i in 0 until count) {
                val f = fileFor(prefix, i)
                if (!f.exists()) throw IOException("Missing segment $i")
                f.inputStream().use { it.copyTo(os) }
            }
        }
    }

    /** Download a single file (init segment or whole-file representation). */
    suspend fun fetchFile(s: SegSpec, out: File) {
        if (out.exists()) return
        fetchOne(s, out)
    }
}
