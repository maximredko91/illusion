package com.illusion.app.data.scan

import android.media.MediaDataSource
import com.illusion.app.data.smb.SmbRandomAccessFile

/**
 * Lets [android.media.MediaMetadataRetriever] / [android.media.MediaExtractor] read a video
 * directly off the SMB share (via positional reads) instead of requiring a local file.
 *
 * Reads through a few [WINDOW_BYTES] read-ahead windows, same reasoning as SmbDataSource's own
 * read-ahead: the platform extractor asks for a sample at a time, and every SMB READ is a full
 * round trip to the NAS. Without buffering, fingerprinting five minutes of an episode's audio took
 * ~75 s of nothing but round trips (measured on-device, 2026-11-15).
 *
 * Several windows, not one: the MP4 extractor alternates between the sample data and the sample
 * tables in `moov` on every sample, megabytes apart, so a single window was refilled on EVERY
 * read - a first attempt with one 1 MiB window made the same job take 4.5 minutes instead. The
 * windows are deliberately small, too: only the audio is wanted, and a big window would drag the
 * video interleaved around it off the share for nothing. Least recently used window is replaced.
 */
class SmbMediaDataSource(
    private val randomAccessFile: SmbRandomAccessFile,
    private val length: Long
) : MediaDataSource() {

    private class Window {
        val data = ByteArray(WINDOW_BYTES)
        var start = 0L
        var length = 0
        var lastUsed = 0L
    }

    private val windows = Array(WINDOW_COUNT) { Window() }
    private var useCounter = 0L

    // readAt arrives on binder threads, not necessarily one at a time.
    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= length) return -1
        val want = minOf(size.toLong(), length - position).toInt()
        var totalRead = 0
        while (totalRead < want) {
            val current = position + totalRead
            val window = windows.firstOrNull { current >= it.start && current < it.start + it.length }
                ?: fill(current)
                ?: break
            window.lastUsed = ++useCounter
            val inWindow = (current - window.start).toInt()
            val count = minOf(want - totalRead, window.length - inWindow)
            System.arraycopy(window.data, inWindow, buffer, offset + totalRead, count)
            totalRead += count
        }
        return if (totalRead == 0) -1 else totalRead
    }

    /** Loads the least recently used window from [position]; null at end of file / on a failed read. */
    private fun fill(position: Long): Window? {
        val window = windows.minByOrNull { it.lastUsed }!!
        val toRead = minOf(WINDOW_BYTES.toLong(), length - position).toInt()
        val read = randomAccessFile.read(window.data, position, 0, toRead)
        if (read <= 0) {
            window.length = 0
            return null
        }
        window.start = position
        window.length = read
        return window
    }

    override fun getSize(): Long = length

    override fun close() {
        randomAccessFile.close()
    }
}

private const val WINDOW_BYTES = 256 * 1024
private const val WINDOW_COUNT = 4
