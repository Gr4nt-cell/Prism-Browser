package app.prism.browser

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.prism.browser.engine.BrowserController
import app.prism.browser.engine.BrowserHost
import app.prism.browser.engine.Sheet
import app.prism.browser.ui.BrowserRoot
import java.io.File

class MainActivity : ComponentActivity(), BrowserHost {

    lateinit var browser: BrowserController
        private set

    override val activity: Activity get() = this

    // ---------------------------------------------------------------- file chooser
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var captureUri: Uri? = null

    private val fileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val cb = fileCallback ?: return@registerForActivityResult
        fileCallback = null
        if (res.resultCode != RESULT_OK) { cb.onReceiveValue(null); return@registerForActivityResult }
        val data = res.data
        val uris = ArrayList<Uri>()
        data?.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(uris::add) }
        if (uris.isEmpty()) data?.data?.let(uris::add)
        if (uris.isEmpty()) captureUri?.let { u ->
            val f = File(cacheDir, "captures/" + u.lastPathSegment)
            if (f.exists() && f.length() > 0) uris += u
        }
        cb.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
    }

    // ---------------------------------------------------------------- permissions
    private var permCallback: ((Boolean) -> Unit)? = null
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val cb = permCallback; permCallback = null
        cb?.invoke(result.values.all { it } || result.isEmpty())
    }

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) browser.load(text)
    }

    // ---------------------------------------------------------------- lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        browser = BrowserController(this)
        browser.restore(urlFromIntent(intent))
        if (intent?.action == ACTION_SHOW_DOWNLOADS) browser.sheet = Sheet.DOWNLOADS

        setContentView(BrowserRoot(this, browser))

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    fullscreenView != null -> fullscreenCallback?.onCustomViewHidden()?.also { hideFullscreen() } ?: hideFullscreen()
                    browser.contextTarget != null -> browser.contextTarget = null
                    browser.sheet != Sheet.NONE -> browser.sheet = Sheet.NONE
                    browser.showTabs -> browser.showTabs = false
                    browser.findVisible -> browser.closeFind()
                    browser.back() -> {}
                    else -> moveTaskToBack(true)
                }
            }
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getSharedPreferences("prism", MODE_PRIVATE).getBoolean("askedNotif", false)
        ) {
            getSharedPreferences("prism", MODE_PRIVATE).edit().putBoolean("askedNotif", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) {}
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_SHOW_DOWNLOADS) { browser.sheet = Sheet.DOWNLOADS; return }
        urlFromIntent(intent)?.let { browser.newTab(it) }
    }

    private fun urlFromIntent(i: Intent?): String? = when (i?.action) {
        Intent.ACTION_VIEW -> i.dataString
        Intent.ACTION_WEB_SEARCH -> i.getStringExtra("query")
        Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)?.let { t ->
            Regex("https?://\\S+").find(t)?.value ?: t
        }
        else -> null
    }?.let { app.prism.browser.engine.UrlUtil.toUrl(it) }

    override fun onPause() {
        super.onPause()
        browser.persist()
        CookieManager.getInstance().flush()
    }

    // ---------------------------------------------------------------- BrowserHost

    override fun requestPermissions(perms: Array<String>, onResult: (Boolean) -> Unit) {
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) { onResult(true); return }
        permCallback?.invoke(false)
        permCallback = onResult
        permLauncher.launch(missing.toTypedArray())
    }

    override fun showFileChooser(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        fileCallback?.onReceiveValue(null)
        fileCallback = callback

        val accept = params.acceptTypes.orEmpty().flatMap { it.split(',') }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        // ".aab" / ".apk" etc. -> mime; unknown extensions stay null (=> show every file)
        val mapped = accept.map { a -> if (a.startsWith(".")) MimeTypeMap.getSingleton().getMimeTypeFromExtension(a.drop(1)) else a }
        val anyFile = accept.isEmpty() || mapped.any { it == null || it == "*/*" }
        val mimes = mapped.filterNotNull().distinct()
        val wantsImage = accept.isEmpty() || accept.contains("*/*") || mimes.any { it.startsWith("image/") }
        val wantsVideo = accept.isEmpty() || accept.contains("*/*") || mimes.any { it.startsWith("video/") }
        val multiple = params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE

        val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            when {
                anyFile -> type = "*/*"
                mimes.size == 1 -> type = mimes[0]
                else -> { type = "*/*"; putExtra(Intent.EXTRA_MIME_TYPES, mimes.toTypedArray()) }
            }
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        }

        val launch = { cameraAllowed: Boolean ->
            val cameraIntents = ArrayList<Intent>()
            captureUri = null
            if (cameraAllowed) {
                val dir = File(cacheDir, "captures").apply { mkdirs() }
                if (wantsImage) {
                    val f = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
                    captureUri = uri
                    cameraIntents += Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri)
                        .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (wantsVideo) {
                    cameraIntents += Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                }
            }
            val intent = if (params.isCaptureEnabled && cameraIntents.isNotEmpty()) {
                cameraIntents.first()
            } else {
                Intent.createChooser(pick, if (multiple) "Choose files" else "Choose a file").apply {
                    if (cameraIntents.isNotEmpty()) putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toTypedArray())
                }
            }
            try {
                fileLauncher.launch(intent)
            } catch (e: Exception) {
                fileCallback?.onReceiveValue(null); fileCallback = null
            }
        }

        if (wantsImage || wantsVideo) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA)) { granted -> launch(granted) }
        } else {
            launch(false)
        }
        return true
    }

    fun voiceSearch() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search or say a website")
        runCatching { voiceLauncher.launch(i) }
    }

    // ---------------------------------------------------------------- fullscreen video
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    override fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenView != null) { callback.onCustomViewHidden(); return }
        fullscreenView = view
        fullscreenCallback = callback
        val decor = window.decorView as FrameLayout
        view.setBackgroundColor(android.graphics.Color.BLACK)
        decor.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        WindowInsetsControllerCompat(window, decor).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
    }

    override fun hideFullscreen() {
        val v = fullscreenView ?: return
        (window.decorView as FrameLayout).removeView(v)
        fullscreenView = null
        fullscreenCallback = null
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        browser.fullscreen = false
    }

    companion object {
        const val ACTION_SHOW_DOWNLOADS = "com.gr4nt.prismbrowser.SHOW_DOWNLOADS"
    }
}
