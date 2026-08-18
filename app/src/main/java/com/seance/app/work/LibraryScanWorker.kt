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
        val result = scanner.scanAll { progress -> setProgress(progress.toData()) }
        // A source erroring out doesn't fail the whole scan (the others may have indexed fine),
        // except when EVERY source did - nothing got indexed and there's nothing useful to show,
        // so that's reported as an actual failure with the first source's classified reason
        // instead of the old always-success/generic-message behavior.
        if (result.totalIndexed == 0 && result.sourceErrors.isNotEmpty()) {
            val message = result.sourceErrors.joinToString("; ") { "${it.sourceName}: ${it.message}" }
            return Result.failure(workDataOf(KEY_ERROR to message))
        }
        val requireCharging = settingsRepository.requireChargingForHeavyTasks.first()
        WorkScheduler.enqueueThumbnailGeneration(applicationContext, requireCharging)
        if (settingsRepository.posterCachingEnabled.first()) {
            WorkScheduler.enqueuePosterPreload(applicationContext, requireCharging)
        }
        val partialErrorMessage = result.sourceErrors
            .takeIf { it.isNotEmpty() }
            ?.joinToString("; ") { "${it.sourceName}: ${it.message}" }
        return Result.success(
            workDataOf(
                KEY_TOTAL_INDEXED to result.totalIndexed,
                KEY_PARTIAL_ERROR to partialErrorMessage
            )
        )
    }

    companion object {
        const val KEY_TOTAL_INDEXED = "total_indexed"
        const val KEY_ERROR = "error"
        const val KEY_PARTIAL_ERROR = "partial_error"
    }
}
