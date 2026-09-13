package com.illusion.app.data.player.asf

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.Log
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import java.io.EOFException
import kotlin.math.abs

/**
 * Media3 [Extractor] for ASF (.wmv/.asf) - Media3 1.11.0 ships none (checked against the
 * media3-extractor class list), so these files used to fail before a single byte was decoded.
 *
 * Follows the ASF specification and FFmpeg's own demuxer (libavformat/asfdec_f.c, n8.1.2), which
 * was the reference for every layout detail here: the header objects are read into memory, then
 * the fixed-size data packets are parsed one at a time and media objects reassembled from payload
 * fragments. Seeking uses the Simple Index Object after the data (keyframe packet numbers at a fixed
 * time interval), falling back to a linear estimate over the packet count when a file has none.
 *
 * Deliberately left out, as in files from this library they don't occur: DRM (streams flagged
 * encrypted are skipped), DVR-MS audio descrambling, and FFmpeg's resync search for packets with a
 * broken error-correction header.
 *
 * Decoding is the app's own FFmpeg build (WMV1-3/VC-1 via FfmpegVideoRenderer, WMA via
 * FfmpegAudioRenderer); MP3 audio and MPEG-4 video tracks in ASF go to the platform decoders.
 */
@OptIn(UnstableApi::class)
class AsfExtractor : Extractor {

    private var output: ExtractorOutput = ExtractorOutput.PLACEHOLDER
    private var state = STATE_READING_HEADER
    private var headerParsed = false
    private var seekMapOutput = false

    // File Properties Object
    private var packetSize = 0
    private var packetCount = 0L
    private var prerollMs = 0L
    private var durationUs = C.TIME_UNSET
    private var broadcast = false

    private var dataStart = 0L
    private var dataEnd = C.LENGTH_UNSET.toLong()

    private val streamInfos = LinkedHashMap<Int, StreamInfo>()
    private val streams = arrayOfNulls<Stream>(128)

    private var indexIntervalUs = 0L
    private var indexPacketNumbers: IntArray? = null

    private var packet = ParsableByteArray()

    override fun sniff(input: ExtractorInput): Boolean {
        val guid = ByteArray(GUID_SIZE)
        return input.peekFully(guid, 0, GUID_SIZE, true) && guid.contentEquals(GUID_HEADER)
    }

    override fun init(output: ExtractorOutput) {
        this.output = output
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = when (state) {
        STATE_READING_HEADER -> readHeader(input, seekPosition)
        STATE_READING_INDEX -> readIndex(input, seekPosition)
        else -> readPacket(input, seekPosition)
    }

    override fun seek(position: Long, timeUs: Long) {
        for (stream in streams) {
            stream ?: continue
            stream.discardObject()
            // A seek lands on the packet holding the preceding keyframe's start, but other video
            // objects in that packet (or a fragment tail) can come first.
            stream.waitingForKeyFrame = stream.isVideo
        }
        state = when {
            !headerParsed -> STATE_READING_HEADER
            !seekMapOutput -> STATE_READING_INDEX
            else -> STATE_READING_PACKETS
        }
    }

    override fun release() = Unit

    private fun readHeader(input: ExtractorInput, seekPosition: PositionHolder): Int {
        if (input.position != 0L) {
            seekPosition.position = 0
            return Extractor.RESULT_SEEK
        }
        val prefix = ParsableByteArray(HEADER_OBJECT_PREFIX_SIZE)
        input.readFully(prefix.data, 0, HEADER_OBJECT_PREFIX_SIZE)
        if (!readGuid(prefix).contentEquals(GUID_HEADER)) throw malformed("Missing ASF Header Object")
        val headerSize = prefix.readLittleEndianLong()
        if (headerSize < HEADER_OBJECT_PREFIX_SIZE || headerSize > MAX_HEADER_SIZE) {
            throw malformed("Invalid ASF header size $headerSize")
        }
        val body = ParsableByteArray((headerSize - HEADER_OBJECT_PREFIX_SIZE).toInt())
        input.readFully(body.data, 0, body.limit())
        parseHeaderObjects(body, body.limit())
        if (packetSize <= 0) throw malformed("ASF file has no usable File Properties Object")

        val dataObjectPosition = input.position
        val dataHeader = ParsableByteArray(DATA_OBJECT_HEADER_SIZE)
        input.readFully(dataHeader.data, 0, DATA_OBJECT_HEADER_SIZE)
        if (!readGuid(dataHeader).contentEquals(GUID_DATA)) throw malformed("Missing ASF Data Object")
        val dataObjectSize = dataHeader.readLittleEndianLong()
        dataStart = input.position
        dataEnd = if (!broadcast && dataObjectSize >= DATA_OBJECT_HEADER_SIZE) {
            dataObjectPosition + dataObjectSize
        } else {
            C.LENGTH_UNSET.toLong()
        }

        outputTracks()
        packet = ParsableByteArray(packetSize)
        headerParsed = true
        state = STATE_READING_INDEX
        return readIndex(input, seekPosition)
    }

    private fun readIndex(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val length = input.length
        val indexMayExist = dataEnd != C.LENGTH_UNSET.toLong() &&
            (length == C.LENGTH_UNSET.toLong() || dataEnd + OBJECT_HEADER_SIZE <= length)
        if (indexMayExist && indexPacketNumbers == null) {
            if (input.position != dataEnd) {
                seekPosition.position = dataEnd
                return Extractor.RESULT_SEEK
            }
            readTrailingObjects(input)
        }
        output.seekMap(AsfSeekMap())
        seekMapOutput = true
        state = STATE_READING_PACKETS
        seekPosition.position = dataStart
        return Extractor.RESULT_SEEK
    }

    /** Walks the top-level objects after the Data Object looking for a Simple Index Object. */
    private fun readTrailingObjects(input: ExtractorInput) {
        val objectHeader = ByteArray(OBJECT_HEADER_SIZE)
        try {
            while (input.readFully(objectHeader, 0, OBJECT_HEADER_SIZE, true)) {
                val header = ParsableByteArray(objectHeader)
                val guid = readGuid(header)
                val size = header.readLittleEndianLong()
                if (size < OBJECT_HEADER_SIZE) return
                val bodySize = size - OBJECT_HEADER_SIZE
                if (guid.contentEquals(GUID_SIMPLE_INDEX) && bodySize in SIMPLE_INDEX_FIXED_SIZE..MAX_INDEX_SIZE) {
                    val body = ParsableByteArray(bodySize.toInt())
                    if (!input.readFully(body.data, 0, body.limit(), true)) return
                    parseSimpleIndex(body)
                    return
                }
                if (bodySize > Int.MAX_VALUE || !input.skipFully(bodySize.toInt(), true)) return
            }
        } catch (e: EOFException) {
            // A truncated index just means seeking falls back to the packet-count estimate.
        }
    }

    private fun parseSimpleIndex(body: ParsableByteArray) {
        body.skipBytes(GUID_SIZE) // file ID
        val interval100ns = body.readLittleEndianLong()
        body.skipBytes(4) // maximum packet count
        val entryCount = body.readLittleEndianUnsignedInt()
        if (interval100ns <= 0 || entryCount <= 0 || entryCount > body.bytesLeft() / SIMPLE_INDEX_ENTRY_SIZE) return
        indexIntervalUs = interval100ns / 10
        indexPacketNumbers = IntArray(entryCount.toInt()) {
            val packetNumber = body.readLittleEndianUnsignedInt()
            body.skipBytes(2) // packet count
            packetNumber.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }

    private fun readPacket(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val position = input.position
        if (position < dataStart) {
            seekPosition.position = dataStart
            return Extractor.RESULT_SEEK
        }
        // Loader retries resume at the last read position, which needn't be a packet boundary.
        val offsetInPacket = (position - dataStart) % packetSize
        if (offsetInPacket != 0L) {
            seekPosition.position = position + packetSize - offsetInPacket
            return Extractor.RESULT_SEEK
        }
        if (dataEnd != C.LENGTH_UNSET.toLong() && position + packetSize > dataEnd) return Extractor.RESULT_END_OF_INPUT
        if (!input.readFully(packet.data, 0, packetSize, true)) return Extractor.RESULT_END_OF_INPUT
        packet.setPosition(0)
        try {
            parsePacket()
        } catch (e: RuntimeException) {
            // Damaged packets (field lengths running past the packet) surface as out-of-bounds reads.
            // Losing the objects touching this packet beats failing the whole file.
            Log.w(TAG, "Skipping malformed ASF packet at $position", e)
            for (stream in streams) stream?.discardObject()
        }
        return Extractor.RESULT_CONTINUE
    }

    private fun parsePacket() {
        var lengthTypeFlags = packet.readUnsignedByte()
        if (lengthTypeFlags and 0x80 != 0) {
            // Error Correction Data: the low nibble is its length, the Payload Parsing Information follows.
            packet.skipBytes(lengthTypeFlags and 0x0F)
            lengthTypeFlags = packet.readUnsignedByte()
        }
        val propertyFlags = packet.readUnsignedByte()
        val packetLength = readVariable(lengthTypeFlags shr 5, packetSize)
        readVariable(lengthTypeFlags shr 1, 0) // sequence, unused
        val paddingLength = readVariable(lengthTypeFlags shr 3, 0)
        packet.skipBytes(4 + 2) // send time, duration
        val payloadsEnd = packetLength.coerceAtMost(packetSize) - paddingLength

        val multiplePayloads = lengthTypeFlags and 0x01 != 0
        var payloadCount = 1
        var payloadLengthType = 0
        if (multiplePayloads) {
            val payloadFlags = packet.readUnsignedByte()
            payloadCount = payloadFlags and 0x3F
            payloadLengthType = payloadFlags shr 6
        }

        repeat(payloadCount) {
            if (packet.position >= payloadsEnd) return
            val streamByte = packet.readUnsignedByte()
            val stream = streams[streamByte and 0x7F]
            val keyFrame = streamByte and 0x80 != 0
            val objectNumber = readVariable(propertyFlags shr 4, 0)
            val offsetOrTime = readVariable(propertyFlags shr 2, 0)
            val replicatedLength = readVariable(propertyFlags, 0)
            var objectSize = 0L
            var objectTimeMs = 0L
            var timeDelta = 0
            when {
                replicatedLength >= 8 -> {
                    objectSize = packet.readLittleEndianUnsignedInt()
                    objectTimeMs = packet.readLittleEndianUnsignedInt()
                    // The rest is payload extension data (sample duration, aspect ratio, ...), unused.
                    packet.skipBytes(replicatedLength - 8)
                }
                // Compressed payload: offsetOrTime is the presentation time, a time delta follows.
                replicatedLength == 1 -> timeDelta = packet.readUnsignedByte()
                replicatedLength != 0 -> return
            }
            val payloadLength = if (multiplePayloads) readVariable(payloadLengthType, 0) else payloadsEnd - packet.position
            // FFmpeg tolerates a payload running into the padding (seen in real files), never past the packet.
            if (payloadLength < 0 || payloadLength > packetSize - packet.position) return
            val payloadEnd = packet.position + payloadLength
            if (stream != null) {
                if (replicatedLength == 1) {
                    var timeMs = offsetOrTime.toLong()
                    while (packet.position < payloadEnd) {
                        val subPayloadSize = packet.readUnsignedByte()
                        if (subPayloadSize > payloadEnd - packet.position) break
                        stream.outputWholeObject(packet, subPayloadSize, toTimeUs(timeMs), keyFrame)
                        timeMs += timeDelta
                    }
                } else {
                    stream.appendFragment(
                        packet, payloadLength, objectNumber, offsetOrTime, objectSize, toTimeUs(objectTimeMs), keyFrame
                    )
                }
            }
            packet.setPosition(payloadEnd)
        }
    }

    private fun readVariable(lengthType: Int, defaultValue: Int): Int = when (lengthType and 0x03) {
        1 -> packet.readUnsignedByte()
        2 -> packet.readLittleEndianUnsignedShort()
        3 -> packet.readLittleEndianUnsignedInt().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        else -> defaultValue
    }

    private fun toTimeUs(timeMs: Long): Long = (timeMs - prerollMs) * 1000

    private fun parseHeaderObjects(buffer: ParsableByteArray, end: Int) {
        while (buffer.position + OBJECT_HEADER_SIZE <= end) {
            val start = buffer.position
            val guid = readGuid(buffer)
            val size = buffer.readLittleEndianLong()
            if (size < OBJECT_HEADER_SIZE || start + size > end) return
            val objectEnd = (start + size).toInt()
            when {
                guid.contentEquals(GUID_FILE_PROPERTIES) -> parseFileProperties(buffer)
                guid.contentEquals(GUID_STREAM_PROPERTIES) -> parseStreamProperties(buffer, objectEnd)
                guid.contentEquals(GUID_HEADER_EXTENSION) -> {
                    buffer.skipBytes(GUID_SIZE + 2) // reserved GUID and word
                    val dataSize = buffer.readLittleEndianUnsignedInt()
                    parseHeaderObjects(buffer, minOf(objectEnd.toLong(), buffer.position + dataSize).toInt())
                }
                guid.contentEquals(GUID_EXTENDED_STREAM_PROPERTIES) -> parseExtendedStreamProperties(buffer, objectEnd)
            }
            buffer.setPosition(objectEnd)
        }
    }

    private fun parseFileProperties(buffer: ParsableByteArray) {
        buffer.skipBytes(GUID_SIZE + 8 + 8) // file ID, file size, creation date
        packetCount = buffer.readLittleEndianLong()
        val playDuration100ns = buffer.readLittleEndianLong()
        buffer.skipBytes(8) // send duration
        prerollMs = buffer.readLittleEndianLong()
        val flags = buffer.readLittleEndianUnsignedInt()
        buffer.skipBytes(4) // minimum data packet size - the spec requires it to equal the maximum
        val maximumPacketSize = buffer.readLittleEndianUnsignedInt()
        broadcast = flags and 0x01L != 0L
        if (maximumPacketSize in 1 until MAX_PACKET_SIZE) packetSize = maximumPacketSize.toInt()
        durationUs = if (!broadcast && playDuration100ns > 0) {
            (playDuration100ns / 10 - prerollMs * 1000).coerceAtLeast(0)
        } else {
            C.TIME_UNSET
        }
    }

    private fun parseStreamProperties(buffer: ParsableByteArray, objectEnd: Int) {
        val streamType = readGuid(buffer)
        buffer.skipBytes(GUID_SIZE + 8) // error correction type, time offset
        val typeSpecificLength = buffer.readLittleEndianUnsignedInt()
        buffer.skipBytes(4) // error correction data length
        val flags = buffer.readLittleEndianUnsignedShort()
        buffer.skipBytes(4) // reserved
        val encrypted = flags and 0x8000 != 0
        if (encrypted || buffer.position + typeSpecificLength > objectEnd) return
        val typeSpecificEnd = buffer.position + typeSpecificLength.toInt()
        val info = streamInfos.getOrPut(flags and 0x7F) { StreamInfo(flags and 0x7F) }
        when {
            streamType.contentEquals(GUID_AUDIO_MEDIA) -> parseWaveFormat(buffer, typeSpecificEnd, info)
            streamType.contentEquals(GUID_VIDEO_MEDIA) -> parseVideoFormat(buffer, typeSpecificEnd, info)
        }
    }

    /** WAVEFORMATEX. */
    private fun parseWaveFormat(buffer: ParsableByteArray, end: Int, info: StreamInfo) {
        if (end - buffer.position < WAVEFORMAT_SIZE) return
        val formatTag = buffer.readLittleEndianUnsignedShort()
        info.channelCount = buffer.readLittleEndianUnsignedShort()
        info.sampleRate = buffer.readLittleEndianUnsignedInt().toInt()
        info.averageBitrate = (buffer.readLittleEndianUnsignedInt() * 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        info.blockAlign = buffer.readLittleEndianUnsignedShort()
        info.bitsPerSample = buffer.readLittleEndianUnsignedShort()
        info.extraData = if (end - buffer.position >= 2) {
            val size = minOf(buffer.readLittleEndianUnsignedShort(), end - buffer.position)
            ByteArray(size).also { buffer.readBytes(it, 0, size) }
        } else {
            ByteArray(0)
        }
        info.isVideo = false
        info.mimeType = when (formatTag) {
            0x0160 -> WindowsMediaMimeTypes.AUDIO_WMA_V1
            0x0161 -> WindowsMediaMimeTypes.AUDIO_WMA_V2
            0x0162 -> WindowsMediaMimeTypes.AUDIO_WMA_PRO
            0x0055 -> MimeTypes.AUDIO_MPEG
            else -> null
        }
    }

    /** Video media type-specific data: a short prefix, then a BITMAPINFOHEADER plus codec extra data. */
    private fun parseVideoFormat(buffer: ParsableByteArray, end: Int, info: StreamInfo) {
        if (end - buffer.position < VIDEO_PREFIX_SIZE + BITMAPINFOHEADER_SIZE) return
        buffer.skipBytes(VIDEO_PREFIX_SIZE) // encoded width/height (repeated below), reserved flags, format data size
        val bitmapInfoSize = buffer.readLittleEndianUnsignedInt()
        info.width = buffer.readLittleEndianInt()
        info.height = abs(buffer.readLittleEndianInt())
        buffer.skipBytes(2 + 2) // planes, bit count
        val fourCc = ByteArray(4).also { buffer.readBytes(it, 0, 4) }
        buffer.skipBytes(4 * 5) // image size, pixels per meter x/y, colors used/important
        val extraSize = (bitmapInfoSize - BITMAPINFOHEADER_SIZE).coerceIn(0, (end - buffer.position).toLong()).toInt()
        info.extraData = ByteArray(extraSize).also { buffer.readBytes(it, 0, extraSize) }
        info.isVideo = true
        info.mimeType = when (String(fourCc, Charsets.US_ASCII).uppercase()) {
            "WMV1" -> WindowsMediaMimeTypes.VIDEO_WMV1
            "WMV2" -> WindowsMediaMimeTypes.VIDEO_WMV2
            "WMV3" -> WindowsMediaMimeTypes.VIDEO_WMV3
            "WVC1", "WMVA" -> MimeTypes.VIDEO_VC1
            "MP43", "DIV3" -> MimeTypes.VIDEO_MP43
            "MP42", "DIV2" -> MimeTypes.VIDEO_MP42
            "MP4S", "M4S2", "XVID", "DIVX", "DX50", "FMP4" -> MimeTypes.VIDEO_MP4V
            else -> null
        }
    }

    private fun parseExtendedStreamProperties(buffer: ParsableByteArray, objectEnd: Int) {
        buffer.skipBytes(8 + 8 + 4 * 6) // start/end time, bitrate and buffer triples
        val maxObjectSize = buffer.readLittleEndianUnsignedInt()
        buffer.skipBytes(4) // flags
        val streamNumber = buffer.readLittleEndianUnsignedShort() and 0x7F
        buffer.skipBytes(2) // language ID index
        val averageTimePerFrame100ns = buffer.readLittleEndianLong()
        val nameCount = buffer.readLittleEndianUnsignedShort()
        val payloadExtensionCount = buffer.readLittleEndianUnsignedShort()
        val info = streamInfos.getOrPut(streamNumber) { StreamInfo(streamNumber) }
        if (averageTimePerFrame100ns > 0) info.frameRate = (10_000_000.0 / averageTimePerFrame100ns).toFloat()
        if (maxObjectSize in 1..MAX_OBJECT_SIZE.toLong()) info.maxObjectSize = maxObjectSize.toInt()
        repeat(nameCount) {
            buffer.skipBytes(2) // language ID index
            buffer.skipBytes(buffer.readLittleEndianUnsignedShort())
        }
        repeat(payloadExtensionCount) {
            buffer.skipBytes(GUID_SIZE + 2) // extension system ID, data size
            buffer.skipBytes(buffer.readLittleEndianUnsignedInt().toInt())
        }
        // Some files define a stream only through a Stream Properties Object embedded here.
        if (buffer.position + OBJECT_HEADER_SIZE <= objectEnd) parseHeaderObjects(buffer, objectEnd)
    }

    private fun outputTracks() {
        for (info in streamInfos.values) {
            val mimeType = info.mimeType ?: continue
            val builder = Format.Builder()
                .setId(info.streamNumber)
                .setContainerMimeType(CONTAINER_MIME_TYPE)
                .setSampleMimeType(mimeType)
            if (info.maxObjectSize > 0) builder.setMaxInputSize(info.maxObjectSize)
            val trackType = if (info.isVideo) {
                builder.setWidth(info.width)
                    .setHeight(info.height)
                    .setFrameRate(info.frameRate)
                    .setInitializationData(if (info.extraData.isNotEmpty()) listOf(info.extraData) else emptyList())
                C.TRACK_TYPE_VIDEO
            } else {
                builder.setChannelCount(info.channelCount)
                    .setSampleRate(info.sampleRate)
                    .setAverageBitrate(info.averageBitrate)
                if (mimeType.startsWith(WMA_MIME_PREFIX)) {
                    builder.setInitializationData(
                        listOf(info.extraData, WindowsMediaMimeTypes.encodeAudioParams(info.blockAlign, info.bitsPerSample))
                    )
                }
                C.TRACK_TYPE_AUDIO
            }
            val trackOutput = output.track(info.streamNumber, trackType)
            trackOutput.format(builder.build())
            streams[info.streamNumber] = Stream(trackOutput, info.isVideo)
        }
        output.endTracks()
    }

    // Every outer field referenced here is qualified: inside a class implementing SeekMap, a bare
    // `durationUs` resolves to SeekMap.getDurationUs() itself - infinite recursion, caught by
    // AsfExtractorTest as a StackOverflowError before it ever reached a device.
    private inner class AsfSeekMap : SeekMap {
        override fun isSeekable(): Boolean =
            indexPacketNumbers != null || (this@AsfExtractor.durationUs != C.TIME_UNSET && this@AsfExtractor.durationUs > 0 && packetCount > 0)

        override fun getDurationUs(): Long = this@AsfExtractor.durationUs

        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
            val index = indexPacketNumbers
            if (index != null) {
                // Index entry i covers presentation time i * interval, which still includes the preroll.
                val prerollUs = prerollMs * 1000
                val entry = ((timeUs + prerollUs) / indexIntervalUs).coerceIn(0, index.size - 1L).toInt()
                val entryTimeUs = (entry * indexIntervalUs - prerollUs).coerceAtLeast(0)
                return SeekMap.SeekPoints(SeekPoint(entryTimeUs, dataStart + index[entry].toLong() * packetSize))
            }
            if (!isSeekable()) return SeekMap.SeekPoints(SeekPoint(0, dataStart))
            val packetNumber = (timeUs.toDouble() / this@AsfExtractor.durationUs * packetCount).toLong().coerceIn(0, packetCount - 1)
            return SeekMap.SeekPoints(SeekPoint(timeUs, dataStart + packetNumber * packetSize))
        }
    }

    private class StreamInfo(val streamNumber: Int) {
        var mimeType: String? = null
        var isVideo = false
        var width = Format.NO_VALUE
        var height = Format.NO_VALUE
        var frameRate = Format.NO_VALUE.toFloat()
        var channelCount = Format.NO_VALUE
        var sampleRate = Format.NO_VALUE
        var averageBitrate = Format.NO_VALUE
        var blockAlign = 0
        var bitsPerSample = 0
        var maxObjectSize = 0
        var extraData = ByteArray(0)
    }

    /** Reassembles media objects for one track from payload fragments spread across data packets. */
    private class Stream(private val trackOutput: TrackOutput, val isVideo: Boolean) {
        var waitingForKeyFrame = false
        private var buffer = ByteArray(0)
        private var objectSize = 0
        private var filled = 0
        private var objectNumber = -1
        private var objectTimeUs = 0L
        private var objectKeyFrame = false
        private val sample = ParsableByteArray()

        fun discardObject() {
            objectSize = 0
            filled = 0
            objectNumber = -1
        }

        fun appendFragment(
            packet: ParsableByteArray,
            length: Int,
            number: Int,
            offset: Int,
            size: Long,
            timeUs: Long,
            keyFrame: Boolean
        ) {
            if (offset == 0) {
                if (size <= 0 || size > MAX_OBJECT_SIZE) {
                    discardObject()
                    return
                }
                if (buffer.size < size) buffer = ByteArray(size.toInt())
                objectSize = size.toInt()
                filled = 0
                objectNumber = number
                objectTimeUs = timeUs
                objectKeyFrame = keyFrame
            } else if (objectSize == 0 || number != objectNumber || offset != filled) {
                // The rest of an object whose start was never seen (e.g. right after a seek) or lost.
                discardObject()
                return
            }
            val count = minOf(length, objectSize - filled)
            packet.readBytes(buffer, filled, count)
            filled += count
            if (filled == objectSize) {
                sample.reset(buffer, objectSize)
                outputSample(sample, objectSize, objectTimeUs, objectKeyFrame)
                discardObject()
            }
        }

        fun outputWholeObject(packet: ParsableByteArray, size: Int, timeUs: Long, keyFrame: Boolean) {
            discardObject()
            outputSample(packet, size, timeUs, keyFrame)
        }

        private fun outputSample(data: ParsableByteArray, size: Int, timeUs: Long, keyFrame: Boolean) {
            val isKeyFrame = keyFrame || !isVideo
            if (waitingForKeyFrame && !isKeyFrame) {
                data.skipBytes(size)
                return
            }
            waitingForKeyFrame = false
            trackOutput.sampleData(data, size)
            trackOutput.sampleMetadata(timeUs, if (isKeyFrame) C.BUFFER_FLAG_KEY_FRAME else 0, size, 0, null)
        }
    }

    private companion object {
        const val TAG = "AsfExtractor"
        const val CONTAINER_MIME_TYPE = "video/x-ms-asf"
        const val WMA_MIME_PREFIX = "audio/x-ms-wma"

        const val STATE_READING_HEADER = 0
        const val STATE_READING_INDEX = 1
        const val STATE_READING_PACKETS = 2

        const val GUID_SIZE = 16
        const val OBJECT_HEADER_SIZE = 24
        const val HEADER_OBJECT_PREFIX_SIZE = 30 // object header + object count + two reserved bytes
        const val DATA_OBJECT_HEADER_SIZE = 50 // object header + file ID + total packets + reserved
        const val SIMPLE_INDEX_FIXED_SIZE = 32L // file ID + time interval + max packet count + entry count
        const val SIMPLE_INDEX_ENTRY_SIZE = 6
        const val WAVEFORMAT_SIZE = 16
        const val VIDEO_PREFIX_SIZE = 4 + 4 + 1 + 2
        const val BITMAPINFOHEADER_SIZE = 40

        const val MAX_HEADER_SIZE = 64L shl 20
        const val MAX_INDEX_SIZE = 64L shl 20
        const val MAX_PACKET_SIZE = 1L shl 20
        const val MAX_OBJECT_SIZE = 1 shl 24 // same cap as FFmpeg

        // GUIDs as stored on disk (the first three fields little-endian), cross-checked with libavformat/asf.c.
        val GUID_HEADER = guid("3026B2758E66CF11A6D900AA0062CE6C")
        val GUID_DATA = guid("3626B2758E66CF11A6D900AA0062CE6C")
        val GUID_FILE_PROPERTIES = guid("A1DCAB8C47A9CF118EE400C00C205365")
        val GUID_STREAM_PROPERTIES = guid("9107DCB7B7A9CF118EE600C00C205365")
        val GUID_HEADER_EXTENSION = guid("B503BF5F2EA9CF118EE300C00C205365")
        val GUID_EXTENDED_STREAM_PROPERTIES = guid("CBA5E61472C632438399A96952065B5A")
        val GUID_AUDIO_MEDIA = guid("409E69F84D5BCF11A8FD00805F5C442B")
        val GUID_VIDEO_MEDIA = guid("C0EF19BC4D5BCF11A8FD00805F5C442B")
        val GUID_SIMPLE_INDEX = guid("90080033B1E5CF1189F400A0C90349CB")

        fun guid(hex: String): ByteArray = ByteArray(GUID_SIZE) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

        fun readGuid(buffer: ParsableByteArray): ByteArray = ByteArray(GUID_SIZE).also { buffer.readBytes(it, 0, GUID_SIZE) }

        fun malformed(message: String): ParserException = ParserException.createForMalformedContainer(message, null)
    }
}
