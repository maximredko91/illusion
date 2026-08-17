package com.seance.app.ui.details

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.seance.app.data.local.entity.DownloadEntity
import com.seance.app.data.local.entity.DownloadStatus
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.data.local.entity.WatchProgressEntity
import com.seance.app.data.player.AudioTrackProber
import com.seance.app.data.repository.AudioTrackRepository
import com.seance.app.data.repository.DownloadRepository
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.data.repository.WatchProgressRepository
import com.seance.app.work.WorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailsUiState(
    val item: MediaItemEntity? = null,
    val similar: List<MediaItemEntity> = emptyList(),
    val collection: List<MediaItemEntity> = emptyList(),
    val episodes: List<MediaItemEntity> = emptyList(),
    /** Director/actor names with more than one title in the library - only these are worth opening a filmography for. */
    val clickablePersons: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    /** null = not probed yet (or probe failed/timed out); empty = probed, container had none. */
    val audioTracks: List<String>? = null
) {
    /** The show's own name, e.g. "Больница Питт (2025)" - the show folder, not this representative episode's own NFO title. */
    val seriesTitle: String?
        get() = item?.seriesStableId?.substringAfterLast('\\')
}

class DetailsViewModel(
    private val stableId: String,
    private val libraryRepository: LibraryRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val downloadRepository: DownloadRepository,
    private val audioTrackRepository: AudioTrackRepository,
    private val audioTrackProber: AudioTrackProber
) : ViewModel() {
    private val _state = MutableStateFlow(DetailsUiState())
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    val isFavorite: StateFlow<Boolean> = watchProgressRepository.observeIsFavorite(stableId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val download: StateFlow<DownloadEntity?> = downloadRepository.observeForItem(stableId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Every download in the app, keyed by item - used to show per-episode/season download state without a separate query per episode row. */
    val downloads: StateFlow<Map<String, DownloadEntity>> = downloadRepository.observeAll()
        .map { list -> list.associateBy { it.stableId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val watchProgress: StateFlow<WatchProgressEntity?> = watchProgressRepository.observeProgress(stableId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            val item = libraryRepository.getById(stableId)
            if (item == null) {
                _state.value = DetailsUiState(isLoading = false)
                return@launch
            }
            val similar = libraryRepository.getSimilar(item)
            val collection = item.collectionName
                ?.let { libraryRepository.observeByCollection(it).first() }
                ?.filter { it.stableId != stableId }
                ?: emptyList()
            val episodes = item.seriesStableId
                ?.let { libraryRepository.observeEpisodes(it).first() }
                ?: emptyList()
            _state.value = DetailsUiState(
                item = item,
                similar = similar,
                collection = collection,
                episodes = episodes,
                isLoading = false
            )
            loadAudioTracks(item)
            loadClickablePersons(item)
        }
    }

    /** Which actors/directors have more than one title in the library - only these are worth opening a filmography for. A full-library scan, so it runs after the screen is already showing rather than gating [DetailsUiState.isLoading]. */
    private suspend fun loadClickablePersons(item: MediaItemEntity) {
        // One getAll() scan shared across every person on this item, rather than a separate
        // getFilmography() library scan per name.
        val allItems = libraryRepository.getAll()
        val clickablePersons = (item.director + item.actors).distinct()
            .filter { name -> allItems.count { name in it.actors || name in it.director } > 1 }
            .toSet()
        _state.update { it.copy(clickablePersons = clickablePersons) }
    }

    private suspend fun loadAudioTracks(item: MediaItemEntity) {
        val cached = audioTrackRepository.getForItem(item.stableId)
        if (cached != null) {
            _state.update { it.copy(audioTracks = cached.tracks) }
            return
        }
        val probed = audioTrackProber.probe(item.sourceId, item.filePath) ?: return
        audioTrackRepository.save(item.stableId, probed, System.currentTimeMillis())
        _state.update { it.copy(audioTracks = probed) }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            watchProgressRepository.setFavorite(stableId, !isFavorite.value, System.currentTimeMillis())
        }
    }

    fun startDownload(context: Context) {
        WorkScheduler.enqueueDownload(context, stableId)
    }

    /** Same as [startDownload] but for a different item (an episode row in this show's list, not the item this screen was opened for). */
    fun startDownload(context: Context, targetStableId: String) {
        WorkScheduler.enqueueDownload(context, targetStableId)
    }

    /** Downloads every episode in [episodeStableIds] that isn't already downloaded/downloading - re-enqueuing a completed download would otherwise restart it from scratch (`enqueueDownload` always replaces). */
    fun startSeasonDownload(context: Context, episodeStableIds: List<String>) {
        val current = downloads.value
        episodeStableIds
            .filter { id -> current[id]?.status.let { it == null || it == DownloadStatus.FAILED } }
            .forEach { id -> WorkScheduler.enqueueDownload(context, id) }
    }

    /** Cancels an in-progress download, or deletes a finished/failed one - either way the row and any partial file are gone. */
    fun removeDownload(context: Context) {
        removeDownload(context, stableId)
    }

    /** Same as [removeDownload] but for a different item (an episode row in this show's list, not the item this screen was opened for). */
    fun removeDownload(context: Context, targetStableId: String) {
        WorkScheduler.cancelDownload(context, targetStableId)
        viewModelScope.launch { downloadRepository.remove(targetStableId) }
    }

    /**
     * Deletes every completed download among [episodeStableIds] - the season-list counterpart to
     * [removeDownload]. Runs as one sequential batch in a single coroutine (not N detached
     * `launch` calls, one per episode) - a dozen concurrent Room writes racing each other proved
     * unreliable in practice (silently dropped a few rows under load), and a `runCatching` per
     * item means one failure can't derail the rest of the batch.
     */
    fun removeSeasonDownloads(context: Context, episodeStableIds: List<String>) {
        val current = downloads.value
        val idsToRemove = episodeStableIds.filter { id -> current[id]?.status == DownloadStatus.COMPLETED }
        viewModelScope.launch {
            idsToRemove.forEach { id ->
                WorkScheduler.cancelDownload(context, id)
                runCatching { downloadRepository.remove(id) }
            }
        }
    }

    companion object {
        fun factory(
            stableId: String,
            libraryRepository: LibraryRepository,
            watchProgressRepository: WatchProgressRepository,
            downloadRepository: DownloadRepository,
            audioTrackRepository: AudioTrackRepository,
            audioTrackProber: AudioTrackProber
        ) = viewModelFactory {
            initializer {
                DetailsViewModel(
                    stableId,
                    libraryRepository,
                    watchProgressRepository,
                    downloadRepository,
                    audioTrackRepository,
                    audioTrackProber
                )
            }
        }
    }
}
