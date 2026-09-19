package com.illusion.app.data.intro

import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes the first [limitMs] of a file's audio track into mono PCM at [FINGERPRINT_SAMPLE_RATE],
 * reading straight off the SMB share through whatever [MediaDataSource] it's handed.
 *
 * Platform MediaCodec, not the app's own FFmpeg decoders: the formats that need FFmpeg here (DTS,
 * TrueHD, WMA) belong to films and remuxes, not to the TV series this is used on, and a track the
 * platform can't decode simply reports "не удалось" rather than pulling the whole FFmpeg audio
 * path into a background job.
 *
 * Returns null if the file has no audio track, no decoder exists for it, or nothing decoded.
 */
fun decodeMonoPcm(dataSource: MediaDataSource, limitMs: Long, isCancelled: () -> Boolean = { false }): FloatArray? {
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    try {
        extractor.setDataSource(dataSource)
        val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return null

        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(2)
        extractor.selectTrack(trackIndex)

        codec = runCatching { MediaCodec.createDecoderByType(mime) }.getOrNull() ?: return null
        codec.configure(format, null, null, 0)
        codec.start()

        val output = FloatArrayBuilder(expectedSamples(limitMs))
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        // Fractional read position in the SOURCE stream, advanced by sourceRate/target per output
        // sample - a plain "take every Nth sample" decimation aliases badly enough at 8 kHz to
        // change the fingerprint.
        var resamplePosition = 0.0
        val step = sourceRate.toDouble() / FINGERPRINT_SAMPLE_RATE
        var carry = FloatArray(0)

        while (!outputDone && !isCancelled()) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val buffer = codec.getInputBuffer(inputIndex)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0 || extractor.sampleTime > limitMs * 1000) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputIndex >= 0 -> {
                    val buffer = codec.getOutputBuffer(outputIndex)
                    if (buffer != null && bufferInfo.size > 0) {
                        val mono = toMono(buffer, bufferInfo.offset, bufferInfo.size, channels)
                        val joined = if (carry.isEmpty()) mono else carry + mono
                        resamplePosition = resample(joined, resamplePosition, step, output)
                        // Whatever the resampler hasn't consumed yet has to survive into the next
                        // buffer, or every buffer boundary drops a sample and the timeline drifts.
                        val consumed = resamplePosition.toInt()
                        carry = joined.copyOfRange(consumed.coerceAtMost(joined.size), joined.size)
                        resamplePosition -= consumed
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone -> outputDone = true
            }
        }
        return output.toFloatArray().takeIf { it.size >= FRAME_SIZE }
    } catch (e: Exception) {
        // A malformed/unsupported track, a codec refusing this profile, an SMB read failing
        // mid-decode: all mean "no fingerprint for this episode", never a crashed worker.
        return null
    } finally {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { extractor.release() }
        runCatching { dataSource.close() }
    }
}

/** 16-bit PCM (what every platform decoder outputs) down-mixed to one channel, as -1..1 floats. */
private fun toMono(buffer: ByteBuffer, offset: Int, size: Int, channels: Int): FloatArray {
    val shorts = buffer.duplicate().order(ByteOrder.nativeOrder()).apply {
        position(offset)
        limit(offset + size)
    }.asShortBuffer()
    val frames = shorts.remaining() / channels.coerceAtLeast(1)
    val mono = FloatArray(frames)
    for (i in 0 until frames) {
        var sum = 0f
        for (channel in 0 until channels) sum += shorts.get(i * channels + channel) / 32768f
        mono[i] = sum / channels
    }
    return mono
}

/** Linear-interpolating resample of [input] into [output], continuing from [position]; returns the
 * new (fractional) read position within [input]. */
private fun resample(input: FloatArray, position: Double, step: Double, output: FloatArrayBuilder): Double {
    var current = position
    while (current < input.size - 1) {
        val index = current.toInt()
        val fraction = (current - index).toFloat()
        output.add(input[index] * (1f - fraction) + input[index + 1] * fraction)
        current += step
    }
    return current
}

private fun expectedSamples(limitMs: Long) = (limitMs * FINGERPRINT_SAMPLE_RATE / 1000).toInt().coerceAtLeast(FRAME_SIZE)

/** Plain growable float buffer - the decoded stretch is a few MB and ArrayList<Float> would box
 * every single sample. */
private class FloatArrayBuilder(initialCapacity: Int) {
    private var data = FloatArray(initialCapacity)
    private var size = 0

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    fun toFloatArray(): FloatArray = data.copyOf(size)
}

private const val TIMEOUT_US = 10_000L
