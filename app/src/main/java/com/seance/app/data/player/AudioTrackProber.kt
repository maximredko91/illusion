package com.seance.app.data.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers

/**
 * Reads a video's audio track languages/labels (e.g. a dub studio name a release tagged the
 * track with) without playing it - reuses the exact same Media3/SmbDataSource pipeline the
 * player uses for real playback, just released right after track info is known instead of ever
 * calling play(). Media3 1.11.0 removed the old static `MetadataRetriever` utility (present up
 * to 1.4.0 - checked by decompiling both versions' sources, not assumed from memory), so this
 * hand-rolls the same "prepare, wait for tracks, release" sequence via a throwaway headless
 * ExoPlayer. Track groups come from container parsing during preparation and are populated
 * before any renderer is enabled/decodes anything, so this never actually touches audio/video
 * frames - only the container header goes over the network.
 *
 * For MP4 files, [Format.label] from that probe is always null - Media3's MP4 extractor never
 * parses the container's per-track title box (confirmed by decompiling `BoxParser.java`, see
 * [Mp4AudioTrackTitleReader]'s doc) - so [mp4TrackTitleReader] separately reads that title
 * straight from the raw bytes and the two results are merged in [audioLabel].
 */
@OptIn(UnstableApi::class)
class AudioTrackProber(
    private val context: Context,
    private val dataSourceFactory: SmbDataSourceFactory,
    private val mp4TrackTitleReader: Mp4AudioTrackTitleReader = Mp4AudioTrackTitleReader()
) {
    suspend fun probe(sourceId: Long, filePath: String, sizeBytes: Long): List<String>? {
        val tracks = probeTracks(sourceId, filePath, sizeBytes) ?: return null
        val mp4Titles = if (isMp4(filePath)) readMp4Titles(sourceId, filePath) else emptyList()
        return audioLabels(tracks, mp4Titles)
    }

    private suspend fun probeTracks(sourceId: Long, filePath: String, sizeBytes: Long): Tracks? =
        withContext(Dispatchers.Main) {
            val player = ExoPlayer.Builder(context, DefaultRenderersFactory(context))
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(context)
                        .setDataSourceFactory(DefaultDataSource.Factory(context, dataSourceFactory))
                )
                .build()
            try {
                withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                    suspendCancellableCoroutine { continuation ->
                        val listener = object : Player.Listener {
                            override fun onTracksChanged(tracks: Tracks) {
                                player.removeListener(this)
                                if (continuation.isActive) continuation.resume(tracks)
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                player.removeListener(this)
                                if (continuation.isActive) continuation.resume(null)
                            }
                        }
                        player.addListener(listener)
                        player.setMediaItem(MediaItem.Builder().setUri(SmbMediaUri.build(sourceId, filePath, sizeBytes)).build())
                        player.prepare()
                    }
                }
            } finally {
                player.release()
            }
        }

    /** Best-effort - a read failure here still leaves the language-only labels from [probeTracks] intact. */
    private suspend fun readMp4Titles(sourceId: Long, filePath: String): List<String?> =
        withContext(Dispatchers.IO) {
            runCatching {
                dataSourceFactory.openFile(sourceId, filePath).use { raf ->
                    mp4TrackTitleReader.readAudioTrackTitles(raf)
                }
            }.getOrDefault(emptyList())
        }

    private fun isMp4(filePath: String): Boolean =
        filePath.substringAfterLast('.', "").lowercase() in setOf("mp4", "m4v")

    /**
     * [mp4Titles] is indexed in raw `trak` file order - matched against this same audio-track
     * enumeration order, which for the overwhelmingly common single-audio-track case is trivially
     * correct.
     */
    private fun audioLabels(tracks: Tracks, mp4Titles: List<String?>): List<String> =
        tracks.groups
            .asSequence()
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { group -> (0 until group.length).map { i -> group.getTrackFormat(i) } }
            .mapIndexed { index, format -> audioLabel(format, mp4Titles.getOrNull(index)) }
            .distinct()
            .toList()

    private fun audioLabel(format: Format, mp4Title: String?): String {
        val language = format.language?.let { languageName(it) }
        val label = format.label?.takeIf { it.isNotBlank() && !it.equals(format.language, ignoreCase = true) }
            ?: mp4Title?.takeIf { it.isNotBlank() }
        return listOfNotNull(language, label).joinToString(" · ").ifBlank { "Без описания" }
    }

    private fun languageName(code: String): String = when (code.lowercase()) {
        "rus", "ru" -> "Русский"
        "eng", "en" -> "Английский"
        "ukr", "uk" -> "Украинский"
        "ger", "de" -> "Немецкий"
        "fre", "fra", "fr" -> "Французский"
        "spa", "es" -> "Испанский"
        "jpn", "ja" -> "Японский"
        "kor", "ko" -> "Корейский"
        "chi", "zho", "zh" -> "Китайский"
        else -> code.uppercase()
    }

    companion object {
        private const val PROBE_TIMEOUT_MS = 15_000L
    }
}
