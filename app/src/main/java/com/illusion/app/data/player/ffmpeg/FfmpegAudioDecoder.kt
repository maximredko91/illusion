package com.illusion.app.data.player.ffmpeg

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import com.illusion.app.data.player.asf.WindowsMediaMimeTypes
import java.nio.ByteBuffer

/**
 * libavcodec-backed audio [SimpleDecoder] - WMA from .wmv files, which no platform decoder on these
 * devices handles. Modelled on Media3's own decoder_ffmpeg FfmpegAudioDecoder (1.11.0), except that
 * the native side decodes a packet into its own buffer first and reports the size, so the output
 * buffer is sized here instead of native code calling back into Java to grow it.
 */
// SimpleDecoder fills both arrays itself via createInputBuffer()/createOutputBuffer() in its own
// constructor; they only need to arrive empty and correctly sized, hence the unchecked null arrays.
@Suppress("UNCHECKED_CAST")
@OptIn(UnstableApi::class)
class FfmpegAudioDecoder(
    format: Format,
    numInputBuffers: Int,
    numOutputBuffers: Int,
    initialInputBufferSize: Int,
    outputFloat: Boolean
) : SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FfmpegDecoderException>(
    arrayOfNulls<DecoderInputBuffer>(numInputBuffers) as Array<DecoderInputBuffer>,
    arrayOfNulls<SimpleDecoderOutputBuffer>(numOutputBuffers) as Array<SimpleDecoderOutputBuffer>
) {
    private val codecName: String =
        FfmpegLibrary.codecNameFor(format.sampleMimeType)
            ?: throw FfmpegDecoderException("No FFmpeg decoder for ${format.sampleMimeType}")

    private val nativeContext: Long

    val encoding: Int = if (outputFloat) C.ENCODING_PCM_FLOAT else C.ENCODING_PCM_16BIT

    @Volatile
    var channelCount: Int = format.channelCount
        private set

    @Volatile
    var sampleRate: Int = format.sampleRate
        private set

    init {
        if (!FfmpegLibrary.isAvailable) throw FfmpegDecoderException("FFmpeg native libraries not loaded")
        val (blockAlign, bitsPerSample) = WindowsMediaMimeTypes.decodeAudioParams(format)
        nativeContext = nativeInit(
            codecName,
            format.initializationData.firstOrNull()?.takeIf { it.isNotEmpty() },
            format.sampleRate.coerceAtLeast(0),
            format.channelCount.coerceAtLeast(0),
            format.averageBitrate.coerceAtLeast(0),
            blockAlign,
            bitsPerSample,
            outputFloat
        )
        if (nativeContext == 0L) throw FfmpegDecoderException("Failed to open FFmpeg decoder $codecName")
        setInitialInputBufferSize(initialInputBufferSize)
    }

    override fun getName(): String = "ffmpeg-$codecName"

    override fun createInputBuffer(): DecoderInputBuffer =
        DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)

    override fun createOutputBuffer(): SimpleDecoderOutputBuffer =
        SimpleDecoderOutputBuffer { releaseOutputBuffer(it) }

    override fun createUnexpectedDecodeException(error: Throwable): FfmpegDecoderException =
        FfmpegDecoderException("Unexpected decode error", error)

    override fun decode(
        inputBuffer: DecoderInputBuffer,
        outputBuffer: SimpleDecoderOutputBuffer,
        reset: Boolean
    ): FfmpegDecoderException? {
        if (reset) nativeFlush(nativeContext)
        val data = inputBuffer.data ?: return FfmpegDecoderException("Input buffer has no data")
        val size = nativeDecode(nativeContext, data, data.limit())
        if (size == ERROR_FATAL) return FfmpegDecoderException("Decode error in $codecName")
        if (size <= 0) {
            // Damaged packet (logged natively) or a packet that produced no audio yet.
            outputBuffer.shouldBeSkipped = true
            return null
        }
        val output = outputBuffer.init(inputBuffer.timeUs, size)
        nativeReadOutput(nativeContext, output, size)
        output.position(0)
        output.limit(size)
        channelCount = nativeGetChannelCount(nativeContext)
        sampleRate = nativeGetSampleRate(nativeContext)
        return null
    }

    override fun release() {
        super.release()
        nativeRelease(nativeContext)
    }

    private external fun nativeInit(
        codecName: String,
        extraData: ByteArray?,
        sampleRate: Int,
        channelCount: Int,
        bitRate: Int,
        blockAlign: Int,
        bitsPerSample: Int,
        outputFloat: Boolean
    ): Long

    private external fun nativeDecode(context: Long, data: ByteBuffer, size: Int): Int
    private external fun nativeReadOutput(context: Long, output: ByteBuffer, size: Int)
    private external fun nativeGetChannelCount(context: Long): Int
    private external fun nativeGetSampleRate(context: Long): Int
    private external fun nativeFlush(context: Long)
    private external fun nativeRelease(context: Long)

    private companion object {
        // Must match ffmpeg_audio_jni.cc. -1 (damaged packet) is handled like "no output".
        const val ERROR_FATAL = -2
    }
}
