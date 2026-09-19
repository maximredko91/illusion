package com.illusion.app.data.player

import com.illusion.app.data.smb.SmbRandomAccessFile
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.security.SecureRandom

/**
 * HTTP server that re-exposes a single SMB file as
 * `http://<host>:<port>/stream?source=<id>&path=<encoded path>&size=<bytes>&token=<token>`, with
 * `Range` support for seeking. Exists because most external video players (everything except
 * VLC/MX Player, which have their own SMB clients) can't open an `smb://` URI passed via
 * `Intent.ACTION_VIEW` at all - they only understand ordinary HTTP(S).
 *
 * Binds every interface, not just loopback (changed 2026-09-19): a DLNA renderer on the TV fetches
 * the file over the LAN, so a loopback-only bind can't serve it. External players on this device
 * still use the 127.0.0.1 form, unchanged. [token] is what keeps that wider bind from turning the
 * NAS into an open share for the whole network - it's regenerated per server instance and every
 * request without it is refused, so a URL is only usable by whoever this app handed it to, for as
 * long as the server lives.
 *
 * Reuses [SmbDataSourceFactory] (the same connection pool ExoPlayer's own internal playback uses)
 * rather than opening a separate SMB session, so this doesn't pay for a second authentication
 * round-trip on top of whatever's already cached.
 */
class LocalStreamingServer(
    private val dataSourceFactory: SmbDataSourceFactory,
    port: Int = 0
) : NanoHTTPD(null, port) {

    /** Per-instance secret every request must carry - see this class's own KDoc. */
    val token: String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

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
        val params = session.parameters
        if (params["token"]?.firstOrNull() != token) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Forbidden")
        }
        val sourceId = params["source"]?.firstOrNull()?.toLongOrNull()
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing source")
        val path = params["path"]?.firstOrNull()
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing path")
        val totalLength = params["size"]?.firstOrNull()?.toLongOrNull()?.takeIf { it > 0 } ?: -1L

        val raf = dataSourceFactory.openFile(sourceId, path)
        val mimeType = mimeTypeForExtension(path.substringAfterLast('.', ""))
        val rangeHeader = session.headers["range"]
        val (start, end, isPartial) = resolveStreamRange(rangeHeader, totalLength)
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

/** (start, end, isPartial) for a Range header like "bytes=1000-" or "bytes=1000-2000" against a
 * file of [totalLength] bytes. Falls back to the whole file (non-partial) if there's no Range
 * header or the length is unknown. */
internal fun resolveStreamRange(rangeHeader: String?, totalLength: Long): Triple<Long, Long, Boolean> {
    if (rangeHeader == null || !rangeHeader.startsWith("bytes=") || totalLength <= 0) {
        return Triple(0L, (totalLength - 1).coerceAtLeast(0L), false)
    }
    val spec = rangeHeader.removePrefix("bytes=")
    val parts = spec.split("-")
    val start = parts.getOrNull(0)?.toLongOrNull()?.coerceIn(0, totalLength - 1) ?: 0L
    val requestedEnd = parts.getOrNull(1)?.toLongOrNull()
    val end = requestedEnd?.coerceIn(start, totalLength - 1) ?: (totalLength - 1)
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
