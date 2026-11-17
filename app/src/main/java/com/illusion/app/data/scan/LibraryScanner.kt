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
/** [newlyAddedStableIds] - items this specific scan found that weren't already indexed (not merely updated) - see [LibraryScanner.hasNewContent]'s sibling use of the same "new" definition, and [com.illusion.app.ui.scan.ScanProgressScreen]'s "что добавилось" summary. */
data class ScanResult(val totalIndexed: Int, val sourceErrors: List<SourceScanError>, val newlyAddedStableIds: List<String> = emptyList(), val removed: List<RemovedTitle> = emptyList())
data class SourceScanError(val sourceName: String, val message: String)
private data class SourceScanOutcome(
    val indexed: Int,
    val newlyAddedStableIds: List<String>,
    val pruneWarning: String? = null,
    val removed: List<RemovedTitle> = emptyList()
)

private class PruneOutcome(val warning: String? = null, val removed: List<RemovedTitle> = emptyList())

/** One line of "what this scan removed": a film, or a series with how many of its episodes went. */
data class RemovedTitle(val title: String, val itemCount: Int, val isSeries: Boolean)

/** Below this many items a source is too small for "more than half vanished" to mean anything. */
private const val MIN_ITEMS_FOR_PRUNE_GUARD = 20

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
        val newlyAdded = mutableListOf<String>()
        val removed = mutableListOf<RemovedTitle>()
        val total = sources.foldIndexed(0) { index, sum, source ->
            val outcome = runCatching { scanSource(source, index, sources.size, force, onProgress) }
            outcome.exceptionOrNull()?.let {
                // classifySmbError turns anything it doesn't recognise into a network-sounding
                // message; the real exception is the only way to tell a code bug from a dead NAS.
                android.util.Log.w("LibraryScanner", "source ${source.displayName} failed", it)
                errors += SourceScanError(source.displayName, classifySmbError(it, source.host, smbClient.localIpv4()))
            }
            outcome.getOrNull()?.let { newlyAdded += it.newlyAddedStableIds }
            outcome.getOrNull()?.pruneWarning?.let { errors += SourceScanError(source.displayName, it) }
            outcome.getOrNull()?.let { removed += it.removed }
            sum + (outcome.getOrNull()?.indexed ?: 0)
        }
        ScanResult(totalIndexed = total, sourceErrors = errors, newlyAddedStableIds = newlyAdded, removed = removed)
    }

    /**
     * Cheap "has anything new turned up on the NAS" check for [com.illusion.app.ui.home.HomeScreen]'s
     * new-content banner - reuses the same directory walk [scanSource] uses for file discovery,
     * but never opens/parses a single `.nfo`, probes a video's container header, or writes
     * anything to Room, and stops at the first source with something new rather than walking every
     * source. A real rescan is still what actually indexes anything found here - this only answers
     * "is it worth telling the user to go rescan", not "what changed".
     */
    suspend fun hasNewContent(): Boolean = withContext(Dispatchers.IO) {
        sourceRepository.getEnabledSources().any { source -> runCatching { sourceHasNewContent(source) }.getOrDefault(false) }
    }

    private suspend fun sourceHasNewContent(source: SmbSourceEntity): Boolean {
        val info = sourceRepository.connectionInfo(source) ?: return false
        val existingPaths = libraryRepository.getBySource(source.id).map { it.filePath }.toSet()
        return withConnectionPool(info) { pool ->
            val discovered = AtomicInteger(0)
            val allFiles = walkConcurrent(pool, source.rootPath, discovered) {}
            allFiles.any { it.isVideo && !it.isTrailer && it.path !in existingPaths }
        }
    }

    private suspend fun scanSource(
        source: SmbSourceEntity,
        sourceIndex: Int,
        sourceCount: Int,
        force: Boolean,
        onProgress: suspend (ScanProgress) -> Unit
    ): SourceScanOutcome {
        val info = sourceRepository.connectionInfo(source) ?: return SourceScanOutcome(0, emptyList())
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
            val existing = libraryRepository.getBySource(source.id)
            val existingByPath = existing.associateBy { it.filePath }
            // And keyed by content: a file that was moved or renamed, or re-read because its .nfo
            // changed or the rescan is a full one, comes out of toMediaItem as a freshly built row
            // with the SAME stableId - see the carry-over below.
            val existingById = existing.associateBy { it.stableId }

            val indexed = AtomicInteger(0)
            // Kodi's per-episode .nfo (episodedetails) rarely carries genre/year - those live only
            // in the show-root tvshow.nfo - so fetch it once per show (not once per episode) and
            // reuse across every episode of the same series within this scan.
            val showNfoCache = ConcurrentHashMap<String, NfoMetadata?>()
            // A file's own path (not its stableId) is what "already indexed" actually means here -
            // stableId can't be known before toMediaItem builds it. Collected concurrently from
            // every videoFiles.map{async{}} below, same reasoning as ConcurrentHashMap above.
            val newlyAddedStableIds = java.util.concurrent.ConcurrentLinkedQueue<String>()
            val items = coroutineScope {
                videoFiles.map { file ->
                    async {
                        val built = withConnection(pool) { connection ->
                            toMediaItem(source, connection, file, subtitleFiles, imageFiles, trailerFiles, filesByPath, existingByPath, showNfoCache, force)
                        }
                        // What the app itself wrote to the row - intro/credits/scene markers, the
                        // date it first appeared - isn't in the file or its .nfo, so a rebuilt row
                        // has none of it. Without this a full rescan wiped every marker in the
                        // library, and an edited .nfo or a moved file wiped that title's.
                        val before = existingById[built.stableId]
                        val item = if (before == null) built else built.copy(
                            dateAdded = before.dateAdded,
                            introStartMs = built.introStartMs ?: before.introStartMs,
                            introEndMs = built.introEndMs ?: before.introEndMs,
                            outroStartMs = built.outroStartMs ?: before.outroStartMs,
                            postCreditsStartMs = built.postCreditsStartMs ?: before.postCreditsStartMs,
                            postCreditsScenesMs = built.postCreditsScenesMs ?: before.postCreditsScenesMs
                        )
                        // A moved file is at a new path but isn't a new title.
                        if (before == null && file.path !in existingByPath) newlyAddedStableIds += item.stableId
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
            // By stableId, not by path: a moved or renamed file keeps its id and only changes path,
            // and judged by path its row - just rewritten above with the new path - was deleted
            // here together with its watch progress and favourite, to reappear bare on the next
            // scan. The other way round, a file replaced by a different one at the same path left
            // its old row behind for good.
            val seenIds = items.mapTo(HashSet()) { it.stableId }
            // A downloaded title whose original is gone is DownloadRepository's business, not this.
            val vanished = existing.filter { it.stableId !in seenIds && !it.isOrphanedDownload }
            val pruned = pruneVanished(source, vanished, indexedBefore = existing.size, seenNow = videoFiles.size, force = force)
            SourceScanOutcome(items.size, newlyAddedStableIds.toList(), pruned.warning, pruned.removed)
        }
    }

    /**
     * Removes library items whose files this scan no longer found on the server. Until this
     * existed, scanning only ever added and updated: a deleted series stayed in the library as
     * cards that couldn't be opened, through any number of rescans, full ones included.
     *
     * Only ever called after a COMPLETE walk of the source (any directory that fails to list
     * throws out of [scanSource] long before this), so "not seen" really does mean "not there".
     * Two cases still don't get pruned, because being wrong costs the user their markers, watch
     * progress and favourites for everything removed:
     * - the walk found no videos at all - a share whose disk isn't mounted looks exactly like an
     *   emptied one, and this NAS is a router with a USB drive;
     * - an ordinary rescan would drop more than half of a sizeable source - the same failure
     *   partway (one of several disks missing). A full rescan is the user saying "yes, really".
     * Returns what was removed (grouped for display - a series is one line, not one per episode)
     * or, when it declined to prune, a message for the user saying why.
     */
    private suspend fun pruneVanished(
        source: SmbSourceEntity,
        vanished: List<MediaItemEntity>,
        indexedBefore: Int,
        seenNow: Int,
        force: Boolean
    ): PruneOutcome {
        if (vanished.isEmpty()) return PruneOutcome()
        if (seenNow == 0) {
            android.util.Log.w("LibraryScanner", "${source.displayName}: no videos found, ${vanished.size} items kept")
            return PruneOutcome(warning = "на сервере не найдено ни одного видео — похоже, диск не подключён; библиотека оставлена как есть")
        }
        if (!force && indexedBefore >= MIN_ITEMS_FOR_PRUNE_GUARD && vanished.size * 2 > indexedBefore) {
            android.util.Log.w("LibraryScanner", "${source.displayName}: ${vanished.size} of $indexedBefore items vanished, kept")
            return PruneOutcome(
                warning = "пропало больше половины файлов (${vanished.size} из $indexedBefore) — карточки оставлены; " +
                    "если файлы действительно удалены, запустите полное пересканирование"
            )
        }
        val removedCount = libraryRepository.deleteVanished(vanished.map { it.stableId })
        // Items with a downloaded copy survive deleteVanished - only look for them when the count
        // says some did, so the report lists exactly what went.
        val gone = if (removedCount == vanished.size) vanished else vanished.filter { libraryRepository.getById(it.stableId) == null }
        // A series is reported as one line with its episode count; the show folder's own name is
        // the series title everywhere else in the app too (see DetailsUiState.seriesTitle).
        val removed = gone.groupBy { it.seriesStableId ?: it.stableId }.values.map { group ->
            val first = group.first()
            RemovedTitle(
                title = first.seriesStableId?.substringAfterLast('\\') ?: first.title,
                itemCount = group.size,
                isSeries = first.seriesStableId != null
            )
        }.sortedBy { it.title }
        // The full list goes to logcat as well: the result screen only shows the first few dozen,
        // and after the first real prune (67 items) there was no record anywhere of WHAT had gone.
        android.util.Log.i("LibraryScanner", "${source.displayName}: removed $removedCount items whose files are gone: " +
            removed.joinToString("; ") { if (it.isSeries) "${it.title} (${it.itemCount})" else it.title })
        return PruneOutcome(removed = removed)
    }

    /** [channel]'s connections plus the [info] that made them - [withConnection] needs [info] to reconnect a connection that died mid-scan, which a bare `Channel<SmbConnection>` had no way to carry. */
    private class SmbPool(val channel: Channel<SmbConnection>, val info: SmbConnectionInfo, val liveConnections: AtomicInteger)

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
        val pool = SmbPool(channel, info, AtomicInteger(connections.size))
        try {
            return block(pool)
        } finally {
            // Drains the channel itself rather than re-closing the original `connections` list -
            // block() (the whole per-source walk) has fully joined by the time we get here, so
            // every connection still alive is sitting in the channel unborrowed, whether it's one
            // of the originals or a retry's replacement (see withConnection). Closing the channel
            // first is safe - already-buffered elements are still drainable via tryReceive() after
            // close(), only a NEW receive()/send() on an empty closed channel fails. The old code
            // only ever closed the original list, silently leaking every replacement connection a
            // successful retry swapped in.
            channel.close()
            while (true) {
                val leftover = channel.tryReceive().getOrNull() ?: break
                runCatching { leftover.close() }
            }
        }
    }

    /** Marks one [SmbPool] connection slot as permanently gone (neither the original connection
     * nor a fresh replacement survived). Closes the pool's channel once every slot has been lost
     * this way, so any [Channel.receive] still suspended waiting for a connection - or called
     * later by a sibling coroutine - fails fast with `ClosedReceiveChannelException` instead of
     * suspending forever with nothing left that could ever send it one. Before this, a run of
     * transient failures could silently drain the pool to zero and hang the entire per-source
     * scan (and, since [scanAll] scans sources sequentially, every source queued after it too)
     * rather than surfacing as the normal per-source scan failure [scanAll] already tolerates. */
    private fun markConnectionSlotLost(pool: SmbPool) {
        if (pool.liveConnections.decrementAndGet() <= 0) pool.channel.close()
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

        val fresh = try {
            smbClient.connect(pool.info)
        } catch (connectFailure: Exception) {
            markConnectionSlotLost(pool)
            throw connectFailure
        }
        val retryResult = runCatching { block(fresh) }
        if (retryResult.isFailure) {
            runCatching { fresh.close() }
            markConnectionSlotLost(pool)
            return retryResult.getOrThrow()
        }
        // The retry itself succeeded - try to recycle the connection, but a concurrent sibling's
        // own double-failure may have already closed the channel in the meantime (send() then
        // throws). Either way the actual work succeeded, so that's not this borrow's failure to
        // report - just close the connection instead of leaking it.
        runCatching { pool.channel.send(fresh) }.onFailure { runCatching { fresh.close() } }
        return retryResult.getOrThrow()
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
        val showFolder = showFolderOf(file.path, categoryMatch)
        val seriesStableId = showFolder?.let { "${source.id}|$it" }
        // Look for poster/fanart next to the video first (how movies are usually organized), then
        // fall back to the show's own root folder - a season subfolder rarely has its own poster,
        // that normally sits at .../Сериалы/ShowName/poster.jpg, one level above "Сезон N".
        val imageSearchFolders = listOfNotNull(videoFolder, showFolder).distinct()
        // Radarr/Sonarr-style libraries drop poster.jpg/fanart.jpg as real files rather than
        // pointing <thumb> at something reachable over SMB - prefer those; a <thumb> value is only
        // usable as a fallback when it's a real scraper URL, not a path from the machine that wrote
        // the .nfo. These are local-file lookups only (no metadata dependency) so the unchanged
        // fast path below can compute them without touching the .nfo at all - it falls back to
        // whatever was resolved last scan (including a metadata-URL fallback) when no local file is
        // found, which is exactly equivalent to recomputing it since the .nfo hasn't changed either.
        val localPosterPath = findImage(imageFiles, imageSearchFolders, baseName, POSTER_NAMES)?.path
        val localFanartPath = findImage(imageFiles, imageSearchFolders, baseName, FANART_NAMES)?.path
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
        fun trailerFor(season: Int?) = resolveTrailer(trailerFiles, videoFolder, showFolder, baseName, season)

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
        // Season/episode from the file name and folders, for a series laid out without
        // per-episode .nfo files - see parseSeasonEpisode. An .nfo value always wins.
        val fromPath = if (seriesStableId != null) parseSeasonEpisode(file.path) else SeasonEpisode(null, null)
        if (unchanged) {
            // Applied on this path too (it's pure string work, no SMB read): otherwise episodes
            // indexed before the fallback existed would stay season-less until a full rescan.
            val seasonNumber = previous!!.seasonNumber ?: fromPath.season
            val trailerFile = trailerFor(seasonNumber)
            return previous.copy(
                category = category,
                seasonNumber = seasonNumber,
                episodeNumber = previous.episodeNumber ?: fromPath.episode,
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
        val seasonNumber = metadata?.season ?: fromPath.season
        val trailerFile = trailerFor(seasonNumber)

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
            seasonNumber = seasonNumber,
            episodeNumber = metadata?.episode ?: fromPath.episode,
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
            // Show-level only - episodedetails.nfo never carries its own <status>, only tvshow.nfo
            // does (per Kodi's spec), same reasoning as the showMetadata fallbacks above.
            status = showMetadata?.status,
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

    internal companion object {
        private const val FILE_DISCOVERY_PROGRESS_STEP = 20

        // Each connection is only ever touched by one coroutine at a time (borrowed from this
        // pool), which sidesteps smbj's DiskShare not being documented as thread-safe - real
        // concurrency comes from having several independent connections, not sharing one.
        private const val CONNECTION_POOL_SIZE = 6

        private const val STABLE_ID_SAMPLE_BYTES = 65536

        private val COLLECTION_FOLDER_REGEX = Regex("""^(.+?)\s*\(Коллекция\)$""", RegexOption.IGNORE_CASE)

        /** "Название-S1-trailer.mp4" / "Show - s02 - trailer2.mkv" - the season marker plus the word itself. */
        private val SEASON_TRAILER_PATTERN = Regex("""[-_. ]s(\d{1,2})[-_. ]+trailer""", RegexOption.IGNORE_CASE)

        /** Usual "S01E02" episode naming, used to place an episode in a season when it has no .nfo. */
        private val SEASON_EPISODE_PATTERN = Regex("""s(\d{1,2})[-_. ]?e\d{1,3}""", RegexOption.IGNORE_CASE)

        val POSTER_NAMES = setOf("poster", "folder", "cover")
        val FANART_NAMES = setOf("fanart", "backdrop", "background")

        /**
         * Group by the show's own folder (one level under whichever folder matched the category),
         * not just the immediate parent - shows are commonly split into per-season subfolders
         * (.../Сериалы/ShowName/Сезон 1/...), and grouping by immediate parent alone would treat
         * each season as a separate "series". Null for movies/cartoons, and for a video lying
         * directly in the category folder.
         */
        fun showFolderOf(path: String, categoryMatch: CategoryMatch): String? {
            if (categoryMatch.category != Category.TV_SHOWS && categoryMatch.category != Category.CARTOON_SERIES) return null
            val segments = path.split('\\')
            val showFolderIndex = categoryMatch.categoryFolderIndex + 1
            return if (showFolderIndex < segments.size - 1) segments.take(showFolderIndex + 1).joinToString("\\") else null
        }

        private fun isNumberedSuffix(rest: String) = rest.all { it.isDigit() || it == '-' || it == '_' }

        /**
         * Beyond a plain "poster.jpg"/"fanart.jpg", also matches Kodi/tinyMediaManager's
         * "<video name>-fanart.jpg" convention and numbered extrafanart variants like
         * "fanart1.jpg"/"fanart-02.jpg" - picking the first alphabetically when several exist, so
         * the choice is at least deterministic across rescans. [folders] are tried in order.
         */
        fun findImage(imageFiles: List<SmbFileRef>, folders: List<String>, baseName: String, names: Set<String>): SmbFileRef? {
            fun matches(ref: SmbFileRef): Boolean {
                val refBase = ref.baseName.lowercase()
                if (refBase in names) return true
                if (names.any { refBase == "${baseName.lowercase()}-$it" }) return true
                return names.any { name ->
                    refBase.startsWith(name) && refBase.length > name.length && isNumberedSuffix(refBase.substring(name.length))
                }
            }
            return folders.firstNotNullOfOrNull { folder ->
                imageFiles
                    .filter { it.path.substringBeforeLast('\\') == folder && matches(it) }
                    .minByOrNull { it.name }
            }
        }

        /**
         * "<basename>-trailer[N]" (this exact episode/movie) wins over a season trailer
         * ("<whatever>-S<n>-trailer[N]", which may also sit at the show root since one file covers
         * a whole season), which in turn wins over a bare "trailer[N]" shared by the folder.
         * [season] is the .nfo's value when known; otherwise it comes from "S01E02" in the name.
         */
        fun resolveTrailer(
            trailerFiles: List<SmbFileRef>,
            videoFolder: String,
            showFolder: String?,
            baseName: String,
            season: Int?
        ): SmbFileRef? {
            val base = baseName.lowercase()
            fun matchesOwn(ref: SmbFileRef): Boolean {
                val refBase = ref.baseName.lowercase()
                val prefix = "$base-trailer"
                return refBase == prefix || (refBase.startsWith(prefix) && isNumberedSuffix(refBase.substring(prefix.length)))
            }
            fun matchesBare(ref: SmbFileRef): Boolean {
                val refBase = ref.baseName.lowercase()
                return refBase == "trailer" ||
                    (refBase.startsWith("trailer") && isNumberedSuffix(refBase.substring("trailer".length)))
            }
            fun matchesSeason(ref: SmbFileRef, season: Int): Boolean {
                val refBase = ref.baseName.lowercase()
                val markerIndex = SEASON_TRAILER_PATTERN.find(refBase)
                    ?.takeIf { it.groupValues[1].toIntOrNull() == season }
                    ?.range?.last ?: return false
                return isNumberedSuffix(refBase.substring(markerIndex + 1))
            }

            val inVideoFolder = trailerFiles.filter { it.path.substringBeforeLast('\\') == videoFolder }
            inVideoFolder.filter { matchesOwn(it) }.minByOrNull { it.name }?.let { return it }
            val effectiveSeason = season ?: SEASON_EPISODE_PATTERN.find(baseName)?.groupValues?.get(1)?.toIntOrNull()
            if (effectiveSeason != null) {
                listOfNotNull(videoFolder, showFolder).distinct().firstNotNullOfOrNull { folder ->
                    trailerFiles
                        .filter { it.path.substringBeforeLast('\\') == folder && matchesSeason(it, effectiveSeason) }
                        .minByOrNull { it.name }
                }?.let { return it }
            }
            return inVideoFolder.filter { matchesBare(it) }.minByOrNull { it.name }
        }

        /**
         * Scans every path segment (not just the first) for a category keyword, since the category
         * folder isn't necessarily the top of the share - e.g. "Movies\Сериалы\ShowName\Сезон 1\...".
         * "мультсериал" is checked before the plain "сериал" match since it contains "сериал" as a
         * substring and would otherwise always be misread as a TV show folder.
         */
        fun categorize(path: String): CategoryMatch {
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

        /**
         * Looks for a "<Name> (Коллекция)" path segment anywhere between the source root and the
         * video file - the fallback for a franchise organized as a real NAS folder (e.g.
         * "Люди в чёрном (Коллекция)\Люди в чёрном (1997)\movie.mp4") whose individual movies' own
         * .nfo files don't (or can't consistently) carry a matching <set> tag - a sequel/reboot's .nfo
         * commonly has an empty or differently-worded <set>, or none at all, which is exactly why
         * that film silently fails to group under the app's original nfo-only matching. Purely a path
         * string check on data the directory walk already has in memory - no extra SMB round trip.
         */
        fun collectionFolderName(path: String): String? =
            path.split('\\').firstNotNullOfOrNull { segment ->
                COLLECTION_FOLDER_REGEX.find(segment)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
            }
    }

    internal data class CategoryMatch(val category: Category, val categoryFolderIndex: Int)
}