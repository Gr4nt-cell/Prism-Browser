package app.prism.browser.download

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import app.prism.browser.data.Store
import app.prism.browser.download.Downloader.await
import app.prism.browser.video.Hls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

/** Parallel HLS (m3u8) downloader with AES-128 support, resume, and TS -> MP4 remux. */
object HlsDownloader {
    private const val TAG = "HlsDownloader"

    suspend fun run(context: Context, item: DownloadItem) = coroutineScope {
        val dir = File(Downloader.partialDir(), item.id).apply { mkdirs() }
        // 1) playlist (cached for resume)
        val plFile = File(dir, "playlist.m3u8")
        val baseFile = File(dir, "base.txt")
        var base = item.url
        var text = if (plFile.exists()) plFile.readText().also { base = baseFile.readText() } else fetchText(item, item.url)
        if (Hls.isMaster(text)) {
            val best = Hls.parseMaster(base, text).firstOrNull() ?: throw IOException("No playable stream")
            base = best.url
            text = fetchText(item, best.url)
        }
        plFile.writeText(text); baseFile.writeText(base)
        val media = Hls.parseMedia(base, text)
        if (media.segments.isEmpty()) throw IOException("Empty playlist")
        if (media.segments.any { it.key != null && it.key.method != "AES-128" }) {
            throw IOException("This stream is DRM protected (${media.segments.first { it.key != null }.key!!.method})")
        }
        item.partsTotal = media.segments.size
        item.resumable = true

        val bytes = AtomicLong(dir.listFiles()?.filter { it.name.startsWith("s") && !it.name.endsWith(".tmp") }?.sumOf { it.length() } ?: 0)
        item.downloaded = bytes.get()
        val done = java.util.concurrent.atomic.AtomicInteger(media.segments.indices.count { File(dir, "s%06d".format(it)).exists() })
        val active = java.util.concurrent.atomic.AtomicInteger(0)
        item.partsDone = done.get()

        val keys = ConcurrentHashMap<String, ByteArray>()
        val sem = Semaphore(Store.downloadThreads.coerceIn(2, 16))

        val ticker = launch {
            var last = bytes.get(); var lastT = System.nanoTime(); var ema = 0.0; var tick = 0
            while (true) {
                delay(500)
                val now = System.nanoTime(); val b = bytes.get()
                val inst = (b - last) * 1e9 / (now - lastT)
                ema = if (ema == 0.0) inst else ema * .7 + inst * .3
                item.speed = ema.toLong(); item.downloaded = b
                last = b; lastT = now
                if (++tick % 6 == 0) Downloader.save()
            }
        }

        // init segment (fMP4)
        var initFile: File? = null
        if (media.initUrl != null) {
            initFile = File(dir, "init")
            if (!initFile.exists()) {
                val data = fetchBytes(item, media.initUrl, media.initRange)
                File(dir, "init.tmp").apply { writeBytes(data) }.renameTo(initFile)
            }
        }

        item.activeThreads = 0
        media.segments.mapIndexed { idx, seg ->
            async {
                val out = File(dir, "s%06d".format(idx))
                if (out.exists()) return@async
                sem.withPermit {
                    item.activeThreads = active.incrementAndGet()
                    try {
                        var data = fetchBytes(item, seg.url, seg.byteRange)
                        seg.key?.let { k ->
                            val keyBytes = keys.getOrPut(k.uri!!) { fetchBytes(item, k.uri, null) }
                            val iv = k.iv ?: ByteArray(16).also { b ->
                                var s = seg.seq
                                for (i in 15 downTo 8) { b[i] = (s and 0xff).toByte(); s = s shr 8 }
                            }
                            val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
                            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
                            data = c.doFinal(data)
                        }
                        val tmp = File(dir, "s%06d.tmp".format(idx))
                        tmp.writeBytes(data)
                        tmp.renameTo(out)
                        bytes.addAndGet(data.size.toLong())
                        item.partsDone = done.incrementAndGet()
                    } finally {
                        item.activeThreads = active.decrementAndGet()
                    }
                }
            }
        }.awaitAll()
        ticker.cancel()
        item.downloaded = bytes.get()

        // 2) join
        val fmp4 = initFile != null
        val joined = File(dir, if (fmp4) "joined.mp4" else "joined.ts")
        joined.outputStream().buffered(1 shl 20).use { out ->
            initFile?.inputStream()?.use { it.copyTo(out) }
            for (i in media.segments.indices) File(dir, "s%06d".format(i)).inputStream().use { it.copyTo(out, 1 shl 20) }
        }

        // 3) remux TS -> MP4 so it plays everywhere
        var finalFile = joined
        var name = item.fileName
        if (!fmp4) {
            val mp4 = File(dir, "remux.mp4")
            if (remux(joined, mp4)) {
                finalFile = mp4
                if (!name.endsWith(".mp4", true)) name = name.substringBeforeLast('.') + ".mp4"
            } else {
                name = name.substringBeforeLast('.') + ".ts"
            }
        } else if (!name.endsWith(".mp4", true)) name = name.substringBeforeLast('.') + ".mp4"
        item.fileName = name
        item.total = finalFile.length()
        item.downloaded = item.total
        item.outputUri = Storage.publish(context, finalFile, name, "video/mp4")
        dir.deleteRecursively()
    }

    private suspend fun fetchText(item: DownloadItem, url: String): String = String(fetchBytes(item, url, null))

    private suspend fun fetchBytes(item: DownloadItem, url: String, range: LongRange?): ByteArray {
        var attempt = 0
        while (true) {
            try {
                val b = Request.Builder().url(url)
                item.headers.forEach { (k, v) -> b.header(k, v) }
                range?.let { b.header("Range", "bytes=${it.first}-${it.last}") }
                Downloader.client.newCall(b.build()).await().use { r ->
                    if (!r.isSuccessful) {
                        if (r.code in 400..499 && r.code != 408 && r.code != 429) throw FatalHttp(r.code)
                        throw IOException("HTTP ${r.code}")
                    }
                    return r.body!!.bytes()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FatalHttp) {
                throw IOException("HTTP ${e.code} for segment")
            } catch (e: IOException) {
                if (++attempt > 8) throw e
                delay(min(15_000L, 500L shl attempt))
            }
        }
    }

    private class FatalHttp(val code: Int) : IOException()

    /** Lossless TS -> MP4 container conversion using the platform MediaExtractor/MediaMuxer. */
    fun remux(src: File, dst: File): Boolean {
        val ex = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            ex.setDataSource(src.absolutePath)
            muxer = MediaMuxer(dst.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val map = HashMap<Int, Int>()
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    if (mime !in setOf("video/avc", "video/hevc", "audio/mp4a-latm", "audio/mpeg", "video/mp4v-es")) continue
                    ex.selectTrack(i)
                    map[i] = muxer.addTrack(f)
                }
            }
            if (map.isEmpty()) return false
            muxer.start()
            val buf = ByteBuffer.allocate(8 shl 20)
            val info = MediaCodec.BufferInfo()
            var globalBase = Long.MIN_VALUE
            val lastPts = HashMap<Int, Long>()
            while (true) {
                val track = ex.sampleTrackIndex
                if (track < 0) break
                val size = ex.readSampleData(buf, 0)
                if (size < 0) break
                if (globalBase == Long.MIN_VALUE) globalBase = ex.sampleTime
                var pts = maxOf(0L, ex.sampleTime - globalBase)
                val prev = lastPts[track]
                if (prev != null && pts <= prev) pts = prev + 1
                lastPts[track] = pts
                info.set(0, size, pts, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(map[track]!!, buf, info)
                ex.advance()
            }
            muxer.stop()
            dst.length() > 0
        } catch (e: Throwable) {
            Log.w(TAG, "remux failed: $e")
            dst.delete()
            false
        } finally {
            runCatching { muxer?.release() }
            ex.release()
        }
    }
}
