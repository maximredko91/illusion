package com.seance.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.data.repository.WatchProgressRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    libraryRepository: LibraryRepository,
    watchProgressRepository: WatchProgressRepository
) : ViewModel() {
    val recentlyAdded: StateFlow<List<MediaItemEntity>> = libraryRepository.observeRecentlyAdded()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continueWatching: StateFlow<List<MediaItemEntity>> = watchProgressRepository.observeContinueWatching()
        .map { progressList -> progressList.mapNotNull { libraryRepository.getById(it.mediaItemStableId) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun factory(libraryRepository: LibraryRepository, watchProgressRepository: WatchProgressRepository) =
            viewModelFactory {
                initializer { HomeViewModel(libraryRepository, watchProgressRepository) }
            }
    }
}
