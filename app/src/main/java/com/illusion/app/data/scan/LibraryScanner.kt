package com.illusion.app.data.scan

import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.local.entity.SmbSourceEntity
import com.illusion.app.data.nfo.NfoMetadata
import com.illusion.app.data.nfo.NfoParser
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.smb.SmbClient
import com.illusion.app.data.smb.SmbConnection
import com.illusion.app.data.smb.SmbConnectionInfo
import com.illusion.app.data.smb.SmbFileRef
import com.illusion.app.data.smb.StableIdGenerator
import com.illusion.app.data.smb.baseName
import com.illusion.app.data.smb.classifySmbError
import com.illusion.app.data.smb.isImage
import com.illusion.app.data.smb.isSubtitle
import com.illusion.app.data.smb.isTrailer
import com.illusion.app.data.smb.isVideo
import com.illusion.app.domain.model.Category
import java.io.IOException
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
data class ScanResult(val totalIndexed: Int, val sourceErrors: List<SourceScanError>)
data class SourceScanError(val sourceName: String, val message: String)

class LibraryScanner(
    private val sourceRepository: SmbSourceRepository,
    private val libraryRepository: LibraryRepository,
    private val smbClient: SmbClient,
    private val nfoParser: NfoParser
) {
    /**
     * Scans every enabled source, tolerating a single source being unreachable (bad
     * credentials, network down, ...) rather than aborting the whole scan - the other sources
     * still get indexed. Previously each source's failure was silently swallowed
     * (`runCatching { }.getOrDefault(0)`) with no trace of what happened or which source;
     * now it's classified via [classifySmbError] (the same mapping [SmbClient.testConnection]
     * uses) and returned so the caller can surface it instead of a generic "scan failed".
     */
    /**
     * [force] skips the per-file "unchanged since last scan" fast path (see [toMediaItem]) even
     * when neither the video's own size/lastModified nor the sidecar .nfo's lastModified changed -
     * a normal rescan only ever re-hashes/re-parses files that actually changed on the NAS, which
     * means a metadata field added to this app's own parsing (e.g. MediaItemEntity.tags) stays
     * empty for every already-indexed file forever, since nothing about those files themselves
     * ever changes just because the app now reads one more tag from the same .nfo. Confirmed via a
     * real on-device DB check: a full manual rescan indexed all 3233 items and populated zero
     * tags, because every single one took the unchanged fast path. This is the deliberate escape
     * hatch for that - re-parses every .nfo regardless of whether anything on disk moved.
     */
    suspend fun scanAll(force: Boolean = false, onProgress: suspend (ScanProgress) -> Unit = {}): ScanResult = withContext(Dispatchers.IO) {
        val sources = sourceRepository.getEnabledSources()
        val errors = mutableListOf<SourceScanError>()
        val total = sources.foldIndexed(0) { index, sum, source ->
            val outcome = runCatching { scanSource(source, index, sources.size, force, onProgress) }
            outcome.exceptionOrNull()?.let { errors += SourceScanError(source.displayName, classifySmbError(it)) }
            sum + outcome.getOrDefault(0)
        }
        ScanResult(totalIndexed = total, sourceErrors = errors)
    }

    private suspend fun scanSource(
        source: SmbSourceEntity,
        sourceIndex: Int,
        sourceCount: Int,
        force: Boolean,
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
            // A trailer file matches isVideo too - carve it out first so it's never indexed as its
            // own library item, only ever attached to the real movie/episode it sits next to.
            val videoFiles = allFiles.filter { it.isVideo && !it.isTrailer }
            val trailerFiles = allFiles.filter { it.isTrailer }
            val subtitleFiles = allFiles.filter { it.isSubtitle }
            val imageFiles = allFiles.filter { it.isImage }

            // Every file this walk saw, keyed by path - lets per-video .nfo existence be read back
            // from the listing already in memory instead of a second SMB round trip to ask again.
            val filesByPath = allFiles.associateBy { it.path }
            // What's already indexed for this source, keyed by path - the fast path in toMediaItem
            // compares against this to skip re-hashing/re-parsing a file that hasn't changed since
            // the last scan (see toMediaItem's unchanged check).
            val existingByPath = libraryRepository.getBySource(source.id).associateBy { it.filePath }

            val indexed = AtomicInteger(0)
            // Kodi's per-episode .nfo (episodedetails) rarely carries genre/year - those live only
            // in the show-root tvshow.nfo - so fetch it once per show (not once per episode) and
            // reuse across every episode of the same series within this scan.
            val showNfoCache = ConcurrentHashMap<String, NfoMetadata?>()
            val items = coroutineScope {
                videoFiles.map { file ->
                    async {
                        val item = withConnection(pool) { connection ->
                            toMediaItem(source, connection, file, subtitleFiles, imageFiles, trailerFiles, filesByPath, existingByPath, showNfoCache, force)
                        }
                        // Upserted per-file as each one finishes, not batched into one call after
                        // every file in the source has processed - confirmed on-device that losing
                        // Wi-Fi partway through a large share (10000+ files) threw the whole scan
                        // out via the source-level runCatching in scanAll, and since nothing had
                        // been saved yet the next attempt (even once the network came back) had to
                        // redo the entire source from zero instead of resuming. Committing each
                        // item as it completes means everything already processed survives an
                        // interruption, and a retry's own "unchanged since last scan" fast path
                        // (see toMediaItem) then skips straight past it instead of re-parsing.
                        libraryRepository.upsertAll(listOf(item))
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
            items.size
        }
    }

    /** [channel]'s connections plus the [info] that made them - [withConnection] needs [info] to reconnect a connection that died mid-scan, which a bare `Channel<SmbConnection>` had no way to carry. */
    private class SmbPool(val channel: Channel<SmbConnection>, val info: SmbConnectionInfo)

    /**
     * Opens up to [CONNECTION_POOL_SIZE] connections up front and closes all of them once [block]
     * returns. Tolerates individual connect() failures instead of letting one bad attempt fail
     * the whole pool - some SMB servers cap concurrent sessions per user account (confirmed
     * on-device: a NAS with a newly-restricted, non-admin user rejected this app's 5th/6th
     * simultaneous login with the SAME correct credentials that a single "Test Connection" had
     * just accepted moments earlier, surfaced as a login-failure error rather than anything
     * mentioning a session limit - "Проверить соединение" succeeds, then starting a real scan
     * immediately fails with "неверный логин/пароль" even though nothing about the credentials
     * changed). Keeps whichever connections did succeed (down to a minimum of 1) rather than
     * treating a partial failure as fatal - only rethrows if EVERY attempt failed, since that's
     * still a genuine "can't reach this source at all" case worth surfacing as an error.
     */
    private suspend fun <T> withConnectionPool(info: SmbConnectionInfo, block: suspend (SmbPool) -> T): T {
        val connections = mutableListOf<SmbConnection>()
        var lastError: Exception? = null
        repeat(CONNECTION_POOL_SIZE) {
            try {
                connections.add(smbClient.connect(info))
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (connections.isEmpty()) throw lastError ?: IOException("Unable to connect to $info")
        val channel = Channel<SmbConnection>(connections.size)
        connections.forEach { channel.trySend(it) }
        try {
            return block(SmbPool(channel, info))
        } finally {
            connections.forEach { runCatching { it.close() } }
        }
    }

    /**
     * Borrows one connection from [pool] for [block], returning it afterwards - never touches a
     * connection concurrently from two coroutines. A large scan (thousands of files, tens of
     * minutes over Wi-Fi) can outlast a single connection's own session - confirmed on-device via
     * a real crash report ("i11 has already been closed", an smbj internal class) partway through
     * a 30000+-file walk, which then killed the ENTIRE scan for that source (one dead connection
     * among [CONNECTION_POOL_SIZE] failing every call it touched, and any one coroutine throwing
     * cancels every sibling under the shared coroutineScope in scanSource/walkConcurrent). The old
     * version always returned the same connection to the pool in its `finally`, dead or not, so a
     * single drop poisoned that pool slot for the rest of the scan. Now: on failure, open a fresh
     * connection with the same [SmbPool.info] and retry [block] once before giving up - the fresh
     * connection replaces the dead one in the pool either way, so a transient drop only costs one
     * retry, not the whole source.
     */
    private suspend fun <T> withConnection(pool: SmbPool, block: suspend (SmbConnection) -> T): T {
        val connection = pool.channel.receive()
        val result = runCatching { block(connection) }
        if (result.isSuccess) {
            pool.channel.send(connection)
            return result.getOrThrow()
        }
        runCatching { connection.close() }
        val fresh = smbClient.connect(pool.info)
        return try {
            val retryResult = block(fresh)
            pool.channel.send(fresh)
            retryResult
        } catch (retryFailure: Throwable) {
            runCatching { fresh.close() }
            throw retryFailure
        }
    }

    /** Recursively lists [path], fanning sibling sub-directories out across [pool]'s connections instead of one at a time. */
    private suspend fun walkConcurrent(
        pool: SmbPool,
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
        trailerFiles: List<SmbFileRef>,
        filesByPath: Map<String, SmbFileRef>,
        existingByPath: Map<String, MediaItemEntity>,
        showNfoCache: ConcurrentHashMap<String, NfoMetadata?>,
        force: Boolean = false
    ): MediaItemEntity {
        val nfoPath = file.path.substringBeforeLast('.', file.path) + ".nfo"
        // Already part of this walk's listing - no need to ask the server again whether it exists.
        val nfoRef = filesByPath[nfoPath]
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
        // usable as a fallback when it's a real scraper URL, not a path from the machine that wrote
        // the .nfo. These are local-file lookups only (no metadata dependency) so the unchanged
        // fast path below can compute them without touching the .nfo at all - it falls back to
        // whatever was resolved last scan (including a metadata-URL fallback) when no local file is
        // found, which is exactly equivalent to recomputing it since the .nfo hasn't changed either.
        val localPosterPath = findImage(setOf("poster", "folder", "cover"))?.path
        val localFanartPath = findImage(setOf("fanart", "backdrop", "background"))?.path
        // Per-episode screenshot - Kodi/tinyMediaManager write these as "<video name>-thumb.jpg"
        // next to the video, distinct from the show's own poster.jpg at the show root (which
        // `posterPath` above already resolves to for every episode, since findImage checks
        // showFolder too - that's the right thing for the show-grouped library card, but wrong for
        // an individual episode row wanting its own screenshot). Only meaningful for episodes.
        val localEpisodeThumbPath = if (seriesStableId != null) {
            imageFiles
                .firstOrNull {
                    it.path.substringBeforeLast('\\') == videoFolder &&
                        it.baseName.equals("$baseName-thumb", ignoreCase = true)
                }
                ?.path
        } else {
            null
        }
        // Same folder as the video only (not the show root) - trailers are per-movie/per-episode,
        // never shared across a whole series the way a poster is. "<basename>-trailer[N]" wins over
        // a bare "trailer[N]" when both happen to be present.
        fun matchesTrailer(ref: SmbFileRef): Boolean {
            val refBase = ref.baseName.lowercase()
            val prefix = "${baseName.lowercase()}-trailer"
            if (refBase == prefix || (refBase.startsWith(prefix) && refBase.substring(prefix.length).all { it.isDigit() || it == '-' || it == '_' })) return true
            if (refBase == "trailer" || (refBase.startsWith("trailer") && refBase.substring("trailer".length).all { it.isDigit() || it == '-' || it == '_' })) return true
            return false
        }
        val trailerFile = trailerFiles
            .filter { it.path.substringBeforeLast('\\') == videoFolder && matchesTrailer(it) }
            .minByOrNull { it.name }

        // Fields that only ever come from an SMB read of the video itself (content-hash stableId)
        // or its .nfo (everything metadata-shaped) never need re-deriving when neither has changed
        // since the last scan - reuse them wholesale rather than paying for the hash read + nfo
        // fetch/parse again. Path-derived and sibling-file-derived fields above (category, poster/
        // fanart/trailer/subtitles...) are always recomputed fresh regardless, since those can
        // change (a poster or trailer added, say) without the video file itself changing at all.
        val previous = existingByPath[file.path]
        val unchanged = !force &&
            previous != null &&
            previous.sizeBytes == file.sizeBytes &&
            previous.lastModified == file.lastModified &&
            previous.nfoLastModified == nfoRef?.lastModified
        if (unchanged) {
            return previous!!.copy(
                category = category,
                posterPath = localPosterPath ?: previous.posterPath,
                fanartPath = localFanartPath ?: previous.fanartPath,
                episodeThumbPath = localEpisodeThumbPath ?: previous.episodeThumbPath,
                seriesStableId = seriesStableId,
                sizeBytes = file.sizeBytes,
                subtitlePaths = subtitlePaths,
                trailerPath = trailerFile?.path,
                trailerSizeBytes = trailerFile?.sizeBytes,
                lastModified = file.lastModified,
                nfoLastModified = nfoRef?.lastModified
            )
        }

        val (headBytes, tailBytes) = connection.readEdgeSamples(file.path, file.sizeBytes, STABLE_ID_SAMPLE_BYTES)
        val stableId = StableIdGenerator.forFile(source.id, file.sizeBytes, headBytes, tailBytes)
        val metadata = if (nfoRef != null) {
            connection.openInputStream(nfoPath).use { nfoParser.parse(it) }
        } else {
            null
        }
        // Fallback for fields Kodi/tinyMediaManager write once at the show level (tvshow.nfo)
        // rather than repeating in every episode's own .nfo - genre and year most commonly,
        // sometimes rating/country/plot too. Without this, series categories end up with no
        // genre/year on any item, which silently disables the Library genre/year filter chips
        // (they only render when at least one item in the category has a value).
        val showMetadata = showFolder?.let { fetchShowNfo(connection, it, showNfoCache) }

        return MediaItemEntity(
            stableId = stableId,
            sourceId = source.id,
            filePath = file.path,
            category = category,
            title = metadata?.title ?: baseName,
            // Show-level value wins whenever one exists (opposite priority from genre/year/rating
            // below, which are genuine "fill in what's missing" fallbacks) - an episode's own .nfo
            // commonly sets <originaltitle> too, but to the EPISODE's own original name, not the
            // show's. Details' card for a series displays this on the show-level card (via
            // DetailsUiState.seriesTitle for the main title), so a per-episode value there always
            // reads as wrong ("why is this random episode name here"), never as more correct.
            // showMetadata is only ever non-null for TV_SHOWS/CARTOON_SERIES (see seriesStableId
            // above), so movies are unaffected - metadata?.originalTitle still wins for them.
            originalTitle = showMetadata?.originalTitle ?: metadata?.originalTitle,
            year = metadata?.year ?: showMetadata?.year,
            genres = metadata?.genres?.takeIf { it.isNotEmpty() } ?: showMetadata?.genres ?: emptyList(),
            tags = metadata?.tags ?: emptyList(),
            rating = metadata?.rating ?: showMetadata?.rating,
            country = metadata?.country ?: showMetadata?.country,
            runtimeMinutes = metadata?.runtimeMinutes,
            plot = metadata?.plot,
            director = metadata?.director ?: emptyList(),
            actors = metadata?.actors ?: emptyList(),
            // Kept separate from folderCollectionName below (not folder-wins-over-nfo, as an
            // earlier version of this had it) - per feedback, Details wants these as two distinct
            // rows ("Другие части" from whatever the .nfo scraper linked, "Коллекция" from how
            // the files are actually organized on the NAS), which commonly disagree (a reboot's
            // own separate TMDB collection vs. the whole franchise folder) - collapsing them into
            // one value only ever showed one of the two.
            collectionName = metadata?.collectionName,
            // See MediaItemEntity.folderCollectionName's own KDoc.
            folderCollectionName = collectionFolderName(file.path),
            posterPath = localPosterPath ?: metadata?.posterUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") },
            fanartPath = localFanartPath ?: metadata?.fanartUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") },
            episodeThumbPath = localEpisodeThumbPath
                ?: metadata?.posterUrl?.takeIf { seriesStableId != null && (it.startsWith("http://") || it.startsWith("https://")) },
            seasonNumber = metadata?.season,
            episodeNumber = metadata?.episode,
            seriesStableId = seriesStableId,
            dateAdded = previous?.dateAdded ?: System.currentTimeMillis(),
            sizeBytes = file.sizeBytes,
            subtitlePaths = subtitlePaths,
            trailerPath = trailerFile?.path,
            trailerSizeBytes = trailerFile?.sizeBytes,
            lastModified = file.lastModified,
            nfoLastModified = nfoRef?.lastModified,
            mpaa = metadata?.mpaa ?: showMetadata?.mpaa,
            tagline = metadata?.tagline,
            studio = metadata?.studio ?: showMetadata?.studio,
            premiered = metadata?.premiered,
            imdbId = metadata?.imdbId ?: showMetadata?.imdbId,
            tmdbId = metadata?.tmdbId ?: showMetadata?.tmdbId,
            // From the nfo's own <fileinfo><streamdetails><video> - not re-probed by this app (see
            // NfoMetadata.videoWidth's own KDoc for why: an earlier version read each video's real
            // container header itself over SMB, confirmed on-device to be dramatically slower for a
            // library of thousands of files than just reading a value already sitting in the nfo
            // this app fetches anyway). Genuinely per-file, unlike genre/year above - no
            // showMetadata fallback, since a per-episode nfo carries its own streamdetails too.
            videoWidth = metadata?.videoWidth,
            videoHeight = metadata?.videoHeight,
            // Per-file only, same as videoWidth/videoHeight above - a movie's own edition never
            // comes from the show-level tvshow.nfo.
            edition = metadata?.edition
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

    /**
     * Looks for a "<Name> (Коллекция)" path segment anywhere between the source root and the
     * video file - the fallback for a franchise organized as a real NAS folder (e.g.
     * "Люди в чёрном (Коллекция)\Люди в чёрном (1997)\movie.mp4") whose individual movies' own
     * .nfo files don't (or can't consistently) carry a matching <set> tag - a sequel/reboot's .nfo
     * commonly has an empty or differently-worded <set>, or none at all, which is exactly why
     * that film silently fails to group under the app's original nfo-only matching. Purely a path
     * string check on data the directory walk already has in memory - no extra SMB round trip.
     */
    private fun collectionFolderName(path: String): String? =
        path.split('\\').firstNotNullOfOrNull { segment ->
            COLLECTION_FOLDER_REGEX.find(segment)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
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

        private val COLLECTION_FOLDER_REGEX = Regex("""^(.+?)\s*\(Коллекция\)$""", RegexOption.IGNORE_CASE)
    }
}
