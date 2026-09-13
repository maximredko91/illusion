package com.illusion.app.data.player.ffmpeg

import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.VideoDecoderOutputBuffer
import java.nio.ByteBuffer

/**
 * libavcodec-backed [SimpleDecoder], modelled on Media3's own VpxDecoder (decoder_vp9, 1.11.0).
 *
 * SimpleDecoder expects one output buffer per input buffer, while libavcodec reorders frames
 * (B-frames) and may hold one back: an input that produces no frame yet just yields a skipped
 * output, and each real frame carries its own reordered timestamp rather than the input's. The
 * cost is that the last held-back frame of a stream never comes out - end of stream in
 * SimpleDecoder doesn't reach [decode] at all - one frame, invisible in practice.
 */
// SimpleDecoder fills both arrays itself via createInputBuffer()/createOutputBuffer() in its own
// constructor; they only need to arrive empty and correctly sized, hence the unchecked null arrays.
@Suppress("UNCHECKED_CAST")
@OptIn(UnstableApi::class)
class FfmpegVideoDecoder(
    format: Format,
    numInputBuffers: Int,
    numOutputBuffers: Int,
    initialInputBufferSize: Int,
    threads: Int
) : SimpleDecoder<DecoderInputBuffer, VideoDecoderOutputBuffer, FfmpegDecoderException>(
    arrayOfNulls<DecoderInputBuffer>(numInputBuffers) as Array<DecoderInputBuffer>,
    arrayOfNulls<VideoDecoderOutputBuffer>(numOutputBuffers) as Array<VideoDecoderOutputBuffer>
) {
    private val codecName: String =
        FfmpegLibrary.codecNameFor(format.sampleMimeType)
            ?: throw FfmpegDecoderException("No FFmpeg decoder for ${format.sampleMimeType}")

    private val nativeContext: Long

    @Volatile
    private var outputMode: Int = C.VIDEO_OUTPUT_MODE_NONE

    init {
        if (!FfmpegLibrary.isAvailable) throw FfmpegDecoderException("FFmpeg native libraries not loaded")
        nativeContext = nativeInit(
            codecName,
            format.initializationData.firstOrNull(),
            format.width.coerceAtLeast(0),
            format.height.coerceAtLeast(0),
            threads
        )
        if (nativeContext == 0L) throw FfmpegDecoderException("Failed to open FFmpeg decoder $codecName")
        setInitialInputBufferSize(initialInputBufferSize)
    }

    override fun getName(): String = "ffmpeg-$codecName"

    override fun createInputBuffer(): DecoderInputBuffer =
        DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)

    override fun createOutputBuffer(): VideoDecoderOutputBuffer =
        VideoDecoderOutputBuffer { releaseOutputBuffer(it) }

    override fun releaseOutputBuffer(outputBuffer: VideoDecoderOutputBuffer) {
        // Skipped outputs never got a native frame attached; everything else in Surface mode did.
        if (outputMode == C.VIDEO_OUTPUT_MODE_SURFACE_YUV && !outputBuffer.shouldBeSkipped) {
            nativeReleaseFrame(outputBuffer)
        }
        super.releaseOutputBuffer(outputBuffer)
    }

    override fun createUnexpectedDecodeException(error: Throwable): FfmpegDecoderException =
        FfmpegDecoderException("Unexpected decode error", error)

    override fun decode(
        inputBuffer: DecoderInputBuffer,
        outputBuffer: VideoDecoderOutputBuffer,
        reset: Boolean
    ): FfmpegDecoderException? {
        if (reset) nativeFlush(nativeContext)
        val data = inputBuffer.data ?: return FfmpegDecoderException("Input buffer has no data")
        when (nativeDecode(nativeContext, data, data.limit(), inputBuffer.timeUs)) {
            FRAME_READY -> Unit
            NO_FRAME -> {
                outputBuffer.shouldBeSkipped = true
                return null
            }
            else -> return FfmpegDecoderException("Decode error in $codecName")
        }

        val frameTimeUs = nativeGetFrameTimeUs(nativeContext).takeIf { it != NO_PTS } ?: inputBuffer.timeUs
        if (!isAtLeastOutputStartTimeUs(frameTimeUs)) {
            // Before the seek target: the native side drops the unclaimed frame on the next decode.
            outputBuffer.shouldBeSkipped = true
            return null
        }
        outputBuffer.init(frameTimeUs, outputMode, null)
        if (nativeGetFrame(nativeContext, outputBuffer, outputMode) != FRAME_READY) {
            return FfmpegDecoderException("Failed to hand over decoded frame from $codecName")
        }
        outputBuffer.format = inputBuffer.format
        return null
    }

    override fun release() {
        super.release()
        nativeRelease(nativeContext)
    }

    fun setOutputMode(outputMode: Int) {
        this.outputMode = outputMode
    }

    fun renderToSurface(outputBuffer: VideoDecoderOutputBuffer, surface: Surface) {
        if (nativeRenderFrame(nativeContext, surface, outputBuffer) == ERROR) {
            throw FfmpegDecoderException("Failed to render $codecName frame to surface")
        }
    }

    private external fun nativeInit(codecName: String, extraData: ByteArray?, width: Int, height: Int, threads: Int): Long
    private external fun nativeDecode(context: Long, data: ByteBuffer, size: Int, timeUs: Long): Int
    private external fun nativeGetFrameTimeUs(context: Long): Long
    private external fun nativeGetFrame(context: Long, outputBuffer: VideoDecoderOutputBuffer, outputMode: Int): Int
    private external fun nativeRenderFrame(context: Long, surface: Surface, outputBuffer: VideoDecoderOutputBuffer): Int
    private external fun nativeReleaseFrame(outputBuffer: VideoDecoderOutputBuffer)
    private external fun nativeFlush(context: Long)
    private external fun nativeRelease(context: Long)

    private companion object {
        // Must match ffmpeg_video_jni.cc.
        const val FRAME_READY = 0
        const val NO_FRAME = 1
        const val ERROR = -1

        // AV_NOPTS_VALUE (INT64_MIN).
        const val NO_PTS = Long.MIN_VALUE
    }
}
