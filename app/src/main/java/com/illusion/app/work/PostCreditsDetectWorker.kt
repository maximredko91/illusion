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
 *
 * Second mode ([KEY_AUTO_QUEUE]): after a library scan, the films that scan added are searched
 * unattended, one after another, from AutoPostCreditsQueue - see [drainQueue].
 */
class PostCreditsDetectWorker(
    context: Context,
    params: WorkerParameters,
    private val libraryRepository: LibraryRepository,
    private val sourceRepository: SmbSourceRepository,
    private val smbClient: SmbClient
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (inputData.getBoolean(KEY_AUTO_QUEUE, false)) return drainQueue()
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

    /**
     * The unattended run after a scan. Silent - nobody asked for it, so no toasts; the result
     * shows up in the film's card. A film leaves the queue only once it has been dealt with, so
     * being stopped (WorkManager's 10-minute limit included) or losing the decoder to playback
     * just means "retry and carry on from the same film". Never fails: a failure would cancel
     * the work appended behind this one.
     */
    private suspend fun drainQueue(): Result {
        val queue = com.illusion.app.data.credits.AutoPostCreditsQueue(applicationContext)
        val detector = CreditsDetector(sourceRepository, smbClient)
        while (true) {
            if (isStopped || PlaybackActivity.isActive) return Result.retry()
            val stableId = queue.peek() ?: return Result.success()
            // Away from home the server can't be reached, and "couldn't open the file" would be
            // taken for "no credits" - the film would leave the queue unsearched.
            if (!com.illusion.app.ui.common.isOnLocalNetwork(applicationContext)) return Result.retry()
            val item = libraryRepository.getById(stableId)
            if (item != null && item.seriesStableId == null && item.outroStartMs == null) {
                val outcome = try {
                    detector.detect(item = item, isCancelled = { isStopped })
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "auto credits ${item.title}: failed", e)
                    CreditsScanOutcome.NotFound
                }
                when (outcome) {
                    CreditsScanOutcome.Interrupted -> return Result.retry()
                    CreditsScanOutcome.NotFound -> android.util.Log.i(TAG, "auto credits ${item.title}: not found")
                    is CreditsScanOutcome.Found -> {
                        libraryRepository.markCreditsStartForEpisode(item, outcome.layout.creditsStartMs)
                        if (outcome.layout.scenes.isNotEmpty()) libraryRepository.setPostCreditsScenes(item, outcome.layout.scenes)
                        android.util.Log.i(TAG, "auto credits ${item.title}: credits at ${formatPosition(outcome.layout.creditsStartMs)}, scenes ${outcome.layout.scenes.size}")
                    }
                }
            }
            queue.remove(stableId)
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
        const val KEY_AUTO_QUEUE = "auto_queue"
        private const val TAG = "PostCreditsDetectWorker"
        private const val MAX_ATTEMPTS = 60
    }
}
