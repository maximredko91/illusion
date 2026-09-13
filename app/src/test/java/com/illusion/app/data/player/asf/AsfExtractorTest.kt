package com.illusion.app.data.player.asf

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs [AsfExtractor] over a real ASF file muxed by FFmpeg (WMV2 176x144 15 fps + WMA2 22.05 kHz
 * mono, 4 s, with a Simple Index). Expected counts come from ffprobe on the same file:
 * 60 video frames, 87 audio packets.
 */
class AsfExtractorTest {

    @Before
    fun enforceReadLimits() {
        // ParsableByteArray otherwise decides this by probing for Robolectric, which crashes in a
        // plain JVM test (static init NPE) - and strict limits are what a parser test wants anyway.
        ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(true)
    }

    @After
    fun resetReadLimits() {
        ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(null)
    }

    private val file: ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("asf/fixture_wmv2_wma2.wmv")).use { it.readBytes() }

    @Test
    fun sniffsAsfAndRejectsOtherData() {
        assertTrue(AsfExtractor().sniff(input(0)))
        val notAsf = ByteArray(64) { it.toByte() }
        assertEquals(false, AsfExtractor().sniff(DefaultExtractorInput(reader(notAsf), 0, notAsf.size.toLong())))
    }

    @Test
    fun extractsBothTracksWithCodecSetup() {
        val output = extractAll()
        val video = output.tracks.values.single { it.format?.sampleMimeType == WindowsMediaMimeTypes.VIDEO_WMV2 }
        val audio = output.tracks.values.single { it.format?.sampleMimeType == WindowsMediaMimeTypes.AUDIO_WMA_V2 }

        val videoFormat = checkNotNull(video.format)
        assertEquals(176, videoFormat.width)
        assertEquals(144, videoFormat.height)
        assertEquals(4, videoFormat.initializationData.single().size)

        val audioFormat = checkNotNull(audio.format)
        assertEquals(22050, audioFormat.sampleRate)
        assertEquals(1, audioFormat.channelCount)
        assertEquals(10, audioFormat.initializationData[0].size)
        assertTrue(WindowsMediaMimeTypes.decodeAudioParams(audioFormat).first > 0)
    }

    @Test
    fun reassemblesEveryMediaObject() {
        val output = extractAll()
        val video = output.tracks.values.single { it.format?.sampleMimeType == WindowsMediaMimeTypes.VIDEO_WMV2 }
        val audio = output.tracks.values.single { it.format?.sampleMimeType == WindowsMediaMimeTypes.AUDIO_WMA_V2 }

        assertEquals(60, video.samples.size)
        assertEquals(87, audio.samples.size)
        assertTrue(video.samples.first().flags and C.BUFFER_FLAG_KEY_FRAME != 0)
        // No B-frames in WMV2: presentation times strictly increase, one frame every ~66.7 ms, from 0.
        assertTrue(video.samples.first().timeUs in 0..70_000)
        video.samples.zipWithNext().forEach { (a, b) -> assertTrue("${a.timeUs} -> ${b.timeUs}", b.timeUs > a.timeUs) }
        assertTrue(video.samples.last().timeUs in 3_800_000..4_100_000)
        // Byte-exact reassembly: every sample's size matches the bytes actually written for it.
        (video.samples + audio.samples).forEach { assertEquals(it.size, it.data.size) }
    }

    @Test
    fun seekMapUsesIndexAndSeekLandsOnKeyFrame() {
        val output = extractAll()
        val seekMap = checkNotNull(output.seekMap)
        assertTrue(seekMap.isSeekable)
        assertTrue(seekMap.durationUs in 3_500_000..4_500_000)

        val target = 2_000_000L
        val seekPoint = seekMap.getSeekPoints(target).first
        assertTrue(seekPoint.position > 0 && seekPoint.position < file.size)
        assertTrue(seekPoint.timeUs <= target)

        // Continue the same extractor from the seek point, like ProgressiveMediaPeriod does.
        val extractor = output.extractor
        extractor.seek(seekPoint.position, target)
        val video = output.tracks.values.single { it.format?.sampleMimeType == WindowsMediaMimeTypes.VIDEO_WMV2 }
        val before = video.samples.size
        readUntilEnd(extractor, seekPoint.position)
        val firstAfterSeek = video.samples[before]
        assertTrue(firstAfterSeek.flags and C.BUFFER_FLAG_KEY_FRAME != 0)
        // A keyframe every 15 frames = 1 s, so the preceding one is at most a second before the target.
        assertTrue("${firstAfterSeek.timeUs}", firstAfterSeek.timeUs in (target - 1_100_000)..target)
    }

    private fun extractAll(): FakeOutput {
        val extractor = AsfExtractor()
        val output = FakeOutput(extractor)
        extractor.init(output)
        readUntilEnd(extractor, 0)
        assertNotNull(output.seekMap)
        return output
    }

    private fun readUntilEnd(extractor: Extractor, startPosition: Long) {
        var input = input(startPosition)
        val positionHolder = PositionHolder()
        while (true) {
            when (extractor.read(input, positionHolder)) {
                Extractor.RESULT_END_OF_INPUT -> return
                Extractor.RESULT_SEEK -> input = input(positionHolder.position)
            }
        }
    }

    private fun input(position: Long) = DefaultExtractorInput(reader(file, position.toInt()), position, file.size.toLong())

    private fun reader(data: ByteArray, start: Int = 0) = object : DataReader {
        private var position = start
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= data.size) return C.RESULT_END_OF_INPUT
            val count = minOf(length, data.size - position)
            System.arraycopy(data, position, buffer, offset, count)
            position += count
            return count
        }
    }

    private class Sample(val timeUs: Long, val flags: Int, val size: Int, val data: ByteArray)

    private class FakeTrack : TrackOutput {
        var format: Format? = null
        val samples = mutableListOf<Sample>()
        private val pending = java.io.ByteArrayOutputStream()

        override fun format(format: Format) {
            this.format = format
        }

        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
            val buffer = ByteArray(length)
            val read = input.read(buffer, 0, length)
            if (read > 0) pending.write(buffer, 0, read)
            return read
        }

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            val buffer = ByteArray(length)
            data.readBytes(buffer, 0, length)
            pending.write(buffer)
        }

        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
            samples += Sample(timeUs, flags, size, pending.toByteArray())
            pending.reset()
        }
    }

    private class FakeOutput(val extractor: Extractor) : ExtractorOutput {
        val tracks = LinkedHashMap<Int, FakeTrack>()
        var seekMap: SeekMap? = null

        override fun track(id: Int, type: Int): TrackOutput = tracks.getOrPut(id) { FakeTrack() }
        override fun endTracks() = Unit
        override fun seekMap(seekMap: SeekMap) {
            this.seekMap = seekMap
        }
    }
}
