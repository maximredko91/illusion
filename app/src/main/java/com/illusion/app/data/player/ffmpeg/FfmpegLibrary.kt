package com.illusion.app.data.player.ffmpeg

import android.util.Log
import androidx.media3.common.MimeTypes

/**
 * The app's own LGPL FFmpeg build (see scripts/build_ffmpeg_android.sh) - a software video decoder
 * for formats this device's platform decoders handle badly or not at all.
 *
 * Checked against the phone's media_codecs*.xml: MPEG-4 Part 2 only has c2.android.mpeg4.decoder,
 * which is Simple Profile only, so DivX/XviD (Advanced Simple Profile) breaks up into macroblocks;
 * MS-MPEG4 (DivX 3) and VC-1/WMV have no decoder at all; MPEG-2 and H.263 do have working software
 * platform decoders and only go through FFmpeg when the user explicitly picks «Программный».
 */
object FfmpegLibrary {
    private const val TAG = "FfmpegLibrary"

    val isAvailable: Boolean by lazy {
        try {
            // libavcodec/libswscale/libavutil are DT_NEEDED dependencies of this library and get
            // loaded from the APK's own lib directory automatically.
            System.loadLibrary("illusion_ffmpeg")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "FFmpeg native libraries unavailable", e)
            false
        }
    }

    /** libavcodec decoder name for a Media3 sample MIME type, or null if this build has none for it. */
    fun codecNameFor(mimeType: String?): String? = when (mimeType) {
        MimeTypes.VIDEO_MP4V, MimeTypes.VIDEO_DIVX -> "mpeg4"
        MimeTypes.VIDEO_MP42 -> "msmpeg4v2"
        MimeTypes.VIDEO_MP43 -> "msmpeg4v3"
        MimeTypes.VIDEO_H263 -> "h263"
        MimeTypes.VIDEO_VC1 -> "vc1"
        MimeTypes.VIDEO_MPEG2 -> "mpeg2video"
        MimeTypes.VIDEO_MPEG -> "mpeg1video"
        else -> null
    }

    /**
     * Formats where FFmpeg should win even in Авто mode, because the platform decoder is broken
     * (MPEG-4 ASP) or absent (MS-MPEG4, VC-1) - as opposed to MPEG-2/H.263, where the platform's
     * own software decoder works and stays the default.
     */
    fun isPreferredOverPlatform(mimeType: String?): Boolean = when (mimeType) {
        MimeTypes.VIDEO_MP4V, MimeTypes.VIDEO_DIVX, MimeTypes.VIDEO_MP42, MimeTypes.VIDEO_MP43,
        MimeTypes.VIDEO_VC1 -> true
        else -> false
    }

    fun supportsFormat(mimeType: String?): Boolean {
        val codecName = codecNameFor(mimeType) ?: return false
        return isAvailable && nativeHasDecoder(codecName)
    }

    val version: String? get() = if (isAvailable) nativeGetVersion() else null

    private external fun nativeGetVersion(): String
    private external fun nativeHasDecoder(codecName: String): Boolean
}
