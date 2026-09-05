package com.illusion.app.ui.addmedia

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.illusion.app.data.local.entity.SmbSourceEntity
import com.illusion.app.data.nfo.NfoMetadata
import com.illusion.app.data.nfo.NfoWriter
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.security.DevAccessStore
import com.illusion.app.data.smb.SmbClient
import com.illusion.app.data.tmdb.TmdbClient
import com.illusion.app.data.tmdb.TmdbContentRatingsResponse
import com.illusion.app.data.tmdb.TmdbReleaseDatesResponse
import com.illusion.app.data.tmdb.TmdbSearchResult
import com.illusion.app.work.UploadWorker
import com.illusion.app.work.WorkScheduler
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MediaKind { MOVIE, TV_EPISODE }

enum class AddMediaStep { SETUP, SEARCH, CONFIRM, UPLOADING, DONE }

data class AddMediaUiState(
    val step: AddMediaStep = AddMediaStep.SETUP,
    val isTmdbConfigured: Boolean = false,
    val showTmdbKeyEditor: Boolean = false,
    val tmdbKeyInput: String = "",
    val sources: List<SmbSourceEntity> = emptyList(),
    val selectedSourceId: Long? = null,
    val isLoadingFreeSpace: Boolean = false,
    val freeSpaceBytes: Long? = null,
    val freeSpaceError: String? = null,
    val kind: MediaKind = MediaKind.MOVIE,
    val pickedFileUri: Uri? = null,
    val pickedFileName: String? = null,
    val pickedFileSize: Long = 0L,
    val pickedSubtitleUri: Uri? = null,
    val pickedSubtitleName: String? = null,
    val showTitleInput: String = "",
    val seasonNumber: String = "1",
    val episodeNumber: String = "1",
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<TmdbSearchResult> = emptyList(),
    val searchError: String? = null,
    val isFetchingDetails: Boolean = false,
    val fetchError: String? = null,
    val fetched: FetchedMetadata? = null,
    val destinationFolder: String = "",
    val destinationFileName: String = "",
    val isPreparing: Boolean = false,
    val prepareError: String? = null,
    val uploadWorkId: UUID? = null,
    val uploadedBytes: Long = 0L,
    val verifyingUpload: Boolean = false,
    val uploadTotalBytes: Long = 0L,
    val uploadError: String? = null
)

/**
 * Everything pulled from TMDB for the picked title/episode, editable by the developer before
 * writing. Mirrors every field `NfoMetadata`/`NfoWriter` can express - same set Details actually
 * shows (tagline, studio, country, runtime) plus a few more useful ones (mpaa, collection,
 * premiered) that scanning already reads from an .nfo but this flow hadn't been filling in.
 * Deliberately does NOT include a trailer: TMDB only returns YouTube video keys, never a
 * downloadable file, and this app's player only plays local SMB files - there's no TMDB path to
 * the sibling `-trailer.ext` file `LibraryScanner` looks for, that has to be added by hand.
 */
data class FetchedMetadata(
    val tmdbId: Int,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val plot: String?,
    val tagline: String?,
    val genres: List<String>,
    val rating: Double?,
    val cast: List<String>,
    val directors: List<String>,
    val studio: String?,
    val country: String?,
    val runtimeMinutes: Int?,
    val collectionName: String?,
    val mpaa: String?,
    val premiered: String?,
    val imdbId: String?,
    val posterPath: String?,
    val backdropPath: String?,
    // TV_EPISODE only - the specific episode within the picked show, on top of the show-level fields above
    val episodeTitle: String? = null,
    val episodePlot: String? = null,
    val episodeStillPath: String? = null,
    val episodePremiered: String? = null
)

/**
 * Backs the developer-only "add media" scraper (see [com.illusion.app.data.security.DevAccessStore]
 * for the access gate). Fetches metadata from TMDB once, writes it + poster/fanart as local files
 * next to the video on the NAS (same layout [com.illusion.app.data.scan.LibraryScanner] already
 * reads), then hands the actual (potentially large, slow) video byte-copy to [UploadWorker] so it
 * survives the screen closing. A manual rescan afterwards is what actually adds the item to the
 * library - this class never touches Room directly.
 */
class AddMediaViewModel(
    private val sourceRepository: SmbSourceRepository,
    private val smbClient: SmbClient,
    private val tmdbClient: TmdbClient,
    private val nfoWriter: NfoWriter,
    private val devAccessStore: DevAccessStore
) : ViewModel() {

    private val _state = MutableStateFlow(AddMediaUiState(isTmdbConfigured = tmdbClient.isConfigured))
    val state: StateFlow<AddMediaUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val sources = sourceRepository.getEnabledSources()
            val selected = sources.firstOrNull()?.id
            _state.update { it.copy(sources = sources, selectedSourceId = selected) }
            if (selected != null) refreshFreeSpace(selected)
        }
    }

    private fun refreshFreeSpace(sourceId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingFreeSpace = true, freeSpaceError = null) }
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    val info = sourceRepository.connectionInfoById(sourceId) ?: error("Источник SMB недоступен")
                    smbClient.connect(info).use { it.freeSpaceBytes() }
                }
            }
            // Stale response from a source the user already switched away from - drop it rather
            // than overwriting the (possibly already-loaded) figure for the now-selected source.
            if (_state.value.selectedSourceId != sourceId) return@launch
            _state.update {
                it.copy(
                    isLoadingFreeSpace = false,
                    freeSpaceBytes = outcome.getOrNull(),
                    freeSpaceError = outcome.exceptionOrNull()?.message
                )
            }
        }
    }

    fun setTmdbKeyInput(value: String) = _state.update { it.copy(tmdbKeyInput = value) }

    fun openTmdbKeyEditor() = _state.update { it.copy(showTmdbKeyEditor = true, tmdbKeyInput = devAccessStore.tmdbApiKey ?: "") }

    fun cancelTmdbKeyEditor() = _state.update { it.copy(showTmdbKeyEditor = false, tmdbKeyInput = "") }

    /** Takes effect immediately - [TmdbClient] reads the stored key fresh on every request, no restart needed. */
    fun saveTmdbApiKey() {
        devAccessStore.tmdbApiKey = _state.value.tmdbKeyInput.trim()
        _state.update { it.copy(isTmdbConfigured = tmdbClient.isConfigured, showTmdbKeyEditor = false, tmdbKeyInput = "") }
    }

    fun selectSource(sourceId: Long) {
        _state.update { it.copy(selectedSourceId = sourceId, freeSpaceBytes = null, freeSpaceError = null) }
        refreshFreeSpace(sourceId)
    }

    fun selectKind(kind: MediaKind) = _state.update { it.copy(kind = kind) }

    fun setShowTitleInput(value: String) = _state.update { it.copy(showTitleInput = value) }

    fun setSeasonNumber(value: String) = _state.update { it.copy(seasonNumber = value.filter { c -> c.isDigit() }) }

    fun setEpisodeNumber(value: String) = _state.update { it.copy(episodeNumber = value.filter { c -> c.isDigit() }) }

    fun setSearchQuery(value: String) = _state.update { it.copy(searchQuery = value) }

    fun pickFile(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermissionSafely(uri)
        val (name, size) = queryNameAndSize(context.contentResolver, uri)
        val guess = guessTitle(name ?: "")
        _state.update {
            it.copy(
                pickedFileUri = uri,
                pickedFileName = name,
                pickedFileSize = size,
                searchQuery = if (it.kind == MediaKind.TV_EPISODE) it.showTitleInput.ifBlank { guess } else guess
            )
        }
    }

    fun pickSubtitle(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermissionSafely(uri)
        val (name, _) = queryNameAndSize(context.contentResolver, uri)
        _state.update { it.copy(pickedSubtitleUri = uri, pickedSubtitleName = name) }
    }

    fun clearSubtitle() = _state.update { it.copy(pickedSubtitleUri = null, pickedSubtitleName = null) }

    fun goToSearch() {
        val current = _state.value
        val query = (if (current.kind == MediaKind.TV_EPISODE) current.showTitleInput else current.searchQuery).ifBlank { current.searchQuery }
        _state.update { it.copy(step = AddMediaStep.SEARCH, searchQuery = query) }
        search()
    }

    fun search() {
        val query = _state.value.searchQuery.trim()
        if (query.isEmpty()) return
        val kind = _state.value.kind
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, searchError = null) }
            val result = runCatching {
                if (kind == MediaKind.MOVIE) tmdbClient.searchMovies(query) else tmdbClient.searchTvShows(query)
            }
            _state.update {
                it.copy(
                    isSearching = false,
                    searchResults = result.getOrDefault(emptyList()),
                    searchError = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun selectResult(result: TmdbSearchResult) {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isFetchingDetails = true, fetchError = null) }
            val outcome = runCatching {
                if (current.kind == MediaKind.MOVIE) {
                    fetchMovie(result.id)
                } else {
                    val season = current.seasonNumber.toIntOrNull() ?: 1
                    val episode = current.episodeNumber.toIntOrNull() ?: 1
                    fetchTvEpisode(result.id, season, episode)
                }
            }
            val fetched = outcome.getOrNull()
            _state.update {
                it.copy(
                    isFetchingDetails = false,
                    fetchError = outcome.exceptionOrNull()?.message,
                    fetched = fetched,
                    step = if (fetched != null) AddMediaStep.CONFIRM else it.step
                )
            }
            if (fetched != null) applySuggestedDestination(fetched)
        }
    }

    private suspend fun fetchMovie(tmdbId: Int): FetchedMetadata {
        val details = tmdbClient.getMovieDetails(tmdbId)
        return FetchedMetadata(
            tmdbId = details.id,
            title = details.title,
            originalTitle = details.originalTitle,
            year = details.releaseDate?.take(4)?.toIntOrNull(),
            plot = details.overview,
            tagline = details.tagline?.takeIf { it.isNotBlank() },
            genres = details.genres.map { it.name },
            rating = details.voteAverage,
            cast = details.credits?.cast.orEmpty().sortedBy { it.order }.take(MAX_CAST).map { it.name },
            directors = details.credits?.crew.orEmpty().filter { it.job == "Director" }.map { it.name },
            studio = details.productionCompanies.firstOrNull()?.name,
            country = details.productionCountries.firstOrNull()?.name,
            runtimeMinutes = details.runtime,
            collectionName = details.belongsToCollection?.name,
            mpaa = certificationFrom(details.releaseDates),
            premiered = details.releaseDate,
            imdbId = details.externalIds?.imdbId,
            posterPath = details.posterPath,
            backdropPath = details.backdropPath
        )
    }

    private suspend fun fetchTvEpisode(tmdbId: Int, season: Int, episode: Int): FetchedMetadata {
        val details = tmdbClient.getTvDetails(tmdbId)
        val seasonDetails = runCatching { tmdbClient.getSeasonDetails(tmdbId, season) }.getOrNull()
        val episodeInfo = seasonDetails?.episodes?.firstOrNull { it.episodeNumber == episode }
        return FetchedMetadata(
            tmdbId = details.id,
            title = details.name,
            originalTitle = details.originalName,
            year = details.firstAirDate?.take(4)?.toIntOrNull(),
            plot = details.overview,
            tagline = details.tagline?.takeIf { it.isNotBlank() },
            genres = details.genres.map { it.name },
            rating = details.voteAverage,
            cast = details.credits?.cast.orEmpty().sortedBy { it.order }.take(MAX_CAST).map { it.name },
            directors = details.createdBy.map { it.name },
            studio = details.networks.firstOrNull()?.name,
            country = details.originCountry.firstOrNull(),
            runtimeMinutes = details.episodeRunTime.firstOrNull(),
            collectionName = null,
            mpaa = certificationFromTv(details.contentRatings),
            premiered = details.firstAirDate,
            imdbId = details.externalIds?.imdbId,
            posterPath = details.posterPath,
            backdropPath = details.backdropPath,
            episodeTitle = episodeInfo?.name,
            episodePlot = episodeInfo?.overview,
            episodeStillPath = episodeInfo?.stillPath,
            episodePremiered = episodeInfo?.airDate
        )
    }

    /** Prefers a Russian certification, then US, then whatever's first - TMDB's release-date certifications are per-country and RU/US aren't always both present. */
    private fun certificationFrom(response: TmdbReleaseDatesResponse?): String? {
        val countries = response?.results ?: return null
        val preferred = countries.firstOrNull { it.country == "RU" } ?: countries.firstOrNull { it.country == "US" } ?: countries.firstOrNull()
        return preferred?.releaseDates?.firstOrNull { it.certification.isNotBlank() }?.certification
    }

    private fun certificationFromTv(response: TmdbContentRatingsResponse?): String? {
        val countries = response?.results ?: return null
        val preferred = countries.firstOrNull { it.country == "RU" } ?: countries.firstOrNull { it.country == "US" } ?: countries.firstOrNull()
        return preferred?.rating?.takeIf { it.isNotBlank() }
    }

    private fun applySuggestedDestination(fetched: FetchedMetadata) {
        val current = _state.value
        val extension = current.pickedFileName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() } ?: "mkv"
        val titleYear = sanitize(fetched.year?.let { "${fetched.title} ($it)" } ?: fetched.title)
        val folder: String
        val fileName: String
        if (current.kind == MediaKind.MOVIE) {
            folder = titleYear
            fileName = "$titleYear.$extension"
        } else {
            val showFolder = sanitize(fetched.year?.let { "${fetched.title} ($it)" } ?: fetched.title)
            val season = current.seasonNumber.toIntOrNull() ?: 1
            val episode = current.episodeNumber.toIntOrNull() ?: 1
            folder = "$showFolder\\Сезон $season"
            val episodeLabel = fetched.episodeTitle?.let { " - $it" } ?: ""
            fileName = sanitize("$showFolder - S${season.pad2()}E${episode.pad2()}$episodeLabel") + ".$extension"
        }
        _state.update { it.copy(destinationFolder = folder, destinationFileName = fileName) }
    }

    fun setDestinationFolder(value: String) = _state.update { it.copy(destinationFolder = value) }
    fun setDestinationFileName(value: String) = _state.update { it.copy(destinationFileName = value) }

    fun updateFetchedTitle(value: String) = updateFetched { it.copy(title = value) }
    fun updateFetchedOriginalTitle(value: String) = updateFetched { it.copy(originalTitle = value) }
    fun updateFetchedYear(value: String) = updateFetched { it.copy(year = value.toIntOrNull()) }
    fun updateFetchedPlot(value: String) = updateFetched { it.copy(plot = value) }

    private fun updateFetched(transform: (FetchedMetadata) -> FetchedMetadata) {
        _state.update { state -> state.fetched?.let { state.copy(fetched = transform(it)) } ?: state }
    }

    /**
     * Writes the .nfo(s) and poster/fanart images straight to the NAS (fast, small - done inline
     * here rather than via WorkManager), then hands off to [UploadWorker] for the video's own byte
     * copy, which is the only part slow/large enough to need survive-backgrounding + resume.
     */
    fun confirmAndUpload(context: Context) {
        val current = _state.value
        val fetched = current.fetched ?: return
        val sourceId = current.selectedSourceId ?: return
        val videoUri = current.pickedFileUri ?: return
        if (current.destinationFolder.isBlank() || current.destinationFileName.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isPreparing = true, prepareError = null) }
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                val info = sourceRepository.connectionInfoById(sourceId)
                    ?: error("Источник SMB недоступен")
                smbClient.connect(info).use { connection ->
                    val root = info.rootPath.trim('\\')
                    val folderPath = listOf(root, current.destinationFolder.trim('\\'))
                        .filter { it.isNotBlank() }
                        .joinToString("\\")
                    connection.mkdirs(folderPath)

                    val videoPath = "$folderPath\\${current.destinationFileName}"
                    val nfoPath = videoPath.substringBeforeLast('.') + ".nfo"
                    val existingVideo = connection.fileExists(videoPath)

                    if (current.kind == MediaKind.MOVIE) {
                        if (!existingVideo || !connection.fileExists(nfoPath)) connection.openOutputStream(nfoPath).use { out ->
                            nfoWriter.writeMovie(out, fetched.toMovieNfo())
                        }
                        writeImageIfAbsent(connection, folderPath, "poster.jpg", fetched.posterPath)
                        writeImageIfAbsent(connection, folderPath, "fanart.jpg", fetched.backdropPath)
                    } else {
                        val season = current.seasonNumber.toIntOrNull() ?: 1
                        val episode = current.episodeNumber.toIntOrNull() ?: 1
                        if (!existingVideo || !connection.fileExists(nfoPath)) connection.openOutputStream(nfoPath).use { out ->
                            nfoWriter.writeEpisode(out, fetched.toEpisodeNfo(season, episode))
                        }
                        fetched.episodeStillPath?.let { still ->
                            writeImageIfAbsent(connection, folderPath, "${current.destinationFileName.substringBeforeLast('.')}-thumb.jpg", still)
                        }
                        // Show-root sits one level above the "Сезон N" folder this episode's own folderPath points into.
                        val showFolder = folderPath.substringBeforeLast('\\')
                        val tvNfoPath = "$showFolder\\tvshow.nfo"
                        if (!connection.fileExists(tvNfoPath)) {
                            connection.openOutputStream(tvNfoPath).use { out ->
                                nfoWriter.writeTvShow(out, fetched.toTvShowNfo())
                            }
                        }
                        writeImageIfAbsent(connection, showFolder, "poster.jpg", fetched.posterPath)
                        writeImageIfAbsent(connection, showFolder, "fanart.jpg", fetched.backdropPath)
                    }

                    // Named as "<video base name>.<ext>" (e.g. "Interstellar (2014).srt") so
                    // LibraryScanner's sibling-subtitle match (same folder, name starts with the
                    // video's base name) picks it up on the next rescan - same as any subtitle
                    // dropped next to a video by hand.
                    current.pickedSubtitleUri?.let { subtitleUri ->
                        val extension = current.pickedSubtitleName?.substringAfterLast('.', "srt")?.takeIf { it.isNotBlank() } ?: "srt"
                        val subtitlePath = "${videoPath.substringBeforeLast('.')}.$extension"
                        if (!existingVideo || !connection.fileExists(subtitlePath)) context.contentResolver.openInputStream(subtitleUri)?.use { input ->
                            connection.openOutputStream(subtitlePath).use { out -> input.copyTo(out) }
                        }
                    }

                    videoPath
                }
                }
            }

            val videoPath = outcome.getOrNull()
            if (videoPath == null) {
                val error = outcome.exceptionOrNull()
                android.util.Log.e("AddMediaViewModel", "confirmAndUpload: prepare failed", error)
                val message = error?.message?.takeIf { it.isNotBlank() }
                    ?: error?.let { "${it::class.simpleName}" }
                    ?: "Не удалось подготовить папку на NAS"
                _state.update { it.copy(isPreparing = false, prepareError = message) }
                return@launch
            }

            val workId = WorkScheduler.enqueueUpload(context, sourceId, videoUri.toString(), videoPath, current.pickedFileSize)
            _state.update {
                it.copy(
                    isPreparing = false,
                    step = AddMediaStep.UPLOADING,
                    uploadWorkId = workId,
                    uploadTotalBytes = current.pickedFileSize
                )
            }
        }
    }

    fun onUploadProgress(uploaded: Long, total: Long, verifying: Boolean = false) =
        _state.update { it.copy(uploadedBytes = uploaded, uploadTotalBytes = total, verifyingUpload = verifying) }

    fun onUploadFinished(success: Boolean, error: String?) {
        _state.update { it.copy(step = if (success) AddMediaStep.DONE else it.step, uploadError = error, verifyingUpload = false) }
        if (success) _state.value.selectedSourceId?.let(::refreshFreeSpace)
    }

    private suspend fun writeImageIfAbsent(
        connection: com.illusion.app.data.smb.SmbConnection,
        folderPath: String,
        fileName: String,
        tmdbImagePath: String?
    ) {
        if (tmdbImagePath == null) return
        val path = "$folderPath\\$fileName"
        if (connection.fileExists(path)) return
        val bytes = runCatching { tmdbClient.downloadImage(tmdbImagePath) }.getOrNull() ?: return
        connection.openOutputStream(path).use { it.write(bytes) }
    }

    private fun queryNameAndSize(resolver: ContentResolver, uri: Uri): Pair<String?, Long> {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                return name to size
            }
        }
        return null to 0L
    }

    private fun ContentResolver.takePersistableUriPermissionSafely(uri: Uri) {
        runCatching { takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun guessTitle(fileName: String): String =
        fileName.substringBeforeLast('.')
            .replace(Regex("[._]"), " ")
            .replace(Regex("(?i)\\b(1080p|720p|2160p|4k|x264|x265|hevc|web-?dl|bluray|brrip|webrip)\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun sanitize(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

    private fun Int.pad2(): String = toString().padStart(2, '0')

    companion object {
        private const val MAX_CAST = 15

        fun factory(
            sourceRepository: SmbSourceRepository,
            smbClient: SmbClient,
            tmdbClient: TmdbClient,
            nfoWriter: NfoWriter,
            devAccessStore: DevAccessStore
        ) = viewModelFactory {
            initializer { AddMediaViewModel(sourceRepository, smbClient, tmdbClient, nfoWriter, devAccessStore) }
        }
    }
}

private fun FetchedMetadata.toMovieNfo() = NfoMetadata(
    title = title,
    originalTitle = originalTitle,
    year = year,
    genres = genres,
    rating = rating,
    plot = plot,
    director = directors,
    actors = cast,
    country = country,
    runtimeMinutes = runtimeMinutes,
    collectionName = collectionName,
    studio = studio,
    mpaa = mpaa,
    tagline = tagline,
    premiered = premiered,
    imdbId = imdbId,
    tmdbId = tmdbId.toString()
)

private fun FetchedMetadata.toTvShowNfo() = NfoMetadata(
    title = title,
    originalTitle = originalTitle,
    year = year,
    genres = genres,
    rating = rating,
    plot = plot,
    director = directors,
    actors = cast,
    country = country,
    studio = studio,
    mpaa = mpaa,
    tagline = tagline,
    premiered = premiered,
    imdbId = imdbId,
    tmdbId = tmdbId.toString()
)

private fun FetchedMetadata.toEpisodeNfo(season: Int, episode: Int) = NfoMetadata(
    title = episodeTitle ?: title,
    plot = episodePlot,
    actors = cast,
    director = directors,
    season = season,
    episode = episode,
    premiered = episodePremiered,
    tmdbId = tmdbId.toString()
)
