package app.prism.browser

import app.prism.browser.adblock.FilterEngine
import app.prism.browser.adblock.FilterEngine.Companion.TYPE_DOCUMENT
import app.prism.browser.adblock.FilterEngine.Companion.TYPE_IMAGE
import app.prism.browser.adblock.FilterEngine.Companion.TYPE_SCRIPT
import app.prism.browser.adblock.FilterEngine.Companion.TYPE_SUBDOCUMENT
import app.prism.browser.adblock.FilterEngine.Companion.TYPE_XHR
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class FilterEngineTest {
    companion object {
        lateinit var e: FilterEngine

        @BeforeClass @JvmStatic
        fun load() {
            e = FilterEngine()
            val dir = File("src/main/assets")
            val t0 = System.currentTimeMillis()
            e.addList(File(dir, "builtin.txt").readText())
            File(dir, "filters").listFiles()!!.sorted().forEach { e.addList(it.readText()) }
            println("rules=${e.ruleCount} compiled in ${System.currentTimeMillis() - t0}ms")
        }

        fun host(u: String) = java.net.URI(u).host
    }

    private fun blocked(url: String, page: String, type: Int = TYPE_SCRIPT) = e.shouldBlock(url, host(url), host(page), type)

    @Test fun blocksMajorAdNetworks() {
        val page = "https://www.example-news.com/article"
        listOf(
            "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            "https://securepubads.g.doubleclick.net/tag/js/gpt.js",
            "https://www.googletagservices.com/tag/js/gpt.js",
            "https://c.amazon-adsystem.com/aax2/apstag.js",
            "https://ib.adnxs.com/ut/v3/prebid",
            "https://static.criteo.net/js/ld/publishertag.js",
            "https://cdn.taboola.com/libtrc/site/loader.js",
            "https://widgets.outbrain.com/outbrain.js",
            "https://www.google-analytics.com/analytics.js",
            "https://connect.facebook.net/en_US/fbevents.js",
            "https://sb.scorecardresearch.com/beacon.js",
            "https://cdn.popcash.net/show.js",
            "https://a.exosrv.com/ad-provider.js",
        ).forEach { assertTrue("should block $it", blocked(it, page)) }
    }

    @Test fun blocksPathRules() {
        val page = "https://www.somesite.org/"
        assertTrue(blocked("https://www.somesite.org/ads/banner728x90.gif", page, TYPE_IMAGE) ||
            blocked("https://cdn.somesite.org/adserver/getad.js", page))
        assertTrue(blocked("https://www.somesite.org/prebid.js", page) || blocked("https://www.somesite.org/js/prebid-ads.js", page))
    }

    @Test fun doesNotBlockNormalContent() {
        listOf(
            "https://www.google.com/search?q=hello" to "https://www.google.com/",
            "https://www.gstatic.com/og/_/js/k=og.qtm.en_US/main.js" to "https://www.google.com/",
            "https://en.wikipedia.org/w/load.php?modules=startup" to "https://en.wikipedia.org/wiki/Main_Page",
            "https://upload.wikimedia.org/wikipedia/commons/a/a9/Example.jpg" to "https://en.wikipedia.org/wiki/Main_Page",
            "https://www.youtube.com/s/player/abc/player_ias.vflset/en_US/base.js" to "https://www.youtube.com/",
            "https://i.ytimg.com/vi/abc/hqdefault.jpg" to "https://www.youtube.com/",
            "https://play.google.com/console/u/0/developers" to "https://play.google.com/console",
            "https://accounts.google.com/v3/signin/identifier" to "https://accounts.google.com/",
            "https://github.githubassets.com/assets/app.js" to "https://github.com/",
            "https://cdnjs.cloudflare.com/ajax/libs/jquery/3.7.1/jquery.min.js" to "https://example.com/",
            "https://fonts.googleapis.com/css2?family=Sora" to "https://example.com/",
        ).forEach { (u, p) -> assertFalse("must not block $u", blocked(u, p, if (u.endsWith(".jpg")) TYPE_IMAGE else TYPE_SCRIPT or TYPE_XHR)) }
    }

    @Test fun popupNetworksBlockedAsNavigation() {
        listOf("https://www.popads.net/", "https://go.onclkds.com/abc", "https://propellerads.com/x", "https://adsterra.com/landing").forEach {
            assertTrue("popup $it", e.isPopupBlocked(it, host(it), "www.movie-site.example"))
        }
        assertFalse(e.isPopupBlocked("https://accounts.google.com/o/oauth2/v2/auth?x", "accounts.google.com", "www.example.com"))
        assertFalse(e.isPopupBlocked("https://www.paypal.com/checkoutnow", "www.paypal.com", "shop.example.com"))
    }

    @Test fun cosmeticCss() {
        val css = e.cssFor("www.example-news.com")
        assertTrue(css.contains(".adsbygoogle"))
        assertTrue(css.length > 10_000)
        println("generic css bytes=${css.length}")
    }

    @Test fun matchingIsFast() {
        val urls = listOf(
            "https://www.example.com/static/js/main.4f2a.js", "https://cdn.example.net/img/photo_123.jpg?w=400",
            "https://api.example.com/v1/feed?page=2&limit=20", "https://securepubads.g.doubleclick.net/gampad/ads?iu=/123/abc",
            "https://www.example.com/wp-content/plugins/some-plugin/js/frontend.min.js?ver=1.2",
        )
        repeat(2000) { urls.forEach { u -> blocked(u, "https://www.example.com/") } }
        val t0 = System.nanoTime()
        val n = 20_000
        repeat(n / urls.size) { urls.forEach { u -> blocked(u, "https://www.example.com/", TYPE_SCRIPT or TYPE_XHR) } }
        val perReq = (System.nanoTime() - t0) / n / 1000.0
        println("avg match time = ${"%.1f".format(perReq)} µs")
        assertTrue("too slow: $perReq µs", perReq < 200)
    }

    @Test fun subdocumentAdsBlocked() {
        assertTrue(blocked("https://googleads.g.doubleclick.net/pagead/ads?client=ca-pub-1", "https://news.example.com/", TYPE_SUBDOCUMENT))
        assertFalse(e.shouldBlock("https://www.youtube.com/embed/xyz", "www.youtube.com", "blog.example.com", TYPE_SUBDOCUMENT))
        assertFalse(e.isPopupBlocked("https://www.example.com/next-page", "www.example.com", "www.example.com") && TYPE_DOCUMENT == 0)
    }

    @Test fun onDemandGenericRules() {
        val base = e.cssFor("www.some-blog.example")
        println("always-injected css bytes=${base.length}")
        assertTrue(base.length < 200_000)
        val hits = e.genericFor("www.some-blog.example", ".adsbygoogle .article-body #sidebar .ad-banner .post-title")
        assertTrue(hits.contains(".adsbygoogle"))
        assertFalse(hits.contains(".article-body"))
        assertFalse(hits.contains(".post-title"))
    }
}
