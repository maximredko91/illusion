package com.seance.app.data.player

import android.net.Uri

/**
 * Encodes an SMB source id + in-share path (+ optional known size) as a Uri Media3 can hold
 * on a MediaItem, so [SmbDataSource] can resolve it back to a share/file without a DB lookup
 * on the playback thread.
 */
object SmbMediaUri {
    private const val SCHEME = "smb-item"

    fun build(sourceId: Long, path: String, sizeBytes: Long = -1L): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(sourceId.toString())
        .appendQueryParameter("path", path)
        .appendQueryParameter("size", sizeBytes.toString())
        .build()

    data class Parsed(val sourceId: Long, val path: String, val sizeBytes: Long)

    fun parse(uri: Uri): Parsed {
        val sourceId = requireNotNull(uri.authority?.toLongOrNull()) { "Invalid smb uri, missing source id: $uri" }
        val path = requireNotNull(uri.getQueryParameter("path")) { "Invalid smb uri, missing path: $uri" }
        val sizeBytes = uri.getQueryParameter("size")?.toLongOrNull() ?: -1L
        return Parsed(sourceId, path, sizeBytes)
    }
}
