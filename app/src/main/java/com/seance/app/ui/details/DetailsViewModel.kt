package com.seance.app.ui.details

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.seance.app.data.local.entity.DownloadEntity
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.data.repository.DownloadRepository
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.data.repository.WatchProgressRepository
import com.seance.app.work.WorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailsUiState(
    val item: MediaItemEntity? = null,
    val similar: List<MediaItemEntity> = emptyList(),
    val collection: List<MediaItemEntity> = emptyList(),
    val episodes: List<MediaItemEntity> = emptyList(),
    val isLoading: Boolean = true
) {
    /** The show's own name, e.g. "Больница Питт (2025)" - the show folder, not this representative episode's own NFO title. */
    val seriesTitle: String?
        get() = item?.seriesStableId?.substringAfterLast('\\')
}

class DetailsViewModel(
    private val stableId: String,
    private val libraryRepository: LibraryRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val downloadRepository: DownloadRepository
) : ViewModel() {
    private val _state = MutableStateFlow(DetailsUiState())
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    val isFavorite: StateFlow<Boolean> = watchProgressRepository.observeIsFavorite(stableId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val download: StateFlow<DownloadEntity?> = downloadRepository.observeForItem(stableId)
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
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            watchProgressRepository.setFavorite(stableId, !isFavorite.value, System.currentTimeMillis())
        }
    }

    fun startDownload(context: Context) {
        WorkScheduler.enqueueDownload(context, stableId)
    }

    /** Cancels an in-progress download, or deletes a finished/failed one - either way the row and any partial file are gone. */
    fun removeDownload(context: Context) {
        WorkScheduler.cancelDownload(context, stableId)
        viewModelScope.launch { downloadRepository.remove(stableId) }
    }

    companion object {
        fun factory(
            stableId: String,
            libraryRepository: LibraryRepository,
            watchProgressRepository: WatchProgressRepository,
            downloadRepository: DownloadRepository
        ) = viewModelFactory {
            initializer { DetailsViewModel(stableId, libraryRepository, watchProgressRepository, downloadRepository) }
        }
    }
}
