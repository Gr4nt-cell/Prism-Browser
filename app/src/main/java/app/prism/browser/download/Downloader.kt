package app.prism.browser.download

import app.prism.browser.core.Obs
import app.prism.browser.core.ObsList
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.core.content.ContextCompat
import app.prism.browser.data.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

object Downloader {
    private const val TAG = "Downloader"
    private const val MIN_SEGMENT = 512L * 1024
    private const val STEAL_THRESHOLD = 1024L * 1024
    private const val MAX_PARALLEL_DOWNLOADS = 3

    val items = ObsList<DownloadItem>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()
    private lateinit var app: Context

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply { maxRequests = 128; maxRequestsPerHost = 32 })
            .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
            // HTTP/1.1 gives one TCP connection per segment = real parallel bandwidth
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()
    }

    fun init(context: Context) {
        app = context.applicationContext
        load()
    }

    private fun stateFile() = File(app.filesDir, "downloads.json")
    fun partialDir() = File(app.getExternalFilesDir(null) ?: app.filesDir, "partial").apply { mkdirs() }

    private fun load() {
        val f = stateFile()
        if (!f.exists()) return
        runCatching {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) items += DownloadItem.fromJson(arr.getJSONObject(i))
        }.onFailure { Log.w(TAG, "load failed", it) }
    }

    fun save() {
        if (!::app.isInitialized) return
        scope.launch {
            saveMutex.withLock {
                val arr = JSONArray()
                items.toList().forEach { arr.put(it.toJson()) }
                val tmp = File(app.filesDir, "downloads.json.tmp")
                tmp.writeText(arr.toString())
                tmp.renameTo(stateFile())
            }
        }
    }

    // ------------------------------------------------------------------ public API

    fun enqueue(
        url: String,
        fileName: String?,
        mime: String?,
        userAgent: String?,
        referer: String?,
        contentDisposition: String? = null,
        kind: DKind = DKind.HTTP,
        extraHeaders: Map<String, String> = emptyMap(),
    ): DownloadItem {
        val headers = LinkedHashMap<String, String>()
        userAgent?.let { headers["User-Agent"] = it }
        referer?.let { if (it.startsWith("http")) headers["Referer"] = it }
        CookieManager.getInstance().getCookie(url)?.let { headers["Cookie"] = it }
        headers.putAll(extraHeaders)
        val name = sanitize(fileName ?: URLUtil.guessFileName(url, contentDisposition, mime))
        val item = DownloadItem(UUID.randomUUID().toString(), url, name, mime, kind, headers, pageUrl = referer)
        items.add(0, item)
        start(item)
        return item
    }

    fun pause(item: DownloadItem) {
        item.job?.cancel()
        item.job = null
        if (item.status == DStatus.RUNNING || item.status == DStatus.QUEUED) item.status = DStatus.PAUSED
        item.speed = 0
        item.activeThreads = 0
        save()
        pump()
    }

    fun resume(item: DownloadItem) {
        if (item.status == DStatus.DONE) return
        item.error = null
        start(item)
    }

    fun retry(item: DownloadItem) = resume(item)

    fun cancel(item: DownloadItem, deleteFile: Boolean = true) {
        val wasDone = item.status == DStatus.DONE
        item.job?.cancel()
        item.status = DStatus.CANCELED
        scope.launch {
            File(partialDir(), "${item.id}.part").delete()
            File(partialDir(), item.id).deleteRecursively()
            if (deleteFile && wasDone) Storage.delete(app, item)
        }
        items.remove(item)
        save()
        pump()
    }

    fun remove(item: DownloadItem, deleteFile: Boolean) {
        if (deleteFile) scope.launch { Storage.delete(app, item) }
        item.job?.cancel()
        items.remove(item)
        save()
    }

    fun pauseAll() = items.filter { it.status == DStatus.RUNNING || it.status == DStatus.QUEUED }.forEach { pause(it) }

    val activeCount get() = items.count { it.status == DStatus.RUNNING || it.status == DStatus.QUEUED }

    // ------------------------------------------------------------------ scheduling

    private fun start(item: DownloadItem) {
        item.status = DStatus.QUEUED
        ensureService()
        pump()
    }

    @Synchronized
    private fun pump() {
        val running = items.count { it.status == DStatus.RUNNING }
        var slots = MAX_PARALLEL_DOWNLOADS - running
        for (item in items.reversed()) {
            if (slots <= 0) break
            if (item.status != DStatus.QUEUED) continue
            slots--
            item.status = DStatus.RUNNING
            item.job = scope.launch { execute(item) }
        }
    }

    private fun ensureService() {
        try {
            val i = Intent(app, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(app, i) else app.startService(i)
        } catch (e: Exception) {
            Log.w(TAG, "service start failed: $e")
        }
    }

    private suspend fun execute(item: DownloadItem) {
        try {
            when (item.kind) {
                DKind.HTTP -> runHttp(item)
                DKind.HLS -> HlsDownloader.run(app, item)
            }
            item.status = DStatus.DONE
            item.finishedAt = System.currentTimeMillis()
            item.speed = 0
            DownloadService.notifyFinished(app, item)
        } catch (e: CancellationException) {
            if (item.status == DStatus.RUNNING) item.status = DStatus.PAUSED
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "download failed ${item.url}", e)
            item.status = DStatus.FAILED
            item.error = e.message ?: e.javaClass.simpleName
            item.speed = 0
            DownloadService.notifyFinished(app, item)
        } finally {
            item.activeThreads = 0
            save()
            pump()
        }
    }

    // ------------------------------------------------------------------ HTTP engine

    private suspend fun runHttp(item: DownloadItem) {
        val part = File(partialDir(), "${item.id}.part")
        fetchToFile(item, part, Store.downloadThreads)
        item.outputUri = Storage.publish(app, part, item.fileName, item.mime)
        part.delete()
        synchronized(item.segments) { item.segments.clear() }
    }

    /**
     * Platform-independent core: downloads [item] into [part] with up to [threads] parallel
     * range connections. Resumes from the byte offsets stored in item.segments.
     */
    suspend fun fetchToFile(item: DownloadItem, part: File, threads: Int) = coroutineScope {
        if (item.segments.isEmpty() || !part.exists()) {
            synchronized(item.segments) { item.segments.clear() }
            probe(item, threads)
            part.delete()
        }
        val channel = RandomAccessFile(part, "rw").channel
        try {
            if (item.total > 0 && part.length() != item.total) RandomAccessFile(part, "rw").use { it.setLength(item.total) }

            val ticker = launch { progressLoop(item) }
            val workers = ArrayList<Job>()
            val lock = Any()

            fun spawn(seg: Segment) {
                workers += launch {
                    segmentWorker(item, seg, channel)
                    // work stealing: an idle connection splits the biggest remaining segment
                    while (isActive) {
                        val stolen = synchronized(lock) { steal(item) } ?: break
                        segmentWorker(item, stolen, channel)
                    }
                }
            }

            synchronized(item.segments) { item.segments.filter { !it.done }.toList() }.forEach { spawn(it) }
            workers.toList().forEach { it.join() }
            ticker.cancel()
            updateProgress(item)

            if (item.total > 0 && item.downloaded < item.total) throw IOException("Incomplete download")
            channel.force(true)
        } finally {
            channel.close()
        }
        if (item.total < 0) item.total = part.length()
        item.downloaded = item.total
    }

    private fun steal(item: DownloadItem): Segment? {
        synchronized(item.segments) {
            if (!item.resumable) return null
            val victim = item.segments.filter { !it.done && it.end >= 0 }.maxByOrNull { it.remaining } ?: return null
            val rem = victim.remaining
            if (rem < STEAL_THRESHOLD) return null
            val mid = victim.pos + rem / 2
            val newSeg = Segment(mid, victim.end, mid)
            victim.end = mid - 1
            item.segments += newSeg
            return newSeg
        }
    }

    private suspend fun probe(item: DownloadItem, threads: Int) {
        val req = request(item, "bytes=0-")
        val resp = client.newCall(req).await()
        resp.use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.header("Content-Disposition")?.let { cd ->
                val guessed = URLUtil.guessFileName(r.request.url.toString(), cd, r.header("Content-Type"))
                if (guessed.isNotBlank() && cd.contains("filename")) item.fileName = sanitize(guessed)
            }
            val contentRange = r.header("Content-Range")
            val total = when {
                r.code == 206 && contentRange != null -> contentRange.substringAfter('/').toLongOrNull() ?: -1L
                else -> r.body?.contentLength() ?: -1L
            }
            item.total = total
            item.resumable = r.code == 206 && total > 0
            synchronized(item.segments) {
                if (item.resumable) {
                    val count = min(threads.toLong(), maxOf(1L, total / MIN_SEGMENT)).toInt()
                    val size = total / count
                    for (i in 0 until count) {
                        val s = i * size
                        val e = if (i == count - 1) total - 1 else s + size - 1
                        item.segments += Segment(s, e, s)
                    }
                } else {
                    item.segments += Segment(0, if (total > 0) total - 1 else -1, 0)
                }
            }
        }
    }

    private suspend fun segmentWorker(item: DownloadItem, seg: Segment, channel: FileChannel) {
        var attempt = 0
        val buf = ByteArray(128 * 1024)
        while (!seg.done) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            try {
                if (!item.resumable && seg.pos > 0) {
                    // server cannot resume: restart from scratch
                    seg.pos = 0
                }
                val range = if (item.resumable) "bytes=${seg.pos}-${if (seg.end >= 0) seg.end else ""}" else null
                val resp = client.newCall(request(item, range)).await()
                resp.use { r ->
                    if (r.code == 416) { seg.pos = seg.end + 1; return@use }
                    if (!r.isSuccessful) throw HttpStatusException(r.code)
                    if (item.resumable && r.code != 206) throw IOException("Server stopped honouring ranges")
                    val input = r.body?.byteStream() ?: throw IOException("Empty body")
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) {
                            if (seg.end < 0) seg.end = seg.pos - 1 // unknown length finished
                            break
                        }
                        val take = if (seg.end >= 0) min(n.toLong(), seg.end - seg.pos + 1).toInt() else n
                        if (take > 0) {
                            val bb = ByteBuffer.wrap(buf, 0, take)
                            var p = seg.pos
                            while (bb.hasRemaining()) p += channel.write(bb, p)
                            seg.pos += take
                            attempt = 0
                        }
                        if (seg.done) break
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpStatusException) {
                if (e.code in 400..499 && e.code != 408 && e.code != 429) throw IOException("HTTP ${e.code}")
                attempt++
                if (attempt > 8) throw IOException("HTTP ${e.code}")
                delay(min(15_000L, 500L shl attempt))
            } catch (e: IOException) {
                attempt++
                if (attempt > 8) throw e
                delay(min(15_000L, 500L shl attempt))
            }
        }
    }

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    private fun request(item: DownloadItem, range: String?): Request {
        val b = Request.Builder().url(item.url)
        item.headers.forEach { (k, v) -> b.header(k, v) }
        if (range != null) b.header("Range", range)
        b.header("Accept-Encoding", "identity")
        return b.build()
    }

    private fun updateProgress(item: DownloadItem) {
        var active = 0
        val sum = synchronized(item.segments) {
            active = item.segments.count { !it.done }
            item.segments.sumOf { minOf(it.pos, if (it.end >= 0) it.end + 1 else it.pos) - it.start }
        }
        item.downloaded = sum
        item.activeThreads = active
    }

    private suspend fun progressLoop(item: DownloadItem) {
        var last = item.downloaded
        var lastT = System.nanoTime()
        var ema = 0.0
        var tick = 0
        while (true) {
            delay(400)
            updateProgress(item)
            val now = System.nanoTime()
            val inst = (item.downloaded - last) * 1e9 / (now - lastT)
            ema = if (ema == 0.0) inst else ema * 0.7 + inst * 0.3
            item.speed = ema.toLong()
            last = item.downloaded; lastT = now
            if (++tick % 5 == 0) save()
        }
    }

    fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_").trim().take(150).ifBlank { "download" }

    suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) { _, _, _ -> response.close() } else response.close()
            }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }
}
