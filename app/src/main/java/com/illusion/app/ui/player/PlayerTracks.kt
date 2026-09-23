package com.illusion.app.ui.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.illusion.app.data.player.SmbMediaUri

/** Audio and subtitle options offered in the player's track pickers, in the tracks' own order. */
internal fun trackOptionsFrom(tracks: Tracks): Pair<List<TrackOption>, List<TrackOption>> {
    val audio = mutableListOf<TrackOption>()
    val subtitles = mutableListOf<TrackOption>()
    for (group in tracks.groups) {
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            // format.language is a raw ISO code ("ru") - it was winning over format.label and
            // showing up verbatim in the track picker. Prefer the embedded human label, and
            // otherwise turn the code into its Russian display name ("Русский").
            val label = format.label
                ?: format.language?.let { code ->
                    java.util.Locale.forLanguageTag(code)
                        .getDisplayLanguage(java.util.Locale("ru"))
                        .takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
                        ?.replaceFirstChar { c -> c.uppercase() }
                        ?: code
                }
                ?: "Дорожка ${i + 1}"
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> audio += TrackOption(group, i, label, group.isTrackSelected(i))
                C.TRACK_TYPE_TEXT -> subtitles += TrackOption(group, i, label, group.isTrackSelected(i))
                else -> Unit
            }
        }
    }
    return audio to subtitles
}

/**
 * The GL effects pipeline (i.e. sharpen on) never fires onVideoSizeChanged - its implementation
 * is a deliberate upstream no-op in Media3 1.11.0 (TODO b/292111083), which is why aspect-ratio
 * cycling used to go dead for the rest of the session once sharpen had been switched on. The
 * selected video track's own Format carries width/height/pixelWidthHeightRatio regardless of
 * which render path is in use, so read the ratio from there instead and hand it to the UI,
 * which applies it to PlayerView's content frame itself (see PlayerScreen).
 */
@OptIn(UnstableApi::class)
internal fun videoAspectRatioFrom(tracks: Tracks): Float? {
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_VIDEO) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSelected(i)) continue
            val format = group.getTrackFormat(i)
            if (format.width <= 0 || format.height <= 0) continue
            val pixelRatio = if (format.pixelWidthHeightRatio > 0f) format.pixelWidthHeightRatio else 1f
            // A 90/270-degree rotation swaps the displayed dimensions.
            val rotated = format.rotationDegrees == 90 || format.rotationDegrees == 270
            val width = (if (rotated) format.height else format.width).toFloat()
            val height = (if (rotated) format.width else format.height).toFloat()
            val ratio = if (rotated) width / (height * pixelRatio) else (width * pixelRatio) / height
            return ratio.takeIf { it > 0f }
        }
    }
    return null
}

internal fun buildSubtitleConfig(sourceId: Long, path: String): MediaItem.SubtitleConfiguration =
    buildSubtitleConfig(SmbMediaUri.build(sourceId, path), path)

/** [remotePath] is only used to derive mime type/language/label from its filename - the actual bytes are read from [uri], which may be the live SMB path or a downloaded local copy. */
internal fun buildSubtitleConfig(uri: Uri, remotePath: String): MediaItem.SubtitleConfiguration {
    val extension = remotePath.substringAfterLast('.', "").lowercase()
    val mimeType = when (extension) {
        "ass" -> MimeTypes.TEXT_SSA
        "vtt" -> MimeTypes.TEXT_VTT
        else -> MimeTypes.APPLICATION_SUBRIP
    }
    val fileName = remotePath.substringAfterLast('\\')
    val language = guessLanguage(fileName)
    return MediaItem.SubtitleConfiguration.Builder(uri)
        .setMimeType(mimeType)
        .setLanguage(language)
        .setLabel(language ?: fileName)
        // Without a selection flag, ExoPlayer's default track selector never auto-picks a text
        // track on its own - subtitlesEnabled=true in the UI state only means the track TYPE
        // isn't disabled, it doesn't mean any specific track actually gets selected/rendered.
        // Sidecar subtitles found during scanning are exactly the case a viewer wants shown
        // automatically (unlike embedded tracks in other languages they didn't ask for).
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()
}

internal fun guessLanguage(fileName: String): String? {
    val parts = fileName.split('.')
    if (parts.size < 3) return null
    val candidate = parts[parts.size - 2]
    return candidate.takeIf { it.length in 2..3 && it.all { c -> c.isLetter() } }?.lowercase()
}
