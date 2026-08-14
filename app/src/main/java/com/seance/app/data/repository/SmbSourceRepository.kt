package com.seance.app.data.repository

import com.seance.app.data.local.dao.SmbSourceDao
import com.seance.app.data.local.entity.SmbSourceEntity
import com.seance.app.data.smb.SmbClient
import com.seance.app.data.smb.SmbConnectionInfo
import com.seance.app.data.smb.SmbCredentialStore
import kotlinx.coroutines.flow.Flow

class SmbSourceRepository(
    private val dao: SmbSourceDao,
    private val credentialStore: SmbCredentialStore,
    private val smbClient: SmbClient
) {
    fun observeSources(): Flow<List<SmbSourceEntity>> = dao.observeAll()

    suspend fun getById(id: Long): SmbSourceEntity? = dao.getById(id)

    suspend fun getEnabledSources(): List<SmbSourceEntity> = dao.getEnabled()

    suspend fun addSource(source: SmbSourceEntity, password: String): Long {
        val id = dao.upsert(source)
        credentialStore.setPassword(id, password)
        return id
    }

    suspend fun updateSource(source: SmbSourceEntity, password: String?) {
        dao.update(source)
        if (password != null) credentialStore.setPassword(source.id, password)
    }

    suspend fun deleteSource(source: SmbSourceEntity) {
        dao.delete(source)
        credentialStore.removePassword(source.id)
    }

    suspend fun connectionInfoById(sourceId: Long): SmbConnectionInfo? {
        val source = dao.getById(sourceId) ?: return null
        return connectionInfo(source)
    }

    suspend fun connectionInfo(source: SmbSourceEntity): SmbConnectionInfo? {
        val password = credentialStore.getPassword(source.id) ?: return null
        return SmbConnectionInfo(
            host = source.host,
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
