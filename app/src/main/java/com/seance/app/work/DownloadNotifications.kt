package com.seance.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.seance.app.MainActivity
import com.seance.app.R

/**
 * Keeps [DownloadWorker] running as a genuine foreground service for the whole download, not just
 * a plain background CoroutineWorker - a multi-GB video over a home network's real-world SMB
 * throughput easily runs past the ~10 minute execution ceiling Android (MIUI especially
 * aggressively) imposes on background work with no foreground notification pinning it as
 * user-visible. Without this, a download would appear to stall (speed drops to 0, never resumes)
 * once the OS silently killed the worker - it was never actually retried, the coroutine was gone.
 */
object DownloadNotifications {
    private const val CHANNEL_ID = "downloads"
    private const val PROGRESS_NOTIFICATION_ID = 2001

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.download_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun progressForegroundInfo(context: Context, title: String, percent: Int?): ForegroundInfo {
        ensureChannel(context)
        val text = if (percent != null) {
            context.getString(R.string.download_progress_percent, percent)
        } else {
            context.getString(R.string.download_progress_starting)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { if (percent != null) setProgress(100, percent, false) else setProgress(0, 0, true) }
            .setContentIntent(contentIntent(context))
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification)
        }
    }
}
