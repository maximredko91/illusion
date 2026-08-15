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
 */
@OptIn(UnstableApi::class)
class AudioTrackProber(
    private val context: Context,
    private val dataSourceFactory: SmbDataSourceFactory
) {
    suspend fun probe(sourceId: Long, filePath: String, sizeBytes: Long): List<String>? =
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
                                if (continuation.isActive) continuation.resume(audioLabels(tracks))
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

    private fun audioLabels(tracks: Tracks): List<String> =
        tracks.groups
            .asSequence()
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { group -> (0 until group.length).map { i -> group.getTrackFormat(i) } }
            .map { format -> audioLabel(format) }
            .distinct()
            .toList()

    private fun audioLabel(format: Format): String {
        val language = format.language?.let { languageName(it) }
        val label = format.label?.takeIf { it.isNotBlank() && !it.equals(format.language, ignoreCase = true) }
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
