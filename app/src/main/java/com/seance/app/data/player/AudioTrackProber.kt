package com.seance.app.data.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a video's audio track title(s) (e.g. a dub studio name a release tagged the track with)
 * straight from the MP4 container's `moov/trak/udta/name` box, via a couple of small positional
 * SMB reads - see [Mp4AudioTrackTitleReader]'s doc for why this can't come from Media3's own
 * track probing. An earlier version of this class instead launched a throwaway headless ExoPlayer
 * to prepare the file and read `Tracks`/`Format.label` - noticeably slow to open a Details card
 * with (a full SMB connection + container parse just to read track metadata) and, for MP4 files,
 * [androidx.media3.common.Format.label] was always null anyway (confirmed by decompiling
 * `BoxParser.java` - it never calls `Format.Builder.setLabel()`). Removed in favor of this
 * direct read, which only supports MP4/M4V for the same reason.
 */
class AudioTrackProber(
    private val dataSourceFactory: SmbDataSourceFactory,
    private val mp4TrackTitleReader: Mp4AudioTrackTitleReader = Mp4AudioTrackTitleReader()
) {
    suspend fun probe(sourceId: Long, filePath: String): List<String>? {
        if (!isMp4(filePath)) return null
        val titles = readMp4Titles(sourceId, filePath)
        if (titles.isEmpty()) return null
        return titles.map { it?.takeIf(String::isNotBlank) ?: "Без описания" }.distinct()
    }

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
}
