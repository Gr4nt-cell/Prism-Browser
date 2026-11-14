package app.prism.browser

import app.prism.browser.video.Hls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsTest {
    @Test fun master() {
        val m = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2"
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
            https://cdn.example.com/hd/index.m3u8?token=a,b
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
            ../mid/index.m3u8
        """.trimIndent()
        assertTrue(Hls.isMaster(m))
        val v = Hls.parseMaster("https://ex.com/video/master.m3u8", m)
        assertEquals(listOf("1080p", "720p", "360p"), v.map { it.label })
        assertEquals("https://cdn.example.com/hd/index.m3u8?token=a,b", v[0].url)
        assertEquals("https://ex.com/mid/index.m3u8", v[1].url)
        assertEquals("https://ex.com/video/low/index.m3u8", v[2].url)
    }

    @Test fun mediaWithKeysAndInit() {
        val p = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:100
            #EXT-X-MAP:URI="init.mp4"
            #EXT-X-KEY:METHOD=AES-128,URI="https://keys.ex.com/k1",IV=0x000102030405060708090a0b0c0d0e0f
            #EXTINF:6.0,
            seg100.m4s
            #EXTINF:5.5,
            seg101.m4s
            #EXT-X-KEY:METHOD=NONE
            #EXT-X-BYTERANGE:1000@0
            #EXTINF:4,
            big.ts
            #EXT-X-BYTERANGE:500
            #EXTINF:4,
            big.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val m = Hls.parseMedia("https://ex.com/v/720/index.m3u8", p)
        assertEquals(4, m.segments.size)
        assertEquals("https://ex.com/v/720/init.mp4", m.initUrl)
        assertEquals(100L, m.segments[0].seq)
        assertEquals("AES-128", m.segments[1].key!!.method)
        assertEquals(15, m.segments[0].key!!.iv!![15].toInt())
        assertEquals(null, m.segments[2].key)
        assertEquals(0L..999L, m.segments[2].byteRange)
        assertEquals(1000L..1499L, m.segments[3].byteRange)
        assertEquals(19.5, m.duration, 0.001)
    }
}
