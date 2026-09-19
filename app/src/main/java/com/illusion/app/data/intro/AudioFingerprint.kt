package com.illusion.app.data.intro

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.PI

/**
 * Acoustic fingerprint of one audio stretch: one 32-bit hash per [FRAME_HOP] of audio, in order.
 *
 * The scheme is Haitsma-Kalker's (what Chromaprint and every "find the same audio in two files"
 * tool is a variation of): per frame, the energy in a handful of log-spaced bands; per bit, the
 * sign of how the difference between two neighbouring bands CHANGED since the previous frame. That
 * second derivative is what makes the hash survive a different encode of the same audio - volume,
 * codec and bitrate move band energies up and down together, while the shape of the change stays.
 */
@JvmInline
value class Fingerprint(val hashes: IntArray) {
    val size: Int get() = hashes.size
}

/** Audio is resampled to this before analysis - an intro theme's shape lives well below 4 kHz, and
 * a low rate keeps both the FFT and the SMB read cheap. */
const val FINGERPRINT_SAMPLE_RATE = 8000

/** 2048 samples @ 8 kHz = 256 ms per frame, 512 = 64 ms of hop, i.e. ~15.6 hashes per second. */
const val FRAME_SIZE = 2048
const val FRAME_HOP = 512

/** Milliseconds one hash step covers - used to translate frame indices back into playback time. */
const val FRAME_HOP_MS = FRAME_HOP * 1000L / FINGERPRINT_SAMPLE_RATE

/** 33 band edges -> 32 bands -> 32 bits, log-spaced over the range where speech/music detail sits. */
private const val BAND_COUNT = 32
private const val MIN_FREQUENCY = 300.0
private const val MAX_FREQUENCY = 3400.0

/**
 * Builds the fingerprint of [samples] (mono PCM, [FINGERPRINT_SAMPLE_RATE]).
 *
 * Returns an empty fingerprint for anything shorter than a single frame rather than throwing -
 * a file whose audio track fails to decode past a second or two is a normal outcome here, not an
 * error worth propagating.
 */
fun fingerprint(samples: FloatArray): Fingerprint {
    if (samples.size < FRAME_SIZE) return Fingerprint(IntArray(0))
    val bandEdges = logBandEdges()
    val window = hannWindow(FRAME_SIZE)
    val real = DoubleArray(FRAME_SIZE)
    val imaginary = DoubleArray(FRAME_SIZE)

    val frameCount = (samples.size - FRAME_SIZE) / FRAME_HOP + 1
    val hashes = IntArray((frameCount - 1).coerceAtLeast(0))
    var previousBands: DoubleArray? = null
    var writeIndex = 0

    for (frame in 0 until frameCount) {
        val offset = frame * FRAME_HOP
        for (i in 0 until FRAME_SIZE) {
            real[i] = samples[offset + i].toDouble() * window[i]
            imaginary[i] = 0.0
        }
        fft(real, imaginary)

        val bands = DoubleArray(BAND_COUNT + 1)
        for (band in 0..BAND_COUNT) {
            var energy = 0.0
            for (bin in bandEdges[band] until bandEdges[band + 1]) {
                energy += hypot(real[bin], imaginary[bin])
            }
            // Log domain: a quieter encode of the same audio scales energies, which then cancels
            // out in the differences below instead of flipping their signs.
            bands[band] = ln(energy + 1e-9)
        }

        val previous = previousBands
        if (previous != null) {
            var hash = 0
            for (bit in 0 until BAND_COUNT) {
                val now = bands[bit] - bands[bit + 1]
                val before = previous[bit] - previous[bit + 1]
                if (now - before > 0) hash = hash or (1 shl bit)
            }
            hashes[writeIndex++] = hash
        }
        previousBands = bands
    }
    return Fingerprint(hashes)
}

/** BAND_COUNT + 2 FFT-bin boundaries, log-spaced between [MIN_FREQUENCY] and [MAX_FREQUENCY]. */
private fun logBandEdges(): IntArray {
    val edges = IntArray(BAND_COUNT + 2)
    val binsPerHz = FRAME_SIZE.toDouble() / FINGERPRINT_SAMPLE_RATE
    val ratio = MAX_FREQUENCY / MIN_FREQUENCY
    for (i in edges.indices) {
        val frequency = MIN_FREQUENCY * Math.pow(ratio, i.toDouble() / (BAND_COUNT + 1))
        edges[i] = (frequency * binsPerHz).toInt().coerceIn(1, FRAME_SIZE / 2 - 1)
    }
    // Bands must be non-empty, or two adjacent bands collapse into the same energy and their bit
    // is stuck at 0 for the whole file - at 8 kHz the lowest bands are only a few bins wide.
    for (i in 1 until edges.size) {
        if (edges[i] <= edges[i - 1]) edges[i] = edges[i - 1] + 1
    }
    return edges
}

private fun hannWindow(size: Int) = DoubleArray(size) { 0.5 - 0.5 * cos(2.0 * PI * it / (size - 1)) }

/**
 * In-place iterative radix-2 FFT. [real]/[imaginary] must be the same power-of-two length.
 *
 * Hand-rolled rather than pulled in: this is the one transform the app needs, on a fixed size,
 * and every Android-friendly FFT library would be a dependency carried for ~40 lines of code.
 */
internal fun fft(real: DoubleArray, imaginary: DoubleArray) {
    val n = real.size
    require(n and (n - 1) == 0) { "FFT size must be a power of two" }

    // Bit-reversal permutation.
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) {
            j = j xor bit
            bit = bit shr 1
        }
        j = j or bit
        if (i < j) {
            real[i] = real[j].also { real[j] = real[i] }
            imaginary[i] = imaginary[j].also { imaginary[j] = imaginary[i] }
        }
    }

    var length = 2
    while (length <= n) {
        val angle = -2.0 * PI / length
        val stepReal = cos(angle)
        val stepImaginary = kotlin.math.sin(angle)
        var i = 0
        while (i < n) {
            var twiddleReal = 1.0
            var twiddleImaginary = 0.0
            for (k in 0 until length / 2) {
                val evenReal = real[i + k]
                val evenImaginary = imaginary[i + k]
                val oddReal = real[i + k + length / 2] * twiddleReal - imaginary[i + k + length / 2] * twiddleImaginary
                val oddImaginary = real[i + k + length / 2] * twiddleImaginary + imaginary[i + k + length / 2] * twiddleReal
                real[i + k] = evenReal + oddReal
                imaginary[i + k] = evenImaginary + oddImaginary
                real[i + k + length / 2] = evenReal - oddReal
                imaginary[i + k + length / 2] = evenImaginary - oddImaginary
                val nextTwiddleReal = twiddleReal * stepReal - twiddleImaginary * stepImaginary
                twiddleImaginary = twiddleReal * stepImaginary + twiddleImaginary * stepReal
                twiddleReal = nextTwiddleReal
            }
            i += length
        }
        length = length shl 1
    }
}
