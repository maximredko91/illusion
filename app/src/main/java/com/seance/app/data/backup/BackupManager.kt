package com.seance.app.data.backup

import com.seance.app.data.local.entity.FavoriteEntity
import com.seance.app.data.local.entity.WatchProgressEntity
import com.seance.app.data.repository.SmbSourceRepository
import com.seance.app.data.repository.WatchProgressRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ImportSummary(val favoritesCount: Int, val historyCount: Int, val pendingSources: List<BackupSource>)

class BackupManager(
    private val smbSourceRepository: SmbSourceRepository,
    private val watchProgressRepository: WatchProgressRepository
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun buildPayload(): BackupPayload {
        val sources = smbSourceRepository.observeSources().first().map {
            BackupSource(it.displayName, it.host, it.share, it.rootPath, it.domain, it.username)
        }
        val favorites = watchProgressRepository.observeFavorites().first().map {
            BackupFavorite(it.mediaItemStableId, it.addedAt)
        }
        val progress = watchProgressRepository.observeHistory().first().map {
            BackupWatchProgress(it.mediaItemStableId, it.positionMs, it.durationMs, it.watched, it.updatedAt)
        }
        return BackupPayload(sources = sources, favorites = favorites, watchProgress = progress)
    }

    fun serialize(payload: BackupPayload): String = json.encodeToString(payload)

    fun parse(text: String): BackupPayload = json.decodeFromString(text)

    /** Restores favorites/watch history unconditionally (idempotent - just upserts), and returns the sources that don't already exist (matched by host+share) so the caller can prompt for a password before creating each one. */
    suspend fun restoreLocalData(payload: BackupPayload): ImportSummary {
        watchProgressRepository.restoreFavorites(payload.favorites.map { FavoriteEntity(it.stableId, it.addedAt) })
        watchProgressRepository.restoreProgress(
            payload.watchProgress.map { WatchProgressEntity(it.stableId, it.positionMs, it.durationMs, it.watched, it.updatedAt) }
        )
        val existing = smbSourceRepository.observeSources().first()
        val newSources = payload.sources.filterNot { backup ->
            existing.any { it.host == backup.host && it.share == backup.share }
        }
        return ImportSummary(payload.favorites.size, payload.watchProgress.size, newSources)
    }
}
