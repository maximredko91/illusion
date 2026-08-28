package com.illusion.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.illusion.app.data.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TagSortOrder { COUNT, ALPHABETICAL }

/** Tags are already Russian by the time they land in .nfo files - translated upstream by the
 * user's own script before scanning, not by this app. [label] used to be a separately-resolved
 * translation (on-device ML Kit, or DeepL via a manual Settings upgrade); now it's always just
 * [tag] itself. Kept as a distinct field rather than collapsing call sites onto `tag` directly, so
 * a future re-introduction of translation (a different source, a different tag set) wouldn't need
 * to touch every call site again. */
data class TagCount(val tag: String, val count: Int, val label: String = tag)

/** Full tag browser backing TagsScreen - unlike Search's own inline top-N chip row, this loads every distinct <tag> in the library (a large one can have thousands - see SearchViewModel's own comment), so sort/filter are the only way to make that actually navigable. */
class TagsViewModel(
    private val libraryRepository: LibraryRepository
) : ViewModel() {
    private val _allTags = MutableStateFlow<List<TagCount>>(emptyList())
    private val _sortOrder = MutableStateFlow(TagSortOrder.COUNT)
    val sortOrder: StateFlow<TagSortOrder> = _sortOrder.asStateFlow()

    private val _filter = MutableStateFlow("")
    val filter: StateFlow<String> = _filter.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _tags = MutableStateFlow<List<TagCount>>(emptyList())
    val tags: StateFlow<List<TagCount>> = _tags.asStateFlow()

    init {
        viewModelScope.launch {
            val counts = withContext(Dispatchers.Default) {
                libraryRepository.getAll()
                    .flatMap { it.tags }
                    .groupingBy { it }
                    .eachCount()
                    .map { (tag, count) -> TagCount(tag, count) }
            }
            _allTags.value = counts
            _isLoading.value = false
            recompute()
        }
    }

    fun setSortOrder(order: TagSortOrder) {
        _sortOrder.value = order
        recompute()
    }

    fun setFilter(value: String) {
        _filter.value = value
        recompute()
    }

    private fun recompute() {
        val filtered = _allTags.value.filter {
            _filter.value.isBlank() || it.label.contains(_filter.value, ignoreCase = true) || it.tag.contains(_filter.value, ignoreCase = true)
        }
        _tags.value = when (_sortOrder.value) {
            TagSortOrder.COUNT -> filtered.sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.label })
            TagSortOrder.ALPHABETICAL -> filtered.sortedBy { it.label }
        }
    }

    companion object {
        fun factory(libraryRepository: LibraryRepository) = viewModelFactory {
            initializer { TagsViewModel(libraryRepository) }
        }
    }
}
