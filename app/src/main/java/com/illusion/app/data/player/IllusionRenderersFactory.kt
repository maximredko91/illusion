package com.illusion.app.data.player

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.illusion.app.data.player.ffmpeg.FfmpegAudioRenderer
import com.illusion.app.data.player.ffmpeg.FfmpegLibrary
import com.illusion.app.data.player.ffmpeg.FfmpegVideoRenderer
import com.illusion.app.domain.model.DecoderMode

/**
 * Renderers for one player instance, shaped by the player's decoder-mode choice (see [DecoderMode]).
 *
 * - The platform decoder list is reordered per mode by [decoderSelector] - AUTO leaves it untouched.
 * - The app's own FFmpeg video renderer goes first in Авто/Программный and last in Аппаратный, so a
 *   tie with the platform renderer goes to whichever the mode prefers. In Авто it additionally
 *   under-reports support for formats the platform decodes well - see [FfmpegVideoRenderer]'s KDoc.
 * - The FFmpeg audio renderer goes after the platform ones: it only claims WMA, which nothing else
 *   decodes, so there is no tie to settle.
 * - EXTENSION_RENDERER_MODE_ON keeps Media3's own optional audio FFmpeg extension (DTS/AC3/TrueHD,
 *   scripts/build_ffmpeg_extension.sh) as a fallback if it's ever put on the classpath; unrelated
 *   to the video renderer above.
 */
@OptIn(UnstableApi::class)
class IllusionRenderersFactory(
    context: Context,
    private val decoderMode: DecoderMode
) : DefaultRenderersFactory(context) {

    init {
        setMediaCodecSelector(decoderSelector(decoderMode))
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out
        )
        if (!FfmpegLibrary.isAvailable) return
        val ffmpeg = FfmpegVideoRenderer(
            decoderMode,
            allowedVideoJoiningTimeMs,
            eventHandler,
            eventListener,
            MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
        )
        if (decoderMode == DecoderMode.HARDWARE) out.add(ffmpeg) else out.add(0, ffmpeg)
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out
        )
        if (FfmpegLibrary.isAvailable) out.add(FfmpegAudioRenderer(eventHandler, eventListener, audioSink))
    }
}
