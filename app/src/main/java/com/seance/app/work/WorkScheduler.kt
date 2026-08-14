package com.seance.app.work

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

    /** Runs a scan right away (e.g. after onboarding or adding a source) and returns its work id so the UI can observe progress. */
    fun enqueueOneTimeScan(context: Context): UUID {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<LibraryScanWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_SCAN_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        return request.id
    }

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
}
