package com.illusion.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.illusion.app.R
import com.illusion.app.data.scan.LibraryScanner
import com.illusion.app.data.scan.NewContentNotifier
import com.illusion.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

class LibraryScanWorker(
    context: Context,
    params: WorkerParameters,
    private val scanner: LibraryScanner,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, params) {

    // Promoting to a foreground service (rather than staying a plain background CoroutineWorker)
    // is what actually fixes scans "pausing"/getting interrupted mid-run - a periodic rescan with
    // no screen observing it is exactly the kind of background work Doze/App Standby defer or
    // kill outright, which read as the scan randomly stalling.
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ScanNotifications.progressForegroundInfo(applicationContext, applicationContext.getString(R.string.scan_progress_starting))

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())
        val force = inputData.getBoolean(KEY_FORCE, false)
        val result = scanner.scanAll(force = force) { progress ->
            setProgress(progress.toData())
            setForeground(ScanNotifications.progressForegroundInfo(applicationContext, progress.toNotificationText(applicationContext)))
        }
        // A source erroring out doesn't fail the whole scan (the others may have indexed fine),
        // except when EVERY source did - nothing got indexed and there's nothing useful to show,
        // so that's reported as an actual failure with the first source's classified reason
        // instead of the old always-success/generic-message behavior.
        if (result.totalIndexed == 0 && result.sourceErrors.isNotEmpty()) {
            val message = result.sourceErrors.joinToString("; ") { "${it.sourceName}: ${it.message}" }
            ScanNotifications.notifyResult(
                applicationContext,
                applicationContext.getString(R.string.scan_notification_result_failed_title),
                message
            )
            return Result.failure(workDataOf(KEY_ERROR to message))
        }
        val requireCharging = settingsRepository.requireChargingForHeavyTasks.first()
        if (settingsRepository.posterCachingEnabled.first()) {
            WorkScheduler.enqueuePosterPreload(applicationContext, requireCharging)
        }
        val partialErrorMessage = result.sourceErrors
            .takeIf { it.isNotEmpty() }
            ?.joinToString("; ") { "${it.sourceName}: ${it.message}" }
        val resultText = if (partialErrorMessage != null) {
            applicationContext.getString(R.string.scan_notification_result_partial_text, result.totalIndexed, partialErrorMessage)
        } else {
            applicationContext.getString(R.string.scan_notification_result_success_text, result.totalIndexed)
        }
        ScanNotifications.notifyResult(
            applicationContext,
            applicationContext.getString(R.string.scan_notification_result_success_title),
            resultText
        )
        // Any completed rescan (not just one started from the banner itself) already picked up
        // whatever was new - the banner shouldn't keep nagging about it.
        NewContentNotifier.clear()
        return Result.success(
            workDataOf(
                KEY_TOTAL_INDEXED to result.totalIndexed,
                KEY_PARTIAL_ERROR to partialErrorMessage,
                // Capped - WorkManager's Data has a ~10KB serialized limit, and stableId is a
                // 64-char SHA-256 hex string; a huge first-ever scan (thousands of new items)
                // would blow past that anyway and doesn't need this "что добавилось" summary as
                // much as a routine incremental rescan (a handful of new episodes/movies) does.
                KEY_NEWLY_ADDED to result.newlyAddedStableIds.take(MAX_REPORTED_NEWLY_ADDED).toTypedArray()
            )
        )
    }

    companion object {
        const val KEY_TOTAL_INDEXED = "total_indexed"
        const val KEY_ERROR = "error"
        const val KEY_PARTIAL_ERROR = "partial_error"
        const val KEY_NEWLY_ADDED = "newly_added"
        /** Input key - see LibraryScanner.scanAll's own KDoc for what this actually does and why it exists. */
        const val KEY_FORCE = "force"
        private const val MAX_REPORTED_NEWLY_ADDED = 100
    }
}
