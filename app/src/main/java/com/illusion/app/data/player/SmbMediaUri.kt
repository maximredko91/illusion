package com.illusion.app.data.player

import android.net.Uri

/**
 * Encodes an SMB source id + in-share path (+ optional known size) as a Uri Media3 can hold
 * on a MediaItem, so [SmbDataSource] can resolve it back to a share/file without a DB lookup
 * on the playback thread.
 */
object SmbMediaUri {
    private const val SCHEME = "smb-item"

    /**
     * The file's own name is appended as a path segment purely as a hint: DefaultExtractorsFactory
     * orders its extractors by the file type inferred from the Uri, and this Uri used to carry no
     * name or extension at all. With nothing to infer from, the default order applies and a
     * sniffer can win before the right one is ever tried - confirmed on-device on an .avi
     * (XVID/MP3) episode that Media3 parsed as a bare MP3 stream: audio played, video "didn't
     * exist", and playback sat buffering forever. [parse] still reads everything it needs from the
     * authority and query parameters, so the segment is inert beyond that hint.
     */
    fun build(sourceId: Long, path: String, sizeBytes: Long = -1L): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(sourceId.toString())
        .appendPath(path.substringAfterLast('\\').substringAfterLast('/'))
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
