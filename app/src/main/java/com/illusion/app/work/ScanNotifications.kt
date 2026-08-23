package com.illusion.app.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.illusion.app.MainActivity
import com.illusion.app.R

/**
 * Notifications for [LibraryScanWorker] - a low-priority "scanning..." notification while the
 * worker runs as a foreground service (so the OS doesn't defer/kill it under Doze/App Standby
 * the way a plain background CoroutineWorker can be), plus a final result notification so a
 * periodic background rescan (no screen observing it, unlike a manual "Rescan Now") is still
 * visible to the user when it finishes.
 */
object ScanNotifications {
    private const val CHANNEL_ID = "library_scan"
    private const val PROGRESS_NOTIFICATION_ID = 1001
    private const val RESULT_NOTIFICATION_ID = 1002

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.scan_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun progressForegroundInfo(context: Context, contentText: String): ForegroundInfo {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setContentTitle(context.getString(R.string.scan_progress_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent(context))
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    /** Posts the final "scan finished" notification. No-op if POST_NOTIFICATIONS isn't granted (API 33+) - the scan itself already succeeded either way, this is best-effort visibility only. */
    fun notifyResult(context: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(RESULT_NOTIFICATION_ID, notification) }
    }
}
