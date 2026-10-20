package com.gr4nt.browser

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
import com.gr4nt.browser.browser.Browser
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import com.gr4nt.browser.player.Media
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.provider.OpenableColumns
import android.util.Rational
import android.view.WindowManager
import com.gr4nt.browser.ui.BrowserApp
import com.gr4nt.browser.ui.IBrowserTheme

class MainActivity : ComponentActivity() {

    private var captureUri: Uri? = null
    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val cb = Browser.fileCallback
        Browser.fileCallback = null
        var result = WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data)
        // a photo/video taken with the camera arrives in our capture file, not in the result intent
        if (res.resultCode == RESULT_OK && (result == null || result.isEmpty()) && captureUri != null) result = arrayOf(captureUri!!)
        captureUri = null
        cb?.onReceiveValue(result)
    }

    // runtime permissions (camera, microphone, location) requested on behalf of web pages
    private var runtimeCallback: ((Boolean) -> Unit)? = null
    private val runtimePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val cb = runtimeCallback; runtimeCallback = null
        cb?.invoke(res.values.any { it })
    }

    // "Open File…" from the page menu
    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = displayName(uri)
        Browser.openLocalFile(uri, name, contentResolver.getType(uri))
    }

    // download folder picker
    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        val path = com.gr4nt.browser.download.DownloadManager.pathFromTree(uri)
        if (path == null) {
            Browser.toast("Choose a folder on the phone's internal storage")
        } else {
            com.gr4nt.browser.data.Store.settings.downloadDir = path
            com.gr4nt.browser.data.Store.settings.save()
            if (!hasAllFilesAccess()) requestAllFilesAccess()
            Browser.toast("Downloads will be saved to ${path.removePrefix("/storage/emulated/0/")}")
        }
    }

    fun displayName(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "File"

    fun openFilePicker() = runCatching { openFile.launch(arrayOf("*/*")) }
    fun openFolderPicker() = runCatching { pickFolder.launch(null) }

    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Opens Android's "Allow access to manage all files" screen for iBrowser. */
    fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
        } else {
            runtimeCallback = null
            runtimePermissions.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    /** Build an upload picker that also offers the camera / sound recorder, like Safari's "Take Photo or Video". */
    private fun buildChooser(params: WebChromeClient.FileChooserParams?): Intent {
        val accept = params?.acceptTypes?.filter { it.isNotBlank() }?.joinToString(",")?.lowercase() ?: ""
        val wantsImage = accept.isEmpty() || accept.contains("image") || accept.contains("*/*")
        val wantsVideo = accept.isEmpty() || accept.contains("video") || accept.contains("*/*")
        val wantsAudio = accept.contains("audio")
        val pick = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
        if (params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        val extras = ArrayList<Intent>()
        val canCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (canCamera && (wantsImage || wantsVideo)) {
            val dir = java.io.File(cacheDir, "capture").apply { mkdirs() }
            if (wantsImage) {
                val f = java.io.File(dir, "photo_${System.currentTimeMillis()}.jpg")
                val u = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", f)
                captureUri = u
                extras.add(Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, u)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
            if (wantsVideo) extras.add(Intent(MediaStore.ACTION_VIDEO_CAPTURE))
        }
        if (wantsAudio) extras.add(Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION))
        // capture="camera" on the <input>: go straight to the camera
        if (params?.isCaptureEnabled == true && extras.isNotEmpty()) return extras.first()
        return Intent.createChooser(pick, "Choose File").apply {
            if (extras.isNotEmpty()) putExtra(Intent.EXTRA_INITIAL_INTENTS, extras.toTypedArray())
        }
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Browser.attach(this)
        Browser.restore()
        Browser.requestFileChooser = {
            val launch = {
                runCatching { fileChooser.launch(buildChooser(Browser.fileChooserParams)) }.onFailure {
                    Browser.fileCallback?.onReceiveValue(null); Browser.fileCallback = null
                }
            }
            // ask for the camera first so the picker can offer "Take Photo"
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
                Browser.fileChooserParams?.acceptTypes?.any { it.isBlank() || it.contains("image") || it.contains("video") } != false
            ) {
                runtimeCallback = { launch() }
                runtimePermissions.launch(arrayOf(Manifest.permission.CAMERA))
            } else launch()
        }
        com.gr4nt.browser.browser.SitePermissions.requestRuntime = { perms, cb ->
            val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isEmpty()) cb(true)
            else { runtimeCallback = cb; runtimePermissions.launch(missing.toTypedArray()) }
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
        com.gr4nt.browser.data.Store.settings.save()
    }
}
