package com.illusion.app.data.player.ffmpeg

import android.os.Handler
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.CryptoConfig
import androidx.media3.decoder.VideoDecoderOutputBuffer
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.video.DecoderVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.illusion.app.domain.model.DecoderMode

/**
 * Video renderer on top of [FfmpegVideoDecoder], modelled on Media3's LibvpxVideoRenderer.
 *
 * How it competes with the platform's MediaCodecVideoRenderer depends on [decoderMode] (see
 * [com.illusion.app.data.player.IllusionRenderersFactory] for the renderer order): ExoPlayer's track
 * selector gives a track to the renderer reporting the best support level, the earlier renderer on a
 * tie. In Авто this renderer sits first but reports [C.FORMAT_EXCEEDS_CAPABILITIES] for formats the
 * platform decodes fine, so the platform still wins those and FFmpeg only takes over where the
 * platform decoder is broken or missing.
 *
 * Doesn't support Media3 video effects - the sharpen shader has no effect on FFmpeg-decoded video.
 */
@OptIn(UnstableApi::class)
class FfmpegVideoRenderer(
    private val decoderMode: DecoderMode,
    allowedJoiningTimeMs: Long,
    eventHandler: Handler?,
    eventListener: VideoRendererEventListener?,
    maxDroppedFramesToNotify: Int
) : DecoderVideoRenderer(allowedJoiningTimeMs, eventHandler, eventListener, maxDroppedFramesToNotify) {

    private var decoder: FfmpegVideoDecoder? = null

    override fun getName(): String = "FfmpegVideoRenderer"

    override fun supportsFormat(format: Format): Int {
        val mimeType = format.sampleMimeType
        if (!MimeTypes.isVideo(mimeType) || !FfmpegLibrary.supportsFormat(mimeType)) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        }
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_DRM)
        }
        val support = if (decoderMode == DecoderMode.AUTO && !FfmpegLibrary.isPreferredOverPlatform(mimeType)) {
            C.FORMAT_EXCEEDS_CAPABILITIES
        } else {
            C.FORMAT_HANDLED
        }
        return RendererCapabilities.create(
            support,
            RendererCapabilities.ADAPTIVE_NOT_SEAMLESS,
            RendererCapabilities.TUNNELING_NOT_SUPPORTED
        )
    }

    override fun createDecoder(format: Format, cryptoConfig: CryptoConfig?): FfmpegVideoDecoder {
        val initialInputBufferSize =
            if (format.maxInputSize != Format.NO_VALUE) format.maxInputSize else DEFAULT_INPUT_BUFFER_SIZE
        return FfmpegVideoDecoder(
            format,
            NUM_BUFFERS,
            NUM_BUFFERS,
            initialInputBufferSize,
            Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_THREADS)
        ).also { decoder = it }
    }

    override fun renderOutputBufferToSurface(outputBuffer: VideoDecoderOutputBuffer, surface: Surface) {
        val decoder = decoder ?: throw FfmpegDecoderException("Render before decoder was created")
        decoder.renderToSurface(outputBuffer, surface)
        outputBuffer.release()
    }

    override fun setDecoderOutputMode(outputMode: Int) {
        decoder?.setOutputMode(outputMode)
    }

    override fun canReuseDecoder(decoderName: String, oldFormat: Format, newFormat: Format): DecoderReuseEvaluation {
        val sameStream = oldFormat.sampleMimeType == newFormat.sampleMimeType &&
            oldFormat.initializationData == newFormat.initializationData
        return DecoderReuseEvaluation(
            decoderName,
            oldFormat,
            newFormat,
            if (sameStream) DecoderReuseEvaluation.REUSE_RESULT_YES_WITHOUT_RECONFIGURATION else DecoderReuseEvaluation.REUSE_RESULT_NO,
            if (sameStream) 0 else DecoderReuseEvaluation.DISCARD_REASON_INITIALIZATION_DATA_CHANGED
        )
    }

    private companion object {
        const val NUM_BUFFERS = 4
        const val DEFAULT_INPUT_BUFFER_SIZE = 768 * 1024

        // Slice threading beyond this buys nothing for SD/HD MPEG-2 and just burns cores.
        const val MAX_THREADS = 4
    }
}
