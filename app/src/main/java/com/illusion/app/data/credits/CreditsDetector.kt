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
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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

    /** What one frame looks like: mostly black, and how many words the text recogniser read in it. */
    private class Look(val black: Boolean, val words: Int)

    /**
     * One frame every [STEP_MS] over the last [windowMs] of the file; null if interrupted. With a
     * [recognizer] the frames are taken large enough for the roll's small print to be read.
     */
    private fun sampleTail(
        retriever: MediaMetadataRetriever,
        durationMs: Long,
        windowMs: Long,
        recognizer: TextRecognizer?,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean
    ): List<Pair<Long, Look>>? {
        val looks = ArrayList<Pair<Long, Look>>()
        var timeMs = durationMs - windowMs
        while (timeMs < durationMs - 1000) {
            if (isCancelled() || PlaybackActivity.isActive) return null
            // Sync frames only on the coarse pass: decoding from the previous keyframe for every
            // sample would multiply the work, and a frame a second or two off is fine at this step.
            look(retriever, timeMs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, recognizer)?.let { looks += timeMs to it }
            timeMs += STEP_MS
            onProgress(1f - (durationMs - timeMs).coerceAtLeast(0).toFloat() / windowMs)
        }
        // One character per sample - the whole story of a scan at a glance when a title comes out
        // wrong. 'C' mostly black, '.' picture; then the word counts as base-36 digits (z = 35+).
        Log.i(TAG, "window ${windowMs / 1000} s, ${looks.size} frames: " + looks.joinToString("") { if (it.second.black) "C" else "." })
        if (recognizer != null) {
            Log.i(TAG, "words: " + looks.joinToString("") { Character.forDigit(it.second.words.coerceAtMost(35), 36).toString() })
        }
        return looks
    }

    private fun scanEpisode(retriever: MediaMetadataRetriever, isCancelled: () -> Boolean): Long? {
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
        val windowMs = minOf(EPISODE_WINDOW_MS, durationMs * 3 / 10)
        val samples = sampleTail(retriever, durationMs, windowMs, null, {}, isCancelled)
            ?.map { (timeMs, look) -> FrameSample(timeMs, look.black) } ?: return null
        val coarse = findEpisodeCreditsStart(samples, durationMs) ?: return null
        val previousMs = samples.lastOrNull { it.timeMs < coarse }?.timeMs ?: coarse
        return refine(retriever, previousMs, coarse, upperIsCredit = true, null, isCancelled)?.second
    }

    private fun scan(retriever: MediaMetadataRetriever, onProgress: (Float) -> Unit, isCancelled: () -> Boolean): CreditsScanOutcome {
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            ?: return CreditsScanOutcome.NotFound
        if (durationMs < MIN_DURATION_MS) return CreditsScanOutcome.NotFound
        val windowMs = minOf(MAX_WINDOW_MS, durationMs * 3 / 10)
        // A device where the recogniser can't be created still gets the search by blackness.
        val recognizer = runCatching { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
            .onFailure { Log.w(TAG, "no text recogniser", it) }.getOrNull()
        try {
            val looks = sampleTail(retriever, durationMs, windowMs, recognizer, onProgress, isCancelled)
                ?: return CreditsScanOutcome.Interrupted

            // Credits are frames full of words; a scene has next to none. That reads coloured and
            // animated titles as titles and a dark scene as a scene, which "mostly black" gets
            // the wrong way round both times (Eternals: the first scene was lost inside the
            // "credits" and the titles before it passed for the film). Blackness stays as the
            // fallback for when the words show no roll at all - print too small to read, a
            // script the Latin model doesn't know.
            val byText = recognizer?.let {
                findCreditsLayout(looks.map { (timeMs, look) -> FrameSample(timeMs, look.words >= MIN_CREDIT_WORDS) }, durationMs)
            }
            val samples = looks.map { (timeMs, look) ->
                FrameSample(timeMs, if (byText != null) look.words >= MIN_CREDIT_WORDS else look.black)
            }
            val coarse = byText ?: findCreditsLayout(samples, durationMs) ?: return CreditsScanOutcome.NotFound
            val refineWith = recognizer.takeIf { byText != null }
            Log.i(TAG, if (byText != null) "layout by text" else "layout by blackness")

            // Each boundary is only known to the sampling step; narrow it with a few exact frames.
            fun previousSampleMs(boundaryMs: Long) = samples.lastOrNull { it.timeMs < boundaryMs }?.timeMs ?: boundaryMs
            val creditsStartMs = refine(retriever, previousSampleMs(coarse.creditsStartMs), coarse.creditsStartMs, upperIsCredit = true, refineWith, isCancelled)
                ?.second ?: return CreditsScanOutcome.Interrupted
            // A scene's sound often starts before its picture: the music fades and a voice-over
            // begins over the still-black screen. On the first film this was tried on, that was 15
            // seconds of narration before the first frame - invisible to a search that only looks
            // at pictures. So every scene start is moved back by a fixed lead-in (20 s at first;
            // cut to 5 after Eternals, where the picture start was exact and the user got 20 s of
            // roll). Never back past the credits start or the previous scene's end.
            var earliestMs = creditsStartMs
            val scenes = coarse.scenes.map { scene ->
                // The LOWER bound of the bisect (the last frame still known to be credits).
                val pictureStartMs = refine(retriever, previousSampleMs(scene.startMs), scene.startMs, upperIsCredit = false, refineWith, isCancelled)
                    ?.first ?: return CreditsScanOutcome.Interrupted
                val startMs = (pictureStartMs - SCENE_LEAD_IN_MS).coerceAtLeast(earliestMs)
                earliestMs = scene.endMs ?: pictureStartMs
                PostCreditsScene(startMs, scene.endMs)
            }
            Log.i(TAG, "credits from $creditsStartMs ms, scenes: $scenes")
            return CreditsScanOutcome.Found(CreditsLayout(creditsStartMs, scenes))
        } finally {
            runCatching { recognizer?.close() }
        }
    }

    /**
     * Bisects between [lowMs] and [highMs], where the frame at [highMs] is known to be credit-like
     * or not ([upperIsCredit]) and the one at [lowMs] the opposite. Credit-like means words when a
     * [recognizer] is given (the layout was found by text), blackness otherwise. Returns the
     * narrowed (low, high) pair, or null if the scan was interrupted.
     */
    private fun refine(
        retriever: MediaMetadataRetriever,
        lowMs: Long,
        highMs: Long,
        upperIsCredit: Boolean,
        recognizer: TextRecognizer?,
        isCancelled: () -> Boolean
    ): Pair<Long, Long>? {
        var low = lowMs
        var high = highMs
        repeat(REFINE_STEPS) {
            if (isCancelled() || PlaybackActivity.isActive) return null
            if (high - low < 1000) return low to high
            val middle = (low + high) / 2
            val look = look(retriever, middle, MediaMetadataRetriever.OPTION_CLOSEST, recognizer) ?: return low to high
            val creditLike = if (recognizer != null) look.words >= MIN_CREDIT_WORDS else look.black
            if (creditLike == upperIsCredit) high = middle else low = middle
        }
        return low to high
    }

    /** Null when the retriever has no frame for that time (past the last frame, a decode error). */
    private fun look(retriever: MediaMetadataRetriever, timeMs: Long, option: Int, recognizer: TextRecognizer?): Look? {
        // Small is enough to judge blackness; reading the roll's print needs a real frame.
        val width = if (recognizer != null) TEXT_FRAME_WIDTH else FRAME_WIDTH
        val height = if (recognizer != null) TEXT_FRAME_HEIGHT else FRAME_HEIGHT
        val frame = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(timeMs * 1000, option, width, height)
            } else {
                retriever.getFrameAtTime(timeMs * 1000, option)?.let { original ->
                    original.scale(width, height).also { if (it !== original) original.recycle() }
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
        val black = isCreditLikeFrame(pixels, readable.width, readable.height)
        // Words of two letters or more: stray marks the recogniser makes out of scenery don't count.
        val words = if (recognizer == null) 0 else runCatching {
            Tasks.await(recognizer.process(InputImage.fromBitmap(readable, 0))).textBlocks.sumOf { block ->
                block.lines.sumOf { line -> line.elements.count { element -> element.text.count(Char::isLetter) >= 2 } }
            }
        }.onFailure { Log.w(TAG, "text recognition failed", it) }.getOrDefault(0)
        readable.recycle()
        return Look(black, words)
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

        /** Frame size for reading text: at 160x90 the roll's small print is a grey smear. */
        const val TEXT_FRAME_WIDTH = 640
        const val TEXT_FRAME_HEIGHT = 360
    }
}
