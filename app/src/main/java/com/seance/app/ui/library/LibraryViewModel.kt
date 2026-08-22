package com.seance.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.data.settings.SettingsRepository
import com.seance.app.domain.model.Category
import com.seance.app.domain.model.SortOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val category: Category
) : ViewModel() {
    private val _sortOrder = MutableStateFlow(SortOrder.DATE_ADDED)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    // Tracks whether the user has picked a sort order via this screen's own SortMenu this
    // session - until they do, this ViewModel keeps following live changes to Settings' "default
    // sort order" instead of only reading it once in init{} (a one-shot read meant the setting
    // only visibly applied after an app restart, since this ViewModel outlives the Settings
    // screen for as long as its tab stays selected).
    private var userOverrodeSortOrder = false

    init {
        viewModelScope.launch {
            settingsRepository.defaultSortOrder.collect { default ->
                if (!userOverrodeSortOrder) _sortOrder.value = default
            }
        }
    }

    private val _genreFilter = MutableStateFlow<String?>(null)
    val genreFilter: StateFlow<String?> = _genreFilter.asStateFlow()

    private val _yearFilter = MutableStateFlow<Int?>(null)
    val yearFilter: StateFlow<Int?> = _yearFilter.asStateFlow()

    private val isSeriesCategory = category == Category.TV_SHOWS || category == Category.CARTOON_SERIES

    // Room's query itself is near-instant, but it's still async - without this, items.isEmpty()
    // is briefly true on every fresh subscription (e.g. switching back to a tab after the 5s
    // WhileSubscribed grace period lapsed), flashing the "nothing here" empty state before the
    // real first emission arrives.
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val allItems: StateFlow<List<MediaItemEntity>> = _sortOrder
        .flatMapLatest { sort ->
            if (isSeriesCategory) {
                libraryRepository.observeSeriesGroupedByCategory(category, sort)
            } else {
                libraryRepository.observeByCategory(category, sort)
            }
        }
        .onEach { _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val items: StateFlow<List<MediaItemEntity>> = combine(allItems, _genreFilter, _yearFilter) { items, genre, year ->
        val filtered = items.filter { item ->
            (genre == null || genre in item.genres) && (year == null || item.year == year)
        }
        // Filtering by a genre also brings along items where it's a minor/secondary tag (e.g. a
        // "Драма" filter matching a movie whose genres are [Боевик, Триллер, Драма]) - those used
        // to sort interleaved with items the genre actually defines. sortedByDescending is stable,
        // so this only reorders by "is it this item's primary genre" and leaves the existing sort
        // (rating/year/title/dateAdded, whichever _sortOrder is active) as the tiebreak within each
        // of the two groups, rather than replacing it.
        if (genre != null) {
            filtered.sortedByDescending { it.genres.firstOrNull() == genre }
        } else {
            filtered
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Each filter menu's options reflect the OTHER filter already chosen (not the unfiltered
    // category) - otherwise picking "Вестерн" then opening the year menu still listed every year
    // in the whole category, including years with zero westerns in them, a dead end that looked
    // like a real option but always emptied the list.
    val availableGenres: StateFlow<List<String>> = combine(allItems, _yearFilter) { items, year ->
        items.filter { year == null || it.year == year }
            .flatMap { it.genres }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableYears: StateFlow<List<Int>> = combine(allItems, _genreFilter) { items, genre ->
        items.filter { genre == null || genre in it.genres }
            .mapNotNull { it.year }.distinct().sortedDescending()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSortOrder(order: SortOrder) {
        userOverrodeSortOrder = true
        _sortOrder.value = order
    }

    fun setGenreFilter(genre: String?) {
        _genreFilter.value = genre
    }

    fun setYearFilter(year: Int?) {
        _yearFilter.value = year
    }

    companion object {
        fun factory(libraryRepository: LibraryRepository, settingsRepository: SettingsRepository, category: Category) = viewModelFactory {
            initializer { LibraryViewModel(libraryRepository, settingsRepository, category) }
        }
    }
}
