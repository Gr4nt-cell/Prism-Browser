package app.prism.browser.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import app.prism.browser.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE_ALL) {
            Downloader.pauseAll()
        }
        val n = build()
        try {
            ServiceCompat.startForeground(
                this, ONGOING_ID, n,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
        } catch (_: Exception) {
            stopSelf(); return START_NOT_STICKY
        }
        scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
        scope.launch {
            while (true) {
                delay(1000)
                if (Downloader.activeCount == 0) {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                runCatching { NotificationManagerCompat.from(this@DownloadService).notify(ONGOING_ID, build()) }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(): Notification {
        val active = Downloader.items.filter { it.status == DStatus.RUNNING || it.status == DStatus.QUEUED }
        val total = active.sumOf { maxOf(0L, it.total) }
        val done = active.sumOf { it.downloaded }
        val speed = active.sumOf { it.speed }
        val pct = if (total > 0) (done * 100 / total).toInt() else -1
        val title = when (active.size) {
            0 -> "Downloads paused"
            1 -> active[0].fileName
            else -> "Downloading ${active.size} files"
        }
        val text = buildString {
            append(Formatter.formatShortFileSize(this@DownloadService, done))
            if (total > 0) append(" / ").append(Formatter.formatShortFileSize(this@DownloadService, total))
            if (speed > 0) append(" · ").append(Formatter.formatShortFileSize(this@DownloadService, speed)).append("/s")
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_DOWNLOADS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pause = PendingIntent.getService(
            this, 1, Intent(this, DownloadService::class.java).setAction(ACTION_PAUSE_ALL), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .setProgress(100, pct.coerceAtLeast(0), pct < 0)
            .addAction(0, "Pause all", pause)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_PROGRESS = "downloads"
        const val CHANNEL_DONE = "downloads_done"
        const val ONGOING_ID = 4201
        const val ACTION_PAUSE_ALL = "com.gr4nt.prismbrowser.PAUSE_ALL"

        fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT))
        }

        fun notifyFinished(context: Context, item: DownloadItem) {
            val ok = item.status == DStatus.DONE
            val intent = if (ok) {
                Storage.contentUri(context, item)?.let { uri ->
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, Storage.mimeFor(item.fileName, item.mime))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else null
            val pi = PendingIntent.getActivity(
                context, item.id.hashCode(),
                intent ?: Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_DOWNLOADS),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                .setContentTitle(item.fileName)
                .setContentText(if (ok) "Download complete · ${Formatter.formatShortFileSize(context, item.total)}" else "Download failed: ${item.error}")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(item.id.hashCode(), n) }
        }
    }
}
