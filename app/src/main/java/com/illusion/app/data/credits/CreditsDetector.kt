package com.illusion.app.data.credits

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import androidx.core.graphics.scale
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.player.PlaybackActivity
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.scan.SmbMediaDataSource
import com.illusion.app.data.smb.SmbClient
import com.illusion.app.domain.model.PostCreditsScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a credits scan ended with. */
sealed interface CreditsScanOutcome {
    class Found(val layout: CreditsLayout) : CreditsScanOutcome

    /** The closing stretch of the film has nothing that looks like a credits roll. */
    data object NotFound : CreditsScanOutcome

    /** Cancelled, or playback started and took the decoder - worth trying again later. */
    data object Interrupted : CreditsScanOutcome
}

/**
 * Samples frames over the end of a film straight off the SMB share and hands them to
 * [findCreditsLayout] - see CreditsFrameAnalysis.kt for the idea and its blind spots.
 *
 * Frames come from [MediaMetadataRetriever], which decodes on the same hardware video decoder the
 * player uses. Running both at once is what once left the player with sound and a black screen
 * (see [PlaybackActivity]'s own KDoc and ThumbnailGenerator), so a scan never starts while
 * something is playing and gives up the moment playback begins.
 */
class CreditsDetector(
    private val sourceRepository: SmbSourceRepository,
    private val smbClient: SmbClient
) {

    suspend fun detect(
        item: MediaItemEntity,
        onProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): CreditsScanOutcome =
        withRetriever(item) { retriever -> scan(retriever, onProgress, isCancelled) } ?: CreditsScanOutcome.NotFound

    /**
     * Where ONE EPISODE's credits start, by picture - the fallback for episodes the audio search
     * can't place because the closing music differs from episode to episode (see
     * IntroDetectWorker.detectCredits). Null when the episode has no black roll running to its
     * end, or when the scan was cancelled or playback took the decoder: for a fallback, "no
     * marker" is the right answer to all three.
     */
    suspend fun detectEpisodeCreditsStart(item: MediaItemEntity, isCancelled: () -> Boolean = { false }): Long? =
        withRetriever(item) { retriever -> scanEpisode(retriever, isCancelled) }

    private suspend fun <T> withRetriever(item: MediaItemEntity, block: (MediaMetadataRetriever) -> T): T? =
        withContext(Dispatchers.IO) {
            if (item.sizeBytes <= 0) return@withContext null
            val info = sourceRepository.connectionInfoById(item.sourceId) ?: return@withContext null
            try {
                smbClient.connect(info).use { connection ->
                    val dataSource = SmbMediaDataSource(connection.openRandomAccessFile(item.filePath), item.sizeBytes)
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(dataSource)
                        block(retriever)
                    } finally {
                        runCatching { retriever.release() }
                        runCatching { dataSource.close() }
                    }
                }
            } catch (e: Exception) {
                // An SMB read failing mid-scan, a container the retriever can't open: no result,
                // never a crashed worker.
                Log.w(TAG, "scan failed for ${item.filePath}", e)
                null
            }
        }

    /** One frame every [STEP_MS] over the last [windowMs] of the file; null if interrupted. */
    private fun sampleTail(
        retriever: MediaMetadataRetriever,
        durationMs: Long,
        windowMs: Long,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean
    ): List<FrameSample>? {
        val samples = ArrayList<FrameSample>()
        var timeMs = durationMs - windowMs
        while (timeMs < durationMs - 1000) {
            if (isCancelled() || PlaybackActivity.isActive) return null
            // Sync frames only on the coarse pass: decoding from the previous keyframe for every
            // sample would multiply the work, and a frame a second or two off is fine at this step.
            classify(retriever, timeMs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { samples += FrameSample(timeMs, it) }
            timeMs += STEP_MS
            onProgress(1f - (durationMs - timeMs).coerceAtLeast(0).toFloat() / windowMs)
        }
        // 'C' credit-like, '.' picture, one character per sample - the whole story of a scan at a
        // glance when a title comes out wrong.
        Log.i(TAG, "window ${windowMs / 1000} s, ${samples.size} frames: " + samples.joinToString("") { if (it.creditLike) "C" else "." })
        return samples
    }

    private fun scanEpisode(retriever: MediaMetadataRetriever, isCancelled: () -> Boolean): Long? {
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
        val windowMs = minOf(EPISODE_WINDOW_MS, durationMs * 3 / 10)
        val samples = sampleTail(retriever, durationMs, windowMs, {}, isCancelled) ?: return null
        val coarse = findEpisodeCreditsStart(samples, durationMs) ?: return null
        val previousMs = samples.lastOrNull { it.timeMs < coarse }?.timeMs ?: coarse
        return refine(retriever, previousMs, coarse, upperIsCredit = true, isCancelled)?.second
    }

    private fun scan(retriever: MediaMetadataRetriever, onProgress: (Float) -> Unit, isCancelled: () -> Boolean): CreditsScanOutcome {
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            ?: return CreditsScanOutcome.NotFound
        if (durationMs < MIN_DURATION_MS) return CreditsScanOutcome.NotFound
        val windowMs = minOf(MAX_WINDOW_MS, durationMs * 3 / 10)
        val samples = sampleTail(retriever, durationMs, windowMs, onProgress, isCancelled)
            ?: return CreditsScanOutcome.Interrupted

        val coarse = findCreditsLayout(samples, durationMs) ?: return CreditsScanOutcome.NotFound

        // Each boundary is only known to the sampling step; narrow it with a few exact frames.
        fun previousSampleMs(boundaryMs: Long) = samples.lastOrNull { it.timeMs < boundaryMs }?.timeMs ?: boundaryMs
        val creditsStartMs = refine(retriever, previousSampleMs(coarse.creditsStartMs), coarse.creditsStartMs, upperIsCredit = true, isCancelled)
            ?.second ?: return CreditsScanOutcome.Interrupted
        // A scene's sound often starts before its picture: the music fades and a voice-over begins
        // over the still-black screen. On the first film this was tried on, that was 15 seconds of
        // narration before the first frame - invisible to a search that only looks at pictures. So
        // every scene start is moved back by a fixed lead-in (20 s at first; cut to 5 after Eternals,
        // where the picture start was exact and the user got 20 s of roll): a little credits music is a
        // cheap price for never cutting into the scene itself. Never back past the credits start or
        // the previous scene's end.
        var earliestMs = creditsStartMs
        val scenes = coarse.scenes.map { scene ->
            // The LOWER bound of the bisect (the last frame still known to be credits).
            val pictureStartMs = refine(retriever, previousSampleMs(scene.startMs), scene.startMs, upperIsCredit = false, isCancelled)
                ?.first ?: return CreditsScanOutcome.Interrupted
            val startMs = (pictureStartMs - SCENE_LEAD_IN_MS).coerceAtLeast(earliestMs)
            earliestMs = scene.endMs ?: pictureStartMs
            PostCreditsScene(startMs, scene.endMs)
        }
        Log.i(TAG, "credits from $creditsStartMs ms, scenes: $scenes")
        return CreditsScanOutcome.Found(CreditsLayout(creditsStartMs, scenes))
    }

    /**
     * Bisects between [lowMs] and [highMs], where the frame at [highMs] is known to be credit-like
     * or not ([upperIsCredit]) and the one at [lowMs] the opposite. Returns the narrowed
     * (low, high) pair, or null if the scan was interrupted.
     */
    private fun refine(
        retriever: MediaMetadataRetriever,
        lowMs: Long,
        highMs: Long,
        upperIsCredit: Boolean,
        isCancelled: () -> Boolean
    ): Pair<Long, Long>? {
        var low = lowMs
        var high = highMs
        repeat(REFINE_STEPS) {
            if (isCancelled() || PlaybackActivity.isActive) return null
            if (high - low < 1000) return low to high
            val middle = (low + high) / 2
            val creditLike = classify(retriever, middle, MediaMetadataRetriever.OPTION_CLOSEST) ?: return low to high
            if (creditLike == upperIsCredit) high = middle else low = middle
        }
        return low to high
    }

    /** Null when the retriever has no frame for that time (past the last frame, a decode error). */
    private fun classify(retriever: MediaMetadataRetriever, timeMs: Long, option: Int): Boolean? {
        val frame = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(timeMs * 1000, option, FRAME_WIDTH, FRAME_HEIGHT)
            } else {
                retriever.getFrameAtTime(timeMs * 1000, option)?.let { original ->
                    original.scale(FRAME_WIDTH, FRAME_HEIGHT).also { if (it !== original) original.recycle() }
                }
            }
        }.getOrNull() ?: return null
        // getPixels can't read a hardware bitmap; the retriever normally hands out software ones,
        // but that's an implementation detail worth not depending on.
        val readable = if (frame.config == Bitmap.Config.HARDWARE) {
            frame.copy(Bitmap.Config.ARGB_8888, false).also { frame.recycle() } ?: return null
        } else {
            frame
        }
        val pixels = IntArray(readable.width * readable.height)
        readable.getPixels(pixels, 0, readable.width, 0, 0, readable.width, readable.height)
        val creditLike = isCreditLikeFrame(pixels, readable.width, readable.height)
        readable.recycle()
        return creditLike
    }

    private companion object {
        const val TAG = "CreditsDetector"

        /** One frame every five seconds: fine enough that a 15-second scene is three samples. */
        const val STEP_MS = 5_000L

        /** Film credits run up to ~12 minutes; with scenes in between, 25 covers them with room. */
        const val MAX_WINDOW_MS = 25 * 60 * 1000L

        /** An episode's credits are a minute or two at the very end; five covers them with room. */
        const val EPISODE_WINDOW_MS = 5 * 60 * 1000L

        /** Anything shorter than this has no credits roll worth looking for. */
        const val MIN_DURATION_MS = 10 * 60 * 1000L

        /** Three halvings of a 5-second gap: boundaries end up within about 0.6 s. */
        const val REFINE_STEPS = 3

        /** How far before a scene's first picture frame its marker is placed - see [scan]. */
        const val SCENE_LEAD_IN_MS = 5_000L

        const val FRAME_WIDTH = 160
        const val FRAME_HEIGHT = 90
    }
}
