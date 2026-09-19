package com.illusion.app.data.intro

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The detector's pure-Kotlin half, tested on synthesised audio: a shared "theme" pasted into two
 * otherwise different episodes has to come back as a match at the right offset, and two unrelated
 * stretches must not.
 */
class IntroMatcherTest {

    @Test
    fun `fft matches a naive dft on a known signal`() {
        val size = 64
        val real = DoubleArray(size) { sin(2.0 * PI * 5 * it / size) }
        val imaginary = DoubleArray(size)
        val expectedReal = DoubleArray(size)
        val expectedImaginary = DoubleArray(size)
        for (k in 0 until size) {
            for (n in 0 until size) {
                val angle = -2.0 * PI * k * n / size
                expectedReal[k] += real[n] * kotlin.math.cos(angle)
                expectedImaginary[k] += real[n] * sin(angle)
            }
        }

        fft(real, imaginary)

        for (k in 0 until size) {
            assertEquals(expectedReal[k], real[k], 1e-6)
            assertEquals(expectedImaginary[k], imaginary[k], 1e-6)
        }
    }

    @Test
    fun `the same theme in two different episodes is found at its real position`() {
        val theme = noise(seed = 1, durationMs = 40_000)
        val first = noise(seed = 2, durationMs = 20_000) + theme + noise(seed = 3, durationMs = 60_000)
        val second = noise(seed = 4, durationMs = 50_000) + theme + noise(seed = 5, durationMs = 30_000)

        val match = findSharedIntro(fingerprint(first), fingerprint(second))

        assertNotNull(match)
        // Within a couple of frames of the 20 s mark, and about as long as the theme itself.
        assertTrue("start was ${match!!.startMs}", match.startMs in 19_000..21_000)
        assertTrue("duration was ${match.durationMs}", match.durationMs in 36_000..44_000)
    }

    @Test
    fun `two unrelated episodes produce no match`() {
        val first = noise(seed = 10, durationMs = 120_000)
        val second = noise(seed = 11, durationMs = 120_000)

        assertNull(findSharedIntro(fingerprint(first), fingerprint(second)))
    }

    @Test
    fun `a shared stretch shorter than the minimum is not reported as an intro`() {
        val jingle = noise(seed = 20, durationMs = 4_000)
        val first = noise(seed = 21, durationMs = 30_000) + jingle + noise(seed = 22, durationMs = 30_000)
        val second = noise(seed = 23, durationMs = 10_000) + jingle + noise(seed = 24, durationMs = 50_000)

        assertNull(findSharedIntro(fingerprint(first), fingerprint(second)))
    }

    @Test
    fun `the neighbouring episode is the one compared against`() {
        val episodes = listOf(episode("s1e1", 1), episode("s1e2", 2), episode("s1e3", 3))

        assertEquals("s1e3", pickReferenceEpisode(episodes[1], episodes)?.stableId)
        // Last episode of the season has nothing after it - falls back to an earlier one.
        assertEquals("s1e2", pickReferenceEpisode(episodes[2], episodes)?.stableId)
        assertNull(pickReferenceEpisode(episodes[0], listOf(episodes[0])))
    }

    /** Band-limited noise: random enough that two seeds never match, smooth enough to survive the
     * fingerprint's 8 kHz band layout the way real audio does. */
    private fun noise(seed: Int, durationMs: Int): FloatArray {
        val random = Random(seed)
        val samples = FloatArray(durationMs * FINGERPRINT_SAMPLE_RATE / 1000)
        var previous = 0f
        for (i in samples.indices) {
            val white = random.nextFloat() * 2f - 1f
            previous = previous * 0.6f + white * 0.4f
            samples[i] = previous
        }
        return samples
    }

    private fun episode(stableId: String, number: Int) = com.illusion.app.data.local.entity.MediaItemEntity(
        stableId = stableId,
        sourceId = 1L,
        filePath = "show/s01/$stableId.mkv",
        category = com.illusion.app.domain.model.Category.TV_SHOWS,
        title = stableId,
        originalTitle = null,
        year = null,
        genres = emptyList(),
        rating = null,
        country = null,
        runtimeMinutes = null,
        plot = null,
        director = emptyList(),
        actors = emptyList(),
        collectionName = null,
        posterPath = null,
        fanartPath = null,
        seasonNumber = 1,
        episodeNumber = number,
        seriesStableId = "show",
        dateAdded = 0L,
        sizeBytes = 1_000_000L,
        subtitlePaths = emptyList()
    )
}
