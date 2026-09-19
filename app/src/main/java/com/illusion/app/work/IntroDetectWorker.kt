package com.illusion.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.illusion.app.data.intro.IntroDetector
import com.illusion.app.data.intro.pickReferenceEpisode
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.smb.SmbClient

/**
 * Detects a season's title sequence by audio and writes it to every episode of that season - the
 * automatic counterpart to the player's manual marking (see [IntroDetector] for how, and
 * `LibraryRepository.markIntroRange` for the scoping, which is the same as the manual path's).
 *
 * A worker rather than a view-model coroutine because it runs for minutes (two episodes' opening
 * five minutes are pulled off the SMB share) and has to survive the player screen closing or the
 * user switching apps mid-detection.
 */
class IntroDetectWorker(
    context: Context,
    params: WorkerParameters,
    private val libraryRepository: LibraryRepository,
    private val sourceRepository: SmbSourceRepository,
    private val smbClient: SmbClient
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val stableId = inputData.getString(KEY_STABLE_ID) ?: return Result.failure()
        val item = libraryRepository.getById(stableId)
            ?: return Result.failure(workDataOf(KEY_ERROR to ERROR_NO_ITEM))
        val seriesId = item.seriesStableId
        val season = item.seasonNumber
        if (seriesId == null || season == null) return Result.failure(workDataOf(KEY_ERROR to ERROR_NOT_SERIES))

        val reference = pickReferenceEpisode(item, libraryRepository.getSeasonEpisodes(seriesId, season))
            ?: return Result.failure(workDataOf(KEY_ERROR to ERROR_NO_REFERENCE))

        setProgress(workDataOf(KEY_PROGRESS to 0f))
        val match = IntroDetector(sourceRepository, smbClient).detect(
            item = item,
            reference = reference,
            isCancelled = { isStopped },
            onProgress = { progress -> setProgressBlocking(progress) }
        ) ?: return if (isStopped) {
            Result.failure()
        } else {
            Result.failure(workDataOf(KEY_ERROR to ERROR_NOT_FOUND))
        }

        libraryRepository.markIntroRange(item, match.startMs, match.endMs)
        return Result.success(workDataOf(KEY_START_MS to match.startMs, KEY_END_MS to match.endMs))
    }

    // setProgress is suspending and the detector's callback isn't - progress here is a nicety on a
    // multi-minute job, so a dropped update is harmless and blocking the (background) worker thread
    // for it is not worth threading a coroutine scope through the detector.
    private fun setProgressBlocking(progress: Float) {
        runCatching { setProgressAsync(workDataOf(KEY_PROGRESS to progress)).get() }
    }

    companion object {
        const val KEY_STABLE_ID = "stable_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_START_MS = "start_ms"
        const val KEY_END_MS = "end_ms"
        const val KEY_ERROR = "error"

        const val ERROR_NO_ITEM = "no_item"
        const val ERROR_NOT_SERIES = "not_series"
        const val ERROR_NO_REFERENCE = "no_reference"
        const val ERROR_NOT_FOUND = "not_found"
    }
}
