package com.illusion.app.data.smb

import java.security.MessageDigest

/**
 * Derives an identifier for a remote file that survives rename AND move: based on file size plus
 * a content sample (first/last bytes) rather than the file's name or path, so a rescan can
 * re-associate watch progress/favorites even after the file was renamed or relocated on the share.
 */
object StableIdGenerator {
    fun forFile(sourceId: Long, sizeBytes: Long, headBytes: ByteArray, tailBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("$sourceId|$sizeBytes|".toByteArray(Charsets.UTF_8))
        digest.update(headBytes)
        digest.update(tailBytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
