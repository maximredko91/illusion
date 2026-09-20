package com.illusion.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.illusion.app.BuildConfig
import com.illusion.app.MainActivity
import com.illusion.app.R
import com.illusion.app.data.settings.SettingsRepository
import com.illusion.app.data.update.LocalUpdateChecker
import com.illusion.app.data.update.UpdateCheckResult
import com.illusion.app.data.update.UpdateChecker
import com.illusion.app.data.update.UpdateInfo
import com.illusion.app.domain.model.UpdateSource
import kotlinx.coroutines.flow.first

/**
 * Daily background check for a newer build, posting a system notification instead of waiting for
 * the next app launch (see UpdateViewModel.checkForUpdate for the on-launch dialog check).
 *
 * Follows the same rules as that on-launch check - the "Автопроверка обновлений" interval, "Отключена"
 * still letting mandatory releases through (at most once a day), and "Пропустить версию" - but keeps
 * its own last-check timestamp: sharing the on-launch one would let a background check silently
 * suppress the in-app dialog for a user who has notifications turned off. Notifies about a given
 * versionCode only once, so a release that stays unapplied doesn't re-notify every day.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
    private val updateChecker: UpdateChecker,
    private val localUpdateChecker: LocalUpdateChecker,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val intervalHours = settingsRepository.updateCheckIntervalHours.first()
        val autoCheckDisabled = intervalHours <= 0
        // В сеть ходим не реже раза в сутки при ЛЮБОЙ периодичности. Раньше запасной суточный
        // интервал включался только при «Выключено», а при «раз в месяц» приложение месяц не
        // спрашивало сервер вовсе - и обязательный релиз всё это время оставался невидимым.
        // Периодичность из настроек решает не «когда проверять», а «когда беспокоить обычным
        // обновлением» (см. ниже).
        val pollIntervalHours = if (autoCheckDisabled) {
            MANDATORY_FALLBACK_INTERVAL_HOURS
        } else {
            minOf(intervalHours, MANDATORY_FALLBACK_INTERVAL_HOURS)
        }
        val now = System.currentTimeMillis()
        val lastCheckedAt = settingsRepository.lastBackgroundUpdateCheckAtMs.first()
        // An hour of slack: the periodic work itself fires roughly every 24 h, not to the minute, and
        // without it a daily interval would regularly land a few minutes short and skip a whole day.
        if (now - lastCheckedAt < pollIntervalHours * HOUR_MS - HOUR_MS) return Result.success()

        val result = when (settingsRepository.updateSource.first()) {
            UpdateSource.LOCAL -> {
                val sourceId = settingsRepository.localUpdateSourceId.first() ?: return Result.success()
                localUpdateChecker.checkForUpdate(sourceId, BuildConfig.VERSION_CODE)
            }
            else -> updateChecker.checkForUpdate(BuildConfig.VERSION_CODE)
        }
        // A failed check (no internet, NAS off) doesn't consume the interval - the next daily run tries again.
        if (result is UpdateCheckResult.Failed) return Result.success()
        settingsRepository.setLastBackgroundUpdateCheckAtMs(now)
        if (result !is UpdateCheckResult.Available) return Result.success()

        val info = result.info
        if (!info.mandatory) {
            // Обычное обновление уважает и «Выключено», и выбранную периодичность, и «Пропустить
            // версию». Обязательное не уважает ничего из этого - в том и смысл пометки.
            if (autoCheckDisabled) return Result.success()
            val lastShownAt = settingsRepository.lastRegularUpdateShownAtMs.first()
            if (now - lastShownAt < intervalHours * HOUR_MS - HOUR_MS) return Result.success()
            if (settingsRepository.skippedUpdateVersionCode.first() == info.versionCode) return Result.success()
        }
        if (settingsRepository.lastNotifiedUpdateVersionCode.first() == info.versionCode) return Result.success()

        if (UpdateNotifications.notifyAvailable(applicationContext, info)) {
            settingsRepository.setLastNotifiedUpdateVersionCode(info.versionCode)
            if (!info.mandatory) settingsRepository.setLastRegularUpdateShownAtMs(now)
        }
        return Result.success()
    }

    private companion object {
        const val HOUR_MS = 60 * 60 * 1000L
        /** Same fallback as UpdateViewModel's: with auto-check off, still look for mandatory releases once a day. */
        const val MANDATORY_FALLBACK_INTERVAL_HOURS = 24
    }
}

object UpdateNotifications {
    /** Set on the notification's Intent - MainActivity then runs a forced update check so the dialog opens right away. */
    const val EXTRA_SHOW_UPDATE = "com.illusion.app.extra.SHOW_UPDATE"

    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 1101

    /** Returns false when nothing was posted (POST_NOTIFICATIONS denied on API 33+), so the caller can try again next time. */
    fun notifyAvailable(context: Context, info: UpdateInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.update_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SHOW_UPDATE, true)
        val contentIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = context.getString(
            if (info.mandatory) R.string.update_notification_title_mandatory else R.string.update_notification_title,
            info.versionName
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.update_notification_text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        return runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }.isSuccess
    }
}
