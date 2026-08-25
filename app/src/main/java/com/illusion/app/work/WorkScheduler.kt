package com.illusion.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object WorkScheduler {
    private const val PERIODIC_SCAN_WORK_NAME = "library_scan_periodic"
    private const val ONE_TIME_SCAN_WORK_NAME = "library_scan_manual"
    private const val THUMBNAIL_WORK_NAME = "thumbnail_generation"
    const val POSTER_PRELOAD_WORK_NAME = "poster_preload"
    private fun downloadWorkName(stableId: String) = "download_$stableId"

    fun schedulePeriodicScan(context: Context, intervalHours: Int, requireCharging: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresCharging(requireCharging)
            .build()

        val request = PeriodicWorkRequestBuilder<LibraryScanWorker>(
            intervalHours.toLong(), TimeUnit.HOURS
        ).setConstraints(constraints).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SCAN_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelPeriodicScan(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_SCAN_WORK_NAME)
    }

    /** Runs a scan right away (e.g. after onboarding or adding a source) and returns its work id so the UI can observe progress. [force] bypasses the unchanged-file fast path - see LibraryScanner.scanAll's own KDoc. */
    fun enqueueOneTimeScan(context: Context, force: Boolean = false): UUID {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<LibraryScanWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(LibraryScanWorker.KEY_FORCE to force))
            .build()

        val workManager = WorkManager.getInstance(context)
        // The periodic auto-rescan (rescheduled on every app launch, see Splash) uses a separate
        // unique work name, so REPLACE below only dedupes against a previous manual scan - it
        // does nothing to stop the periodic one from running at the same time. Two LibraryScanner
        // passes over the same SMB source concurrently was observed on-device to produce
        // incomplete/incorrect rescans (both racing the same Room writes and the same limited SMB
        // session budget) - cancel the periodic run explicitly so a manual "rescan now" always has
        // the source to itself. It's harmless to cancel: the next app launch reschedules it.
        workManager.cancelUniqueWork(PERIODIC_SCAN_WORK_NAME)
        workManager.enqueueUniqueWork(
            ONE_TIME_SCAN_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        return request.id
    }

    /** Id of a still-running (or not-yet-started) manual "rescan now" work, if any - lets Splash reattach the ScanProgress screen after a cold start instead of dropping straight into the tabs while a scan the user is still expecting to watch keeps running underneath. Null once it's finished/failed/never ran. */
    suspend fun runningOneTimeScanWorkId(context: Context): UUID? {
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ONE_TIME_SCAN_WORK_NAME).first()
        return infos.firstOrNull { !it.state.isFinished }?.id
    }

    /** Live version of [runningOneTimeScanWorkId] - lets a screen like Settings reflect "a scan is already running" (and disable/relabel its own "rescan now" button) instead of showing a plain idle button that silently no-ops (REPLACE just restarts the same work) while one is already in flight underneath, with no indication anywhere that it's happening. */
    fun isOneTimeScanRunning(context: Context): Flow<Boolean> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ONE_TIME_SCAN_WORK_NAME)
            .map { infos -> infos.any { !it.state.isFinished } }

    /** Generates scrubbing-preview sprites for any library items that don't have one yet. Slow - honors the charging-only setting. */
    fun enqueueThumbnailGeneration(context: Context, requireCharging: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresCharging(requireCharging)
            .build()

        val request = OneTimeWorkRequestBuilder<ThumbnailGenerationWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            THUMBNAIL_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /** Warms the poster/fanart disk cache for the whole library so grids stop showing loading placeholders on every visit. */
    fun enqueuePosterPreload(context: Context, requireCharging: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresCharging(requireCharging)
            .build()

        val request = OneTimeWorkRequestBuilder<PosterPreloadWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            POSTER_PRELOAD_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /** Downloads [stableId] for offline playback. Re-running this on an existing download restarts it from scratch (no resume). */
    fun enqueueDownload(context: Context, stableId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(DownloadWorker.KEY_STABLE_ID to stableId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            downloadWorkName(stableId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelDownload(context: Context, stableId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(downloadWorkName(stableId))
    }

    /** Copies a picked local video to [destinationPath] on SMB source [sourceId] - the developer-only "add media" scraper's one background step. Returns the work id so the UI can observe progress. */
    fun enqueueUpload(context: Context, sourceId: Long, videoUri: String, destinationPath: String, totalBytes: Long): UUID {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    UploadWorker.KEY_SOURCE_ID to sourceId,
                    UploadWorker.KEY_VIDEO_URI to videoUri,
                    UploadWorker.KEY_DESTINATION_PATH to destinationPath,
                    UploadWorker.KEY_TOTAL_BYTES to totalBytes
                )
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ADD_MEDIA_UPLOAD_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        return request.id
    }

    private const val ADD_MEDIA_UPLOAD_WORK_NAME = "add_media_upload"
    private const val UPDATE_DOWNLOAD_WORK_NAME = "update_download"

    /** Downloads a GitHub release .apk that UpdateChecker already confirmed is newer than the running build. Progress/result observed via [updateDownloadWorkInfo]. */
    fun enqueueUpdateDownload(context: Context, apkUrl: String, versionCode: Int) {
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(
                workDataOf(
                    UpdateDownloadWorker.KEY_URL to apkUrl,
                    UpdateDownloadWorker.KEY_VERSION_CODE to versionCode
                )
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UPDATE_DOWNLOAD_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun updateDownloadWorkInfo(context: Context): Flow<androidx.work.WorkInfo?> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(UPDATE_DOWNLOAD_WORK_NAME)
            .map { infos -> infos.firstOrNull() }
}
