package com.illusion.app.data.repository

import com.illusion.app.data.local.dao.SmbSourceDao
import com.illusion.app.data.local.entity.SmbSourceEntity
import com.illusion.app.data.smb.MissingSmbCredentialException
import com.illusion.app.data.smb.SmbClient
import com.illusion.app.data.smb.SmbConnectionInfo
import com.illusion.app.data.smb.SmbCredentialStore
import com.illusion.app.data.smb.chooseReachableHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SmbSourceRepository(
    private val dao: SmbSourceDao,
    private val credentialStore: SmbCredentialStore,
    private val smbClient: SmbClient,
    private val libraryRepository: LibraryRepository
) {
    fun observeSources(): Flow<List<SmbSourceEntity>> = dao.observeAll()

    suspend fun getById(id: Long): SmbSourceEntity? = dao.getById(id)

    suspend fun getEnabledSources(): List<SmbSourceEntity> = dao.getEnabled()

    /** Toggles whether [source] is included the next time [scanAll][com.illusion.app.data.scan.LibraryScanner.scanAll] runs - lets the user pick which of several connected SMB sources actually contribute to the library, without deleting/re-adding a source's saved connection details. Отключённый источник не сканируется, и его элементы скрыты со всех экранов библиотеки - запросы в [com.illusion.app.data.local.dao.MediaItemDao] отбирают только элементы включённых источников. Из базы они при этом никуда не деваются: включили обратно - вернулись вместе с прогрессом и избранным. Удаляет их насовсем только [deleteSource]. */
    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun addSource(source: SmbSourceEntity, password: String): Long {
        val id = dao.upsert(source)
        credentialStore.setPassword(id, password)
        return id
    }

    suspend fun updateSource(source: SmbSourceEntity, password: String?) {
        dao.update(source)
        if (password != null) credentialStore.setPassword(source.id, password)
    }

    /** Удаляет и всё, что этот источник внёс в библиотеку - иначе его элементы остаются навсегда: открыть их нечем, а почистить нечем тем более (см. [LibraryRepository.deleteBySource]). */
    suspend fun deleteSource(source: SmbSourceEntity) {
        libraryRepository.deleteBySource(source.id)
        dao.delete(source)
        credentialStore.removePassword(source.id)
    }

    /** Factory reset - removes every source and its stored credential, one at a time so each credential is cleaned up too (a bulk DELETE on the sources table alone would leave orphaned SmbCredentialStore entries behind). */
    suspend fun deleteAllSources() {
        observeSources().first().forEach { deleteSource(it) }
    }

    suspend fun connectionInfoById(sourceId: Long): SmbConnectionInfo? {
        val source = dao.getById(sourceId) ?: return null
        return connectionInfo(source)
    }

    /** Whether [source] has a saved password entry at all - not whether it's correct. False here means [connectionInfo] will throw [MissingSmbCredentialException] rather than attempt a connection. */
    fun hasStoredPassword(sourceId: Long): Boolean = credentialStore.getPassword(sourceId) != null

    suspend fun connectionInfo(source: SmbSourceEntity): SmbConnectionInfo? {
        val password = credentialStore.getPassword(source.id)
            ?: throw MissingSmbCredentialException(source.displayName)
        return SmbConnectionInfo(
            host = chooseReachableHost(source.host, source.alternateHosts, smbClient.localIpv4()),
            share = source.share,
            rootPath = source.rootPath,
            domain = source.domain,
            username = source.username,
            password = password
        )
    }

    suspend fun testConnection(source: SmbSourceEntity, password: String): Result<Unit> {
        val info = SmbConnectionInfo(
            host = source.host,
            share = source.share,
            rootPath = source.rootPath,
            domain = source.domain,
            username = source.username,
            password = password
        )
        return smbClient.testConnection(info)
    }
}
