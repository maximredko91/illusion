package com.seance.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.data.repository.WatchProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val libraryRepository: LibraryRepository,
    watchProgressRepository: WatchProgressRepository
) : ViewModel() {
    val continueWatching: StateFlow<List<MediaItemEntity>> = watchProgressRepository.observeContinueWatching()
        .map { progressList -> progressList.mapNotNull { libraryRepository.getById(it.mediaItemStableId) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Replaces what used to be "Недавно добавленное" - for a library that isn't added to often,
    // a recently-added row goes stale and shows the same handful of titles indefinitely. A random
    // pick re-shuffled each visit (or via the row's own refresh button) surfaces older library
    // items instead, so there's always something "new" to look at regardless of how often the
    // library itself actually grows.
    private val _randomPicks = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val randomPicks: StateFlow<List<MediaItemEntity>> = _randomPicks.asStateFlow()

    init {
        refreshRandomPicks()
    }

    fun refreshRandomPicks() {
        viewModelScope.launch {
            _randomPicks.value = libraryRepository.getRandom()
        }
    }

    companion object {
        fun factory(libraryRepository: LibraryRepository, watchProgressRepository: WatchProgressRepository) =
            viewModelFactory {
                initializer { HomeViewModel(libraryRepository, watchProgressRepository) }
            }
    }
}
