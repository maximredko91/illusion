package com.seance.app.data.scan

import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.data.local.entity.SmbSourceEntity
import com.seance.app.data.nfo.NfoMetadata
import com.seance.app.data.nfo.NfoParser
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.data.repository.SmbSourceRepository
import com.seance.app.data.smb.SmbClient
import com.seance.app.data.smb.SmbConnection
import com.seance.app.data.smb.SmbConnectionInfo
import com.seance.app.data.smb.SmbFileRef
import com.seance.app.data.smb.StableIdGenerator
import com.seance.app.data.smb.baseName
import com.seance.app.data.smb.isImage
import com.seance.app.data.smb.isSubtitle
import com.seance.app.data.smb.isVideo
import com.seance.app.domain.model.Category
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Walks every enabled SMB source, matches each video against a sibling .nfo file
 * and upserts the result into the library index. A missing/corrupt .nfo never blocks
 * a file from being indexed - it just falls back to the file name as the title.
 *
 * Both the directory walk and the per-video .nfo lookup are pure network latency (one SMB round
 * trip each), so both fan out across a small pool of connections instead of going one request at
 * a time - a share with thousands of files/folders was otherwise the slow, silent part of a scan.
 */
class LibraryScanner(
    private val sourceRepository: SmbSourceRepository,
    private val libraryRepository: LibraryRepository,
    private val smbClient: SmbClient,
    private val nfoParser: NfoParser
) {
    /** Returns the total number of video files indexed across all sources, for a completion summary. */
    suspend fun scanAll(onProgress: suspend (ScanProgress) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        val sources = sourceRepository.getEnabledSources()
        sources.foldIndexed(0) { index, total, source ->
            total + (runCatching { scanSource(source, index, sources.size, onProgress) }.getOrDefault(0))
        }
    }

    private suspend fun scanSource(
        source: SmbSourceEntity,
        sourceIndex: Int,
        sourceCount: Int,
        onProgress: suspend (ScanProgress) -> Unit
    ): Int {
        val info = sourceRepository.connectionInfo(source) ?: return 0
        return withConnectionPool(info) { pool ->
            val discovered = AtomicInteger(0)
            val allFiles = walkConcurrent(pool, source.rootPath, discovered) { count ->
                // Throttled - the underlying WorkManager progress update is a DB write per call,
                // and a large share can discover thousands of files during this phase alone.
                if (count % FILE_DISCOVERY_PROGRESS_STEP == 0) {
                    onProgress(
                        ScanProgress(
                            sourceIndex = sourceIndex,
                            sourceCount = sourceCount,
                            currentSourceName = source.displayName,
                            filesScanned = count,
                            filesTotal = ScanProgress.TOTAL_UNKNOWN
                        )
                    )
                }
            }
            val videoFiles = allFiles.filter { it.isVideo }
            val subtitleFiles = allFiles.filter { it.isSubtitle }
            val imageFiles = allFiles.filter { it.isImage }

            val indexed = AtomicInteger(0)
            // Kodi's per-episode .nfo (episodedetails) rarely carries genre/year - those live only
            // in the show-root tvshow.nfo - so fetch it once per show (not once per episode) and
            // reuse across every episode of the same series within this scan.
            val showNfoCache = ConcurrentHashMap<String, NfoMetadata?>()
            val items = coroutineScope {
                videoFiles.map { file ->
                    async {
                        val item = withConnection(pool) { connection ->
                            toMediaItem(source, connection, file, subtitleFiles, imageFiles, showNfoCache)
                        }
                        onProgress(
                            ScanProgress(
                                sourceIndex = sourceIndex,
                                sourceCount = sourceCount,
                                currentSourceName = source.displayName,
                                filesScanned = indexed.incrementAndGet(),
                                filesTotal = videoFiles.size
                            )
                        )
                        item
                    }
                }.awaitAll()
            }
            libraryRepository.upsertAll(items)
            items.size
        }
    }

    /** Opens [CONNECTION_POOL_SIZE] connections up front and closes all of them once [block] returns. */
    private suspend fun <T> withConnectionPool(info: SmbConnectionInfo, block: suspend (Channel<SmbConnection>) -> T): T {
        val connections = List(CONNECTION_POOL_SIZE) { smbClient.connect(info) }
        val pool = Channel<SmbConnection>(CONNECTION_POOL_SIZE)
        connections.forEach { pool.trySend(it) }
        try {
            return block(pool)
        } finally {
            connections.forEach { it.close() }
        }
    }

    /** Borrows one connection from [pool] for [block], returning it afterwards - never touches a connection concurrently from two coroutines. */
    private suspend fun <T> withConnection(pool: Channel<SmbConnection>, block: suspend (SmbConnection) -> T): T {
        val connection = pool.receive()
        try {
            return block(connection)
        } finally {
            pool.send(connection)
        }
    }

    /** Recursively lists [path], fanning sibling sub-directories out across [pool]'s connections instead of one at a time. */
    private suspend fun walkConcurrent(
        pool: Channel<SmbConnection>,
        path: String,
        discovered: AtomicInteger,
        onProgress: suspend (Int) -> Unit
    ): List<SmbFileRef> = coroutineScope {
        val listing = withConnection(pool) { it.listDirectory(path) }
        listing.files.forEach { onProgress(discovered.incrementAndGet()) }
        val nested = listing.directoryPaths
            .map { childPath -> async { walkConcurrent(pool, childPath, discovered, onProgress) } }
            .awaitAll()
        listing.files + nested.flatten()
    }

    private fun toMediaItem(
        source: SmbSourceEntity,
        connection: SmbConnection,
        file: SmbFileRef,
        subtitleFiles: List<SmbFileRef>,
        imageFiles: List<SmbFileRef>,
        showNfoCache: ConcurrentHashMap<String, NfoMetadata?>
    ): MediaItemEntity {
        val (headBytes, tailBytes) = connection.readEdgeSamples(file.path, file.sizeBytes, STABLE_ID_SAMPLE_BYTES)
        val stableId = StableIdGenerator.forFile(source.id, file.sizeBytes, headBytes, tailBytes)
        val nfoPath = file.path.substringBeforeLast('.', file.path) + ".nfo"
        val metadata = if (connection.fileExists(nfoPath)) {
            connection.openInputStream(nfoPath).use { nfoParser.parse(it) }
        } else {
            null
        }
        val baseName = file.name.substringBeforeLast('.')
        val videoFolder = file.path.substringBeforeLast('\\')
        val subtitlePaths = subtitleFiles
            .filter { it.path.substringBeforeLast('\\') == videoFolder }
            .filter { it.name.startsWith("$baseName.") }
            .map { it.path }
        val categoryMatch = categorize(file.path)
        val category = categoryMatch.category
        // Group by the show's own folder (one level under whichever folder matched the category),
        // not just the immediate parent - shows are commonly split into per-season subfolders
        // (.../Сериалы/ShowName/Сезон 1/...), and grouping by immediate parent alone would treat
        // each season as a separate "series".
        val seriesStableId = if (category == Category.TV_SHOWS || category == Category.CARTOON_SERIES) {
            val segments = file.path.split('\\')
            val showFolderIndex = categoryMatch.categoryFolderIndex + 1
            if (showFolderIndex < segments.size - 1) {
                "${source.id}|${segments.take(showFolderIndex + 1).joinToString("\\")}"
            } else {
                null
            }
        } else {
            null
        }
        // Look for poster/fanart next to the video first (how movies are usually organized), then
        // fall back to the show's own root folder - a season subfolder rarely has its own poster,
        // that normally sits at .../Сериалы/ShowName/poster.jpg, one level above "Сезон N".
        val showFolder = seriesStableId?.substringAfter('|')
        // Fallback for fields Kodi/tinyMediaManager write once at the show level (tvshow.nfo)
        // rather than repeating in every episode's own .nfo - genre and year most commonly,
        // sometimes rating/country/plot too. Without this, series categories end up with no
        // genre/year on any item, which silently disables the Library genre/year filter chips
        // (they only render when at least one item in the category has a value).
        val showMetadata = showFolder?.let { fetchShowNfo(connection, it, showNfoCache) }
        val imageSearchFolders = listOfNotNull(videoFolder, showFolder).distinct()
        // Beyond a plain "poster.jpg"/"fanart.jpg", also match Kodi/tinyMediaManager's
        // "<video name>-fanart.jpg" convention and numbered extrafanart variants like
        // "fanart1.jpg"/"fanart-02.jpg" - picking the lowest-numbered/first alphabetically when
        // several exist, so the choice is at least deterministic across rescans.
        fun findImage(names: Set<String>): SmbFileRef? {
            fun matches(ref: SmbFileRef): Boolean {
                val refBase = ref.baseName.lowercase()
                if (refBase in names) return true
                if (names.any { refBase == "${baseName.lowercase()}-$it" }) return true
                return names.any { name ->
                    refBase.startsWith(name) && refBase.length > name.length &&
                        refBase.substring(name.length).all { it.isDigit() || it == '-' || it == '_' }
                }
            }
            return imageSearchFolders.firstNotNullOfOrNull { folder ->
                imageFiles
                    .filter { it.path.substringBeforeLast('\\') == folder && matches(it) }
                    .minByOrNull { it.name }
            }
        }
        // Radarr/Sonarr-style libraries drop poster.jpg/fanart.jpg as real files rather than
        // pointing <thumb> at something reachable over SMB - prefer those; a <thumb> value is only
        // usable as a fallback when it's a real scraper URL, not a path from the machine that wrote the .nfo.
        val posterPath = findImage(setOf("poster", "folder", "cover"))?.path
            ?: metadata?.posterUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        val fanartPath = findImage(setOf("fanart", "backdrop", "background"))?.path
            ?: metadata?.fanartUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

        return MediaItemEntity(
            stableId = stableId,
            sourceId = source.id,
            filePath = file.path,
            category = category,
            title = metadata?.title ?: baseName,
            originalTitle = metadata?.originalTitle,
            year = metadata?.year ?: showMetadata?.year,
            genres = metadata?.genres?.takeIf { it.isNotEmpty() } ?: showMetadata?.genres ?: emptyList(),
            rating = metadata?.rating ?: showMetadata?.rating,
            country = metadata?.country ?: showMetadata?.country,
            runtimeMinutes = metadata?.runtimeMinutes,
            plot = metadata?.plot,
            director = metadata?.director ?: emptyList(),
            actors = metadata?.actors ?: emptyList(),
            actorRoles = metadata?.actorRoles ?: emptyList(),
            collectionName = metadata?.collectionName,
            posterPath = posterPath,
            fanartPath = fanartPath,
            seasonNumber = metadata?.season,
            episodeNumber = metadata?.episode,
            seriesStableId = seriesStableId,
            hasNfo = metadata != null,
            dateAdded = System.currentTimeMillis(),
            sizeBytes = file.sizeBytes,
            subtitlePaths = subtitlePaths,
            mpaa = metadata?.mpaa ?: showMetadata?.mpaa,
            tagline = metadata?.tagline,
            studio = metadata?.studio ?: showMetadata?.studio,
            premiered = metadata?.premiered,
            imdbId = metadata?.imdbId ?: showMetadata?.imdbId,
            tmdbId = metadata?.tmdbId ?: showMetadata?.tmdbId
        )
    }

    /** Parses [showFolder]'s tvshow.nfo once and caches the result (including a miss) for the rest of this scan. */
    private fun fetchShowNfo(
        connection: SmbConnection,
        showFolder: String,
        cache: ConcurrentHashMap<String, NfoMetadata?>
    ): NfoMetadata? = cache.computeIfAbsent(showFolder) {
        val path = "$showFolder\\tvshow.nfo"
        if (connection.fileExists(path)) {
            connection.openInputStream(path).use { nfoParser.parse(it) }
        } else {
            null
        }
    }

    private data class CategoryMatch(val category: Category, val categoryFolderIndex: Int)

    /**
     * Scans every path segment (not just the first) for a category keyword, since the category
     * folder isn't necessarily the top of the share - e.g. "Movies\Сериалы\ShowName\Сезон 1\...".
     * "мультсериал" is checked before the plain "сериал" match since it contains "сериал" as a
     * substring and would otherwise always be misread as a TV show folder.
     */
    private fun categorize(path: String): CategoryMatch {
        val segments = path.split('\\').map { it.lowercase() }
        segments.indexOfFirst { "мультсериал" in it }
            .takeIf { it >= 0 }
            ?.let { return CategoryMatch(Category.CARTOON_SERIES, it) }
        segments.indexOfFirst { "сериал" in it || it == "tv" || "tv shows" in it }
            .takeIf { it >= 0 }
            ?.let { return CategoryMatch(Category.TV_SHOWS, it) }
        segments.indexOfFirst { "мультфильм" in it || "cartoon" in it }
            .takeIf { it >= 0 }
            ?.let { return CategoryMatch(Category.CARTOONS, it) }
        return CategoryMatch(Category.MOVIES, -1)
    }

    companion object {
        private const val FILE_DISCOVERY_PROGRESS_STEP = 20

        // Each connection is only ever touched by one coroutine at a time (borrowed from this
        // pool), which sidesteps smbj's DiskShare not being documented as thread-safe - real
        // concurrency comes from having several independent connections, not sharing one.
        private const val CONNECTION_POOL_SIZE = 6

        private const val STABLE_ID_SAMPLE_BYTES = 65536
    }
}
