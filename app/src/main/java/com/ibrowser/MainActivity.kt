package com.ibrowser

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ibrowser.browser.Browser
import com.ibrowser.player.Media
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.provider.OpenableColumns
import android.util.Rational
import android.view.WindowManager
import com.ibrowser.ui.BrowserApp
import com.ibrowser.ui.IBrowserTheme

class MainActivity : ComponentActivity() {

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val cb = Browser.fileCallback
        Browser.fileCallback = null
        cb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data))
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Browser.attach(this)
        Browser.restore()
        Browser.requestFileChooser = {
            val params = Browser.fileChooserParams
            val intent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
            if (params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            runCatching { fileChooser.launch(intent) }.onFailure {
                Browser.fileCallback?.onReceiveValue(null); Browser.fileCallback = null
            }
        }
        handleIntent(intent)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT <= 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!Browser.handleBack()) moveTaskToBack(true)
            }
        })

        setContent {
            IBrowserTheme {
                BrowserApp()
            }
        }
    }

    fun setFullscreen(on: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else c.show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        i ?: return
        if (i.getBooleanExtra("openDownloads", false)) {
            Browser.showDownloads = true; return
        }
        if (i.getBooleanExtra("openPlayer", false)) {
            if (Media.active) Media.visible = true
            return
        }
        // Media files opened from other apps (Files, Gallery, …) play in the glass player
        val type = i.type ?: i.data?.let { contentResolver.getType(it) }
        val scheme = i.data?.scheme
        if (i.action == Intent.ACTION_VIEW && (scheme == "content" || scheme == "file") &&
            (type?.startsWith("video/") == true || type?.startsWith("audio/") == true)
        ) {
            val uri = i.data!!
            val name = runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: uri.lastPathSegment ?: "Media"
            Media.play(uri.toString(), name.substringBeforeLast('.'), mime = type)
            return
        }
        val url: String? = when (i.action) {
            Intent.ACTION_VIEW -> i.dataString
            Intent.ACTION_WEB_SEARCH -> i.getStringExtra(SearchManager.QUERY)
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        if (!url.isNullOrBlank()) {
            val cur = Browser.current
            if (cur != null && cur.isStartPage && !cur.isPrivate) Browser.load(url, cur)
            else Browser.load(url, Browser.newTab(private = false))
            Browser.showTabs = false
        }
    }

    /** Video keeps playing in a floating window when you leave the app. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Media.visible && Media.isPlaying && Media.hasVideo) enterPip()
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT < 26) return
        val ratio = Media.videoAspect.coerceIn(0.42f, 2.39f)
        val w = (ratio * 1000).toInt()
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(w, 1000)).build()
            )
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Media.inPip = isInPictureInPictureMode
    }

    /** null = follow the sensor, true = landscape, false = portrait. */
    fun lockOrientation(landscape: Boolean?) {
        requestedOrientation = when (landscape) {
            true -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            false -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            null -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun setBrightness(level: Float?) {
        val lp = window.attributes
        lp.screenBrightness = level ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    override fun onStop() {
        super.onStop()
        Browser.persistTabs()
        com.ibrowser.data.Store.settings.save()
    }
}
