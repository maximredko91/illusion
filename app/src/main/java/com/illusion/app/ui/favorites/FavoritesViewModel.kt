package com.illusion.app.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.WatchProgressRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class FavoritesViewModel(
    libraryRepository: LibraryRepository,
    watchProgressRepository: WatchProgressRepository
) : ViewModel() {
    val items: StateFlow<List<MediaItemEntity>> = watchProgressRepository.observeFavorites()
        .map { favorites -> favorites.mapNotNull { libraryRepository.getById(it.mediaItemStableId) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun factory(libraryRepository: LibraryRepository, watchProgressRepository: WatchProgressRepository) =
            viewModelFactory {
                initializer { FavoritesViewModel(libraryRepository, watchProgressRepository) }
            }
    }
}
