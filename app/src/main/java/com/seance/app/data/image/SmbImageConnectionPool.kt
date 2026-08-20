package com.seance.app.data.image

import com.seance.app.data.repository.SmbSourceRepository
import com.seance.app.data.smb.SmbClient
import com.seance.app.data.smb.SmbConnection
import com.seance.app.data.smb.SmbConnectionInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A small pool of long-lived connections per SMB source, reused across poster/fanart fetches so
 * loading a grid of images doesn't open a fresh TCP+auth handshake per thumbnail. Each connection
 * is only ever borrowed by one coroutine at a time (via the Channel), sidestepping smbj's
 * DiskShare not being documented as safe for concurrent access from multiple threads.
 */
class SmbImageConnectionPool(
    private val sourceRepository: SmbSourceRepository,
    private val smbClient: SmbClient
) {
    private val mutex = Mutex()
    private val pools = mutableMapOf<Long, Channel<SmbConnection>>()
    private val connectionInfos = mutableMapOf<Long, SmbConnectionInfo>()

    private suspend fun poolFor(sourceId: Long): Channel<SmbConnection> = mutex.withLock {
        pools[sourceId]?.let { return@withLock it }

        val info = connectionInfos.getOrPut(sourceId) {
            sourceRepository.connectionInfoById(sourceId) ?: error("Unknown or unconfigured SMB source $sourceId")
        }
        val channel = Channel<SmbConnection>(POOL_SIZE_PER_SOURCE)
        var opened = 0
        repeat(POOL_SIZE_PER_SOURCE) {
            connectWithRetry(info)?.let { connection ->
                channel.trySend(connection)
                opened++
            }
        }
        // A NAS that briefly can't keep up with a burst of new SMB sessions (observed on-device
        // against this app's router-based NAS, even with a healthy connection) can fail some of
        // the connects above - as long as at least one made it through, a smaller-than-requested
        // pool still serves every request, just with less parallelism. Only truly out-of-reach
        // sources should surface as a hard failure.
        check(opened > 0) { "Could not open any SMB connection for source $sourceId" }
        pools[sourceId] = channel
        channel
    }

    /** Retries with a short backoff between tries - an immediate retry lands in the same burst that caused the first failure. */
    private suspend fun connectWithRetry(info: SmbConnectionInfo): SmbConnection? {
        repeat(CONNECT_ATTEMPTS) { attempt ->
            try {
                return smbClient.connect(info)
            } catch (e: Exception) {
                if (attempt < CONNECT_ATTEMPTS - 1) delay(CONNECT_RETRY_BACKOFF_MS)
            }
        }
        return null
    }

    /**
     * A pooled connection left idle long enough can be closed server-side (SMB session timeout) -
     * the app only finds out when a request on it fails. On any failure, replace the connection
     * with a fresh one and retry once instead of leaving every subsequent poster load on this
     * source broken until the app restarts.
     */
    suspend fun <T> withConnection(sourceId: Long, block: suspend (SmbConnection) -> T): T {
        val pool = try {
            poolFor(sourceId)
        } catch (e: Exception) {
            // The whole burst of pool connections failed (NAS-side transient overload, seen
            // on-device) - rather than failing this one request outright, serve it off a single
            // standalone connection instead of the pool. Same retry budget as the pool build
            // itself: this is often the very first request racing the pool's own cold-start
            // burst, so it deserves the same chances to ride out the NAS's transient hiccup.
            val connection = connectWithRetry(connectionInfos.getValue(sourceId))
                ?: error("Could not open any SMB connection for source $sourceId")
            return try {
                block(connection)
            } finally {
                runCatching { connection.close() }
            }
        }
        var connection = pool.receive()
        try {
            return try {
                block(connection)
            } catch (e: Exception) {
                runCatching { connection.close() }
                connection = smbClient.connect(connectionInfos.getValue(sourceId))
                block(connection)
            }
        } finally {
            pool.send(connection)
        }
    }

    companion object {
        // Grid screens request every visible poster's image at once in layout order (top-left to
        // bottom-right) - too small a pool serializes them into a visible staggered/diagonal
        // pop-in as each fetch finishes. 6 lets a full row or more load in parallel while still
        // bounding how many concurrent SMB reads one screen can put on the NAS.
        private const val POOL_SIZE_PER_SOURCE = 6
        private const val CONNECT_ATTEMPTS = 3
        private const val CONNECT_RETRY_BACKOFF_MS = 250L
    }
}
