package com.illusion.app.data.player.ffmpeg

import android.util.Log
import androidx.media3.common.MimeTypes
import com.illusion.app.data.player.asf.WindowsMediaMimeTypes

/**
 * The app's own LGPL FFmpeg build (see scripts/build_ffmpeg_android.sh) - a software video decoder
 * for formats this device's platform decoders handle badly or not at all.
 *
 * Checked against the phone's media_codecs*.xml: MPEG-4 Part 2 only has c2.android.mpeg4.decoder,
 * which is Simple Profile only, so DivX/XviD (Advanced Simple Profile) breaks up into macroblocks;
 * MS-MPEG4 (DivX 3) and VC-1/WMV have no decoder at all; MPEG-2 and H.263 do have working software
 * platform decoders and only go through FFmpeg when the user explicitly picks «Программный».
 * WMV1-3 (from .wmv, see AsfExtractor) has no platform decoder either; WMA goes through FFmpeg too via
 * AsfExtractor's own MIME types, although the phone does list a c2.qti.wma decoder. AC3/E-AC3, DTS and
 * TrueHD audio - the usual tracks in .mkv remuxes - have no platform decoder at all.
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
        WindowsMediaMimeTypes.VIDEO_WMV1 -> "wmv1"
        WindowsMediaMimeTypes.VIDEO_WMV2 -> "wmv2"
        WindowsMediaMimeTypes.VIDEO_WMV3 -> "wmv3"
        WindowsMediaMimeTypes.AUDIO_WMA_V1 -> "wmav1"
        WindowsMediaMimeTypes.AUDIO_WMA_V2 -> "wmav2"
        WindowsMediaMimeTypes.AUDIO_WMA_PRO -> "wmapro"
        // Same mapping as Media3's own decoder_ffmpeg FfmpegLibrary (1.11.0). DTS:X is left out: FFmpeg's
        // dca decoder only has its core, and a platform or passthrough path is the better bet there.
        MimeTypes.AUDIO_AC3 -> "ac3"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "eac3"
        MimeTypes.AUDIO_TRUEHD -> "truehd"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_EXPRESS, MimeTypes.AUDIO_DTS_HD -> "dca"
        else -> null
    }

    /**
     * Formats where FFmpeg should win even in Авто mode, because the platform decoder is broken
     * (MPEG-4 ASP) or absent (MS-MPEG4, VC-1) - as opposed to MPEG-2/H.263, where the platform's
     * own software decoder works and stays the default.
     */
    fun isPreferredOverPlatform(mimeType: String?): Boolean = when (mimeType) {
        MimeTypes.VIDEO_MP4V, MimeTypes.VIDEO_DIVX, MimeTypes.VIDEO_MP42, MimeTypes.VIDEO_MP43,
        MimeTypes.VIDEO_VC1, WindowsMediaMimeTypes.VIDEO_WMV1, WindowsMediaMimeTypes.VIDEO_WMV2,
        WindowsMediaMimeTypes.VIDEO_WMV3 -> true
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
