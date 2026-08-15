package com.seance.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.data.local.entity.WatchProgressEntity
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.data.repository.WatchProgressRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryEntry(val item: MediaItemEntity, val progress: WatchProgressEntity)

class HistoryViewModel(
    libraryRepository: LibraryRepository,
    private val watchProgressRepository: WatchProgressRepository
) : ViewModel() {
    val entries: StateFlow<List<HistoryEntry>> = watchProgressRepository.observeHistory()
        .map { history ->
            history.mapNotNull { progress ->
                libraryRepository.getById(progress.mediaItemStableId)?.let { HistoryEntry(it, progress) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteEntry(stableId: String) {
        viewModelScope.launch { watchProgressRepository.deleteHistoryEntry(stableId) }
    }

    fun clearAll() {
        viewModelScope.launch { watchProgressRepository.clearHistory() }
    }

    companion object {
        fun factory(libraryRepository: LibraryRepository, watchProgressRepository: WatchProgressRepository) =
            viewModelFactory {
                initializer { HistoryViewModel(libraryRepository, watchProgressRepository) }
            }
    }
}
