@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.illusion.app.data.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.smbj.common.SMBRuntimeException
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.smb.MissingSmbCredentialException
import com.illusion.app.data.smb.SmbClient
import com.illusion.app.data.smb.SmbConnection
import com.illusion.app.data.smb.SmbRandomAccessFile
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking

/**
 * Streams playback directly from an SMB share via smbj, keyed off the sourceId+path encoded
 * in [SmbMediaUri]. Connections are cached per sourceId (shared across DataSource instances,
 * i.e. across seeks) so scrubbing doesn't pay for a fresh SMB session every time.
 */
class SmbDataSourceFactory(
    private val sourceRepository: SmbSourceRepository,
    private val smbClient: SmbClient
) : DataSource.Factory, AutoCloseable {
    private val connections = ConcurrentHashMap<Long, SmbConnection>()

    override fun createDataSource(): DataSource = SmbDataSource(this)

    internal fun openFile(sourceId: Long, path: String): SmbRandomAccessFile =
        connectionFor(sourceId).openRandomAccessFile(path)

    /**
     * Drops the cached connection for [sourceId] so the next [openFile] call re-authenticates -
     * needed because a NAS/router can silently close an idle SMB session server-side; without
     * this, every read/seek on that source keeps failing forever against the same dead session.
     */
    internal fun invalidate(sourceId: Long) {
        connections.remove(sourceId)?.let { runCatching { it.close() } }
    }

    /**
     * A cached connection can go dead between uses - the NAS closes idle SMB sessions
     * aggressively (observed within ~15s of no reads), and [SmbConnection.isConnected] reflects
     * that immediately without a network round trip. Reusing a dead connection without checking
     * fails on the very first operation, and since it stays cached, every retry keeps hitting the
     * exact same dead session - `getOrPut` alone can't fix this since it only creates when the
     * key is absent, not when the cached value has gone stale.
     */
    private fun connectionFor(sourceId: Long): SmbConnection {
        connections[sourceId]?.let { existing ->
            if (existing.isConnected) return existing
            connections.remove(sourceId, existing)
            runCatching { existing.close() }
        }
        return connections.getOrPut(sourceId) {
            runBlocking {
                // DataSource.open()'s contract (and ExoPlayer's loader thread) expects an
                // IOException on failure, not an arbitrary exception - MissingSmbCredentialException
                // is neither, so it has to be wrapped here rather than left to propagate raw.
                val info = try {
                    sourceRepository.connectionInfoById(sourceId)
                } catch (e: MissingSmbCredentialException) {
                    throw IOException(e.message, e)
                } ?: throw IOException("Unknown SMB source or missing credentials: $sourceId")
                smbClient.connect(info)
            }
        }
    }

    override fun close() {
        connections.values.forEach { runCatching { it.close() } }
        connections.clear()
    }
}

class SmbDataSource internal constructor(
    private val factory: SmbDataSourceFactory
) : BaseDataSource(/* isNetwork = */ true) {

    private var randomAccessFile: SmbRandomAccessFile? = null
    private var position: Long = 0
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false
    private var currentUri: Uri? = null
    private var scratch = ByteArray(0)
    private var sourceId: Long = -1
    private var path: String = ""

    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        val parsed = SmbMediaUri.parse(dataSpec.uri)
        sourceId = parsed.sourceId
        path = parsed.path
        randomAccessFile = factory.openFile(sourceId, path)
        position = dataSpec.position
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            parsed.sizeBytes >= 0 -> parsed.sizeBytes - dataSpec.position
            else -> C.LENGTH_UNSET.toLong()
        }
        opened = true
        transferInitializing(dataSpec)
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val maxRead = if (bytesRemaining != C.LENGTH_UNSET.toLong() && bytesRemaining < length) {
            bytesRemaining.toInt()
        } else {
            length
        }
        val target = if (offset == 0) buffer else scratchBuffer(maxRead)
        // maxRead must be passed explicitly - target may be a scratch buffer left over (larger)
        // from a previous, bigger read, and smbj's 2-arg read() fills up to buffer.size, not the
        // caller's requested length, which previously overflowed the destination on the next copy.
        val read = readWithReconnect(target, maxRead)
        if (read <= 0) return C.RESULT_END_OF_INPUT
        if (offset != 0) System.arraycopy(target, 0, buffer, offset, read)
        position += read
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    /**
     * A stale/idle-closed SMB session surfaces two different ways from an otherwise-valid file
     * handle - observed happening within ~15s of no reads on this router's NAS share, much
     * sooner than a normal idle timeout, so a single retry isn't always enough. smbj's `Share.send`
     * re-checks `isConnected()` right before every request and throws the unchecked
     * [SMBRuntimeException] ("... has already been closed") if the session died in the window
     * between our own isConnected check and this read - that's not an [IOException], so it must be
     * caught separately or it skips the retry entirely and crashes playback. Retries up to
     * [MAX_RECONNECT_ATTEMPTS] times with a short backoff before giving up for real.
     */
    private fun readWithReconnect(target: ByteArray, maxRead: Int): Int {
        var raf = randomAccessFile ?: return C.RESULT_END_OF_INPUT
        var lastError: Exception? = null
        repeat(1 + MAX_RECONNECT_ATTEMPTS) { attempt ->
            try {
                return raf.read(target, position, 0, maxRead)
            } catch (e: Exception) {
                if (e !is IOException && e !is SMBRuntimeException) throw e
                lastError = e
                Log.w(TAG, "read failed at position=$position (attempt $attempt), reconnecting: ${e.message}")
                if (attempt < MAX_RECONNECT_ATTEMPTS) {
                    if (attempt > 0) Thread.sleep(RECONNECT_BACKOFF_MS)
                    factory.invalidate(sourceId)
                    // Do NOT close raf here - invalidate() just tore down the DiskShare this file
                    // handle belongs to, so raf.close() would send a protocol close over an
                    // already-closed share. smbj surfaces that synchronously as
                    // SMBRuntimeException("DiskShare has already been closed") - observed on-device
                    // to abort the whole process via a JNI CheckJNI violation rather than being a
                    // catchable Kotlin exception. The socket is already gone, so the server releases
                    // the handle on its own; just drop the reference.
                    raf = factory.openFile(sourceId, path)
                    randomAccessFile = raf
                }
            }
        }
        throw lastError!!
    }

    private fun scratchBuffer(size: Int): ByteArray {
        if (scratch.size < size) scratch = ByteArray(size)
        return scratch
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        // TEMP DEBUG (Avatar infinite-buffering investigation) - remove once diagnosed. Confirms
        // whether ExoPlayer ever gives up on / abandons this DataSource instance instead of
        // reading from it, vs. it just being held open indefinitely with nothing happening at all.
        Log.d(TAG, "TEMP close() called: position=$position opened=$opened")
        randomAccessFile?.let { runCatching { it.close() } }
        randomAccessFile = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    companion object {
        private const val TAG = "SmbDataSource"
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val RECONNECT_BACKOFF_MS = 300L
    }
}
