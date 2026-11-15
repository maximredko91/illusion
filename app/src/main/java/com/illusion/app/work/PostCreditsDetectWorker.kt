package com.illusion.app.work

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.illusion.app.R
import com.illusion.app.data.credits.CreditsDetector
import com.illusion.app.data.credits.CreditsScanOutcome
import com.illusion.app.data.player.PlaybackActivity
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.smb.SmbClient

/**
 * Finds a film's credits and the scenes in them by picture (see data/credits) and writes the
 * markers to that one title.
 *
 * Started from the film's Details screen, where nothing is playing. The scan needs the hardware
 * video decoder the player uses, so if playback does start meanwhile it steps aside and asks to
 * be retried; WorkManager's backoff does the waiting (waiting inside doWork would run into the
 * 10-minute execution limit), which also survives the process being killed.
 *
 * The outcome is also reported with a toast, for when the user has left the screen by the time
 * the scan finishes.
 */
class PostCreditsDetectWorker(
    context: Context,
    params: WorkerParameters,
    private val libraryRepository: LibraryRepository,
    private val sourceRepository: SmbSourceRepository,
    private val smbClient: SmbClient
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val stableId = inputData.getString(KEY_STABLE_ID) ?: return Result.failure()
        val item = libraryRepository.getById(stableId) ?: return Result.failure()
        if (PlaybackActivity.isActive) return retryLater()

        var reportedPercent = -1
        val outcome = CreditsDetector(sourceRepository, smbClient).detect(
            item = item,
            onProgress = { progress ->
                // Whole percents only: a WorkManager progress write per frame would be ~300 Room
                // writes for a number the screen shows as an integer anyway.
                val percent = (progress * 100).toInt()
                if (percent != reportedPercent) {
                    reportedPercent = percent
                    setProgressAsync(workDataOf(KEY_PROGRESS to progress))
                }
            },
            isCancelled = { isStopped }
        )
        return when (outcome) {
            CreditsScanOutcome.Interrupted -> if (isStopped) Result.failure() else retryLater()
            CreditsScanOutcome.NotFound -> {
                toast(applicationContext.getString(R.string.post_credits_result_not_found, item.title))
                Result.failure()
            }
            is CreditsScanOutcome.Found -> {
                val layout = outcome.layout
                libraryRepository.markCreditsStartForEpisode(item, layout.creditsStartMs)
                // An empty result doesn't wipe scenes the user marked by hand: "found none" is
                // exactly the case where a manual mark is the correction.
                if (layout.scenes.isNotEmpty()) libraryRepository.setPostCreditsScenes(item, layout.scenes)
                val creditsAt = formatPosition(layout.creditsStartMs)
                toast(
                    if (layout.scenes.isEmpty()) {
                        applicationContext.getString(R.string.post_credits_result_none, item.title, creditsAt)
                    } else {
                        applicationContext.getString(R.string.post_credits_result_scenes, item.title, creditsAt, layout.scenes.size)
                    }
                )
                Result.success()
            }
        }
    }

    /** Give up eventually rather than retry forever against a player that never closes. */
    private fun retryLater(): Result = if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()

    private fun toast(text: String) {
        Handler(Looper.getMainLooper()).post {
            runCatching { Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show() }
        }
    }

    private fun formatPosition(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

    companion object {
        const val KEY_STABLE_ID = "stable_id"
        const val KEY_PROGRESS = "progress"
        private const val MAX_ATTEMPTS = 60
    }
}
