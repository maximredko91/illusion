package com.illusion.app.data.repository

import android.content.Context
import android.net.Uri
import com.illusion.app.data.download.DownloadStorage
import com.illusion.app.data.local.dao.DownloadDao
import com.illusion.app.data.local.entity.DownloadEntity
import com.illusion.app.data.local.entity.DownloadStatus
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.smb.VIDEO_EXTENSIONS
import com.illusion.app.domain.model.Category
import com.illusion.app.work.DownloadWorker
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

class DownloadRepository(private val context: Context, private val dao: DownloadDao) {
    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()

    fun observeForItem(stableId: String): Flow<DownloadEntity?> = dao.observeForItem(stableId)

    suspend fun getForItem(stableId: String): DownloadEntity? = dao.getForItem(stableId)

    suspend fun upsert(download: DownloadEntity) = dao.upsert(download)

    /** Deletes the DB row and the downloaded video + subtitle files themselves - the two must never drift apart or a stale row would point at nothing. */
    suspend fun remove(stableId: String) {
        dao.getForItem(stableId)?.let { entity ->
            runCatching { DownloadStorage.delete(context, Uri.parse(entity.contentUri)) }
            entity.subtitles.forEach { sub -> runCatching { DownloadStorage.delete(context, Uri.parse(sub.uri)) } }
        }
        dao.delete(stableId)
    }

    /** Total on-disk size of every completed/in-progress download, for the Settings cache summary. */
    suspend fun totalSizeBytes(): Long = dao.getAllOnce().sumOf { it.downloadedBytes }

    suspend fun removeAll() {
        dao.getAllOnce().forEach { remove(it.stableId) }
    }

    /**
     * Finds video files under [treeUri] (a folder the user just picked via the system folder
     * picker - see the "Восстановить загрузки" action in Settings) with no matching `downloads`
     * row - the app's data was cleared, or it was reinstalled, while the downloaded files
     * themselves survived (both wipe Room; neither touches the actual downloaded files). For
     * each one found, inserts a synthetic, minimal [MediaItemEntity] (title/year parsed from the
     * per-title folder name DownloadWorker itself created, `isOrphanedDownload = true` so it never
     * shows up in the ordinary library) plus a matching completed [DownloadEntity], so it reappears
     * in the Downloads screen and is playable again exactly like any other completed download.
     *
     * Manual and folder-picker-driven rather than an automatic startup scan of the default
     * Downloads/Illusion location - see [DownloadStorage.listFilesUnderTree]'s own KDoc for why an
     * automatic MediaStore-based scan doesn't actually work for this. Returns how many files were
     * recovered, for the caller to show a result message. Deliberately a no-op for anything already
     * tracked (existing `contentUri`s are skipped) - only ever adds rows, never removes or overwrites.
     */
    suspend fun recoverOrphanedDownloads(libraryRepository: LibraryRepository, treeUri: Uri): Int {
        val known = dao.getAllOnce().map { it.contentUri }.toSet()
        val now = System.currentTimeMillis()
        var recovered = 0
        val allFiles = DownloadStorage.listFilesUnderTree(context, treeUri)
        // poster.jpg/fanart.jpg siblings DownloadWorker.downloadImages wrote next to the video, if
        // any - the only way a recovered item (sourceId=-1, no real SMB source to fetch a live
        // image from, see MediaItemEntity's own posterModel/fanartModel) can still show one.
        // Grouped by parent folder name, same key parseTitleYear below already relies on.
        val imagesByFolder = allFiles
            .filter { it.parentFolderName != null }
            .groupBy { it.parentFolderName!! }
            .mapValues { (_, files) ->
                val poster = files.firstOrNull { it.displayName.equals(DownloadWorker.POSTER_FILE_NAME, ignoreCase = true) }?.uri
                val fanart = files.firstOrNull { it.displayName.equals(DownloadWorker.FANART_FILE_NAME, ignoreCase = true) }?.uri
                poster to fanart
            }
        allFiles
            .filter { it.uri.toString() !in known }
            .filter { it.displayName.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS }
            .forEach { file ->
                recovered++
                val stableId = "recovered:" + sha256(file.uri.toString())
                val (title, year) = parseTitleYear(file.parentFolderName, file.displayName)
                val (posterUri, fanartUri) = imagesByFolder[file.parentFolderName] ?: (null to null)
                libraryRepository.upsertAll(
                    listOf(
                        MediaItemEntity(
                            stableId = stableId,
                            sourceId = -1L,
                            filePath = file.uri.toString(),
                            category = Category.MOVIES,
                            title = title,
                            originalTitle = null,
                            year = year,
                            genres = emptyList(),
                            rating = null,
                            country = null,
                            runtimeMinutes = null,
                            plot = null,
                            director = emptyList(),
                            actors = emptyList(),
                            collectionName = null,
                            posterPath = posterUri?.toString(),
                            fanartPath = fanartUri?.toString(),
                            seasonNumber = null,
                            episodeNumber = null,
                            seriesStableId = null,
                            dateAdded = now,
                            sizeBytes = file.sizeBytes,
                            subtitlePaths = emptyList(),
                            isOrphanedDownload = true
                        )
                    )
                )
                dao.upsert(
                    DownloadEntity(
                        stableId = stableId,
                        contentUri = file.uri.toString(),
                        status = DownloadStatus.COMPLETED,
                        totalBytes = file.sizeBytes,
                        downloadedBytes = file.sizeBytes,
                        updatedAt = now,
                        posterUri = posterUri?.toString(),
                        fanartUri = fanartUri?.toString()
                    )
                )
            }
        return recovered
    }

    /** "Movie Title (2020)" -> ("Movie Title", 2020); anything else (a series' "Сезон N" subfolder, no folder at all) falls back to the bare filename with no year - series folders are named from the show's own stableId fragment (see DownloadWorker.folderSegments), not a readable title, so there's nothing better to parse there. */
    private fun parseTitleYear(folderName: String?, fileName: String): Pair<String, Int?> {
        val match = folderName?.let { TITLE_YEAR_PATTERN.matchEntire(it) }
        return if (match != null) {
            match.groupValues[1] to match.groupValues[2].toIntOrNull()
        } else {
            (folderName ?: fileName.substringBeforeLast('.')) to null
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        private val TITLE_YEAR_PATTERN = Regex("^(.+) \\((\\d{4})\\)$")
    }
}
