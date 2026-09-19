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
    private const val ONE_TIME_SCAN_WORK_NAME = "library_scan_manual"
    const val POSTER_PRELOAD_WORK_NAME = "poster_preload"
    private fun downloadWorkName(stableId: String) = "download_$stableId"

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

    /** Stops a running manual "rescan now" - WorkManager cancels the underlying coroutine (CancellationException at its next suspend point), so whatever source was mid-scan simply never gets its upsertAll() call, same end state as the app being killed mid-scan. Nothing to clean up: already-scanned sources from earlier in this same run were already persisted (see LibraryScanner's own per-source upsertAll timing). */
    fun cancelOneTimeScan(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME_SCAN_WORK_NAME)
    }

    /**
     * Warms the poster/fanart disk cache for the whole library so grids stop showing loading
     * placeholders on every visit. Deliberately NOT gated on charging (unlike this app's other
     * heavy background tasks used to be) - per feedback, a charging-only constraint could leave a
     * user who rarely charges on their exact schedule never seeing a poster load in the
     * background at all, defeating the whole point of preloading them.
     */
    fun enqueuePosterPreload(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
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

    /**
     * Copies a picked local video to [destinationPath] on SMB source [sourceId] - the developer-only
     * "add media" scraper's one background step. Returns the work id so the UI can observe progress.
     *
     * The file name and total size also ride along as tags, so a screen opened after the app itself
     * was killed mid-upload can show what is being uploaded before the worker's first progress
     * report arrives - see [addMediaUploadWorkInfo] and AddMediaViewModel.onUploadWorkInfo.
     */
    fun enqueueUpload(context: Context, sourceId: Long, videoUri: String, destinationPath: String, totalBytes: Long): UUID {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .addTag(UPLOAD_NAME_TAG_PREFIX + destinationPath.substringAfterLast('\\'))
            .addTag(UPLOAD_TOTAL_TAG_PREFIX + totalBytes)
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

    /**
     * The upload's current state, whether or not this process is the one that enqueued it: keyed on
     * the unique work name rather than a work id, so it survives the app being killed and restarted
     * mid-upload (WorkManager keeps running/restarts the worker itself, and UploadWorker resumes
     * from the bytes already on the NAS after verifying them).
     */
    fun addMediaUploadWorkInfo(context: Context): Flow<androidx.work.WorkInfo?> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ADD_MEDIA_UPLOAD_WORK_NAME)
            .map { infos -> infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull() }

    fun cancelUpload(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(ADD_MEDIA_UPLOAD_WORK_NAME)
    }

    /** Uploaded file name / total size, carried as work tags - see [enqueueUpload]. */
    const val UPLOAD_NAME_TAG_PREFIX = "upload_name:"
    const val UPLOAD_TOTAL_TAG_PREFIX = "upload_total:"

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

    /** Lets the user bail out of a stuck/too-slow update download instead of staring at a progress dialog with no way out - see the Cancel button in DownloadProgressDialog. */
    fun cancelUpdateDownload(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UPDATE_DOWNLOAD_WORK_NAME)
    }

    /** Kicks off automatic intro detection for the season [stableId] belongs to. Unique: two runs
     * at once would each pull minutes of video off the same share for nothing. */
    fun enqueueIntroDetect(context: Context, stableId: String) {
        val request = OneTimeWorkRequestBuilder<IntroDetectWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(IntroDetectWorker.KEY_STABLE_ID to stableId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            INTRO_DETECT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun introDetectWorkInfo(context: Context): Flow<androidx.work.WorkInfo?> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(INTRO_DETECT_WORK_NAME)
            .map { infos -> infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull() }

    fun cancelIntroDetect(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(INTRO_DETECT_WORK_NAME)
    }

    private const val INTRO_DETECT_WORK_NAME = "intro_detect"

    private const val UPDATE_CHECK_WORK_NAME = "update_check_periodic"

    /** Schedules [UpdateCheckWorker] once a day. KEEP leaves an existing schedule alone, so calling this on every app start is safe. */
    fun schedulePeriodicUpdateCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UPDATE_CHECK_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
