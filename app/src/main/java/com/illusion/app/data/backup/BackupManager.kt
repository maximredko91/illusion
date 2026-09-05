package com.illusion.app.data.backup

import android.content.Context
import com.illusion.app.data.local.entity.FavoriteEntity
import com.illusion.app.data.local.entity.WatchProgressEntity
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.repository.WatchProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ImportSummary(val favoritesCount: Int, val historyCount: Int, val pendingSources: List<BackupSource>)

class BackupManager(
    context: Context,
    private val smbSourceRepository: SmbSourceRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val libraryRepository: LibraryRepository
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val pendingStore = context.getSharedPreferences("pending_backup_restore", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    suspend fun buildPayload(): BackupPayload = mutex.withLock {
        val sources = smbSourceRepository.observeSources().first()
        suspend fun reference(stableId: String): BackupMediaRef? {
            val item = libraryRepository.getById(stableId) ?: return null
            val source = sources.find { it.id == item.sourceId } ?: return null
            return BackupMediaRef(
                BackupSource(source.displayName, source.host, source.share, source.rootPath, source.domain, source.username),
                item.filePath, item.sizeBytes
            )
        }
        val pending = readPending()
        val favorites = watchProgressRepository.observeFavorites().first().map {
            BackupFavorite(it.mediaItemStableId, it.addedAt, reference(it.mediaItemStableId))
        }
        val progress = watchProgressRepository.observeHistory().first().map {
            BackupWatchProgress(it.mediaItemStableId, it.positionMs, it.durationMs, it.watched, it.updatedAt, reference(it.mediaItemStableId))
        }
        BackupPayload(
            version = 2,
            sources = (sources.map { BackupSource(it.displayName, it.host, it.share, it.rootPath, it.domain, it.username) } + pending.sources).distinct(),
            favorites = (pending.favorites + favorites).distinctBy { it.media ?: it.stableId },
            watchProgress = (pending.watchProgress + progress).distinctBy { it.media ?: it.stableId }
        )
    }

    fun serialize(payload: BackupPayload): String = json.encodeToString(payload)

    fun parse(text: String): BackupPayload = json.decodeFromString<BackupPayload>(text).also {
        require(it.version in 1..2) { "Неподдерживаемая версия резервной копии" }
    }

    suspend fun restoreLocalData(payload: BackupPayload): ImportSummary = mutex.withLock {
        val previous = readPending()
        savePending(payload.copy(
            favorites = (payload.favorites + previous.favorites).distinctBy { it.media ?: it.stableId },
            watchProgress = (payload.watchProgress + previous.watchProgress).distinctBy { it.media ?: it.stableId },
            sources = (payload.sources + previous.sources).distinct()
        ))
        applyPendingLocked()
        val existing = smbSourceRepository.observeSources().first()
        val newSources = payload.sources.distinct().filterNot { backup ->
            existing.any { matchesSource(backup, it.host, it.share, it.rootPath, it.domain, it.username) }
        }
        ImportSummary(payload.favorites.size, payload.watchProgress.size, newSources)
    }

    /** Retry after scanning: the new device's generated stable IDs are now available. */
    suspend fun applyPendingRestore() = mutex.withLock { applyPendingLocked() }

    suspend fun clearPendingRestore() = mutex.withLock {
        savePending(BackupPayload(version = 2, sources = emptyList(), favorites = emptyList(), watchProgress = emptyList()))
    }

    private suspend fun applyPendingLocked() {
        val pending = readPending()
        if (pending.favorites.isEmpty() && pending.watchProgress.isEmpty()) return
        val sources = smbSourceRepository.observeSources().first()
        val items = sources.associate { it.id to libraryRepository.getBySource(it.id) }
        suspend fun resolve(stableId: String, ref: BackupMediaRef?): String? {
            // Old copies lack a portable reference. Keep them pending until the original ID appears.
            if (ref == null) return libraryRepository.getById(stableId)?.stableId
            val source = sources.find { matchesSource(ref.source, it.host, it.share, it.rootPath, it.domain, it.username) } ?: return null
            return items[source.id]?.find {
                (it.stableId == stableId || it.filePath.replace('/', '\\') == ref.filePath.replace('/', '\\')) && it.sizeBytes == ref.sizeBytes
            }?.stableId
        }
        val remainingFavorites = mutableListOf<BackupFavorite>()
        for (entry in pending.favorites) {
            val id = resolve(entry.stableId, entry.media)
            if (id == null) remainingFavorites += entry
            else watchProgressRepository.restoreFavorites(listOf(FavoriteEntity(id, entry.addedAt)))
        }
        val remainingProgress = mutableListOf<BackupWatchProgress>()
        for (entry in pending.watchProgress) {
            val id = resolve(entry.stableId, entry.media)
            if (id == null) remainingProgress += entry
            else {
                val current = watchProgressRepository.getProgress(id)
                if (current == null || current.updatedAt <= entry.updatedAt) {
                    watchProgressRepository.restoreProgress(listOf(WatchProgressEntity(id, entry.positionMs, entry.durationMs, entry.watched, entry.updatedAt)))
                }
            }
        }
        savePending(pending.copy(
            sources = (remainingFavorites.mapNotNull { it.media?.source } + remainingProgress.mapNotNull { it.media?.source }).distinct(),
            favorites = remainingFavorites, watchProgress = remainingProgress
        ))
    }

    private suspend fun readPending(): BackupPayload = withContext(Dispatchers.IO) {
        pendingStore.getString("payload", null)?.let(::parse)
            ?: BackupPayload(version = 2, sources = emptyList(), favorites = emptyList(), watchProgress = emptyList())
    }

    private suspend fun savePending(payload: BackupPayload) = withContext(Dispatchers.IO) {
        check(pendingStore.edit().putString("payload", serialize(payload)).commit()) { "Не удалось сохранить импорт" }
    }
}

internal fun matchesSource(backup: BackupSource, host: String, share: String, rootPath: String, domain: String, username: String): Boolean =
    backup.host.equals(host, ignoreCase = true) && backup.share.equals(share, ignoreCase = true) &&
        backup.rootPath.replace('/', '\\').trim('\\') == rootPath.replace('/', '\\').trim('\\') &&
        backup.domain.equals(domain, ignoreCase = true) && backup.username.equals(username, ignoreCase = true)
