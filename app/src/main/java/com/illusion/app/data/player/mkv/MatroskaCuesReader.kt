package com.illusion.app.data.player.mkv

/**
 * Reads a Matroska file's seek index - SeekHead, TimecodeScale and Cues - through plain random
 * access reads, without Media3's MatroskaExtractor or its loader.
 *
 * Exists for [OutOfBandCuesExtractor]: on a handful of very large files (a 38 GB 4K remux with 14
 * tracks and 76 chapters) Media3's own "seek to the Cues, then back" hangs the player forever, while
 * the same extractor with Cues seeking disabled plays fine but can't seek at all. Reading the Cues
 * separately gives those files their seeking back.
 *
 * Element IDs and semantics are Media3's (MatroskaExtractor, 1.11.0): cue cluster positions are
 * relative to the start of the Segment's content, cue times are in TimecodeScale units. Kept free of
 * Android and Media3 types so it can be unit-tested on the JVM.
 */
object MatroskaCuesReader {

    /** Returns up to [length] bytes starting at [position]; fewer only at end of file. */
    fun interface RandomReader {
        fun read(position: Long, length: Int): ByteArray
    }

    /** One cue: a time and the absolute file position of the Cluster that holds it. */
    data class CuePoint(val timeUs: Long, val clusterPosition: Long)

    /** Cue points per Matroska track number, each list sorted by time. */
    class Index(val cuesByTrack: Map<Int, List<CuePoint>>)

    /** Null when the file isn't Matroska, lists no Cues in its SeekHead, or anything is malformed. */
    fun read(reader: RandomReader): Index? {
        val ebmlHeader = reader.read(0, ELEMENT_HEADER_MAX_SIZE)
        val ebml = parseElementHeader(ebmlHeader, 0) ?: return null
        if (ebml.id != ID_EBML || ebml.size < 0) return null

        val segmentPosition = ebml.headerSize + ebml.size
        val segment = parseElementHeader(reader.read(segmentPosition, ELEMENT_HEADER_MAX_SIZE), 0) ?: return null
        if (segment.id != ID_SEGMENT) return null
        val segmentContentPosition = segmentPosition + segment.headerSize

        val layout = scanSegmentStart(reader, segmentContentPosition)
        var cuesRelativePosition = layout.seekEntries[ID_CUES]
        // A SeekHead may only point at a second SeekHead (typically at the end of the file) listing the rest.
        if (cuesRelativePosition == null) {
            layout.seekEntries[ID_SEEK_HEAD]?.let { secondSeekHead ->
                readElement(reader, segmentContentPosition + secondSeekHead, ID_SEEK_HEAD, MAX_SEEK_HEAD_SIZE)
                    ?.let { cuesRelativePosition = parseSeekHead(it)[ID_CUES] }
            }
        }
        val cuesPosition = cuesRelativePosition ?: return null

        var timecodeScaleNs = layout.timecodeScaleNs
        if (timecodeScaleNs == null) {
            timecodeScaleNs = layout.seekEntries[ID_INFO]
                ?.let { readElement(reader, segmentContentPosition + it, ID_INFO, MAX_INFO_SIZE) }
                ?.let { parseTimecodeScale(it) }
        }
        val cues = readElement(reader, segmentContentPosition + cuesPosition, ID_CUES, MAX_CUES_SIZE) ?: return null
        return Index(parseCues(cues, timecodeScaleNs ?: DEFAULT_TIMECODE_SCALE_NS, segmentContentPosition))
    }

    private class SegmentStart(val seekEntries: Map<Int, Long>, val timecodeScaleNs: Long?)

    /**
     * Walks the Segment's top-level children from its start until the first Cluster, reading only
     * SeekHead and Info bodies - everything else (Tracks, Chapters, megabytes of font attachments) is
     * jumped over by its size, never downloaded.
     */
    private fun scanSegmentStart(reader: RandomReader, segmentContentPosition: Long): SegmentStart {
        val seekEntries = HashMap<Int, Long>()
        var timecodeScaleNs: Long? = null
        var position = segmentContentPosition
        repeat(MAX_TOP_LEVEL_ELEMENTS) {
            val header = parseElementHeader(reader.read(position, ELEMENT_HEADER_MAX_SIZE), 0) ?: return@repeat
            if (header.id == ID_CLUSTER || header.size < 0) return SegmentStart(seekEntries, timecodeScaleNs)
            val contentPosition = position + header.headerSize
            when (header.id) {
                ID_SEEK_HEAD -> if (header.size <= MAX_SEEK_HEAD_SIZE && seekEntries.isEmpty()) {
                    seekEntries += parseSeekHead(reader.read(contentPosition, header.size.toInt()))
                }
                ID_INFO -> if (header.size <= MAX_INFO_SIZE) {
                    timecodeScaleNs = parseTimecodeScale(reader.read(contentPosition, header.size.toInt()))
                }
            }
            if (seekEntries.isNotEmpty() && timecodeScaleNs != null) return SegmentStart(seekEntries, timecodeScaleNs)
            position = contentPosition + header.size
        }
        return SegmentStart(seekEntries, timecodeScaleNs)
    }

    /** Reads a whole element's content at [position], or null if it isn't [expectedId] or too large. */
    private fun readElement(reader: RandomReader, position: Long, expectedId: Int, maxSize: Long): ByteArray? {
        val header = parseElementHeader(reader.read(position, ELEMENT_HEADER_MAX_SIZE), 0) ?: return null
        if (header.id != expectedId || header.size < 0 || header.size > maxSize) return null
        val content = reader.read(position + header.headerSize, header.size.toInt())
        return if (content.size.toLong() == header.size) content else null
    }

    private fun parseSeekHead(content: ByteArray): Map<Int, Long> {
        val entries = HashMap<Int, Long>()
        forEachChild(content, 0, content.size) { id, start, end ->
            if (id != ID_SEEK) return@forEachChild
            var seekId = -1
            var seekPosition = -1L
            forEachChild(content, start, end) { childId, childStart, childEnd ->
                when (childId) {
                    ID_SEEK_ID -> seekId = readUnsigned(content, childStart, childEnd).toInt()
                    ID_SEEK_POSITION -> seekPosition = readUnsigned(content, childStart, childEnd)
                }
            }
            if (seekId != -1 && seekPosition >= 0 && seekId !in entries) entries[seekId] = seekPosition
        }
        return entries
    }

    private fun parseTimecodeScale(content: ByteArray): Long? {
        var scale: Long? = null
        forEachChild(content, 0, content.size) { id, start, end ->
            if (id == ID_TIMECODE_SCALE) scale = readUnsigned(content, start, end).takeIf { it > 0 }
        }
        return scale
    }

    private fun parseCues(content: ByteArray, timecodeScaleNs: Long, segmentContentPosition: Long): Map<Int, List<CuePoint>> {
        val cuesByTrack = HashMap<Int, MutableList<CuePoint>>()
        forEachChild(content, 0, content.size) { id, start, end ->
            if (id != ID_CUE_POINT) return@forEachChild
            var cueTime = -1L
            val positions = ArrayList<Pair<Int, Long>>(1)
            forEachChild(content, start, end) { childId, childStart, childEnd ->
                when (childId) {
                    ID_CUE_TIME -> cueTime = readUnsigned(content, childStart, childEnd)
                    ID_CUE_TRACK_POSITIONS -> {
                        var track = -1
                        var clusterPosition = -1L
                        forEachChild(content, childStart, childEnd) { positionId, positionStart, positionEnd ->
                            when (positionId) {
                                ID_CUE_TRACK -> track = readUnsigned(content, positionStart, positionEnd).toInt()
                                // Media3 keeps the first CueClusterPosition if an entry repeats it.
                                ID_CUE_CLUSTER_POSITION -> if (clusterPosition < 0) {
                                    clusterPosition = readUnsigned(content, positionStart, positionEnd)
                                }
                            }
                        }
                        if (track >= 0 && clusterPosition >= 0) positions += track to clusterPosition
                    }
                }
            }
            if (cueTime < 0) return@forEachChild
            val timeUs = cueTime * timecodeScaleNs / 1000
            for ((track, clusterPosition) in positions) {
                cuesByTrack.getOrPut(track) { ArrayList() } += CuePoint(timeUs, segmentContentPosition + clusterPosition)
            }
        }
        return cuesByTrack.mapValues { (_, cues) -> cues.sortedBy { it.timeUs } }
    }

    private class ElementHeader(val id: Int, val size: Long, val headerSize: Int)

    /** EBML element ID (marker bits kept, as Matroska IDs are written) and size (-1 when unknown). */
    private fun parseElementHeader(data: ByteArray, offset: Int): ElementHeader? {
        if (offset >= data.size) return null
        val idLength = varintLength(data[offset].toInt() and 0xFF, maxLength = 4) ?: return null
        if (offset + idLength >= data.size) return null
        var id = 0
        for (i in 0 until idLength) id = (id shl 8) or (data[offset + i].toInt() and 0xFF)
        val sizeOffset = offset + idLength
        val firstSizeByte = data[sizeOffset].toInt() and 0xFF
        val sizeLength = varintLength(firstSizeByte, maxLength = 8) ?: return null
        if (sizeOffset + sizeLength > data.size) return null
        var size = (firstSizeByte and (0xFF shr sizeLength)).toLong()
        var allOnes = size == (0xFF shr sizeLength).toLong()
        for (i in 1 until sizeLength) {
            val b = data[sizeOffset + i].toInt() and 0xFF
            allOnes = allOnes && b == 0xFF
            size = (size shl 8) or b.toLong()
        }
        return ElementHeader(id, if (allOnes) -1 else size, idLength + sizeLength)
    }

    private fun varintLength(firstByte: Int, maxLength: Int): Int? {
        for (length in 1..maxLength) {
            if (firstByte and (0x80 shr (length - 1)) != 0) return length
        }
        return null
    }

    private inline fun forEachChild(data: ByteArray, start: Int, end: Int, block: (id: Int, contentStart: Int, contentEnd: Int) -> Unit) {
        var position = start
        while (position < end) {
            val header = parseElementHeader(data, position) ?: return
            val contentStart = position + header.headerSize
            if (header.size < 0 || contentStart + header.size > end) return
            val contentEnd = (contentStart + header.size).toInt()
            block(header.id, contentStart, contentEnd)
            position = contentEnd
        }
    }

    private fun readUnsigned(data: ByteArray, start: Int, end: Int): Long {
        var value = 0L
        for (i in start until minOf(end, start + 8)) value = (value shl 8) or (data[i].toLong() and 0xFF)
        return value
    }

    private const val ID_EBML = 0x1A45DFA3
    private const val ID_SEGMENT = 0x18538067
    private const val ID_SEEK_HEAD = 0x114D9B74
    private const val ID_SEEK = 0x4DBB
    private const val ID_SEEK_ID = 0x53AB
    private const val ID_SEEK_POSITION = 0x53AC
    private const val ID_INFO = 0x1549A966
    private const val ID_TIMECODE_SCALE = 0x2AD7B1
    private const val ID_CLUSTER = 0x1F43B675
    private const val ID_CUES = 0x1C53BB6B
    private const val ID_CUE_POINT = 0xBB
    private const val ID_CUE_TIME = 0xB3
    private const val ID_CUE_TRACK_POSITIONS = 0xB7
    private const val ID_CUE_TRACK = 0xF7
    private const val ID_CUE_CLUSTER_POSITION = 0xF1

    private const val DEFAULT_TIMECODE_SCALE_NS = 1_000_000L
    private const val ELEMENT_HEADER_MAX_SIZE = 12 // 4-byte ID + 8-byte size
    private const val MAX_TOP_LEVEL_ELEMENTS = 64
    private const val MAX_SEEK_HEAD_SIZE = 1L shl 20
    private const val MAX_INFO_SIZE = 1L shl 20
    // Even a 3-hour remux with a cue on every track stays in the low megabytes; this only guards memory.
    private const val MAX_CUES_SIZE = 64L shl 20
}
