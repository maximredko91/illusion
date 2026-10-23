package com.illusion.app.data.player

import com.illusion.app.data.smb.SmbRandomAccessFile
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * HTTP server that re-exposes a single SMB file as
 * `http://<host>:<port>/stream?token=<token>`, with `Range` support for seeking. Exists because most external video players (everything except
 * VLC/MX Player, which have their own SMB clients) can't open an `smb://` URI passed via
 * `Intent.ACTION_VIEW` at all - they only understand ordinary HTTP(S).
 *
 * Binds every interface, not just loopback (changed 2026-09-19): a DLNA renderer on the TV fetches
 * the file over the LAN, so a loopback-only bind can't serve it. The token is what keeps that
 * wider bind from turning the NAS into an open share for the whole network, and since 2026-10-24
 * it is also what names the file: a token is minted per [register]ed file and the server reads the
 * source/path/size from its own registration, never from the request. Before that the token was
 * per server instance while the path rode in the query string, so anyone holding one URL could
 * read ANY path on ANY configured source - the whole NAS, not just the file being cast.
 *
 * Reuses [SmbDataSourceFactory] (the same connection pool ExoPlayer's own internal playback uses)
 * rather than opening a separate SMB session, so this doesn't pay for a second authentication
 * round-trip on top of whatever's already cached.
 */
class LocalStreamingServer(
    private val dataSourceFactory: SmbDataSourceFactory,
    port: Int = 0
) : NanoHTTPD(null, port) {

    /** token -> the one file it may read. */
    private val registrations = ConcurrentHashMap<String, StreamRegistration>()
    private val random = SecureRandom()

    /** What a token stands for - the file this server will serve for it, and nothing else. */
    private class StreamRegistration(val sourceId: Long, val path: String, val sizeBytes: Long)

    /**
     * Mints (or reuses) the token for one file and returns it. Reused for a repeat request for the
     * same file so re-casting the same item doesn't grow the map without bound; a token stays valid
     * only as long as this server instance, which [StreamingService.stop] disposes of.
     */
    fun register(sourceId: Long, path: String, sizeBytes: Long): String {
        registrations.entries.firstOrNull { (_, it) -> it.sourceId == sourceId && it.path == path && it.sizeBytes == sizeBytes }
            ?.let { return it.key }
        val token = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        registrations[token] = StreamRegistration(sourceId, path, sizeBytes)
        return token
    }

    /** Called when a streaming response starts / when it (eventually) closes - lets
     * [StreamingService] know whether a connection is actually still live rather than guessing
     * from request cadence. A player can legitimately buffer ahead over the loopback connection
     * and then go quiet on it for a long stretch while it plays from its own buffer without ever
     * closing the connection - only the close is a real "done" signal. */
    var onStreamOpened: (() -> Unit)? = null
    var onStreamClosed: (() -> Unit)? = null

    override fun serve(session: IHTTPSession): Response {
        val response = when {
            session.method == Method.OPTIONS && session.uri == STREAM_PATH ->
                newFixedLengthResponse(Response.Status.OK, "text/plain", "")
            else -> serveRequest(session)
        }
        // The default Google Cast receiver is a web app, so Range requests need CORS too.
        // Actual media requests still require the per-server token in serveFile().
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Range, Content-Type")
        response.addHeader("Access-Control-Expose-Headers", "Content-Length, Content-Range, Accept-Ranges")
        return response
    }

    private fun serveRequest(session: IHTTPSession): Response = when (session.uri) {
        STREAM_PATH -> runCatching { serveFile(session) }
            .getOrElse { newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", it.message ?: "Error") }
        else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
    }

    private fun serveFile(session: IHTTPSession): Response {
        val token = session.parameters["token"]?.firstOrNull()
        val registration = token?.let { registrations[it] }
            ?: return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Forbidden")
        val totalLength = registration.sizeBytes.takeIf { it > 0 } ?: -1L

        val rangeHeader = session.headers["range"]
        val range = resolveStreamRange(rangeHeader, totalLength)
            ?: return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "")
                .also { if (totalLength > 0) it.addHeader("Content-Range", "bytes */$totalLength") }
        val (start, end, isPartial) = range

        val raf = dataSourceFactory.openFile(registration.sourceId, registration.path)
        val mimeType = mimeTypeForExtension(registration.path.substringAfterLast('.', ""))
        val contentLength = if (totalLength > 0) end - start + 1 else -1L

        onStreamOpened?.invoke()
        val response = newFixedLengthResponse(
            if (isPartial) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
            mimeType,
            SmbStreamingInputStream(raf, start) { onStreamClosed?.invoke() },
            contentLength
        )
        response.addHeader("Accept-Ranges", "bytes")
        if (isPartial && totalLength > 0) {
            response.addHeader("Content-Range", "bytes $start-$end/$totalLength")
        }
        return response
    }

    companion object {
        const val STREAM_PATH = "/stream"
    }
}

/** Sequential [InputStream] view over an [SmbRandomAccessFile], starting at [startPosition] -
 * NanoHTTPD reads this like any other stream, translating each `read()` into a positional SMB
 * read at the current offset. [onClosed] fires exactly once, whenever NanoHTTPD is actually done
 * with the response (full read, client disconnect, or a seek that abandons this stream for a new
 * ranged request). */
private class SmbStreamingInputStream(
    private val raf: SmbRandomAccessFile,
    startPosition: Long,
    private val onClosed: () -> Unit
) : InputStream() {
    private var position = startPosition
    private var closed = false
    private val single = ByteArray(1)

    override fun read(): Int {
        val read = read(single, 0, 1)
        return if (read <= 0) -1 else single[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = raf.read(b, position, off, len)
        if (read > 0) position += read
        return read
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { raf.close() }
        onClosed()
    }
}

/**
 * (start, end, isPartial) for a Range header against a file of [totalLength] bytes, or null when
 * the header is there but unsatisfiable (the caller answers 416). Handles the three forms RFC 9110
 * defines for a single range: `bytes=1000-`, `bytes=1000-2000` and the suffix form `bytes=-500`,
 * which asks for the LAST 500 bytes - until 2026-10-24 that one was read as "start at -500",
 * clamped to 0, and quietly served the FIRST 500 bytes instead. Falls back to the whole file
 * (non-partial) if there's no Range header or the length is unknown.
 */
internal fun resolveStreamRange(rangeHeader: String?, totalLength: Long): Triple<Long, Long, Boolean>? {
    if (rangeHeader == null || !rangeHeader.startsWith("bytes=") || totalLength <= 0) {
        return Triple(0L, (totalLength - 1).coerceAtLeast(0L), false)
    }
    // Multiple ranges in one request ("bytes=0-99,200-299") would need a multipart response; no
    // player this bridge serves asks for them, so take the first and serve that.
    val spec = rangeHeader.removePrefix("bytes=").substringBefore(',').trim()
    if (!spec.contains('-')) return null
    val startSpec = spec.substringBefore('-').trim()
    val endSpec = spec.substringAfter('-').trim()
    if (startSpec.isEmpty()) {
        // Suffix form: the last N bytes. N == 0 is unsatisfiable by the spec.
        val suffixLength = endSpec.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val start = (totalLength - suffixLength).coerceAtLeast(0L)
        return Triple(start, totalLength - 1, true)
    }
    val start = startSpec.toLongOrNull() ?: return null
    if (start >= totalLength) return null
    val end = endSpec.takeIf { it.isNotEmpty() }?.let { it.toLongOrNull() ?: return null }
        ?.coerceIn(start, totalLength - 1)
        ?: (totalLength - 1)
    return Triple(start, end, true)
}

internal fun mimeTypeForExtension(extension: String): String = when (extension.lowercase()) {
    "mp4", "m4v" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "avi" -> "video/x-msvideo"
    "mov" -> "video/quicktime"
    "webm" -> "video/webm"
    "ts" -> "video/mp2t"
    "wmv" -> "video/x-ms-wmv"
    "mpg", "mpeg", "vob" -> "video/mpeg"
    else -> "video/*"
}
