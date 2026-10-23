package com.gr4nt.browser.browser

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gr4nt.browser.data.Store
import com.gr4nt.browser.data.hostOf

/** An installed app that can open a web link (YouTube, Instagram, Maps, …). */
data class LinkApp(val pkg: String, val activity: String, val label: String, val icon: Bitmap?)

/** "Open in YouTube?" banner shown while the page keeps loading in iBrowser. */
data class AppPrompt(val url: String, val app: LinkApp, val at: Long = System.currentTimeMillis())

/**
 * Opening web links in other apps:
 *  - Settings › Open Links in Apps: Ask (banner), Always, or Never.
 *  - "Open in App…" in the ^ menu and the link menu shows Android's chooser (iBrowser excluded).
 */
object AppLinks {
    const val ASK = 0
    const val ALWAYS = 1
    const val NEVER = 2

    var prompt by mutableStateOf<AppPrompt?>(null)

    private var browserPkgs: Set<String>? = null

    /** Packages that open any web page (other browsers) — those aren't "the app for this link". */
    private fun browsers(ctx: Context): Set<String> {
        browserPkgs?.let { return it }
        val generic = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid/")).addCategory(Intent.CATEGORY_BROWSABLE)
        val set = query(ctx, generic).map { it.activityInfo.packageName }.toSet() + ctx.packageName
        browserPkgs = set
        return set
    }

    private fun query(ctx: Context, intent: Intent): List<ResolveInfo> {
        val pm = ctx.packageManager
        return if (Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        else @Suppress("DEPRECATION") pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    }

    /** Non-browser apps that handle [url]. */
    fun appsFor(ctx: Context, url: String): List<LinkApp> {
        val scheme = Uri.parse(url).scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return emptyList()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        val skip = browsers(ctx)
        val pm = ctx.packageManager
        return runCatching {
            query(ctx, intent)
                .filter { it.activityInfo.packageName !in skip }
                .distinctBy { it.activityInfo.packageName }
                .map { ri ->
                    LinkApp(
                        ri.activityInfo.packageName, ri.activityInfo.name,
                        ri.loadLabel(pm).toString(), runCatching { ri.loadIcon(pm).toBitmap() }.getOrNull()
                    )
                }
        }.getOrDefault(emptyList())
    }

    /**
     * Called for link taps in the page. Returns true when the link was handed to another app
     * (so the WebView must not load it).
     */
    fun onLinkTap(ctx: Context, from: String, url: String, private: Boolean): Boolean {
        val mode = Store.settings.openInApps
        if (mode == NEVER) return false
        // links inside the same site stay in the browser
        if (hostOf(url).removePrefix("www.") == hostOf(from).removePrefix("www.")) return false
        val app = appsFor(ctx, url).firstOrNull() ?: return false
        return if (mode == ALWAYS && !private) {
            open(ctx, url, app)
        } else {
            prompt = AppPrompt(url, app)
            false
        }
    }

    fun open(ctx: Context, url: String, app: LinkApp? = null): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (app != null) intent.component = ComponentName(app.pkg, app.activity)
        else if (Build.VERSION.SDK_INT >= 30) intent.addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
        return try {
            ctx.startActivity(intent); prompt = null; true
        } catch (e: ActivityNotFoundException) {
            Browser.toast("No app can open this link"); false
        } catch (e: SecurityException) {
            Browser.toast("No app can open this link"); false
        }
    }

    /** Android's "Open with" chooser for [url], without iBrowser itself in the list. */
    fun openWith(ctx: Context, url: String) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        val own = query(ctx, view).filter { it.activityInfo.packageName == ctx.packageName }
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
        val chooser = Intent.createChooser(view, "Open With").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (own.isNotEmpty()) chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, own.toTypedArray())
        runCatching { ctx.startActivity(chooser) }.onFailure { Browser.toast("No app can open this link") }
    }

    private fun android.graphics.drawable.Drawable.toBitmap(): Bitmap {
        val w = intrinsicWidth.coerceIn(1, 192); val h = intrinsicHeight.coerceIn(1, 192)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); setBounds(0, 0, w, h); draw(c)
        return b
    }
}
