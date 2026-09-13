package com.illusion.app.data.player.asf

import androidx.media3.common.Format
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Sample MIME types for Windows Media codecs, which Media3 has no constants for (VC-1 is the only
 * one it knows, as [androidx.media3.common.MimeTypes.VIDEO_VC1]). Produced by [AsfExtractor] and
 * mapped to libavcodec decoder names by [com.illusion.app.data.player.ffmpeg.FfmpegLibrary] - no
 * platform decoder on these devices handles any of them.
 */
object WindowsMediaMimeTypes {
    const val VIDEO_WMV1 = "video/x-ms-wmv1"
    const val VIDEO_WMV2 = "video/x-ms-wmv2"
    const val VIDEO_WMV3 = "video/x-ms-wmv3"
    const val AUDIO_WMA_V1 = "audio/x-ms-wma-v1"
    const val AUDIO_WMA_V2 = "audio/x-ms-wma-v2"
    const val AUDIO_WMA_PRO = "audio/x-ms-wma-pro"

    /**
     * WMA decoders need the WAVEFORMATEX block alignment and bit depth besides the codec extra data,
     * and [Format] has no field for either - so they ride along as a second initialization data
     * entry: two little-endian ints.
     */
    fun encodeAudioParams(blockAlign: Int, bitsPerSample: Int): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(blockAlign).putInt(bitsPerSample).array()

    /** Inverse of [encodeAudioParams]: (blockAlign, bitsPerSample), zeros if absent. */
    fun decodeAudioParams(format: Format): Pair<Int, Int> {
        val data = format.initializationData.getOrNull(1)?.takeIf { it.size >= 8 } ?: return 0 to 0
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        return buffer.int to buffer.int
    }
}
