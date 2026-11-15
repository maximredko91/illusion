package com.illusion.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.illusion.app.data.credits.CreditsDetector
import com.illusion.app.data.intro.AutoIntroQueue
import com.illusion.app.data.intro.IntroDetector
import com.illusion.app.data.intro.TailFingerprint
import com.illusion.app.data.intro.findSharedIntro
import com.illusion.app.data.intro.pickReferenceEpisode
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.smb.SmbClient

/**
 * Finds a series' title sequences or its credits by audio and writes the markers - the automatic
 * counterpart to the player's manual marking (see [IntroDetector] for how). Started from the
 * series' Details screen and always covers the whole series, season by season - or, after a
 * library scan, run unattended over the seasons that got new episodes ([drainQueue]).
 *
 * A worker rather than a view-model coroutine because it runs for minutes (episodes' opening or
 * closing minutes are pulled off the SMB share) and has to survive the screen closing or the user
 * switching apps mid-search. It reports only progress: what got marked is read back from Room by
 * whoever is showing it.
 */
class IntroDetectWorker(
    context: Context,
    params: WorkerParameters,
    private val libraryRepository: LibraryRepository,
    private val sourceRepository: SmbSourceRepository,
    private val smbClient: SmbClient
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (inputData.getBoolean(KEY_AUTO_QUEUE, false)) return drainQueue()
        val seriesId = inputData.getString(KEY_SERIES_ID) ?: return Result.failure()
        val bySeason = libraryRepository.getSeriesEpisodes(seriesId)
            .filter { it.seasonNumber != null && it.sizeBytes > 0 }
            .groupBy { it.seasonNumber!! }
            .toSortedMap()
            .mapValues { (_, episodes) -> episodes.sortedBy { it.episodeNumber ?: Int.MAX_VALUE } }
        if (bySeason.isEmpty()) return Result.failure()

        setProgress(workDataOf(KEY_PROGRESS to 0f))
        val detector = IntroDetector(sourceRepository, smbClient)
        val found = if (inputData.getBoolean(KEY_CREDITS, false)) {
            detectCredits(detector, bySeason)
        } else {
            detectIntros(detector, bySeason)
        }
        return if (found == null || found == 0) Result.failure() else Result.success()
    }

    /**
     * The unattended run after a library scan: works through [AutoIntroQueue] one season at a
     * time. A season that already has markers, or still has a single episode, is just dropped from
     * the queue (the second episode arriving later queues it again). Always "succeeds" - nothing
     * is waiting on the outcome, and a failure would cancel runs appended behind this one; stopped
     * mid-season, it asks to be retried and the season stays queued.
     */
    private suspend fun drainQueue(): Result {
        val queue = AutoIntroQueue(applicationContext)
        val detector = IntroDetector(sourceRepository, smbClient)
        while (true) {
            if (isStopped) return Result.retry()
            val season = queue.peek() ?: return Result.success()
            val episodes = libraryRepository.getSeasonEpisodes(season.seriesStableId, season.seasonNumber)
                .filter { it.sizeBytes > 0 }
                .sortedBy { it.episodeNumber ?: Int.MAX_VALUE }
            if (episodes.size >= 2 && episodes.none { it.introEndMs != null }) {
                val target = episodes.first()
                val match = pickReferenceEpisode(target, episodes)?.let { reference ->
                    detector.detect(item = target, reference = reference, isCancelled = { isStopped })
                }
                if (isStopped) return Result.retry()
                if (match != null) libraryRepository.markIntroRange(target, match.startMs, match.endMs)
                Log.i(TAG, "auto intro S${season.seasonNumber} of ${season.seriesStableId}: ${match ?: "not found"}")
            }
            queue.remove(season)
        }
    }

    /**
     * One detection per season, each season compared within itself (intros change between
     * seasons, so there's nothing to share across them): its first episode against the next one,
     * the range written to the whole season. A season that already has markers is left alone -
     * those are an earlier run's result or the user's own manual mark, and a re-run shouldn't
     * overwrite either; clearing the marker in the player is how a season gets searched again.
     * Returns how many seasons now have an intro, or null if the work was stopped.
     */
    private suspend fun detectIntros(detector: IntroDetector, bySeason: Map<Int, List<MediaItemEntity>>): Int? {
        var found = 0
        var index = 0
        for ((_, episodes) in bySeason) {
            if (isStopped) return null
            val seasonIndex = index++
            if (episodes.any { it.introEndMs != null }) {
                found++
            } else {
                val target = episodes.first()
                val reference = pickReferenceEpisode(target, episodes)
                val match = reference?.let {
                    detector.detect(
                        item = target,
                        reference = it,
                        isCancelled = { isStopped },
                        onProgress = { progress -> setProgressBlocking((seasonIndex + progress) / bySeason.size) }
                    )
                }
                if (match != null) {
                    libraryRepository.markIntroRange(target, match.startMs, match.endMs)
                    found++
                }
            }
            setProgressBlocking((seasonIndex + 1f) / bySeason.size)
        }
        return if (isStopped) null else found
    }

    /**
     * The credits search: fingerprints the closing minutes of EVERY episode, then matches each one
     * against a neighbour in its own season and writes that episode's own credits start. Per
     * episode rather than one value for the season (the intro's scoping) because episodes differ
     * in length - a position found in one is wrong for the rest. An episode whose tail matches
     * neither neighbour (different closing music, an undecodable track) gets a second chance by
     * picture - a black roll running to the end of the file - and is left unmarked if that finds
     * nothing either. Returns how many episodes got a marker, or null if the work was stopped.
     */
    private suspend fun detectCredits(detector: IntroDetector, bySeason: Map<Int, List<MediaItemEntity>>): Int? {
        val pictureDetector = CreditsDetector(sourceRepository, smbClient)
        val total = bySeason.values.sumOf { it.size }
        var done = 0
        var found = 0
        for ((season, episodes) in bySeason) {
            val tails = ArrayList<TailFingerprint?>(episodes.size)
            for (episode in episodes) {
                if (isStopped) return null
                tails += detector.tailFingerprintOf(episode) { isStopped }
                setProgressBlocking(++done / total.toFloat())
            }
            if (isStopped) return null
            for ((index, episode) in episodes.withIndex()) {
                if (isStopped) return null
                val own = tails[index]
                // Next episode first, previous one as the fallback - the last episode has no next,
                // and a neighbour whose tail failed to decode shouldn't cost this one its marker.
                val byAudio = own?.let {
                    listOfNotNull(tails.getOrNull(index + 1), tails.getOrNull(index - 1)).firstNotNullOfOrNull { other ->
                        findSharedIntro(
                            own.fingerprint,
                            other.fingerprint,
                            maxDurationMs = IntroDetector.ANALYSIS_WINDOW_MS,
                            diagnostics = { Log.i(TAG, "credits S${season}E${episode.episodeNumber}: $it") }
                        )
                    }?.let { match -> own.startMs + match.startMs }
                }
                // No shared closing music (whole seasons of the first real series this ran on use a
                // different track every episode), or no decodable audio at all: look for the black
                // roll itself instead. Skipped while something is playing - it needs the decoder.
                val startMs = byAudio
                    ?: pictureDetector.detectEpisodeCreditsStart(episode) { isStopped }
                        ?.also { Log.i(TAG, "credits S${season}E${episode.episodeNumber}: by picture at $it ms") }
                    ?: continue
                libraryRepository.markCreditsStartForEpisode(episode, startMs)
                found++
            }
        }
        return found
    }

    // setProgress is suspending and the detector's callback isn't - progress here is a nicety on a
    // multi-minute job, so a dropped update is harmless and blocking the (background) worker thread
    // for it is not worth threading a coroutine scope through the detector.
    private fun setProgressBlocking(progress: Float) {
        runCatching { setProgressAsync(workDataOf(KEY_PROGRESS to progress)).get() }
    }

    companion object {
        const val KEY_SERIES_ID = "series_id"
        const val KEY_CREDITS = "credits"
        const val KEY_AUTO_QUEUE = "auto_queue"
        const val KEY_PROGRESS = "progress"
        private const val TAG = "IntroDetectWorker"
    }
}
