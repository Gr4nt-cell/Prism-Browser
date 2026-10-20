package com.gr4nt.browser.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.gr4nt.browser.MainActivity
import com.gr4nt.browser.ui.formatBytes

class DownloadService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH, "Downloads", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> DownloadManager.pauseAll()
            ACTION_RESUME_ALL -> DownloadManager.resumeAll()
        }
        val n = build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID, n)
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_NOT_STICKY
    }

    private val notified = HashSet<String>()

    private fun update() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        DownloadManager.items.toList().forEach { d ->
            if (d.status == DlStatus.COMPLETED && d.createdAt > startedAt && notified.add(d.id)) {
                nm.notify(d.id.hashCode(), Notification.Builder(this, CH_DONE)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(d.fileName)
                    .setContentText("Download complete")
                    .setContentIntent(openApp())
                    .setAutoCancel(true)
                    .build())
            }
        }
        if (DownloadManager.items.none { it.isActive }) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            handler.removeCallbacks(tick)
            return
        }
        nm.notify(ID, build())
    }

    private val startedAt = System.currentTimeMillis() - 5_000

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).putExtra("openDownloads", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun build(): Notification {
        val active = DownloadManager.items.filter { it.isActive }
        val total = active.sumOf { maxOf(it.total, 0) }
        val done = active.sumOf { it.downloaded }
        val speed = active.sumOf { it.speed }
        val title = when (active.size) {
            0 -> "Downloads"
            1 -> active[0].fileName
            else -> "${active.size} downloads"
        }
        val text = buildString {
            append(formatBytes(done))
            if (total > 0) append(" of ").append(formatBytes(total))
            if (speed > 0) append(" · ").append(formatBytes(speed)).append("/s")
            active.firstOrNull { it.status == DlStatus.CONVERTING }?.let { append(" · converting") }
        }
        val pause = PendingIntent.getService(
            this, 1, Intent(this, DownloadService::class.java).setAction(ACTION_PAUSE_ALL),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(1000, if (total > 0) (done * 1000 / total).toInt() else 0, total <= 0)
            .setContentIntent(openApp())
            .addAction(Notification.Action.Builder(null, "Pause all", pause).build())
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    companion object {
        const val CH = "downloads"
        const val CH_DONE = "downloads_done"
        const val ID = 4201
        const val ACTION_PAUSE_ALL = "pause_all"
        const val ACTION_RESUME_ALL = "resume_all"
    }
}
