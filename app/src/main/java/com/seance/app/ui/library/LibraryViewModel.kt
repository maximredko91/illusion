package com.seance.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.domain.model.Category
import com.seance.app.domain.model.SortOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val category: Category
) : ViewModel() {
    private val _sortOrder = MutableStateFlow(SortOrder.DATE_ADDED)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

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
        items.filter { item ->
            (genre == null || genre in item.genres) && (year == null || item.year == year)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Genres/years present in the unfiltered category list, for populating the filter menus. */
    val availableGenres: StateFlow<List<String>> = allItems
        .map { items -> items.flatMap { it.genres }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableYears: StateFlow<List<Int>> = allItems
        .map { items -> items.mapNotNull { it.year }.distinct().sortedDescending() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setGenreFilter(genre: String?) {
        _genreFilter.value = genre
    }

    fun setYearFilter(year: Int?) {
        _yearFilter.value = year
    }

    companion object {
        fun factory(libraryRepository: LibraryRepository, category: Category) = viewModelFactory {
            initializer { LibraryViewModel(libraryRepository, category) }
        }
    }
}
