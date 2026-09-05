package com.illusion.app.work

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadPrefixTest {
    private val video = ByteArray(900_000) { (it * 31).toByte() }
    private fun remote(buffer: ByteArray, offset: Long, count: Int): Int {
        val read = minOf(count, 37) // SMB reads may be short.
        video.copyInto(buffer, 0, offset.toInt(), offset.toInt() + read)
        return read
    }

    @Test fun resumesAtExactPositionEvenWhenSkipMakesNoProgress() {
        val input = object : ByteArrayInputStream(video) {
            override fun skip(n: Long): Long = 0
        }
        verifyUploadPrefix(input, 600_003, ::remote)
        assertArrayEquals(video.copyOfRange(600_003, video.size), input.readBytes())
    }

    @Test(expected = IOException::class) fun rejectsDifferentVideoWithSameSize() {
        val other = video.copyOf().also { it[599_999] = (it[599_999] + 1).toByte() }
        verifyUploadPrefix(ByteArrayInputStream(other), 600_000, ::remote)
    }

    @Test(expected = EOFException::class) fun rejectsRemoteLongerThanLocal() {
        verifyUploadPrefix(ByteArrayInputStream(video.copyOf(100)), 101, ::remote)
    }

    @Test(expected = EOFException::class) fun rejectsRemoteTruncatedDuringVerification() {
        verifyUploadPrefix(ByteArrayInputStream(video), 100) { _, _, _ -> -1 }
    }

    @Test fun completeMatchingFileLeavesNoBytesToAppend() {
        val input = ByteArrayInputStream(video)
        verifyUploadPrefix(input, video.size.toLong(), ::remote)
        assertEquals(-1, input.read())
    }
}
