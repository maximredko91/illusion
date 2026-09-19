package com.illusion.app.data.intro

import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.scan.SmbMediaDataSource
import com.illusion.app.data.smb.SmbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds a season's title sequence by comparing what two of its episodes sound like - the automatic
 * counterpart to the player's manual "отметить конец заставки".
 *
 * Only the opening [ANALYSIS_WINDOW_MS] of each episode is looked at: that's where intros live, and
 * it bounds the SMB traffic (MediaExtractor pulls the container's video bytes past the audio it
 * actually wants, so the whole window is read off the share either way - minutes of transfer per
 * episode, which is why this is a background job with a progress UI and not something that runs
 * behind the user's back).
 */
class IntroDetector(
    private val sourceRepository: SmbSourceRepository,
    private val smbClient: SmbClient
) {

    /**
     * Fingerprints [item] and [reference] and returns the intro's position **in [item]'s own
     * timeline**, or null when the two share nothing intro-shaped (a show with no fixed opening,
     * an episode whose audio couldn't be decoded, a cancelled job).
     */
    suspend fun detect(
        item: MediaItemEntity,
        reference: MediaItemEntity,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): IntroMatch? = withContext(Dispatchers.IO) {
        val first = fingerprintOf(item, isCancelled) ?: return@withContext null
        onProgress(0.5f)
        if (isCancelled()) return@withContext null
        val second = fingerprintOf(reference, isCancelled) ?: return@withContext null
        onProgress(0.9f)
        if (isCancelled()) return@withContext null
        findSharedIntro(first, second)
    }

    private suspend fun fingerprintOf(item: MediaItemEntity, isCancelled: () -> Boolean): Fingerprint? {
        if (item.sizeBytes <= 0) return null
        val info = sourceRepository.connectionInfoById(item.sourceId) ?: return null
        return smbClient.connect(info).use { connection ->
            val randomAccessFile = connection.openRandomAccessFile(item.filePath)
            val samples = decodeMonoPcm(SmbMediaDataSource(randomAccessFile, item.sizeBytes), ANALYSIS_WINDOW_MS, isCancelled)
                ?: return@use null
            fingerprint(samples).takeIf { it.size > 0 }
        }
    }

    companion object {
        /** Five minutes covers a cold open plus the theme on effectively every show. */
        const val ANALYSIS_WINDOW_MS = 5 * 60 * 1000L
    }
}

/**
 * The episode to compare [item] against: the next one in the same season, or the previous one when
 * [item] is last. Picking a neighbour rather than, say, episode 1 keeps a season that switches
 * intro halfway (rare, but real) matching against something contemporaneous.
 */
fun pickReferenceEpisode(item: MediaItemEntity, seasonEpisodes: List<MediaItemEntity>): MediaItemEntity? {
    val sorted = seasonEpisodes
        .filter { it.stableId != item.stableId && it.sizeBytes > 0 }
        .sortedBy { it.episodeNumber ?: Int.MAX_VALUE }
    if (sorted.isEmpty()) return null
    val episodeNumber = item.episodeNumber ?: return sorted.first()
    return sorted.firstOrNull { (it.episodeNumber ?: Int.MAX_VALUE) > episodeNumber }
        ?: sorted.last()
}
