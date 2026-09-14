package com.illusion.app.data.player.mkv

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.extractor.ChunkIndex
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import java.io.IOException

/**
 * Wraps a MatroskaExtractor created with FLAG_DISABLE_SEEK_FOR_CUES and gives it its seeking back.
 *
 * For a few very large files, Media3's normal "jump to the Cues at the end of the file, then back"
 * leaves the player buffering forever (the loader opens the file at the Cues offset and never reads
 * - see the note in CLAUDE.md). With that jump disabled the same extractor plays those files fine,
 * but reports the whole file as unseekable. This wrapper intercepts that unseekable seek map and
 * replaces it with one built from the Cues read through a separate [DataSource] (via
 * [MatroskaCuesReader]) - no jump inside Media3's loader at all.
 *
 * The seek map is the same [ChunkIndex] Media3's own MatroskaSeekMap builds (primary track's cues,
 * absolute Cluster positions), so seeking goes through MatroskaExtractor.seek() exactly as it does
 * for every ordinary .mkv. If the Cues can't be read, the file stays unseekable, as before.
 */
@OptIn(UnstableApi::class)
class OutOfBandCuesExtractor(
    private val delegate: Extractor,
    private val dataSourceFactory: DataSource.Factory,
    private val uri: Uri
) : Extractor {

    private lateinit var output: ExtractorOutput
    private var firstVideoTrack = C.INDEX_UNSET
    private var firstAudioTrack = C.INDEX_UNSET
    private var pendingUnseekableDurationUs: Long? = null

    override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(input)

    override fun init(output: ExtractorOutput) {
        this.output = output
        delegate.init(InterceptingOutput(output))
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val result = delegate.read(input, seekPosition)
        pendingUnseekableDurationUs?.let { durationUs ->
            pendingUnseekableDurationUs = null
            // Blocking network reads on the loader thread - which is what it's for.
            output.seekMap(buildSeekMap(durationUs))
        }
        return result
    }

    override fun seek(position: Long, timeUs: Long) = delegate.seek(position, timeUs)

    override fun release() = delegate.release()

    override fun getUnderlyingImplementation(): Extractor = delegate.underlyingImplementation

    private fun buildSeekMap(durationUs: Long): SeekMap {
        val index = try {
            MatroskaCuesReader.read(DataSourceRandomReader(dataSourceFactory, uri))
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't read Cues out of band, file stays unseekable", e)
            null
        }
        // Primary track the way MatroskaExtractor picks it (without the default-flag refinement):
        // first video track, else first audio track.
        val cues = index?.cuesByTrack?.let { byTrack ->
            byTrack[firstVideoTrack] ?: byTrack[firstAudioTrack]
        }?.filter { durationUs == C.TIME_UNSET || it.timeUs < durationUs }
        if (cues.isNullOrEmpty() || durationUs == C.TIME_UNSET) {
            Log.w(TAG, "No usable Cues (index=${index != null}), file stays unseekable")
            return SeekMap.Unseekable(durationUs)
        }
        val count = cues.size
        val offsets = LongArray(count) { cues[it].clusterPosition }
        val timesUs = LongArray(count) { cues[it].timeUs }
        val durationsUs = LongArray(count) { i -> (if (i + 1 < count) timesUs[i + 1] else durationUs) - timesUs[i] }
        // Chunk sizes only matter to ChunkIndex consumers other than seeking; the last one is unknown here.
        val sizes = IntArray(count) { i -> if (i + 1 < count) (offsets[i + 1] - offsets[i]).coerceIn(0, Int.MAX_VALUE.toLong()).toInt() else 0 }
        Log.i(TAG, "Seeking restored from $count out-of-band cue points")
        return ChunkIndex(sizes, offsets, durationsUs, timesUs)
    }

    private inner class InterceptingOutput(private val real: ExtractorOutput) : ExtractorOutput {
        override fun track(id: Int, type: Int): TrackOutput {
            if (type == C.TRACK_TYPE_VIDEO && firstVideoTrack == C.INDEX_UNSET) firstVideoTrack = id
            if (type == C.TRACK_TYPE_AUDIO && firstAudioTrack == C.INDEX_UNSET) firstAudioTrack = id
            return real.track(id, type)
        }

        override fun endTracks() = real.endTracks()

        override fun seekMap(seekMap: SeekMap) {
            if (seekMap.isSeekable) {
                real.seekMap(seekMap)
            } else {
                // Emitted from inside delegate.read(); replaced once that call returns (see read()).
                pendingUnseekableDurationUs = seekMap.durationUs
            }
        }
    }

    private class DataSourceRandomReader(
        private val factory: DataSource.Factory,
        private val uri: Uri
    ) : MatroskaCuesReader.RandomReader {
        override fun read(position: Long, length: Int): ByteArray {
            val dataSource = factory.createDataSource()
            try {
                dataSource.open(DataSpec(uri, position, length.toLong()))
                val buffer = ByteArray(length)
                var filled = 0
                while (filled < length) {
                    val read = dataSource.read(buffer, filled, length - filled)
                    if (read == C.RESULT_END_OF_INPUT) break
                    filled += read
                }
                return if (filled == length) buffer else buffer.copyOf(filled)
            } finally {
                DataSourceUtil.closeQuietly(dataSource)
            }
        }
    }

    private companion object {
        const val TAG = "OutOfBandCues"
    }
}
