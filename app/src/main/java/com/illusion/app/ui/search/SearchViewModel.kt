package com.illusion.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    initialQuery: String? = null,
    initialDisplayQuery: String? = null
) : ViewModel() {
    // Split from _query so a tag selection can search by its raw English tag (what the data
    // actually matches on) while the field shows the tag's Russian translation - typing normally
    // keeps both in sync via setQuery below, this only ever diverges right after arriving from a
    // translated tag, and never in a way the user can tell (nothing in the UI ever shows _query
    // directly instead of _displayQuery).
    private val _query = MutableStateFlow(initialQuery.orEmpty())
    private val _displayQuery = MutableStateFlow(initialDisplayQuery ?: initialQuery.orEmpty())
    val displayQuery: StateFlow<String> = _displayQuery.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<MediaItemEntity>> = _query
        .debounce(300)
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else libraryRepository.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSearches: StateFlow<List<String>> = settingsRepository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
        _displayQuery.value = value
    }

    /** Called on IME "search" submit or on opening a result - saves the query to recent-searches history. */
    fun commitSearch(value: String = _query.value) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { settingsRepository.addRecentSearch(trimmed) }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch { settingsRepository.removeRecentSearch(query) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { settingsRepository.clearRecentSearches() }
    }

    companion object {
        fun factory(
            libraryRepository: LibraryRepository,
            settingsRepository: SettingsRepository,
            initialQuery: String? = null,
            initialDisplayQuery: String? = null
        ) = viewModelFactory {
            initializer { SearchViewModel(libraryRepository, settingsRepository, initialQuery, initialDisplayQuery) }
        }
    }
}
