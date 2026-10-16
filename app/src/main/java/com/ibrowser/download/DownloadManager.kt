package com.ibrowser.download

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.compose.runtime.mutableStateListOf
import com.ibrowser.data.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CoroutineStart
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Dispatcher
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object DownloadManager {
    private lateinit var app: Context
    val items = mutableStateListOf<DownloadItem>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Download client tuned for throughput:
     * - HTTP/1.1 only, so every range/segment gets its OWN TCP connection. Over HTTP/2 OkHttp
     *   multiplexes all ranges onto one socket, which caps a "16-connection" download at the
     *   speed of a single connection.
     * - Big socket receive buffers (2 MB) and TCP_NODELAY for fast window ramp-up.
     * - A large connection pool so retries and work-stealing splits reuse warm connections.
     */
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .socketFactory(FastSocketFactory)
            .connectionPool(ConnectionPool(96, 5, TimeUnit.MINUTES))
            .dispatcher(Dispatcher().apply { maxRequests = 128; maxRequestsPerHost = 64 })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private object FastSocketFactory : SocketFactory() {
        private val base = SocketFactory.getDefault()
        private fun tune(s: Socket): Socket = s.apply {
            runCatching { receiveBufferSize = 2 * 1024 * 1024 }
            runCatching { tcpNoDelay = true }
            runCatching { keepAlive = true }
        }
        override fun createSocket(): Socket = tune(base.createSocket())
        override fun createSocket(host: String?, port: Int): Socket = tune(base.createSocket(host, port))
        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = tune(base.createSocket(host, port, localHost, localPort))
        override fun createSocket(host: InetAddress?, port: Int): Socket = tune(base.createSocket(host, port))
        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = tune(base.createSocket(address, port, localAddress, localPort))
    }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        runCatching {
            val f = stateFile()
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) items.add(DownloadItem.fromJson(arr.getJSONObject(i)))
            }
        }
        // periodic persistence + speed computation
        scope.launch {
            val last = HashMap<String, Long>()
            while (true) {
                delay(1000)
                var any = false
                for (d in items.toList()) {
                    if (d.status == DlStatus.RUNNING) {
                        any = true
                        val prev = last[d.id] ?: d.downloaded
                        val inst = (d.downloaded - prev).coerceAtLeast(0)
                        d.speed = if (d.speed == 0L) inst else (d.speed * 0.6 + inst * 0.4).toLong()
                        last[d.id] = d.downloaded
                    } else {
                        d.speed = 0; last.remove(d.id)
                    }
                }
                if (any) save()
            }
        }
    }

    private fun stateFile() = File(app.filesDir, "downloads.json")
    fun workDir(id: String) = File(app.getExternalFilesDir(null) ?: app.filesDir, "work/$id").apply { mkdirs() }

    @Synchronized
    fun save() {
        runCatching {
            val arr = JSONArray()
            items.toList().forEach { arr.put(it.toJson()) }
            stateFile().writeText(arr.toString())
        }
    }

    /** Convert a folder picked with the system picker into a real path (internal storage only). */
    fun pathFromTree(uri: android.net.Uri): String? = runCatching {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)   // e.g. "primary:Movies/Clips"
        if (!docId.startsWith("primary:")) return null
        File(Environment.getExternalStorageDirectory(), docId.removePrefix("primary:")).absolutePath
    }.getOrNull()

    private fun writable(dir: File): Boolean = runCatching {
        dir.mkdirs()
        val probe = File(dir, ".probe")
        probe.writeText("x"); probe.delete(); true
    }.getOrDefault(false)

    /** The folder chosen in Settings, else public Downloads/iBrowser, else the app's own downloads folder. */
    fun downloadDir(): File {
        Store.settings.downloadDir.takeIf { it.isNotBlank() }?.let { File(it) }?.let { if (writable(it)) return it }
        val pub = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "iBrowser")
        runCatching {
            pub.mkdirs()
            val probe = File(pub, ".probe")
            probe.writeText("x"); probe.delete()
            return pub
        }
        return File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: app.filesDir, "iBrowser").apply { mkdirs() }
    }

    fun uniqueFile(name: String): File {
        val dir = downloadDir()
        var f = File(dir, name)
        if (!f.exists() && !File(dir, "$name.part").exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var i = 1
        while (f.exists() || File(dir, "${f.name}.part").exists()) { f = File(dir, "$base ($i)$ext"); i++ }
        return f
    }

    fun guessName(url: String, contentDisposition: String?, mime: String?): String {
        var n = URLUtil.guessFileName(url, contentDisposition, mime)
        n = n.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (n.endsWith(".bin") && mime != null) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { n = n.removeSuffix(".bin") + "." + it }
        }
        return n.take(150)
    }

    fun kindFor(url: String, mime: String?): DlKind {
        val u = url.lowercase().substringBefore('?')
        val m = mime?.lowercase() ?: ""
        return when {
            u.endsWith(".m3u8") || m.contains("mpegurl") -> DlKind.HLS
            u.endsWith(".mpd") || m.contains("dash+xml") -> DlKind.DASH
            else -> DlKind.FILE
        }
    }

    fun enqueue(
        url: String,
        fileName: String,
        mime: String? = null,
        userAgent: String? = null,
        referer: String? = null,
        kind: DlKind = kindFor(url, mime),
        variant: String? = null,
        audio: String? = null,
        threads: Int = Store.settings.threads
    ): DownloadItem {
        val name = when (kind) {
            DlKind.FILE -> fileName
            else -> fileName.substringBeforeLast('.').ifBlank { "video" } + ".mp4"
        }
        val item = DownloadItem(
            UUID.randomUUID().toString().take(12), url, name, kind, mime, userAgent, referer,
            threads.coerceIn(1, 32), variant, audio
        )
        items.add(0, item)
        save()
        schedule()
        return item
    }

    fun pause(id: String) {
        val d = items.firstOrNull { it.id == id } ?: return
        jobs.remove(id)?.cancel()
        if (d.status != DlStatus.COMPLETED) d.status = DlStatus.PAUSED
        save(); schedule()
    }

    fun resume(id: String) {
        val d = items.firstOrNull { it.id == id } ?: return
        if (d.status == DlStatus.PAUSED || d.status == DlStatus.FAILED || d.status == DlStatus.CANCELLED) {
            d.error = null
            d.status = DlStatus.QUEUED
            save(); schedule()
        }
    }

    fun pauseAll() = items.filter { it.isActive }.forEach { pause(it.id) }
    fun resumeAll() = items.filter { it.status == DlStatus.PAUSED || it.status == DlStatus.FAILED }.forEach { resume(it.id) }

    fun cancel(id: String) {
        val d = items.firstOrNull { it.id == id } ?: return
        jobs.remove(id)?.cancel()
        d.status = DlStatus.CANCELLED
        cleanupPartial(d)
        save(); schedule()
    }

    fun remove(id: String, deleteFile: Boolean) {
        val d = items.firstOrNull { it.id == id } ?: return
        jobs.remove(id)?.cancel()
        cleanupPartial(d)
        if (deleteFile) d.path?.let { File(it).delete() }
        items.remove(d)
        save(); schedule()
    }

    fun clearFinished() {
        items.filter { it.status == DlStatus.COMPLETED || it.status == DlStatus.CANCELLED }.forEach { items.remove(it) }
        save()
    }

    private fun cleanupPartial(d: DownloadItem) {
        runCatching { workDir(d.id).deleteRecursively() }
        if (d.status != DlStatus.COMPLETED) d.path?.let { File("$it.part").delete() }
    }

    @Synchronized
    fun schedule() {
        val running = items.count { jobs.containsKey(it.id) }
        var slots = Store.settings.maxParallel - running
        for (d in items.reversed()) {
            if (slots <= 0) break
            if (d.status == DlStatus.QUEUED && !jobs.containsKey(d.id)) {
                start(d); slots--
            }
        }
        val anyActive = items.any { it.isActive }
        if (anyActive) startService()
    }

    private fun startService() {
        runCatching {
            val i = Intent(app, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i) else app.startService(i)
        }
    }

    private fun start(d: DownloadItem) {
        d.status = DlStatus.RUNNING
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                when (d.kind) {
                    DlKind.FILE -> FileDownloader(d).run()
                    DlKind.HLS -> HlsDownloader(d).run()
                    DlKind.DASH -> DashDownloader(d).run()
                }
                d.status = DlStatus.COMPLETED
                d.speed = 0
                d.path?.let { p -> MediaScannerConnection.scanFile(app, arrayOf(p), null, null) }
                runCatching { workDir(d.id).deleteRecursively() }
            } catch (e: Throwable) {
                if (e is CancellationException || !isActive) {
                    // paused or cancelled by the user; status already set by caller
                } else {
                    Log.w("Download", "failed ${d.url}", e)
                    d.status = DlStatus.FAILED
                    d.error = if (e is DrmException) e.message else (e.message ?: e.javaClass.simpleName)
                }
            } finally {
                if (jobs[d.id] === coroutineContext[Job]) jobs.remove(d.id)
                d.activeThreads = 0
                save()
                schedule()
            }
        }
        jobs[d.id] = job
        job.start()
    }

    // ---------------- HTTP helpers ----------------
    fun request(d: DownloadItem, url: String): Request.Builder {
        val b = Request.Builder().url(url)
        b.header("User-Agent", d.userAgent ?: DEFAULT_UA)
        d.referer?.let { b.header("Referer", it) }
        b.header("Accept", "*/*")
        b.header("Accept-Encoding", "identity")
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()?.let { b.header("Cookie", it) }
        return b
    }

    fun fetchText(d: DownloadItem, url: String): String {
        http.newCall(request(d, url).build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code} for playlist")
            return r.body!!.string()
        }
    }

    fun fetchBytes(d: DownloadItem, url: String, range: String? = null): ByteArray {
        val b = request(d, url)
        range?.let { b.header("Range", "bytes=$it") }
        http.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            return r.body!!.bytes()
        }
    }

    const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36"
}
