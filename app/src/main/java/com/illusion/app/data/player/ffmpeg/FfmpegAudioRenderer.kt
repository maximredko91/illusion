package com.illusion.app.data.player.ffmpeg

import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.decoder.CryptoConfig
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DecoderAudioRenderer

/**
 * Audio renderer on top of [FfmpegAudioDecoder], modelled on Media3's decoder_ffmpeg
 * FfmpegAudioRenderer (1.11.0). Only claims the formats [FfmpegLibrary] maps to a libavcodec audio
 * decoder (WMA, AC3/E-AC3, DTS, TrueHD). It sits after the platform audio renderer, so where the
 * platform can take those itself - HDMI passthrough to a receiver on the TV box - that still wins.
 */
@OptIn(UnstableApi::class)
class FfmpegAudioRenderer(
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    audioSink: AudioSink
) : DecoderAudioRenderer<FfmpegAudioDecoder>(eventHandler, eventListener, audioSink) {

    override fun getName(): String = "FfmpegAudioRenderer"

    override fun supportsFormatInternal(format: Format): Int {
        val mimeType = format.sampleMimeType
        if (!MimeTypes.isAudio(mimeType) || !FfmpegLibrary.supportsFormat(mimeType)) return C.FORMAT_UNSUPPORTED_TYPE
        if (!sinkSupports(format, C.ENCODING_PCM_16BIT) && !sinkSupports(format, C.ENCODING_PCM_FLOAT)) {
            return C.FORMAT_UNSUPPORTED_SUBTYPE
        }
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) return C.FORMAT_UNSUPPORTED_DRM
        return C.FORMAT_HANDLED
    }

    override fun createDecoder(format: Format, cryptoConfig: CryptoConfig?): FfmpegAudioDecoder {
        val initialInputBufferSize =
            if (format.maxInputSize != Format.NO_VALUE) format.maxInputSize else DEFAULT_INPUT_BUFFER_SIZE
        // 16-bit whenever the sink takes it - WMA's float output gains nothing audible over it here,
        // and 16-bit PCM is the one encoding every AudioTrack path supports without conversion.
        val outputFloat = !sinkSupports(format, C.ENCODING_PCM_16BIT)
        return FfmpegAudioDecoder(format, NUM_BUFFERS, NUM_BUFFERS, initialInputBufferSize, outputFloat)
    }

    override fun getOutputFormat(decoder: FfmpegAudioDecoder): Format =
        Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(decoder.channelCount)
            .setSampleRate(decoder.sampleRate)
            .setPcmEncoding(decoder.encoding)
            .build()

    private fun sinkSupports(format: Format, encoding: Int): Boolean =
        sinkSupportsFormat(Util.getPcmFormat(encoding, format.channelCount, format.sampleRate))

    private companion object {
        const val NUM_BUFFERS = 16
        const val DEFAULT_INPUT_BUFFER_SIZE = 64 * 1024
    }
}
