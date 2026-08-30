package com.illusion.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.WatchProgressRepository
import com.illusion.app.data.scan.NewContentNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [progressFraction] is null when the source WatchProgressEntity has no known duration yet (e.g. probed lazily on first playback) - PosterCard just omits the bar rather than drawing a 0% one, which would misleadingly suggest "not started" for something mid-watch. */
data class ContinueWatchingItem(val item: MediaItemEntity, val progressFraction: Float?)

class HomeViewModel(
    private val libraryRepository: LibraryRepository,
    watchProgressRepository: WatchProgressRepository
) : ViewModel() {
    val continueWatching: StateFlow<List<ContinueWatchingItem>> = watchProgressRepository.observeContinueWatching()
        .map { progressList ->
            progressList.mapNotNull { progress ->
                val item = libraryRepository.getById(progress.mediaItemStableId) ?: return@mapNotNull null
                val fraction = if (progress.durationMs > 0) {
                    (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f)
                } else {
                    null
                }
                ContinueWatchingItem(item, fraction)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Replaces what used to be "Недавно добавленное" - for a library that isn't added to often,
    // a recently-added row goes stale and shows the same handful of titles indefinitely. A random
    // pick re-shuffled each visit (or via the row's own refresh button) surfaces older library
    // items instead, so there's always something "new" to look at regardless of how often the
    // library itself actually grows.
    private val _randomPicks = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val randomPicks: StateFlow<List<MediaItemEntity>> = _randomPicks.asStateFlow()

    val collections: StateFlow<List<LibraryRepository.CollectionSummary>> = libraryRepository.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasNewContent: StateFlow<Boolean> = NewContentNotifier.hasNewContent

    fun dismissNewContentBanner() = NewContentNotifier.clear()

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
