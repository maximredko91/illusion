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
        // Итоговый экран показывал только общее число видео - по нему нельзя было понять,
        // что именно дал перескан: сколько добавилось и сколько это заняло.
        val startedAt = System.currentTimeMillis()
        val result = scanner.scanAll(force = force) { progress ->
            setProgress(progress.toData())
            setForeground(ScanNotifications.progressForegroundInfo(applicationContext, progress.toNotificationText(applicationContext)))
        }
        (applicationContext as com.illusion.app.IllusionApplication).backupManager.applyPendingRestore()
        // A source erroring out doesn't fail the whole scan (the others may have indexed fine),
        // except when EVERY source did - nothing got indexed and there's nothing useful to show,
        // so that's reported as an actual failure with the first source's classified reason
        // instead of the old always-success/generic-message behavior.
        if (result.totalIndexed == 0 && result.sourceErrors.isNotEmpty()) {
            val message = result.sourceErrors.joinToString("; ") { "${it.sourceName}: ${it.message}" }
            // The message otherwise only exists in a notification and on the progress screen -
            // nothing to read back from a device over adb when a scan fails.
            android.util.Log.w("LibraryScanWorker", "scan failed: $message")
            ScanNotifications.notifyResult(
                applicationContext,
                applicationContext.getString(R.string.scan_notification_result_failed_title),
                message
            )
            return Result.failure(workDataOf(KEY_ERROR to message))
        }
        if (settingsRepository.posterCachingEnabled.first()) {
            WorkScheduler.enqueuePosterPreload(applicationContext)
        }
        val partialErrorMessage = result.sourceErrors
            .takeIf { it.isNotEmpty() }
            ?.joinToString("; ") { "${it.sourceName}: ${it.message}" }
        val resultText = if (partialErrorMessage != null) {
            applicationContext.getString(R.string.scan_notification_result_partial_text, result.totalIndexed, partialErrorMessage)
        } else {
            applicationContext.getString(R.string.scan_notification_result_success_text, result.totalIndexed)
        }
        val removedCount = result.removed.sumOf { it.itemCount }
        val removedLines = result.removed.map { removed ->
            if (removed.isSeries) {
                applicationContext.getString(R.string.scan_progress_removed_series, removed.title.take(80), removed.itemCount)
            } else {
                removed.title.take(80)
            }
        }
        // The result screen and the notification show this once; kept so it can be read later
        // from Settings (the output below is capped, this isn't).
        if (removedLines.isNotEmpty()) {
            com.illusion.app.data.scan.RemovedReportStore(applicationContext)
                .save(com.illusion.app.data.scan.RemovedReport(System.currentTimeMillis(), removedCount, removedLines))
        }
        ScanNotifications.notifyResult(
            applicationContext,
            applicationContext.getString(R.string.scan_notification_result_success_title),
            if (removedCount > 0) {
                resultText + " " + applicationContext.getString(R.string.scan_notification_result_removed, removedCount)
            } else {
                resultText
            }
        )
        // Any completed rescan (not just one started from the banner itself) already picked up
        // whatever was new - the banner shouldn't keep nagging about it.
        NewContentNotifier.clear()
        // Seasons that just got new episodes are searched for an intro unattended - the scan is
        // the one moment the app knows something new arrived. Films drop out in getSeasonsOf.
        android.util.Log.i("LibraryScanWorker", "scan done: ${result.totalIndexed} indexed, ${result.newlyAddedStableIds.size} new")
        if (result.newlyAddedStableIds.isNotEmpty() && settingsRepository.autoIntroAfterScan.first()) {
            val app = applicationContext as com.illusion.app.IllusionApplication
            val seasons = app.libraryRepository.getSeasonsOf(result.newlyAddedStableIds.toList())
            android.util.Log.i("LibraryScanWorker", "auto intro: ${result.newlyAddedStableIds.size} new items in ${seasons.size} seasons")
            if (seasons.isNotEmpty()) {
                com.illusion.app.data.intro.AutoIntroQueue(applicationContext).add(seasons)
                WorkScheduler.enqueueAutoIntroDetect(applicationContext)
            }
            // New films get their credits and post-credits scenes searched the same way.
            val films = app.libraryRepository.getFilmsWithoutCredits(result.newlyAddedStableIds.toList())
            android.util.Log.i("LibraryScanWorker", "auto credits: ${films.size} new films")
            if (films.isNotEmpty()) {
                com.illusion.app.data.credits.AutoPostCreditsQueue(applicationContext).add(films)
                WorkScheduler.enqueueAutoPostCreditsDetect(applicationContext)
            }
        }
        // Both lists are capped, but a scan that added a hundred items AND removed dozens of
        // long-titled ones can still exceed Data's 10 KB - and that throws, failing a scan that
        // has in fact finished. The counts alone always fit.
        val output = runCatching {
            workDataOf(
                KEY_TOTAL_INDEXED to result.totalIndexed,
                KEY_PARTIAL_ERROR to partialErrorMessage,
                // Capped - WorkManager's Data has a ~10KB serialized limit, and stableId is a
                // 64-char SHA-256 hex string; a huge first-ever scan (thousands of new items)
                // would blow past that anyway and doesn't need this "что добавилось" summary as
                // much as a routine incremental rescan (a handful of new episodes/movies) does.
                KEY_NEWLY_ADDED to result.newlyAddedStableIds.take(MAX_REPORTED_NEWLY_ADDED).toTypedArray(),
                // Полное число новых, а не длина обрезанного выше списка.
                KEY_NEWLY_ADDED_COUNT to result.newlyAddedStableIds.size,
                // What the scan removed because its files are gone from the server. Titles, not
                // ids - the items no longer exist to be looked up. Capped for the same ~10KB
                // reason as above; the count is of library items, the list is of titles.
                KEY_REMOVED to removedLines.take(MAX_REPORTED_REMOVED).toTypedArray(),
                KEY_REMOVED_TITLES_TOTAL to result.removed.size,
                KEY_REMOVED_COUNT to removedCount,
                KEY_DURATION_MS to (System.currentTimeMillis() - startedAt)
            )
        }.getOrElse {
            workDataOf(
                KEY_TOTAL_INDEXED to result.totalIndexed,
                KEY_NEWLY_ADDED_COUNT to result.newlyAddedStableIds.size,
                KEY_REMOVED_TITLES_TOTAL to result.removed.size,
                KEY_REMOVED_COUNT to removedCount,
                KEY_DURATION_MS to (System.currentTimeMillis() - startedAt)
            )
        }
        return Result.success(output)
    }

    companion object {
        const val KEY_TOTAL_INDEXED = "total_indexed"
        const val KEY_ERROR = "error"
        const val KEY_PARTIAL_ERROR = "partial_error"
        const val KEY_REMOVED = "removed"
        const val KEY_REMOVED_TITLES_TOTAL = "removed_titles_total"
        const val KEY_REMOVED_COUNT = "removed_count"
        private const val MAX_REPORTED_REMOVED = 40
        const val KEY_NEWLY_ADDED = "newly_added"
        const val KEY_NEWLY_ADDED_COUNT = "newly_added_count"
        const val KEY_DURATION_MS = "duration_ms"
        /** Input key - see LibraryScanner.scanAll's own KDoc for what this actually does and why it exists. */
        const val KEY_FORCE = "force"
        private const val MAX_REPORTED_NEWLY_ADDED = 100
    }
}
