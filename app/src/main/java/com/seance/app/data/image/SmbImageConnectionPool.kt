package com.seance.app.data.image

import com.seance.app.data.repository.SmbSourceRepository
import com.seance.app.data.smb.SmbClient
import com.seance.app.data.smb.SmbConnection
import com.seance.app.data.smb.SmbConnectionInfo
import kotlinx.coroutines.channels.Channel
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
        pools.getOrPut(sourceId) {
            val info = connectionInfos.getOrPut(sourceId) {
                sourceRepository.connectionInfoById(sourceId) ?: error("Unknown or unconfigured SMB source $sourceId")
            }
            Channel<SmbConnection>(POOL_SIZE_PER_SOURCE).apply {
                repeat(POOL_SIZE_PER_SOURCE) { trySend(smbClient.connect(info)) }
            }
        }
    }

    /**
     * A pooled connection left idle long enough can be closed server-side (SMB session timeout) -
     * the app only finds out when a request on it fails. On any failure, replace the connection
     * with a fresh one and retry once instead of leaving every subsequent poster load on this
     * source broken until the app restarts.
     */
    suspend fun <T> withConnection(sourceId: Long, block: suspend (SmbConnection) -> T): T {
        val pool = poolFor(sourceId)
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
        private const val POOL_SIZE_PER_SOURCE = 3
    }
}
