package com.illusion.app.data.player.mkv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs [MatroskaCuesReader] over an MKV muxed by FFmpeg (H.264 176x144 24 fps with a keyframe every
 * second, three AAC tracks, 30 chapters, a 48 KB attachment, Cues at the end, 12 s).
 *
 * The check that doesn't just restate the parser: every cue position must land exactly on a
 * Cluster element ID in the actual file bytes.
 */
class MatroskaCuesReaderTest {

    private val file: ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("mkv/fixture_cues.mkv")).use { it.readBytes() }

    private val reader = MatroskaCuesReader.RandomReader { position, length ->
        val start = position.coerceAtMost(file.size.toLong()).toInt()
        file.copyOfRange(start, minOf(file.size, start + length))
    }

    @Test
    fun readsVideoCuesPointingAtClusters() {
        val index = checkNotNull(MatroskaCuesReader.read(reader))
        val videoCues = checkNotNull(index.cuesByTrack[1])

        // One keyframe per second over 12 s.
        assertTrue("${videoCues.size} cues", videoCues.size in 11..13)
        assertEquals(0L, videoCues.first().timeUs)
        videoCues.zipWithNext().forEach { (a, b) -> assertTrue(b.timeUs > a.timeUs && b.clusterPosition > a.clusterPosition) }
        assertTrue(videoCues.last().timeUs in 10_000_000..12_100_000)

        for (cue in videoCues) {
            val at = cue.clusterPosition.toInt()
            assertArrayEquals("cue at ${cue.timeUs} -> $at", CLUSTER_ID, file.copyOfRange(at, at + 4))
        }
    }

    @Test
    fun cueTimesTrackKeyframeSpacing() {
        val videoCues = checkNotNull(checkNotNull(MatroskaCuesReader.read(reader)).cuesByTrack[1])
        // -g 24 at 24 fps: cues one second apart (millisecond TimecodeScale, so exact).
        videoCues.zipWithNext().forEach { (a, b) -> assertEquals(1_000_000L, b.timeUs - a.timeUs) }
    }

    @Test
    fun rejectsNonMatroska() {
        val notMkv = ByteArray(256) { it.toByte() }
        assertNull(MatroskaCuesReader.read { position, length ->
            val start = position.coerceAtMost(notMkv.size.toLong()).toInt()
            notMkv.copyOfRange(start, minOf(notMkv.size, start + length))
        })
    }

    private companion object {
        val CLUSTER_ID = byteArrayOf(0x1F, 0x43, 0xB6.toByte(), 0x75)
    }
}
