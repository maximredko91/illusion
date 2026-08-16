package com.seance.app.data.player

import com.seance.app.data.smb.SmbRandomAccessFile

/**
 * Reads the audio-track title(s) embedded directly in an MP4's ISO-BMFF box tree
 * (`moov/trak/udta/name`) - e.g. "AC3 5.1 @ 448 kbps - DUB, Blu-ray CEE", the exact string
 * TinyMediaManager's "Media Files > Audio Streams > Title" field shows. Media3 1.11.0's MP4
 * extractor never surfaces this into [androidx.media3.common.Format.label]: `BoxParser.java` has
 * no call to `Format.Builder.setLabel()` anywhere (confirmed by decompiling
 * `media3-extractor`'s sources - `MatroskaExtractor.java` does the MKV equivalent via its
 * native per-track Name element, but nothing does it for MP4), so [AudioTrackProber]'s
 * ExoPlayer-based probe can only ever report a track's language for an MP4 file, never this.
 * This reads the raw bytes instead - verified against a real file's hex dump: a plain `name` box
 * (no version/flags header, unlike the sibling iTunes-style `udta/meta/ilst/©nam/data` box some
 * encoders also add at the movie level) holding the title as raw UTF-8 text, nested directly
 * inside the audio `trak`'s own `udta` box.
 *
 * Only ever reads the `moov` box, never `mdat` (the actual video/audio payload) - for a "fast
 * start" file (`moov` before `mdat`, the norm for anything meant to stream) that's one small
 * positional read of a header-sized region plus one read of `moov` itself, both well under a few
 * MB. Files whose `moov` isn't found within [MAX_SEARCH_BYTES] of the start give up rather than
 * scanning an arbitrarily large prefix over the network.
 */
class Mp4AudioTrackTitleReader {

    /**
     * One entry per audio `trak` found, in file order (null where that track has no `udta`/`name`
     * box) - or an empty list if `moov` couldn't be found/read. The caller is responsible for
     * matching this order against ExoPlayer's own audio track enumeration, which for the
     * overwhelmingly common single-audio-track case is trivially correct.
     */
    fun readAudioTrackTitles(raf: SmbRandomAccessFile): List<String?> {
        val moovRange = findTopLevelBox(raf, "moov") ?: return emptyList()
        val moov = ByteArray(moovRange.size)
        if (!raf.readFully(moov, moovRange.start)) return emptyList()

        // One entry per audio trak ONLY - a non-audio (e.g. video) trak must not add a placeholder
        // slot here, or this list's indices drift out of sync with the caller's audio-only
        // enumeration for any file with more than one trak (i.e. nearly all of them: video + audio
        // is the norm). Confirmed on-device this was silently returning the video track's (always
        // null) slot instead of the audio track's real title for exactly that reason.
        val titles = mutableListOf<String?>()
        forEachChildBox(moov, 0, moov.size) { type, start, end ->
            if (type == "trak") {
                val (isAudio, title) = readTrak(moov, start, end)
                if (isAudio) titles += title
            }
        }
        return titles
    }

    /** [Pair.first] is whether this `trak` is an audio track; [Pair.second] is its title box content, if any. */
    private fun readTrak(buf: ByteArray, start: Int, end: Int): Pair<Boolean, String?> {
        var isAudio = false
        var title: String? = null
        forEachChildBox(buf, start, end) { type, childStart, childEnd ->
            when (type) {
                "mdia" -> forEachChildBox(buf, childStart, childEnd) { mdiaType, mdiaStart, mdiaEnd ->
                    // hdlr payload layout: 4 bytes version+flags, 4 bytes predefined, then the
                    // 4-byte handler type ("soun" for audio).
                    if (mdiaType == "hdlr" && mdiaEnd - mdiaStart >= 12) {
                        if (String(buf, mdiaStart + 8, 4, Charsets.US_ASCII) == "soun") isAudio = true
                    }
                }
                "udta" -> forEachChildBox(buf, childStart, childEnd) { udtaType, nameStart, nameEnd ->
                    if (udtaType == "name" && nameEnd > nameStart) {
                        title = String(buf, nameStart, nameEnd - nameStart, Charsets.UTF_8).trim()
                    }
                }
            }
        }
        return isAudio to title.takeIf { !it.isNullOrBlank() }
    }

    private data class BoxRange(val start: Long, val size: Int)

    /** Walks top-level boxes via positional reads until [targetType] is found, or [MAX_SEARCH_BYTES] is exceeded. */
    private fun findTopLevelBox(raf: SmbRandomAccessFile, targetType: String): BoxRange? {
        var offset = 0L
        val header = ByteArray(16)
        while (offset < MAX_SEARCH_BYTES) {
            val read = raf.read(header, offset, 0, header.size)
            if (read < 8) return null
            var size = readUInt32(header, 0)
            val type = String(header, 4, 4, Charsets.US_ASCII)
            var headerSize = 8L
            if (size == 1L) {
                if (read < 16) return null
                size = readUInt64(header, 8)
                headerSize = 16L
            }
            // size < headerSize covers both malformed boxes and size==0 ("extends to EOF", which
            // moov never legitimately does) - give up rather than guess at the file's length.
            if (size < headerSize) return null
            if (type == targetType) {
                val payloadSize = size - headerSize
                if (payloadSize <= 0 || payloadSize > MAX_MOOV_SIZE) return null
                return BoxRange(offset + headerSize, payloadSize.toInt())
            }
            offset += size
        }
        return null
    }

    /** Invokes [onChild] for each direct child box of buffer region [start] until [end) - in-memory, no I/O. */
    private inline fun forEachChildBox(buf: ByteArray, start: Int, end: Int, onChild: (type: String, childStart: Int, childEnd: Int) -> Unit) {
        var offset = start
        while (offset + 8 <= end) {
            var size = readUInt32(buf, offset).toInt()
            val type = String(buf, offset + 4, 4, Charsets.US_ASCII)
            var headerSize = 8
            if (size == 1) {
                if (offset + 16 > end) return
                size = readUInt64(buf, offset + 8).toInt()
                headerSize = 16
            }
            if (size < headerSize) return
            val childEnd = offset + size
            if (childEnd > end) return
            onChild(type, offset + headerSize, childEnd)
            offset = childEnd
        }
    }

    /** Reads until [destination] is completely filled, or returns false on EOF/error partway through. */
    private fun SmbRandomAccessFile.readFully(destination: ByteArray, filePosition: Long): Boolean {
        var total = 0
        while (total < destination.size) {
            val read = read(destination, filePosition + total, total, destination.size - total)
            if (read <= 0) return false
            total += read
        }
        return true
    }

    private fun readUInt32(buf: ByteArray, offset: Int): Long =
        ((buf[offset].toLong() and 0xFF) shl 24) or
            ((buf[offset + 1].toLong() and 0xFF) shl 16) or
            ((buf[offset + 2].toLong() and 0xFF) shl 8) or
            (buf[offset + 3].toLong() and 0xFF)

    private fun readUInt64(buf: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (buf[offset + i].toLong() and 0xFF)
        }
        return result
    }

    companion object {
        private const val MAX_SEARCH_BYTES = 64L * 1024 * 1024
        private const val MAX_MOOV_SIZE = 32L * 1024 * 1024
    }
}
