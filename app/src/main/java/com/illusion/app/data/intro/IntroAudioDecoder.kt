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
fun decodeMonoPcm(dataSource: MediaDataSource, limitMs: Long, isCancelled: () -> Boolean = { false }): FloatArray? =
    decodeWindow(dataSource, limitMs, fromEnd = false, isCancelled)?.samples

/** Decoded audio plus where in the file it starts - 0 for the opening window, non-zero for a tail. */
class DecodedAudio(val samples: FloatArray, val startMs: Long)

/**
 * Same as [decodeMonoPcm] but for the LAST [windowMs] of the file - what the credits search needs.
 * Seeks by the audio track's own duration; a container that doesn't report one can't be searched
 * from the end, so that returns null like any other undecodable track.
 */
fun decodeMonoPcmTail(dataSource: MediaDataSource, windowMs: Long, isCancelled: () -> Boolean = { false }): DecodedAudio? =
    decodeWindow(dataSource, windowMs, fromEnd = true, isCancelled)

private fun decodeWindow(dataSource: MediaDataSource, limitMs: Long, fromEnd: Boolean, isCancelled: () -> Boolean): DecodedAudio? {
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    try {
        extractor.setDataSource(dataSource)
        val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run {
            android.util.Log.w(DECODER_TAG, "no audio track among ${extractor.trackCount} tracks")
            return null
        }

        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        android.util.Log.i(DECODER_TAG, "audio track: $format")
        val sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(2)
        extractor.selectTrack(trackIndex)

        var startUs = 0L
        var endUs = limitMs * 1000
        if (fromEnd) {
            if (!format.containsKey(MediaFormat.KEY_DURATION)) {
                android.util.Log.w(DECODER_TAG, "no track duration, can't read the tail")
                return null
            }
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            // Never more than the last 30% of the file. With a fixed window, an episode shorter
            // than the window had its whole length fingerprinted as its "tail", and the credits
            // search then matched the INTRO two episodes share and marked that as the credits
            // (seen on 3-minute test episodes; an 8-minute cartoon short would do the same).
            val windowUs = minOf(limitMs * 1000, durationUs * 3 / 10)
            extractor.seekTo((durationUs - windowUs).coerceAtLeast(0), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            // Where the seek actually landed, not where it was asked to: the caller turns positions
            // inside the decoded window back into file positions with this.
            startUs = extractor.sampleTime.coerceAtLeast(0)
            endUs = Long.MAX_VALUE
        }

        codec = runCatching { MediaCodec.createDecoderByType(mime) }.getOrNull() ?: run {
            android.util.Log.w(DECODER_TAG, "no platform decoder for $mime")
            return null
        }
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
                    if (size < 0 || extractor.sampleTime > endUs) {
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
        val samples = output.toFloatArray()
        android.util.Log.i(DECODER_TAG, "decoded ${samples.size} samples (${samples.size / FINGERPRINT_SAMPLE_RATE} s), output format ${codec.outputFormat}")
        return if (samples.size >= FRAME_SIZE) DecodedAudio(samples, startUs / 1000) else null
    } catch (e: Exception) {
        android.util.Log.w(DECODER_TAG, "decode failed", e)
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
private const val DECODER_TAG = "IntroAudioDecoder"
