package com.illusion.app.data.scan

import android.media.MediaDataSource
import com.illusion.app.data.smb.SmbRandomAccessFile

/**
 * Lets [android.media.MediaMetadataRetriever] read a video directly off the SMB share
 * (via positional reads) instead of requiring a local file.
 */
class SmbMediaDataSource(
    private val randomAccessFile: SmbRandomAccessFile,
    private val length: Long
) : MediaDataSource() {

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= length) return -1
        val want = minOf(size.toLong(), length - position).toInt()
        var totalRead = 0
        while (totalRead < want) {
            val chunk = ByteArray(want - totalRead)
            val read = randomAccessFile.read(chunk, position + totalRead)
            if (read <= 0) break
            System.arraycopy(chunk, 0, buffer, offset + totalRead, read)
            totalRead += read
        }
        return if (totalRead == 0) -1 else totalRead
    }

    override fun getSize(): Long = length

    override fun close() {
        randomAccessFile.close()
    }
}
