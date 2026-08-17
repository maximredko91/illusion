package com.seance.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.seance.app.data.scan.LibraryScanner
import com.seance.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

class LibraryScanWorker(
    context: Context,
    params: WorkerParameters,
    private val scanner: LibraryScanner,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val totalIndexed = scanner.scanAll { progress -> setProgress(progress.toData()) }
        val requireCharging = settingsRepository.requireChargingForHeavyTasks.first()
        WorkScheduler.enqueueThumbnailGeneration(applicationContext, requireCharging)
        if (settingsRepository.posterCachingEnabled.first()) {
            WorkScheduler.enqueuePosterPreload(applicationContext, requireCharging)
        }
        return Result.success(workDataOf(KEY_TOTAL_INDEXED to totalIndexed))
    }

    companion object {
        const val KEY_TOTAL_INDEXED = "total_indexed"
    }
}
