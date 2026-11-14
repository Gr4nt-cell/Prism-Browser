package app.prism.browser

import app.prism.browser.download.DKind
import app.prism.browser.download.DownloadItem
import app.prism.browser.download.Downloader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class DownloaderTest {
    companion object {
        private const val SIZE = 24 * 1024 * 1024
        private const val PER_CONN_BPS = 3L * 1024 * 1024 // each connection is throttled to 3 MB/s
        val data = Random(42).nextBytes(SIZE)
        val served = AtomicLong()
        lateinit var server: ServerSocket
        var port = 0

        @BeforeClass @JvmStatic
        fun start() {
            server = ServerSocket(0)
            port = server.localPort
            val pool = Executors.newCachedThreadPool()
            Thread {
                while (!server.isClosed) {
                    val sock = try { server.accept() } catch (e: Exception) { break }
                    pool.execute { handle(sock) }
                }
            }.apply { isDaemon = true }.start()
        }

        private fun handle(sock: Socket) = sock.use { so ->
            val input = so.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            val path = requestLine.split(' ')[1]
            var range: String? = null
            while (true) {
                val l = input.readLine() ?: break
                if (l.isEmpty()) break
                if (l.startsWith("Range:", true)) range = l.substringAfter(':').trim()
            }
            val out = so.getOutputStream()
            fun head(code: Int, headers: List<String>) {
                val status = if (code == 206) "206 Partial Content" else "200 OK"
                out.write(("HTTP/1.1 $status\r\nConnection: close\r\n" + headers.joinToString("") { "$it\r\n" } + "\r\n").toByteArray())
            }
            when (path) {
                "/file.bin" -> {
                    val m = range?.let { Regex("bytes=(\\d+)-(\\d*)").find(it) }
                    if (m != null) {
                        val st = m.groupValues[1].toInt()
                        val en = m.groupValues[2].toIntOrNull() ?: (SIZE - 1)
                        head(206, listOf("Content-Range: bytes $st-$en/$SIZE", "Content-Length: ${en - st + 1}", "Accept-Ranges: bytes"))
                        throttledWrite(out, st, en - st + 1)
                    } else {
                        head(200, listOf("Content-Length: $SIZE", "Accept-Ranges: bytes"))
                        throttledWrite(out, 0, SIZE)
                    }
                }
                "/norange.bin" -> { head(200, listOf("Content-Length: $SIZE")); throttledWrite(out, 0, SIZE, fast = true) }
                "/chunked.bin" -> { head(200, emptyList()); throttledWrite(out, 0, 3 * 1024 * 1024, fast = true) }
            }
        }

        private fun throttledWrite(out: java.io.OutputStream, start: Int, len: Int, fast: Boolean = false) {
            try {
                out.let {
                    var pos = start
                    val endEx = start + len
                    val chunk = 64 * 1024
                    while (pos < endEx) {
                        val n = minOf(chunk, endEx - pos)
                        it.write(data, pos, n)
                        served.addAndGet(n.toLong())
                        pos += n
                        if (!fast) Thread.sleep(n * 1000L / PER_CONN_BPS)
                    }
                }
            } catch (_: Exception) { /* client went away (pause) */ }
        }

        @AfterClass @JvmStatic
        fun stop() = server.close()

        fun sha(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)
    }

    private fun item(path: String) = DownloadItem(UUID.randomUUID().toString(), "http://127.0.0.1:$port$path", "f.bin", null, DKind.HTTP, emptyMap())

    private fun tmp() = File.createTempFile("prism", ".part").apply { delete(); deleteOnExit() }

    @Test fun multiThreadedIsCorrectAndFaster() = runBlocking {
        val f1 = tmp(); val i1 = item("/file.bin")
        val t1 = System.nanoTime(); Downloader.fetchToFile(i1, f1, 1); val single = (System.nanoTime() - t1) / 1e9
        val f8 = tmp(); val i8 = item("/file.bin")
        val t8 = System.nanoTime(); Downloader.fetchToFile(i8, f8, 8); val multi = (System.nanoTime() - t8) / 1e9
        println("1 connection: %.2fs (%.1f MB/s) | 8 connections: %.2fs (%.1f MB/s) | speedup x%.1f".format(
            single, SIZE / 1048576.0 / single, multi, SIZE / 1048576.0 / multi, single / multi))
        assertArrayEquals(sha(data), sha(f1.readBytes()))
        assertArrayEquals(sha(data), sha(f8.readBytes()))
        assertTrue("expected a big speed-up, got x${single / multi}", single / multi > 4)
    }

    @Test fun pauseAndResumeKeepsProgress() = runBlocking {
        val f = tmp(); val it = item("/file.bin")
        val job = launch { Downloader.fetchToFile(it, f, 8) }
        delay(1200)
        job.cancel(); job.join()   // "pause"
        val partial = synchronized(it.segments) { it.segments.sumOf { s -> minOf(s.pos, s.end + 1) - s.start } }
        println("paused at ${partial / 1024} KB of ${SIZE / 1024} KB")
        assertTrue(partial in 1 until SIZE)
        val before = served.get()
        Downloader.fetchToFile(it, f, 8) // "resume"
        val resent = served.get() - before
        println("resume transferred ${resent / 1024} KB (remaining was ${(SIZE - partial) / 1024} KB)")
        assertArrayEquals(sha(data), sha(f.readBytes()))
        assertTrue("resume re-downloaded too much: $resent", resent <= (SIZE - partial) + 8 * 256 * 1024)
    }

    @Test fun serverWithoutRangeSupport() = runBlocking {
        val f = tmp(); val it = item("/norange.bin")
        Downloader.fetchToFile(it, f, 8)
        assertFalse(it.resumable)
        assertEquals(1, it.segments.size)
        assertArrayEquals(sha(data), sha(f.readBytes()))
    }

    @Test fun unknownLength() = runBlocking {
        val f = tmp(); val it = item("/chunked.bin")
        Downloader.fetchToFile(it, f, 8)
        assertEquals(3L * 1024 * 1024, f.length())
        assertArrayEquals(sha(data.copyOf(3 * 1024 * 1024)), sha(f.readBytes()))
    }
}
