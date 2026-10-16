@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.ibrowser.player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.ibrowser.download.DownloadManager

/**
 * App-wide media player (Media3 / ExoPlayer). Plays local files, content:// URIs, progressive
 * http(s) media and HLS / DASH streams. State is exposed as Compose state for the glass UI.
 * A MediaSessionService ([PlaybackService]) hosts the session for background playback,
 * lock-screen and notification controls.
 */
object Media {
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private var exo: ExoPlayer? = null

    val player: ExoPlayer? get() = exo

    // ---- UI state ----
    var visible by mutableStateOf(false)      // full-screen player open
    var active by mutableStateOf(false)       // something is loaded
    var title by mutableStateOf("")
    var subtitle by mutableStateOf("")
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var duration by mutableLongStateOf(0L)
    var position by mutableLongStateOf(0L)
    var buffered by mutableLongStateOf(0L)
    var hasVideo by mutableStateOf(false)
    var videoAspect by mutableFloatStateOf(16f / 9f)
    var speed by mutableFloatStateOf(1f)
    var repeat by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var artwork by mutableStateOf<Bitmap?>(null)
    var inPip by mutableStateOf(false)
    var isLive by mutableStateOf(false)
    var currentUrl: String? = null
        private set

    private val prefs by lazy { app.getSharedPreferences("resume", Context.MODE_PRIVATE) }

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    /** Returns the shared player, creating it on first use (main thread). */
    fun obtain(): ExoPlayer {
        exo?.let { return it }
        val p = ExoPlayer.Builder(app)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        p.addListener(listener)
        exo = p
        return p
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            if (!playing) savePosition()
        }

        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            val p = exo ?: return
            if (state == Player.STATE_READY) {
                duration = p.duration.coerceAtLeast(0)
                isLive = p.isCurrentMediaItemLive
                error = null
            }
            if (state == Player.STATE_ENDED) {
                currentUrl?.let { prefs.edit().remove(it).apply() }
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                hasVideo = true
                videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            hasVideo = tracks.containsType(C.TRACK_TYPE_VIDEO)
        }

        override fun onMediaMetadataChanged(m: MediaMetadata) {
            m.title?.toString()?.takeIf { it.isNotBlank() }?.let { title = it }
            (m.artist ?: m.albumArtist)?.toString()?.takeIf { it.isNotBlank() }?.let { subtitle = it }
            m.artworkData?.let { data -> runCatching { BitmapFactory.decodeByteArray(data, 0, data.size) }.getOrNull()?.let { artwork = it } }
        }

        override fun onPlayerError(e: PlaybackException) {
            error = when (e.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network error — check your connection"
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "The server refused to play this media"
                PlaybackException.ERROR_CODE_DRM_UNSPECIFIED, PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED -> "This media is DRM protected"
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "This format isn't supported"
                else -> e.message ?: "Playback error"
            }
            buffering = false
        }

        override fun onPlaybackParametersChanged(params: PlaybackParameters) {
            speed = params.speed
        }

        override fun onRepeatModeChanged(mode: Int) {
            repeat = mode != Player.REPEAT_MODE_OFF
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val p = exo
            if (p != null && active) {
                position = p.currentPosition.coerceAtLeast(0)
                buffered = p.bufferedPosition.coerceAtLeast(0)
                if (p.duration > 0) duration = p.duration
                main.postDelayed(this, if (visible) 200 else 500)
            }
        }
    }

    /**
     * Start playing [url]. [referer]/[userAgent] are sent for web media so hot-link protected
     * files and streams play like they do on the page.
     */
    fun play(
        url: String,
        title: String,
        referer: String? = null,
        userAgent: String? = null,
        mime: String? = null,
        artist: String? = null,
        openPlayer: Boolean = true
    ) {
        val p = obtain()
        savePosition()
        currentUrl = url
        this.title = title.ifBlank { Uri.parse(url).lastPathSegment ?: "Media" }
        this.subtitle = artist ?: referer?.let { runCatching { Uri.parse(it).host?.removePrefix("www.") }.getOrNull() } ?: ""
        artwork = null
        error = null
        hasVideo = false
        duration = 0; position = 0; buffered = 0

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent ?: DownloadManager.DEFAULT_UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val headers = HashMap<String, String>()
        referer?.let { headers["Referer"] = it }
        if (url.startsWith("http")) runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()?.let { headers["Cookie"] = it }
        if (headers.isNotEmpty()) http.setDefaultRequestProperties(headers)

        val lower = url.lowercase().substringBefore('?')
        val resolvedMime = mime ?: when {
            lower.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            lower.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            else -> null
        }
        val meta = MediaMetadata.Builder().setTitle(this.title).setArtist(this.subtitle).build()
        val item = MediaItem.Builder().setUri(url).setMediaMetadata(meta).apply {
            if (resolvedMime != null) setMimeType(resolvedMime)
        }.build()
        val source = DefaultMediaSourceFactory(DefaultDataSource.Factory(app, http)).createMediaSource(item)
        p.setMediaSource(source)
        val resume = prefs.getLong(url, 0L)
        if (resume > 10_000) p.seekTo(resume)
        p.prepare()
        p.playWhenReady = true
        active = true
        if (openPlayer) visible = true
        main.removeCallbacks(ticker)
        main.post(ticker)
        runCatching { app.startService(Intent(app, PlaybackService::class.java)) }
    }

    fun playFile(path: String, title: String, mime: String? = null) =
        play(Uri.fromFile(java.io.File(path)).toString(), title, mime = mime)

    fun toggle() {
        val p = exo ?: return
        if (p.playbackState == Player.STATE_ENDED) { p.seekTo(0); p.play(); return }
        if (p.playbackState == Player.STATE_IDLE) p.prepare()
        if (p.isPlaying) p.pause() else p.play()
    }

    fun seekTo(ms: Long) {
        val p = exo ?: return
        val d = if (p.duration > 0) p.duration else Long.MAX_VALUE
        p.seekTo(ms.coerceIn(0, d))
        position = ms.coerceIn(0, d)
    }

    fun seekBy(ms: Long) = seekTo((exo?.currentPosition ?: 0) + ms)

    fun setPlaybackSpeed(s: Float) { exo?.setPlaybackSpeed(s); speed = s }

    fun cycleSpeed() {
        val steps = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)
        val i = steps.indexOfFirst { it >= speed - 0.01f }.let { if (it < 0) 2 else it }
        setPlaybackSpeed(steps[(i + 1) % steps.size])
    }

    fun toggleRepeat() {
        val p = exo ?: return
        p.repeatMode = if (p.repeatMode == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun retry() {
        val p = exo ?: return
        error = null
        p.prepare(); p.play()
    }

    /** Collapse to the mini player (keeps playing). */
    fun minimize() { visible = false }

    /** Stop playback, hide the player and remove the notification. */
    fun stop() {
        savePosition()
        exo?.let { it.stop(); it.clearMediaItems() }
        active = false
        visible = false
        isPlaying = false
        hasVideo = false
        currentUrl = null
        main.removeCallbacks(ticker)
        runCatching { app.stopService(Intent(app, PlaybackService::class.java)) }
    }

    private fun savePosition() {
        val p = exo ?: return
        val url = currentUrl ?: return
        val pos = p.currentPosition
        val dur = p.duration
        if (dur > 60_000 && pos > 10_000 && pos < dur - 15_000) prefs.edit().putLong(url, pos).apply()
        else if (dur > 0 && pos >= dur - 15_000) prefs.edit().remove(url).apply()
    }

    fun isMediaUrl(url: String): Boolean =
        Regex("\\.(mp4|m4v|webm|mkv|mov|3gp|mp3|m4a|aac|ogg|oga|opus|wav|flac|m3u8|mpd)(\\?|#|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)

    fun isPlayableFile(name: String, mime: String?): Boolean {
        val m = mime ?: ""
        return m.startsWith("video/") || m.startsWith("audio/") || isMediaUrl(name) || name.lowercase().endsWith(".ts")
    }
}
